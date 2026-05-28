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
    public DisbandSubCommand()
    {
        super(command("disband")
                .description("Disbands your guild")
                .usage("/guild <command>")
                .permission("plex.guilds.disband")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Guilds.get().getGuildRepository().deleteGuild(guild.getGuildUuid()).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                Guilds.get().getGuildHolder().removeGuild(guild.getGuildUuid());
                send(player, messageComponent("guildDisbanded"));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
