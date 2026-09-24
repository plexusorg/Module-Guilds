package dev.plex.handler;

import org.bukkit.Bukkit;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.gui.RankPermissionInventoryHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.CompletionException;

public class RankPermissionMenuListener implements Listener
{
    private final Guilds module;

    public RankPermissionMenuListener(Guilds module)
    {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof RankPermissionInventoryHolder holder))
        {
            return;
        }
        event.setCancelled(true);
        Inventory inventory = event.getInventory();
        if (!holder.guild().isOwner(player.getUniqueId()))
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == inventory)
                {
                    player.closeInventory();
                }
            }, null));
            player.sendMessage(module.messageComponent("guildNotOwner"));
            return;
        }
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
        {
            return;
        }
        if (slot == 26)
        {
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == inventory
                        && holder.guild().isMember(player.getUniqueId()))
                {
                    module.getGuildMenuListener().openHome(player, holder.guild());
                }
            }, null));
            return;
        }
        GuildPermission permission = permissionBySlot(slot);
        if (permission == null)
        {
            return;
        }
        module.getGuildMutationService().toggleMemberPermission(holder.guild(), player.getUniqueId(), permission)
                .whenComplete((enabled, failure) ->
        {
            if (failure != null)
            {
                if (failure.getCause() instanceof SecurityException)
                {
                    player.sendMessage(module.messageComponent("guildNotOwner"));
                    return;
                }
                module.getLogger().error("Failed to update guild member permission {}", permission, failure);
                player.sendMessage(module.messageComponent("guildStorageFailed"));
                return;
            }
            module.ownTask(player.getScheduler().run(module.plugin(), task ->
            {
                if (player.getOpenInventory().getTopInventory() == inventory
                        && holder.guild().isOwner(player.getUniqueId()))
                {
                    inventory.setItem(slot, permissionItem(holder.guild(), permission));
                }
            }, null));
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event)
    {
        if (event.getInventory().getHolder() instanceof RankPermissionInventoryHolder
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize()))
        {
            event.setCancelled(true);
        }
    }

    public void openPermissions(Player player, Guild guild)
    {
        Inventory inventory = Bukkit.createInventory(new RankPermissionInventoryHolder(guild), 27, title("* Member Permissions"));
        GuildPermission[] permissions = GuildPermission.values();
        for (int i = 0; i < permissions.length; i++)
        {
            inventory.setItem(11 + i * 2, permissionItem(guild, permissions[i]));
        }
        inventory.setItem(26, backItem());
        player.openInventory(inventory);
    }

    private GuildPermission permissionBySlot(int slot)
    {
        return switch (slot)
        {
            case 11 -> GuildPermission.BLOCK_BREAKING;
            case 13 -> GuildPermission.BLOCK_PLACING;
            case 15 -> GuildPermission.INTERACTING;
            case 17 -> GuildPermission.MANAGE_GUESTS;
            default -> null;
        };
    }

    private ItemStack backItem()
    {
        ItemStack itemStack = new ItemStack(Material.ARROW);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(text("Back", NamedTextColor.YELLOW));
        itemMeta.lore(List.of(line("Click to return to the guild menu", NamedTextColor.GRAY)));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack permissionItem(Guild guild, GuildPermission permission)
    {
        ItemStack itemStack = new ItemStack(permission.material());
        ItemMeta itemMeta = itemStack.getItemMeta();
        boolean enabled = guild.isMemberPermissionEnabled(permission);
        itemMeta.displayName(text(permission.displayName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        itemMeta.lore(List.of(
                text("Status: ", NamedTextColor.GRAY).append(text(enabled ? "Enabled" : "Disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)),
                Component.empty(),
                line(enabled ? "Click to deny" : "Click to allow", NamedTextColor.YELLOW)
        ));
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
}
