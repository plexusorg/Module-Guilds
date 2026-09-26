package dev.plex.hook;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import dev.plex.Guilds;
import dev.plex.guild.data.GuildPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class GuildWorldEditHook
{
    private final Guilds module;
    private final Component denialMessage;

    public GuildWorldEditHook(Guilds module)
    {
        this.module = module;
        this.denialMessage = module.messageComponent("guildWorldPermissionDenied");
    }

    public Runnable register()
    {
        WorldEdit.getInstance().getEventBus().register(this);
        return () -> WorldEdit.getInstance().getEventBus().unregister(this);
    }

    public static @Nullable String selectionWorldName(String playerName)
    {
        LocalSession session = WorldEdit.getInstance().getSessionManager().findByName(playerName);
        World world = session == null ? null : session.getSelectionWorld();
        return world == null ? null : world.getName();
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event)
    {
        Actor actor = event.getActor();
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || actor == null || !actor.isPlayer()
                || event.getWorld() == null)
        {
            return;
        }
        if (module.getGuildWorldProtectionListener().canUse(actor.getUniqueId(), event.getWorld().getName(), GuildPermission.BUILD))
        {
            return;
        }
        event.setCancelled(true);
        Player player = Bukkit.getPlayer(actor.getUniqueId());
        if (player != null)
        {
            player.sendMessage(denialMessage);
        }
    }
}
