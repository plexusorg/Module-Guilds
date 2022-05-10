package dev.plex.command.sub;

import com.google.common.collect.ImmutableList;
import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.guild.data.Member;
import dev.plex.rank.enums.Rank;
import dev.plex.util.PlexLog;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
// TODO: 5/9/2022 5 minute timeout for invites
// TODO: 5/9/2022 deny command maybe?
// TODO: 5/9/2022 deny members from inviting themselves or existing members in the current guild

@CommandParameters(name = "invite", aliases = "inv", usage = "/guild <command> <player name>", description = "Invites a player to the guild")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.invite")
public class InviteSubCommand extends PlexCommand
{
    public InviteSubCommand()
    {
        super(false);
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return usage();
        }
        assert player != null;
        if (args[0].equalsIgnoreCase("accept"))
        {
            if (!GuildHolder.PENDING_INVITES.containsKey(player.getUniqueId()))
            {
                return messageComponent("guildNoInvite");
            }
            String guildName = StringUtils.join(args, " ", 1, args.length);
            GuildHolder.PENDING_INVITES.get(player.getUniqueId()).stream().filter(guild -> guild.getName().equalsIgnoreCase(guildName)).findFirst().ifPresentOrElse(guild ->
            {
                AtomicBoolean continueCheck = new AtomicBoolean(true);
                Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresent(guild1 ->
                {
                    if (guild1.getGuildUuid().equals(guild.getGuildUuid()))
                    {
                        send(player, messageComponent("guildInThis"));
                        continueCheck.set(false);
                        return;
                    }
                    if (guild1.getOwner().getUuid().equals(player.getUniqueId()))
                    {
                        if (guild1.getMembers().size() - 1 > 0)
                        {
                            send(player, messageComponent("guildDisbandNeeded"));
                            continueCheck.set(false);
                            return;
                        } else
                        {
                            Guilds.get().getSqlGuildManager().deleteGuild(guild1.getGuildUuid()).whenComplete((unused, throwable) ->
                            {
                                send(player, messageComponent("guildAutoDisbanded"));
                            });
                        }
                    }
                    guild1.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(player1 ->
                    {
                        send(player1, messageComponent("guildMemberLeft", player.getName()));
                    });
                    guild1.getMembers().removeIf(member -> member.getUuid().equals(player.getUniqueId()));
                });
                if (!continueCheck.get())
                {
                    return;
                }
                GuildHolder.PENDING_INVITES.remove(player.getUniqueId());
                guild.addMember(player.getUniqueId());
                guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(player1 ->
                {
                    send(player1, messageComponent("guildMemberJoined", player.getName()));
                });
            }, () -> send(player, messageComponent("guildNotValidInvite")));
            return null;
        }
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.getOwner().getUuid().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Player target = getNonNullPlayer(args[0]);
            boolean invite = GuildHolder.sendInvite(target.getUniqueId(), guild);
            if (!invite)
            {
                send(player, messageComponent("guildInviteExists"));
                return;
            }
            send(player, messageComponent("guildInviteSent", target.getName()));
            send(target, messageComponent("guildInviteReceived", player.getName(), guild.getName()));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        if (!(sender instanceof Player player)) return ImmutableList.of();
        if (args.length == 0) return ImmutableList.of();
        if (args[0].equalsIgnoreCase("accept") && args.length == 2)
        {
            if (!GuildHolder.PENDING_INVITES.containsKey(player.getUniqueId()))
            {
                return ImmutableList.of();
            }
            PlexLog.debug("Tab Completing moment");
            return GuildHolder.PENDING_INVITES.get(player.getUniqueId()).stream().map(Guild::getName).collect(Collectors.toList());
        }
        return ImmutableList.of();
    }
}
