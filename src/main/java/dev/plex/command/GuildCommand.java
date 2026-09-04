package dev.plex.command;

import static dev.plex.api.message.MessagePlaceholder.placeholder;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.command.sub.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class GuildCommand extends SimplePlexCommand
{
    private final Guilds module;
    private final List<GuildSubCommand> subCommands = Lists.newArrayList();

    public GuildCommand(Guilds module)
    {
        super(command("guild")
                .description("Guild menu")
                .aliases("guilds,g")
                .permission("plex.guilds.guild")
                .build());
        this.module = module;
        this.registerSubCommand(new CreateSubCommand(module));
        this.registerSubCommand(new DisbandSubCommand(module));
        this.registerSubCommand(new LeaveSubCommand(module));
        this.registerSubCommand(new InfoSubCommand(module));
        this.registerSubCommand(new PrefixSubCommand(module));
        this.registerSubCommand(new SetWarpSubCommand(module));
        this.registerSubCommand(new WarpSubCommand(module));
        this.registerSubCommand(new WarpListSubCommand(module));
        this.registerSubCommand(new ChatSubCommand(module));
        this.registerSubCommand(new SetHomeSubCommand(module));
        this.registerSubCommand(new HomeSubCommand(module));
        this.registerSubCommand(new WorldSubCommand(module));
        this.registerSubCommand(new PermissionsSubCommand(module));
        this.registerSubCommand(new OwnerSubCommand(module));
        this.registerSubCommand(new InviteSubCommand(module));
        this.registerSubCommand(new AcceptSubCommand(module));
        this.registerSubCommand(new DenySubCommand(module));
        this.registerSubCommand(new MenuSubCommand(module));
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
        if (player == null)
        {
            return getSubs();
        }
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(
                guild -> module.getGuildMenuListener().openHome(player, guild),
                () -> player.sendMessage(messageComponent("guildNotFound"))
        );
        return null;
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
            return messageComponent("guildCommandNotFound", placeholder("command", label));
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
        if (first == null)
        {
            return usage("/guild help <subcommand>");
        }
        GuildSubCommand subCommand = getSubCommand(first);
        if (subCommand == null)
        {
            return messageComponent("guildCommandNotFound", placeholder("command", first));
        }
        return mmString("<gradient:gold:yellow>========<newline>").append(mmString("<gold>Command Name: <yellow>" + subCommand.getName())).append(Component.newline())
                .append(mmString("<gold>Command Aliases: <yellow>" + StringUtils.join(subCommand.getAliases(), ", "))).append(Component.newline())
                .append(mmString("<gold>Description: <yellow>" + subCommand.getDescription())).append(Component.newline())
                .append(mmString("<gold>Permission: <yellow>" + subCommand.getPermission())).append(Component.newline())
                .append(mmString("<gold>Required Source: <yellow>" + subCommand.getRequiredSource().name()));
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestArguments(
            com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String first)
    {
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

    public Component getSubs()
    {
        Component commands = Component.empty();
        for (int i = 0; i < this.subCommands.size(); i++)
        {
            commands = commands.append(messageComponent("guildsCommandDisplay", placeholder("command", "/guild " + this.subCommands.get(i).getName()), placeholder("description", this.subCommands.get(i).getDescription())).clickEvent(ClickEvent.suggestCommand("/guild help " + this.subCommands.get(i).getName())));
            if (i < this.subCommands.size() - 1)
            {
                commands = commands.append(Component.newline());
            }
        }
        return messageComponent("guildsHelpCommand", placeholder("content", commands));
    }

}
