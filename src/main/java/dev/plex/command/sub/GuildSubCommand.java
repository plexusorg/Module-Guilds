package dev.plex.command.sub;

import dev.plex.command.CommandSpec;
import dev.plex.command.SimplePlexCommand;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GuildSubCommand extends SimplePlexCommand
{
    protected GuildSubCommand(CommandSpec commandSpec)
    {
        super(commandSpec);
    }

    public final Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        return execute(sender, player, args);
    }

    public final @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args)
    {
        return suggestions(sender, alias, args);
    }
}
