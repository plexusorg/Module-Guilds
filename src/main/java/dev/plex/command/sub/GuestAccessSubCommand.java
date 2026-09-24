package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildPermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuestAccessSubCommand extends GuildSubCommand
{
    private final boolean grant;

    public GuestAccessSubCommand(Guilds module, boolean grant)
    {
        super(module, command(grant ? "guest" : "unguest")
                .description(grant ? "Give temporary world access" : "Remove guest access")
                .usage(grant ? "/guild <command> <player name or UUID> <view|edit> <minutes>"
                        : "/guild <command> <player name or UUID>")
                .permission("plex.guilds.guests")
                .source(RequiredCommandSource.IN_GAME)
                .build());
        this.grant = grant;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        if (first == null || (!grant && remaining != null))
        {
            return usage();
        }
        Guild guild = module.getGuildHolder().guild(player.getUniqueId()).orElse(null);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.hasPermission(player.getUniqueId(), GuildPermission.MANAGE_GUESTS))
        {
            return messageComponent("guildGuestManagementDenied");
        }
        boolean editing = false;
        Instant expiresAt = null;
        if (grant)
        {
            String[] options = remaining == null ? new String[0] : remaining.split(" ");
            if (options.length != 2 || !(options[0].equalsIgnoreCase("view") || options[0].equalsIgnoreCase("edit")))
            {
                return usage();
            }
            try
            {
                int minutes = Integer.parseInt(options[1]);
                if (minutes <= 0)
                {
                    return messageComponent("guildGuestDurationInvalid");
                }
                expiresAt = Instant.now().plus(minutes, ChronoUnit.MINUTES);
            }
            catch (NumberFormatException exception)
            {
                return messageComponent("guildGuestDurationInvalid");
            }
            editing = options[0].equalsIgnoreCase("edit");
        }
        changeAccess(player, guild, first, editing, expiresAt);
        return null;
    }

    private CompletableFuture<UUID> resolveTarget(String target)
    {
        try
        {
            return CompletableFuture.completedFuture(UUID.fromString(target));
        }
        catch (IllegalArgumentException exception)
        {
            return module.api().players().byName(target).thenApply(result -> result.map(view -> view.uuid()).orElse(null));
        }
    }

    private void changeAccess(Player player, Guild guild, String target, boolean editing, Instant expiresAt)
    {
        UUID managerId = player.getUniqueId();
        resolveTarget(target).thenCompose(targetId ->
        {
            if (targetId == null)
            {
                player.sendMessage(messageComponent("playerNotFound"));
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> mutation = grant
                    ? module.getGuildMutationService().grantGuest(guild, managerId, new Guest(targetId, editing, expiresAt))
                    : module.getGuildMutationService().revokeGuest(guild, managerId, targetId);
            return mutation.thenRun(() ->
            {
                player.sendMessage(messageComponent(grant ? "guildGuestGranted" : "guildGuestRevoked",
                    Placeholder.unparsed("player", target),
                    Placeholder.unparsed("mode", editing ? "editing" : "view-only"),
                    Placeholder.unparsed("expires", String.valueOf(expiresAt))));
                if (grant)
                {
                    notifyGuest(guild, new Guest(targetId, editing, expiresAt));
                }
            });
        }).exceptionally(failure ->
        {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null)
            {
                cause = cause.getCause();
            }
            if (cause instanceof SecurityException)
            {
                player.sendMessage(messageComponent("guildGuestManagementDenied"));
            }
            else if (cause instanceof IllegalArgumentException)
            {
                player.sendMessage(messageComponent("guildGuestTargetInvalid"));
            }
            else
            {
                module.getLogger().error("Failed to change guest access for {} in guild {}", target, guild.getGuildUuid(), failure);
                player.sendMessage(messageComponent("guildStorageFailed"));
            }
            return null;
        });
    }

    private void notifyGuest(Guild guild, Guest guest)
    {
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task ->
        {
            if (!module.isReady() || module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild
                    || !guest.equals(guild.getActiveGuest(guest.playerUuid())))
            {
                return;
            }
            Player target = Bukkit.getPlayer(guest.playerUuid());
            if (target != null)
            {
                target.sendMessage(messageComponent("guildGuestReceived",
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.unparsed("mode", guest.editing() ? "editing" : "view-only"),
                        Placeholder.unparsed("expires", guest.expiresAt().toString()))
                        .clickEvent(ClickEvent.runCommand("/guild visit " + guild.getGuildUuid())));
            }
        }));
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? module.api().players().onlineNames() : grant ? List.of("view", "edit") : List.of();
    }
}
