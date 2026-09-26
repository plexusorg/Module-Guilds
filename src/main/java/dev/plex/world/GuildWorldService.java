package dev.plex.world;

import dev.plex.guild.Guild;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * ASP-free contract for guild world support.
 *
 * <p>Keeping ASP types out of this interface lets the rest of the module load on
 * servers that do not provide Advanced Slime Paper.</p>
 */
public interface GuildWorldService
{
    void enable();

    CompletableFuture<World> ensureWorld(Guild guild);

    CompletableFuture<World> generateWorld(Guild guild, GuildWorldType type, java.util.UUID actor);

    default CompletableFuture<Void> resetWorld(Guild guild)
    {
        return resetWorld(guild, null);
    }

    CompletableFuture<Void> resetWorld(Guild guild, java.util.UUID actor);

    boolean isResetting(String worldName);

    /**
     * Finds a safe place to stand at the x and z of the location. Moves up out of blocks, then down onto the ground.
     * Returns the location unchanged when it is already safe.
     */
    CompletableFuture<Location> safeLocation(Location location);

    /**
     * Permanently deletes the guild world with no backup. Evacuates its players, unloads it without a save,
     * and removes its file. The world name stays closed after this call, also when the deletion fails.
     * Completes normally when the world does not exist.
     */
    CompletableFuture<Void> deleteWorld(Guild guild);

    void disable();
}
