package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.data.SQLGuildManager;
import dev.plex.data.SQLManager;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.listener.impl.ChatListener;
import dev.plex.module.PlexModule;
import dev.plex.storage.StorageType;
import dev.plex.util.PlexLog;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;

import java.util.Arrays;


//TODO: Implement mongodb
@Getter
public class Guilds extends PlexModule
{
    private static Guilds module;
    private final GuildHolder guildHolder = new GuildHolder();

    private SQLGuildManager sqlGuildManager;

    @Override
    public void load()
    {
        module = this;
    }

    @Override
    public void enable()
    {
        if (getPlex().getStorageType() == StorageType.MONGODB)
        {
            getPlex().getMongoConnection().getDatastore().getMapper().map(Guild.class);
            getPlex().getMongoConnection().getDatastore().ensureIndexes();
        } else {
            SQLManager.makeTables();
            sqlGuildManager = new SQLGuildManager();
            sqlGuildManager.getGuilds().whenComplete((guilds, throwable) -> {
               PlexLog.debug("Finished loading {0} guilds", guilds.size());
               guilds.forEach(guildHolder::addGuild);
               this.registerCommand(new GuildCommand());
            });
        }


        this.addDefaultMessage("guildsHelpCommand", "<gradient:gold:yellow>======</gradient>Guild Menu<gradient:gold:yellow>======</gradient><newline>\n" +
                "<newline><gold>/guild <gray>Returns this menu\n" +
                "<newline><gold>/guild help");
        this.addDefaultMessage("guildCommandNotFound", "<red>'{0}'<gold> is not a valid sub command!", "{0} - The sub command");
        this.addDefaultMessage("guildNotFound", "<red>You're currently not a part of a guild!");
        this.addDefaultMessage("alreadyInGuild", "<red>You're currently in a guild. Please do <gold>/guild leave<red> if you're a member, or if you're an owner with members, <gold>/guild promote <player><red> then <gold>/guild leave<red>, or just an owner, <gold>/guild disband<red>.");
    }

    @Override
    public void disable()
    {
        // Unregistering listeners / commands is handled by Plex
    }

    private void addDefaultMessage(String message, Object initValue)
    {
        if (Plex.get().messages.getString(message) == null)
        {
            Plex.get().messages.set(message, initValue);
            Plex.get().messages.save();
            PlexLog.debug("'{0}' message added from TFMExtras module", message);
        }
    }

    private void addDefaultMessage(String message, Object initValue, String... comments)
    {
        if (Plex.get().messages.getString(message) == null)
        {
            Plex.get().messages.set(message, initValue);
            Plex.get().messages.save();
            Plex.get().messages.setComments(message, Arrays.asList(comments));
            Plex.get().messages.save();
            PlexLog.debug("'{0}' message added from Plex-Guilds module", message);
        }
    }

    public static Guilds get()
    {
        return module;
    }
}
