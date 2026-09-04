package dev.plex.command.sub;

import static dev.plex.api.message.MessagePlaceholder.placeholder;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class LeaveSubCommand extends GuildSubCommand
{
    public LeaveSubCommand(Guilds module)
    {
        super(module, command("leave")
                .description("Leaves your guild")
                .usage("/guild <command>")
                .permission("plex.guilds.leave")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        java.util.UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        module.getGuildHolder().guild(playerUuid).ifPresentOrElse(guild ->
        {
            if (guild.isOwner(playerUuid))
            {
                if (guild.getMembers().size() > 1)
                {
                    player.sendMessage(messageComponent("guildOwnerLeaveBlocked"));
                    return;
                }
                module.getGuildMutationService().deleteGuild(guild).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    player.sendMessage(messageComponent("guildAutoDisbanded"));
                });
                return;
            }
            module.getGuildMutationService().removeMember(guild, playerUuid, false).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                module.broadcastToGuild(guild, messageComponent("guildMemberLeft", placeholder("player", playerName)))
                        .thenRun(() -> player.sendMessage(messageComponent("guildLeft")));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }
}
