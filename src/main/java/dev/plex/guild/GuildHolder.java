package dev.plex.guild;

import dev.plex.guild.data.Member;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuildHolder
{
    private final Map<UUID, Guild> guildsById = new ConcurrentHashMap<>();
    private final Map<String, Guild> guildsByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> guildByPlayer = new ConcurrentHashMap<>();
    // Pending guilds stay out of the active maps, so active lookups never return one.
    private final Map<UUID, PendingGuild> pendingById = new ConcurrentHashMap<>();

    public void replaceAll(Collection<Guild> guilds, Collection<PendingGuild> pending)
    {
        clear();
        guilds.forEach(this::addGuild);
        pending.forEach(this::addPending);
    }

    public Optional<Guild> guild(UUID playerUuid)
    {
        UUID guildUuid = guildByPlayer.get(playerUuid);
        return guildUuid == null ? Optional.empty() : Optional.ofNullable(guildsById.get(guildUuid));
    }

    public Optional<Guild> guildByWorld(String worldName)
    {
        return Optional.ofNullable(guildsByWorld.get(worldName));
    }

    public Optional<Guild> guildById(UUID guildUuid)
    {
        return Optional.ofNullable(guildsById.get(guildUuid));
    }

    public Optional<Guild> guildByName(String name)
    {
        return guildsById.values().stream().filter(guild -> guild.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Collection<Guild> guilds()
    {
        return guildsById.values();
    }

    public void addGuild(Guild guild)
    {
        guildsById.put(guild.getGuildUuid(), guild);
        guildsByWorld.put(guild.getWorldName(), guild);
        guild.getMembers().stream().map(Member::getUuid).forEach(playerUuid -> indexMember(guild.getGuildUuid(), playerUuid));
    }

    public void removeGuild(UUID guildUuid)
    {
        Guild removed = guildsById.remove(guildUuid);
        if (removed != null)
        {
            guildsByWorld.remove(removed.getWorldName(), removed);
            removed.getMembers().stream().map(Member::getUuid).forEach(guildByPlayer::remove);
        }
    }

    public void addPending(PendingGuild pending)
    {
        pendingById.put(pending.guild().getGuildUuid(), pending);
    }

    public void removePending(UUID guildUuid)
    {
        pendingById.remove(guildUuid);
    }

    public Collection<PendingGuild> pending()
    {
        return pendingById.values();
    }

    public Optional<PendingGuild> pendingById(UUID guildUuid)
    {
        return Optional.ofNullable(pendingById.get(guildUuid));
    }

    public Optional<PendingGuild> pendingByName(String name)
    {
        return pendingById.values().stream().filter(pending -> pending.guild().getName().equalsIgnoreCase(name)).findFirst();
    }

    /** A pending guild keeps its name until finalization. */
    public boolean isNameTaken(String name)
    {
        return guildByName(name).isPresent() || pendingByName(name).isPresent();
    }

    public void indexMember(UUID guildUuid, UUID playerUuid)
    {
        guildByPlayer.put(playerUuid, guildUuid);
    }

    public void unindexMember(UUID playerUuid)
    {
        guildByPlayer.remove(playerUuid);
    }

    public void clear()
    {
        guildsById.clear();
        guildsByWorld.clear();
        guildByPlayer.clear();
        pendingById.clear();
    }

}
