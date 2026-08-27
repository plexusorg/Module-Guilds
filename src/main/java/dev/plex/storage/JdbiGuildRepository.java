package dev.plex.storage;

import dev.plex.Guilds;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.storage.entity.GuildEntity;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.storage.entity.GuildMemberEntity;
import dev.plex.storage.entity.GuildWarpEntity;
import dev.plex.util.CustomLocation;
import org.bukkit.entity.Player;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class JdbiGuildRepository implements GuildRepository
{
    private final Jdbi jdbi;
    private final Executor executor;
    private final String guildsTable;
    private final String membersTable;
    private final String warpsTable;
    private final String invitesTable;

    public JdbiGuildRepository(ModuleStorage storage)
    {
        this.jdbi = storage.jdbi();
        this.executor = Guilds.get().scheduler().asyncExecutor();
        this.guildsTable = storage.table("guilds");
        this.membersTable = storage.table("members");
        this.warpsTable = storage.table("warps");
        this.invitesTable = storage.table("invites");
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
                    Map<String, List<GuildWarpEntity>> warpsByGuild = h.createQuery("SELECT * FROM " + warpsTable)
                            .map((rs, ctx) -> warpMapRow(rs)).list().stream()
                            .collect(Collectors.groupingBy(GuildWarpEntity::getGuildUuid));
                    return guilds.stream().map(entity ->
                    {
                        Guild guild = toGuildBase(entity);
                        membersByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(member -> guild.addMember(toMember(member)));
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
    public CompletableFuture<Void> updateMemberPermission(UUID guildUuid, GuildPermission permission, boolean enabled)
    {
        String column = switch (permission)
        {
            case BLOCK_BREAKING -> "member_block_breaking";
            case BLOCK_PLACING -> "member_block_placing";
            case INTERACTING -> "member_interacting";
        };
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + guildsTable + " SET " + column + " = :enabled WHERE guild_uuid = :g")
                .bind("enabled", enabled)
                .bind("g", guildUuid.toString())
                .execute()));
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
        e.setHomeX(rs.getObject("home_x", Double.class));
        e.setHomeY(rs.getObject("home_y", Double.class));
        e.setHomeZ(rs.getObject("home_z", Double.class));
        e.setHomeYaw(rs.getObject("home_yaw", Float.class));
        e.setHomePitch(rs.getObject("home_pitch", Float.class));
        e.setMotd(rs.getString("motd"));
        e.setTagEnabled(rs.getBoolean("tag_enabled"));
        e.setPublicGuild(rs.getBoolean("is_public"));
        e.setMemberBlockBreaking(rs.getBoolean("member_block_breaking"));
        e.setMemberBlockPlacing(rs.getBoolean("member_block_placing"));
        e.setMemberInteracting(rs.getBoolean("member_interacting"));
        return e;
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
