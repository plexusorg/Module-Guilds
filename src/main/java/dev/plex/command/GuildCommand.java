package dev.plex.command;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.sub.*;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CommandParameters(name = "guild", description = "Main guild command", aliases = "guilds,g")
@CommandPermissions(permission = "plex.guilds.guild")
public class GuildCommand extends PlexCommand
{
    private final List<SubCommand> subcommands = Lists.newArrayList();

    public GuildCommand()
    {
        try
        {
            subcommands.add(new CreateSubCommand());
            subcommands.add(new DeleteWarpSubCommand());
            subcommands.add(new DisbandSubCommand());
            subcommands.add(new HomeSubCommand());
            subcommands.add(new InfoSubCommand());
            subcommands.add(new MotdSubCommand());
            subcommands.add(new PrefixSubCommand());
            subcommands.add(new SetHomeSubCommand());
            subcommands.add(new SetOwnerSubCommand());
            subcommands.add(new SetWarpSubCommand());
            subcommands.add(new WarpSubCommand());
        }
        catch (Exception ex)
        {
            Guilds.logException(ex);
        }
    }

    @Override
    protected Component execute(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return getSubs();
        }

        SubCommand subCommand = getSubCommand(args[0]);
        if (subCommand == null)
        {
            return messageComponent("guildCommandNotFound", args[0]);
        }

        CommandPermissions permissions = subCommand.getClass().getDeclaredAnnotation(CommandPermissions.class);
        if (permissions.source() == RequiredCommandSource.CONSOLE && sender instanceof Player)
        {
            return messageComponent("noPermissionInGame");
        }

        if (permissions.source() == RequiredCommandSource.IN_GAME && isConsole(sender))
        {
            return messageComponent("noPermissionConsole");
        }

        checkPermission(sender, permissions.permission());
        return subCommand.run(sender, player, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1 && silentCheckPermission(sender, this.getPermission()))
        {
            return subcommands.stream().map(PlexCommand::getName).toList();
        }
        else if (args.length >= 2)
        {
            PlexCommand subCommand = getSubCommand(args[0]);
            if (subCommand != null && silentCheckPermission(sender, subCommand.getPermission()))
            {
                return subCommand.smartTabComplete(sender, s, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return Collections.emptyList();
    }

    private SubCommand getSubCommand(String label)
    {
        return subcommands.stream().filter(cmd ->
        {
            CommandParameters parameters = cmd.getClass().getDeclaredAnnotation(CommandParameters.class);
            return parameters.name().equalsIgnoreCase(label) || Arrays.stream(parameters.aliases().split(",")).anyMatch(s -> s.equalsIgnoreCase(label));
        }).findFirst().orElse(null);
    }

    public Component getSubs()
    {
        Component commands = Component.empty();
        for (int i = 0; i < this.subcommands.size(); i++)
        {
            commands = commands.append(messageComponent("guildsCommandDisplay", "/guild " + this.subcommands.get(i).getName(), this.subcommands.get(i).getDescription()).clickEvent(ClickEvent.suggestCommand("/guild help " + this.subcommands.get(i).getName())));
            if (i < this.subcommands.size() - 1)
            {
                commands = commands.append(Component.newline());
            }
        }
        return messageComponent("guildsHelpCommand", commands);
    }
}
