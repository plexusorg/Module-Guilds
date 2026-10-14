package dev.plex.storage;

import dev.plex.guild.Guild;
import dev.plex.guild.PendingGuild;
import java.util.List;

public record LoadedGuilds(List<Guild> active, List<PendingGuild> pending)
{
}
