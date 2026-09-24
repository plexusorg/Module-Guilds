package dev.plex.command;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.command.sub.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class GuildCommand extends SimplePlexCommand
{
    private static final int HELP_PAGE_SIZE = 5;

    private final Guilds module;
    private final List<GuildSubCommand> subCommands = Lists.newArrayList();

    public GuildCommand(Guilds module)
    {
        super(command("guild")
                .description("Guild commands and help")
                .aliases("guilds,g")
                .permission("plex.guilds.guild")
                .build());
        this.module = module;
        this.registerSubCommand(new MenuSubCommand(module));
        this.registerSubCommand(new InfoSubCommand(module));
        this.registerSubCommand(new CreateSubCommand(module));
        this.registerSubCommand(new InviteSubCommand(module));
        this.registerSubCommand(new AcceptSubCommand(module));
        this.registerSubCommand(new WorldSubCommand(module));
        this.registerSubCommand(new VisitSubCommand(module));
        this.registerSubCommand(new GuestAccessSubCommand(module, true));
        this.registerSubCommand(new GuestAccessSubCommand(module, false));
        this.registerSubCommand(new GuestsSubCommand(module));
        this.registerSubCommand(new HomeSubCommand(module));
        this.registerSubCommand(new ChatSubCommand(module));
        this.registerSubCommand(new WarpSubCommand(module));
        this.registerSubCommand(new WarpListSubCommand(module));
        this.registerSubCommand(new SetHomeSubCommand(module));
        this.registerSubCommand(new SetWarpSubCommand(module));
        this.registerSubCommand(new PermissionsSubCommand(module));
        this.registerSubCommand(new PrefixSubCommand(module));
        this.registerSubCommand(new OwnerSubCommand(module));
        this.registerSubCommand(new DenySubCommand(module));
        this.registerSubCommand(new LeaveSubCommand(module));
        this.registerSubCommand(new DisbandSubCommand(module));
        this.registerSubCommand(new ResetWorldSubCommand(module));
    }

    @Override
    protected void configureCommand(com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack> command)
    {
        command.executes(context -> executeCommand(context, this::executeRoot));
        var subcommand = word("subcommand")
                .suggests((context, builder) -> suggestMatching(builder, subcommandNames()))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), null, null)));
        var first = word("first")
                .suggests((context, builder) -> suggestArguments(context, builder, null))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), string(context, "first"), null)));
        first.then(greedyString("remaining")
                .suggests((context, builder) -> suggestArguments(context, builder, string(context, "first")))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), string(context, "first"), normalize(string(context, "remaining"))))));
        subcommand.then(first);
        command.then(subcommand);
    }

    private Component executeRoot(CommandSender sender, Player player)
    {
        if (!module.isReady())
        {
            return messageComponent(module.isLoadFailed() ? "guildStorageFailed" : "guildLoading");
        }
        return helpPage(1);
    }

    private Component dispatch(CommandSender sender, Player player, String label, String first, String remaining)
    {
        if (!module.isReady())
        {
            return messageComponent(module.isLoadFailed() ? "guildStorageFailed" : "guildLoading");
        }
        if (label.equalsIgnoreCase("help"))
        {
            return help(first, remaining);
        }
        GuildSubCommand subCommand = getSubCommand(label);
        if (subCommand == null)
        {
            return messageComponent("guildCommandNotFound", Placeholder.unparsed("command", label));
        }
        if (subCommand.getRequiredSource() == RequiredCommandSource.CONSOLE && sender instanceof Player)
        {
            return messageComponent("noPermissionInGame");
        }
        if (subCommand.getRequiredSource() == RequiredCommandSource.IN_GAME && player == null)
        {
            return messageComponent("noPermissionConsole");
        }
        checkPermission(sender, subCommand.getPermission());
        return subCommand.executeSubCommand(sender, player, first, remaining);
    }

    private Component help(String first, String remaining)
    {
        if (remaining != null)
        {
            return messageComponent("guildHelpUsage");
        }
        if (first == null)
        {
            return helpPage(1);
        }
        if (first.matches("[+-]?[0-9]+"))
        {
            try
            {
                return helpPage(Integer.parseInt(first));
            }
            catch (NumberFormatException exception)
            {
                return messageComponent("guildHelpPageInvalid", Placeholder.unparsed("pages", Integer.toString(helpPageCount())));
            }
        }
        GuildSubCommand subCommand = getSubCommand(first);
        if (subCommand == null)
        {
            return messageComponent("guildHelpUsage");
        }
        Component usage = Component.text(subCommand.getUsage())
                .clickEvent(ClickEvent.suggestCommand("/guild " + subCommand.getName() + " "))
                .hoverEvent(Component.text("Click to fill in this command"));
        return messageComponent("guildHelpDetail",
                Placeholder.component("usage", usage),
                Placeholder.unparsed("description", subCommand.getDescription()));
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestArguments(
            com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String first)
    {
        if (string(context, "subcommand").equalsIgnoreCase("help") && first == null)
        {
            List<String> options = Lists.newArrayList();
            for (int page = 1; page <= helpPageCount(); page++)
            {
                options.add(Integer.toString(page));
            }
            subCommands.forEach(command -> options.add(command.getName()));
            return suggestMatching(builder, options);
        }
        GuildSubCommand subCommand = getSubCommand(string(context, "subcommand"));
        return subCommand == null ? builder.buildFuture()
                : suggestMatching(builder, subCommand.suggestSubCommand(context.getSource().getSender(), first));
    }

    private List<String> subcommandNames()
    {
        List<String> names = Lists.newArrayList("help");
        subCommands.forEach(command ->
        {
            names.add(command.getName());
            names.addAll(command.getAliases());
        });
        return names;
    }

    private String normalize(String value)
    {
        return value.isBlank() ? "" : String.join(" ", value.trim().split("\\s+"));
    }

    private GuildSubCommand getSubCommand(String label)
    {
        return subCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(label)
                        || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label)))
                .findFirst().orElse(null);
    }

    private void registerSubCommand(GuildSubCommand subCommand)
    {
        subCommands.add(subCommand);
    }

    private int helpPageCount()
    {
        return (subCommands.size() + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE;
    }

    private Component helpPage(int page)
    {
        int pages = helpPageCount();
        if (page < 1 || page > pages)
        {
            return messageComponent("guildHelpPageInvalid", Placeholder.unparsed("pages", Integer.toString(pages)));
        }
        Component commands = Component.empty();
        int start = (page - 1) * HELP_PAGE_SIZE;
        int end = Math.min(start + HELP_PAGE_SIZE, subCommands.size());
        for (int i = start; i < end; i++)
        {
            GuildSubCommand subCommand = subCommands.get(i);
            if (i > start)
            {
                commands = commands.append(Component.newline());
            }
            commands = commands.append(messageComponent("guildsCommandDisplay",
                    Placeholder.unparsed("command", "/guild " + subCommand.getName()),
                    Placeholder.unparsed("description", subCommand.getDescription()))
                    .clickEvent(ClickEvent.runCommand("/guild help " + subCommand.getName()))
                    .hoverEvent(Component.text("Click for usage")));
        }
        Component navigation = Component.empty();
        if (page > 1)
        {
            navigation = navigation.append(messageComponent("guildHelpBack")
                    .clickEvent(ClickEvent.runCommand("/guild help " + (page - 1))));
        }
        if (page < pages)
        {
            navigation = navigation.append(messageComponent("guildHelpNext")
                    .clickEvent(ClickEvent.runCommand("/guild help " + (page + 1))));
        }
        return messageComponent("guildHelpPage",
                Placeholder.unparsed("page", Integer.toString(page)),
                Placeholder.unparsed("pages", Integer.toString(pages)),
                Placeholder.component("content", commands),
                Placeholder.component("navigation", navigation));
    }
}
