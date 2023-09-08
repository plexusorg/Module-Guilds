package dev.plex.command.sub;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@CommandParameters(name = "warps", aliases = "listwarps", usage = "/guild <command>", description = "Displays a clickable list of warps")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.warps")
public class WarpListSubCommand extends PlexCommand
{
    public WarpListSubCommand()
    {
        super(false);
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            send(player, getWarps(guild));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    public Component getWarps(Guild guild)
    {
        Set<String> warps = guild.getWarps().keySet();

        List<Component> components = Lists.newArrayList();
        warps.forEach(s -> components.add(mmString("<click:suggest_command:/guild warp " + s + ">" + s)));
        Component parent = mmString("<gold>Warps (" + warps.size() + "): ");
        for (int i = 0; i < components.size(); i++)
        {
            parent = parent.append(components.get(i));
            if (i < components.size() - 1)
            {
                parent = parent.append(mmString(", "));
            }
        }
        return parent;
    }
}
