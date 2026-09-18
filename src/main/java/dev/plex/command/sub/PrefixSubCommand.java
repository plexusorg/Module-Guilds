package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixSubCommand extends GuildSubCommand
{
    // Count a head or sprite once, not by the length of its plain-text fallback.
    private static final PlainTextComponentSerializer DISPLAY_TEXT = PlainTextComponentSerializer.builder()
            .flattener(ComponentFlattener.basic().toBuilder()
                    .mapper(ObjectComponent.class, component -> "\uFFFC").build())
            .build();

    public PrefixSubCommand(Guilds module)
    {
        super(module, command("prefix")
                .description("Sets the guild's default prefix")
                .usage("/guild <command> <prefix>")
                .aliases("tag,settag,setprefix")
                .permission("plex.guilds.prefix")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            if (first.equalsIgnoreCase("clear") || first.equalsIgnoreCase("off"))
            {
                module.getGuildMutationService().updatePrefix(guild, null).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    player.sendMessage(messageComponent("guildPrefixCleared"));
                });
                return;
            }
            String prefix = arguments(first, remaining);
            Component renderedPrefix = module.api().messages().playerText(prefix);
            String displayedText = DISPLAY_TEXT.serialize(renderedPrefix);
            if (displayedText.codePointCount(0, displayedText.length()) > 64)
            {
                player.sendMessage(messageComponent("guildPrefixTooLong"));
                return;
            }
            module.getGuildMutationService().updatePrefix(guild, prefix).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildPrefixSet", Placeholder.component("prefix", renderedPrefix)));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
