package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.gui.GuildMenuInventoryHolder;
import dev.plex.gui.GuildMenuInventoryHolder.Screen;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMutationService;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class GuildMenuListener implements Listener
{
    private static final int SMALL_SIZE = 27;
    private static final int LARGE_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int WORLD_SLOT = 10;
    private static final int MEMBERS_SLOT = 12;
    private static final int WARPS_SLOT = 14;
    private static final int GUESTS_SLOT = 16;
    private static final int SPAWN_SLOT = 22;
    private static final int HEAD_SLOT = 4;
    private static final int FIRST_ACTION_SLOT = 11;
    private static final int SECOND_ACTION_SLOT = 13;
    private static final int THIRD_ACTION_SLOT = 15;
    private static final int SMALL_BACK_SLOT = 22;
    private static final int LARGE_BACK_SLOT = 49;
    private static final int PAGE_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int EMPTY_SLOT = 22;

    private static final NamespacedKey ACTION_KEY = new NamespacedKey("plex_guilds", "menu_action");
    private static final NamespacedKey TARGET_KEY = new NamespacedKey("plex_guilds", "menu_target");
    private static final NamespacedKey WARP_KEY = new NamespacedKey("plex_guilds", "menu_warp");
    private static final NamespacedKey CONFIRM_KEY = new NamespacedKey("plex_guilds", "menu_confirm");

    private final Guilds module;
    private final GuildMutationService mutationService;

    private enum Action
    {
        WORLD,
        MEMBERS,
        GUESTS,
        WARPS,
        SPAWN,
        BACK,
        PREVIOUS,
        NEXT,
        OPEN,
        WARP,
        PROMOTE,
        DEMOTE,
        KICK,
        MODE,
        EXTEND,
        REMOVE
    }

    public GuildMenuListener(Guilds module, GuildMutationService mutationService)
    {
        this.module = module;
        this.mutationService = mutationService;
    }

    /** Opens the main screen. Call this from the player's region thread. */
    public void open(Player player, Guild guild)
    {
        player.openInventory(build(player, guild, Screen.MAIN, null, 0, Map.of()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof GuildMenuInventoryHolder holder))
        {
            return;
        }
        event.setCancelled(true);
        Inventory source = event.getInventory();
        if (!hasAccess(player, holder.guild()))
        {
            close(player, source);
            player.sendMessage(module.messageComponent("guildNotFound"));
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= source.getSize() || item == null || !item.hasItemMeta())
        {
            return;
        }
        Action action = action(item);
        if (action != null)
        {
            click(player, holder, source, event.getRawSlot(), item, action);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event)
    {
        if (event.getInventory().getHolder() instanceof GuildMenuInventoryHolder
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize()))
        {
            event.setCancelled(true);
        }
    }

    private void click(Player player, GuildMenuInventoryHolder holder, Inventory source, int slot, ItemStack item, Action action)
    {
        Guild guild = holder.guild();
        switch (action)
        {
            case WORLD ->
            {
                close(player, source);
                teleport(player, guild, null);
            }
            case MEMBERS -> render(player, guild, Screen.MEMBERS, null, 0, source);
            case GUESTS -> render(player, guild, Screen.GUESTS, null, 0, source);
            case WARPS -> render(player, guild, Screen.WARPS, null, 0, source);
            case BACK ->
            {
                Screen parent = parent(holder.screen());
                render(player, guild, parent, null, parent == Screen.MAIN ? 0 : holder.page(), source);
            }
            case PREVIOUS -> render(player, guild, holder.screen(), null, holder.page() - 1, source);
            case NEXT -> render(player, guild, holder.screen(), null, holder.page() + 1, source);
            case OPEN -> render(player, guild, holder.screen() == Screen.GUESTS ? Screen.GUEST : Screen.MEMBER, target(item), holder.page(), source);
            case WARP -> clickWarp(player, holder, source, item);
            case SPAWN -> clickSpawn(player, guild, source);
            case PROMOTE -> clickPromote(player, holder, source, slot, item);
            case DEMOTE -> clickDemote(player, holder, source);
            case KICK -> clickKick(player, holder, source);
            case MODE -> clickMode(player, holder, source);
            case EXTEND -> clickExtend(player, holder, source);
            case REMOVE -> clickRemove(player, holder, source);
        }
    }

    private void clickWarp(Player player, GuildMenuInventoryHolder holder, Inventory source, ItemStack item)
    {
        Guild guild = holder.guild();
        String name = item.getItemMeta().getPersistentDataContainer().get(WARP_KEY, PersistentDataType.STRING);
        CustomLocation location = name == null ? null : guild.getWarps().get(name);
        if (location == null)
        {
            player.sendMessage(module.messageComponent("guildWarpNotFound", Placeholder.unparsed("warp", String.valueOf(name))));
            render(player, guild, Screen.WARPS, null, holder.page(), source);
            return;
        }
        close(player, source);
        teleport(player, guild, location);
    }

    private void clickSpawn(Player player, Guild guild, Inventory source)
    {
        UUID viewer = player.getUniqueId();
        if (!guild.canManage(viewer))
        {
            deny(player, guild, Screen.MAIN, null, 0, source);
            return;
        }
        if (!inGuildWorld(player, guild))
        {
            player.sendMessage(module.messageComponent("guildMenuSpawnNotInWorld"));
            return;
        }
        CustomLocation spawn = CustomLocation.fromLocation(player.getLocation());
        mutate(player, guild, source, mutationService.updateSpawn(guild, viewer, spawn), null,
                name -> module.messageComponent("guildMenuSpawnSet"), Screen.MAIN, null, 0);
    }

    private void clickPromote(Player player, GuildMenuInventoryHolder holder, Inventory source, int slot, ItemStack item)
    {
        Guild guild = holder.guild();
        UUID viewer = player.getUniqueId();
        UUID target = holder.target();
        if (!guild.canPromote(viewer, target))
        {
            deny(player, guild, Screen.MEMBER, target, holder.page(), source);
            return;
        }
        GuildRole role = guild.getRole(target) == GuildRole.MEMBER ? GuildRole.OFFICER : GuildRole.OWNER;
        if (role == GuildRole.OWNER && !item.getItemMeta().getPersistentDataContainer().has(CONFIRM_KEY))
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source)
                {
                    source.setItem(slot, confirmOwnerItem());
                }
            }, null));
            return;
        }
        String key = role == GuildRole.OWNER ? "guildMenuOwnerTransferred" : "guildMenuPromoted";
        mutate(player, guild, source, mutationService.setRole(guild, viewer, target, role), target,
                name -> module.messageComponent(key, Placeholder.unparsed("player", name)), Screen.MEMBER, target, holder.page());
    }

    private void clickDemote(Player player, GuildMenuInventoryHolder holder, Inventory source)
    {
        Guild guild = holder.guild();
        UUID target = holder.target();
        if (!guild.canDemote(player.getUniqueId(), target))
        {
            deny(player, guild, Screen.MEMBER, target, holder.page(), source);
            return;
        }
        mutate(player, guild, source, mutationService.setRole(guild, player.getUniqueId(), target, GuildRole.MEMBER), target,
                name -> module.messageComponent("guildMenuDemoted", Placeholder.unparsed("player", name)), Screen.MEMBER, target, holder.page());
    }

    private void clickKick(Player player, GuildMenuInventoryHolder holder, Inventory source)
    {
        Guild guild = holder.guild();
        UUID target = holder.target();
        if (!guild.canKick(player.getUniqueId(), target))
        {
            deny(player, guild, Screen.MEMBER, target, holder.page(), source);
            return;
        }
        mutate(player, guild, source, mutationService.removeMember(guild, player.getUniqueId(), target), target,
                name -> module.messageComponent("guildMemberKicked", Placeholder.unparsed("player", name)), Screen.MEMBERS, null, holder.page());
    }

    private void clickMode(Player player, GuildMenuInventoryHolder holder, Inventory source)
    {
        Guild guild = holder.guild();
        UUID target = holder.target();
        Guest guest = activeGuest(guild, target);
        if (!guild.canManage(player.getUniqueId()) || guest == null)
        {
            deny(player, guild, Screen.GUEST, target, holder.page(), source);
            return;
        }
        boolean editing = !guest.editing();
        CompletableFuture<Void> mutation = mutationService.setGuestMode(guild, player.getUniqueId(), target, editing).thenApply(unused -> null);
        mutate(player, guild, source, mutation, target, name -> module.messageComponent("guildMenuGuestModeSet",
                Placeholder.unparsed("player", name), Placeholder.unparsed("mode", editing ? "build" : "view")), Screen.GUEST, target, holder.page());
    }

    private void clickExtend(Player player, GuildMenuInventoryHolder holder, Inventory source)
    {
        Guild guild = holder.guild();
        UUID target = holder.target();
        if (!guild.canManage(player.getUniqueId()) || activeGuest(guild, target) == null)
        {
            deny(player, guild, Screen.GUEST, target, holder.page(), source);
            return;
        }
        Duration duration = module.getGuestDefaultDuration();
        CompletableFuture<Void> mutation = mutationService.addGuest(guild, player.getUniqueId(), target, duration).thenApply(unused -> null);
        mutate(player, guild, source, mutation, target, name -> module.messageComponent("guildMenuGuestExtended",
                Placeholder.unparsed("player", name), Placeholder.unparsed("time", formatDuration(duration))), Screen.GUEST, target, holder.page());
    }

    private void clickRemove(Player player, GuildMenuInventoryHolder holder, Inventory source)
    {
        Guild guild = holder.guild();
        UUID target = holder.target();
        if (!guild.canManage(player.getUniqueId()) || activeGuest(guild, target) == null)
        {
            deny(player, guild, Screen.GUEST, target, holder.page(), source);
            return;
        }
        mutate(player, guild, source, mutationService.revokeGuest(guild, player.getUniqueId(), target), target,
                name -> module.messageComponent("guildGuestRevoked", Placeholder.unparsed("player", name)), Screen.GUESTS, null, holder.page());
    }

    private void deny(Player player, Guild guild, Screen screen, UUID target, int page, Inventory source)
    {
        player.sendMessage(module.messageComponent("guildMenuActionDenied"));
        render(player, guild, screen, target, page, source);
    }

    /**
     * Sends the result of a mutation and then shows the next screen, if the player still has the source menu open.
     * The name of the subject, when there is one, is looked up after the mutation for the success message.
     */
    private void mutate(Player player, Guild guild, Inventory source, CompletableFuture<Void> mutation, UUID subject,
                        Function<String, Component> success, Screen next, UUID nextTarget, int nextPage)
    {
        mutation.thenCompose(unused -> subject == null ? CompletableFuture.completedFuture("") : name(subject))
                .whenComplete((name, failure) ->
                {
                    player.sendMessage(failure == null ? success.apply(name) : failureMessage(failure));
                    render(player, guild, next, nextTarget, nextPage, source);
                });
    }

    private Component failureMessage(Throwable failure)
    {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof SecurityException)
        {
            return module.messageComponent("guildMenuActionDenied");
        }
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException)
        {
            return module.messageComponent("guildMenuActionUnavailable");
        }
        module.getLogger().error("Failed to update the guild from the menu", cause);
        return module.messageComponent("guildStorageFailed");
    }

    private void teleport(Player player, Guild guild, CustomLocation location)
    {
        if (!module.isGuildWorldsEnabled())
        {
            player.sendMessage(module.messageComponent("guildWorldsUnavailable"));
            return;
        }
        player.sendMessage(module.messageComponent("guildWorldLoading"));
        module.getGuildWorldService().ensureWorld(guild).whenComplete((world, throwable) ->
        {
            if (throwable != null)
            {
                module.getLogger().error("Failed to load guild world", throwable);
                player.sendMessage(module.messageComponent("guildWorldLoadFailed"));
                return;
            }
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
                    player.teleportAsync(destination(world, location == null ? guild.getSpawn() : location)), null));
        });
    }

    private Location destination(World world, CustomLocation location)
    {
        if (location == null)
        {
            return world.getSpawnLocation().toCenterLocation();
        }
        return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Looks up the names the screen needs, then opens the screen on the player's thread.
     * Nothing opens when the player has closed or changed the source menu in the meantime.
     */
    private void render(Player player, Guild guild, Screen screen, UUID target, int page, Inventory source)
    {
        Screen resolved = resolve(guild, player.getUniqueId(), screen, target);
        names(subjects(guild, resolved, target, page)).thenAccept(names -> module.ownTask(player.getScheduler().run(module.plugin(), task ->
        {
            if (player.getOpenInventory().getTopInventory() != source)
            {
                return;
            }
            if (!hasAccess(player, guild))
            {
                player.closeInventory();
                player.sendMessage(module.messageComponent("guildNotFound"));
                return;
            }
            player.openInventory(build(player, guild, screen, target, page, names));
        }, null)));
    }

    /**
     * Builds a screen. A list screen shows the page closest to the given page that still exists.
     * A member or guest screen keeps the page so that the back button returns to it.
     */
    private Inventory build(Player player, Guild guild, Screen screen, UUID target, int page, Map<UUID, String> names)
    {
        Screen resolved = resolve(guild, player.getUniqueId(), screen, target);
        return switch (resolved)
        {
            case MAIN -> buildMain(player, guild);
            case MEMBERS -> buildMembers(guild, page, names);
            case MEMBER -> buildMember(player, guild, target, page, names);
            case GUESTS -> buildGuests(guild, page, names);
            case GUEST -> buildGuest(guild, target, page, names);
            case WARPS -> buildWarps(guild, page);
        };
    }

    /** Falls back to a parent screen when the viewer may not see the screen or its target is gone. */
    private Screen resolve(Guild guild, UUID viewer, Screen screen, UUID target)
    {
        return switch (screen)
        {
            case GUESTS -> guild.canManage(viewer) ? screen : Screen.MAIN;
            case GUEST ->
            {
                if (!guild.canManage(viewer))
                {
                    yield Screen.MAIN;
                }
                yield activeGuest(guild, target) == null ? Screen.GUESTS : screen;
            }
            case MEMBER -> target != null && guild.isMember(target) ? screen : Screen.MEMBERS;
            default -> screen;
        };
    }

    private Screen parent(Screen screen)
    {
        return switch (screen)
        {
            case MEMBER -> Screen.MEMBERS;
            case GUEST -> Screen.GUESTS;
            default -> Screen.MAIN;
        };
    }

    private List<UUID> subjects(Guild guild, Screen screen, UUID target, int page)
    {
        return switch (screen)
        {
            case MEMBERS -> page(sortedMembers(guild), page).stream().map(Member::getUuid).toList();
            case GUESTS -> page(sortedGuests(guild), page).stream().map(Guest::playerUuid).toList();
            case MEMBER, GUEST -> List.of(target);
            default -> List.of();
        };
    }

    private Inventory buildMain(Player player, Guild guild)
    {
        UUID viewer = player.getUniqueId();
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.MAIN, null, 0), SMALL_SIZE, title(guild.getName()));
        inventory.setItem(INFO_SLOT, item(Material.NAME_TAG, guild.getName(), NamedTextColor.GOLD, List.of(
                label("Your role", roleName(guild.getRole(viewer)), NamedTextColor.AQUA),
                label("Members", String.valueOf(guild.getMembers().size()), NamedTextColor.AQUA)
        ), null));
        inventory.setItem(WORLD_SLOT, item(Material.GRASS_BLOCK, "Go to world", NamedTextColor.GREEN, List.of(
                line("Teleport to the guild world spawn", NamedTextColor.GRAY)
        ), Action.WORLD));
        inventory.setItem(MEMBERS_SLOT, item(Material.PLAYER_HEAD, "Members", NamedTextColor.AQUA, List.of(
                line("See who is in the guild", NamedTextColor.GRAY)
        ), Action.MEMBERS));
        inventory.setItem(WARPS_SLOT, item(Material.ENDER_PEARL, "Warps", NamedTextColor.LIGHT_PURPLE, List.of(
                line("Teleport to a guild warp", NamedTextColor.GRAY)
        ), Action.WARPS));
        if (guild.canManage(viewer))
        {
            inventory.setItem(GUESTS_SLOT, item(Material.OAK_DOOR, "Guests", NamedTextColor.YELLOW, List.of(
                    line("Manage who can visit the guild world", NamedTextColor.GRAY)
            ), Action.GUESTS));
            inventory.setItem(SPAWN_SLOT, spawnItem(inGuildWorld(player, guild)));
        }
        return inventory;
    }

    private ItemStack spawnItem(boolean enabled)
    {
        if (enabled)
        {
            return item(Material.RED_BED, "Set world spawn here", NamedTextColor.GREEN, List.of(
                    line("Make your location the guild world spawn", NamedTextColor.GRAY)
            ), Action.SPAWN);
        }
        return item(Material.GRAY_DYE, "Set world spawn here", NamedTextColor.GRAY, List.of(
                line("Go to your guild world to use this", NamedTextColor.DARK_GRAY)
        ), Action.SPAWN);
    }

    private Inventory buildMembers(Guild guild, int page, Map<UUID, String> names)
    {
        List<Member> all = sortedMembers(guild);
        int current = clampPage(all.size(), page);
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.MEMBERS, null, current), LARGE_SIZE, title("Members"));
        Set<String> online = onlineNames();
        List<Member> members = page(all, current);
        for (int i = 0; i < members.size(); i++)
        {
            Member member = members.get(i);
            String name = names.getOrDefault(member.getUuid(), member.getUuid().toString());
            List<Component> lore = new ArrayList<>(memberLore(member, name, online));
            lore.add(Component.empty());
            lore.add(line("Click to open", NamedTextColor.YELLOW));
            inventory.setItem(i, head(member.getUuid(), name, lore, Action.OPEN));
        }
        setPageRow(inventory, current, all.size());
        return inventory;
    }

    private Inventory buildMember(Player player, Guild guild, UUID target, int page, Map<UUID, String> names)
    {
        UUID viewer = player.getUniqueId();
        String name = names.getOrDefault(target, target.toString());
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.MEMBER, target, page), SMALL_SIZE, title(name));
        inventory.setItem(HEAD_SLOT, head(target, name, memberLore(guild.getMember(target), name, onlineNames()), null));
        if (guild.canPromote(viewer, target))
        {
            boolean toOwner = guild.getRole(target) == GuildRole.OFFICER;
            inventory.setItem(FIRST_ACTION_SLOT, item(Material.EMERALD, toOwner ? "Make owner" : "Promote to officer", NamedTextColor.GREEN, List.of(
                    line(toOwner ? "You become an officer" : "Officers can invite, manage guests, and set warps", NamedTextColor.GRAY)
            ), Action.PROMOTE));
        }
        if (guild.canDemote(viewer, target))
        {
            inventory.setItem(SECOND_ACTION_SLOT, item(Material.REDSTONE, "Demote to member", NamedTextColor.GOLD, List.of(
                    line("Remove the officer role", NamedTextColor.GRAY)
            ), Action.DEMOTE));
        }
        if (guild.canKick(viewer, target))
        {
            inventory.setItem(THIRD_ACTION_SLOT, item(Material.BARRIER, "Kick", NamedTextColor.RED, List.of(
                    line("Remove this player from the guild", NamedTextColor.GRAY)
            ), Action.KICK));
        }
        inventory.setItem(SMALL_BACK_SLOT, backItem());
        return inventory;
    }

    private ItemStack confirmOwnerItem()
    {
        ItemStack itemStack = item(Material.GOLD_BLOCK, "Click again to confirm", NamedTextColor.GOLD, List.of(
                line("This player becomes the guild owner", NamedTextColor.GRAY),
                line("You become an officer", NamedTextColor.GRAY)
        ), Action.PROMOTE);
        itemStack.editMeta(meta -> meta.getPersistentDataContainer().set(CONFIRM_KEY, PersistentDataType.BOOLEAN, true));
        return itemStack;
    }

    private Inventory buildGuests(Guild guild, int page, Map<UUID, String> names)
    {
        List<Guest> all = sortedGuests(guild);
        int current = clampPage(all.size(), page);
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.GUESTS, null, current), LARGE_SIZE, title("Guests"));
        List<Guest> guests = page(all, current);
        for (int i = 0; i < guests.size(); i++)
        {
            Guest guest = guests.get(i);
            String name = names.getOrDefault(guest.playerUuid(), guest.playerUuid().toString());
            List<Component> lore = new ArrayList<>(guestLore(guest));
            lore.add(Component.empty());
            lore.add(line("Click to manage", NamedTextColor.YELLOW));
            inventory.setItem(i, head(guest.playerUuid(), name, lore, Action.OPEN));
        }
        if (guests.isEmpty())
        {
            inventory.setItem(EMPTY_SLOT, item(Material.GRAY_STAINED_GLASS_PANE, "No guests", NamedTextColor.GRAY, List.of(
                    line("Add one with /guild guest add <player>", NamedTextColor.DARK_GRAY)
            ), null));
        }
        setPageRow(inventory, current, all.size());
        return inventory;
    }

    private Inventory buildGuest(Guild guild, UUID target, int page, Map<UUID, String> names)
    {
        Guest guest = guild.getGuests().get(target);
        if (guest == null)
        {
            return buildGuests(guild, page, names);
        }
        String name = names.getOrDefault(target, target.toString());
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.GUEST, target, page), SMALL_SIZE, title(name));
        inventory.setItem(HEAD_SLOT, head(target, name, guestLore(guest), null));
        inventory.setItem(FIRST_ACTION_SLOT, guest.editing()
                ? item(Material.ENDER_EYE, "Switch to view", NamedTextColor.AQUA, List.of(
                        line("The guest can only look around", NamedTextColor.GRAY)), Action.MODE)
                : item(Material.BRICKS, "Switch to build", NamedTextColor.GREEN, List.of(
                        line("The guest can break, place, and interact", NamedTextColor.GRAY)), Action.MODE));
        inventory.setItem(SECOND_ACTION_SLOT, item(Material.CLOCK, "Extend", NamedTextColor.YELLOW, List.of(
                line("Set the time left to " + formatDuration(module.getGuestDefaultDuration()), NamedTextColor.GRAY)
        ), Action.EXTEND));
        inventory.setItem(THIRD_ACTION_SLOT, item(Material.BARRIER, "Remove", NamedTextColor.RED, List.of(
                line("End guest access now", NamedTextColor.GRAY)
        ), Action.REMOVE));
        inventory.setItem(SMALL_BACK_SLOT, backItem());
        return inventory;
    }

    private Inventory buildWarps(Guild guild, int page)
    {
        List<String> all = guild.getWarps().keySet().stream().sorted().toList();
        int current = clampPage(all.size(), page);
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, Screen.WARPS, null, current), LARGE_SIZE, title("Warps"));
        List<String> warps = page(all, current);
        for (int i = 0; i < warps.size(); i++)
        {
            String warp = warps.get(i);
            ItemStack itemStack = item(Material.ENDER_PEARL, warp, NamedTextColor.LIGHT_PURPLE, List.of(
                    line("Click to teleport", NamedTextColor.YELLOW)
            ), Action.WARP);
            itemStack.editMeta(meta -> meta.getPersistentDataContainer().set(WARP_KEY, PersistentDataType.STRING, warp));
            inventory.setItem(i, itemStack);
        }
        if (warps.isEmpty())
        {
            inventory.setItem(EMPTY_SLOT, item(Material.GRAY_STAINED_GLASS_PANE, "No warps", NamedTextColor.GRAY, List.of(
                    line("Officers can add one with /guild warp set <name>", NamedTextColor.DARK_GRAY)
            ), null));
        }
        setPageRow(inventory, current, all.size());
        return inventory;
    }

    /** Sets the bottom row of a list screen: the back button, and the page buttons when the list has more than one page. */
    private void setPageRow(Inventory inventory, int page, int size)
    {
        int pages = pageCount(size);
        if (page > 0)
        {
            inventory.setItem(PREVIOUS_SLOT, item(Material.SPECTRAL_ARROW, "Previous page", NamedTextColor.YELLOW, List.of(
                    line("Go to page " + page, NamedTextColor.GRAY)
            ), Action.PREVIOUS));
        }
        if (page < pages - 1)
        {
            inventory.setItem(NEXT_SLOT, item(Material.SPECTRAL_ARROW, "Next page", NamedTextColor.YELLOW, List.of(
                    line("Go to page " + (page + 2), NamedTextColor.GRAY)
            ), Action.NEXT));
        }
        if (pages > 1)
        {
            inventory.setItem(PAGE_SLOT, item(Material.PAPER, "Page " + (page + 1) + "/" + pages, NamedTextColor.GRAY, List.of(), null));
        }
        inventory.setItem(LARGE_BACK_SLOT, backItem());
    }

    private static int pageCount(int size)
    {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Returns the given page if it exists, or else the nearest page that exists. */
    private static int clampPage(int size, int page)
    {
        return Math.max(0, Math.min(page, pageCount(size) - 1));
    }

    private static <T> List<T> page(List<T> list, int page)
    {
        int from = clampPage(list.size(), page) * PAGE_SIZE;
        return list.subList(from, Math.min(list.size(), from + PAGE_SIZE));
    }

    private List<Member> sortedMembers(Guild guild)
    {
        return guild.getMembers().stream().sorted(Comparator.comparing(Member::getRole)).toList();
    }

    private List<Guest> sortedGuests(Guild guild)
    {
        Instant now = Instant.now();
        return guild.getGuests().values().stream().filter(guest -> guest.isActive(now))
                .sorted(Comparator.comparing(Guest::expiresAt).thenComparing(Guest::playerUuid)).toList();
    }

    private List<Component> memberLore(Member member, String name, Set<String> online)
    {
        boolean isOnline = online.contains(name.toLowerCase(Locale.ROOT));
        return List.of(
                label("Role", roleName(member == null ? null : member.getRole()), NamedTextColor.AQUA),
                label("Status", isOnline ? "Online" : "Offline", isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY)
        );
    }

    private List<Component> guestLore(Guest guest)
    {
        return List.of(
                label("Mode", guest.editing() ? "Build" : "View", guest.editing() ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                label("Time left", formatDuration(Duration.between(Instant.now(), guest.expiresAt())), NamedTextColor.YELLOW)
        );
    }

    private Guest activeGuest(Guild guild, UUID target)
    {
        return target == null ? null : guild.getActiveGuest(target);
    }

    private boolean hasAccess(Player player, Guild guild)
    {
        return module.getGuildHolder().guild(player.getUniqueId()).orElse(null) == guild && guild.isMember(player.getUniqueId());
    }

    private boolean inGuildWorld(Player player, Guild guild)
    {
        return player.getWorld().getName().equals(guild.getWorldName());
    }

    private void close(Player player, Inventory source)
    {
        module.ownTask(player.getScheduler().run(module.plugin(), task ->
        {
            if (player.getOpenInventory().getTopInventory() == source)
            {
                player.closeInventory();
            }
        }, null));
    }

    private Set<String> onlineNames()
    {
        Set<String> names = new HashSet<>();
        module.api().players().onlineNames().forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        return names;
    }

    private CompletableFuture<String> name(UUID uuid)
    {
        return module.api().players().player(uuid)
                .thenApply(player -> player.map(PlexPlayerView::name).orElse(uuid.toString()))
                .exceptionally(failure ->
                {
                    module.getLogger().error("Failed to look up the name of {}", uuid, failure);
                    return uuid.toString();
                });
    }

    private CompletableFuture<Map<UUID, String>> names(List<UUID> uuids)
    {
        List<CompletableFuture<String>> futures = uuids.stream().map(this::name).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(unused ->
        {
            Map<UUID, String> names = new HashMap<>();
            for (int i = 0; i < uuids.size(); i++)
            {
                names.put(uuids.get(i), futures.get(i).join());
            }
            return names;
        });
    }

    private static String roleName(GuildRole role)
    {
        if (role == null)
        {
            return "None";
        }
        String name = role.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Formats a duration as the two largest units, for example "3h 12m" or "2d 4h". */
    private static String formatDuration(Duration duration)
    {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / 1440;
        long hours = minutes / 60 % 24;
        long rest = minutes % 60;
        if (days > 0)
        {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0)
        {
            return rest > 0 ? hours + "h " + rest + "m" : hours + "h";
        }
        return rest > 0 ? rest + "m" : "<1m";
    }

    private Action action(ItemStack itemStack)
    {
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (value == null)
        {
            return null;
        }
        try
        {
            return Action.valueOf(value);
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }

    private UUID target(ItemStack itemStack)
    {
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(TARGET_KEY, PersistentDataType.STRING);
        return value == null ? null : UUID.fromString(value);
    }

    private ItemStack backItem()
    {
        return item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of(), Action.BACK);
    }

    private ItemStack head(UUID uuid, String name, List<Component> lore, Action action)
    {
        ItemStack itemStack = item(Material.PLAYER_HEAD, name, NamedTextColor.AQUA, lore, action);
        itemStack.editMeta(SkullMeta.class, meta ->
        {
            meta.setPlayerProfile(Bukkit.createProfile(uuid));
            meta.getPersistentDataContainer().set(TARGET_KEY, PersistentDataType.STRING, uuid.toString());
        });
        return itemStack;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, Action action)
    {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(text(name, color));
        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.values());
        if (action != null)
        {
            itemMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action.name());
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private Component title(String value)
    {
        return text(value, NamedTextColor.DARK_AQUA);
    }

    private Component text(String value, NamedTextColor color)
    {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component line(String value, NamedTextColor color)
    {
        return text("> " + value, color);
    }

    private Component label(String label, String value, NamedTextColor valueColor)
    {
        return text(label + ": ", NamedTextColor.GRAY).append(text(value, valueColor));
    }
}
