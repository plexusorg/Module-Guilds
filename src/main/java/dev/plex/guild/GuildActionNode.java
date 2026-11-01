package dev.plex.guild;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

/**
 * Permission nodes for guild actions that a guild role alone does not allow.
 * A staff override bypasses every node. The action phrase fills the {@code <action>} placeholder of guildActionLocked.
 */
@Getter
public enum GuildActionNode
{
    DISBAND("plex.guilds.disband", "disband the guild"),
    WORLD_RESET("plex.guilds.world.reset", "reset the guild world"),
    TRANSFER("plex.guilds.transfer", "make another member the owner"),
    ROLES("plex.guilds.roles", "change member roles"),
    KICK("plex.guilds.kick", "kick members"),
    GUEST_BUILD("plex.guilds.guests.build", "let guests build");

    private final String node;
    private final String action;

    GuildActionNode(String node, String action)
    {
        this.node = node;
        this.action = action;
    }

    /** Returns true when the actor has a staff override or the given player has the node. */
    public boolean allows(@Nullable Permissible player, Actor actor)
    {
        return actor.staffOverride() || player != null && player.hasPermission(node);
    }

    /** Checks the node against the actor's online player. An offline actor fails the check. */
    public void require(Actor actor)
    {
        if (!allows(actor.uuid() == null ? null : Bukkit.getPlayer(actor.uuid()), actor))
        {
            throw new GuildPermissionException(this);
        }
    }
}
