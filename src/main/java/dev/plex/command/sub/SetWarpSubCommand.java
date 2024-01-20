package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.CustomLocation;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "setwarp", aliases = "makewarp,createwarp", usage = "/guild <command> <name>", description = "Creates a new warp at player's location with a specified name")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.setwarp")
public class SetWarpSubCommand extends PlexCommand
{
    public SetWarpSubCommand()
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
            if (!guild.getOwner().getUuid().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            String warpName = StringUtils.join(args, " ");
            if (warpName.length() > 16)
            {
                send(player, mmString("<red>The max length of a warp name is 16 characters!"));
                return;
            }
            if (guild.getWarps().containsKey(warpName.toLowerCase()))
            {
                send(player, messageComponent("guildWarpExists", warpName));
                return;
            }
            if (!StringUtils.isAlphanumericSpace(warpName.toLowerCase(Locale.ROOT)))
            {
                send(player, messageComponent("guildWarpAlphanumeric"));
                return;
            }
            guild.getWarps().put(warpName.toLowerCase(), CustomLocation.fromLocation(player.getLocation()));
            send(player, messageComponent("guildWarpCreated", warpName));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
