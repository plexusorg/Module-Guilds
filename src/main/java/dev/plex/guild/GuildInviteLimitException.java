package dev.plex.guild;

import java.time.Duration;
import lombok.Getter;

@Getter
public final class GuildInviteLimitException extends RuntimeException
{
    private final Duration retryAfter;

    public GuildInviteLimitException(Duration retryAfter)
    {
        super("The guild has reached its daily invite limit");
        this.retryAfter = retryAfter;
    }
}
