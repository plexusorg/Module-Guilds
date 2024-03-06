package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "home", usage = "/guild <command>", description = "Teleport to your guild's home location")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.home")
public class HomeSubCommand extends SubCommand
{
    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresentOrElse(guild ->
                {
                    if (guild.getHome() == null)
                    {
                        send(player, messageComponent("guildHomeNotFound"));
                        return;
                    }

                    player.teleportAsync(guild.getHome());
                    send(player, messageComponent("guildHomeTeleport"));
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
