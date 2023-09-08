package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.data.SQLGuildManager;
import dev.plex.data.SQLManager;
import dev.plex.guild.GuildHolder;
import dev.plex.module.PlexModule;
import dev.plex.util.PlexLog;
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
    }

    @Override
    public void enable()
    {
        SQLManager.makeTables();
        sqlGuildManager = new SQLGuildManager();
        sqlGuildManager.getGuilds().whenComplete((guilds, throwable) ->
        {
            PlexLog.debug("Finished loading {0} guilds", guilds.size());
            guilds.forEach(guildHolder::addGuild);
            this.registerCommand(new GuildCommand());
        });

        //Plex.get().setChat(new ChatHandlerImpl());

        addDefaultMessage("guildsHelpCommand", "<gradient:gold:yellow>======</gradient>Guild Menu<gradient:gold:yellow>======</gradient><newline><newline>{0}", "0 - The commands list");
        addDefaultMessage("guildsCommandDisplay", "<gold>{0} <yellow>{1}", "0 - The command name", "1 - The command description");
        addDefaultMessage("guildCommandNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid sub command!", "0 - The sub command");
        addDefaultMessage("guildNotFound", "<red>You're currently not a part of a guild!");
        addDefaultMessage("guildInThis", "<red>You're currently a part of this guild!");
        addDefaultMessage("alreadyInGuild", "<red>You're currently in a guild. Please do <gold>/guild leave<red> if you're a member, or if you're an owner with members, <gold>/guild promote <player><red> then <gold>/guild leave<red>, or just an owner, <gold>/guild disband<red>.");
        addDefaultMessage("guildNotOwner", "<red>You're not the owner of this guild!");
        addDefaultMessage("guildMemberNotFound", "<red>This guild member could not be found!");
        addDefaultMessage("guildOwnerSet", "<green>You have successfully promoted <dark_green>{0}<green> to be the new guild owner. You have been set to a default guild member.");

        addDefaultMessage("guildPrefixSet", "<green>You have changed the guild prefix to '<gold>{0}</gold><green>'", "0 - The new prefix");
        addDefaultMessage("guildPrefixCleared", "<green>Your guild's prefix has been cleared.");

        addDefaultMessage("guildWarpAlphanumeric", "<red>Warp names may only contain alphabetical and/or numerical characters.");
        addDefaultMessage("guildWarpExists", "<red>'<gold>{0}</gold>'<red> is already an existing warp!", "0 - The warp name");
        addDefaultMessage("guildWarpNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid warp!", "0 - The warp name");
        addDefaultMessage("guildWarpCreated", "<green>You have created a warp called '<dark_green>{0}</dark_green><green>'", "0 - The warp name");

        addDefaultMessage("guildHomeRemoved", "<green>You have removed the guild's home!");
        addDefaultMessage("guildHomeSet", "<green>You have changed the guild's home!");
        addDefaultMessage("guildHomeNotFound", "<red>This guild currently has no home set.");

        addDefaultMessage("guildChatMessage", "<blue>[GUILD] <aqua>{0} <yellow>{1}", "0 - The player name", "1 - The message");
        addDefaultMessage("guildChatToggled", "<green>Your chat has been toggled {0}", "0 - On / Off");
        addDefaultMessage("guildChatConsoleLog", "<blue>[GUILD - {0}:{1}] <aqua>{2} <yellow>{3}", "0 - The guild name", "1 - The guild unique identifier", "2 - The player name", "3 - The message");

        addDefaultMessage("guildNoInvite", "<red>You don't have any pending invitations!");
        addDefaultMessage("guildNotValidInvite", "<red>You don't have an invite from this guild!");
        addDefaultMessage("guildInviteExists", "<red>You've already sent an invite to this person!");
        addDefaultMessage("guildInviteSent", "<green>You have sent an invite to <dark_green>{0}", "0 - The invitee");
        addDefaultMessage("guildInviteReceived", "<gold>You have received an invite from <yellow>{0}<gold> for the guild <yellow>{1}<newline><newline><green><bold><click:run_command:/guild invite accept {1}>[ACCEPT]<newline><newline><!bold><gold>You may also run <yellow>/guild invite accept {1}<gold> to accept this invite. It will expire in 5 minutes", "0 - The inviter", "1 - The guild name");
        addDefaultMessage("guildMemberJoined", "<green>{0} has joined the guild!", "0 - The player who joined");
        addDefaultMessage("guildMemberLeft", "<green>{0} has left the guild!", "0 - The player who left");
        addDefaultMessage("guildDisbandNeeded", "<red>You need to disband your guild using <gold>/guild disband<red> or promote a new owner using <gold>/guild owner <player>");
        addDefaultMessage("guildAutoDisbanded", "<green>Auto-disbanding your guild since there were no members");
    }

    @Override
    public void disable()
    {
        // Unregistering listeners / commands is handled by Plex
        this.getGuildHolder().getGuilds().forEach(sqlGuildManager::updateGuild);
        //this.getPlex().setChat(new ChatListener.PlexChatRenderer());
    }

    public static Guilds get()
    {
        return module;
    }
}
