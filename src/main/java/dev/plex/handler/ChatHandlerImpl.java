package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.data.Member;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
            module.broadcastToGuild(guild, module.messageComponent("guildChatMessage", Placeholder.parsed("player", player.getName()), Placeholder.parsed("content", message)));
            if (module.getConfig().getBoolean("guilds.log-chat-message"))
            {
                Bukkit.getConsoleSender().sendMessage(
                        module.messageComponent("guildChatConsoleLog", Placeholder.parsed("guild", guild.getName()), Placeholder.parsed("guild_id", guild.getGuildUuid().toString()), Placeholder.parsed("player", player.getName()), Placeholder.parsed("content", message)));
            }
            event.setCancelled(true);
        });
    }
}
