package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.guild.GuildHolder;
import dev.plex.handler.ChatHandlerImpl;
import dev.plex.module.PlexModule;
import dev.plex.api.storage.ModuleStorage;
import dev.plex.storage.GuildRepository;
import dev.plex.storage.OrmGuildRepository;
import lombok.Getter;

import java.sql.SQLException;
import java.util.List;

@Getter
public class Guilds extends PlexModule
{
    private static Guilds module;
    private final GuildHolder guildHolder = new GuildHolder();

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
        ModuleStorage storage = api().storage().forModule(this);
        try
        {
            storage.migrations().run(List.of("001_initial_schema"));
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to run Guilds migrations", e);
        }
        guildRepository = new OrmGuildRepository(storage);
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
    }

    @Override
    public void disable()
    {
        guildHolder.clear();
    }

    public static Guilds get()
    {
        return module;
    }
}
