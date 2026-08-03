package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
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
    private static Guilds module;
    private final GuildHolder guildHolder = new GuildHolder();
    private final GuildMenuListener guildMenuListener = new GuildMenuListener();
    private final GuildWorldProtectionListener guildWorldProtectionListener = new GuildWorldProtectionListener();
    private final RankPermissionMenuListener rankPermissionMenuListener = new RankPermissionMenuListener();

    private GuildWorldService guildWorldService;

    private GuildRepository guildRepository;

    private ModuleConfig config;

    @Override
    public void load()
    {
        module = this;
        config = new ModuleConfig(this, "config.yml", "config.yml");
        config.load();
        loadMessages("messages.yml");
        this.registerCommand(new GuildCommand());
    }

    @Override
    public void enable()
    {
        if (slimeWorldsAvailable())
        {
            guildWorldService = new GuildWorldService();
            guildWorldService.enable();
        }
        else
        {
            getLogger().warn("Advanced Slime Paper (ASP/ASWM) was not found; guild worlds are disabled.");
        }
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

    private static boolean slimeWorldsAvailable()
    {
        try
        {
            Class.forName("com.infernalsuite.asp.api.AdvancedSlimePaperAPI", false, Guilds.class.getClassLoader());
            return true;
        }
        catch (Throwable throwable)
        {
            return false;
        }
    }

    public static Guilds get()
    {
        return module;
    }
}
