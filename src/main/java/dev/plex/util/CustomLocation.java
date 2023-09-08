package dev.plex.util;

import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;

@Data
public class CustomLocation
{
    private final String worldName;
    private final double x, y, z;
    private final float yaw, pitch;

    public static CustomLocation fromLocation(Location location)
    {
        return new CustomLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation()
    {
        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }
}
