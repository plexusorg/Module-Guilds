package dev.plex.command;

import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SubCommand extends PlexCommand
{
    public SubCommand()
    {
        super(false);

        if (!getClass().isAnnotationPresent(CommandParameters.class))
        {
            throw new RuntimeException("CommandParameters annotation for guild sub command " + getName() + " could not be found!");
        }

        if (!getClass().isAnnotationPresent(CommandPermissions.class))
        {
            throw new RuntimeException("CommandPermissions annotation for guild sub command " + getName() + " could not be found!");
        }
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] strings)
    {
        return null;
    }

    public abstract Component run(CommandSender sender, Player player, String[] args);
}
