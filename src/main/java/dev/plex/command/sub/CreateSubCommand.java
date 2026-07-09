package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateSubCommand extends GuildSubCommand
{
    public CreateSubCommand()
    {
        super(command("create")
                .description("Creates a guild with a specified name")
                .usage("/guild <command> <name>")
                .aliases("make")
                .permission("plex.guilds.create")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return usage();
        }
        assert player != null;
        if (Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).isPresent())
        {
            return messageComponent("alreadyInGuild");
        }
        Guilds.get().getGuildRepository().createGuild(player, StringUtils.join(args, " "))
                .thenCompose(guild -> Guilds.get().getGuildWorldService().ensureWorld(guild).thenApply(world -> guild))
                .whenComplete((guild, throwable) ->
        {
            if (throwable != null)
            {
                send(player, messageComponent("guildStorageFailed"));
                return;
            }
            Guilds.get().getGuildHolder().addGuild(guild);
            send(player, messageComponent("guildCreated", guild.getName()));
        });
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
