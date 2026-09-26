package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateSubCommand extends GuildSubCommand
{
    public CreateSubCommand(Guilds module)
    {
        super(module, command("create")
                .description("Create a guild")
                .usage("/guild <command> <name>")
                .permission("plex.guilds.create")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return player != null && guildOf(player) == null;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        if (guildOf(player) != null)
        {
            return messageComponent("alreadyInGuild");
        }
        String name = PlainTextComponentSerializer.plainText().serialize(module.api().messages().playerText(arguments(first, remaining)));
        if (name.isBlank())
        {
            return usage();
        }
        if (module.getGuildHolder().guildByName(name).isPresent())
        {
            return messageComponent("guildNameTaken", Placeholder.unparsed("guild", name));
        }
        Guild guildToCreate = Guild.create(player.getUniqueId(), name, module.getZoneId());
        module.getGuildRepository().createGuild(guildToCreate)
                .whenComplete((guild, throwable) ->
        {
            if (throwable != null)
            {
                module.getLogger().error("Failed to create guild {}", name, throwable);
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            module.getGuildHolder().addGuild(guild);
            player.sendMessage(messageComponent("guildCreated", Placeholder.unparsed("guild", guild.getName())));
        });
        return null;
    }
}
