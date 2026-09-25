package dev.plex.handler;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import java.time.Instant;
import dev.plex.guild.data.GuildPermission;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class GuildWorldAccessListener implements Listener
{
    private final Guilds module;

    public GuildWorldAccessListener(Guilds module)
    {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(AsyncPlayerSpawnLocationEvent event)
    {
        if (!module.getGuildWorldProtectionListener().canEnter(event.getConnection().getProfile().getId(), event.getSpawnLocation().getWorld()))
        {
            Location fallback = spawnFallback();
            if (fallback == null)
            {
                event.getConnection().disconnect(module.messageComponent("guildWorldNoAccess"));
            }
            else
            {
                event.setSpawnLocation(fallback);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event)
    {
        if (event.getTo() != null && !module.getGuildWorldProtectionListener().canEnter(event.getPlayer().getUniqueId(), event.getTo().getWorld()))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldNoAccess"));
        }
    }

    // Portal events have a separate handler list from teleport events.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event)
    {
        onTeleport(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event)
    {
        if (!module.getGuildWorldProtectionListener().canEnter(event.getPlayer().getUniqueId(), event.getRespawnLocation().getWorld()))
        {
            Location fallback = fallback();
            if (fallback == null)
            {
                event.getPlayer().kick(module.messageComponent("guildWorldNoAccess"));
            }
            else
            {
                event.setRespawnLocation(fallback);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event)
    {
        rejectResident(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event)
    {
        rejectResident(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event)
    {
        rejectResident(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event)
    {
        if (!module.getGuildWorldProtectionListener().canEnter(event.getPlayer().getUniqueId(), event.getPlayer().getWorld()))
        {
            event.setCancelled(true);
            event.getPlayer().kick(module.messageComponent("guildWorldNoAccess"));
        }
    }

    public void startGuestExpiry()
    {
        module.ownTask(Bukkit.getAsyncScheduler().runAtFixedRate(module.plugin(), task ->
        {
            Instant now = Instant.now();
            for (Guild guild : module.getGuildHolder().guilds())
            {
                for (Guest guest : guild.getGuests().values())
                {
                    // Authorization checks the timestamp on every action. This timer removes idle visitors too.
                    if (!guest.isActive(now) && guild.getGuests().remove(guest.playerUuid(), guest))
                    {
                        revoke(guest.playerUuid());
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS));
    }

    public void revoke(UUID playerId)
    {
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task -> revokeOnline(playerId)));
    }

    private void revokeOnline(UUID playerId)
    {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null)
        {
            return;
        }
        Location fallback = fallback();
        module.ownTask(player.getScheduler().run(module.plugin(), task ->
        {
            if (module.getGuildWorldProtectionListener().canEnter(playerId, player.getWorld()))
            {
                return;
            }
            if (fallback == null)
            {
                rejectResident(player);
                return;
            }
            player.teleportAsync(fallback).orTimeout(30, TimeUnit.SECONDS).whenComplete((success, failure) ->
            {
                if (failure != null)
                {
                    module.getLogger().error("Failed to remove player from guild world", failure);
                }
                if (failure != null || !Boolean.TRUE.equals(success))
                {
                    module.ownTask(player.getScheduler().run(module.plugin(), scheduled -> rejectResident(player), null));
                }
            });
            player.sendMessage(module.messageComponent("guildWorldNoAccess"));
        }, null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event)
    {
        if (event.getEntity() instanceof Player player
                && !module.getGuildWorldProtectionListener().canUse(player.getUniqueId(), event.getMount().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    private Location spawnFallback()
    {
        CompletableFuture<Location> result = new CompletableFuture<>();
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task ->
        {
            try
            {
                result.complete(fallback());
            }
            catch (RuntimeException failure)
            {
                result.completeExceptionally(failure);
            }
        }));
        try
        {
            // Configuration waits for this asynchronous event; no region thread waits here.
            return result.orTimeout(5, TimeUnit.SECONDS).join();
        }
        catch (CompletionException failure)
        {
            module.getLogger().error("Failed to select a public spawn location", failure);
            return null;
        }
    }

    private void rejectResident(Player player)
    {
        if (!module.getGuildWorldProtectionListener().canEnter(player.getUniqueId(), player.getWorld()))
        {
            player.kick(module.messageComponent("guildWorldNoAccess"));
        }
    }

    private Location fallback()
    {
        var world = Bukkit.getServer().getRespawnWorld();
        return module.getGuildWorldProtectionListener().isGuildWorld(world) ? null : world.getSpawnLocation();
    }
}
