package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.util.CustomLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WarpSubCommand extends GuildSubCommand
{
    private static final Pattern WARP_NAME = Pattern.compile("[A-Za-z0-9]{1,16}");
    private static final String SET = "set";
    private static final String DELETE = "delete";

    public WarpSubCommand(Guilds module)
    {
        super(module, command("warp")
                .description("List your guild warps or go to one")
                .usage("/guild <command> [name]")
                .permission("plex.guilds.warp")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return guildOf(player) != null;
    }

    @Override
    public List<HelpEntry> helpEntries(@Nullable Player player)
    {
        List<HelpEntry> entries = new ArrayList<>(super.helpEntries(player));
        Guild guild = guildOf(player);
        if (guild != null && guild.canManage(player.getUniqueId()))
        {
            entries.add(new HelpEntry("/guild warp set <name>", "Create or move a warp where you stand", "/guild warp set "));
            entries.add(new HelpEntry("/guild warp delete <name>", "Delete a warp", "/guild warp delete "));
        }
        return entries;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (first == null)
        {
            return list(guild);
        }
        if (first.equalsIgnoreCase(SET) || first.equalsIgnoreCase(DELETE))
        {
            return change(player, guild, first.equalsIgnoreCase(SET), remaining);
        }
        if (remaining != null)
        {
            return usage();
        }
        return teleport(player, guild, first);
    }

    private Component list(Guild guild)
    {
        if (guild.getWarps().isEmpty())
        {
            return messageComponent("guildWarpsEmpty");
        }
        List<Component> entries = guild.getWarps().keySet().stream().sorted()
                .map(name -> messageComponent("guildWarpEntry", Placeholder.unparsed("warp", name))
                        .clickEvent(ClickEvent.runCommand("/guild warp " + name))
                        .hoverEvent(Component.text("Click to go to " + name)))
                .toList();
        return messageComponent("guildWarpList",
                Placeholder.component("warps", Component.join(JoinConfiguration.commas(true), entries)));
    }

    private Component teleport(Player player, Guild guild, String name)
    {
        CustomLocation warp = guild.getWarps().get(name.toLowerCase(Locale.ROOT));
        if (warp == null)
        {
            return messageComponent("guildWarpNotFound", Placeholder.unparsed("warp", name));
        }
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        teleportInGuildWorld(player, guild, world -> new Location(world, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch()));
        return null;
    }

    private Component change(Player player, Guild guild, boolean set, @Nullable String name)
    {
        if (name == null || name.contains(" "))
        {
            return messageComponent("correctUsagePrefix").append(Component.text(set ? "/guild warp set <name>" : "/guild warp delete <name>", NamedTextColor.GRAY));
        }
        if (!guild.canManage(player.getUniqueId()))
        {
            return messageComponent("guildNotManager");
        }
        if (!set)
        {
            delete(player, guild, name);
            return null;
        }
        if (!WARP_NAME.matcher(name).matches() || name.equalsIgnoreCase(SET) || name.equalsIgnoreCase(DELETE))
        {
            return messageComponent("guildWarpNameInvalid");
        }
        if (!player.getWorld().getName().equals(guild.getWorldName()))
        {
            return messageComponent("guildWarpWrongWorld");
        }
        String key = name.toLowerCase(Locale.ROOT);
        module.getGuildMutationService().upsertWarp(guild, player.getUniqueId(), key, CustomLocation.fromLocation(player.getLocation()))
                .whenComplete((unused, throwable) -> player.sendMessage(throwable == null
                        ? messageComponent("guildWarpSet", Placeholder.unparsed("warp", key))
                        : failureMessage(throwable, "guildWarpWrongWorld")));
        return null;
    }

    private void delete(Player player, Guild guild, String name)
    {
        String key = name.toLowerCase(Locale.ROOT);
        module.getGuildMutationService().deleteWarp(guild, player.getUniqueId(), key)
                .whenComplete((unused, throwable) -> player.sendMessage(throwable == null
                        ? messageComponent("guildWarpDeleted", Placeholder.unparsed("warp", key))
                        : failureMessage(throwable, "guildWarpNotFound", Placeholder.unparsed("warp", name))));
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        Guild guild = sender instanceof Player player ? guildOf(player) : null;
        if (guild == null)
        {
            return List.of();
        }
        boolean manager = guild.canManage(((Player) sender).getUniqueId());
        if (first == null)
        {
            List<String> options = new ArrayList<>(guild.getWarps().keySet());
            if (manager)
            {
                options.add(SET);
                options.add(DELETE);
            }
            return options;
        }
        return manager && first.equalsIgnoreCase(DELETE) ? List.copyOf(guild.getWarps().keySet()) : List.of();
    }
}
