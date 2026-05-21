package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
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

public class SetWarpSubCommand extends SimplePlexCommand
{
    public SetWarpSubCommand()
    {
        super(command("setwarp")
                .description("Creates a new warp at player's location with a specified name")
                .usage("/guild <command> <name>")
                .aliases("makewarp,createwarp")
                .permission("plex.guilds.setwarp")
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
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
