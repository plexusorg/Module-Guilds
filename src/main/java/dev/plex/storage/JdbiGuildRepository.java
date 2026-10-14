package dev.plex.storage;

import dev.plex.api.storage.ModuleStorage;
import dev.plex.guild.Guild;
import dev.plex.guild.Actor;
import dev.plex.guild.GuildAction;
import dev.plex.guild.GuildSession;
import dev.plex.guild.PendingGuild;
import com.google.gson.JsonParser;
import java.util.function.Consumer;
import dev.plex.guild.GuildPrefix;
import dev.plex.guild.GuildPrefixTakenException;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final String inviteHistoryTable;
    private final String guestsTable;
    private final String actionsTable;
    private final String sessionsTable;
    private final String historyPurgesTable;

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
        this.inviteHistoryTable = storage.table("invite_history");
        this.guestsTable = storage.table("guests");
        this.actionsTable = storage.table("actions");
        this.sessionsTable = storage.table("sessions");
        this.historyPurgesTable = storage.table("history_purges");
    }

    @Override
    public CompletableFuture<LoadedGuilds> loadGuilds()
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
                    Map<String, List<Instant>> inviteHistoryByGuild = h.createQuery("SELECT guild_uuid, created_at FROM " + inviteHistoryTable + " WHERE created_at > :cutoff")
                            .bind("cutoff", Instant.now().minus(Guild.INVITE_WINDOW).toEpochMilli())
                            .map((rs, ctx) -> Map.entry(rs.getString("guild_uuid"), Instant.ofEpochMilli(rs.getLong("created_at"))))
                            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
                    List<Guild> active = new ArrayList<>();
                    List<PendingGuild> pending = new ArrayList<>();
                    for (GuildEntity entity : guilds)
                    {
                        Guild guild = toGuildBase(entity);
                        guild.getInviteHistory().addAll(inviteHistoryByGuild.getOrDefault(entity.getGuildUuid(), List.of()));
                        membersByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(member -> guild.addMember(toMember(member)));
                        warpsByGuild.getOrDefault(entity.getGuildUuid(), List.of())
                                .forEach(warp -> guild.getWarps().put(warp.getName(), toLocation(warp)));
                        guestsByGuild.getOrDefault(entity.getGuildUuid(), List.of()).stream()
                                .filter(guest -> !guild.isOwner(guest.playerUuid()) && !guild.isMember(guest.playerUuid()))
                                .forEach(guest -> guild.getGuests().put(guest.playerUuid(), guest));
                        if (entity.getDeletedAt() == null)
                        {
                            active.add(guild);
                        }
                        else
                        {
                            pending.add(new PendingGuild(guild, entity.getDeletedAt(),
                                    entity.getDeletedBy() == null ? null : UUID.fromString(entity.getDeletedBy())));
                        }
                    }
                    return new LoadedGuilds(active, pending);
                });
            }
            catch (JdbiException e)
            {
                throw new IllegalStateException("Failed to load guilds", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Guild> createGuild(Guild guild, GuildAction action)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                GuildEntity e = toEntity(guild);
                write(action, h ->
                {
                    h.createUpdate("INSERT INTO " + guildsTable + " (guild_uuid, name, prefix, prefix_key, owner_uuid, created_at, " +
                                    "spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, time_mode, weather_mode) " +
                                    "VALUES (:guildUuid, :name, :prefix, :prefixKey, :ownerUuid, :createdAt, :spawnWorld, :spawnX, :spawnY, :spawnZ, " +
                                    ":spawnYaw, :spawnPitch, :timeMode, :weatherMode)")
                            .bind("guildUuid", e.getGuildUuid())
                            .bind("name", e.getName())
                            .bind("prefix", e.getPrefix())
                            .bind("prefixKey", e.getPrefixKey())
                            .bind("ownerUuid", e.getOwnerUuid())
                            .bind("createdAt", e.getCreatedAt())
                            .bind("spawnWorld", e.getSpawnWorld())
                            .bind("spawnX", e.getSpawnX())
                            .bind("spawnY", e.getSpawnY())
                            .bind("spawnZ", e.getSpawnZ())
                            .bind("spawnYaw", e.getSpawnYaw())
                            .bind("spawnPitch", e.getSpawnPitch())
                            .bind("timeMode", e.getTimeMode())
                            .bind("weatherMode", e.getWeatherMode())
                            .execute();
                    insertContents(h, guild);
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
    public CompletableFuture<Void> markDeleted(UUID guildUuid, long deletedAt, @Nullable UUID deletedBy, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
        {
            int marked = h.createUpdate("UPDATE " + guildsTable + " SET deleted_at = :at, deleted_by = :by WHERE guild_uuid = :g AND deleted_at IS NULL")
                    .bind("at", deletedAt).bind("by", deletedBy == null ? null : deletedBy.toString()).bind("g", guildUuid.toString()).execute();
            if (marked != 1)
            {
                throw new IllegalStateException("The guild no longer exists");
            }
            for (String table : List.of(membersTable, guestsTable, invitesTable))
            {
                h.createUpdate("DELETE FROM " + table + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            }
        }));
    }

    @Override
    public CompletableFuture<Void> restoreGuild(Guild candidate, GuildAction action)
    {
        return runAsync(() -> write(action, h -> writeGuild(h, candidate, true)));
    }

    @Override
    public CompletableFuture<Void> finalizeDeletion(UUID guildUuid, String world, long untilAt)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            // SQLite cascades only with PRAGMA foreign_keys, so delete every child row by name.
            for (String table : List.of(warpsTable, invitesTable, guestsTable, inviteHistoryTable, membersTable, actionsTable))
            {
                h.createUpdate("DELETE FROM " + table + " WHERE guild_uuid = :g").bind("g", guildUuid.toString()).execute();
            }
            int deleted = h.createUpdate("DELETE FROM " + guildsTable + " WHERE guild_uuid = :g AND deleted_at IS NOT NULL")
                    .bind("g", guildUuid.toString()).execute();
            if (deleted != 1)
            {
                throw new IllegalStateException("The guild is not pending deletion");
            }
            insertHistoryPurge(h, new HistoryPurge(world, untilAt));
        }));
    }

    @Override
    public CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role, long joinedAt, boolean deleteInvite, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
        {
            if (deleteInvite)
            {
                int deleted = h.createUpdate("DELETE FROM " + invitesTable + " WHERE guild_uuid = :g AND invitee_uuid = :p AND expires_at >= :now")
                        .bind("g", guildUuid.toString()).bind("p", playerUuid.toString()).bind("now", System.currentTimeMillis()).execute();
                if (deleted != 1)
                {
                    throw new IllegalArgumentException("The invite no longer exists");
                }
            }
            insertMember(h, guildUuid, playerUuid, role, joinedAt);
            h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                    .bind("g", guildUuid.toString())
                    .bind("p", playerUuid.toString())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
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
    public CompletableFuture<Void> upsertGuest(UUID guildUuid, Guest guest, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
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
    public CompletableFuture<Void> removeGuest(UUID guildUuid, UUID playerUuid, GuildAction action)
    {
        return runAsync(() -> write(action, h -> h.createUpdate("DELETE FROM " + guestsTable + " WHERE guild_uuid = :g AND player_uuid = :p")
                .bind("g", guildUuid.toString())
                .bind("p", playerUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> updateRole(UUID guildUuid, UUID playerUuid, GuildRole role, GuildAction action)
    {
        return runAsync(() -> write(action, h -> updateRoleSync(h, guildUuid, playerUuid, role)));
    }

    @Override
    public CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
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
    public CompletableFuture<Void> updatePrefix(UUID guildUuid, GuildPrefix prefix, GuildAction action)
    {
        return runAsync(() ->
        {
            try
            {
                write(action, h -> h.createUpdate("UPDATE " + guildsTable + " SET prefix = :p, prefix_key = :key WHERE guild_uuid = :g")
                        .bind("p", prefix.text())
                        .bind("key", prefix.key())
                        .bind("g", guildUuid.toString())
                        .execute());
            }
            catch (JdbiException failure)
            {
                if (isUniqueViolation(failure))
                {
                    throw new GuildPrefixTakenException(failure);
                }
                throw failure;
            }
        });
    }

    private boolean isUniqueViolation(Throwable failure)
    {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
        {
            if (cause instanceof SQLException sql)
            {
                return "23505".equals(sql.getSQLState())
                        || (sql.getErrorCode() == 1062 && "23000".equals(sql.getSQLState()))
                        || (sql.getErrorCode() == 19 && sql.getMessage() != null
                            && sql.getMessage().contains("UNIQUE constraint failed: " + guildsTable + ".prefix_key"));
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<Void> updateTimeMode(UUID guildUuid, GuildTimeMode mode, GuildAction action)
    {
        return runAsync(() -> write(action, h -> h.createUpdate("UPDATE " + guildsTable + " SET time_mode = :mode WHERE guild_uuid = :g")
                .bind("mode", mode.name())
                .bind("g", guildUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> updateWeatherMode(UUID guildUuid, GuildWeatherMode mode, GuildAction action)
    {
        return runAsync(() -> write(action, h -> h.createUpdate("UPDATE " + guildsTable + " SET weather_mode = :mode WHERE guild_uuid = :g")
                .bind("mode", mode.name())
                .bind("g", guildUuid.toString())
                .execute()));
    }

    @Override
    public CompletableFuture<Void> updateSpawn(UUID guildUuid, CustomLocation spawn, GuildAction action)
    {
        return runAsync(() -> write(action, h -> h.createUpdate("UPDATE " + guildsTable +
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
    public CompletableFuture<Void> clearWorldLocations(UUID guildUuid, String worldName, GuildAction action, @Nullable HistoryPurge purge)
    {
        return runAsync(() -> write(action, h ->
        {
            insertHistoryPurge(h, purge);
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
    public CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
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
    public CompletableFuture<Void> deleteWarp(UUID guildUuid, String name, GuildAction action)
    {
        return runAsync(() -> write(action, h -> h.createUpdate("DELETE FROM " + warpsTable + " WHERE guild_uuid = :g AND name = :n")
                .bind("g", guildUuid.toString())
                .bind("n", name.toLowerCase(Locale.ROOT))
                .execute()));
    }

    @Override
    public CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant createdAt, Instant expiresAt, GuildAction action)
    {
        return runAsync(() -> write(action, h ->
        {
            deleteInviteSync(h, guildUuid, inviteeUuid);
            h.createUpdate("INSERT INTO " + invitesTable + " (guild_uuid, inviter_uuid, invitee_uuid, expires_at) " +
                            "VALUES (:g, :inviter, :invitee, :expires)")
                    .bind("g", guildUuid.toString())
                    .bind("inviter", inviterUuid.toString())
                    .bind("invitee", inviteeUuid.toString())
                    .bind("expires", expiresAt.toEpochMilli())
                    .execute();
            h.createUpdate("INSERT INTO " + inviteHistoryTable + " (guild_uuid, created_at) VALUES (:g, :created)")
                    .bind("g", guildUuid.toString())
                    .bind("created", createdAt.toEpochMilli())
                    .execute();
            h.createUpdate("DELETE FROM " + inviteHistoryTable + " WHERE guild_uuid = :g AND created_at <= :cutoff")
                    .bind("g", guildUuid.toString())
                    .bind("cutoff", createdAt.minus(Guild.INVITE_WINDOW).toEpochMilli())
                    .execute();
        }));
    }

    @Override
    public CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid, GuildAction action)
    {
        return runAsync(() -> write(action, h -> deleteInviteSync(h, guildUuid, inviteeUuid)));
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
        e.setPrefixKey(rs.getString("prefix_key"));
        e.setOwnerUuid(rs.getString("owner_uuid"));
        e.setCreatedAt(rs.getLong("created_at"));
        e.setTimeMode(rs.getString("time_mode"));
        e.setWeatherMode(rs.getString("weather_mode"));
        e.setSpawnWorld(rs.getString("spawn_world"));
        e.setSpawnX(rs.getObject("spawn_x", Double.class));
        e.setSpawnY(rs.getObject("spawn_y", Double.class));
        e.setSpawnZ(rs.getObject("spawn_z", Double.class));
        e.setSpawnYaw(rs.getObject("spawn_yaw", Float.class));
        e.setSpawnPitch(rs.getObject("spawn_pitch", Float.class));
        e.setDeletedAt(rs.getObject("deleted_at", Long.class));
        e.setDeletedBy(rs.getString("deleted_by"));
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
        GuildPrefix prefix = GuildPrefix.parse(entity.getPrefix(), prefixParser);
        if (!Objects.equals(prefix.key(), entity.getPrefixKey()))
        {
            throw new IllegalStateException("The stored prefix key does not match guild " + entity.getGuildUuid());
        }
        guild.setPrefix(prefix);
        guild.setSpawn(toLocation(entity));
        guild.setTimeMode(GuildTimeMode.valueOf(entity.getTimeMode()));
        guild.setWeatherMode(GuildWeatherMode.valueOf(entity.getWeatherMode()));
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
        entity.setPrefixKey(guild.getPrefixKey());
        entity.setTimeMode(guild.getTimeMode().name());
        entity.setWeatherMode(guild.getWeatherMode().name());
        setSpawn(entity, guild.getSpawn());
        return entity;
    }

    private Member toMember(GuildMemberEntity entity)
    {
        return new Member(UUID.fromString(entity.getPlayerUuid()), GuildRole.valueOf(entity.getRole()), entity.getJoinedAt());
    }

    private GuildMemberEntity memberEntity(UUID guildUuid, UUID playerUuid, GuildRole role, long joinedAt)
    {
        GuildMemberEntity entity = new GuildMemberEntity();
        entity.setGuildUuid(guildUuid.toString());
        entity.setPlayerUuid(playerUuid.toString());
        entity.setRole(role.name());
        entity.setJoinedAt(joinedAt);
        return entity;
    }

    private void insertMember(Handle h, UUID guildUuid, UUID playerUuid, GuildRole role, long joinedAt)
    {
        GuildMemberEntity entity = memberEntity(guildUuid, playerUuid, role, joinedAt);
        h.createUpdate("INSERT INTO " + membersTable + " (guild_uuid, player_uuid, role, joined_at) " +
                        "VALUES (:g, :p, :r, :j)")
                .bind("g", entity.getGuildUuid())
                .bind("p", entity.getPlayerUuid())
                .bind("r", entity.getRole())
                .bind("j", entity.getJoinedAt())
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

    private void write(GuildAction action, Consumer<Handle> mutation)
    {
        jdbi.useTransaction(h ->
        {
            mutation.accept(h);
            insertAction(h, action);
        });
    }

    private void insertAction(Handle h, GuildAction action)
    {
        if (action == null)
        {
            return;
        }
        if (action.undoId() != null)
        {
            int updated = h.createUpdate("UPDATE " + actionsTable + " SET undone_at = :now WHERE id = :id AND undone_at IS NULL")
                    .bind("now", action.createdAt()).bind("id", action.undoId()).execute();
            if (updated != 1)
            {
                throw new IllegalStateException("The action is already undone or expired");
            }
        }
        h.createUpdate("INSERT INTO " + actionsTable + " (session_id, actor_uuid, staff_override, guild_uuid, type, created_at, before_state, after_state) " +
                        "VALUES (:session, :actor, :override, :guild, :type, :now, :before, :after)")
                .bind("session", action.sessionId()).bind("actor", action.actor().uuid() == null ? null : action.actor().uuid().toString())
                .bind("override", action.actor().staffOverride()).bind("guild", action.guildUuid().toString())
                .bind("type", action.type()).bind("now", action.createdAt())
                .bind("before", action.before() == null ? null : action.before().toString())
                .bind("after", action.after() == null ? null : action.after().toString()).execute();
    }

    @Override
    public CompletableFuture<Void> recordAction(GuildAction action)
    {
        return runAsync(() -> write(action, h ->
        {
        }));
    }

    @Override
    public CompletableFuture<List<GuildAction>> actions(Long sessionId, long cutoff)
    {
        return CompletableFuture.supplyAsync(() -> jdbi.withHandle(h ->
        {
            var query = h.createQuery("SELECT * FROM " + actionsTable +
                        " WHERE created_at >= :cutoff AND undone_at IS NULL" + (sessionId == null ? "" : " AND session_id = :session") + " ORDER BY id DESC")
                    .bind("cutoff", cutoff);
            if (sessionId != null)
            {
                query.bind("session", sessionId);
            }
            return query.map((rs, ctx) -> actionMapRow(rs)).list();
        }), executor);
    }

    private static GuildAction actionMapRow(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        return new GuildAction(rs.getLong("id"), rs.getObject("session_id", Long.class),
                new Actor(rs.getString("actor_uuid") == null ? null : UUID.fromString(rs.getString("actor_uuid")), rs.getBoolean("staff_override")),
                UUID.fromString(rs.getString("guild_uuid")), rs.getString("type"), rs.getLong("created_at"),
                rs.getString("before_state") == null ? null : JsonParser.parseString(rs.getString("before_state")),
                rs.getString("after_state") == null ? null : JsonParser.parseString(rs.getString("after_state")), null, null);
    }

    @Override
    public CompletableFuture<List<GuildSession>> sessions(UUID playerUuid, long cutoff)
    {
        return CompletableFuture.supplyAsync(() -> jdbi.withHandle(h -> h.createQuery("SELECT s.*, " +
                        "(SELECT COUNT(*) FROM " + actionsTable + " a WHERE a.session_id = s.id) AS action_count FROM " + sessionsTable +
                        " s WHERE player_uuid = :player AND (ended_at IS NULL OR ended_at >= :cutoff) ORDER BY started_at DESC")
                .bind("player", playerUuid.toString()).bind("cutoff", cutoff)
                .map((rs, ctx) -> new GuildSession(rs.getLong("id"), playerUuid, rs.getString("player_name"),
                        rs.getLong("started_at"), rs.getObject("ended_at", Long.class), rs.getLong("action_count"))).list()), executor);
    }

    @Override
    public CompletableFuture<Long> openSession(UUID playerUuid, String name, long now)
    {
        return CompletableFuture.supplyAsync(() -> jdbi.withHandle(h -> h.createUpdate("INSERT INTO " + sessionsTable +
                        " (player_uuid, player_name, started_at) VALUES (:player, :name, :now)")
                .bind("player", playerUuid.toString()).bind("name", name).bind("now", now)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one()), executor);
    }

    @Override
    public CompletableFuture<Void> closeSession(long id, long now)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + sessionsTable + " SET ended_at = :now WHERE id = :id")
                .bind("now", now).bind("id", id).execute()));
    }

    @Override
    public CompletableFuture<Void> closeOpenSessions(long now)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("UPDATE " + sessionsTable + " SET ended_at = :now WHERE ended_at IS NULL")
                .bind("now", now).execute()));
    }

    @Override
    public CompletableFuture<Void> purgeRecovery(long cutoff)
    {
        return runAsync(() -> jdbi.useTransaction(h ->
        {
            h.createUpdate("DELETE FROM " + actionsTable + " WHERE created_at < :cutoff").bind("cutoff", cutoff).execute();
            h.createUpdate("DELETE FROM " + sessionsTable + " WHERE ended_at < :cutoff AND NOT EXISTS " +
                            "(SELECT 1 FROM " + actionsTable + " WHERE session_id = " + sessionsTable + ".id)")
                    .bind("cutoff", cutoff).execute();
        }));
    }

    private void insertContents(Handle h, Guild guild)
    {
        for (Member member : guild.getMembers())
        {
            insertMember(h, guild.getGuildUuid(), member.getUuid(), member.getRole(), member.getJoinedAt());
        }
        guild.getWarps().forEach((name, location) -> h.createUpdate("INSERT INTO " + warpsTable +
                        " (guild_uuid, name, world, x, y, z, yaw, pitch) VALUES (:g, :n, :w, :x, :y, :z, :yaw, :pitch)")
                .bind("g", guild.getGuildUuid().toString()).bind("n", name).bind("w", location.getWorldName())
                .bind("x", location.getX()).bind("y", location.getY()).bind("z", location.getZ())
                .bind("yaw", location.getYaw()).bind("pitch", location.getPitch()).execute());
        guild.getGuests().values().forEach(guest -> h.createUpdate("INSERT INTO " + guestsTable +
                        " (guild_uuid, player_uuid, editing, expires_at) VALUES (:g, :p, :editing, :expires)")
                .bind("g", guild.getGuildUuid().toString()).bind("p", guest.playerUuid().toString())
                .bind("editing", guest.editing()).bind("expires", guest.expiresAt().toEpochMilli()).execute());
    }

    @Override
    public CompletableFuture<Void> saveGuild(Guild guild, GuildAction action, @Nullable HistoryPurge purge)
    {
        return runAsync(() -> write(action, h ->
        {
            writeGuild(h, guild, false);
            insertHistoryPurge(h, purge);
        }));
    }

    /** Overwrites an active guild, or with restore a pending guild that becomes active. */
    private void writeGuild(Handle h, Guild guild, boolean restore)
    {
        CustomLocation spawn = guild.getSpawn();
        int updated = h.createUpdate("UPDATE " + guildsTable + " SET owner_uuid = :owner, prefix = :prefix, prefix_key = :key, " +
                        "spawn_world = :w, spawn_x = :x, spawn_y = :y, spawn_z = :z, spawn_yaw = :yaw, spawn_pitch = :pitch, " +
                        "time_mode = :time, weather_mode = :weather" +
                        (restore ? ", deleted_at = NULL, deleted_by = NULL WHERE guild_uuid = :g AND deleted_at IS NOT NULL"
                                : " WHERE guild_uuid = :g AND deleted_at IS NULL"))
                .bind("owner", guild.getOwnerUuid().toString()).bind("prefix", guild.getPrefix()).bind("key", guild.getPrefixKey())
                .bind("w", spawn == null ? null : spawn.getWorldName()).bind("x", spawn == null ? null : spawn.getX())
                .bind("y", spawn == null ? null : spawn.getY()).bind("z", spawn == null ? null : spawn.getZ())
                .bind("yaw", spawn == null ? null : spawn.getYaw()).bind("pitch", spawn == null ? null : spawn.getPitch())
                .bind("time", guild.getTimeMode().name()).bind("weather", guild.getWeatherMode().name())
                .bind("g", guild.getGuildUuid().toString()).execute();
        if (updated != 1)
        {
            throw new IllegalStateException(restore ? "The guild is not pending deletion" : "The guild no longer exists");
        }
        for (String table : List.of(membersTable, warpsTable, guestsTable))
        {
            h.createUpdate("DELETE FROM " + table + " WHERE guild_uuid = :g").bind("g", guild.getGuildUuid().toString()).execute();
        }
        insertContents(h, guild);
    }

    private void insertHistoryPurge(Handle h, @Nullable HistoryPurge purge)
    {
        if (purge == null)
        {
            return;
        }
        h.createUpdate("INSERT INTO " + historyPurgesTable + " (world, until_at) VALUES (:w, :until)")
                .bind("w", purge.world()).bind("until", purge.untilAt()).execute();
    }

    @Override
    public CompletableFuture<List<HistoryPurge>> historyPurges()
    {
        return CompletableFuture.supplyAsync(() -> jdbi.withHandle(h -> h.createQuery("SELECT world, MAX(until_at) AS until_at FROM " +
                        historyPurgesTable + " GROUP BY world")
                .map((rs, ctx) -> new HistoryPurge(rs.getString("world"), rs.getLong("until_at"))).list()), executor);
    }

    @Override
    public CompletableFuture<Void> deleteHistoryPurges(String world, long untilAt)
    {
        return runAsync(() -> jdbi.useHandle(h -> h.createUpdate("DELETE FROM " + historyPurgesTable + " WHERE world = :w AND until_at <= :until")
                .bind("w", world).bind("until", untilAt).execute()));
    }

    @Override
    public CompletableFuture<Optional<GuildAction>> disbandAction(UUID guildUuid, long cutoff)
    {
        return CompletableFuture.supplyAsync(() -> jdbi.withHandle(h -> h.createQuery("SELECT * FROM " + actionsTable +
                        " WHERE guild_uuid = :g AND type = 'DISBAND' AND undone_at IS NULL AND created_at >= :cutoff ORDER BY id DESC LIMIT 1")
                .bind("g", guildUuid.toString()).bind("cutoff", cutoff)
                .map((rs, ctx) -> actionMapRow(rs)).findFirst()), executor);
    }

    private CompletableFuture<Void> runAsync(Runnable runnable)
    {
        return CompletableFuture.runAsync(runnable, executor);
    }
}
