package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.CustomLocation;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetWarpSubCommand extends GuildSubCommand
{
    public SetWarpSubCommand(Guilds module)
    {
        super(module, command("setwarp")
                .description("Creates a new warp at player's location with a specified name")
                .usage("/guild <command> <name>")
                .aliases("makewarp,createwarp")
                .permission("plex.guilds.setwarp")
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
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            String warpName = arguments(first, remaining);
            if (warpName.length() > 16)
            {
                player.sendMessage(mmString("<red>The max length of a warp name is 16 characters!"));
                return;
            }
            if (!StringUtils.isAlphanumericSpace(warpName.toLowerCase(Locale.ROOT)))
            {
                player.sendMessage(messageComponent("guildWarpAlphanumeric"));
                return;
            }
            CustomLocation location = CustomLocation.fromLocation(player.getLocation());
            String localName = warpName.toLowerCase(Locale.ROOT);
            module.getGuildMutationService().upsertWarp(guild, localName, location).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildWarpCreated", Placeholder.parsed("warp", warpName)));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
