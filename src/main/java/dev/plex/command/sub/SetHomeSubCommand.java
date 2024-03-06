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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "sethome", usage = "/guild <command> [clear]", description = "Set or clear your guild's home location")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.sethome")
public class SetHomeSubCommand extends SubCommand
{
    @Override
    public Component run(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresentOrElse(guild ->
                {
                    if (!guild.isOwner(member))
                    {
                        send(player, messageComponent("guildNotOwner"));
                        return;
                    }

                    if (args.length == 1 && args[0].equalsIgnoreCase("clear"))
                    {
                        if (guild.getHome() == null)
                        {
                            send(player, messageComponent("guildHomeNotFound"));
                            return;
                        }

                        guild.setHome(null);
                        send(player, messageComponent("guildHomeRemoved"));
                        return;
                    }

                    guild.setHome(player.getLocation());
                    send(player, messageComponent("guildHomeSet"));
                },
                () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1 && silentCheckPermission(sender, getPermission()))
        {
            return Collections.singletonList("clear");
        }
        return Collections.emptyList();
    }
}
