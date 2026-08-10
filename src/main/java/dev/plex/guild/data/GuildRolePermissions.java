package dev.plex.guild.data;

public record GuildRolePermissions(boolean blockBreaking, boolean blockPlacing, boolean interacting)
{
    public static GuildRolePermissions defaults(GuildRole role)
    {
        return role == GuildRole.OWNER ? new GuildRolePermissions(true, true, true) : new GuildRolePermissions(false, false, false);
    }

    public boolean hasPermission(GuildPermission permission)
    {
        return switch (permission)
        {
            case BLOCK_BREAKING -> blockBreaking;
            case BLOCK_PLACING -> blockPlacing;
            case INTERACTING -> interacting;
        };
    }

    public GuildRolePermissions withPermission(GuildPermission permission, boolean enabled)
    {
        return switch (permission)
        {
            case BLOCK_BREAKING -> new GuildRolePermissions(enabled, blockPlacing, interacting);
            case BLOCK_PLACING -> new GuildRolePermissions(blockBreaking, enabled, interacting);
            case INTERACTING -> new GuildRolePermissions(blockBreaking, blockPlacing, enabled);
        };
    }
}
