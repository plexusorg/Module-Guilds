package dev.plex.guild.data;

public enum GuildWorldAccess
{
    PRIVATE("Private"),
    PUBLIC_VIEW("Public view"),
    PUBLIC_BUILD("Public build");

    private final String label;

    GuildWorldAccess(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }

    public GuildWorldAccess next()
    {
        GuildWorldAccess[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
