package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixSubCommand extends GuildSubCommand
{
    // Count a head or sprite once, not by the length of its plain-text fallback.
    private static final PlainTextComponentSerializer DISPLAY_TEXT = PlainTextComponentSerializer.builder()
            .flattener(ComponentFlattener.basic().toBuilder()
                    .mapper(ObjectComponent.class, component -> "￼").build())
            .build();

    public PrefixSubCommand(Guilds module)
    {
        super(module, command("prefix")
                .description("Set your guild prefix, or clear it with no text")
                .usage("/guild <command> [text]")
                .permission("plex.guilds.prefix")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        Guild guild = guildOf(player);
        return guild != null && guild.isOwner(player.getUniqueId());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.isOwner(player.getUniqueId()))
        {
            return messageComponent("guildNotOwner");
        }
        String prefix = first == null ? null : arguments(first, remaining);
        Component renderedPrefix = prefix == null ? Component.empty() : module.api().messages().playerText(prefix);
        String displayedText = DISPLAY_TEXT.serialize(renderedPrefix);
        if (displayedText.codePointCount(0, displayedText.length()) > 64)
        {
            return messageComponent("guildPrefixTooLong");
        }
        module.getGuildMutationService().updatePrefix(guild, player.getUniqueId(), prefix).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(failureMessage(throwable, null));
                return;
            }
            player.sendMessage(prefix == null ? messageComponent("guildPrefixCleared")
                    : messageComponent("guildPrefixSet", Placeholder.component("prefix", renderedPrefix)));
        });
        return null;
    }
}
