package dev.plex.guild;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** A disbanded guild that waits for finalization. A null deletedBy means the console. */
public record PendingGuild(Guild guild, long deletedAt, @Nullable UUID deletedBy)
{
    /** True when the retention window that ends at the cutoff no longer covers the disband. */
    public boolean isDue(long cutoff)
    {
        return deletedAt < cutoff;
    }
}
