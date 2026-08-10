package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.dialog.GuildDialogService;
import dev.plex.guild.GuildHolder;
import dev.plex.handler.ChatHandlerImpl;
import dev.plex.handler.GuildWorldProtectionListener;
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
    private final GuildDialogService guildDialogService = new GuildDialogService();
    private final GuildWorldProtectionListener guildWorldProtectionListener = new GuildWorldProtectionListener();
    private final GuildWorldService guildWorldService = new GuildWorldService();

    private GuildRepository guildRepository;

    private ModuleConfig config;

    @Override
    public void load()
    {
        module = this;
        config = new ModuleConfig(this, "guilds/config.yml", "config.yml");
        config.load();
        loadMessages("guilds/messages.yml");
        this.registerCommand(new GuildCommand());
    }

    @Override
    public void enable()
    {
        guildWorldService.enable();
        ModuleStorage storage = api().storage().forModule(this);
        try
        {
            storage.migrations().run(List.of("001_initial_schema", "002_guild_world_permissions", "003_role_permissions"));
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
        registerListener(guildWorldProtectionListener);
    }

    @Override
    public void disable()
    {
        guildWorldService.disable();
        guildHolder.clear();
    }

    public static Guilds get()
    {
        return module;
    }
}
