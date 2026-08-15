package dev.plex.world;

import dev.plex.guild.Guild;
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

    void saveWorld(Guild guild);

    void ejectNonMembers(Guild guild);

    void disable();
}
