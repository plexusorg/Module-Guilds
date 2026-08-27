package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.api.config.ModuleConfiguration;
import dev.plex.guild.GuildHolder;
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

import java.sql.SQLException;
import java.util.List;

@Getter
public class Guilds extends PlexModule
{
    private static final String ASP_API_CLASS = "com.infernalsuite.asp.api.AdvancedSlimePaperAPI";
    private static final String ASP_LOADER_CLASS = "com.infernalsuite.asp.api.loaders.SlimeLoader";
    private static final String ASP_WORLD_SERVICE_CLASS = "dev.plex.world.AspGuildWorldService";

    private static Guilds module;
    private final GuildHolder guildHolder = new GuildHolder();
    private final GuildMenuListener guildMenuListener = new GuildMenuListener();
    private final GuildWorldProtectionListener guildWorldProtectionListener = new GuildWorldProtectionListener();
    private final RankPermissionMenuListener rankPermissionMenuListener = new RankPermissionMenuListener();

    private GuildWorldService guildWorldService;

    private GuildRepository guildRepository;

    private ModuleConfiguration config;

    @Override
    public void load()
    {
        module = this;
        config = api().moduleConfigs().create(this, "config.yml");
        config.load();
        loadMessages("messages.yml");
        this.registerCommand(new GuildCommand());
    }

    @Override
    public void enable()
    {
        enableGuildWorlds();
        ModuleStorage storage = api().storage().forModule(this);
        try
        {
            storage.migrations().run(List.of("001_initial_schema", "002_guild_world_permissions"));
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to run Guilds migrations", e);
        }
        guildRepository = new JdbiGuildRepository(storage);
        guildRepository.loadGuilds().whenComplete((guilds, throwable) ->
        {
            if (throwable != null)
            {
                getLogger().error("Failed to load guilds", throwable);
                return;
            }
            if (guilds == null)
            {
                getLogger().error("Failed to load guilds");
                return;
            }
            api().logging().debug("Finished loading {0} guilds", guilds.size());
            guildHolder.replaceAll(guilds);
        });
        registerListener(new ChatHandlerImpl());
        registerListener(guildMenuListener);
        registerListener(guildWorldProtectionListener);
        registerListener(rankPermissionMenuListener);
    }

    @Override
    public void disable()
    {
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
            GuildWorldService service = serviceClass.getConstructor().newInstance();
            service.enable();
            guildWorldService = service;
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException throwable)
        {
            getLogger().warn("Advanced Slime Paper (ASP/ASWM) is unavailable or incompatible; guild worlds are disabled, but all other guild features will remain enabled.");
        }
    }

    public static Guilds get()
    {
        return module;
    }
}
