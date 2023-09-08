package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.CustomLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "sethome", aliases = "setspawn", usage = "/guild <command>", description = "Sets the guild's home")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.sethome")
public class SetHomeSubCommand extends PlexCommand
{
    public SetHomeSubCommand()
    {
        super(false);
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.getOwner().getUuid().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("unset") || args[0].equalsIgnoreCase("clear")))
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
            guild.setHome(CustomLocation.fromLocation(player.getLocation()));
            send(player, messageComponent("guildHomeSet"));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
