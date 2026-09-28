package dev.plex.guild.data;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Guest(UUID playerUuid, boolean editing, Instant expiresAt)
{
    public Guest
    {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isActive(Instant now)
    {
        return expiresAt.isAfter(now);
    }
}
