package dev.plex.command.sub;

import static dev.plex.api.message.MessagePlaceholder.placeholder;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateSubCommand extends GuildSubCommand
{
    public CreateSubCommand(Guilds module)
    {
        super(module, command("create")
                .description("Creates a guild with a specified name")
                .usage("/guild <command> <name>")
                .aliases("make")
                .permission("plex.guilds.create")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        if (module.getGuildHolder().guild(player.getUniqueId()).isPresent())
        {
            return messageComponent("alreadyInGuild");
        }
        Guild guildToCreate = Guild.create(player.getUniqueId(), arguments(first, remaining), module.getZoneId());
        module.getGuildRepository().createGuild(guildToCreate)
                .thenCompose(guild -> module.isGuildWorldsEnabled()
                        ? module.getGuildWorldService().ensureWorld(guild).thenApply(world -> guild)
                        : CompletableFuture.completedFuture(guild))
                .whenComplete((guild, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            module.getGuildHolder().addGuild(guild);
            player.sendMessage(messageComponent("guildCreated", placeholder("guild", guild.getName())));
        });
        return null;
    }


}
