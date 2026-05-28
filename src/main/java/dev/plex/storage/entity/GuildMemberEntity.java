package dev.plex.storage.entity;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@DatabaseTable
public class GuildMemberEntity
{
    @DatabaseField(generatedId = true, columnName = "id")
    private long id;

    @DatabaseField(columnName = "guild_uuid", width = 46, canBeNull = false)
    private String guildUuid;

    @DatabaseField(columnName = "player_uuid", width = 46, canBeNull = false)
    private String playerUuid;

    @DatabaseField(columnName = "role", width = 20, canBeNull = false)
    private String role;

    @DatabaseField(columnName = "joined_at")
    private long joinedAt;

    public GuildMemberEntity()
    {
    }
}
