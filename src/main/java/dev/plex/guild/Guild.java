package dev.plex.guild;

import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import dev.plex.util.GuildUtil;
import lombok.Data;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class Guild
{
    private final UUID guildUuid;
    private final ZonedDateTime createdAt;
    private final List<Member> members = new CopyOnWriteArrayList<>();
    private final Map<String, CustomLocation> warps = new ConcurrentHashMap<>();
    private String name;
    private volatile UUID ownerUuid;
    private volatile String prefix;
    private String motd;
    private volatile CustomLocation home;
    private boolean tagEnabled = true;
    private boolean isPublic;
    private volatile boolean memberBlockBreaking;
    private volatile boolean memberBlockPlacing;
    private volatile boolean memberInteracting;

    public static Guild create(UUID ownerUuid, String guildName, ZoneId zoneId)
    {
        Guild guild = new Guild(UUID.randomUUID(), ZonedDateTime.now(zoneId));
        guild.setName(PlainTextComponentSerializer.plainText().serialize(GuildUtil.miniMessageWithoutEvents(guildName)));
        guild.setOwnerUuid(ownerUuid);
        guild.addMember(new Member(ownerUuid, GuildRole.OWNER));
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

    public boolean isMemberPermissionEnabled(GuildPermission permission)
    {
        return switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking;
            case BLOCK_PLACING -> memberBlockPlacing;
            case INTERACTING -> memberInteracting;
        };
    }
}
