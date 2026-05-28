package dev.plex.storage.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuildWarpEntity
{
    private long id;
    private String guildUuid;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public GuildWarpEntity()
    {
    }
}
