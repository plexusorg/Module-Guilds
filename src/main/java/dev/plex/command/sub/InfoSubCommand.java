package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InfoSubCommand extends GuildSubCommand
{
    public InfoSubCommand(Guilds module)
    {
        super(module, command("info")
                .description("Shows the guild's information")
                .usage("/guild <command>")
                .aliases("information")
                .permission("plex.guilds.info")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        resolveGuild(player, first, remaining).whenComplete((guild, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to look up guild information", failure);
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            if (guild == null)
            {
                player.sendMessage(messageComponent("guildNotFound"));
                return;
            }
            List<CompletableFuture<String>> memberNames = guild.getMembers().stream()
                    .filter(member -> !member.getUuid().equals(guild.getOwnerUuid()))
                    .map(member -> playerName(member.getUuid()))
                    .toList();
            CompletableFuture<String> ownerName = playerName(guild.getOwnerUuid());
            CompletableFuture.allOf(ownerName, CompletableFuture.allOf(memberNames.toArray(CompletableFuture[]::new))).whenComplete((unused, nameFailure) ->
            {
                if (nameFailure != null)
                {
                    module.getLogger().error("Failed to look up guild member names", nameFailure);
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                List<String> names = memberNames.stream().map(CompletableFuture::join).toList();
                player.sendMessage(mmString("<gradient:yellow:gold>====<aqua>" + guild.getName() + "<gradient:yellow:gold>===="));
                player.sendMessage(mmString(""));
                player.sendMessage(mmString("<gold>Owner: <yellow>" + ownerName.join()));
                player.sendMessage(mmString("<gold>Members (" + names.size() + "): " + StringUtils.join(names, ", ")));
                player.sendMessage(mmString("<gold>Prefix: " + (guild.getPrefix() == null ? "N/A" : guild.getPrefix())));
                player.sendMessage(mmString("<gold>Created At: " + formatter.format(guild.getCreatedAt())));
            });
        });
        return null;
    }

    private CompletableFuture<Guild> resolveGuild(Player sender, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return CompletableFuture.completedFuture(module.getGuildHolder().guild(sender.getUniqueId()).orElse(null));
        }
        return module.api().players().byName(first).thenApply(result ->
        {
            if (result.isPresent())
            {
                Guild guild = module.getGuildHolder().guild(result.get().uuid()).orElse(null);
                if (guild != null)
                {
                    return guild;
                }
            }
            return module.getGuildHolder().guildByName(arguments(first, remaining)).orElse(null);
        });
    }

    private CompletableFuture<String> playerName(java.util.UUID uuid)
    {
        return module.api().players().player(uuid).thenApply(player -> player.map(PlexPlayerView::name).orElse(uuid.toString()));
    }


}
