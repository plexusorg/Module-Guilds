package dev.plex.command.sub;

import com.google.common.collect.ImmutableList;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildRole;
import dev.plex.storage.entity.GuildInviteEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AcceptSubCommand extends GuildSubCommand
{
    public AcceptSubCommand(Guilds module)
    {
        super(module, command("accept")
                .description("Accepts a guild invite")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.accept")
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
        String playerName = player.getName();
        module.getGuildRepository().invitesFor(playerUuid).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            GuildInviteEntity invite = findInvite(invites, arguments(first, remaining));
            if (invite == null)
            {
                player.sendMessage(messageComponent("guildNotValidInvite"));
                return;
            }
            if (invite.getExpiresAt() < Instant.now().toEpochMilli())
            {
                module.getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), playerUuid);
                player.sendMessage(messageComponent("guildInviteExpired"));
                return;
            }
            Guild target = module.getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null);
            if (target == null)
            {
                player.sendMessage(messageComponent("guildNotValidInvite"));
                return;
            }
            leaveCurrentIfNeeded(player, playerUuid, playerName, target);
        });
        return null;
    }

    private GuildInviteEntity findInvite(List<GuildInviteEntity> invites, String guildName)
    {
        return invites.stream()
                .filter(invite -> module.getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid()))
                        .map(guild -> guild.getName().equalsIgnoreCase(guildName))
                        .orElse(false))
                .findFirst()
                .orElse(null);
    }

    private void leaveCurrentIfNeeded(Player player, UUID playerUuid, String playerName, Guild target)
    {
        module.getGuildHolder().guild(playerUuid).ifPresentOrElse(current ->
        {
            if (current.getGuildUuid().equals(target.getGuildUuid()))
            {
                player.sendMessage(messageComponent("guildInThis"));
                return;
            }
            if (current.isOwner(playerUuid) && current.getMembers().size() > 1)
            {
                player.sendMessage(messageComponent("guildOwnerLeaveBlocked"));
                return;
            }
            if (current.isOwner(playerUuid))
            {
                module.getGuildMutationService().deleteGuild(current).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    joinTarget(player, playerUuid, playerName, target);
                });
                return;
            }
            module.getGuildMutationService().removeMember(current, playerUuid, false).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                module.broadcastToGuild(current, messageComponent("guildMemberLeft", playerName))
                        .thenRun(() -> joinTarget(player, playerUuid, playerName, target));
            });
        }, () -> joinTarget(player, playerUuid, playerName, target));
    }

    private void joinTarget(Player player, UUID playerUuid, String playerName, Guild guild)
    {
        UUID guildUuid = guild.getGuildUuid();
        module.getGuildMutationService().addMember(guild, playerUuid, true)
                .whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    module.broadcastToGuild(guild, messageComponent("guildMemberJoined", playerName));
                });
    }


}
