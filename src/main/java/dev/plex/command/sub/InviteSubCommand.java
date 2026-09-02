package dev.plex.command.sub;

import com.google.common.collect.ImmutableList;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class InviteSubCommand extends GuildSubCommand
{
    public InviteSubCommand()
    {
        super(command("invite")
                .description("Invites a player to the guild")
                .usage("/guild <command> <player name>")
                .aliases("inv")
                .permission("plex.guilds.invite")
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
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Player target = getNonNullPlayer(args[0]);
            if (target.getUniqueId().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildCannotInviteSelf"));
                return;
            }
            if (guild.getMember(target.getUniqueId()) != null || Guilds.get().getGuildHolder().guild(target.getUniqueId()).isPresent())
            {
                send(player, messageComponent("guildTargetAlreadyInGuild"));
                return;
            }
            String inviterName = player.getName();
            String targetName = target.getName();
            Guilds.get().getGuildRepository().createInvite(guild.getGuildUuid(), player.getUniqueId(), target.getUniqueId(), Instant.now().plus(5, ChronoUnit.MINUTES)).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                send(player, messageComponent("guildInviteSent", targetName));
                send(target, messageComponent("guildInviteReceived", inviterName, guild.getName()));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        return args.length == 1 ? onlinePlayerNames() : ImmutableList.of();
    }
}
