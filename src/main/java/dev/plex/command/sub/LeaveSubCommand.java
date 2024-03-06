package dev.plex.command.sub;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "leave", usage = "/guild <command>", description = "Leave the guild you are currently in")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.leave")
public class LeaveSubCommand extends SubCommand
{
    private final List<CommandSender> confirm = Lists.newArrayList();

    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresentOrElse(guild ->
                {
                    if (guild.isOwner(member) && guild.getMembers().size() > 1)
                    {
                        send(player, messageComponent("guildDisbandNeeded"));
                        return;
                    }

                    if (!confirm.contains(sender))
                    {
                        confirm.add(sender);
                        Bukkit.getScheduler().runTaskLater(Guilds.get().getPlex(), () -> confirm.remove(sender), 20 * 10);
                        send(player, messageComponent("guildActionConfirmation", "leave"));
                        return;
                    }

                    confirm.remove(sender);

                    if (guild.isOwner(member) && guild.getMembers().size() == 1)
                    {
                        Guilds.get().getGuildData().deleteGuild(member);
                        send(player, messageComponent("guildAutoDisbanded"));
                        return;
                    }

                    guild.removeMember(member);
                    send(player, messageComponent("guildLeft"));
                    guild.getMembers().stream().map(GuildMember::getPlayer).filter(OfflinePlayer::isOnline).map(OfflinePlayer::getPlayer).forEach(p -> send(p, messageComponent("guildMemberLeft", player.getName())));
                },
                () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
