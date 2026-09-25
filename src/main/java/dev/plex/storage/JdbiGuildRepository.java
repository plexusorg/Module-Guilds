package dev.plex.storage;

import dev.plex.api.storage.ModuleStorage;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.storage.entity.GuildEntity;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.storage.entity.GuildMemberEntity;
import dev.plex.storage.entity.GuildWarpEntity;
import dev.plex.util.CustomLocation;
import net.kyori.adventure.text.Component;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class JdbiGuildRepository implements GuildRepository
{
    private final Jdbi jdbi;
    private final Executor executor;
    private final ZoneId zoneId;
    private final Function<String, Component> prefixParser;
    private final String guildsTable;
    private final String membersTable;
    private final String warpsTable;
    private final String invitesTable;
    private final String guestsTable;

    public JdbiGuildRepository(ModuleStorage storage, Executor executor, ZoneId zoneId, Function<String, Component> prefixParser)
    {
        this.jdbi = storage.jdbi();
        this.executor = executor;
        this.zoneId = zoneId;
        this.prefixParser = prefixParser;
        this.guildsTable = storage.table("guilds");
        this.membersTable = storage.table("members");
        this.warpsTable = storage.table("warps");
        this.invitesTable = storage.table("invites");
        this.guestsTable = storage.table("guests");
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
                    Map<String, List<Guest>> guestsByGuild = h.createQuery("SELECT * FROM " + guestsTable + " WHERE expires_at > :now")
                            .bind("now", Instant.now().toEpochMilli())
                            .map((rs, ctx) -> Map.entry(rs.getString("guild_uuid"), new Guest(
                                    UUID.fromString(rs.getString("player_uuid")), rs.getBoolean("editing"),
                                    Instant.ofEpochMilli(rs.getLong("expires_at")))))
                            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
                    return guilds.stream().map(entity ->
                    {
                        Guild guild = toGuildBase(entity);
                        membersByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(member -> guild.addMember(toMember(member)));
                        warpsByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(warp -> guild.getWarps().put(warp.getName(), toLocation(warp)));
                        guestsByGuild.getOrDefault(entity.getGuildUuid(), List.of()).stream()
                                .filter(guest -> !guild.isOwner(guest.playerUuid()) && !guild.isMember(guest.playerUuid()))
                                .forEach(guest -> guild.getGuests().put(guest.playerUuid(), guest));
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
    public CompletableFuture<Guild> createGuild(Guild guild)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                GuildEntity e = toEntity(guild);
                jdbi.useTransaction(h ->
                {
                    h.createUpdate("INSERT INTO " + guildsTable + " (guild_uuid, name, prefix, owner_uuid, created_at, " +
                                    "spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch) " +
                                    "VALUES (:guildUuid, :name, :prefix, :ownerUuid, :createdAt, :spawnWorld, :spawnX, :spawnY, :spawnZ, " +
                                    ":spawnYaw, :spawnPitch)")
                            .bind("guildUuid", e.getGuildUuid())
                            .bind("name", e.getName())
                            .bind("prefix", e.getPrefix())
                            .bind("ownerUuid", e.getOwnerUuid())
                            .bind("createdAt", e.getCreatedAt())
                            .bind("spawnWorld", e.getSpawnWorld())
                            .bind("spawnX", e.getSpawnX())
                            .bind("spawnY", e.getSpawnY())
                            .bind("spawnZ", e.getSpawnZ())
                            .bind("spawnYaw", e.getSpawnYaw())
                            .bind("spawnPitch", e.getSpawnPitch())
                            .execute();
                    insertMember(h, guild.getGuildUuid(), guild.getOwnerUuid(), GuildRole.OWNER);
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
            h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            h.createUpdate("DELETE FROM " + guildsTable + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
        }));
    }

    @Override
    public CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            upsertMember(h, guildUuid, playerUuid, role);
            h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                    .bind("g", guildUuid.toString())
                    .bind("p", playerUuid.toString())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("DELETE FROM " + membersTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                    .bind("g", guildUuid.toString())
                    .bind("p", playerUuid.toString())
                    .execute();
            h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                    .bind("g", guildUuid.toString())
                    .bind("p", playerUuid.toString())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> upsertGuest(UUID guildUuid, Guest guest)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                    .bind("g", guildUuid.toString())
                    .bind("p", guest.playerUuid().toString())
                    .execute();
            h.createUpdate("INSERT INTO " + guestsTable + " (guild_uuid, player_uuid, editing, expires_at) VALUES (:g, :p, :editing, :expires)")
                    .bind("g", guildUuid.toString())
                    .bind("p", guest.playerUuid().toString())
                    .bind("editing", guest.editing())
                    .bind("expires", guest.expiresAt().toEpochMilli())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> removeGuest(UUID guildUuid, UUID playerUuid)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> updateRole(UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        return runAsync(() -> jdbi.useHandle(h -> updateRoleSync(h, guildUuid, playerUuid, role)));
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
            updateRoleSync(h, guildUuid, oldOwnerUuid, GuildRole.OFFICER);
            updateRoleSync(h, guildUuid, newOwnerUuid, GuildRole.OWNER);
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
    public CompletableFuture<Void> updateSpawn(UUID guildUuid, CustomLocation spawn)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + guildsTable +
                        " SET spawn_world = :w, spawn_x = :x, spawn_y = :y, spawn_z = :z, spawn_yaw = :yaw, spawn_pitch = :pitch " +
                        "WHERE guild_uuid = :g")
                .bind("w", spawn == null ? null : spawn.getWorldName())
                .bind("x", spawn == null ? null : spawn.getX())
                .bind("y", spawn == null ? null : spawn.getY())
                .bind("z", spawn == null ? null : spawn.getZ())
                .bind("yaw", spawn == null ? null : spawn.getYaw())
                .bind("pitch", spawn == null ? null : spawn.getPitch())
                .bind("g", guildUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> clearWorldLocations(UUID guildUuid, String worldName)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("UPDATE " + guildsTable + " SET spawn_world = NULL, spawn_x = NULL, spawn_y = NULL, " +
                            "spawn_z = NULL, spawn_yaw = NULL, spawn_pitch = NULL WHERE guild_uuid = :g AND spawn_world = :w")
                    .bind("g", guildUuid.toString())
                    .bind("w", worldName)
                    .execute();
            h.createUpdate("DELETE FROM " + warpsTable + " WHERE guild_uuid = :g AND world = :w")
                    .bind("g", guildUuid.toString())
                    .bind("w", worldName)
                    .execute();
        }));
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
        e.setSpawnWorld(rs.getString("spawn_world"));
        e.setSpawnX(rs.getObject("spawn_x", Double.class));
        e.setSpawnY(rs.getObject("spawn_y", Double.class));
        e.setSpawnZ(rs.getObject("spawn_z", Double.class));
        e.setSpawnYaw(rs.getObject("spawn_yaw", Float.class));
        e.setSpawnPitch(rs.getObject("spawn_pitch", Float.class));
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
        Guild guild = new Guild(UUID.fromString(entity.getGuildUuid()), ZonedDateTime.ofInstant(Instant.ofEpochMilli(entity.getCreatedAt()), zoneId));
        guild.setName(entity.getName());
        guild.setOwnerUuid(UUID.fromString(entity.getOwnerUuid()));
        guild.setPrefix(entity.getPrefix(), parsePrefix(entity.getPrefix()));
        guild.setSpawn(toLocation(entity));
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
        setSpawn(entity, guild.getSpawn());
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

    private void updateRoleSync(Handle h, UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        int updated = h.createUpdate("UPDATE " + membersTable + " SET role = :r WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("r", role.name())
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .execute();
        if (updated != 1)
        {
            // Throwing rolls back an enclosing transaction.
            throw new IllegalStateException("Guild member " + playerUuid + " does not exist");
        }
    }

    private void deleteInviteSync(Handle h, UUID guildUuid, UUID inviteeUuid)
    {
        h.createUpdate("DELETE FROM " + invitesTable + " WHERE guild_uuid = :g AND invitee_uuid = :i")
                .bind("g", guildUuid.toString())
                .bind("i", inviteeUuid.toString())
                .execute();
    }

    private void setSpawn(GuildEntity entity, CustomLocation spawn)
    {
        entity.setSpawnWorld(spawn == null ? null : spawn.getWorldName());
        entity.setSpawnX(spawn == null ? null : spawn.getX());
        entity.setSpawnY(spawn == null ? null : spawn.getY());
        entity.setSpawnZ(spawn == null ? null : spawn.getZ());
        entity.setSpawnYaw(spawn == null ? null : spawn.getYaw());
        entity.setSpawnPitch(spawn == null ? null : spawn.getPitch());
    }

    private Component parsePrefix(String prefix)
    {
        return prefix == null || prefix.isEmpty() ? Component.empty() : prefixParser.apply(prefix);
    }

    private CustomLocation toLocation(GuildEntity entity)
    {
        if (entity.getSpawnWorld() == null)
        {
            return null;
        }
        return new CustomLocation(entity.getSpawnWorld(), entity.getSpawnX(), entity.getSpawnY(), entity.getSpawnZ(), entity.getSpawnYaw(), entity.getSpawnPitch());
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
