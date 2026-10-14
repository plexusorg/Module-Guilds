package dev.plex.hook;

import com.oasis.api.Oasis;
import com.oasis.api.OasisApi;
import com.oasis.api.OasisException;
import com.oasis.api.Selection;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

/** The only class that uses the Oasis API. Create it only while Oasis is enabled. */
public final class GuildOasisHook
{
    private static final Pattern WORLD_NAME = Pattern.compile("guild_[0-9a-f]{32}");
    private final OasisApi oasis;

    public GuildOasisHook(Plugin plugin)
    {
        this.oasis = Oasis.api(plugin);
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
