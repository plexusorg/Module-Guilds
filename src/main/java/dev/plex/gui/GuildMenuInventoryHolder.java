package dev.plex.gui;

import dev.plex.guild.Guild;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks a guild menu inventory. The target is the member or guest that the screen shows, or null.
 * The page is the page that a list screen shows. On a member or guest screen, it is the page of the list to go back to.
 */
public record GuildMenuInventoryHolder(Guild guild, Screen screen, UUID target, int page) implements InventoryHolder
{
    public enum Screen
    {
        MAIN,
        MEMBERS,
        MEMBER,
        GUESTS,
        GUEST,
        WARPS
    }

    @Override
    public @NotNull Inventory getInventory()
    {
        throw new UnsupportedOperationException();
    }
}
