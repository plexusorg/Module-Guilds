package dev.plex.command.sub;

import com.google.common.collect.ImmutableList;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.storage.entity.GuildInviteEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
        Guilds.get().getGuildRepository().invitesFor(player.getUniqueId()).whenComplete((invites, throwable) ->
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
                Guilds.get().getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), player.getUniqueId());
                send(player, messageComponent("guildInviteExpired"));
                return;
            }
            Guild target = Guilds.get().getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null);
            if (target == null)
            {
                send(player, messageComponent("guildNotValidInvite"));
                return;
            }
            leaveCurrentIfNeeded(player, target, invite);
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

    private void leaveCurrentIfNeeded(Player player, Guild target, GuildInviteEntity invite)
    {
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(current ->
        {
            if (current.getGuildUuid().equals(target.getGuildUuid()))
            {
                send(player, messageComponent("guildInThis"));
                return;
            }
            if (current.isOwner(player.getUniqueId()) && current.getMembers().size() > 1)
            {
                send(player, messageComponent("guildOwnerLeaveBlocked"));
                return;
            }
            if (current.isOwner(player.getUniqueId()))
            {
                Guilds.get().getGuildRepository().deleteGuild(current.getGuildUuid()).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        send(player, messageComponent("guildStorageFailed"));
                        return;
                    }
                    Guilds.get().getGuildHolder().removeGuild(current.getGuildUuid());
                    joinTarget(player, target, invite);
                });
                return;
            }
            Guilds.get().getGuildRepository().removeMember(current.getGuildUuid(), player.getUniqueId()).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                current.getMembers().removeIf(member -> member.getUuid().equals(player.getUniqueId()));
                Guilds.get().getGuildHolder().unindexMember(player.getUniqueId());
                current.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(memberPlayer ->
                        send(memberPlayer, messageComponent("guildMemberLeft", player.getName())));
                joinTarget(player, target, invite);
            });
        }, () -> joinTarget(player, target, invite));
    }

    private void joinTarget(Player player, Guild guild, GuildInviteEntity invite)
    {
        UUID guildUuid = guild.getGuildUuid();
        Guilds.get().getGuildRepository().addMember(guildUuid, player.getUniqueId(), GuildRole.MEMBER)
                .thenCompose(unused -> Guilds.get().getGuildRepository().deleteInvite(guildUuid, player.getUniqueId()))
                .whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        send(player, messageComponent("guildStorageFailed"));
                        return;
                    }
                    guild.addMember(player.getUniqueId());
                    Guilds.get().getGuildHolder().indexMember(guildUuid, player.getUniqueId());
                    guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(memberPlayer ->
                            send(memberPlayer, messageComponent("guildMemberJoined", player.getName())));
                });
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        if (!(sender instanceof Player player) || args.length != 1)
        {
            return ImmutableList.of();
        }
        try
        {
            return Guilds.get().getGuildRepository().invitesFor(player.getUniqueId()).join().stream()
                    .map(invite -> Guilds.get().getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Guild::getName)
                    .toList();
        }
        catch (RuntimeException ignored)
        {
            return ImmutableList.of();
        }
    }
}
