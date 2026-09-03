package dev.plex.command.sub;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WarpListSubCommand extends GuildSubCommand
{
    public WarpListSubCommand(Guilds module)
    {
        super(module, command("warps")
                .description("Displays a clickable list of warps")
                .usage("/guild <command>")
                .aliases("listwarps")
                .permission("plex.guilds.warps")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            player.sendMessage(getWarps(guild));
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
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
