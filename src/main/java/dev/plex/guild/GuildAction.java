package dev.plex.guild;

import com.google.gson.JsonElement;
import java.util.UUID;

public record GuildAction(long id, Long sessionId, Actor actor, UUID guildUuid, String type, long createdAt,
                          JsonElement before, JsonElement after, Long undoneAt, Long undoId)
{
}
