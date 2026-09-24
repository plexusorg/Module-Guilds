package dev.plex.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.CorruptedWorldException;
import com.infernalsuite.asp.api.exceptions.NewerFormatException;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.world.SlimeFlatWorldProfile;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import dev.plex.Guilds;
import dev.plex.guild.Guild;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Owns loaded worlds, coalesced loads, reset admission, and world-file I/O. */
public final class AspGuildWorldService implements GuildWorldService
{
    private final Guilds module;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Map<UUID, SlimeWorldInstance> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<World>> loads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> resets = new ConcurrentHashMap<>();
    private final Set<String> pendingResets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cleanupQueued = new AtomicBoolean();
    private final Object storageLock = new Object();
    private GuildWorldFiles files;
    private ExecutorService io;
    private volatile boolean stopped;
    private SlimeFlatWorldProfile newWorldProfile;
    private Duration backupRetention;

    public AspGuildWorldService(Guilds module)
    {
        this.module = module;
    }

    @Override
    public void enable()
    {
        int worldSize = module.getConfig().getInt("guilds.worlds.size", 500000);
        int retentionDays = module.getConfig().getInt("guilds.worlds.backup-retention-days", 7);
        if (worldSize < 16 || worldSize > 59999968 || worldSize % 2 != 0 || retentionDays < 1)
        {
            throw new IllegalArgumentException("Guild world size must be even and between 16 and 59999968; backup retention must be positive");
        }
        backupRetention = Duration.ofDays(retentionDays);
        int half = worldSize / 2;
        newWorldProfile = new SlimeFlatWorldProfile(1, -64, 320, 0, -half, -half, half, half, "minecraft:plains", List.of(
                new SlimeFlatWorldProfile.Layer("minecraft:bedrock", 1),
                new SlimeFlatWorldProfile.Layer("minecraft:stone", 16),
                new SlimeFlatWorldProfile.Layer("minecraft:dirt", 32),
                new SlimeFlatWorldProfile.Layer("minecraft:grass_block", 1)));
        try
        {
            files = new GuildWorldFiles(module.getDataFolder().toPath().resolve("slime-worlds"));
            pendingResets.addAll(files.pendingResets());
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to open guild world storage", exception);
        }
        io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Plex-Guild-Worlds").factory());
        module.ownTask(Bukkit.getAsyncScheduler().runAtFixedRate(module.plugin(), task -> expireBackups(), 1, 3600, TimeUnit.SECONDS));
        if (!pendingResets.isEmpty())
        {
            module.getLogger().warn("Guild worlds have unfinished resets and remain closed: {}. Run the confirmed reset command again to finish them.", pendingResets);
        }
    }

