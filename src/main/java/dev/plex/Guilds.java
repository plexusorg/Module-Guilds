package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.data.SQLGuildManager;
import dev.plex.data.SQLManager;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildHolder;
import dev.plex.handler.ChatHandlerImpl;
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

    private ModuleConfig config;

    @Override
    public void load()
    {
        module = this;
        config = new ModuleConfig(this, "data/config.yml", "config.yml");
        config.load();
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

        this.getPlex().setChatHandler(new ChatHandlerImpl());


        this.addDefaultMessage("guildsHelpCommand", "<gradient:gold:yellow>======</gradient>Guild Menu<gradient:gold:yellow>======</gradient><newline>\n" +
                "<newline><gold>/guild <gray>Returns this menu\n" +
                "<newline><gold>/guild help");
        this.addDefaultMessage("guildCommandNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid sub command!", "0 - The sub command");
        this.addDefaultMessage("guildNotFound", "<red>You're currently not a part of a guild!");
        this.addDefaultMessage("alreadyInGuild", "<red>You're currently in a guild. Please do <gold>/guild leave<red> if you're a member, or if you're an owner with members, <gold>/guild promote <player><red> then <gold>/guild leave<red>, or just an owner, <gold>/guild disband<red>.");
        this.addDefaultMessage("guildNotOwner", "<red>You're not the owner of this guild!");

        this.addDefaultMessage("guildPrefixSet", "<green>You have changed the guild prefix to '<gold>{0}</gold><green>'", "0 - The new prefix");
        this.addDefaultMessage("guildPrefixCleared", "<green>Your guild's prefix has been cleared.");

        this.addDefaultMessage("guildWarpAlphanumeric", "<red>Warp names may only contain alphabetical and/or numerical characters.");
        this.addDefaultMessage("guildWarpExists", "<red>'<gold>{0}</gold>'<red> is already an existing warp!", "0 - The warp name");
        this.addDefaultMessage("guildWarpNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid warp!", "0 - The warp name");
        this.addDefaultMessage("guildWarpCreated", "<green>You have created a warp called '<dark_green>{0}</dark_green><green>'", "0 - The warp name");

        this.addDefaultMessage("guildHomeRemoved", "<green>You have removed the guild's home!");
        this.addDefaultMessage("guildHomeSet", "<green>You have changed the guild's home!");
        this.addDefaultMessage("guildHomeNotFound", "<red>This guild currently has no home set.");

        this.addDefaultMessage("guildChatMessage", "<blue>[GUILD] <aqua>{0} <yellow>{1}", "0 - The player name", "1 - The message");
        this.addDefaultMessage("guildChatToggled", "<green>Your chat has been toggled {0}", "0 - On / Off");
        this.addDefaultMessage("guildChatConsoleLog", "<blue>[GUILD - {0}:{1}] <aqua>{2} <yellow>{3}", "0 - The guild name", "1 - The guild unique identifier", "2 - The player name", "3 - The message");
    }

    @Override
    public void disable()
    {
        // Unregistering listeners / commands is handled by Plex
        this.getGuildHolder().getGuilds().forEach(sqlGuildManager::updateGuild);
        this.getPlex().setChatHandler(new ChatListener.ChatHandlerImpl());
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
