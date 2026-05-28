package dev.plex.storage.entity;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@DatabaseTable
public class GuildEntity
{
    @DatabaseField(id = true, columnName = "guild_uuid", width = 46)
    private String guildUuid;

    @DatabaseField(columnName = "name", width = 64, canBeNull = false)
    private String name;

    @DatabaseField(columnName = "prefix", width = 64)
    private String prefix;

    @DatabaseField(columnName = "owner_uuid", width = 46, canBeNull = false)
    private String ownerUuid;

    @DatabaseField(columnName = "created_at")
    private long createdAt;

    @DatabaseField(columnName = "home_world", width = 128)
    private String homeWorld;

    @DatabaseField(columnName = "home_x")
    private Double homeX;

    @DatabaseField(columnName = "home_y")
    private Double homeY;

    @DatabaseField(columnName = "home_z")
    private Double homeZ;

    @DatabaseField(columnName = "home_yaw")
    private Float homeYaw;

    @DatabaseField(columnName = "home_pitch")
    private Float homePitch;

    @DatabaseField(columnName = "motd", width = 3000)
    private String motd;

    @DatabaseField(columnName = "tag_enabled")
    private boolean tagEnabled = true;

    @DatabaseField(columnName = "public")
    private boolean publicGuild;

    public GuildEntity()
    {
    }
}
