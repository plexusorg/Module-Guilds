package dev.plex.data;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
public class GuildData
{
    private final List<Guild> guilds = Lists.newArrayList();

    public void addGuild(Guild guild)
    {
        guilds.add(guild);
    }

    public void addNewGuild(Guild guild)
    {
        Guilds.get().getSqlManager().insertGuild(guild);
        addGuild(guild);
    }

    public void deleteGuild(GuildMember player)
    {
        if (guilds.removeIf(g -> g.getOwner().getUuid().equals(player.getUuid())))
        {
            Guilds.get().getSqlManager().deleteGuild(player.getPlayer().getPlayer());
        }
    }

    public Optional<Guild> getGuildByName(String name)
    {
        return guilds.stream().filter(g -> g.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<Guild> getGuildByMember(GuildMember member)
    {
        return guilds.stream().filter(g -> g.getMembers().contains(member)).findFirst();
    }
}
