package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WarpSubCommand extends SimplePlexCommand
{
    public WarpSubCommand()
    {
        super(command("warp")
                .description("Warps to a specified guild warp")
                .usage("/guild <command> <name>")
                .aliases("goto")
                .permission("plex.guilds.warp")
                .source(RequiredCommandSource.IN_GAME)
                .build());
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
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
