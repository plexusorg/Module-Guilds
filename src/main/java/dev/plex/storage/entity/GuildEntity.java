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
    private String ownerUuid;
    private long createdAt;
    private String homeWorld;
    private Double homeX;
    private Double homeY;
    private Double homeZ;
    private Float homeYaw;
    private Float homePitch;
    private String motd;
    private boolean tagEnabled = true;
    private boolean publicGuild;

    public GuildEntity()
    {
    }
}
