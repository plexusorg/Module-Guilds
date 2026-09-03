package dev.plex.guild.data;

import lombok.Data;

import java.util.UUID;

@Data
public class Member
{
    private final UUID uuid;
    private volatile GuildRole role;
    private volatile boolean chat;

    public Member(UUID uuid)
    {
        this(uuid, GuildRole.MEMBER);
    }

    public Member(UUID uuid, GuildRole role)
    {
        this.uuid = uuid;
        this.role = role;
    }

}
