package dev.plex.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.command.sub.*;
import dev.plex.util.GuildUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@CommandParameters(name = "guild", description = "Guild menu", aliases = "guilds,g")
@CommandPermissions(permission = "plex.guilds.guild")
public class GuildCommand extends PlexCommand
{
    private final List<PlexCommand> subCommands = Lists.newArrayList();

    public GuildCommand()
    {
        try
        {
            this.registerSubCommand(new CreateSubCommand());
            this.registerSubCommand(new InfoSubCommand());
            this.registerSubCommand(new PrefixSubCommand());
            this.registerSubCommand(new SetWarpSubCommand());
            this.registerSubCommand(new WarpSubCommand());
            this.registerSubCommand(new WarpListSubCommand());
            this.registerSubCommand(new ChatSubCommand());
            this.registerSubCommand(new SetHomeSubCommand());
            this.registerSubCommand(new HomeSubCommand());
            this.registerSubCommand(new OwnerSubCommand());
            this.registerSubCommand(new InviteSubCommand());
        }
        catch (Exception e)
        {
            GuildUtil.throwExceptionSync(e);
        }
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return getSubs();
        }
        if (args[0].equalsIgnoreCase("help"))
        {
            if (args.length < 2)
            {
                return usage("/guild help <subcommand>");
            }
            PlexCommand subCommand = getSubCommand(args[1]);
            if (subCommand == null)
            {
                return messageComponent("guildCommandNotFound", args[1]);
            }
            CommandPermissions permissions = subCommand.getClass().getDeclaredAnnotation(CommandPermissions.class);
            return mmString("<gradient:gold:yellow>========<newline>").append(mmString("<gold>Command Name: <yellow>" + subCommand.getName())).append(Component.newline())
                    .append(mmString("<gold>Command Aliases: <yellow>" + StringUtils.join(subCommand.getAliases(), ", "))).append(Component.newline())
                    .append(mmString("<gold>Description: <yellow>" + subCommand.getDescription())).append(Component.newline())
                    .append(mmString("<gold>Permission: <yellow>" + permissions.permission())).append(Component.newline())
                    .append(mmString("<gold>Required Source: <yellow>" + permissions.source().name()));
        }
        PlexCommand subCommand = getSubCommand(args[0]);
        if (subCommand == null)
        {
            return messageComponent("guildCommandNotFound", args[0]);
        }

        CommandPermissions permissions = subCommand.getClass().getDeclaredAnnotation(CommandPermissions.class);
        if (permissions.source() == RequiredCommandSource.CONSOLE && commandSender instanceof Player)
        {
            return messageComponent("noPermissionInGame");
        }

        if (permissions.source() == RequiredCommandSource.IN_GAME && commandSender instanceof ConsoleCommandSender)
        {
            return messageComponent("noPermissionConsole");
        }

        checkPermission(player, permissions.permission());

        return subCommand.execute(commandSender, player, Arrays.copyOfRange(args, 1, args.length));
    }

    private PlexCommand getSubCommand(String label)
    {
        return subCommands.stream().filter(cmd ->
        {
            CommandParameters commandParameters = cmd.getClass().getDeclaredAnnotation(CommandParameters.class);
            return commandParameters.name().equalsIgnoreCase(label) || Arrays.stream(commandParameters.aliases().split(",")).anyMatch(s -> s.equalsIgnoreCase(label));
        }).findFirst().orElse(null);
    }

    private void registerSubCommand(PlexCommand subCommand)
    {
        if (!subCommand.getClass().isAnnotationPresent(CommandPermissions.class))
        {
            throw new RuntimeException("CommandPermissions annotation for guild sub command " + subCommand.getName() + " could not be found!");
        }

        if (!subCommand.getClass().isAnnotationPresent(CommandParameters.class))
        {
            throw new RuntimeException("CommandParameters annotation for guild sub command " + subCommand.getName() + " could not be found!");
        }
        this.subCommands.add(subCommand);
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1)
        {
            List<String> possibleCommands = Lists.newArrayList();
            if (!args[0].isEmpty())
            {
                subCommands.forEach(plexCommand ->
                {
                    plexCommand.getAliases().stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(possibleCommands::add);
                    if (plexCommand.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    {
                        possibleCommands.add(plexCommand.getName());
                    }
                });
            }
            return possibleCommands;
        }
        if (args.length >= 2)
        {
            PlexCommand subCommand = getSubCommand(args[0]);
            if (subCommand != null)
            {
                return subCommand.tabComplete(sender, alias, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return ImmutableList.of();
    }

    public Component getSubs()
    {
        Component commands = Component.empty();
        for (int i = 0; i < this.subCommands.size(); i++)
        {
            commands = commands.append(messageComponent("guildsCommandDisplay", "/guild " + this.subCommands.get(i).getName(), this.subCommands.get(i).getDescription()).clickEvent(ClickEvent.suggestCommand("/guild help " + this.subCommands.get(i).getName())));
            if (i < this.subCommands.size() - 1)
            {
                commands = commands.append(Component.newline());
            }
        }
        return messageComponent("guildsHelpCommand", commands);
    }

}
