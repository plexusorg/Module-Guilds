package dev.plex.guild;

import dev.plex.Guilds;
import dev.plex.hook.GuildOasisHook;
import dev.plex.storage.HistoryPurge;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Sends the recorded history purges to Oasis, one at a time, and deletes each row when its purge ends.
 * Without Oasis the rows stay. A refusal stops the drain, and the rows wait for the next hourly step.
 */
public final class HistoryPurgeQueue
{
    private final Guilds module;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean requested = new AtomicBoolean();
    // Only the running drain reads or sets the hook.
    private GuildOasisHook oasis;

    public HistoryPurgeQueue(Guilds module)
    {
        this.module = module;
    }

    /** Starts a drain. A call during a drain makes that drain run once more when it ends. */
    public void drain()
    {
        requested.set(true);
        if (!running.compareAndSet(false, true))
        {
            return;
        }
        requested.set(false);
        CompletableFuture<Void> run;
        try
        {
            GuildOasisHook hook = hook();
            run = hook == null ? CompletableFuture.completedFuture(null)
                    : module.getGuildRepository().historyPurges().thenCompose(rows -> purge(hook, rows, 0));
        }
        catch (RuntimeException exception)
        {
            run = CompletableFuture.failedFuture(exception);
        }
        run.whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                if (cause instanceof GuildOasisHook.Refused)
                {
                    module.getLogger().warn("Oasis refused a guild world history purge ({}). The module tries again in an hour.", cause.getMessage());
                }
                else
                {
                    module.getLogger().error("Failed to purge guild world history", failure);
                }
                requested.set(false);
            }
            running.set(false);
            if (requested.get())
            {
                drain();
            }
        });
    }

    private @Nullable GuildOasisHook hook()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oasis"))
        {
            return null;
        }
        if (oasis == null)
        {
            oasis = new GuildOasisHook(module.plugin());
        }
        return oasis;
    }

    private CompletableFuture<Void> purge(GuildOasisHook hook, List<HistoryPurge> rows, int index)
    {
        if (index == rows.size())
        {
            return CompletableFuture.completedFuture(null);
        }
        HistoryPurge row = rows.get(index);
        CompletableFuture<Long> purged = new CompletableFuture<>();
        // Start the purge on the main thread, where Oasis also completes its futures.
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task ->
        {
            try
            {
                hook.purge(row.world(), Instant.ofEpochMilli(row.untilAt())).whenComplete((deleted, failure) ->
                {
                    if (failure != null)
                    {
                        purged.completeExceptionally(failure);
                    }
                    else
                    {
                        purged.complete(deleted);
                    }
                });
            }
            catch (RuntimeException exception)
            {
                purged.completeExceptionally(exception);
            }
        }));
        return purged.thenComposeAsync(deleted ->
        {
            module.api().logging().debug("Purged {0} Oasis edits of {1}", deleted, row.world());
            return module.getGuildRepository().deleteHistoryPurges(row.world(), row.untilAt());
        }, module.executor()).thenCompose(unused -> purge(hook, rows, index + 1));
    }
}
