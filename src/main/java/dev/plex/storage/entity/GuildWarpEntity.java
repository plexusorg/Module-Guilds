package dev.plex.storage.entity;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@DatabaseTable
public class GuildWarpEntity
{
    @DatabaseField(generatedId = true, columnName = "id")
    private long id;

    @DatabaseField(columnName = "guild_uuid", width = 46, canBeNull = false)
    private String guildUuid;

    @DatabaseField(columnName = "name", width = 16, canBeNull = false)
    private String name;

    @DatabaseField(columnName = "world", width = 128, canBeNull = false)
    private String world;

    @DatabaseField(columnName = "x")
    private double x;

    @DatabaseField(columnName = "y")
    private double y;

    @DatabaseField(columnName = "z")
    private double z;

    @DatabaseField(columnName = "yaw")
    private float yaw;

    @DatabaseField(columnName = "pitch")
    private float pitch;

    public GuildWarpEntity()
    {
    }
}
