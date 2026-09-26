package dev.plex.world;

public final class GuildWorldNotGeneratedException extends IllegalStateException
{
    public GuildWorldNotGeneratedException()
    {
        super("The guild owner must generate a world first");
    }
}
