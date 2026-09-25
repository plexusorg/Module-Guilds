package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.util.DurationParser;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
                .usage("/guild <command> <add|remove> <player> [time]")
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
                new HelpEntry("/guild guest add <player> [time]", "Let a player view your guild world (time: 30m, 12h, 7d)", "/guild guest add "),
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
        if (options.length > (add ? 2 : 1))
        {
            return usage();
        }
        if (!add)
        {
            remove(player, guild, options[0]);
            return null;
        }
        Duration duration = options.length == 2 ? DurationParser.parse(options[1]) : module.getGuestDefaultDuration();
        if (duration == null || duration.compareTo(module.getGuestMaxDuration()) > 0)
        {
            return messageComponent("guildGuestDurationInvalid",
                    Placeholder.unparsed("max", formatDuration(module.getGuestMaxDuration())));
        }
        add(player, guild, options[0], duration);
        return null;
    }

    private void add(Player player, Guild guild, String target, Duration duration)
    {
        resolvePlayer(target).whenComplete((targetId, lookupFailure) ->
        {
            if (lookupFailure != null || targetId == null)
            {
                player.sendMessage(messageComponent("guildPlayerNotFound", Placeholder.unparsed("player", target)));
                return;
            }
            module.getGuildMutationService().addGuest(guild, player.getUniqueId(), targetId, duration).whenComplete((guest, throwable) ->
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
        UUID guestId = guestByName(guild, target);
        if (guestId == null)
        {
            player.sendMessage(messageComponent("guildGuestNotFound", Placeholder.unparsed("player", target)));
            return;
        }
        module.getGuildMutationService().revokeGuest(guild, player.getUniqueId(), guestId).whenComplete((unused, throwable) ->
                player.sendMessage(throwable == null
                        ? messageComponent("guildGuestRevoked", Placeholder.unparsed("player", target))
                        : failureMessage(throwable, null)));
    }

    /** Finds a guest of the guild by name or UUID. Expired guests count, so staff can clear them too. */
    private UUID guestByName(Guild guild, String target)
    {
        for (UUID guestId : guild.getGuests().keySet())
        {
            if (guestId.toString().equalsIgnoreCase(target) || target.equalsIgnoreCase(Bukkit.getOfflinePlayer(guestId).getName()))
            {
                return guestId;
            }
        }
        return null;
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
