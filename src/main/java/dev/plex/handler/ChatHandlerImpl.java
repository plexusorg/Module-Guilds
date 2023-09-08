package dev.plex.handler;

import dev.plex.Guilds;
import dev.plex.Plex;
import dev.plex.guild.data.Member;
import dev.plex.hook.VaultHook;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatHandlerImpl
{
    private final static TextReplacementConfig URL_REPLACEMENT_CONFIG = TextReplacementConfig.builder().match("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").replacement((matchResult, builder) -> Component.empty().content(matchResult.group()).clickEvent(ClickEvent.openUrl(matchResult.group()))).build();
    private final PlexChatRenderer renderer = new PlexChatRenderer();

    @EventHandler
    public void doChat(AsyncChatEvent event)
    {
        event.renderer(renderer);
        Player player = event.getPlayer();
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresent(guild ->
        {
            Member member = guild.getMember(player.getUniqueId());
            if (member == null)
            {
                return;
            }
            if (!member.isChat())
            {
                return;
            }
            guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(player1 ->
            {
                player1.sendMessage(PlexUtils.messageComponent("guildChatMessage", player.getName(), PlainTextComponentSerializer.plainText().serialize(event.message())));
            });
            if (Guilds.get().getConfig().isBoolean("guilds.log-chat-message"))
            {
                Bukkit.getConsoleSender().sendMessage(PlexUtils.messageComponent("guildChatConsoleLog", guild.getName(), guild.getGuildUuid(), player.getName(), PlainTextComponentSerializer.plainText().serialize(event.message())));
            }
            event.setCancelled(true);
        });
    }

    public static class PlexChatRenderer implements ChatRenderer
    {
        @Override
        public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName, @NotNull Component message, @NotNull Audience viewer)
        {
            String text = PlexUtils.getTextFromComponent(message);

            PlexPlayer plexPlayer = Plex.get().getPlayerCache().getPlexPlayerMap().get(source.getUniqueId());
            Component prefix = VaultHook.getPrefix(plexPlayer);

            AtomicBoolean guildPrefix = new AtomicBoolean(false);
            AtomicReference<Component> component = new AtomicReference<>(Component.empty());
            Guilds.get().getGuildHolder().getGuild(source.getUniqueId()).ifPresent(guild ->
            {
                if (guild.getPrefix() != null)
                {
                    component.set(component.get().append(SafeMiniMessage.mmDeserializeWithoutEvents(guild.getPrefix())));
                    guildPrefix.set(true);
                }
            });

            if (prefix != null)
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
