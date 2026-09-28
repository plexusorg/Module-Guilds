package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildWorldAccess;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AccessSubCommand extends GuildSubCommand
{
    public AccessSubCommand(Guilds module)
    {
        super(module, command("access")
                .description("Set who can visit and build in your guild world")
                .usage("/guild <command> <private|view|build>")
                .permission("plex.guilds.access")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        Guild guild = guildOf(player);
        return guild != null && guild.canManage(player.getUniqueId());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null || remaining != null)
        {
            return usage();
        }
        assert player != null;
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.canManage(player.getUniqueId()))
        {
            return messageComponent("guildNotManager");
        }
        GuildWorldAccess access = switch (first.toLowerCase(Locale.ROOT))
        {
            case "private" -> GuildWorldAccess.PRIVATE;
            case "view" -> GuildWorldAccess.PUBLIC_VIEW;
            case "build" -> GuildWorldAccess.PUBLIC_BUILD;
            default -> null;
        };
        if (access == null)
        {
            return usage();
        }
        module.getGuildMutationService().setWorldAccess(guild, player.getUniqueId(), access).whenComplete((unused, throwable) ->
                player.sendMessage(throwable == null
                        ? messageComponent("guildWorldAccessSet", Placeholder.unparsed("access", access.label()))
                        : failureMessage(throwable, null)));
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? List.of("private", "view", "build") : List.of();
    }
}
