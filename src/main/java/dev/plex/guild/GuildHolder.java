package dev.plex.guild;

import com.google.common.collect.Lists;
import dev.plex.guild.data.Member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GuildHolder
{
    private static final List<Guild> GUILDS = Lists.newArrayList();

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
        return GUILDS.stream().filter(guild -> (guild.getOwner() != null &&  guild.getOwner().getUuid().equals(uuid)) || guild.getMembers().stream().map(Member::getUuid).toList().contains(uuid)).findFirst();
    }

    public Collection<Guild> getGuilds()
    {
        return GUILDS.stream().toList();
    }

}
