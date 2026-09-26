package dev.plex.guild;

public final class GuildPrefixTakenException extends RuntimeException
{
    public GuildPrefixTakenException(Throwable cause)
    {
        super("The guild prefix is already taken", cause);
    }
}
