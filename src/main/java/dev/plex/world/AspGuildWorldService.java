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
    private static final int WORLD_SIZE = 500_000;
    private final Guilds module;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Map<UUID, SlimeWorldInstance> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<World>> loads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> resets = new ConcurrentHashMap<>();
    private final Set<String> pendingResets = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedWorlds = ConcurrentHashMap.newKeySet();
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
        int retentionDays = module.getConfig().getInt("guilds.worlds.backup-retention-days", 7);
        if (retentionDays < 1)
        {
            throw new IllegalArgumentException("Guild world backup retention must be positive");
        }
        backupRetention = Duration.ofDays(retentionDays);
        int half = WORLD_SIZE / 2;
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
        if (stopped || resets.containsKey(guild.getGuildUuid()) || isResetting(guild.getWorldName()) || deletedWorlds.contains(guild.getWorldName()))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("Guild worlds are stopped or this world has a pending reset or deletion"));
        }
        UUID id = guild.getGuildUuid();
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
                || loads.containsKey(id))
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
            world.getWorldBorder().setSize(WORLD_SIZE);
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
            int y = type == GuildWorldType.SUPERFLAT ? 50 : type == GuildWorldType.NETHER ? 64
                    : world.getHighestBlockYAt(0, 0) + 1;
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
        CompletableFuture<Void> result = module.getGuildMutationService().resetWorld(guild, actor,
                () ->
                {
                    pendingResets.add(guild.getWorldName());
                    return loading.handle((world, failure) -> null).thenCompose(unused -> prepareReset(guild));
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
        return loading.handle((world, failure) -> null).thenCompose(unused -> onGlobal(() ->
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
        for (SlimeWorldInstance world : loadedWorlds.values())
        {
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
