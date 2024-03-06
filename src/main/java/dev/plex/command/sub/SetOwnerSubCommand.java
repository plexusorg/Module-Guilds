package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.GuildMember;
import dev.plex.util.PlexUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "setowner", usage = "/guild <command> <player>", description = "Transfer the ownership of your guild to another player")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.setowner")
public class SetOwnerSubCommand extends SubCommand
{
    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        if (args.length != 1)
        {
            return usage();
        }

        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresentOrElse(guild ->
                {
                    if (!guild.isOwner(member))
                    {
                        send(player, messageComponent("guildNotOwner"));
                        return;
                    }

                    Player target = getNonNullPlayer(args[0]);
                    GuildMember targetMember = Guilds.get().getMemberData().getMember(target).orElseThrow();
                    if (targetMember.getGuild().isEmpty() || !targetMember.getGuild().get().equals(guild))
                    {
                        send(player, messageComponent("guildMemberNotFound"));
                        return;
                    }

                    guild.setOwner(targetMember);
                    send(player, messageComponent("guildOwnerSet", target.getName()));
                },
                () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1 && silentCheckPermission(sender, getPermission()))
        {
            return PlexUtils.getPlayerNameList();
        }
        return Collections.emptyList();
    }
}
