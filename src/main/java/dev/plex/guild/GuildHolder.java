package dev.plex.guild;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.plex.guild.data.Member;

import java.util.*;

public class GuildHolder
{
    public static final Map<UUID, List<Guild>> PENDING_INVITES = Maps.newHashMap();
    private static final List<Guild> GUILDS = Lists.newArrayList();

    public static boolean sendInvite(UUID uuid, Guild guild)
    {
        if (PENDING_INVITES.containsKey(uuid) && PENDING_INVITES.get(uuid).stream().anyMatch(guild1 -> guild1.getGuildUuid().equals(guild.getGuildUuid())))
        {
            return false;
        }
        if (PENDING_INVITES.containsKey(uuid))
        {
            PENDING_INVITES.get(uuid).add(guild);
        }
        else
        {
            PENDING_INVITES.put(uuid, Lists.newArrayList(guild));
        }
        return true;
    }

    public void addGuild(Guild guild)
    {
        GUILDS.add(guild);
    }

    public void deleteGuild(UUID owner)
    {
        GUILDS.removeIf(guild -> guild.getOwner().getUuid().equals(owner));
    }

    public Optional<Guild> getGuild(UUID uuid)
    {
        return GUILDS.stream().filter(guild -> (guild.getOwner() != null && guild.getOwner().getUuid().equals(uuid)) || guild.getMembers().stream().map(Member::getUuid).toList().contains(uuid)).findFirst();
    }

    public Collection<Guild> getGuilds()
    {
        return GUILDS.stream().toList();
    }

}
