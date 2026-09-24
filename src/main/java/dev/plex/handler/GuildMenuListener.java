package dev.plex.handler;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMutationService;
import dev.plex.guild.data.Member;
import dev.plex.gui.GuildMenuInventoryHolder;
import dev.plex.gui.GuildMenuView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GuildMenuListener implements Listener
{
    private final Guilds module;
    private final GuildMutationService mutationService;
    private static final int MEMBERS_SLOT = 11;
    private static final int WORLD_SLOT = 13;
    private static final int PERMISSIONS_SLOT = 15;
    private static final int BACK_SLOT = 26;
    private static final int MEMBERS_BACK_SLOT = 53;
    private static final int KICK_SLOT = 11;
    private static final int OWNER_SLOT = 13;
    private static final int RANK_PERMISSIONS_SLOT = 15;

    public GuildMenuListener(Guilds module, GuildMutationService mutationService)
    {
        this.module = module;
        this.mutationService = mutationService;
    }

    public void openHome(Player player, Guild guild)
    {
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, GuildMenuView.HOME, null), 27, title("* " + guild.getName()));
        inventory.setItem(MEMBERS_SLOT, item(Material.PLAYER_HEAD, "Guild Members", NamedTextColor.AQUA, List.of(
                line("View online status", NamedTextColor.GRAY),
                line("Click to manage members", NamedTextColor.YELLOW)
        )));
        inventory.setItem(WORLD_SLOT, item(Material.GRASS_BLOCK, "Guild World", NamedTextColor.GREEN, List.of(
                line("Load and enter your guild world", NamedTextColor.GRAY),
                line("Only guild members can access it", NamedTextColor.DARK_AQUA)
        )));
        if (guild.isOwner(player.getUniqueId()))
        {
            inventory.setItem(PERMISSIONS_SLOT, item(Material.COMPARATOR, "Member Permissions", NamedTextColor.GOLD, List.of(
                    line("Configure member build permissions", NamedTextColor.GRAY),
                    line("Break - Place - Interact", NamedTextColor.YELLOW)
            )));
        }
        player.openInventory(inventory);
    }

    private void openMembers(Player player, Guild guild, Inventory source)
    {
        List<Member> members = List.copyOf(guild.getMembers());
        List<CompletableFuture<String>> names = members.stream().limit(45).map(this::memberName).toList();
        CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to load guild member names", failure);
                player.sendMessage(module.messageComponent("guildStorageFailed"));
                return;
            }
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source && guild.isMember(player.getUniqueId()))
                {
                    openMembers(player, guild, members, names);
                }
            }, null));
        });
    }

    private void openMembers(Player player, Guild guild, List<Member> members, List<CompletableFuture<String>> names)
    {
        Set<String> onlineNames = new HashSet<>(module.api().players().onlineNames());
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, GuildMenuView.MEMBERS, null), 54, title("* " + guild.getName() + " Members"));
        for (int i = 0; i < members.size() && i < 45; i++)
        {
            String name = names.get(i).join();
            inventory.setItem(i, memberItem(guild, members.get(i), name, onlineNames.stream().anyMatch(name::equalsIgnoreCase)));
        }
        inventory.setItem(MEMBERS_BACK_SLOT, item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of(line("Return to the guild menu", NamedTextColor.GRAY))));
        player.openInventory(inventory);
    }

    private void openMember(Player player, Guild guild, Member member, Inventory source)
    {
        memberName(member).whenComplete((name, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to load guild member name", failure);
                player.sendMessage(module.messageComponent("guildStorageFailed"));
                return;
            }
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source && guild.isMember(player.getUniqueId()))
                {
                    openMember(player, guild, member, name);
                }
            }, null));
        });
    }

    private void openMember(Player player, Guild guild, Member member, String name)
    {
        Inventory inventory = Bukkit.createInventory(new GuildMenuInventoryHolder(guild, GuildMenuView.MEMBER, member.getUuid()), 27, title("* " + name));
        boolean online = module.api().players().onlineNames().stream().anyMatch(name::equalsIgnoreCase);
        inventory.setItem(4, memberItem(guild, member, name, online));
        if (guild.isOwner(player.getUniqueId()) && !guild.isOwner(member.getUuid()))
        {
            inventory.setItem(KICK_SLOT, item(Material.BARRIER, "Kick Member", NamedTextColor.RED, List.of(
                    line("Remove this player from the guild", NamedTextColor.GRAY),
                    line("They will lose guild world access", NamedTextColor.DARK_RED)
            )));
            inventory.setItem(OWNER_SLOT, item(Material.GOLDEN_HELMET, "Transfer Ownership", NamedTextColor.GOLD, List.of(
                    line("Make this player the guild owner", NamedTextColor.GRAY),
                    line("You will become a regular member", NamedTextColor.YELLOW)
            )));
        }
        if (guild.isOwner(player.getUniqueId()))
        {
            inventory.setItem(RANK_PERMISSIONS_SLOT, item(Material.COMPARATOR, "Member Permissions", NamedTextColor.AQUA, List.of(
                    line("Edit permissions for all members", NamedTextColor.GRAY)
            )));
        }
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of(line("Return to member list", NamedTextColor.GRAY))));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof GuildMenuInventoryHolder holder))
        {
            return;
        }
        event.setCancelled(true);
        if (!holder.guild().isMember(player.getUniqueId()))
        {
            Inventory inventory = event.getInventory();
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == inventory)
                {
                    player.closeInventory();
                }
            }, null));
            player.sendMessage(module.messageComponent("guildNotFound"));
            return;
        }
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
        {
            return;
        }
        switch (holder.view())
        {
            case HOME -> clickHome(player, holder.guild(), event.getRawSlot(), event.getInventory());
            case MEMBERS -> clickMembers(player, holder.guild(), clickedItem, event.getRawSlot(), event.getInventory());
            case MEMBER -> clickMember(player, holder.guild(), holder.memberUuid(), event.getRawSlot(), event.getInventory());
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

    private void clickHome(Player player, Guild guild, int slot, Inventory source)
    {
        if (slot == MEMBERS_SLOT)
        {
            openMembers(player, guild, source);
            return;
        }
        if (slot == WORLD_SLOT)
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source)
                {
                    player.closeInventory();
                }
            }, null));
            if (!module.isGuildWorldsEnabled())
            {
                player.sendMessage(module.messageComponent("guildWorldsUnavailable"));
                return;
            }
            module.getGuildWorldService().ensureWorld(guild).whenComplete((world, throwable) ->
                    module.ownTask(player.getScheduler().run(module.plugin(), task ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(module.messageComponent("guildWorldLoadFailed"));
                        return;
                    }
                    player.teleportAsync(world.getSpawnLocation().toCenterLocation());
                }, null)));
            return;
        }
        if (slot == PERMISSIONS_SLOT && guild.isOwner(player.getUniqueId()))
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source && guild.isOwner(player.getUniqueId()))
                {
                    module.getRankPermissionMenuListener().openPermissions(player, guild);
                }
            }, null));
        }
    }

    private void clickMembers(Player player, Guild guild, ItemStack clickedItem, int slot, Inventory source)
    {
        if (slot == MEMBERS_BACK_SLOT)
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source && guild.isMember(player.getUniqueId()))
                {
                    openHome(player, guild);
                }
            }, null));
            return;
        }
        memberUuid(clickedItem).map(guild::getMember).filter(Objects::nonNull).ifPresent(member -> openMember(player, guild, member, source));
    }

    private void clickMember(Player player, Guild guild, UUID memberUuid, int slot, Inventory source)
    {
        Member member = guild.getMember(memberUuid);
        if (member == null)
        {
            openMembers(player, guild, source);
            return;
        }
        if (slot == BACK_SLOT)
        {
            openMembers(player, guild, source);
            return;
        }
        if (!guild.isOwner(player.getUniqueId()))
        {
            return;
        }
        if (slot == KICK_SLOT && !guild.isOwner(memberUuid))
        {
            memberName(member).thenCompose(name -> mutationService.removeMember(guild, memberUuid).thenApply(unused -> name))
                    .whenComplete((name, throwable) ->
            {
                if (throwable != null)
                {
                    module.getLogger().error("Failed to remove guild member {}", memberUuid, throwable);
                    player.sendMessage(module.messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(module.messageComponent("guildMemberKicked", Placeholder.unparsed("player", name)));
                openMembers(player, guild, source);
            });
            return;
        }
        if (slot == OWNER_SLOT && !guild.isOwner(memberUuid))
        {
            Member previousOwner = guild.getMember(player.getUniqueId());
            memberName(member).thenCompose(name -> mutationService.transferOwnership(
                            guild, member, player.getUniqueId(), previousOwner).thenApply(unused -> name))
                    .whenComplete((name, throwable) ->
            {
                if (throwable != null)
                {
                    module.getLogger().error("Failed to transfer guild ownership to {}", memberUuid, throwable);
                    player.sendMessage(module.messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(module.messageComponent("guildOwnerSet", Placeholder.unparsed("player", name)));
                openMember(player, guild, previousOwner == null ? member : previousOwner, source);
            });
            return;
        }
        if (slot == RANK_PERMISSIONS_SLOT)
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == source && guild.isOwner(player.getUniqueId()))
                {
                    module.getRankPermissionMenuListener().openPermissions(player, guild);
                }
            }, null));
        }
    }

    private CompletableFuture<String> memberName(Member member)
    {
        return module.api().players().player(member.getUuid())
                .thenApply(player -> player.map(dev.plex.api.player.PlexPlayerView::name)
                        .orElse(member.getUuid().toString()));
    }

    private ItemStack memberItem(Guild guild, Member member, String name, boolean online)
    {
        ItemStack itemStack = new ItemStack(online ? Material.LIME_WOOL : Material.GRAY_WOOL);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(text(name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(label("Status", online ? "Online" : "Offline", online ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(label("Role", guild.isOwner(member.getUuid()) ? "Owner" : "Member", guild.isOwner(member.getUuid()) ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        lore.add(label("Rank", guild.isOwner(member.getUuid()) ? "Owner" : "Member", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(line("Click to manage", NamedTextColor.YELLOW));
        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.values());
        itemMeta.getPersistentDataContainer().set(Keys.MEMBER_UUID, org.bukkit.persistence.PersistentDataType.STRING, member.getUuid().toString());
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore)
    {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(text(name, color));
        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.values());
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

    private java.util.Optional<UUID> memberUuid(ItemStack itemStack)
    {
        if (!itemStack.hasItemMeta())
        {
            return java.util.Optional.empty();
        }
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(Keys.MEMBER_UUID, org.bukkit.persistence.PersistentDataType.STRING);
        if (value == null)
        {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(UUID.fromString(value));
    }

    private static class Keys
    {
        private static final NamespacedKey MEMBER_UUID = new NamespacedKey("plex_guilds", "member_uuid");
    }
}
