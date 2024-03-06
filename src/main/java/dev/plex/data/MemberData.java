package dev.plex.data;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.guild.GuildMember;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class MemberData
{
    private final List<GuildMember> members = Lists.newArrayList();

    public void addMember(GuildMember member)
    {
        members.add(member);
    }

    public GuildMember addNewMember(Player player)
    {
        AtomicReference<GuildMember> memberReference = new AtomicReference<>(new GuildMember(player.getUniqueId()));
        Guilds.get().getSqlManager().insertMember(memberReference.get()).whenComplete((m, throwable) ->
        {
            memberReference.set(m);
            addMember(m);
        });
        return memberReference.get();
    }

    public Optional<GuildMember> getMemberByName(String name)
    {
        return members.stream().filter(p -> p.getPlayer().getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<GuildMember> getMemberByUUID(UUID uuid)
    {
        return members.stream().filter(p -> p.getUuid().equals(uuid)).findFirst();
    }

    public Optional<GuildMember> getMemberByID(int id)
    {
        return members.stream().filter(p -> p.getId() == id).findFirst();
    }

    public Optional<GuildMember> getMember(Player player)
    {
        return getMemberByUUID(player.getUniqueId());
    }
}
