package dev.plex.guild;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.plex.Plex;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.Rank;
import dev.plex.util.CustomLocation;
import lombok.Data;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Data
@Entity("guilds")
public class Guild
{
    private final UUID guildUuid;
    private final String name;
    private final Member owner;
    private final ZonedDateTime createdAt;
    private transient final List<UUID> outgoingInvitations = Lists.newArrayList();
    private final List<Member> members = Lists.newArrayList();
    private final List<UUID> moderators = Lists.newArrayList();
    private final List<Rank> ranks = Lists.newArrayList();
    private final Map<String, CustomLocation> warps = Maps.newHashMap();
    private String prefix;
    private String motd;
    private CustomLocation home;
    private boolean tagEnabled;
    private Rank defaultRank = new Rank("default", null);
    private boolean isPublic = false;


    public static Guild create(Player player, String guildName)
    {
        return new Guild(UUID.randomUUID(), guildName, new Member(player.getUniqueId()), ZonedDateTime.now(ZoneId.of(Plex.get().config.getString("server.timezone"))));
    }

    public Member getMember(UUID uuid)
    {
        return owner.getUuid().equals(uuid) ? owner : members.stream().filter(member -> member.getUuid().equals(uuid)).findFirst().get();
    }

    public List<Member> getMembers()
    {
        List<Member> allMembers = Lists.newArrayList(members);
        allMembers.add(owner);
        return allMembers;
    }
}
