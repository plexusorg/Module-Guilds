package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.guild.data.Member;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Objects;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatHandlerImpl implements Listener
{
    @EventHandler
    public void doChat(AsyncChatEvent event)
    {
        Player player = event.getPlayer();
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresent(guild ->
        {
            Member member = guild.getMember(player.getUniqueId());
            if (member == null || !member.isChat())
            {
                return;
            }

            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(memberPlayer ->
                    memberPlayer.sendMessage(Guilds.get().messageComponent("guildChatMessage", player.getName(), message)));
            if (Guilds.get().getConfig().getBoolean("guilds.log-chat-message"))
            {
                Bukkit.getConsoleSender().sendMessage(Guilds.get().messageComponent("guildChatConsoleLog", guild.getName(), guild.getGuildUuid(), player.getName(), message));
            }
            event.setCancelled(true);
        });
    }
}
