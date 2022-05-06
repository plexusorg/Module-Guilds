package dev.plex.guild;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
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
        GUILDS.removeIf(guild -> guild.getOwner().equals(owner));
    }

    public Optional<Guild> getGuild(UUID uuid)
    {
        return GUILDS.stream().filter(guild -> guild.getOwner().equals(uuid) || guild.getMembers().contains(uuid)).findFirst();
    }

}
