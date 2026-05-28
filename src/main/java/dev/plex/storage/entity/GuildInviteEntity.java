package dev.plex.storage.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuildInviteEntity
{
    private long id;
    private String guildUuid;
    private String inviterUuid;
    private String inviteeUuid;
    private long expiresAt;

    public GuildInviteEntity()
    {
    }
}
