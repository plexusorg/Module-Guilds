package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class LeaveSubCommand extends GuildSubCommand
{
    public LeaveSubCommand()
    {
        super(command("leave")
                .description("Leaves your guild")
                .usage("/guild <command>")
                .permission("plex.guilds.leave")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        java.util.UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Guilds.get().getGuildHolder().guild(playerUuid).ifPresentOrElse(guild ->
        {
            if (guild.isOwner(playerUuid))
            {
                if (guild.getMembers().size() > 1)
                {
                    send(player, messageComponent("guildOwnerLeaveBlocked"));
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
                    send(player, messageComponent("guildAutoDisbanded"));
                });
                return;
            }
            Guilds.get().getGuildRepository().removeMember(guild.getGuildUuid(), playerUuid).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                guild.getMembers().removeIf(member -> member.getUuid().equals(playerUuid));
                Guilds.get().getGuildHolder().unindexMember(playerUuid);
                Guilds.get().broadcastToGuild(guild, messageComponent("guildMemberLeft", playerName));
                send(player, messageComponent("guildLeft"));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
