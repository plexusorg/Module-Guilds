package dev.plex.guild;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.util.PlexUtils;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class Guild
{
    private final UUID uuid;
    private final Date createdAt;
    private String name;
    private String displayName = null;
    private GuildMember owner;
    private List<GuildMember> members = Lists.newArrayList();
    private List<GuildMember> moderators = Lists.newArrayList();
    private List<GuildRank> ranks = Lists.newArrayList();
    private List<GuildWarp> warps = Lists.newArrayList();
    private GuildRank defaultRank = null;
    private String prefix = null;
    private boolean prefixEnabled = false;
    private String motd = null;
    private Location home;
    private Privacy privacy = Privacy.PUBLIC;
    private List<GuildMember> pendingInvites = Lists.newArrayList();

    public static Guild create(GuildMember member, String name)
    {
        Guild guild = new Guild(UUID.randomUUID(), new Date());
        guild.setName(name);
        guild.setOwner(member);
        Guilds.get().getGuildData().addNewGuild(guild);
        return guild;
    }

    public void chat(Player player, Component message)
    {
        getMembers().stream().map(GuildMember::getPlayer).filter(OfflinePlayer::isOnline).map(OfflinePlayer::getPlayer).forEach(p -> p.sendMessage(PlexUtils.messageComponent("guildChatMessage", player.getName(), message)));
        if (Guilds.get().getConfig().getBoolean("guilds.log-chat-message"))
        {
            Bukkit.getConsoleSender().sendMessage(PlexUtils.messageComponent("guildChatConsoleLog", name, uuid, player.getName(), PlainTextComponentSerializer.plainText().serialize(message)));
        }
    }

    public String getDisplayName()
    {
        return displayName != null ? displayName : name;
    }

    public boolean isOwner(GuildMember member)
    {
        return owner.equals(member);
    }

    public void setOwner(GuildMember member)
    {
        members.add(owner);
        members.remove(member);
        owner = member;
    }

    public boolean isMember(GuildMember member)
    {
        return getMembers().contains(member);
    }

    public void addMember(GuildMember member)
    {
        members.add(member);
    }

    public void removeMember(GuildMember member)
    {
        members.remove(member);
        moderators.remove(member);
    }

    public void setMemberRank(GuildMember member, GuildRank rank)
    {
        rank.addMember(member);
    }

    public boolean isModerator(GuildMember member)
    {
        return moderators.contains(member) || owner.equals(member);
    }

    public void addModerator(GuildMember member)
    {
        moderators.add(member);
    }

    public void removeModerator(GuildMember member)
    {
        moderators.remove(member);
    }

    public void createRank(String name)
    {
        GuildRank rank = new GuildRank(name);
        Guilds.get().getSqlManager().insertRank(this, rank);
        ranks.add(rank);
    }

    public void deleteRank(GuildRank rank)
    {
        if (rank.equals(defaultRank))
        {
            defaultRank = null;
        }

        Guilds.get().getSqlManager().deleteRank(this, rank);
        ranks.remove(rank);
    }

    public void setDefaultRank(GuildRank rank)
    {
        Guilds.get().getSqlManager().updateDefaultRank(this, rank);
        defaultRank = rank;
    }

    public List<GuildRank> getRanks()
    {
        List<GuildRank> tempRanks = Lists.newArrayList();
        if (defaultRank != null)
        {
            tempRanks.add(defaultRank);
        }
        return tempRanks;
    }

    public Optional<GuildRank> getRankByName(String name)
    {
        return ranks.stream().filter(rank -> rank.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<GuildRank> getRankByMember(GuildMember member)
    {
        return ranks.stream().filter(rank -> rank.getMembers().contains(member)).findFirst().or(() -> Optional.of(defaultRank));
    }

    public List<String> getRankNames()
    {
        return getRanks().stream().map(GuildRank::getName).toList();
    }

    public void createWarp(String name, Location location)
    {
        GuildWarp warp = new GuildWarp(name, location);
        Guilds.get().getSqlManager().insertWarp(this, warp);
        warps.add(warp);
    }

    public void deleteWarp(GuildWarp warp)
    {
        if (warps.removeIf(w -> w.getName().equals(warp.getName())))
        {
            Guilds.get().getSqlManager().deleteWarp(this, warp);
        }
    }

    public Optional<GuildWarp> getWarp(String name)
    {
        return warps.stream().filter(warp -> warp.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Component getWarps()
    {
        List<Component> components = Lists.newArrayList();
        getWarpNames().forEach(s -> components.add(PlexUtils.mmDeserialize("<click:suggest_command:/guild warp " + s + ">" + s)));
        Component parent = PlexUtils.mmDeserialize("<gold>Warps (" + warps.size() + "): ");
        for (int i = 0; i < components.size(); i++)
        {
            parent = parent.append(components.get(i));
            if (i < components.size() - 1)
            {
                parent = parent.append(PlexUtils.mmDeserialize(", "));
            }
        }
        return parent;
    }

    public List<String> getWarpNames()
    {
        return warps.stream().map(GuildWarp::getName).toList();
    }

    public List<GuildMember> getMembers()
    {
        List<GuildMember> temp = Lists.newArrayList(members);
        temp.add(owner);
        return temp;
    }

    public List<String> getMemberNames()
    {
        return members.stream().map(p -> p.getPlayer().getName()).toList();
    }

    public List<Integer> getMemberIDs()
    {
        return members.stream().map(GuildMember::getId).collect(Collectors.toList());
    }

    public List<String> getModeratorNames()
    {
        return moderators.stream().map(p -> p.getPlayer().getName()).toList();
    }

    public List<Integer> getModeratorIDs()
    {
        return moderators.stream().map(GuildMember::getId).toList();
    }

    public enum Privacy
    {
        PUBLIC, PRIVATE, INVITE_ONLY
    }
}
