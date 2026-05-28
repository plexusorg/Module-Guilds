package dev.plex.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.command.sub.*;
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

public class GuildCommand extends SimplePlexCommand
{
    private final List<GuildSubCommand> subCommands = Lists.newArrayList();

    public GuildCommand()
    {
        super(command("guild")
                .description("Guild menu")
                .aliases("guilds,g")
                .permission("plex.guilds.guild")
                .build());
        this.registerSubCommand(new CreateSubCommand());
        this.registerSubCommand(new DisbandSubCommand());
        this.registerSubCommand(new LeaveSubCommand());
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
        this.registerSubCommand(new AcceptSubCommand());
        this.registerSubCommand(new DenySubCommand());
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
            return mmString("<gradient:gold:yellow>========<newline>").append(mmString("<gold>Command Name: <yellow>" + subCommand.getName())).append(Component.newline())
                    .append(mmString("<gold>Command Aliases: <yellow>" + StringUtils.join(subCommand.getAliases(), ", "))).append(Component.newline())
                    .append(mmString("<gold>Description: <yellow>" + subCommand.getDescription())).append(Component.newline())
                    .append(mmString("<gold>Permission: <yellow>" + subCommand.getPermission())).append(Component.newline())
                    .append(mmString("<gold>Required Source: <yellow>" + subCommand.getRequiredSource().name()));
        }
        GuildSubCommand subCommand = getSubCommand(args[0]);
        if (subCommand == null)
        {
            return messageComponent("guildCommandNotFound", args[0]);
        }

        if (subCommand.getRequiredSource() == RequiredCommandSource.CONSOLE && commandSender instanceof Player)
        {
            return messageComponent("noPermissionInGame");
        }

        if (subCommand.getRequiredSource() == RequiredCommandSource.IN_GAME && commandSender instanceof ConsoleCommandSender)
        {
            return messageComponent("noPermissionConsole");
        }

        checkPermission(commandSender, subCommand.getPermission());

        return subCommand.executeSubCommand(commandSender, player, Arrays.copyOfRange(args, 1, args.length));
    }

    private GuildSubCommand getSubCommand(String label)
    {
        return subCommands.stream()
                .filter(cmd -> cmd.getName().equalsIgnoreCase(label) || cmd.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label)))
                .findFirst()
                .orElse(null);
    }

    private void registerSubCommand(GuildSubCommand subCommand)
    {
        if (Guilds.get() != null)
        {
            subCommand.bindModule(Guilds.get());
            if (Guilds.get().api() != null)
            {
                subCommand.bindApi(Guilds.get().api());
            }
        }
        this.subCommands.add(subCommand);
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1)
        {
            List<String> possibleCommands = Lists.newArrayList();
            subCommands.forEach(plexCommand ->
            {
                plexCommand.getAliases().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .forEach(possibleCommands::add);
                if (plexCommand.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                {
                    possibleCommands.add(plexCommand.getName());
                }
            });
            return possibleCommands;
        }
        if (args.length >= 2)
        {
            GuildSubCommand subCommand = getSubCommand(args[0]);
            if (subCommand != null)
            {
                return subCommand.suggestSubCommand(sender, alias, Arrays.copyOfRange(args, 1, args.length));
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
