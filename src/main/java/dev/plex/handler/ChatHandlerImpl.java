package dev.plex.handler;

import static dev.plex.api.message.MessagePlaceholder.placeholder;

import dev.plex.Guilds;
import dev.plex.guild.data.Member;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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

            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            module.broadcastToGuild(guild, module.messageComponent("guildChatMessage", placeholder("player", player.getName()), placeholder("content", message)));
            if (module.getConfig().getBoolean("guilds.log-chat-message"))
            {
                Bukkit.getConsoleSender().sendMessage(
                        module.messageComponent("guildChatConsoleLog", placeholder("guild", guild.getName()), placeholder("guild_id", guild.getGuildUuid()), placeholder("player", player.getName()), placeholder("content", message)));
            }
            event.setCancelled(true);
        });
    }
}
