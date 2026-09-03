package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.api.config.ModuleConfiguration;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.guild.GuildMutationService;
import dev.plex.handler.ChatHandlerImpl;
import dev.plex.handler.GuildMenuListener;
import dev.plex.handler.GuildWorldProtectionListener;
import dev.plex.handler.RankPermissionMenuListener;
import dev.plex.module.PlexModule;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.storage.GuildRepository;
import dev.plex.storage.JdbiGuildRepository;
import dev.plex.world.GuildWorldService;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class Guilds extends PlexModule
{
    private static final String ASP_API_CLASS = "com.infernalsuite.asp.api.AdvancedSlimePaperAPI";
    private static final String ASP_LOADER_CLASS = "com.infernalsuite.asp.api.loaders.SlimeLoader";
    private static final String ASP_WORLD_SERVICE_CLASS = "dev.plex.world.AspGuildWorldService";

    private final GuildHolder guildHolder = new GuildHolder();
    private final GuildMutationService guildMutationService = new GuildMutationService(this);
    private final GuildMenuListener guildMenuListener = new GuildMenuListener(this, guildMutationService);
    private final GuildWorldProtectionListener guildWorldProtectionListener = new GuildWorldProtectionListener(this);
    private final RankPermissionMenuListener rankPermissionMenuListener = new RankPermissionMenuListener(this);

    private GuildWorldService guildWorldService;

    private GuildRepository guildRepository;

    private ModuleConfiguration config;
    private ZoneId zoneId;
    private volatile boolean ready;
    private volatile boolean loadFailed;

    @Override
    public void load()
    {
        config = api().moduleConfigs().create(this, "config.yml");
        config.load();
        zoneId = ZoneId.of(api().configuration().mainConfig().getString("server.timezone", "Etc/UTC"));
        loadMessages("messages.yml");
        this.registerCommand(new GuildCommand(this));
    }

    @Override
    public void enable()
    {
        ready = false;
        loadFailed = false;
        enableGuildWorlds();
        ModuleStorage storage = api().storage().forModule(this);
        try
        {
            storage.migrations().run();
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to run Guilds migrations", e);
        }
        guildRepository = new JdbiGuildRepository(storage, scheduler().asyncExecutor(), zoneId);
        guildRepository.loadGuilds().whenComplete((guilds, throwable) ->
        {
            if (throwable != null)
            {
                loadFailed = true;
                getLogger().error("Failed to load guilds", throwable);
                return;
            }
            api().logging().debug("Finished loading {0} guilds", guilds.size());
            guildHolder.replaceAll(guilds);
            ready = true;
        });
        registerListener(new ChatHandlerImpl(this));
        registerListener(guildMenuListener);
        registerListener(guildWorldProtectionListener);
        registerListener(rankPermissionMenuListener);
    }

    @Override
    public void disable()
    {
        ready = false;
        if (guildWorldService != null)
        {
            guildWorldService.disable();
        }
        guildHolder.clear();
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
            Class.forName(ASP_LOADER_CLASS, false, classLoader);

            Class<? extends GuildWorldService> serviceClass = Class.forName(ASP_WORLD_SERVICE_CLASS, true, classLoader)
                    .asSubclass(GuildWorldService.class);
            GuildWorldService service = serviceClass.getConstructor(Guilds.class).newInstance(this);
            service.enable();
            guildWorldService = service;
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException throwable)
        {
            getLogger().warn("Advanced Slime Paper (ASP/ASWM) is unavailable or incompatible; guild worlds are disabled, but all other guild features will remain enabled.");
        }
    }

    public java.util.concurrent.CompletableFuture<Void> broadcastToGuild(Guild guild, Component message)
    {
        java.util.concurrent.CompletableFuture<Void> completion = new java.util.concurrent.CompletableFuture<>();
        Set<java.util.UUID> recipients = guild.getMembers().stream().map(member -> member.getUuid()).collect(Collectors.toSet());
        scheduler().runGlobal(() ->
        {
            for (Player player : java.util.List.copyOf(Bukkit.getOnlinePlayers()))
            {
                if (recipients.contains(player.getUniqueId())) player.sendMessage(message);
            }
            completion.complete(null);
        });
        return completion;
    }

}
