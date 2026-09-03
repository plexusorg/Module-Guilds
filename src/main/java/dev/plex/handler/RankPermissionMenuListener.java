package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.gui.RankPermissionInventoryHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class RankPermissionMenuListener implements Listener
{
    private final Guilds module;
    private static final UUID MEMBER_PERMISSION_PROBE = new UUID(0L, 0L);

    public RankPermissionMenuListener(Guilds module)
    {
        this.module = module;
    }

    public void openRankList(Player player, Guild guild)
    {
        Inventory inventory = Bukkit.createInventory(new RankPermissionInventoryHolder(guild, null), 27, title("* Guild Rank Permissions"));
        inventory.setItem(13, rankItem());
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event)
    {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof RankPermissionInventoryHolder holder))
        {
            return;
        }
        event.setCancelled(true);
        if (!holder.guild().isOwner(player.getUniqueId()))
        {
            player.closeInventory();
            player.sendMessage(module.messageComponent("guildNotOwner"));
            return;
        }
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
        {
            return;
        }
        if (holder.rankName() == null)
        {
            openPermissionEditor(player, holder.guild());
            return;
        }
        GuildPermission permission = permissionBySlot(event.getRawSlot());
        if (permission == null)
        {
            return;
        }
        module.getGuildMutationService().toggleMemberPermission(holder.guild(), permission)
                .whenComplete((enabled, failure) ->
                        module.scheduler().runEntity(player, () ->
                {
                    if (failure != null)
                    {
                        player.sendMessage(module.messageComponent("guildStorageFailed"));
                        return;
                    }
                    openPermissionEditor(player, holder.guild());
                }));
    }

    private void openPermissionEditor(Player player, Guild guild)
    {
        Inventory inventory = Bukkit.createInventory(new RankPermissionInventoryHolder(guild, "MEMBER"), 27, title("* Member Permissions"));
        GuildPermission[] permissions = GuildPermission.values();
        for (int i = 0; i < permissions.length; i++)
        {
            inventory.setItem(11 + i * 2, permissionItem(guild, permissions[i]));
        }
        player.openInventory(inventory);
    }

    private GuildPermission permissionBySlot(int slot)
    {
        return switch (slot)
        {
            case 11 -> GuildPermission.BLOCK_BREAKING;
            case 13 -> GuildPermission.BLOCK_PLACING;
            case 15 -> GuildPermission.INTERACTING;
            default -> null;
        };
    }

    private ItemStack rankItem()
    {
        ItemStack itemStack = new ItemStack(Material.NAME_TAG);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(text("Member", NamedTextColor.AQUA));
        itemMeta.lore(List.of(
                line("Manage member guild world access", NamedTextColor.GRAY),
                line("Click to edit permissions", NamedTextColor.YELLOW)
        ));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack permissionItem(Guild guild, GuildPermission permission)
    {
        ItemStack itemStack = new ItemStack(permission.material());
        ItemMeta itemMeta = itemStack.getItemMeta();
        boolean enabled = guild.hasPermission(MEMBER_PERMISSION_PROBE, permission);
        itemMeta.displayName(text(permission.displayName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        itemMeta.lore(List.of(
                text("Status: ", NamedTextColor.GRAY).append(text(enabled ? "Enabled" : "Disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)),
                Component.empty(),
                line("Click to toggle", NamedTextColor.YELLOW)
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
