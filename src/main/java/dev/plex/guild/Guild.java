package dev.plex.guild;

import dev.plex.guild.data.GuildWorldAccess;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

import java.time.Instant;
import java.time.Duration;
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
    public static final Duration INVITE_WINDOW = Duration.ofHours(24);

    private final UUID guildUuid;
    private final ZonedDateTime createdAt;
    private final List<Member> members = new CopyOnWriteArrayList<>();
    private final Map<String, CustomLocation> warps = new ConcurrentHashMap<>();
    // Loaded before publication; subsequent access belongs to the guild mutation queue.
    private final List<Instant> inviteHistory = new CopyOnWriteArrayList<>();
    private String name;
    private volatile UUID ownerUuid;
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private volatile GuildPrefix prefixValue = GuildPrefix.empty();
    private volatile CustomLocation spawn;
    private volatile GuildTimeMode timeMode = GuildTimeMode.CYCLE;
    private volatile GuildWeatherMode weatherMode = GuildWeatherMode.CYCLE;

    private volatile GuildWorldAccess worldAccess = GuildWorldAccess.PRIVATE;

    public static Guild create(UUID ownerUuid, String guildName, ZoneId zoneId)
    {
        Guild guild = new Guild(UUID.randomUUID(), ZonedDateTime.now(zoneId));
        guild.setName(guildName);
        guild.setOwnerUuid(ownerUuid);
        guild.addMember(new Member(ownerUuid, GuildRole.OWNER));
        return guild;
    }

    public void setPrefix(GuildPrefix prefix)
    {
        this.prefixValue = prefix;
    }

    public String getPrefix()
    {
        return prefixValue.text();
    }

    public Component getPrefixComponent()
    {
        return prefixValue.component();
    }

    public String getPrefixKey()
    {
        return prefixValue.key();
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

    /** Returns the member's role, or null when the player is not a member. */
    public GuildRole getRole(UUID uuid)
    {
        Member member = getMember(uuid);
        return member == null ? null : member.getRole();
    }

    public boolean canEnterWorld(UUID uuid)
    {
        return isOwner(uuid) || isMember(uuid) || worldAccess != GuildWorldAccess.PRIVATE;
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
        GuildRole role = getRole(uuid);
        if (role != null)
        {
            return permission != GuildPermission.MANAGE || role != GuildRole.MEMBER;
        }
        return permission != GuildPermission.MANAGE && worldAccess == GuildWorldAccess.PUBLIC_BUILD;
    }

    public boolean canManage(UUID actor)
    {
        return hasPermission(actor, GuildPermission.MANAGE);
    }

    public boolean canKick(UUID actor, UUID target)
    {
        GuildRole actorRole = getRole(actor);
        GuildRole targetRole = getRole(target);
        if (actorRole == null || targetRole == null || actor.equals(target))
        {
            return false;
        }
        return switch (targetRole)
        {
            case MEMBER -> actorRole != GuildRole.MEMBER;
            case OFFICER -> actorRole == GuildRole.OWNER;
            case OWNER -> false;
        };
    }

    /** The owner promotes a member to officer, or an officer to owner. */
    public boolean canPromote(UUID actor, UUID target)
    {
        GuildRole targetRole = getRole(target);
        return getRole(actor) == GuildRole.OWNER && (targetRole == GuildRole.MEMBER || targetRole == GuildRole.OFFICER);
    }

    /** The owner demotes an officer to member. */
    public boolean canDemote(UUID actor, UUID target)
    {
        return getRole(actor) == GuildRole.OWNER && getRole(target) == GuildRole.OFFICER;
    }
}
