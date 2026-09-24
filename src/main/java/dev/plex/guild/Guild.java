package dev.plex.guild;

import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import lombok.Data;

import java.time.Instant;
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
    private final Map<UUID, Guest> guests = new ConcurrentHashMap<>();
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
    private volatile boolean memberManageGuests;

    public static Guild create(UUID ownerUuid, String guildName, ZoneId zoneId)
    {
        Guild guild = new Guild(UUID.randomUUID(), ZonedDateTime.now(zoneId));
        guild.setName(guildName);
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

    public Guest getActiveGuest(UUID uuid)
    {
        Guest guest = guests.get(uuid);
        return guest != null && guest.isActive(Instant.now()) ? guest : null;
    }

    public boolean canEnterWorld(UUID uuid)
    {
        return isOwner(uuid) || isMember(uuid) || getActiveGuest(uuid) != null;
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
            Guest guest = getActiveGuest(uuid);
            return permission != GuildPermission.MANAGE_GUESTS && guest != null && guest.editing();
        }
        return switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking;
            case BLOCK_PLACING -> memberBlockPlacing;
            case INTERACTING -> memberInteracting;
            case MANAGE_GUESTS -> memberManageGuests;
        };
    }

    public void setPermission(GuildPermission permission, boolean enabled)
    {
        switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking = enabled;
            case BLOCK_PLACING -> memberBlockPlacing = enabled;
            case INTERACTING -> memberInteracting = enabled;
            case MANAGE_GUESTS -> memberManageGuests = enabled;
        }
    }

    public boolean isMemberPermissionEnabled(GuildPermission permission)
    {
        return switch (permission)
        {
            case BLOCK_BREAKING -> memberBlockBreaking;
            case BLOCK_PLACING -> memberBlockPlacing;
            case INTERACTING -> memberInteracting;
            case MANAGE_GUESTS -> memberManageGuests;
        };
    }
}
