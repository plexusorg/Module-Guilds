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
    public AcceptSubCommand()
    {
        super(command("accept")
                .description("Accepts a guild invite")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.accept")
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
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Guilds.get().getGuildRepository().invitesFor(playerUuid).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                send(player, messageComponent("guildStorageFailed"));
                return;
            }
            GuildInviteEntity invite = findInvite(invites, String.join(" ", args));
            if (invite == null)
            {
                send(player, messageComponent("guildNotValidInvite"));
                return;
            }
            if (invite.getExpiresAt() < Instant.now().toEpochMilli())
            {
                Guilds.get().getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), playerUuid);
                send(player, messageComponent("guildInviteExpired"));
                return;
            }
            Guild target = Guilds.get().getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null);
            if (target == null)
            {
                send(player, messageComponent("guildNotValidInvite"));
                return;
            }
            leaveCurrentIfNeeded(player, playerUuid, playerName, target);
        });
        return null;
    }

    private GuildInviteEntity findInvite(List<GuildInviteEntity> invites, String guildName)
    {
        return invites.stream()
                .filter(invite -> Guilds.get().getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid()))
                        .map(guild -> guild.getName().equalsIgnoreCase(guildName))
                        .orElse(false))
                .findFirst()
                .orElse(null);
    }

    private void leaveCurrentIfNeeded(Player player, UUID playerUuid, String playerName, Guild target)
    {
        Guilds.get().getGuildHolder().guild(playerUuid).ifPresentOrElse(current ->
        {
            if (current.getGuildUuid().equals(target.getGuildUuid()))
            {
                send(player, messageComponent("guildInThis"));
                return;
            }
            if (current.isOwner(playerUuid) && current.getMembers().size() > 1)
            {
                send(player, messageComponent("guildOwnerLeaveBlocked"));
                return;
            }
            if (current.isOwner(playerUuid))
            {
                Guilds.get().getGuildRepository().deleteGuild(current.getGuildUuid()).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        send(player, messageComponent("guildStorageFailed"));
                        return;
                    }
                    Guilds.get().getGuildHolder().removeGuild(current.getGuildUuid());
                    joinTarget(player, playerUuid, playerName, target);
                });
                return;
            }
            Guilds.get().getGuildRepository().removeMember(current.getGuildUuid(), playerUuid).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                current.getMembers().removeIf(member -> member.getUuid().equals(playerUuid));
                Guilds.get().getGuildHolder().unindexMember(playerUuid);
                Guilds.get().broadcastToGuild(current, messageComponent("guildMemberLeft", playerName));
                joinTarget(player, playerUuid, playerName, target);
            });
        }, () -> joinTarget(player, playerUuid, playerName, target));
    }

    private void joinTarget(Player player, UUID playerUuid, String playerName, Guild guild)
    {
        UUID guildUuid = guild.getGuildUuid();
        Guilds.get().getGuildRepository().addMember(guildUuid, playerUuid, GuildRole.MEMBER)
                .thenCompose(unused -> Guilds.get().getGuildRepository().deleteInvite(guildUuid, playerUuid))
                .whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        send(player, messageComponent("guildStorageFailed"));
                        return;
                    }
                    guild.addMember(playerUuid);
                    Guilds.get().getGuildHolder().indexMember(guildUuid, playerUuid);
                    Guilds.get().broadcastToGuild(guild, messageComponent("guildMemberJoined", playerName));
                });
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        if (!(sender instanceof Player player) || args.length != 1)
        {
            return ImmutableList.of();
        }
        return ImmutableList.of();
    }
}
