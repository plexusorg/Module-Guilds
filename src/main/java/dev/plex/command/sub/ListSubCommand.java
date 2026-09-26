package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import java.util.Comparator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ListSubCommand extends GuildSubCommand
{
    private static final int PAGE_SIZE = 10;

    public ListSubCommand(Guilds module)
    {
        super(module, command("list").description("List guilds").usage("/guild <command> [page]")
                .permission("plex.guilds.list").build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        int page = 1;
        try
        {
            if (first != null)
            {
                page = Integer.parseInt(first);
            }
        }
        catch (NumberFormatException exception)
        {
            return usage();
        }
        var guilds = module.getGuildHolder().guilds().stream()
                .sorted(Comparator.comparing(Guild::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Guild::getGuildUuid)).toList();
        int pages = Math.max(1, (guilds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (remaining != null || page < 1 || page > pages)
        {
            return usage();
        }
        Component result = messageComponent("guildListHeader", Placeholder.unparsed("count", Integer.toString(guilds.size())),
                Placeholder.unparsed("page", Integer.toString(page)), Placeholder.unparsed("pages", Integer.toString(pages)));
        for (Guild guild : guilds.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, guilds.size())))
        {
            result = result.append(Component.newline()).append(messageComponent("guildListEntry",
                    Placeholder.unparsed("guild", guild.getName()), Placeholder.unparsed("members", Integer.toString(guild.getMembers().size())))
                    .clickEvent(ClickEvent.runCommand("/guild info " + guild.getGuildUuid()))
                    .hoverEvent(Component.text("Click to see guild information")));
        }
        if (page < pages)
        {
            result = result.append(Component.newline()).append(messageComponent("guildListNext",
                    Placeholder.unparsed("command", "/guild list " + (page + 1)))
                    .clickEvent(ClickEvent.runCommand("/guild list " + (page + 1))));
        }
        return result;
    }
}
