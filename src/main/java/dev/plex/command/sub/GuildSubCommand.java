package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.CommandSpec;
import dev.plex.command.source.RequiredCommandSource;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GuildSubCommand
{
    protected final Guilds module;
    private final CommandSpec commandSpec;

    protected GuildSubCommand(Guilds module, CommandSpec commandSpec)
    {
        this.module = module;
        this.commandSpec = commandSpec;
    }

    protected static CommandSpec.Builder command(String name)
    {
        return CommandSpec.builder(name);
    }

    public String getName()
    {
        return commandSpec.name();
    }

    public String getDescription()
    {
        return commandSpec.description();
    }

    public String getUsage()
    {
        return commandSpec.resolvedUsage();
    }

    public String getPermission()
    {
        return commandSpec.permission();
    }

    public RequiredCommandSource getRequiredSource()
    {
        return commandSpec.requiredSource();
    }

    public List<String> getAliases()
    {
        return commandSpec.aliases();
    }

    public abstract Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                                @Nullable String first, @Nullable String remaining);

    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return List.of();
    }

    protected Component messageComponent(String key, TagResolver... placeholders)
    {
        return module.messageComponent(key, placeholders);
    }

    protected Component usage()
    {
        return messageComponent("correctUsagePrefix")
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(getUsage()).colorIfAbsent(NamedTextColor.GRAY));
    }

    protected Component mmString(String value)
    {
        return module.api().messages().miniMessage(value);
    }

    protected String arguments(@NotNull String first, @Nullable String remaining)
    {
        return remaining == null ? first : first + " " + remaining;
    }
}
