package dev.plex.hook;

import com.oasis.api.Oasis;
import com.oasis.api.OasisApi;
import com.oasis.api.OasisException;
import com.oasis.api.Selection;
import com.oasis.api.WorldLoader;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.world.GuildWorldService;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** The only class that uses the Oasis API. Create it only while Oasis is enabled. */
public final class GuildOasisHook
{
    private static final Pattern WORLD_NAME = Pattern.compile("guild_[0-9a-f]{32}");
    private final Plugin plugin;
    private final OasisApi oasis;
    private @Nullable WorldLoader loader;

    public GuildOasisHook(Plugin plugin)
    {
        this.plugin = plugin;
        this.oasis = Oasis.api(plugin);
    }

    /**
     * Lets Oasis load the world of each guild that exists, so a rollback writes into a guild world that is not loaded.
     * Oasis holds the world while its operation runs, and the idle unload leaves a held world loaded. Until the guilds
     * have loaded, the loader owns every guild world name, and a load waits for the guilds: a recovery at startup can
     * ask for a guild world before the module has read its guilds.
     */
    public void loadGuildWorlds(GuildHolder guilds, GuildWorldService worlds, CompletableFuture<Void> guildsLoaded)
    {
        WorldLoader guildWorlds = new WorldLoader()
        {
            @Override
            public boolean owns(String world)
            {
                return WORLD_NAME.matcher(world).matches() && (!guildsLoaded.isDone() || guild(world).isPresent());
            }

            @Override
            public CompletableFuture<World> load(String world)
            {
                return guildsLoaded.thenComposeAsync(unused -> guild(world).map(worlds::holdWorld)
                        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("No guild owns the world " + world))),
                        task -> Bukkit.getGlobalRegionScheduler().execute(plugin, task));
            }

            @Override
            public void release(String world)
            {
                worlds.releaseWorld(world);
            }

            private Optional<Guild> guild(String world)
            {
                return WORLD_NAME.matcher(world).matches() ? guilds.guildByWorld(world) : Optional.empty();
            }
        };
        oasis.worlds().register(guildWorlds);
        loader = guildWorlds;
    }

    /** Stops Oasis from loading guild worlds. A rollback that holds one still releases it when it ends. */
    public void stopLoadingGuildWorlds()
    {
        if (loader != null)
        {
            oasis.worlds().unregister(loader);
            loader = null;
        }
    }

    /**
     * Purges the whole history of a guild world up to the bound and returns the count of deleted edits.
     * Call it on the main thread. The result completes there, or with a Refused failure when Oasis refuses or fails.
     */
    public CompletableFuture<Long> purge(String world, Instant until)
    {
        // Oasis does not check the name, and a wrong name purges a real world.
        if (!WORLD_NAME.matcher(world).matches())
        {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Not a guild world: " + world));
        }
        return oasis.purges().purge(Selection.all().in(world, null).until(until)).start().result().handle((purged, failure) ->
        {
            if (failure == null)
            {
                return purged.deleted();
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause instanceof OasisException refusal)
            {
                throw new Refused(refusal.failure().reason() + ": " + refusal.failure().message());
            }
            throw new CompletionException(cause);
        });
    }

    /** Oasis refused or failed a purge. The message holds the reason. */
    public static final class Refused extends RuntimeException
    {
        private Refused(String message)
        {
            super(message);
        }
    }
}
