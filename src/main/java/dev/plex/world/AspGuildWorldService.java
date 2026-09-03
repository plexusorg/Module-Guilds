package dev.plex.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.CorruptedWorldException;
import com.infernalsuite.asp.api.exceptions.NewerFormatException;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
import dev.plex.Guilds;
import dev.plex.guild.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AspGuildWorldService implements GuildWorldService
{
    private final Guilds module;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Map<UUID, SlimeWorldInstance> loadedWorlds = new ConcurrentHashMap<>();
    private SlimeLoader loader;

    public AspGuildWorldService(Guilds module)
    {
        this.module = module;
    }

    @Override
    public void enable()
    {
        File worldDirectory = new File(module.getDataFolder(), "slime-worlds");
        loader = new FileLoader(worldDirectory);
    }

    @Override
    public CompletableFuture<World> ensureWorld(Guild guild)
    {
        SlimeWorldInstance loadedWorld = loadedWorlds.get(guild.getGuildUuid());
        if (loadedWorld != null)
        {
            return CompletableFuture.completedFuture(loadedWorld.getBukkitWorld());
        }

        SlimeWorldInstance aspLoadedWorld = asp.getLoadedWorld(guild.getWorldName());
        if (aspLoadedWorld != null)
        {
            loadedWorlds.put(guild.getGuildUuid(), aspLoadedWorld);
            return CompletableFuture.completedFuture(aspLoadedWorld.getBukkitWorld());
        }

        return CompletableFuture.supplyAsync(() -> readOrCreateWorld(guild), module.scheduler().asyncExecutor())
                .thenCompose(slimeWorld -> loadWorld(guild, slimeWorld))
                .thenCompose(world -> initializeWorld(world).thenApply(unused ->
                {
                    saveWorld(guild);
                    return world;
                }));
    }

    @Override
    public void saveWorld(Guild guild)
    {
        SlimeWorldInstance loadedWorld = loadedWorlds.get(guild.getGuildUuid());
        if (loadedWorld == null)
        {
            return;
        }
        CompletableFuture.runAsync(() -> saveLoadedWorld(loadedWorld), module.scheduler().asyncExecutor());
    }

    @Override
    public void ejectNonMembers(Guild guild)
    {
        SlimeWorldInstance loadedWorld = loadedWorlds.get(guild.getGuildUuid());
        if (loadedWorld == null)
        {
            return;
        }
        module.scheduler().executeGlobal(() ->
        {
            World fallback = Bukkit.getWorlds().getFirst();
            Location fallbackSpawn = fallback.getSpawnLocation();
            loadedWorld.getBukkitWorld().getPlayers().forEach(player -> module.scheduler().runEntity(player, () ->
                    {
                        if (!guild.isMember(player.getUniqueId()))
                        {
                            player.teleportAsync(fallbackSpawn);
                            player.sendMessage(module.messageComponent("guildWorldNoAccess"));
                        }
                    }));
        });
    }

    @Override
    public void disable()
    {
        loadedWorlds.values().forEach(loadedWorld ->
        {
            saveLoadedWorld(loadedWorld);
            Bukkit.unloadWorld(loadedWorld.getBukkitWorld(), false);
        });
        loadedWorlds.clear();
    }

    private SlimeWorld readOrCreateWorld(Guild guild)
    {
        SlimePropertyMap propertyMap = createPropertyMap();
        try
        {
            return asp.readWorld(loader, guild.getWorldName(), false, propertyMap);
        }
        catch (UnknownWorldException ignored)
        {
            return asp.createEmptyWorld(guild.getWorldName(), false, propertyMap, loader);
        }
        catch (IOException | CorruptedWorldException | NewerFormatException e)
        {
            throw new GuildWorldException("Failed to read guild world " + guild.getWorldName(), e);
        }
    }

    private CompletableFuture<World> loadWorld(Guild guild, SlimeWorld slimeWorld)
    {
        CompletableFuture<World> future = new CompletableFuture<>();
        module.scheduler().executeGlobal(() ->
        {
            try
            {
                SlimeWorldInstance loadedWorld = asp.loadWorld(slimeWorld, true);
                loadedWorlds.put(guild.getGuildUuid(), loadedWorld);
                future.complete(loadedWorld.getBukkitWorld());
            }
            catch (RuntimeException | LinkageError exception)
            {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private CompletableFuture<Void> initializeWorld(World world)
    {
        CompletableFuture<Void> initialized = new CompletableFuture<>();
        int y = world.getMaxHeight() / 2;
        Location spawn = new Location(world, 0.5, y + 1, 0.5);
        module.scheduler().executeRegion(spawn, () ->
        {
            try
            {
                world.getBlockAt(0, y, 0).setType(Material.GRASS_BLOCK, false);
                module.scheduler().executeGlobal(() ->
                {
                    try
                    {
                        world.setSpawnLocation(spawn);
                        initialized.complete(null);
                    }
                    catch (RuntimeException | LinkageError exception)
                    {
                        initialized.completeExceptionally(exception);
                    }
                });
            }
            catch (RuntimeException | LinkageError exception)
            {
                initialized.completeExceptionally(exception);
            }
        });
        return initialized;
    }

    private void saveLoadedWorld(SlimeWorldInstance loadedWorld)
    {
        try
        {
            asp.saveWorld(loadedWorld.getSerializableCopy());
        }
        catch (IOException e)
        {
            throw new GuildWorldException("Failed to save guild world " + loadedWorld.getName(), e);
        }
    }

    private SlimePropertyMap createPropertyMap()
    {
        SlimePropertyMap propertyMap = new SlimePropertyMap();
        propertyMap.setValue(SlimeProperties.DIFFICULTY, "peaceful");
        propertyMap.setValue(SlimeProperties.SPAWN_X, 0);
        propertyMap.setValue(SlimeProperties.SPAWN_Y, 65);
        propertyMap.setValue(SlimeProperties.SPAWN_Z, 0);
        propertyMap.setValue(SlimeProperties.ALLOW_ANIMALS, false);
        propertyMap.setValue(SlimeProperties.ALLOW_MONSTERS, false);
        propertyMap.setValue(SlimeProperties.DRAGON_BATTLE, false);
        propertyMap.setValue(SlimeProperties.PVP, false);
        propertyMap.setValue(SlimeProperties.ENVIRONMENT, "normal");
        propertyMap.setValue(SlimeProperties.WORLD_TYPE, "FLAT");
        propertyMap.setValue(SlimeProperties.DEFAULT_BIOME, "minecraft:plains");
        return propertyMap;
    }

    public static class GuildWorldException extends RuntimeException
    {
        public GuildWorldException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
