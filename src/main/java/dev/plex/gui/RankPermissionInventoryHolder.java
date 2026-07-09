package dev.plex.gui;

import dev.plex.guild.Guild;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public record RankPermissionInventoryHolder(Guild guild, String rankName) implements InventoryHolder
{
    @Override
    public @NotNull Inventory getInventory()
    {
        throw new UnsupportedOperationException();
    }
}
