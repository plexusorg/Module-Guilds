package dev.plex.world;

import java.util.Locale;

public enum GuildWorldType
{
    OVERWORLD("normal"), NETHER("nether"), END("the_end"), SUPERFLAT("normal");

    private final String environment;

    GuildWorldType(String environment)
    {
        this.environment = environment;
    }

    public String environment()
    {
        return environment;
    }

    public static GuildWorldType parse(String input)
    {
        return valueOf(input.toUpperCase(Locale.ROOT));
    }
}
