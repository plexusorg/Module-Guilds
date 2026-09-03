package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WarpSubCommand extends GuildSubCommand
{
    public WarpSubCommand(Guilds module)
    {
        super(module, command("warp")
                .description("Warps to a specified guild warp")
                .usage("/guild <command> <name>")
                .aliases("goto")
                .permission("plex.guilds.warp")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            String warpName = arguments(first, remaining);
            if (!guild.getWarps().containsKey(warpName.toLowerCase()))
            {
                player.sendMessage(messageComponent("guildWarpNotFound", warpName));
                return;
            }
            player.teleportAsync(guild.getWarps().get(warpName.toLowerCase()).toLocation());
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
