package dev.plex.storage.entity;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@DatabaseTable
public class GuildInviteEntity
{
    @DatabaseField(generatedId = true, columnName = "id")
    private long id;

    @DatabaseField(columnName = "guild_uuid", width = 46, canBeNull = false)
    private String guildUuid;

    @DatabaseField(columnName = "inviter_uuid", width = 46, canBeNull = false)
    private String inviterUuid;

    @DatabaseField(columnName = "invitee_uuid", width = 46, canBeNull = false)
    private String inviteeUuid;

    @DatabaseField(columnName = "expires_at")
    private long expiresAt;

    public GuildInviteEntity()
    {
    }
}
