package dev.plex.handler;

import dev.plex.guild.data.GuildPermission;
import dev.plex.Guilds;
import io.papermc.paper.event.entity.EntityCompostItemEvent;
import io.papermc.paper.event.entity.EntityDyeEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import io.papermc.paper.event.player.PlayerToggleEntityAgeLockEvent;
import java.util.UUID;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.World;
import org.bukkit.Material;

public final class GuildWorldEntityProtectionListener implements Listener
{
    private final Guilds module;

    public GuildWorldEntityProtectionListener(Guilds module)
    {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getRightClicked().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getRightClicked().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event)
    {
        if (denied(event.getPlayer(), event.getRightClicked().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTame(EntityTameEvent event)
    {
        if (denied(event.getOwner().getUniqueId(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDye(EntityDyeEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityEnterLoveModeEvent event)
    {
        if (denied(event.getHumanEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onName(PlayerNameEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAgeLock(PlayerToggleEntityAgeLockEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrame(PlayerItemFrameChangeEvent event)
    {
        if (denied(event.getPlayer(), event.getItemFrame().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.BLOCK_PLACING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.BLOCK_PLACING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event)
    {
        if (denied(event.getRemover(), event.getEntity().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event)
    {
        if (denied(event.getAttacker(), event.getVehicle().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLingeringSplash(LingeringPotionSplashEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFirework(FireworkExplodeEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event)
    {
        UUID actor = actor(event.getDamageSource().getCausingEntity());
        if (actor == null)
        {
            actor = actor(event.getDamager());
        }
        if (denied(actor, event.getEntity().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event)
    {
        UUID actor = actor(event.getDamageSource().getCausingEntity());
        if (actor == null)
        {
            actor = actor(event.getAttacker());
        }
        if (denied(actor, event.getVehicle().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCloudApply(AreaEffectCloudApplyEvent event)
    {
        if (denied(event.getEntity(), event.getEntity().getWorld(), GuildPermission.INTERACTING))
        {
            event.getAffectedEntities().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event)
    {
        if ((event.getEntity() instanceof Mob
                && module.getGuildWorldProtectionListener().isGuildWorld(event.getBlock().getWorld()))
                || denied(event.getEntity(), event.getBlock().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysicalInteract(EntityInteractEvent event)
    {
        // Turtle egg trampling does not fire EntityChangeBlockEvent.
        if ((event.getEntity() instanceof Mob && event.getBlock().getType() == Material.TURTLE_EGG
                && module.getGuildWorldProtectionListener().isGuildWorld(event.getBlock().getWorld()))
                || denied(event.getEntity(), event.getBlock().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompost(EntityCompostItemEvent event)
    {
        if ((event.getEntity() instanceof Mob
                && module.getGuildWorldProtectionListener().isGuildWorld(event.getBlock().getWorld()))
                || denied(event.getEntity(), event.getBlock().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event)
    {
        if (denied(event.getEntity(), event.getBlock().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event)
    {
        if (module.getGuildWorldProtectionListener().isGuildWorld(event.getLocation().getWorld()))
        {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event)
    {
        if (module.getGuildWorldProtectionListener().isGuildWorld(event.getBlock().getWorld()))
        {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event)
    {
        World world = event.getCaught() == null ? event.getHook().getWorld() : event.getCaught().getWorld();
        if (denied(event.getPlayer(), world, GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event)
    {
        if (event.getEntity() instanceof Player player
                && denied(player, event.getItem().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event)
    {
        if (denied(event.getPlayer(), event.getEntity().getWorld(), GuildPermission.BLOCK_BREAKING))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event)
    {
        if (denied(event.getPlayer(), event.getItemDrop().getWorld(), GuildPermission.INTERACTING))
        {
            event.setCancelled(true);
        }
    }

    private boolean denied(Entity source, World world, GuildPermission permission)
    {
        return denied(actor(source), world, permission);
    }

    private boolean denied(UUID actor, World world, GuildPermission permission)
    {
        return actor != null && !module.getGuildWorldProtectionListener().canUse(actor, world, permission);
    }

    private UUID actor(Entity source)
    {
        if (source instanceof Player player)
        {
            return player.getUniqueId();
        }
        if (source instanceof Projectile projectile)
        {
            ProjectileSource shooter = projectile.getShooter();
            return shooter == null ? projectile.getOwnerUniqueId() : actor(shooter);
        }
        if (source instanceof AreaEffectCloud cloud)
        {
            ProjectileSource shooter = cloud.getSource();
            return shooter == null ? cloud.getOwnerUniqueId() : actor(shooter);
        }
        if (source instanceof TNTPrimed tnt)
        {
            return actor(tnt.getSource());
        }
        if (source instanceof Tameable tameable)
        {
            return tameable.getOwnerUniqueId();
        }
        return null;
    }

    private UUID actor(ProjectileSource source)
    {
        return source instanceof Entity entity ? actor(entity) : null;
    }
}
