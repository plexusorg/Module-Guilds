package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.storage.entity.GuildInviteEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DenySubCommand extends GuildSubCommand
{
    public DenySubCommand(Guilds module)
    {
        super(module, command("deny")
                .description("Denies a guild invite")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.deny")
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
        UUID playerUuid = player.getUniqueId();
        module.getGuildRepository().invitesFor(playerUuid).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            GuildInviteEntity invite = invites.stream()
                    .filter(candidate -> module.getGuildHolder().guildById(UUID.fromString(candidate.getGuildUuid()))
                            .map(guild -> guild.getName().equalsIgnoreCase(arguments(first, remaining)))
                            .orElse(false))
                    .findFirst()
                    .orElse(null);
            if (invite == null)
            {
                player.sendMessage(messageComponent("guildNotValidInvite"));
                return;
            }
            module.getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), playerUuid).whenComplete((unused, deleteThrowable) ->
            {
                if (deleteThrowable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildInviteDenied"));
            });
        });
        return null;
    }
}
