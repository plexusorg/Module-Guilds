package dev.plex.guild.data;

import org.bukkit.Material;

public enum GuildPermission
{
    BLOCK_BREAKING("Block Breaking", Material.DIAMOND_PICKAXE),
    BLOCK_PLACING("Block Placing", Material.GRASS_BLOCK),
    INTERACTING("Interacting", Material.OAK_BUTTON);

    private final String displayName;
    private final Material material;

    GuildPermission(String displayName, Material material)
    {
        this.displayName = displayName;
        this.material = material;
    }

    public String displayName()
    {
        return displayName;
    }

    public Material material()
    {
        return material;
    }
}
