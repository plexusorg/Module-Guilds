package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.data.Member;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatHandlerImpl implements Listener
{
    private final Guilds module;

    public ChatHandlerImpl(Guilds module)
    {
        this.module = module;
    }

    @EventHandler
    public void doChat(AsyncChatEvent event)
    {
        Player player = event.getPlayer();
        module.getGuildHolder().guild(player.getUniqueId()).ifPresent(guild ->
        {
            Member member = guild.getMember(player.getUniqueId());
            if (member == null || !member.isChat())
            {
                return;
            }

            module.sendChat(guild, player.getName(), event.message());
            event.setCancelled(true);
        });
    }
}
