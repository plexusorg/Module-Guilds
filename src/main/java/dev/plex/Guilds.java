package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.data.SQLGuildManager;
import dev.plex.data.SQLManager;
import dev.plex.guild.GuildHolder;
import dev.plex.module.PlexModule;
import lombok.Getter;

@Getter
public class Guilds extends PlexModule
{
    private static Guilds module;
    private final GuildHolder guildHolder = new GuildHolder();

    private SQLGuildManager sqlGuildManager;

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
        SQLManager.makeTables();
        sqlGuildManager = new SQLGuildManager();
        sqlGuildManager.getGuilds().whenComplete((guilds, throwable) ->
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
            guilds.forEach(guildHolder::addGuild);
        });
    }

    @Override
    public void disable()
    {
        // Unregistering listeners / commands is handled by Plex
        if (sqlGuildManager != null)
        {
            this.getGuildHolder().getGuilds().forEach(sqlGuildManager::updateGuild);
        }
    }

    public static Guilds get()
    {
        return module;
    }
}
