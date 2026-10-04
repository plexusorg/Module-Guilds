package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InfoSubCommand extends GuildSubCommand
{
    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    public InfoSubCommand(Guilds module)
    {
        super(module, command("info").description("Show guild information").usage("/guild <command> [guild, world, or player]")
                .permission("plex.guilds.info").build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            Guild guild = guildOf(player);
            if (guild == null)
            {
                return player == null ? usage() : messageComponent("guildNotFound");
            }
            show(sender, guild);
            return null;
        }
        String target = arguments(first, remaining);
        Guild guild = module.getGuildHolder().guildByName(target)
                .or(() -> module.getGuildHolder().guildByWorld(target))
                .or(() -> guildById(target))
                .orElse(null);
        if (guild != null)
        {
            show(sender, guild);
            return null;
        }
        module.api().players().byName(target).whenComplete((view, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to look up player {}", target, failure);
            }
            Guild memberGuild = view == null ? null : view.flatMap(found -> module.getGuildHolder().guild(found.uuid())).orElse(null);
            if (memberGuild == null)
            {
                sender.sendMessage(messageComponent("guildInfoNotFound"));
                return;
            }
            show(sender, memberGuild);
        });
        return null;
    }

    private Optional<Guild> guildById(String target)
    {
        try
        {
            return module.getGuildHolder().guildById(UUID.fromString(target));
        }
        catch (IllegalArgumentException ignored)
        {
            return Optional.empty();
        }
    }

    private void show(CommandSender sender, Guild selected)
    {
        List<Member> members = List.copyOf(selected.getMembers());
        List<CompletableFuture<String>> names = members.stream().map(member -> name(member.getUuid())).toList();
        CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).thenRun(() ->
        {
            Set<String> online = new HashSet<>();
            module.api().players().onlineNames().forEach(name -> online.add(name.toLowerCase(Locale.ROOT)));
            Map<GuildRole, List<String>> byRole = new EnumMap<>(GuildRole.class);
            for (int i = 0; i < members.size(); i++)
            {
                byRole.computeIfAbsent(members.get(i).getRole(), role -> new ArrayList<>()).add(names.get(i).join());
            }
            sender.sendMessage(messageComponent("guildInfo",
                    Placeholder.unparsed("guild", selected.getName()),
                    Placeholder.component("prefix", selected.getPrefix() == null || selected.getPrefix().isBlank()
                            ? messageComponent("guildInfoNone") : selected.getPrefixComponent()),
                    Placeholder.unparsed("created", selected.getCreatedAt().format(CREATED_FORMAT)),
                    Placeholder.component("owner", nameList(byRole.get(GuildRole.OWNER), online)),
                    Placeholder.component("officers", nameList(byRole.get(GuildRole.OFFICER), online)),
                    Placeholder.component("members", nameList(byRole.get(GuildRole.MEMBER), online))));
        });
    }

    private CompletableFuture<String> name(UUID uuid)
    {
        return module.api().players().player(uuid)
                .thenApply(player -> player.map(PlexPlayerView::name).orElse(uuid.toString()))
                .exceptionally(failure ->
                {
                    module.getLogger().error("Failed to look up the name of {}", uuid, failure);
                    return uuid.toString();
                });
    }

    /** Online names show in green, offline names in gray. */
    private Component nameList(@Nullable List<String> names, Set<String> online)
    {
        if (names == null)
        {
            return messageComponent("guildInfoNone");
        }
        return Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.DARK_GRAY)), names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> messageComponent(online.contains(name.toLowerCase(Locale.ROOT)) ? "guildInfoOnline" : "guildInfoOffline",
                        Placeholder.unparsed("player", name)))
                .toList());
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? module.getGuildHolder().guilds().stream().map(Guild::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of();
    }
}

