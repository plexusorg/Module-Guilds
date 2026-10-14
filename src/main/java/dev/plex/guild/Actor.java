package dev.plex.guild;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record Actor(@Nullable UUID uuid, boolean staffOverride)
{
}
