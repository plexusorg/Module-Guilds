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

public class GuildWorldService
{
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Map<UUID, SlimeWorldInstance> loadedWorlds = new ConcurrentHashMap<>();
    private SlimeLoader loader;

    public void enable()
    {
        File worldDirectory = new File(Guilds.get().getDataFolder(), "slime-worlds");
        loader = new FileLoader(worldDirectory);
    }

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

        return CompletableFuture.supplyAsync(() -> readOrCreateWorld(guild), Guilds.get().api().scheduler().asyncExecutor())
                .thenCompose(slimeWorld -> loadWorld(guild, slimeWorld))
                .thenApply(world ->
                {
                    initializeWorld(world);
                    saveWorld(guild);
                    return world;
                });
    }

    public void saveWorld(Guild guild)
    {
        SlimeWorldInstance loadedWorld = loadedWorlds.get(guild.getGuildUuid());
        if (loadedWorld == null)
        {
            return;
        }
        CompletableFuture.runAsync(() -> saveLoadedWorld(loadedWorld), Guilds.get().api().scheduler().asyncExecutor());
    }

    public void ejectNonMembers(Guild guild)
    {
        SlimeWorldInstance loadedWorld = loadedWorlds.get(guild.getGuildUuid());
        if (loadedWorld == null)
        {
            return;
        }
        Guilds.get().api().scheduler().executeGlobal(() ->
        {
            World fallback = Bukkit.getWorlds().getFirst();
            loadedWorld.getBukkitWorld().getPlayers().stream()
                    .filter(player -> !guild.isMember(player.getUniqueId()))
                    .forEach(player ->
                    {
                        player.teleportAsync(fallback.getSpawnLocation());
                        player.sendMessage(Guilds.get().messageComponent("guildWorldNoAccess"));
                    });
        });
    }

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
        Guilds.get().api().scheduler().executeGlobal(() ->
        {
            try
            {
                SlimeWorldInstance loadedWorld = asp.loadWorld(slimeWorld, true);
                loadedWorlds.put(guild.getGuildUuid(), loadedWorld);
                future.complete(loadedWorld.getBukkitWorld());
            }
            catch (Throwable throwable)
            {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void initializeWorld(World world)
    {
        int y = world.getMaxHeight() / 2;
        Location spawn = new Location(world, 0.5, y + 1, 0.5);
        world.getBlockAt(0, y, 0).setType(Material.GRASS_BLOCK, false);
        world.setSpawnLocation(spawn);
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
