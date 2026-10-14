package dev.plex.handler;

import dev.plex.Guilds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GuildSessionListener implements Listener
{
    private final Guilds module;
    private final Map<UUID, CompletableFuture<Long>> sessions = new ConcurrentHashMap<>();
    private CompletableFuture<Void> startup;

    public GuildSessionListener(Guilds module)
    {
        this.module = module;
    }

    public CompletableFuture<Void> start()
    {
        startup = module.getGuildRepository().closeOpenSessions(System.currentTimeMillis());
        Bukkit.getOnlinePlayers().forEach(this::open);
        module.ownTask(Bukkit.getAsyncScheduler().runAtFixedRate(module.plugin(), task -> purge(), 1, 1, TimeUnit.HOURS));
        return startup;
    }

    /** The hourly step: finalize due guilds, purge old recovery data, then drain the history purges. Guilds runs it once after the load. */
    public void purge()
    {
        module.getGuildMutationService().finalizeDeletions()
                .thenCompose(unused -> module.getGuildRepository().purgeRecovery(module.getRecoveryCutoff()))
                .whenComplete((unused, failure) ->
                {
                    if (failure != null)
                    {
                        module.getLogger().error("Failed to purge guild recovery data", failure);
                    }
                    module.getHistoryPurgeQueue().drain();
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        open(event.getPlayer());
    }

    private void open(Player player)
    {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        String name = player.getName();
        sessions.put(id, startup.thenCompose(unused -> module.getGuildRepository().openSession(id, name, now)).exceptionally(failure ->
        {
            module.getLogger().error("Failed to open a guild session for {}", id, failure);
            return null;
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        module.getGuildRecoveryService().clearPlayer(event.getPlayer().getUniqueId());
        CompletableFuture<Long> session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null)
        {
            long now = System.currentTimeMillis();
            session.thenCompose(id -> id == null ? CompletableFuture.<Void>completedFuture(null) : module.getGuildRepository().closeSession(id, now)).exceptionally(failure ->
            {
                module.getLogger().error("Failed to close a guild session", failure);
                return null;
            });
        }
    }

    public CompletableFuture<Void> close()
    {
        long now = System.currentTimeMillis();
        return CompletableFuture.allOf(sessions.values().toArray(CompletableFuture[]::new))
                .thenCompose(unused -> module.getGuildRepository().closeOpenSessions(now));
    }

    public CompletableFuture<Long> session(UUID playerUuid)
    {
        CompletableFuture<Long> session = playerUuid == null ? null : sessions.get(playerUuid);
        return session == null ? CompletableFuture.completedFuture(null) : session;
    }
}
