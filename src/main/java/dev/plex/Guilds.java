package dev.plex;

import org.bukkit.Bukkit;

import dev.plex.command.GuildCommand;
import dev.plex.api.config.ModuleConfiguration;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.guild.GuildMutationService;
import dev.plex.handler.ChatHandlerImpl;
import dev.plex.handler.GuildSessionListener;
import dev.plex.guild.GuildRecoveryService;
import dev.plex.guild.HistoryPurgeQueue;
import dev.plex.guild.PendingGuild;
import dev.plex.handler.GuildMenuListener;
import dev.plex.handler.GuildPrefixListener;
import dev.plex.handler.GuildWorldAccessListener;
import dev.plex.handler.GuildWorldProtectionListener;
import dev.plex.handler.GuildWorldEntityProtectionListener;
import dev.plex.hook.GuildOasisHook;
import dev.plex.hook.GuildWorldEditHook;
import dev.plex.module.PlexModule;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.storage.GuildRepository;
import dev.plex.storage.JdbiGuildRepository;
import dev.plex.util.DurationParser;
import dev.plex.world.GuildWorldIndex;
import dev.plex.world.GuildWorldService;
import dev.plex.world.AspGuildWorldService;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public class Guilds extends PlexModule
{
    private static final String ASP_API_CLASS = "com.infernalsuite.asp.api.AdvancedSlimePaperAPI";
    private static final TextReplacementConfig CHAT_LINKS = TextReplacementConfig.builder()
            .match("https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
            .replacement((match, builder) -> builder.clickEvent(ClickEvent.openUrl(match.group())))
            .build();

    private final GuildHolder guildHolder = new GuildHolder();
    private final GuildMutationService guildMutationService = new GuildMutationService(this);
    private final GuildMenuListener guildMenuListener = new GuildMenuListener(this, guildMutationService);
    private final GuildWorldProtectionListener guildWorldProtectionListener = new GuildWorldProtectionListener(this);
    private final GuildWorldAccessListener guildWorldAccessListener = new GuildWorldAccessListener(this);

    private GuildWorldService guildWorldService;
    private Runnable unregisterWorldEdit;
    private GuildOasisHook oasis;

    private GuildRepository guildRepository;
    private final GuildSessionListener guildSessionListener = new GuildSessionListener(this);
    private final GuildRecoveryService guildRecoveryService = new GuildRecoveryService(this);
    private final HistoryPurgeQueue historyPurgeQueue = new HistoryPurgeQueue(this);
    private final GuildWorldIndex guildWorldIndex = new GuildWorldIndex(this);
    private ExecutorService executor;

    private ModuleConfiguration config;
    private ZoneId zoneId;
    private Duration guestDefaultDuration;
    private Duration guestMaxDuration;
    private int inviteDailyLimit;
    private volatile boolean ready;
    private volatile boolean loadFailed;
    // Completes when the guilds are in the holder, the moment ready turns true, or fails when they cannot load.
    private volatile CompletableFuture<Void> guildsLoaded = new CompletableFuture<>();

    @Override
    public void load()
    {
        config = api().moduleConfigs().create(this, "config.yml");
        config.load();
        if (!config.isInt("guilds.invites.daily-limit") || config.getInt("guilds.invites.daily-limit") <= 0)
        {
            throw new IllegalArgumentException("guilds.invites.daily-limit must be a positive whole number");
        }
        inviteDailyLimit = config.getInt("guilds.invites.daily-limit");
        zoneId = ZoneId.of(api().configuration().mainConfig().getString("server.timezone", "Etc/UTC"));
        guestDefaultDuration = DurationParser.parse(config.getString("guilds.guests.default-duration", "24h"));
        guestMaxDuration = DurationParser.parse(config.getString("guilds.guests.max-duration", "30d"));
        if (guestDefaultDuration == null || guestMaxDuration == null || guestDefaultDuration.compareTo(guestMaxDuration) > 0)
        {
            throw new IllegalArgumentException("Guest durations must use <number>m, <number>h, or <number>d, and the default must not exceed the maximum");
        }
        if (getRetentionDays() < 0)
        {
            throw new IllegalArgumentException("Guild recovery retention must not be negative");
        }
        loadMessages("messages.yml");
        this.registerCommand(new GuildCommand(this));
    }

    @Override
    public void enable()
    {
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Plex-Guilds-", 0).factory());
        ready = false;
        loadFailed = false;
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        guildsLoaded = loaded;
        enableGuildWorlds();
        GuildOasisHook hook = oasis();
        if (hook != null && guildWorldService != null)
        {
            hook.loadGuildWorlds(guildHolder, guildWorldService, loaded);
        }
        ModuleStorage storage = api().storage().forModule(this);
        try
        {
            storage.migrations().run();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to run Guilds migrations", e);
        }
        guildRepository = new JdbiGuildRepository(storage, executor, zoneId, api().messages()::playerText);
        guildSessionListener.start().thenCompose(unused -> guildRepository.loadGuilds()).whenComplete((guilds, throwable) ->
        {
            if (throwable != null)
            {
                loadFailed = true;
                getLogger().error("Failed to load guilds", throwable);
                loaded.completeExceptionally(throwable);
                return;
            }
            api().logging().debug("Finished loading {0} guilds and {1} pending guilds", guilds.active().size(), guilds.pending().size());
            guildHolder.replaceAll(guilds.active(), guilds.pending());
            guildWorldIndex.seed(Stream.concat(guilds.active().stream(), guilds.pending().stream().map(PendingGuild::guild)).toList());
            if (guildWorldService != null)
            {
                guilds.pending().forEach(pending -> guildWorldService.closeWorld(pending.guild()).exceptionally(failure ->
                {
                    getLogger().error("Failed to close the world of disbanded guild {}", pending.guild().getGuildUuid(), failure);
                    return null;
                }));
            }
            ready = true;
            loaded.complete(null);
            guildSessionListener.purge();
        });
        registerListener(guildSessionListener);
        registerListener(new ChatHandlerImpl(this));
        registerListener(new GuildPrefixListener(this));
        registerListener(guildMenuListener);
        registerListener(guildWorldProtectionListener);
        registerListener(new GuildWorldEntityProtectionListener(this));
        registerListener(guildWorldAccessListener);
        guildWorldAccessListener.startGuestExpiry();
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"))
        {
            unregisterWorldEdit = new GuildWorldEditHook(this).register();
        }
        else
        {
            getLogger().info("FastAsyncWorldEdit is disabled; skip guild WorldEdit protection.");
        }
    }

    @Override
    public void disable()
    {
        ready = false;
        guildsLoaded.completeExceptionally(new IllegalStateException("Guilds stopped before its guilds loaded"));
        if (unregisterWorldEdit != null)
        {
            unregisterWorldEdit.run();
            unregisterWorldEdit = null;
        }
        if (oasis != null)
        {
            oasis.stopLoadingGuildWorlds();
            oasis = null;
        }
        if (guildWorldService != null)
        {
            guildWorldService.disable();
        }
        if (guildRepository != null && executor != null)
        {
            guildSessionListener.close().exceptionally(failure ->
            {
                getLogger().error("Failed to close guild sessions at shutdown", failure);
                return null;
            }).join();
        }
        guildHolder.clear();
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
    }

    public int getRetentionDays()
    {
        return config.getInt("guilds.recovery.retention-days", 7);
    }

    public long getRecoveryCutoff()
    {
        return System.currentTimeMillis() - Duration.ofDays(getRetentionDays()).toMillis();
    }

    public ExecutorService executor()
    {
        if (executor == null) throw new IllegalStateException("Guilds is not enabled");
        return executor;
    }

    /** The Oasis hook, or null while Oasis is not enabled. */
    public synchronized @Nullable GuildOasisHook oasis()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oasis"))
        {
            return null;
        }
        if (oasis == null)
        {
            oasis = new GuildOasisHook(plugin());
        }
        return oasis;
    }

    public boolean isGuildWorldsEnabled()
    {
        return guildWorldService != null;
    }

    private void enableGuildWorlds()
    {
        try
        {
            ClassLoader classLoader = Guilds.class.getClassLoader();
            Class.forName(ASP_API_CLASS, false, classLoader);
            Class.forName("com.infernalsuite.asp.api.world.SlimeVanillaWorldProfile", false, classLoader);
        }
        catch (ClassNotFoundException exception)
        {
            getLogger().warn("Scissors-ASP with vanilla world profiles is required; guild worlds are disabled.");
            return;
        }
        guildWorldService = new AspGuildWorldService(this);
        guildWorldService.enable();
    }

    public void sendChat(Guild guild, String senderName, Component message)
    {
        Component content = message.replaceText(CHAT_LINKS);
        broadcastToGuild(guild, messageComponent("guildChatMessage",
                Placeholder.unparsed("player", senderName), Placeholder.component("content", content)),
                messageComponent("guildChatSpyMessage",
                        Placeholder.unparsed("guild", guild.getName()),
                        Placeholder.unparsed("player", senderName), Placeholder.component("content", content)));
        if (config.getBoolean("guilds.log-chat-message"))
        {
            Bukkit.getConsoleSender().sendMessage(messageComponent("guildChatConsoleLog",
                    Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.unparsed("guild_id", guild.getGuildUuid().toString()),
                    Placeholder.unparsed("player", senderName), Placeholder.component("content", content)));
        }
    }

    public CompletableFuture<Void> broadcastToGuild(Guild guild, Component message)
    {
        return broadcastToGuild(guild, message, null);
    }

    private CompletableFuture<Void> broadcastToGuild(Guild guild, Component message, Component spyMessage)
    {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Set<UUID> recipients = guild.getMembers().stream().map(member -> member.getUuid()).collect(Collectors.toSet());
        ownTask(Bukkit.getGlobalRegionScheduler().run(plugin(), task ->
        {
            for (Player player : List.copyOf(Bukkit.getOnlinePlayers()))
            {
                if (recipients.contains(player.getUniqueId()))
                {
                    player.sendMessage(message);
                }
                else if (spyMessage != null && player.hasPermission("plex.guilds.chat.spy"))
                {
                    player.sendMessage(spyMessage);
                }
            }
            completion.complete(null);
        }));
        return completion;
    }

}
