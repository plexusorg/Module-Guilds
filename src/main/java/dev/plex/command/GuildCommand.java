package dev.plex.command;

import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.command.sub.AcceptSubCommand;
import dev.plex.command.sub.ChatSubCommand;
import dev.plex.command.sub.CreateSubCommand;
import dev.plex.command.sub.GuestSubCommand;
import dev.plex.command.sub.GuildSubCommand;
import dev.plex.command.sub.InviteSubCommand;
import dev.plex.command.sub.LeaveSubCommand;
import dev.plex.command.sub.PrefixSubCommand;
import dev.plex.command.sub.ResetWorldSubCommand;
import dev.plex.command.sub.VisitSubCommand;
import dev.plex.command.sub.WarpSubCommand;
import dev.plex.command.sub.WorldSubCommand;
import dev.plex.guild.Guild;
import dev.plex.storage.entity.GuildInviteEntity;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildCommand extends SimplePlexCommand
{
    private final Guilds module;
    private final List<GuildSubCommand> subCommands = Lists.newArrayList();

    public GuildCommand(Guilds module)
    {
        super(command("guild")
                .description("Guild commands and help")
                .aliases("guilds,g")
                .permission("plex.guilds.guild")
                .build());
        this.module = module;
        subCommands.add(new CreateSubCommand(module));
        subCommands.add(new AcceptSubCommand(module));
        subCommands.add(new VisitSubCommand(module));
        subCommands.add(new WorldSubCommand(module));
        subCommands.add(new WarpSubCommand(module));
        subCommands.add(new ChatSubCommand(module));
        subCommands.add(new LeaveSubCommand(module));
        subCommands.add(new InviteSubCommand(module));
        subCommands.add(new GuestSubCommand(module));
        subCommands.add(new PrefixSubCommand(module));
        subCommands.add(new ResetWorldSubCommand(module));
    }

    @Override
    protected void configureCommand(LiteralArgumentBuilder<CommandSourceStack> command)
    {
        command.executes(context -> executeCommand(context, this::executeRoot));
        var subcommand = word("subcommand")
                .suggests((context, builder) -> suggestMatching(builder, subcommandNames(context.getSource().getSender())))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), null, null)));
        var first = word("first")
                .suggests((context, builder) -> suggestArguments(context, builder, null))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), string(context, "first"), null)));
        first.then(greedyString("remaining")
                .suggests((context, builder) -> suggestArguments(context, builder, string(context, "first")))
                .executes(context -> executeCommand(context, (sender, player) ->
                        dispatch(sender, player, string(context, "subcommand"), string(context, "first"), normalize(string(context, "remaining"))))));
        subcommand.then(first);
        command.then(subcommand);
    }

    private Component executeRoot(CommandSender sender, Player player)
    {
        if (!module.isReady())
        {
            return messageComponent(module.isLoadFailed() ? "guildStorageFailed" : "guildLoading");
        }
        if (player == null)
        {
            return help(sender, null);
        }
        Guild guild = module.getGuildHolder().guild(player.getUniqueId()).orElse(null);
        if (guild != null)
        {
            module.getGuildMenuListener().open(player, guild);
            return null;
        }
        showInvites(player);
        return null;
    }

    private void showInvites(Player player)
    {
        module.getGuildRepository().invitesFor(player.getUniqueId()).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                module.getLogger().error("Failed to load invites for {}", player.getUniqueId(), throwable);
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            Component message = messageComponent("guildNoGuild");
            boolean found = false;
            long now = Instant.now().toEpochMilli();
            for (GuildInviteEntity invite : invites)
            {
                Guild guild = module.getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null);
                if (guild == null || invite.getExpiresAt() < now)
                {
                    continue;
                }
                found = true;
                message = message.append(Component.newline()).append(messageComponent("guildInviteEntry",
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.styling("join", ClickEvent.runCommand("/guild accept " + guild.getName()),
                                HoverEvent.showText(Component.text("Click to join " + guild.getName())))));
            }
            if (!found)
            {
                message = message.append(Component.newline()).append(messageComponent("guildNoInvites"));
            }
            message = message.append(Component.newline()).append(messageComponent("guildCreateHint",
                            Placeholder.unparsed("command", "/guild create <name>"))
                    .clickEvent(ClickEvent.suggestCommand("/guild create "))
                    .hoverEvent(Component.text("Click to fill in this command")));
            player.sendMessage(message);
        });
    }

    private Component dispatch(CommandSender sender, Player player, String label, String first, String remaining)
    {
        if (!module.isReady())
        {
            return messageComponent(module.isLoadFailed() ? "guildStorageFailed" : "guildLoading");
        }
        if (label.equalsIgnoreCase("help"))
        {
            return help(sender, player);
        }
        GuildSubCommand subCommand = getSubCommand(label);
        if (subCommand == null || !canRun(sender, player, subCommand))
        {
            return messageComponent("guildCommandNotFound");
        }
        return subCommand.executeSubCommand(sender, player, first, remaining);
    }

    private Component help(CommandSender sender, Player player)
    {
        Component content = messageComponent("guildHelpHeader");
        for (GuildSubCommand subCommand : subCommands)
        {
            if (!isUsable(sender, player, subCommand))
            {
                continue;
            }
            for (GuildSubCommand.HelpEntry entry : subCommand.helpEntries(player))
            {
                content = content.append(Component.newline()).append(messageComponent("guildHelpLine",
                                Placeholder.unparsed("usage", entry.usage()),
                                Placeholder.unparsed("description", entry.description()))
                        .clickEvent(ClickEvent.suggestCommand(entry.suggestion()))
                        .hoverEvent(Component.text("Click to fill in this command")));
            }
        }
        return content;
    }

    private CompletableFuture<Suggestions> suggestArguments(CommandContext<CommandSourceStack> context,
                                                            SuggestionsBuilder builder, String first)
    {
        CommandSender sender = context.getSource().getSender();
        GuildSubCommand subCommand = getSubCommand(string(context, "subcommand"));
        if (subCommand == null || !isUsable(sender, sender instanceof Player player ? player : null, subCommand))
        {
            return builder.buildFuture();
        }
        return suggestMatching(builder, subCommand.suggestSubCommand(sender, first));
    }

    private List<String> subcommandNames(CommandSender sender)
    {
        Player player = sender instanceof Player online ? online : null;
        List<String> names = Lists.newArrayList("help");
        subCommands.stream()
                .filter(command -> isUsable(sender, player, command))
                .forEach(command -> names.add(command.getName()));
        return names;
    }

    /** The sender has the source and permission for the command. */
    private boolean canRun(CommandSender sender, Player player, GuildSubCommand subCommand)
    {
        RequiredCommandSource source = subCommand.getRequiredSource();
        if (source == RequiredCommandSource.IN_GAME && player == null || source == RequiredCommandSource.CONSOLE && player != null)
        {
            return false;
        }
        return silentCheckPermission(sender, subCommand.getPermission());
    }

    /** The sender can run the command, and the command fits the player's current state. */
    private boolean isUsable(CommandSender sender, Player player, GuildSubCommand subCommand)
    {
        return canRun(sender, player, subCommand) && subCommand.isAvailable(player);
    }

    private String normalize(String value)
    {
        return value.isBlank() ? null : String.join(" ", value.trim().split("\\s+"));
    }

    private GuildSubCommand getSubCommand(String label)
    {
        return subCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(label))
                .findFirst().orElse(null);
    }
}
