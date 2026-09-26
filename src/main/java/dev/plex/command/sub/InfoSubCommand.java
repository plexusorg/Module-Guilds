package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InfoSubCommand extends GuildSubCommand
{
    public InfoSubCommand(Guilds module)
    {
        super(module, command("info").description("Show guild information").usage("/guild <command> [guild name or UUID]")
                .permission("plex.guilds.info").build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        Guild guild = guildOf(player);
        if (first != null)
        {
            String target = arguments(first, remaining);
            guild = module.getGuildHolder().guildByName(target).orElse(null);
            if (guild == null)
            {
                try
                {
                    guild = module.getGuildHolder().guildById(UUID.fromString(target)).orElse(null);
                }
                catch (IllegalArgumentException ignored)
                {
                    return messageComponent("guildInfoNotFound");
                }
            }
        }
        if (guild == null)
        {
            return first == null && player == null ? usage() : messageComponent(first == null ? "guildNotFound" : "guildInfoNotFound");
        }
        Guild selected = guild;
        module.api().players().player(selected.getOwnerUuid()).whenComplete((owner, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to look up guild owner {}", selected.getOwnerUuid(), failure);
            }
            String ownerName = failure == null ? owner.map(view -> view.name()).orElse(selected.getOwnerUuid().toString())
                    : selected.getOwnerUuid().toString();
            sender.sendMessage(messageComponent("guildInfo",
                    Placeholder.unparsed("guild", selected.getName()), Placeholder.unparsed("id", selected.getGuildUuid().toString()),
                    Placeholder.unparsed("owner", ownerName), Placeholder.unparsed("members", Integer.toString(selected.getMembers().size())),
                    Placeholder.unparsed("created", selected.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)),
                    Placeholder.component("prefix", selected.getPrefixComponent())));
        });
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? module.getGuildHolder().guilds().stream().map(Guild::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of();
    }
}

