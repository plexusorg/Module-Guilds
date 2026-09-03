package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

public class GuildWorldProtectionListener implements Listener
{
    private final Guilds module;

    public GuildWorldProtectionListener(Guilds module)
    {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event)
    {
        if (event.getTo() == null)
        {
            return;
        }
        Optional<Guild> guild = guildByWorld(event.getTo().getWorld());
        if (guild.isPresent() && !guild.get().isMember(event.getPlayer().getUniqueId()))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldNoAccess"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event)
    {
        Optional<Guild> guild = guildByWorld(event.getPlayer().getWorld());
        if (guild.isPresent() && !guild.get().isMember(event.getPlayer().getUniqueId()))
        {
            World fallback = Bukkit.getWorlds().getFirst();
            event.getPlayer().teleportAsync(fallback.getSpawnLocation());
            event.getPlayer().sendMessage(module.messageComponent("guildWorldNoAccess"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        if (!canUse(event.getPlayer(), event.getBlock().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldPermissionDenied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        if (!canUse(event.getPlayer(), event.getBlock().getWorld(), GuildPermission.BLOCK_PLACING))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldPermissionDenied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event)
    {
        if (event.getClickedBlock() == null)
        {
            return;
        }
        if (!canUse(event.getPlayer(), event.getClickedBlock().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldPermissionDenied"));
        }
    }

    private boolean canUse(Player player, World world, GuildPermission permission)
    {
        Optional<Guild> guild = guildByWorld(world);
        return guild.map(value -> value.hasPermission(player.getUniqueId(), permission)).orElse(true);
    }

    private Optional<Guild> guildByWorld(World world)
    {
        if (world == null)
        {
            return Optional.empty();
        }
        return module.getGuildHolder().guilds().stream()
                .filter(guild -> guild.getWorldName().equals(world.getName()))
                .findFirst();
    }
}
