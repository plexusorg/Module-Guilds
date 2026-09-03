package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.CustomLocation;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetHomeSubCommand extends GuildSubCommand
{
    public SetHomeSubCommand(Guilds module)
    {
        super(module, command("sethome")
                .description("Sets the guild's home")
                .usage("/guild <command>")
                .aliases("setspawn")
                .permission("plex.guilds.sethome")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            if (first != null && (first.equalsIgnoreCase("remove") || first.equalsIgnoreCase("unset") || first.equalsIgnoreCase("clear")))
            {
                if (guild.getHome() == null)
                {
                    player.sendMessage(messageComponent("guildHomeNotFound"));
                    return;
                }
                module.getGuildMutationService().updateHome(guild, null).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    player.sendMessage(messageComponent("guildHomeRemoved"));
                });
                return;
            }
            CustomLocation home = CustomLocation.fromLocation(player.getLocation());
            module.getGuildMutationService().updateHome(guild, home).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildHomeSet"));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
