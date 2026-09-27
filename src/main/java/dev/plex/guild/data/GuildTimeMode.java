package dev.plex.guild.data;

public enum GuildTimeMode
{
    CYCLE("Cycle"),
    DAY("Day"),
    NOON("Noon"),
    SUNSET("Sunset"),
    NIGHT("Night");

    private final String label;

    GuildTimeMode(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }

    public GuildTimeMode next()
    {
        GuildTimeMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
