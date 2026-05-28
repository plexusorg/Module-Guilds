package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.Member;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LeaveSubCommand extends SimplePlexCommand
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
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (guild.isOwner(player.getUniqueId()))
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
            Guilds.get().getGuildRepository().removeMember(guild.getGuildUuid(), player.getUniqueId()).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                guild.getMembers().removeIf(member -> member.getUuid().equals(player.getUniqueId()));
                Guilds.get().getGuildHolder().unindexMember(player.getUniqueId());
                guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(memberPlayer ->
                        send(memberPlayer, messageComponent("guildMemberLeft", player.getName())));
                send(player, messageComponent("guildLeft"));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
