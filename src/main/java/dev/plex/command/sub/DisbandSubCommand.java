package dev.plex.command.sub;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "disband", usage = "/guild <command>", description = "Disband your guild")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.disband")
public class DisbandSubCommand extends SubCommand
{
    private final List<CommandSender> confirm = Lists.newArrayList();

    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        if (member.getGuild().isEmpty())
        {
            return messageComponent("guildNotFound");
        }

        Guild guild = member.getGuild().get();
        if (!guild.isOwner(member))
        {
            return messageComponent("guildNotOwner");
        }

        if (!confirm.contains(sender))
        {
            confirm.add(sender);
            Bukkit.getScheduler().runTaskLater(Guilds.get().getPlex(), () -> confirm.remove(sender), 20 * 10);
            return messageComponent("guildActionConfirmation", "disband");
        }

        Guilds.get().getGuildData().deleteGuild(member);
        confirm.remove(sender);
        return messageComponent("guildDisbanded");
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
