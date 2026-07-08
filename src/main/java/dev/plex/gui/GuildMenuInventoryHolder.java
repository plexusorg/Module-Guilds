package dev.plex.gui;

import dev.plex.guild.Guild;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record GuildMenuInventoryHolder(Guild guild, GuildMenuView view, UUID memberUuid) implements InventoryHolder
{
    @Override
    public @NotNull Inventory getInventory()
    {
        throw new UnsupportedOperationException();
    }
}
