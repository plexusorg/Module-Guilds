package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Actor;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildAction;
import dev.plex.guild.PendingGuild;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RecoverSubCommand extends GuildSubCommand
{
    public RecoverSubCommand(Guilds module)
    {
        super(module, command("recover").description("Restore a guild or undo a player session")
                .usage("/guild recover <deleted|guild <name or UUID> [confirm]|sessions <player>|session <id> [confirm]>")
                .permission("plex.guilds.recover").source(RequiredCommandSource.ANY).build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining, GuildContext context)
    {
        if (first == null)
        {
            return usage();
        }
        Actor actor = new Actor(player == null ? null : player.getUniqueId(), player == null);
        CompletableFuture<Void> work;
        switch (first.toLowerCase(java.util.Locale.ROOT))
        {
            case "deleted" ->
            {
                showDeleted(sender);
                return null;
            }
            case "guild" ->
            {
                if (remaining == null)
                {
                    return usage();
                }
                work = recoverGuild(sender, remaining, actor);
            }
            case "sessions" ->
            {
                if (remaining == null)
                {
                    return usage();
                }
                work = sessions(sender, remaining);
            }
            case "session" ->
            {
                if (remaining == null)
                {
                    return usage();
                }
                String[] parts = remaining.split(" ");
                if (parts.length > 2 || parts.length == 2 && !parts[1].equalsIgnoreCase("confirm"))
                {
                    return usage();
                }
                long id;
                try
                {
                    id = Long.parseLong(parts[0]);
                }
                catch (NumberFormatException exception)
                {
                    return usage();
                }
                work = session(sender, id, parts.length == 2, actor);
            }
            default ->
            {
                return usage();
            }
        }
        work.exceptionally(failure ->
        {
            sender.sendMessage(failureMessage(failure, null));
            return null;
        });
        return null;
    }

    private CompletableFuture<Void> sessions(CommandSender sender, String player)
    {
        return resolvePlayer(player).thenCompose(id ->
        {
            if (id == null)
            {
                sender.sendMessage(messageComponent("guildPlayerNotFound", Placeholder.unparsed("player", player)));
                return CompletableFuture.completedFuture(null);
            }
            return module.getGuildRepository().sessions(id, module.getRecoveryCutoff()).thenAccept(sessions ->
            {
                if (sessions.isEmpty())
                {
                    sender.sendMessage(messageComponent("guildRecoveryEmpty"));
                }
                sessions.forEach(session -> sender.sendMessage(messageComponent("guildRecoverySession",
                        Placeholder.unparsed("id", Long.toString(session.id())),
                        Placeholder.unparsed("start", Instant.ofEpochMilli(session.startedAt()).toString()),
                        Placeholder.component("end", session.endedAt() == null ? messageComponent("guildRecoveryOnline")
                                : Component.text(Instant.ofEpochMilli(session.endedAt()).toString())),
                        Placeholder.unparsed("count", Long.toString(session.actions())))
                        .clickEvent(ClickEvent.runCommand("/guild recover session " + session.id()))));
            });
        });
    }

    private CompletableFuture<Void> session(CommandSender sender, long id, boolean confirm, Actor actor)
    {
        return module.getGuildRecoveryService().actions(id).thenCompose(actions ->
        {
            if (actions.isEmpty())
            {
                sender.sendMessage(messageComponent("guildRecoveryEmpty"));
                return CompletableFuture.completedFuture(null);
            }
            if (confirm)
            {
                return module.getGuildRecoveryService().undo(actions, actor).thenAccept(lines -> lines.forEach(sender::sendMessage));
            }
            for (GuildAction action : actions)
            {
                String guild = module.getGuildHolder().guildById(action.guildUuid()).map(found -> found.getName()).orElse(action.guildUuid().toString());
                sender.sendMessage(messageComponent("guildRecoveryAction", Placeholder.unparsed("id", Long.toString(action.id())),
                        Placeholder.unparsed("guild", guild), Placeholder.unparsed("type", action.type())));
            }
            sender.sendMessage(messageComponent("guildRecoverySessionWarning"));
            confirm(sender, "/guild recover session " + id + " confirm");
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Void> recoverGuild(CommandSender sender, String input, Actor actor)
    {
        PendingGuild exact = pending(input);
        boolean confirm = exact == null && input.toLowerCase(java.util.Locale.ROOT).endsWith(" confirm");
        PendingGuild pending = confirm ? pending(input.substring(0, input.length() - 8)) : exact;
        if (pending == null)
        {
            sender.sendMessage(messageComponent("guildRecoveryEmpty"));
            return CompletableFuture.completedFuture(null);
        }
        UUID guildUuid = pending.guild().getGuildUuid();
        return module.getGuildRepository().disbandAction(guildUuid, module.getRecoveryCutoff()).thenCompose(found ->
        {
            if (found.isEmpty())
            {
                sender.sendMessage(messageComponent("guildRecoveryEmpty"));
                return CompletableFuture.completedFuture(null);
            }
            if (confirm)
            {
                return module.getGuildRecoveryService().undo(List.of(found.get()), actor).thenAccept(lines -> lines.forEach(sender::sendMessage));
            }
            module.getGuildRecoveryService().preview(found.get()).forEach(sender::sendMessage);
            confirm(sender, "/guild recover guild " + guildUuid + " confirm");
            return CompletableFuture.completedFuture(null);
        });
    }

    /** Names are unique across active and pending guilds, so a name or UUID matches at most one pending guild. */
    private @Nullable PendingGuild pending(String target)
    {
        return module.getGuildHolder().pendingByName(target).or(() -> module.getGuildHolder().pending().stream()
                .filter(pending -> pending.guild().getGuildUuid().toString().equalsIgnoreCase(target)).findFirst()).orElse(null);
    }

    private void showDeleted(CommandSender sender)
    {
        List<PendingGuild> pending = module.getGuildHolder().pending().stream()
                .sorted(Comparator.comparingLong(PendingGuild::deletedAt).reversed()).toList();
        if (pending.isEmpty())
        {
            sender.sendMessage(messageComponent("guildRecoveryEmpty"));
        }
        long retention = Duration.ofDays(module.getRetentionDays()).toMillis();
        for (PendingGuild entry : pending)
        {
            Guild guild = entry.guild();
            sender.sendMessage(messageComponent("guildRecoveryDeleted", Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.unparsed("uuid", guild.getGuildUuid().toString()), Placeholder.unparsed("owner", guild.getOwnerUuid().toString()),
                    Placeholder.unparsed("time", Instant.ofEpochMilli(entry.deletedAt()).toString()),
                    Placeholder.component("actor", entry.deletedBy() == null ? messageComponent("guildRecoveryConsole")
                            : Component.text(entry.deletedBy().toString())),
                    Placeholder.unparsed("due", Instant.ofEpochMilli(entry.deletedAt() + retention).toString()))
                    .clickEvent(ClickEvent.runCommand("/guild recover guild " + guild.getGuildUuid())));
        }
    }

    private void confirm(CommandSender sender, String command)
    {
        sender.sendMessage(messageComponent("guildRecoveryConfirm", Placeholder.unparsed("command", command))
                .clickEvent(ClickEvent.suggestCommand(command)).hoverEvent(messageComponent("guildRecoveryClick")));
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? List.of("deleted", "guild", "sessions", "session") : List.of();
    }
}
