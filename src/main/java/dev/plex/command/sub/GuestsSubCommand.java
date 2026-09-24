package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildPermission;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuestsSubCommand extends GuildSubCommand
{
    private static final int PAGE_SIZE = 5;

    public GuestsSubCommand(Guilds module)
    {
        super(module, command("guests")
                .description("List current guests")
                .usage("/guild <command> [page]")
                .permission("plex.guilds.guests")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        if (remaining != null)
        {
            return usage();
        }
        int page;
        try
        {
            page = first == null ? 1 : Integer.parseInt(first);
        }
        catch (NumberFormatException exception)
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
        Instant now = Instant.now();
        List<Guest> guests = guild.getGuests().values().stream().filter(guest -> guest.isActive(now))
                .sorted(Comparator.comparing(Guest::expiresAt).thenComparing(Guest::playerUuid)).toList();
        int pages = Math.max(1, (guests.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1 || page > pages)
        {
            return messageComponent("guildGuestsPageInvalid", Placeholder.unparsed("pages", Integer.toString(pages)));
        }
        if (guests.isEmpty())
        {
            return messageComponent("guildGuestsEmpty");
        }
        sendPage(player, guild, guests.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, guests.size())), page, pages);
        return null;
    }

    private void sendPage(Player player, Guild guild, List<Guest> guests, int page, int pages)
    {
        List<CompletableFuture<Component>> entries = guests.stream().map(guest ->
                module.api().players().player(guest.playerUuid()).thenApply(result -> messageComponent("guildGuestEntry",
                        Placeholder.unparsed("player", result.map(view -> view.name()).orElse(guest.playerUuid().toString())),
                        Placeholder.unparsed("mode", guest.editing() ? "edit" : "view"),
                        Placeholder.unparsed("expires", guest.expiresAt().toString()))
                        .clickEvent(ClickEvent.suggestCommand("/guild unguest " + guest.playerUuid()))
                        .hoverEvent(Component.text("Click to fill in the removal command")))).toList();
        CompletableFuture.allOf(entries.toArray(CompletableFuture[]::new)).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to look up guests for guild {}", guild.getGuildUuid(), failure);
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            if (!module.isReady() || module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild
                    || !guild.hasPermission(player.getUniqueId(), GuildPermission.MANAGE_GUESTS))
            {
                player.sendMessage(messageComponent("guildGuestManagementDenied"));
                return;
            }
            Component content = Component.empty();
            for (CompletableFuture<Component> entry : entries)
            {
                content = content.append(Component.newline()).append(entry.join());
            }
            Component navigation = Component.empty();
            if (page > 1)
            {
                navigation = navigation.append(messageComponent("guildHelpBack")
                        .clickEvent(ClickEvent.runCommand("/guild guests " + (page - 1))));
            }
            if (page < pages)
            {
                navigation = navigation.append(messageComponent("guildHelpNext")
                        .clickEvent(ClickEvent.runCommand("/guild guests " + (page + 1))));
            }
            player.sendMessage(messageComponent("guildGuestsPage",
                    Placeholder.unparsed("page", Integer.toString(page)),
                    Placeholder.unparsed("pages", Integer.toString(pages)),
                    Placeholder.component("content", content), Placeholder.component("navigation", navigation)));
        });
    }
}
