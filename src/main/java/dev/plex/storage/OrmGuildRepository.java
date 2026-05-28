package dev.plex.storage;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import dev.plex.Guilds;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.storage.entity.GuildEntity;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.storage.entity.GuildMemberEntity;
import dev.plex.storage.entity.GuildWarpEntity;
import dev.plex.util.CustomLocation;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class OrmGuildRepository implements GuildRepository
{
    private final ModuleStorage moduleStorage;
    private final Executor executor;
    private final Dao<GuildEntity, String> guilds;
    private final Dao<GuildMemberEntity, Long> members;
    private final Dao<GuildWarpEntity, Long> warps;
    private final Dao<GuildInviteEntity, Long> invites;

    public OrmGuildRepository(ModuleStorage moduleStorage)
    {
        this.moduleStorage = moduleStorage;
        this.executor = Guilds.get().api().scheduler().asyncExecutor();
        try
        {
            this.guilds = moduleStorage.orm().dao(GuildEntity.class, "guilds");
            this.members = moduleStorage.orm().dao(GuildMemberEntity.class, "members");
            this.warps = moduleStorage.orm().dao(GuildWarpEntity.class, "warps");
            this.invites = moduleStorage.orm().dao(GuildInviteEntity.class, "invites");
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to create guild DAOs", e);
        }
    }

    @Override
    public CompletableFuture<List<Guild>> loadGuilds()
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return guilds.queryForAll().stream().map(this::toGuild).toList();
            }
            catch (SQLException e)
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
                moduleStorage.transaction(() ->
                {
                    guilds.create(toEntity(guild));
                    members.create(memberEntity(guild.getGuildUuid(), owner.getUniqueId(), GuildRole.OWNER));
                    return null;
                });
                return guild;
            }
            catch (SQLException e)
            {
                throw new IllegalStateException("Failed to create guild", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteGuild(UUID guildUuid)
    {
        return runAsync(() -> moduleStorage.transaction(() ->
        {
            deleteByGuild(members, guildUuid);
            deleteByGuild(warps, guildUuid);
            deleteByGuild(invites, guildUuid);
            guilds.deleteById(guildUuid.toString());
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role)
    {
        return runAsync(() -> upsertMember(guildUuid, playerUuid, role));
    }

    @Override
    public CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid)
    {
        return runAsync(() ->
        {
            DeleteBuilder<GuildMemberEntity, Long> delete = members.deleteBuilder();
            delete.where().eq("guild_uuid", guildUuid.toString()).and().eq("player_uuid", playerUuid.toString());
            delete.delete();
        });
    }

    @Override
    public CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid)
    {
        return runAsync(() -> moduleStorage.transaction(() ->
        {
            GuildEntity guild = guilds.queryForId(guildUuid.toString());
            if (guild != null)
            {
                guild.setOwnerUuid(newOwnerUuid.toString());
                guilds.update(guild);
            }
            upsertMember(guildUuid, oldOwnerUuid, GuildRole.MEMBER);
            upsertMember(guildUuid, newOwnerUuid, GuildRole.OWNER);
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> updatePrefix(UUID guildUuid, String prefix)
    {
        return runAsync(() ->
        {
            GuildEntity guild = guilds.queryForId(guildUuid.toString());
            if (guild != null)
            {
                guild.setPrefix(prefix);
                guilds.update(guild);
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateHome(UUID guildUuid, CustomLocation home)
    {
        return runAsync(() ->
        {
            GuildEntity guild = guilds.queryForId(guildUuid.toString());
            if (guild != null)
            {
                setHome(guild, home);
                guilds.update(guild);
            }
        });
    }

    @Override
    public CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location)
    {
        return runAsync(() ->
        {
            GuildWarpEntity entity = warps.queryBuilder().where()
                    .eq("guild_uuid", guildUuid.toString()).and().eq("name", name.toLowerCase(Locale.ROOT))
                    .queryForFirst();
            if (entity == null)
            {
                entity = new GuildWarpEntity();
                entity.setGuildUuid(guildUuid.toString());
                entity.setName(name.toLowerCase(Locale.ROOT));
            }
            setWarpLocation(entity, location);
            warps.createOrUpdate(entity);
        });
    }

    @Override
    public CompletableFuture<Void> deleteWarp(UUID guildUuid, String name)
    {
        return runAsync(() ->
        {
            DeleteBuilder<GuildWarpEntity, Long> delete = warps.deleteBuilder();
            delete.where().eq("guild_uuid", guildUuid.toString()).and().eq("name", name.toLowerCase(Locale.ROOT));
            delete.delete();
        });
    }

    @Override
    public CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt)
    {
        return runAsync(() ->
        {
            deleteInviteSync(guildUuid, inviteeUuid);
            GuildInviteEntity invite = new GuildInviteEntity();
            invite.setGuildUuid(guildUuid.toString());
            invite.setInviterUuid(inviterUuid.toString());
            invite.setInviteeUuid(inviteeUuid.toString());
            invite.setExpiresAt(expiresAt.toEpochMilli());
            invites.create(invite);
        });
    }

    @Override
    public CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid)
    {
        return runAsync(() -> deleteInviteSync(guildUuid, inviteeUuid));
    }

    @Override
    public CompletableFuture<List<GuildInviteEntity>> invitesFor(UUID inviteeUuid)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                long now = Instant.now().toEpochMilli();
                return invites.queryForEq("invitee_uuid", inviteeUuid.toString()).stream()
                        .filter(invite -> invite.getExpiresAt() >= now)
                        .toList();
            }
            catch (SQLException e)
            {
                throw new IllegalStateException("Failed to load guild invites", e);
            }
        }, executor);
    }

    private Guild toGuild(GuildEntity entity)
    {
        String timezone = Guilds.get().api().configuration().mainConfig().getString("server.timezone", "Etc/UTC");
        Guild guild = new Guild(UUID.fromString(entity.getGuildUuid()), ZonedDateTime.ofInstant(Instant.ofEpochMilli(entity.getCreatedAt()), ZoneId.of(timezone)));
        guild.setName(entity.getName());
        guild.setOwnerUuid(UUID.fromString(entity.getOwnerUuid()));
        guild.setPrefix(entity.getPrefix());
        guild.setMotd(entity.getMotd());
        guild.setTagEnabled(entity.isTagEnabled());
        guild.setPublic(entity.isPublicGuild());
        guild.setHome(toLocation(entity));
        try
        {
            members.queryForEq("guild_uuid", entity.getGuildUuid()).forEach(member -> guild.addMember(toMember(member)));
            warps.queryForEq("guild_uuid", entity.getGuildUuid()).forEach(warp -> guild.getWarps().put(warp.getName(), toLocation(warp)));
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to load guild relations", e);
        }
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

    private void upsertMember(UUID guildUuid, UUID playerUuid, GuildRole role) throws SQLException
    {
        GuildMemberEntity entity = members.queryBuilder().where()
                .eq("guild_uuid", guildUuid.toString()).and().eq("player_uuid", playerUuid.toString())
                .queryForFirst();
        if (entity == null)
        {
            members.create(memberEntity(guildUuid, playerUuid, role));
            return;
        }
        entity.setRole(role.name());
        members.update(entity);
    }

    private void deleteInviteSync(UUID guildUuid, UUID inviteeUuid) throws SQLException
    {
        DeleteBuilder<GuildInviteEntity, Long> delete = invites.deleteBuilder();
        delete.where().eq("guild_uuid", guildUuid.toString()).and().eq("invitee_uuid", inviteeUuid.toString());
        delete.delete();
    }

    private <T> void deleteByGuild(Dao<T, Long> dao, UUID guildUuid) throws SQLException
    {
        DeleteBuilder<T, Long> delete = dao.deleteBuilder();
        delete.where().eq("guild_uuid", guildUuid.toString());
        delete.delete();
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

    private CompletableFuture<Void> runAsync(SqlRunnable runnable)
    {
        return CompletableFuture.runAsync(() ->
        {
            try
            {
                runnable.run();
            }
            catch (SQLException e)
            {
                throw new IllegalStateException("Guild storage operation failed", e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlRunnable
    {
        void run() throws SQLException;
    }
}