    @Override
    public synchronized CompletableFuture<World> ensureWorld(Guild guild)
    {
        if (stopped || isResetting(guild.getWorldName()))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds are stopped or this world has a pending reset"));
        }
        UUID id = guild.getGuildUuid();
        SlimeWorldInstance loaded = loadedWorlds.get(id);
        if (loaded == null)
        {
            loaded = asp.getLoadedWorld(guild.getWorldName());
            if (loaded != null)
            {
                loadedWorlds.put(id, loaded);
            }
        }
        if (loaded != null)
        {
            return CompletableFuture.completedFuture(loaded.getBukkitWorld());
        }
        CompletableFuture<World> loading = loads.get(id);
        if (loading != null)
        {
            return loading;
        }
        CompletableFuture<World> result = onStorage(() -> readOrCreateWorld(guild))
                .thenCompose(world -> onGlobal(() -> loadWorld(guild, world)));
        loads.put(id, result);
        result.whenComplete((world, failure) -> loads.remove(id, result));
        return result;
    }

    @Override
    public boolean isResetting(String worldName)
    {
        return pendingResets.contains(worldName);
    }

    @Override
    public synchronized CompletableFuture<Void> resetWorld(Guild guild)
    {
        UUID id = guild.getGuildUuid();
        if (stopped || resets.containsKey(id))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds are stopped or a reset is already running"));
        }
        pendingResets.add(guild.getWorldName());
        CompletableFuture<World> loading = loads.getOrDefault(id, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> result = module.getGuildMutationService().resetWorld(guild,
                () -> loading.handle((world, failure) -> null).thenCompose(unused -> prepareReset(guild)),
                () -> onStorage(() ->
                {
                    asp.saveWorld(createWorld(guild));
                    files.completeReset(guild.getWorldName());
                    pendingResets.remove(guild.getWorldName());
                    return null;
                }));
        resets.put(id, result);
        result.whenComplete((unused, failure) ->
        {
            resets.remove(id, result);
            if (failure != null && !stopped)
            {
                onGlobal(() ->
                {
                    SlimeWorldInstance loaded = loadedWorlds.get(id);
                    if (loaded != null && !resets.containsKey(id))
                    {
                        loaded.getBukkitWorld().setAutoSave(true);
                    }
                    return null;
                }).exceptionally(restoreFailure ->
                {
                    module.getLogger().error("Failed to restore guild world autosave", restoreFailure);
                    return null;
                });
            }
        });
        return result;
    }

    private CompletableFuture<Void> prepareReset(Guild guild)
    {
        return onGlobal(() ->
        {
            SlimeWorldInstance loaded = asp.getLoadedWorld(guild.getWorldName());
            if (loaded != null)
            {
                loadedWorlds.put(guild.getGuildUuid(), loaded);
            }
            return loaded;
        }).thenCompose(loaded ->
        {
            CompletableFuture<Void> unload = loaded == null ? CompletableFuture.completedFuture(null)
                    : evacuate(loaded.getBukkitWorld()).thenCompose(unused -> onIo(() ->
                    {
                        asp.saveWorld(loaded);
                        return null;
                    })).thenCompose(unused -> onGlobal(() ->
                    {
                        if (!Bukkit.unloadWorld(loaded.getBukkitWorld(), false))
                        {
                            throw new IllegalStateException("Could not unload guild world " + guild.getWorldName());
                        }
                        loadedWorlds.remove(guild.getGuildUuid(), loaded);
                        return null;
                    }));
            return unload.thenCompose(unused -> onStorage(() ->
            {
                files.prepareReset(guild.getWorldName());
                return null;
            }));
        });
    }

    private CompletableFuture<Void> evacuate(World world)
    {
        return onGlobal(() ->
        {
            world.setAutoSave(false);
            World fallback = Bukkit.getWorlds().getFirst();
            if (fallback == world)
            {
                throw new IllegalStateException("Cannot reset the server's fallback world");
            }
            Location spawn = fallback.getSpawnLocation();
            List<CompletableFuture<Void>> teleports = List.copyOf(world.getPlayers()).stream()
                    .map(player -> evacuatePlayer(player, spawn)).toList();
            return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
        }).thenCompose(future -> future);
    }

    private CompletableFuture<Void> evacuatePlayer(Player player, Location spawn)
    {
        CompletableFuture<Void> result = new CompletableFuture<>();
        var task = player.getScheduler().run(module.plugin(), scheduled ->
                player.teleportAsync(spawn).whenComplete((success, failure) ->
                {
                    if (failure != null)
                    {
                        result.completeExceptionally(failure);
                    }
                    else if (!success)
                    {
                        result.completeExceptionally(new IllegalStateException("Could not evacuate " + player.getUniqueId()));
                    }
                    else
                    {
                        result.complete(null);
                    }
                }), () -> result.complete(null));
        module.ownTask(task);
        if (task == null)
        {
            result.complete(null);
        }
        return result.orTimeout(30, TimeUnit.SECONDS);
    }

    @Override
    public void ejectNonMembers(Guild guild)
    {
        onGlobal(() ->
        {
            SlimeWorldInstance world = loadedWorlds.get(guild.getGuildUuid());
            if (world != null)
            {
                Location spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
                for (Player player : List.copyOf(world.getBukkitWorld().getPlayers()))
                {
                    if (!guild.isMember(player.getUniqueId()))
                    {
                        evacuatePlayer(player, spawn).whenComplete((unused, failure) ->
                        {
                            if (failure != null)
                            {
                                module.getLogger().error("Failed to remove player from guild world", failure);
                            }
                        });
                        player.sendMessage(module.messageComponent("guildWorldNoAccess"));
                    }
                }
            }
            return null;
        }).exceptionally(failure ->
        {
            module.getLogger().error("Failed to remove nonmembers from guild world", failure);
            return null;
        });
    }

    @Override
    public synchronized void disable()
    {
        // Only file-only work holds this lock. Never wait here for an API save that needs the server thread.
        synchronized (storageLock)
        {
            stopped = true;
        }
        IllegalStateException failure = new IllegalStateException("Guild worlds stopped");
        loads.values().forEach(future -> future.completeExceptionally(failure));
        resets.values().forEach(future -> future.completeExceptionally(failure));
        for (SlimeWorldInstance world : loadedWorlds.values())
        {
            try
            {
                asp.saveWorld(world);
                if (!Bukkit.unloadWorld(world.getBukkitWorld(), false))
                {
                    module.getLogger().warn("Guild world {} remains loaded; ASP will continue to own its saves", world.getName());
                    world.getBukkitWorld().setAutoSave(true);
                }
            }
            catch (IOException | RuntimeException exception)
            {
                module.getLogger().error("Failed to save guild world {} during shutdown", world.getName(), exception);
                if (Bukkit.getWorld(world.getName()) == world.getBukkitWorld())
                {
                    world.getBukkitWorld().setAutoSave(true);
                }
            }
        }
        loadedWorlds.clear();
        if (io != null)
        {
            io.shutdown();
        }
    }

    private SlimeWorld readOrCreateWorld(Guild guild) throws IOException, CorruptedWorldException, NewerFormatException
    {
        try
        {
            return asp.readWorld(files, guild.getWorldName(), false, new SlimePropertyMap());
        }
        catch (UnknownWorldException ignored)
        {
            SlimeWorld world = createWorld(guild);
            asp.saveWorld(world);
            return world;
        }
    }

    private SlimeWorld createWorld(Guild guild)
    {
        SlimePropertyMap properties = new SlimePropertyMap();
        properties.setValue(SlimeProperties.DIFFICULTY, "peaceful");
        properties.setValue(SlimeProperties.SPAWN_X, 0);
        properties.setValue(SlimeProperties.SPAWN_Y, 50);
        properties.setValue(SlimeProperties.SPAWN_Z, 0);
        properties.setValue(SlimeProperties.ALLOW_ANIMALS, false);
        properties.setValue(SlimeProperties.ALLOW_MONSTERS, false);
        properties.setValue(SlimeProperties.DRAGON_BATTLE, false);
        properties.setValue(SlimeProperties.PVP, false);
        properties.setValue(SlimeProperties.ENVIRONMENT, "normal");
        properties.setValue(SlimeProperties.WORLD_TYPE, "flat");
        properties.setValue(SlimeProperties.DEFAULT_BIOME, "minecraft:plains");
        properties.setValue(SlimeProperties.SAVE_BLOCK_TICKS, true);
        properties.setValue(SlimeProperties.SAVE_FLUID_TICKS, true);
        properties.setValue(SlimeProperties.SAVE_POI, true);
        SlimeWorld world = asp.createEmptyWorld(guild.getWorldName(), false, properties, files);
        newWorldProfile.install(world);
        return world;
    }

    private World loadWorld(Guild guild, SlimeWorld data)
    {
        SlimeWorldInstance loaded = asp.loadWorld(data, true);
        loadedWorlds.put(guild.getGuildUuid(), loaded);
        World world = loaded.getBukkitWorld();
        SlimeFlatWorldProfile profile = SlimeFlatWorldProfile.fromWorld(data);
        if (profile != null)
        {
            world.getWorldBorder().setCenter((profile.minX() + profile.maxX()) / 2.0, (profile.minZ() + profile.maxZ()) / 2.0);
            world.getWorldBorder().setSize(profile.maxX() - profile.minX());
        }
        return world;
    }

    private void expireBackups()
    {
        if (stopped || !cleanupQueued.compareAndSet(false, true))
        {
            return;
        }
        onStorage(() ->
        {
            files.expireBackups(backupRetention);
            return null;
        }).whenComplete((unused, failure) ->
        {
            cleanupQueued.set(false);
            if (failure != null && !stopped)
            {
                module.getLogger().error("Failed to expire guild world backups", failure);
            }
        });
    }

    private <T> CompletableFuture<T> onIo(Callable<T> operation)
    {
        if (stopped)
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds stopped"));
        }
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                if (stopped)
                {
                    throw new IllegalStateException("Guild worlds stopped");
                }
                return operation.call();
            }
            catch (Exception exception)
            {
                throw new CompletionException(exception);
            }
        }, io);
    }

    private <T> CompletableFuture<T> onStorage(Callable<T> operation)
    {
        return onIo(() ->
        {
            synchronized (storageLock)
            {
                if (stopped)
                {
                    throw new IllegalStateException("Guild worlds stopped");
                }
                return operation.call();
            }
        });
    }

    private <T> CompletableFuture<T> onGlobal(Supplier<T> operation)
    {
        CompletableFuture<T> result = new CompletableFuture<>();
        module.ownTask(Bukkit.getGlobalRegionScheduler().run(module.plugin(), task ->
        {
            try
            {
                if (stopped)
                {
                    throw new IllegalStateException("Guild worlds stopped");
                }
                result.complete(operation.get());
            }
            catch (RuntimeException | LinkageError exception)
            {
                result.completeExceptionally(exception);
            }
        }));
        return result;
    }
}
