package dev.plex.guild.data;

public enum GuildWeatherMode
{
    CYCLE,
    CLEAR,
    RAIN,
    THUNDER;

    public GuildWeatherMode next()
    {
        GuildWeatherMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
