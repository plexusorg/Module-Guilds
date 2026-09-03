package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisbandSubCommand extends GuildSubCommand
{
    public DisbandSubCommand(Guilds module)
    {
        super(module, command("disband")
                .description("Disbands your guild")
                .usage("/guild <command>")
                .permission("plex.guilds.disband")
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
            module.getGuildMutationService().deleteGuild(guild).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildDisbanded"));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }
}
