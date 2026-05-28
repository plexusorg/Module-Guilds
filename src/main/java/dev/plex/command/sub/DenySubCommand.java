package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.storage.entity.GuildInviteEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DenySubCommand extends SimplePlexCommand
{
    public DenySubCommand()
    {
        super(command("deny")
                .description("Denies a guild invite")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.deny")
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
        Guilds.get().getGuildRepository().invitesFor(player.getUniqueId()).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                send(player, messageComponent("guildStorageFailed"));
                return;
            }
            GuildInviteEntity invite = invites.stream()
                    .filter(candidate -> Guilds.get().getGuildHolder().guildById(UUID.fromString(candidate.getGuildUuid()))
                            .map(guild -> guild.getName().equalsIgnoreCase(String.join(" ", args)))
                            .orElse(false))
                    .findFirst()
                    .orElse(null);
            if (invite == null)
            {
                send(player, messageComponent("guildNotValidInvite"));
                return;
            }
            Guilds.get().getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), player.getUniqueId()).whenComplete((unused, deleteThrowable) ->
            {
                if (deleteThrowable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                send(player, messageComponent("guildInviteDenied"));
            });
        });
        return null;
    }
}
