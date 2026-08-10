package dev.plex.storage.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuildRolePermissionEntity
{
    private long id;
    private String guildUuid;
    private String role;
    private boolean blockBreaking;
    private boolean blockPlacing;
    private boolean interacting;
}
