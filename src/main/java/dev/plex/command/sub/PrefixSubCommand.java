package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildPrefix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PrefixSubCommand extends GuildSubCommand
{
    private static final String SET = "set";
    private static final String CLEAR = "clear";

    public PrefixSubCommand(Guilds module)
    {
        super(module, command("prefix")
                .description("Set or clear your guild prefix")
                .usage("/guild <command> <set <text>|clear>")
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
    public List<HelpEntry> helpEntries(@Nullable Player player)
    {
        return List.of(
                new HelpEntry("/guild prefix set <text>", PlainTextComponentSerializer.plainText()
                        .serialize(messageComponent("guildPrefixHelp")), "/guild prefix set "),
                new HelpEntry("/guild prefix clear", "Remove the guild prefix", "/guild prefix clear"));
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        boolean set = SET.equalsIgnoreCase(first);
        if (set ? remaining == null : !CLEAR.equalsIgnoreCase(first) || remaining != null)
        {
            return usage();
        }
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.isOwner(player.getUniqueId()))
        {
            return messageComponent("guildNotOwner");
        }
        GuildPrefix prefix;
        try
        {
            prefix = GuildPrefix.parse(set ? remaining : null, module.api().messages()::playerText);
        }
        catch (IllegalArgumentException exception)
        {
            return messageComponent("guildPrefixInvalid");
        }
        module.getGuildMutationService().updatePrefix(guild, player.getUniqueId(), prefix).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(failureMessage(throwable, null));
                return;
            }
            player.sendMessage(prefix.text() == null ? messageComponent("guildPrefixCleared")
                    : messageComponent("guildPrefixSet", Placeholder.component("prefix", prefix.component())));
        });
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? List.of(SET, CLEAR) : List.of();
    }
}
