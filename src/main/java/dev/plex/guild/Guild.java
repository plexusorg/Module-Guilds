package dev.plex.guild;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.plex.Guilds;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.util.CustomLocation;
import dev.plex.util.GuildUtil;
import lombok.Data;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Data
public class Guild
{
    private final UUID guildUuid;
    private final ZonedDateTime createdAt;
    private final List<Member> members = Lists.newArrayList();
    private final Map<String, CustomLocation> warps = Maps.newHashMap();
    private String name;
    private UUID ownerUuid;
    private String prefix;
    private String motd;
    private CustomLocation home;
    private boolean tagEnabled = true;
    private boolean isPublic;
    private boolean memberBlockBreaking;
    private boolean memberBlockPlacing;
    private boolean memberInteracting;

    public static Guild create(Player player, String guildName)
    {
        String timezone = Guilds.get().api().configuration().mainConfig().getString("server.timezone", "Etc/UTC");
        Guild guild = new Guild(UUID.randomUUID(), ZonedDateTime.now(ZoneId.of(timezone)));
        guild.setName(PlainTextComponentSerializer.plainText().serialize(GuildUtil.miniMessageWithoutEvents(guildName)));
        guild.setOwnerUuid(player.getUniqueId());
        guild.addMember(new Member(player.getUniqueId(), GuildRole.OWNER));
        return guild;
    }

    public Member getMember(UUID uuid)
    {
        return members.stream().filter(member -> member.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    public void addMember(UUID uuid)
    {
        addMember(new Member(uuid, GuildRole.MEMBER));
    }

    public void addMember(Member member)
    {
        members.removeIf(existing -> existing.getUuid().equals(member.getUuid()));
        members.add(member);
    }

    public boolean isOwner(UUID uuid)
    {
        return ownerUuid != null && ownerUuid.equals(uuid);
    }

    public boolean isMember(UUID uuid)
    {
        return getMember(uuid) != null;
    }

    public void removeMember(UUID uuid)
    {
        members.removeIf(member -> member.getUuid().equals(uuid));
    }

    public List<? extends PlexPlayerView> getPlexPlayers()
    {
        return members.stream()
                .map(Member::getPlexPlayer)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public List<Player> getOnlinePlayers()
    {
        return getPlexPlayers().stream()
                .map(PlexPlayerView::bukkitPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    public String getWorldName()
    {
        return "guild_" + guildUuid.toString().replace("-", "");
    }

    public boolean hasPermission(UUID uuid, GuildPermission permission)
    {
        if (isOwner(uuid))
        {
            return true;
        }
        if (!isMember(uuid))
        {
            return false;
        }
        return switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking;
            case BLOCK_PLACING -> memberBlockPlacing;
            case INTERACTING -> memberInteracting;
        };
    }

    public void setPermission(GuildPermission permission, boolean enabled)
    {
        switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking = enabled;
            case BLOCK_PLACING -> memberBlockPlacing = enabled;
            case INTERACTING -> memberInteracting = enabled;
        }
    }
}
