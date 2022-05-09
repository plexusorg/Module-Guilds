package dev.plex.guild;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.plex.Plex;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.Rank;
import dev.plex.util.CustomLocation;
import dev.plex.util.minimessage.SafeMiniMessage;
import lombok.Data;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Data
@Entity("guilds")
public class Guild
{
    private final UUID guildUuid;
    private final ZonedDateTime createdAt;
    private final List<Member> members = Lists.newArrayList();
    private final List<UUID> moderators = Lists.newArrayList();
    private final List<Rank> ranks = Lists.newArrayList();
    private final Map<String, CustomLocation> warps = Maps.newHashMap();
    private String name;
    private Member owner;
    private String prefix;
    private String motd;
    private CustomLocation home;
    private boolean tagEnabled;
    private Rank defaultRank = new Rank("default", null);
    private boolean isPublic = false;


    public static Guild create(Player player, String guildName)
    {
        Guild guild = new Guild(UUID.randomUUID(), ZonedDateTime.now(ZoneId.of(Plex.get().config.getString("server.timezone"))));
        guild.setName(PlainTextComponentSerializer.plainText().serialize(SafeMiniMessage.mmDeserialize(guildName)));
        guild.setOwner(new Member(player.getUniqueId()));
        return guild;
    }

    public Member getMember(UUID uuid)
    {
        if (owner.getUuid().equals(uuid))
        {
            return owner;
        }
        return members.stream().filter(m -> m.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    public void addMember(UUID uuid)
    {
        addMember(new Member(uuid));
    }

    public void addMember(Member member)
    {
        this.members.add(member);
    }

    public List<Member> getMembers()
    {
        List<Member> allMembers = Lists.newArrayList(members);
        allMembers.add(owner);
        return allMembers;
    }
}
