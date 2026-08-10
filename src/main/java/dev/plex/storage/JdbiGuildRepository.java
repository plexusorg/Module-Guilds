package dev.plex.storage;

import dev.plex.Guilds;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildRolePermissions;
import dev.plex.guild.data.Member;
import dev.plex.storage.entity.GuildEntity;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.storage.entity.GuildMemberEntity;
import dev.plex.storage.entity.GuildRolePermissionEntity;
import dev.plex.storage.entity.GuildWarpEntity;
import dev.plex.util.CustomLocation;
import org.bukkit.entity.Player;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JdbiGuildRepository implements GuildRepository
{
    private static final Pattern UUID_FIELD_PATTERN = Pattern.compile("\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    private final Jdbi jdbi;
    private final Executor executor;
    private final String guildsTable;
    private final String membersTable;
    private final String rolePermissionsTable;
    private final String warpsTable;
    private final String invitesTable;

    public JdbiGuildRepository(ModuleStorage storage)
    {
        this.jdbi = storage.jdbi();
        this.executor = Guilds.get().api().scheduler().asyncExecutor();
        this.guildsTable = storage.table("guilds");
        this.membersTable = storage.table("members");
        this.rolePermissionsTable = storage.table("role_permissions");
        this.warpsTable = storage.table("warps");
        this.invitesTable = storage.table("invites");
        upgradeLegacyGuildTable();
    }

    @Override
    public CompletableFuture<List<Guild>> loadGuilds()
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return jdbi.withHandle(h ->
                {
                    List<GuildEntity> guilds = h.createQuery("SELECT * FROM " + guildsTable)
                            .map((rs, ctx) -> guildMapRow(rs)).list();
                    Map<String, List<GuildMemberEntity>> membersByGuild = h.createQuery("SELECT * FROM " + membersTable)
                            .map((rs, ctx) -> memberMapRow(rs)).list().stream()
                            .collect(Collectors.groupingBy(GuildMemberEntity::getGuildUuid));
                    Map<String, List<GuildRolePermissionEntity>> permissionsByGuild = h.createQuery("SELECT * FROM " + rolePermissionsTable)
                            .map((rs, ctx) -> rolePermissionMapRow(rs)).list().stream()
                            .collect(Collectors.groupingBy(GuildRolePermissionEntity::getGuildUuid));
                    Map<String, List<GuildWarpEntity>> warpsByGuild = h.createQuery("SELECT * FROM " + warpsTable)
                            .map((rs, ctx) -> warpMapRow(rs)).list().stream()
                            .collect(Collectors.groupingBy(GuildWarpEntity::getGuildUuid));
                    return guilds.stream().map(entity ->
                    {
                        Guild guild = toGuildBase(entity);
                        membersByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(member -> guild.addMember(toMember(member)));
                        permissionsByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(permission -> guild.setRolePermissions(GuildRole.valueOf(permission.getRole()), toRolePermissions(permission)));
                        warpsByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(warp -> guild.getWarps().put(warp.getName(), toLocation(warp)));
                        return guild;
                    }).toList();
                });
            }
            catch (JdbiException e)
            {
                throw new IllegalStateException("Failed to load guilds", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Guild> createGuild(Player owner, String name)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                Guild guild = Guild.create(owner, name);
                GuildEntity e = toEntity(guild);
                jdbi.useTransaction(h ->
                {
                    h.createUpdate("INSERT INTO " + guildsTable + " (guild_uuid, name, prefix, owner_uuid, created_at, " +
                                    "home_world, home_x, home_y, home_z, home_yaw, home_pitch, motd, tag_enabled, is_public, " +
                                    "member_block_breaking, member_block_placing, member_interacting) " +
                                    "VALUES (:guildUuid, :name, :prefix, :ownerUuid, :createdAt, :homeWorld, :homeX, :homeY, :homeZ, " +
                                    ":homeYaw, :homePitch, :motd, :tagEnabled, :isPublic, :memberBlockBreaking, :memberBlockPlacing, :memberInteracting)")
                            .bind("guildUuid", e.getGuildUuid())
                            .bind("name", e.getName())
                            .bind("prefix", e.getPrefix())
                            .bind("ownerUuid", e.getOwnerUuid())
                            .bind("createdAt", e.getCreatedAt())
                            .bind("homeWorld", e.getHomeWorld())
                            .bind("homeX", e.getHomeX())
                            .bind("homeY", e.getHomeY())
                            .bind("homeZ", e.getHomeZ())
                            .bind("homeYaw", e.getHomeYaw())
                            .bind("homePitch", e.getHomePitch())
                            .bind("motd", e.getMotd())
                            .bind("tagEnabled", e.isTagEnabled())
                            .bind("isPublic", e.isPublicGuild())
                            .bind("memberBlockBreaking", e.isMemberBlockBreaking())
                            .bind("memberBlockPlacing", e.isMemberBlockPlacing())
                            .bind("memberInteracting", e.isMemberInteracting())
                            .execute();
                    insertMember(h, guild.getGuildUuid(), owner.getUniqueId(), GuildRole.OWNER);
                    insertDefaultRolePermissions(h, guild.getGuildUuid());
                });
                return guild;
            }
            catch (JdbiException e)
            {
                throw new IllegalStateException("Failed to create guild", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteGuild(UUID guildUuid)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("DELETE FROM " + membersTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            h.createUpdate("DELETE FROM " + rolePermissionsTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            h.createUpdate("DELETE FROM " + warpsTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            h.createUpdate("DELETE FROM " + invitesTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            h.createUpdate("DELETE FROM " + guildsTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
        }));
    }

    @Override
    public CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        return runAsync(() -> jdbi.useTransaction(h -> upsertMember(h, guildUuid, playerUuid, role)));
    }

    @Override
    public CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("DELETE FROM " + membersTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("UPDATE " + guildsTable + " SET owner_uuid = :o WHERE guild_uuid = :g")
                    .bind("o", newOwnerUuid.toString())
                    .bind("g", guildUuid.toString())
                    .execute();
            upsertMember(h, guildUuid, oldOwnerUuid, GuildRole.MEMBER);
            upsertMember(h, guildUuid, newOwnerUuid, GuildRole.OWNER);
        }));
    }

    @Override
    public CompletableFuture<Void> updateRolePermission(UUID guildUuid, GuildRole role, GuildPermission permission, boolean enabled)
    {
        String column = switch (permission)
        {
            case BLOCK_BREAKING -> "block_breaking";
            case BLOCK_PLACING -> "block_placing";
            case INTERACTING -> "interacting";
        };
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            upsertRolePermissions(h, guildUuid, role, GuildRolePermissions.defaults(role));
            h.createUpdate("UPDATE " + rolePermissionsTable + " SET " + column + " = :enabled WHERE guild_uuid = :g AND role = :role")
                    .bind("enabled", enabled)
                    .bind("g", guildUuid.toString())
                    .bind("role", role.name())
                    .execute();
            if (role == GuildRole.MEMBER)
            {
                String legacyColumn = switch (permission)
                {
                    case BLOCK_BREAKING -> "member_block_breaking";
                    case BLOCK_PLACING -> "member_block_placing";
                    case INTERACTING -> "member_interacting";
                };
                h.createUpdate("UPDATE " + guildsTable + " SET " + legacyColumn + " = :enabled WHERE guild_uuid = :g")
                        .bind("enabled", enabled)
                        .bind("g", guildUuid.toString())
                        .execute();
            }
        }));
    }

    @Override
    public CompletableFuture<Void> updatePrefix(UUID guildUuid, String prefix)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + guildsTable + " SET prefix = :p WHERE guild_uuid = :g")
                .bind("p", prefix)
                .bind("g", guildUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> updateHome(UUID guildUuid, CustomLocation home)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + guildsTable +
                        " SET home_world = :w, home_x = :x, home_y = :y, home_z = :z, home_yaw = :yaw, home_pitch = :pitch " +
                        "WHERE guild_uuid = :g")
                .bind("w", home == null ? null : home.getWorldName())
                .bind("x", home == null ? null : home.getX())
                .bind("y", home == null ? null : home.getY())
                .bind("z", home == null ? null : home.getZ())
                .bind("yaw", home == null ? null : home.getYaw())
                .bind("pitch", home == null ? null : home.getPitch())
                .bind("g", guildUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            String lowerName = name.toLowerCase(Locale.ROOT);
            GuildWarpEntity entity = h.createQuery("SELECT * FROM " + warpsTable + " WHERE guild_uuid = :g AND name = :n")
                    .bind("g", guildUuid.toString())
                    .bind("n", lowerName)
                    .map((rs, ctx) -> warpMapRow(rs))
                    .findFirst()
                    .orElse(null);
            if (entity == null)
            {
                entity = new GuildWarpEntity();
                entity.setGuildUuid(guildUuid.toString());
                entity.setName(lowerName);
                setWarpLocation(entity, location);
                h.createUpdate("INSERT INTO " + warpsTable + " (guild_uuid, name, world, x, y, z, yaw, pitch) " +
                                "VALUES (:g, :n, :w, :x, :y, :z, :yaw, :pitch)")
                        .bind("g", entity.getGuildUuid())
                        .bind("n", entity.getName())
                        .bind("w", entity.getWorld())
                        .bind("x", entity.getX())
                        .bind("y", entity.getY())
                        .bind("z", entity.getZ())
                        .bind("yaw", entity.getYaw())
                        .bind("pitch", entity.getPitch())
                        .execute();
                return;
            }
            setWarpLocation(entity, location);
            h.createUpdate("UPDATE " + warpsTable + " SET world = :w, x = :x, y = :y, z = :z, yaw = :yaw, pitch = :pitch " +
                            "WHERE guild_uuid = :g AND name = :n")
                    .bind("w", entity.getWorld())
                    .bind("x", entity.getX())
                    .bind("y", entity.getY())
                    .bind("z", entity.getZ())
                    .bind("yaw", entity.getYaw())
                    .bind("pitch", entity.getPitch())
                    .bind("g", guildUuid.toString())
                    .bind("n", lowerName)
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> deleteWarp(UUID guildUuid, String name)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("DELETE FROM " + warpsTable + " WHERE guild_uuid = :g AND name = :n")
                .bind("g", guildUuid.toString())
                .bind("n", name.toLowerCase(Locale.ROOT))
                .execute()));
    }

    @Override
    public CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            deleteInviteSync(h, guildUuid, inviteeUuid);
            h.createUpdate("INSERT INTO " + invitesTable + " (guild_uuid, inviter_uuid, invitee_uuid, expires_at) " +
                            "VALUES (:g, :inviter, :invitee, :expires)")
                    .bind("g", guildUuid.toString())
                    .bind("inviter", inviterUuid.toString())
                    .bind("invitee", inviteeUuid.toString())
                    .bind("expires", expiresAt.toEpochMilli())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid)
    {
        return runAsync(() -> jdbi.useHandle(h -> deleteInviteSync(h, guildUuid, inviteeUuid)));
    }

    @Override
    public CompletableFuture<List<GuildInviteEntity>> invitesFor(UUID inviteeUuid)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                long now = Instant.now().toEpochMilli();
                return jdbi.withHandle(h -> h.createQuery("SELECT * FROM " + invitesTable + " WHERE invitee_uuid = :i")
                        .bind("i", inviteeUuid.toString())
                        .map((rs, ctx) -> inviteMapRow(rs))
                        .list()).stream()
                        .filter(invite -> invite.getExpiresAt() >= now)
                        .toList();
            }
            catch (JdbiException e)
            {
                throw new IllegalStateException("Failed to load guild invites", e);
            }
        }, executor);
    }

    private static GuildEntity guildMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        GuildEntity e = new GuildEntity();
        e.setGuildUuid(rs.getString("guild_uuid"));
        e.setName(rs.getString("name"));
        e.setPrefix(rs.getString("prefix"));
        e.setOwnerUuid(rs.getString("owner_uuid"));
        e.setCreatedAt(rs.getLong("created_at"));
        e.setHomeWorld(rs.getString("home_world"));
        e.setHomeX(nullableDouble(rs, "home_x"));
        e.setHomeY(nullableDouble(rs, "home_y"));
        e.setHomeZ(nullableDouble(rs, "home_z"));
        e.setHomeYaw(nullableFloat(rs, "home_yaw"));
        e.setHomePitch(nullableFloat(rs, "home_pitch"));
        e.setMotd(rs.getString("motd"));
        e.setTagEnabled(rs.getBoolean("tag_enabled"));
        e.setPublicGuild(rs.getBoolean("is_public"));
        e.setMemberBlockBreaking(rs.getBoolean("member_block_breaking"));
        e.setMemberBlockPlacing(rs.getBoolean("member_block_placing"));
        e.setMemberInteracting(rs.getBoolean("member_interacting"));
        return e;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException
    {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Float nullableFloat(ResultSet rs, String column) throws SQLException
    {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : value;
    }

    private void upgradeLegacyGuildTable()
    {
        jdbi.useTransaction(h ->
        {
            ensureAuxiliaryTables(h);
            Set<String> columns = columns(h, guildsTable);
            if (columns.contains("guild_uuid"))
            {
                ensureCurrentPermissionColumns(h, columns);
                backfillRolePermissions(h);
                return;
            }
            if (!columns.contains("guildUuid"))
            {
                return;
            }

            safeAddColumn(h, columns, "guild_uuid", "VARCHAR(46)");
            safeAddColumn(h, columns, "owner_uuid", "VARCHAR(46)");
            safeAddColumn(h, columns, "created_at", "BIGINT");
            safeAddColumn(h, columns, "home_world", "VARCHAR(128)");
            safeAddColumn(h, columns, "home_x", "DOUBLE");
            safeAddColumn(h, columns, "home_y", "DOUBLE");
            safeAddColumn(h, columns, "home_z", "DOUBLE");
            safeAddColumn(h, columns, "home_yaw", "FLOAT");
            safeAddColumn(h, columns, "home_pitch", "FLOAT");
            safeAddColumn(h, columns, "tag_enabled", "BOOLEAN NOT NULL DEFAULT TRUE");
            safeAddColumn(h, columns, "is_public", "BOOLEAN NOT NULL DEFAULT FALSE");
            safeAddColumn(h, columns, "member_block_breaking", "BOOLEAN NOT NULL DEFAULT FALSE");
            safeAddColumn(h, columns, "member_block_placing", "BOOLEAN NOT NULL DEFAULT FALSE");
            safeAddColumn(h, columns, "member_interacting", "BOOLEAN NOT NULL DEFAULT FALSE");

            h.createUpdate("UPDATE " + guildsTable + " SET " +
                            "guild_uuid = guildUuid, " +
                            "created_at = createdAt, " +
                            "tag_enabled = COALESCE(tagEnabled, tag_enabled), " +
                            "is_public = COALESCE(isPublic, is_public) " +
                            "WHERE guild_uuid IS NULL")
                    .execute();

            h.createQuery("SELECT guildUuid, owner, members FROM " + guildsTable)
                    .map((rs, ctx) -> new LegacyGuildRow(rs.getString("guildUuid"), rs.getString("owner"), rs.getString("members")))
                    .forEach(row -> migrateLegacyMembers(h, row));
        });
    }

    private void backfillRolePermissions(Handle h)
    {
        h.createQuery("SELECT guild_uuid, member_block_breaking, member_block_placing, member_interacting FROM " + guildsTable)
                .map((rs, ctx) -> new GuildRolePermissionBackfill(
                        UUID.fromString(rs.getString("guild_uuid")),
                        rs.getBoolean("member_block_breaking"),
                        rs.getBoolean("member_block_placing"),
                        rs.getBoolean("member_interacting")))
                .forEach(backfill ->
                {
                    upsertRolePermissions(h, backfill.guildUuid(), GuildRole.OWNER, GuildRolePermissions.defaults(GuildRole.OWNER));
                    upsertRolePermissions(h, backfill.guildUuid(), GuildRole.MEMBER, new GuildRolePermissions(backfill.blockBreaking(), backfill.blockPlacing(), backfill.interacting()));
                });
    }

    private void ensureAuxiliaryTables(Handle h)
    {
        String database = databaseProduct(h);
        if (!tableExists(h, membersTable))
        {
            h.createUpdate(membersTableSql(database)).execute();
        }
        if (!tableExists(h, rolePermissionsTable))
        {
            h.createUpdate(rolePermissionsTableSql(database)).execute();
        }
        if (!tableExists(h, warpsTable))
        {
            h.createUpdate(warpsTableSql(database)).execute();
        }
        if (!tableExists(h, invitesTable))
        {
            h.createUpdate(invitesTableSql(database)).execute();
        }
    }

    private boolean tableExists(Handle h, String table)
    {
        try (ResultSet rs = h.getConnection().getMetaData().getTables(null, null, table, null))
        {
            if (rs.next())
            {
                return true;
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to inspect guild storage tables", e);
        }

        try (ResultSet rs = h.getConnection().getMetaData().getTables(null, null, table.toUpperCase(Locale.ROOT), null))
        {
            return rs.next();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to inspect guild storage tables", e);
        }
    }

    private String databaseProduct(Handle h)
    {
        try
        {
            return h.getConnection().getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to inspect database product", e);
        }
    }

    private String membersTableSql(String database)
    {
        if (database.contains("sqlite"))
        {
            return "CREATE TABLE IF NOT EXISTS " + membersTable + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "player_uuid VARCHAR(46) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "joined_at BIGINT NOT NULL, " +
                    "UNIQUE (guild_uuid, player_uuid))";
        }
        if (database.contains("postgres"))
        {
            return "CREATE TABLE IF NOT EXISTS " + membersTable + " (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "player_uuid VARCHAR(46) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "joined_at BIGINT NOT NULL, " +
                    "CONSTRAINT uq_" + membersTable + "_guild_player UNIQUE (guild_uuid, player_uuid))";
        }
        return "CREATE TABLE IF NOT EXISTS " + membersTable + " (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "guild_uuid VARCHAR(46) NOT NULL, " +
                "player_uuid VARCHAR(46) NOT NULL, " +
                "role VARCHAR(20) NOT NULL, " +
                "joined_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uq_" + membersTable + "_guild_player (guild_uuid, player_uuid))";
    }

    private String warpsTableSql(String database)
    {
        if (database.contains("sqlite"))
        {
            return "CREATE TABLE IF NOT EXISTS " + warpsTable + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "world VARCHAR(128) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "UNIQUE (guild_uuid, name))";
        }
        if (database.contains("postgres"))
        {
            return "CREATE TABLE IF NOT EXISTS " + warpsTable + " (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "world VARCHAR(128) NOT NULL, " +
                    "x DOUBLE PRECISION NOT NULL, " +
                    "y DOUBLE PRECISION NOT NULL, " +
                    "z DOUBLE PRECISION NOT NULL, " +
                    "yaw REAL NOT NULL, " +
                    "pitch REAL NOT NULL, " +
                    "CONSTRAINT uq_" + warpsTable + "_guild_name UNIQUE (guild_uuid, name))";
        }
        return "CREATE TABLE IF NOT EXISTS " + warpsTable + " (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "guild_uuid VARCHAR(46) NOT NULL, " +
                "name VARCHAR(16) NOT NULL, " +
                "world VARCHAR(128) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, " +
                "pitch FLOAT NOT NULL, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uq_" + warpsTable + "_guild_name (guild_uuid, name))";
    }

    private String rolePermissionsTableSql(String database)
    {
        if (database.contains("sqlite"))
        {
            return "CREATE TABLE IF NOT EXISTS " + rolePermissionsTable + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "block_breaking BOOLEAN NOT NULL DEFAULT 0, " +
                    "block_placing BOOLEAN NOT NULL DEFAULT 0, " +
                    "interacting BOOLEAN NOT NULL DEFAULT 0, " +
                    "UNIQUE (guild_uuid, role))";
        }
        if (database.contains("postgres"))
        {
            return "CREATE TABLE IF NOT EXISTS " + rolePermissionsTable + " (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "block_breaking BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "block_placing BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "interacting BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "CONSTRAINT uq_" + rolePermissionsTable + "_guild_role UNIQUE (guild_uuid, role))";
        }
        return "CREATE TABLE IF NOT EXISTS " + rolePermissionsTable + " (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "guild_uuid VARCHAR(46) NOT NULL, " +
                "role VARCHAR(20) NOT NULL, " +
                "block_breaking BOOLEAN NOT NULL DEFAULT FALSE, " +
                "block_placing BOOLEAN NOT NULL DEFAULT FALSE, " +
                "interacting BOOLEAN NOT NULL DEFAULT FALSE, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uq_" + rolePermissionsTable + "_guild_role (guild_uuid, role))";
    }

    private String invitesTableSql(String database)
    {
        if (database.contains("sqlite"))
        {
            return "CREATE TABLE IF NOT EXISTS " + invitesTable + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "inviter_uuid VARCHAR(46) NOT NULL, " +
                    "invitee_uuid VARCHAR(46) NOT NULL, " +
                    "expires_at BIGINT NOT NULL, " +
                    "UNIQUE (guild_uuid, invitee_uuid))";
        }
        if (database.contains("postgres"))
        {
            return "CREATE TABLE IF NOT EXISTS " + invitesTable + " (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "guild_uuid VARCHAR(46) NOT NULL, " +
                    "inviter_uuid VARCHAR(46) NOT NULL, " +
                    "invitee_uuid VARCHAR(46) NOT NULL, " +
                    "expires_at BIGINT NOT NULL, " +
                    "CONSTRAINT uq_" + invitesTable + "_guild_invitee UNIQUE (guild_uuid, invitee_uuid))";
        }
        return "CREATE TABLE IF NOT EXISTS " + invitesTable + " (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "guild_uuid VARCHAR(46) NOT NULL, " +
                "inviter_uuid VARCHAR(46) NOT NULL, " +
                "invitee_uuid VARCHAR(46) NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uq_" + invitesTable + "_guild_invitee (guild_uuid, invitee_uuid))";
    }

    private void ensureCurrentPermissionColumns(Handle h, Set<String> columns)
    {
        safeAddColumn(h, columns, "member_block_breaking", "BOOLEAN NOT NULL DEFAULT FALSE");
        safeAddColumn(h, columns, "member_block_placing", "BOOLEAN NOT NULL DEFAULT FALSE");
        safeAddColumn(h, columns, "member_interacting", "BOOLEAN NOT NULL DEFAULT FALSE");
    }

    private Set<String> columns(Handle h, String table)
    {
        return h.createQuery("SELECT * FROM " + table + " WHERE 1 = 0")
                .map((rs, ctx) ->
                {
                    Set<String> columns = new HashSet<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++)
                    {
                        columns.add(metaData.getColumnName(i));
                    }
                    return columns;
                })
                .findFirst()
                .orElseGet(() ->
                {
                    try
                    {
                        Set<String> columns = new HashSet<>();
                        try (ResultSet rs = h.getConnection().getMetaData().getColumns(null, null, table, null))
                        {
                            while (rs.next())
                            {
                                columns.add(rs.getString("COLUMN_NAME"));
                            }
                        }
                        return columns;
                    }
                    catch (SQLException e)
                    {
                        throw new IllegalStateException("Failed to inspect guild table columns", e);
                    }
                });
    }

    private void safeAddColumn(Handle h, Set<String> columns, String column, String definition)
    {
        if (columns.contains(column))
        {
            return;
        }
        try
        {
            h.createUpdate("ALTER TABLE " + guildsTable + " ADD COLUMN " + column + " " + definition).execute();
            columns.add(column);
        }
        catch (JdbiException ignored)
        {
            columns.add(column);
        }
    }

    private void migrateLegacyMembers(Handle h, LegacyGuildRow row)
    {
        UUID guildUuid = UUID.fromString(row.guildUuid());
        UUID ownerUuid = firstUuid(row.ownerJson());
        if (ownerUuid == null)
        {
            return;
        }
        h.createUpdate("UPDATE " + guildsTable + " SET owner_uuid = :owner WHERE guild_uuid = :guild")
                .bind("owner", ownerUuid.toString())
                .bind("guild", guildUuid.toString())
                .execute();
        upsertMember(h, guildUuid, ownerUuid, GuildRole.OWNER);
        uuids(row.membersJson()).stream()
                .filter(uuid -> !uuid.equals(ownerUuid))
                .forEach(uuid -> upsertMember(h, guildUuid, uuid, GuildRole.MEMBER));
        upsertRolePermissions(h, guildUuid, GuildRole.OWNER, GuildRolePermissions.defaults(GuildRole.OWNER));
        upsertRolePermissions(h, guildUuid, GuildRole.MEMBER, new GuildRolePermissions(
                legacyBoolean(h, guildUuid, "member_block_breaking"),
                legacyBoolean(h, guildUuid, "member_block_placing"),
                legacyBoolean(h, guildUuid, "member_interacting")));
    }

    private boolean legacyBoolean(Handle h, UUID guildUuid, String column)
    {
        return h.createQuery("SELECT " + column + " FROM " + guildsTable + " WHERE guild_uuid = :guild")
                .bind("guild", guildUuid.toString())
                .mapTo(Boolean.class)
                .findFirst()
                .orElse(false);
    }

    private UUID firstUuid(String json)
    {
        return uuids(json).stream().findFirst().orElse(null);
    }

    private List<UUID> uuids(String json)
    {
        if (json == null || json.isBlank())
        {
            return List.of();
        }
        Matcher matcher = UUID_FIELD_PATTERN.matcher(json);
        return matcher.results()
                .map(result -> UUID.fromString(result.group(1)))
                .distinct()
                .toList();
    }

    private record LegacyGuildRow(String guildUuid, String ownerJson, String membersJson)
    {
    }

    private record GuildRolePermissionBackfill(UUID guildUuid, boolean blockBreaking, boolean blockPlacing, boolean interacting)
    {
    }

    private static GuildMemberEntity memberMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        GuildMemberEntity e = new GuildMemberEntity();
        e.setId(rs.getLong("id"));
        e.setGuildUuid(rs.getString("guild_uuid"));
        e.setPlayerUuid(rs.getString("player_uuid"));
        e.setRole(rs.getString("role"));
        e.setJoinedAt(rs.getLong("joined_at"));
        return e;
    }

    private static GuildRolePermissionEntity rolePermissionMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        GuildRolePermissionEntity e = new GuildRolePermissionEntity();
        e.setId(rs.getLong("id"));
        e.setGuildUuid(rs.getString("guild_uuid"));
        e.setRole(rs.getString("role"));
        e.setBlockBreaking(rs.getBoolean("block_breaking"));
        e.setBlockPlacing(rs.getBoolean("block_placing"));
        e.setInteracting(rs.getBoolean("interacting"));
        return e;
    }

    private static GuildWarpEntity warpMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        GuildWarpEntity e = new GuildWarpEntity();
        e.setId(rs.getLong("id"));
        e.setGuildUuid(rs.getString("guild_uuid"));
        e.setName(rs.getString("name"));
        e.setWorld(rs.getString("world"));
        e.setX(rs.getDouble("x"));
        e.setY(rs.getDouble("y"));
        e.setZ(rs.getDouble("z"));
        e.setYaw(rs.getFloat("yaw"));
        e.setPitch(rs.getFloat("pitch"));
        return e;
    }

    private static GuildInviteEntity inviteMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        GuildInviteEntity e = new GuildInviteEntity();
        e.setId(rs.getLong("id"));
        e.setGuildUuid(rs.getString("guild_uuid"));
        e.setInviterUuid(rs.getString("inviter_uuid"));
        e.setInviteeUuid(rs.getString("invitee_uuid"));
        e.setExpiresAt(rs.getLong("expires_at"));
        return e;
    }

    private Guild toGuildBase(GuildEntity entity)
    {
        String timezone = Guilds.get().api().configuration().mainConfig().getString("server.timezone", "Etc/UTC");
        Guild guild = new Guild(UUID.fromString(entity.getGuildUuid()), ZonedDateTime.ofInstant(Instant.ofEpochMilli(entity.getCreatedAt()), ZoneId.of(timezone)));
        guild.setName(entity.getName());
        guild.setOwnerUuid(UUID.fromString(entity.getOwnerUuid()));
        guild.setPrefix(entity.getPrefix());
        guild.setMotd(entity.getMotd());
        guild.setTagEnabled(entity.isTagEnabled());
        guild.setPublic(entity.isPublicGuild());
        guild.setMemberBlockBreaking(entity.isMemberBlockBreaking());
        guild.setMemberBlockPlacing(entity.isMemberBlockPlacing());
        guild.setMemberInteracting(entity.isMemberInteracting());
        guild.setHome(toLocation(entity));
        return guild;
    }

    private GuildEntity toEntity(Guild guild)
    {
        GuildEntity entity = new GuildEntity();
        entity.setGuildUuid(guild.getGuildUuid().toString());
        entity.setName(guild.getName());
        entity.setOwnerUuid(guild.getOwnerUuid().toString());
        entity.setCreatedAt(guild.getCreatedAt().toInstant().toEpochMilli());
        entity.setPrefix(guild.getPrefix());
        entity.setMotd(guild.getMotd());
        entity.setTagEnabled(guild.isTagEnabled());
        entity.setPublicGuild(guild.isPublic());
        entity.setMemberBlockBreaking(guild.isMemberBlockBreaking());
        entity.setMemberBlockPlacing(guild.isMemberBlockPlacing());
        entity.setMemberInteracting(guild.isMemberInteracting());
        setHome(entity, guild.getHome());
        return entity;
    }

    private Member toMember(GuildMemberEntity entity)
    {
        return new Member(UUID.fromString(entity.getPlayerUuid()), GuildRole.valueOf(entity.getRole()));
    }

    private GuildRolePermissions toRolePermissions(GuildRolePermissionEntity entity)
    {
        return new GuildRolePermissions(entity.isBlockBreaking(), entity.isBlockPlacing(), entity.isInteracting());
    }

    private GuildMemberEntity memberEntity(UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        GuildMemberEntity entity = new GuildMemberEntity();
        entity.setGuildUuid(guildUuid.toString());
        entity.setPlayerUuid(playerUuid.toString());
        entity.setRole(role.name());
        entity.setJoinedAt(Instant.now().toEpochMilli());
        return entity;
    }

    private void insertMember(Handle h, UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        GuildMemberEntity entity = memberEntity(guildUuid, playerUuid, role);
        h.createUpdate("INSERT INTO " + membersTable + " (guild_uuid, player_uuid, role, joined_at) " +
                        "VALUES (:g, :p, :r, :j)")
                .bind("g", entity.getGuildUuid())
                .bind("p", entity.getPlayerUuid())
                .bind("r", entity.getRole())
                .bind("j", entity.getJoinedAt())
                .execute();
    }

    private void upsertMember(Handle h, UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        GuildMemberEntity existing = h.createQuery("SELECT * FROM " + membersTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .map((rs, ctx) -> memberMapRow(rs))
                .findFirst()
                .orElse(null);
        if (existing == null)
        {
            insertMember(h, guildUuid, playerUuid, role);
            return;
        }
        h.createUpdate("UPDATE " + membersTable + " SET role = :r WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("r", role.name())
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .execute();
    }

    private void insertDefaultRolePermissions(Handle h, UUID guildUuid)
    {
        for (GuildRole role : GuildRole.values())
        {
            upsertRolePermissions(h, guildUuid, role, GuildRolePermissions.defaults(role));
        }
    }

    private void upsertRolePermissions(Handle h, UUID guildUuid, GuildRole role, GuildRolePermissions permissions)
    {
        GuildRolePermissionEntity existing = h.createQuery("SELECT * FROM " + rolePermissionsTable + " WHERE guild_uuid = :g AND role = :role")
                .bind("g", guildUuid.toString())
                .bind("role", role.name())
                .map((rs, ctx) -> rolePermissionMapRow(rs))
                .findFirst()
                .orElse(null);
        if (existing != null)
        {
            return;
        }
        h.createUpdate("INSERT INTO " + rolePermissionsTable + " (guild_uuid, role, block_breaking, block_placing, interacting) " +
                        "VALUES (:g, :role, :blockBreak, :blockPlace, :interact)")
                .bind("g", guildUuid.toString())
                .bind("role", role.name())
                .bind("blockBreak", permissions.blockBreaking())
                .bind("blockPlace", permissions.blockPlacing())
                .bind("interact", permissions.interacting())
                .execute();
    }

    private void deleteInviteSync(Handle h, UUID guildUuid, UUID inviteeUuid)
    {
        h.createUpdate("DELETE FROM " + invitesTable + " WHERE guild_uuid = :g AND invitee_uuid = :i")
                .bind("g", guildUuid.toString())
                .bind("i", inviteeUuid.toString())
                .execute();
    }

    private void setHome(GuildEntity entity, CustomLocation home)
    {
        entity.setHomeWorld(home == null ? null : home.getWorldName());
        entity.setHomeX(home == null ? null : home.getX());
        entity.setHomeY(home == null ? null : home.getY());
        entity.setHomeZ(home == null ? null : home.getZ());
        entity.setHomeYaw(home == null ? null : home.getYaw());
        entity.setHomePitch(home == null ? null : home.getPitch());
    }

    private CustomLocation toLocation(GuildEntity entity)
    {
        if (entity.getHomeWorld() == null)
        {
            return null;
        }
        return new CustomLocation(entity.getHomeWorld(), entity.getHomeX(), entity.getHomeY(), entity.getHomeZ(), entity.getHomeYaw(), entity.getHomePitch());
    }

    private void setWarpLocation(GuildWarpEntity entity, CustomLocation location)
    {
        entity.setWorld(location.getWorldName());
        entity.setX(location.getX());
        entity.setY(location.getY());
        entity.setZ(location.getZ());
        entity.setYaw(location.getYaw());
        entity.setPitch(location.getPitch());
    }

    private CustomLocation toLocation(GuildWarpEntity entity)
    {
        return new CustomLocation(entity.getWorld(), entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
    }

    private CompletableFuture<Void> runAsync(Runnable runnable)
    {
        return CompletableFuture.runAsync(runnable, executor);
    }
}
