package dev.plex.guild;

import com.google.common.collect.Lists;
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
import java.util.List;
import java.util.UUID;

@Data
@Entity("guilds")
public class Guild
{
    private final UUID guildUuid;
    private final String name;
    private final UUID owner;
    private final ZonedDateTime createdAt;
    private transient final List<UUID> outgoingInvitations = Lists.newArrayList();
    private final List<Member> members = Lists.newArrayList();
    private final List<UUID> moderators = Lists.newArrayList();
    private final List<Rank> ranks = Lists.newArrayList();
    private String prefix;
    private String motd;
    private CustomLocation home;
    private boolean tagEnabled;
    private Rank defaultRank = new Rank("default", null);

    public static Guild create(Player player, String guildName)
    {
        return new Guild(UUID.randomUUID(), guildName, player.getUniqueId(), ZonedDateTime.now(ZoneId.of(Plex.get().config.getString("server.timezone"))));
    }
}
