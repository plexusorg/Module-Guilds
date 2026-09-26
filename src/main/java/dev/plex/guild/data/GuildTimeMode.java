package dev.plex.guild.data;

public enum GuildTimeMode
{
    CYCLE,
    DAY,
    NOON,
    SUNSET,
    NIGHT;

    public GuildTimeMode next()
    {
        GuildTimeMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
