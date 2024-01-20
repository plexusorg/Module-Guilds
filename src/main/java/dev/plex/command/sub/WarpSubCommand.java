package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "warp", aliases = "goto", usage = "/guild <command> <name>", description = "Warps to a specified guild warp")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.warp")
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
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            String warpName = StringUtils.join(args, " ");
            if (!guild.getWarps().containsKey(warpName.toLowerCase()))
            {
                send(player, messageComponent("guildWarpNotFound", warpName));
                return;
            }
            player.teleportAsync(guild.getWarps().get(warpName.toLowerCase()).toLocation());
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
