package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.Plex;
import dev.plex.api.chat.IChatHandler;
import dev.plex.cache.PlayerCache;
import dev.plex.player.PlexPlayer;
import dev.plex.util.PlexUtils;
import dev.plex.util.minimessage.SafeMiniMessage;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatHandlerImpl implements IChatHandler
{
    private final static TextReplacementConfig URL_REPLACEMENT_CONFIG = TextReplacementConfig.builder().match("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").replacement((matchResult, builder) -> Component.empty().content(matchResult.group()).clickEvent(ClickEvent.openUrl(matchResult.group()))).build();
    private final PlexChatRenderer renderer = new PlexChatRenderer();

    @Override
    public void doChat(AsyncChatEvent event)
    {
        PlexPlayer plexPlayer = PlayerCache.getPlexPlayerMap().get(event.getPlayer().getUniqueId());
        Component prefix = Plex.get().getRankManager().getPrefix(plexPlayer);

        if (prefix != null)
        {
            renderer.hasPrefix = true;
            renderer.prefix = prefix;
        } else
        {
            renderer.hasPrefix = false;
            renderer.prefix = null;
        }

        event.renderer(renderer);
    }

    public static class PlexChatRenderer implements ChatRenderer
    {
        public boolean hasPrefix;
        public Component prefix;

        @Override
        public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName, @NotNull Component message, @NotNull Audience viewer)
        {
            String text = PlexUtils.getTextFromComponent(message);

            AtomicBoolean guildPrefix = new AtomicBoolean(false);
            AtomicReference<Component> component = new AtomicReference<>(Component.empty());
            Guilds.get().getGuildHolder().getGuild(source.getUniqueId()).ifPresent(guild -> {
                if (guild.getPrefix() != null)
                {
                    component.set(component.get().append(MiniMessage.miniMessage().deserialize(guild.getPrefix())));
                    guildPrefix.set(true);
                }
            });

            if (hasPrefix)
            {
                if (guildPrefix.get())
                {
                    component.set(component.get().append(Component.space()));
                }
                component.set(component.get().append(prefix));
            }

            return component.get().append(Component.space()).append(PlexUtils.mmDeserialize(Plex.get().config.getString("chat.name-color", "<white>") + MiniMessage.builder().tags(TagResolver.resolver(StandardTags.color(), StandardTags.rainbow(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.transition())).build().serialize(sourceDisplayName))).append(Component.space()).append(Component.text("»").color(NamedTextColor.GRAY)).append(Component.space()).append(SafeMiniMessage.mmDeserializeWithoutEvents(text)).replaceText(URL_REPLACEMENT_CONFIG);
        }
    }
}
