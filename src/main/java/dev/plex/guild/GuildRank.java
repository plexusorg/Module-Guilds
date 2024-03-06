package dev.plex.guild;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class GuildRank
{
    private final String name;
    private List<GuildMember> members = Lists.newArrayList();

    public void addMember(GuildMember member)
    {
        members.add(member);
    }

    public List<String> getMemberNames()
    {
        return getMembers().stream().map(p -> p.getPlayer().getName()).collect(Collectors.toList());
    }

    public List<Integer> getMemberIDs()
    {
        return getMembers().stream().map(GuildMember::getId).collect(Collectors.toList());
    }
}
