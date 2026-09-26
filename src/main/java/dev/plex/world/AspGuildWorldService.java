package dev.plex.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.CorruptedWorldException;
import com.infernalsuite.asp.api.exceptions.NewerFormatException;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.world.SlimeFlatWorldProfile;
import com.infernalsuite.asp.api.world.SlimeVanillaWorldProfile;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.util.DurationParser;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Owns loaded worlds, coalesced loads, reset admission, and world-file I/O. */
public final class AspGuildWorldService implements GuildWorldService
{
    private final Guilds module;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Map<UUID, SlimeWorldInstance> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<World>> loads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> resets = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> unloads = new HashMap<>();
    private final Map<UUID, Long> emptySince = new HashMap<>();
    private final Set<String> pendingResets = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedWorlds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cleanupQueued = new AtomicBoolean();
    private final Object storageLock = new Object();
    private GuildWorldFiles files;
    private ExecutorService io;
    private volatile boolean stopped;
    private SlimeFlatWorldProfile newWorldProfile;
    private Duration backupRetention;
    private Duration unloadAfter;
    private int worldSize;

    public AspGuildWorldService(Guilds module)
    {
        this.module = module;
    }

    @Override
    public void enable()
    {
        worldSize = module.getConfig().getInt("guilds.worlds.size", 500000);
        if (worldSize < 16 || worldSize > 59999968 || worldSize % 2 != 0)
        {
            throw new IllegalArgumentException("Guild world size must be even and between 16 and 59999968");
        }
        int retentionDays = module.getConfig().getInt("guilds.worlds.backup-retention-days", 7);
        if (retentionDays < 1)
        {
            throw new IllegalArgumentException("Guild world backup retention must be positive");
        }
        backupRetention = Duration.ofDays(retentionDays);
        unloadAfter = DurationParser.parse(module.getConfig().getString("guilds.worlds.unload-after", "5m"));
        if (unloadAfter == null)
        {
            throw new IllegalArgumentException("Guild world unload-after must be a positive duration in m, h, or d");
        }
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
        module.ownTask(Bukkit.getAsyncScheduler().runAtFixedRate(module.plugin(), task ->
                onGlobal(() ->
                {
                    checkIdleWorlds();
                    return null;
                }).exceptionally(failure ->
                {
                    if (!stopped)
                    {
                        module.getLogger().error("Failed to check idle guild worlds", failure);
                    }
                    return null;
                }), 30, 30, TimeUnit.SECONDS));
        if (!pendingResets.isEmpty())
        {
            module.getLogger().warn("Guild worlds have unfinished resets and remain closed: {}. Run the confirmed reset command again to finish them.", pendingResets);
        }
    }

