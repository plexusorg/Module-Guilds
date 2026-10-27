package dev.plex.world;

import dev.plex.guild.Guild;
import dev.plex.guild.Actor;
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

    /**
     * Loads the world as ensureWorld does and keeps it loaded until releaseWorld: the idle unload skips a held world.
     * A hold whose future fails ends by itself; every other hold needs one releaseWorld.
     */
    CompletableFuture<World> holdWorld(Guild guild);

    /** Ends one hold of the world. The idle unload counts the world again once no hold is left. */
    void releaseWorld(String worldName);

    CompletableFuture<Void> applySettings(Guild guild);

    CompletableFuture<World> generateWorld(Guild guild, GuildWorldType type, Actor actor);

    CompletableFuture<Void> resetWorld(Guild guild, Actor actor);

    boolean isResetting(String worldName);

    /**
     * Finds a safe place to stand at the x and z of the location. Moves up out of blocks, then down onto the ground.
     * Returns the location unchanged when it is already safe.
     */
    CompletableFuture<Location> safeLocation(Location location);

    /**
     * Closes the world name, then unloads the world and waits for its saves. ensureWorld, generateWorld, and resetWorld
     * refuse a closed name. The world file stays.
     */
    CompletableFuture<Void> closeWorld(Guild guild);

    /** Closes the world, then deletes its file, reset journal, and reset backups. The name stays closed. */
    CompletableFuture<Void> deleteWorld(Guild guild);

    /** Opens a closed world name again. */
    void openWorld(String worldName);

    CompletableFuture<Boolean> hasBackup(String backup);

    CompletableFuture<String> restoreBackup(Guild guild, String backup);

    void disable();
}
