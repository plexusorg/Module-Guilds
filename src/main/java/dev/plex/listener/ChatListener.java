package dev.plex.listener;

import dev.plex.Guilds;
import dev.plex.guild.GuildMember;
import dev.plex.util.minimessage.SafeMiniMessage;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class ChatListener extends PlexListener
{
    private GuildChatRenderer guildChatRenderer = null;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event)
    {
        if (event.isCancelled())
        {
            return;
        }

        if (guildChatRenderer == null)
        {
            guildChatRenderer = new GuildChatRenderer(event.renderer());
        }

        event.renderer(guildChatRenderer);

        final Player player = event.getPlayer();
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresent(guild ->
        {
            if (member.isChat())
            {
                guild.chat(player, event.message());
                event.setCancelled(true);
            }
        });
    }

    private record GuildChatRenderer(ChatRenderer renderer) implements ChatRenderer
    {
        @Override
        public @NotNull Component render(@NotNull Player player, @NotNull Component sourceDisplayName, @NotNull Component message, @NotNull Audience audience)
        {
            Component outcome = Component.empty();
            GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
            AtomicReference<String> atomicPrefix = new AtomicReference<>(null);

            member.getGuild().ifPresent(guild ->
            {
                if (guild.isPrefixEnabled())
                {
                    String rankName = "";
                    if (guild.getRankByMember(member).isPresent())
                    {
                        rankName = guild.getRankByMember(member).get().getName();
                    }

                    atomicPrefix.set(guild.getPrefix().replace("%rank%", rankName).replace("%name%", guild.getDisplayName()));
                }
            });

            if (atomicPrefix.get() != null && member.isPrefix())
            {
                outcome = outcome.append(SafeMiniMessage.mmDeserializeWithoutEvents(atomicPrefix.get())).append(Component.space());
            }

            outcome = outcome.append(renderer.render(player, sourceDisplayName, message, audience));
            return outcome;
        }
    }
}