    @Override
    public synchronized CompletableFuture<World> ensureWorld(Guild guild)
    {
        if (stopped || resets.containsKey(guild.getGuildUuid()) || isResetting(guild.getWorldName()) || deletedWorlds.contains(guild.getWorldName()))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds are stopped or this world has a pending reset or deletion"));
        }
        UUID id = guild.getGuildUuid();
        emptySince.remove(id);
        CompletableFuture<Void> unloading = unloads.get(id);
        if (unloading != null)
        {
            return unloading.thenCompose(unused -> ensureWorld(guild));
        }
        CompletableFuture<World> loading = loads.get(id);
        if (loading != null)
        {
            return loading;
        }
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
            CompletableFuture<World> result = finishLoad(guild, loaded.getBukkitWorld());
            loads.put(id, result);
            result.whenComplete((world, failure) -> loads.remove(id, result));
            return result;
        }
        CompletableFuture<World> result = onStorage(() -> readWorld(guild))
                .thenCompose(world -> onGlobal(() -> loadWorld(guild, world)))
                .thenCompose(world -> finishLoad(guild, world));
        loads.put(id, result);
        result.whenComplete((world, failure) -> loads.remove(id, result));
        return result;
    }

    @Override
    public CompletableFuture<World> generateWorld(Guild guild, GuildWorldType type, UUID actor)
    {
        return module.getGuildMutationService().generateWorld(guild, actor, () -> generateWorld(guild, type));
    }

    private synchronized CompletableFuture<World> generateWorld(Guild guild, GuildWorldType type)
    {
        UUID id = guild.getGuildUuid();
        if (stopped || resets.containsKey(id) || isResetting(guild.getWorldName()) || deletedWorlds.contains(guild.getWorldName())
                || loads.containsKey(id) || unloads.containsKey(id))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("The world exists or is busy"));
        }
        if (asp.getLoadedWorld(guild.getWorldName()) != null)
        {
            return CompletableFuture.failedFuture(new IllegalArgumentException("The guild already has a world"));
        }
        CompletableFuture<World> result = onStorage(() ->
                {
                    if (files.worldExists(guild.getWorldName()))
                    {
                        throw new IllegalArgumentException("Reset the existing world before generating another");
                    }
                    SlimeWorld world = createWorld(guild, type);
                    asp.saveWorld(world);
                    return world;
                }).thenCompose(world -> onGlobal(() -> loadWorld(guild, world)))
                        .thenCompose(world -> finishLoad(guild, world));
        loads.put(id, result);
        result.whenComplete((world, failure) -> loads.remove(id, result));
        return result;
    }

    private CompletableFuture<World> finishLoad(Guild guild, World world)
    {
        return onGlobal(() ->
        {
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(worldSize);
            return world;
        }).thenCompose(unused -> prepareSpawnIfNeeded(guild, world));
    }

    private CompletableFuture<World> prepareSpawnIfNeeded(Guild guild, World world)
    {
        SlimeWorldInstance instance = loadedWorlds.get(guild.getGuildUuid());
        if (!instance.getExtraData().containsKey("guild:spawn_pending"))
        {
            return CompletableFuture.completedFuture(world);
        }
        SlimeVanillaWorldProfile profile = SlimeVanillaWorldProfile.fromWorld(instance);
        GuildWorldType type = profile == null ? GuildWorldType.SUPERFLAT : switch (profile.environment())
        {
            case "nether" -> GuildWorldType.NETHER;
            case "the_end" -> GuildWorldType.END;
            default -> GuildWorldType.OVERWORLD;
        };
        return prepareSpawn(guild, world, type);
    }

    private CompletableFuture<World> prepareSpawn(Guild guild, World world, GuildWorldType type)
    {
        // Generate before placing a small safe landing area. Never place players inside terrain or over the void.
        return world.getChunkAtAsync(0, 0).thenCompose(chunk -> onGlobal(() ->
        {
            int y = type == GuildWorldType.NETHER ? 64 : world.getHighestBlockYAt(0, 0) + 1;
            y = Math.max(world.getMinHeight() + 1, Math.min(y, world.getMaxHeight() - 4));
            for (int x = 0; type != GuildWorldType.SUPERFLAT && x <= 4; x++)
            {
                for (int z = 0; z <= 4; z++)
                {
                    world.getBlockAt(x, y - 1, z).setType(org.bukkit.Material.OBSIDIAN, false);
                    for (int height = 0; height <= 3; height++)
                    {
                        boolean wall = type == GuildWorldType.NETHER && (x == 0 || x == 4 || z == 0 || z == 4 || height == 3);
                        world.getBlockAt(x, y + height, z).setType(wall ? org.bukkit.Material.OBSIDIAN : org.bukkit.Material.AIR, false);
                    }
                }
            }
            world.setSpawnLocation(2, y, 2);
            SlimeWorldInstance instance = loadedWorlds.get(guild.getGuildUuid());
            instance.getPropertyMap().setValue(SlimeProperties.SPAWN_X, 2);
            instance.getPropertyMap().setValue(SlimeProperties.SPAWN_Y, y);
            instance.getPropertyMap().setValue(SlimeProperties.SPAWN_Z, 2);
            instance.getExtraData().remove("guild:spawn_pending");
            return instance;
        })).thenCompose(instance -> onIo(() ->
        {
            asp.saveWorld(instance);
            return world;
        }));
    }

    @Override
    public boolean isResetting(String worldName)
    {
        return pendingResets.contains(worldName);
    }

    @Override
    public synchronized CompletableFuture<Void> resetWorld(Guild guild, UUID actor)
    {
        UUID id = guild.getGuildUuid();
        if (stopped || resets.containsKey(id) || deletedWorlds.contains(guild.getWorldName()))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds are stopped, a reset is already running, or the world is deleted"));
        }
        CompletableFuture<World> loading = loads.getOrDefault(id, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> unloading = unloads.getOrDefault(id, CompletableFuture.completedFuture(null));
        emptySince.remove(id);
        CompletableFuture<Void> result = module.getGuildMutationService().resetWorld(guild, actor,
                () ->
                {
                    pendingResets.add(guild.getWorldName());
                    return unloading.thenCompose(unused -> loading.handle((world, failure) -> null))
                            .thenCompose(unused -> prepareReset(guild));
                },
                () -> onStorage(() ->
                {
                    if (files.worldExists(guild.getWorldName()))
                    {
                        files.deleteWorld(guild.getWorldName());
                    }
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
                        if (!Bukkit.unloadWorld(loaded.getBukkitWorld(), true))
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

    @Override
    public synchronized CompletableFuture<Void> deleteWorld(Guild guild)
    {
        UUID id = guild.getGuildUuid();
        String worldName = guild.getWorldName();
        // Close the name first: ensureWorld and resetWorld refuse it from here on, so nothing can load it again.
        if (!deletedWorlds.add(worldName))
        {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<World> loading = loads.getOrDefault(id, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> unloading = unloads.getOrDefault(id, CompletableFuture.completedFuture(null));
        emptySince.remove(id);
        return unloading.thenCompose(unused -> loading.handle((world, failure) -> null)).thenCompose(unused -> onGlobal(() ->
        {
            loadedWorlds.remove(id);
            return asp.getLoadedWorld(worldName);
        })).thenCompose(loaded -> loaded == null ? CompletableFuture.completedFuture(null)
                : evacuate(loaded.getBukkitWorld()).thenCompose(unused -> onGlobal(() ->
                {
                    if (!Bukkit.unloadWorld(loaded.getBukkitWorld(), false))
                    {
                        throw new IllegalStateException("Could not unload guild world " + worldName);
                    }
                    return null;
                }))).thenCompose(unused -> onStorage(() ->
                {
                    try
                    {
                        files.deleteWorld(worldName);
                    }
                    catch (UnknownWorldException ignored)
                    {
                        // The world was never created.
                    }
                    files.cancelReset(worldName);
                    pendingResets.remove(worldName);
                    return null;
                }));
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
        emptySince.clear();
        for (Map.Entry<UUID, SlimeWorldInstance> entry : loadedWorlds.entrySet())
        {
            SlimeWorldInstance world = entry.getValue();
            if (unloads.containsKey(entry.getKey()))
            {
                // ASP saves can wait for this thread. Leave ownership with ASP instead of waiting or saving twice.
                world.getBukkitWorld().setAutoSave(true);
                module.getLogger().warn("Guild world {} has an idle unload in progress; ASP will continue to own its saves", world.getName());
                continue;
            }
            try
            {
                asp.saveWorld(world);
                if (!Bukkit.unloadWorld(world.getBukkitWorld(), true))
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
        List.copyOf(unloads.values()).forEach(future -> future.completeExceptionally(failure));
        unloads.clear();
        if (io != null)
        {
            io.shutdown();
        }
    }

    private SlimeWorld readWorld(Guild guild) throws IOException, CorruptedWorldException, NewerFormatException
    {
        try
        {
            return asp.readWorld(files, guild.getWorldName(), false, new SlimePropertyMap());
        }
        catch (UnknownWorldException ignored)
        {
            throw new GuildWorldNotGeneratedException();
        }
    }

    private SlimeWorld createWorld(Guild guild, GuildWorldType type)
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
        world.getExtraData().put("guild:spawn_pending", net.kyori.adventure.nbt.ByteBinaryTag.byteBinaryTag((byte) 1));
        if (type == GuildWorldType.SUPERFLAT)
        {
            newWorldProfile.install(world);
        }
        else
        {
            new SlimeVanillaWorldProfile(1, java.util.concurrent.ThreadLocalRandom.current().nextLong(), type.environment()).install(world);
            properties.setValue(SlimeProperties.SEA_LEVEL, type == GuildWorldType.NETHER ? 32 : 63);
        }
        return world;
    }

    private World loadWorld(Guild guild, SlimeWorld data)
    {
        SlimeWorldInstance loaded = asp.loadWorld(data, true);
        loadedWorlds.put(guild.getGuildUuid(), loaded);
        return loaded.getBukkitWorld();
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

    private synchronized void checkIdleWorlds()
    {
        if (stopped)
        {
            return;
        }
        long now = System.nanoTime();
        for (Map.Entry<UUID, SlimeWorldInstance> entry : loadedWorlds.entrySet())
        {
            UUID id = entry.getKey();
            SlimeWorldInstance loaded = entry.getValue();
            if (unloads.containsKey(id))
            {
                continue;
            }
            if (loads.containsKey(id) || resets.containsKey(id) || pendingResets.contains(loaded.getName())
                    || deletedWorlds.contains(loaded.getName()) || !loaded.getBukkitWorld().getPlayers().isEmpty())
            {
                emptySince.remove(id);
                continue;
            }
            Long since = emptySince.putIfAbsent(id, now);
            if (since != null && Duration.ofNanos(now - since).compareTo(unloadAfter) >= 0)
            {
                unloadIdleWorld(id, loaded);
            }
        }
    }

    private void unloadIdleWorld(UUID id, SlimeWorldInstance loaded)
    {
        CompletableFuture<Void> result = new CompletableFuture<>();
        unloads.put(id, result);
        onIo(() ->
        {
            asp.saveWorld(loaded);
            return null;
        }).handle((unused, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to save idle guild world {}", loaded.getName(), failure);
            }
            return failure == null;
        }).thenCompose(saved -> onGlobal(() ->
        {
            finishIdleUnload(id, loaded, saved);
            return null;
        })).whenComplete((unused, failure) ->
        {
            synchronized (this)
            {
                if (failure != null && !stopped)
                {
                    module.getLogger().error("Failed to unload idle guild world {}", loaded.getName(), failure);
                }
                unloads.remove(id, result);
                emptySince.remove(id);
                result.complete(null);
            }
        });
    }

    // Runs without the service lock so region threads never wait on this save. The unloads entry keeps
    // ensureWorld, resetWorld, deleteWorld, and generateWorld waiting until the unload completes.
    private void finishIdleUnload(UUID id, SlimeWorldInstance loaded, boolean saved)
    {
        if (stopped)
        {
            return;
        }
        World world = loaded.getBukkitWorld();
        // A player can arrive without ensureWorld while the save runs. Never evacuate an idle world.
        if (!saved || resets.containsKey(id) || pendingResets.contains(loaded.getName())
                || deletedWorlds.contains(loaded.getName()) || !world.getPlayers().isEmpty())
        {
            world.setAutoSave(true);
            return;
        }
        try
        {
            if (!Bukkit.unloadWorld(world, true))
            {
                throw new IllegalStateException("Could not unload idle guild world " + loaded.getName());
            }
            loadedWorlds.remove(id, loaded);
        }
        catch (RuntimeException exception)
        {
            world.setAutoSave(true);
            module.getLogger().error("Failed to unload idle guild world {}", loaded.getName(), exception);
        }
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

    @Override
    public CompletableFuture<Location> safeLocation(Location location)
    {
        return location.getWorld().getChunkAtAsync(location).thenCompose(chunk -> onRegion(location, () ->
        {
            World world = location.getWorld();
            int x = location.getBlockX();
            int z = location.getBlockZ();
            int min = world.getMinHeight();
            int max = world.getMaxHeight() - 2;
            int y = Math.max(min + 1, Math.min(location.getBlockY(), max));
            while (y < max && !(open(world.getBlockAt(x, y, z)) && open(world.getBlockAt(x, y + 1, z))))
            {
                y++;
            }
            while (y > min && open(world.getBlockAt(x, y - 1, z)))
            {
                y--;
            }
            if (y == location.getBlockY())
            {
                return location;
            }
            Location safe = location.clone();
            safe.setY(y);
            return safe;
        }));
    }

    /** A player can stand in a passable block, but never in lava. */
    private static boolean open(Block block)
    {
        return block.isPassable() && block.getType() != Material.LAVA;
    }

    private <T> CompletableFuture<T> onRegion(Location location, Supplier<T> operation)
    {
        CompletableFuture<T> result = new CompletableFuture<>();
        module.ownTask(Bukkit.getRegionScheduler().run(module.plugin(), location, task ->
        {
            try
            {
                if (stopped)
                {
                    throw new IllegalStateException("Guild worlds stopped");
                }
                result.complete(operation.get());
            }
            catch (RuntimeException exception)
            {
                result.completeExceptionally(exception);
            }
        }));
        return result;
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
