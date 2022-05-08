package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.rank.enums.Rank;
import dev.plex.util.CustomLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "warp", aliases = "goto", usage = "/guild <command> <name>")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.warp")
public class WarpSubCommand extends PlexCommand
{
    public WarpSubCommand()
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
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild -> {
            if (!guild.getWarps().containsKey(args[0].toLowerCase()))
            {
                send(player, messageComponent("guildWarpNotFound", args[0]));
                return;
            }
            player.teleportAsync(guild.getWarps().get(args[0].toLowerCase()).toLocation());
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}