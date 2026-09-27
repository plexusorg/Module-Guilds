package dev.plex.guild.data;

public enum GuildWeatherMode
{
    CYCLE("Cycle"),
    CLEAR("Clear"),
    RAIN("Rain"),
    THUNDER("Thunder");

    private final String label;

    GuildWeatherMode(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }

    public GuildWeatherMode next()
    {
        GuildWeatherMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
