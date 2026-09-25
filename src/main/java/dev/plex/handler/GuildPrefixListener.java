package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.api.event.PlayerPrefixEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Contributes each player's guild prefix to both the chat and tab-list targets.
 * Reads only the cached prefix component on the guild; it never parses text here.
 */
public final class GuildPrefixListener implements Listener
{
    private final Guilds module;

    public GuildPrefixListener(Guilds module)
    {
        this.module = module;
    }

    @EventHandler
    public void onPrefix(PlayerPrefixEvent event)
    {
        module.getGuildHolder().guild(event.getPlayer().getUniqueId())
                .ifPresent(guild -> event.addPrefix(guild.getPrefixComponent()));
    }
}
