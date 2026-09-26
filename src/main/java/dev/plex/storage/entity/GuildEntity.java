package dev.plex.storage.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuildEntity
{
    private String guildUuid;
    private String name;
    private String prefix;
    private String prefixKey;
    private String ownerUuid;
    private long createdAt;
    private String timeMode;
    private String weatherMode;
    private String spawnWorld;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;
    private Float spawnYaw;
    private Float spawnPitch;

    public GuildEntity()
    {
    }
}
