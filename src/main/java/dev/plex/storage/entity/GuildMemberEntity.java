package dev.plex.storage.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuildMemberEntity
{
    private long id;
    private String guildUuid;
    private String playerUuid;
    private String role;
    private long joinedAt;

    public GuildMemberEntity()
    {
    }
}
