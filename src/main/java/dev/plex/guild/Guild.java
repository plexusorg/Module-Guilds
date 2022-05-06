package dev.plex.guild;

import com.google.common.collect.Lists;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.plex.Plex;
import dev.plex.util.CustomLocation;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Entity("guilds")
public class Guild
{
    private final String name;
    private final UUID owner;
    private final ZonedDateTime createdAt;
    @Transient
    private final List<UUID> outgoingInvitations = Lists.newArrayList();
    private final List<UUID> members = Lists.newArrayList();
    private final List<UUID> moderators = Lists.newArrayList();
    private String prefix;
    private String motd;
    private CustomLocation home;
    private boolean tagEnabled;

    public static Guild create(Player player, String guildName)
    {
        return new Guild(guildName, player.getUniqueId(), ZonedDateTime.now(ZoneId.of(Plex.get().config.getString("server.timezone"))));
    }
}
