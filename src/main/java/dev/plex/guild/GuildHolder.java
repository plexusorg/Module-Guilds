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
    private final Map<UUID, UUID> guildByPlayer = new ConcurrentHashMap<>();

    public void replaceAll(Collection<Guild> guilds)
    {
        guildsById.clear();
        guildByPlayer.clear();
        guilds.forEach(this::addGuild);
    }

    public Optional<Guild> guild(UUID playerUuid)
    {
        UUID guildUuid = guildByPlayer.get(playerUuid);
        return guildUuid == null ? Optional.empty() : Optional.ofNullable(guildsById.get(guildUuid));
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
        guild.getMembers().stream().map(Member::getUuid).forEach(playerUuid -> indexMember(guild.getGuildUuid(), playerUuid));
    }

    public void removeGuild(UUID guildUuid)
    {
        Guild removed = guildsById.remove(guildUuid);
        if (removed != null)
        {
            removed.getMembers().stream().map(Member::getUuid).forEach(guildByPlayer::remove);
        }
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
        guildByPlayer.clear();
    }

    public Optional<Guild> getGuild(UUID uuid)
    {
        return guild(uuid);
    }

    public Collection<Guild> getGuilds()
    {
        return guilds();
    }
}
