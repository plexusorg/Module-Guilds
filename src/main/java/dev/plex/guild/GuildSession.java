package dev.plex.guild;

import java.util.UUID;

public record GuildSession(long id, UUID playerUuid, String playerName, long startedAt, Long endedAt, long actions)
{
}
