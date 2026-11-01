package dev.plex.guild;

import lombok.Getter;

/** The actor's guild role allows the action, but the actor lacks its permission node. */
@Getter
public final class GuildPermissionException extends SecurityException
{
    private final GuildActionNode node;

    public GuildPermissionException(GuildActionNode node)
    {
        super("The actor lacks " + node.getNode() + " to " + node.getAction());
        this.node = node;
    }
}
