package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryView;

import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerLecternPageChangeEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent;
import org.bukkit.Material;

import java.util.UUID;

public class GuildWorldProtectionListener implements Listener
{
    private final Guilds module;

    public GuildWorldProtectionListener(Guilds module)
    {
        this.module = module;
    }

    public boolean isGuildWorld(World world)
    {
        // Reserve the namespace even while storage is loading or a guild has been deleted.
        return world != null && world.getName().startsWith("guild_");
    }

    public boolean canEnter(UUID playerId, World world)
    {
        if (!isGuildWorld(world))
        {
            return true;
        }
        Guild guild = accessibleGuild(world);
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        return guild != null && (guild.canEnterWorld(playerId) || player != null && player.hasPermission("plex.guilds.world.bypass"));
    }

    public boolean canUse(UUID playerId, World world, GuildPermission permission)
    {
        if (!isGuildWorld(world))
        {
            return true;
        }
        Guild guild = accessibleGuild(world);
        return guild != null && guild.hasPermission(playerId, permission);
    }

    private Guild accessibleGuild(World world)
    {
        if (!module.isReady() || !module.isGuildWorldsEnabled()
                || module.getGuildWorldService().isResetting(world.getName()))
        {
            return null;
        }
        return module.getGuildHolder().guildByWorld(world.getName()).orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldPermissionDenied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
            event.getPlayer().sendMessage(module.messageComponent("guildWorldPermissionDenied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event)
    {
        World world = event.getClickedBlock() == null ? event.getPlayer().getWorld() : event.getClickedBlock().getWorld();
        if (!canUse(event.getPlayer().getUniqueId(), world, GuildPermission.INTERACT))
        {
            // Air interactions can start cancelled for blocks but still permit item use.
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getHarvestedBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event)
    {
        Player player = event.getPlayer();
        if (player != null && !canUse(player.getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event)
    {
        Player player = event.getPlayer();
        if (isGuildWorld(event.getBlock().getWorld())
                && (player == null || !canUse(player.getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD)))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event)
    {
        if (!canUseInventory(event.getPlayer(), event.getView()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event)
    {
        if (!canUseInventory(event.getWhoClicked(), event.getView()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event)
    {
        if (!canUseInventory(event.getWhoClicked(), event.getView()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBed().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getLectern().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternInsert(PlayerInsertLecternBookEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternPage(PlayerLecternPageChangeEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getLectern().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getSign().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconChange(PlayerChangeBeaconEffectEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBeacon().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlowerPot(PlayerFlowerPotManipulateEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getFlowerpot().getWorld(), GuildPermission.INTERACT))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShearBlock(PlayerShearBlockEvent event)
    {
        if (!canUse(event.getPlayer().getUniqueId(), event.getBlock().getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event)
    {
        Player player = event.getPlayer();
        if (player != null && !canUse(player.getUniqueId(), event.getWorld(), GuildPermission.BUILD))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event)
    {
        if (isGuildWorld(event.getBlock().getWorld()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event)
    {
        Material type = event.getNewState().getType();
        if (isGuildWorld(event.getBlock().getWorld()) && (type == Material.FIRE || type == Material.SOUL_FIRE))
        {
            event.setCancelled(true);
        }
    }

    private boolean canUseInventory(HumanEntity player, InventoryView view)
    {
        if (!canEnter(player.getUniqueId(), player.getWorld()))
        {
            return false;
        }
        if (view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.CREATIVE)
        {
            return true;
        }
        // Plugin menus have no world container to edit.
        Location location = view.getTopInventory().getLocation();
        return location == null || canUse(player.getUniqueId(), location.getWorld(), GuildPermission.INTERACT);
    }
}
