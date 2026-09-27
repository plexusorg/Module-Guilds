package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.util.DurationParser;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuestSubCommand extends GuildSubCommand
{
    private static final String ADD = "add";
    private static final String REMOVE = "remove";

    public GuestSubCommand(Guilds module)
    {
        super(module, command("guest")
                .description("Add or remove a guest in your guild world")
                .usage("/guild <command> <add|remove> <player> [view|build] [time]")
                .permission("plex.guilds.guests")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        Guild guild = guildOf(player);
        return guild != null && guild.canManage(player.getUniqueId());
    }

    @Override
    public List<HelpEntry> helpEntries(@Nullable Player player)
    {
        return List.of(
                new HelpEntry("/guild guest add <player> [view|build] [time]", "Let a player view or build in your guild world (time: 30m, 12h, 7d)", "/guild guest add "),
                new HelpEntry("/guild guest remove <player>", "Remove a guest", "/guild guest remove "));
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        boolean add = ADD.equalsIgnoreCase(first);
        if (remaining == null || !add && !REMOVE.equalsIgnoreCase(first))
        {
            return usage();
        }
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.canManage(player.getUniqueId()))
        {
            return messageComponent("guildNotManager");
        }
        String[] options = remaining.split(" ");
        if (!add)
        {
            if (options.length != 1)
            {
                return usage();
            }
            remove(player, guild, options[0]);
            return null;
        }
        Boolean editing = null;
        int timeIndex = 1;
        if (options.length > 1)
        {
            editing = switch (options[1].toLowerCase(Locale.ROOT))
            {
                case "view" -> false;
                case "build" -> true;
                default -> null;
            };
            if (editing != null)
            {
                timeIndex = 2;
            }
        }
        if (options.length > timeIndex + 1)
        {
            return usage();
        }
        Duration duration = options.length > timeIndex ? DurationParser.parse(options[timeIndex]) : module.getGuestDefaultDuration();
        if (duration == null || duration.compareTo(module.getGuestMaxDuration()) > 0)
        {
            return messageComponent("guildGuestDurationInvalid",
                    Placeholder.unparsed("max", formatDuration(module.getGuestMaxDuration())));
        }
        add(player, guild, options[0], editing, duration);
        return null;
    }

    private void add(Player player, Guild guild, String target, @Nullable Boolean editing, Duration duration)
    {
        resolvePlayer(target).whenComplete((targetId, lookupFailure) ->
        {
            if (lookupFailure != null)
            {
                player.sendMessage(failureMessage(lookupFailure, null));
                return;
            }
            if (targetId == null)
            {
                player.sendMessage(messageComponent("guildPlayerNotFound", Placeholder.unparsed("player", target)));
                return;
            }
            module.getGuildMutationService().addGuest(guild, player.getUniqueId(), targetId, editing, duration).whenComplete((guest, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(failureMessage(throwable, "guildGuestTargetInvalid"));
                    return;
                }
                player.sendMessage(messageComponent("guildGuestAdded",
                        Placeholder.unparsed("player", target),
                        Placeholder.unparsed("mode", mode(guest)),
                        Placeholder.unparsed("duration", formatDuration(duration))));
                notifyGuest(guild, guest, duration);
            });
        });
    }

    private void remove(Player player, Guild guild, String target)
    {
        resolvePlayer(target).whenComplete((resolvedId, failure) ->
        {
            if (failure != null)
            {
                player.sendMessage(failureMessage(failure, null));
                return;
            }
            if (resolvedId == null || !guild.getGuests().containsKey(resolvedId))
            {
                player.sendMessage(messageComponent("guildGuestNotFound", Placeholder.unparsed("player", target)));
                return;
            }
            module.getGuildMutationService().revokeGuest(guild, player.getUniqueId(), resolvedId).whenComplete((unused, throwable) ->
                    player.sendMessage(throwable == null
                            ? messageComponent("guildGuestRevoked", Placeholder.unparsed("player", target))
                            : failureMessage(throwable, null)));
        });
    }

    private String mode(Guest guest)
    {
        return guest.editing() ? "build" : "view";
    }

    private void notifyGuest(Guild guild, Guest guest, Duration duration)
    {
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task ->
        {
            if (module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild
                    || !guest.equals(guild.getActiveGuest(guest.playerUuid())))
            {
                return;
            }
            Player target = Bukkit.getPlayer(guest.playerUuid());
            if (target != null)
            {
                target.sendMessage(messageComponent("guildGuestReceived",
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.unparsed("mode", mode(guest)),
                        Placeholder.unparsed("duration", formatDuration(duration)))
                        .clickEvent(ClickEvent.runCommand("/guild visit " + guild.getName())));
            }
        }));
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        if (first == null)
        {
            return List.of(ADD, REMOVE);
        }
        if (ADD.equalsIgnoreCase(first))
        {
            return module.api().players().onlineNames();
        }
        String[] arguments = first.split(" ");
        if (arguments.length == 2 && ADD.equalsIgnoreCase(arguments[0]))
        {
            return List.of("view", "build");
        }
        Guild guild = sender instanceof Player player ? guildOf(player) : null;
        if (!REMOVE.equalsIgnoreCase(first) || guild == null)
        {
            return List.of();
        }
        return guild.getGuests().values().stream()
                .filter(guest -> guild.getActiveGuest(guest.playerUuid()) != null)
                .map(guest -> Bukkit.getOfflinePlayer(guest.playerUuid()).getName())
                .filter(Objects::nonNull)
                .toList();
    }
}
