package dev.plex;

import dev.plex.command.GuildCommand;
import dev.plex.config.ModuleConfig;
import dev.plex.data.GuildData;
import dev.plex.data.MemberData;
import dev.plex.listener.ChatListener;
import dev.plex.listener.JoinListener;
import dev.plex.module.PlexModule;
import dev.plex.util.PlexLog;
import lombok.Getter;
import org.bukkit.Bukkit;

@Getter
public class Guilds extends PlexModule
{
    private static Guilds module;
    private final SQLManager sqlManager = new SQLManager();
    private final GuildData guildData = new GuildData();
    private final MemberData memberData = new MemberData();
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
        addDefaultMessage("guildsHelpCommand", "<gradient:gold:yellow>======</gradient>Guild Menu<gradient:gold:yellow>======</gradient><newline><newline>{0}", "0 - The commands list");
        addDefaultMessage("guildsCommandDisplay", "<gold>{0} <yellow>{1}", "0 - The command name", "1 - The command description");
        addDefaultMessage("guildCommandNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid sub command!", "0 - The sub command");
        addDefaultMessage("guildNotFound", "<red>You're currently not a part of a guild!");
        addDefaultMessage("guildNotExist", "<red>A guild doesn't exist with the name '<gold>{0}</gold>'", "0 - Name used to find a guild");
        addDefaultMessage("guildInThis", "<red>You're currently a part of this guild!");
        addDefaultMessage("alreadyInGuild", "<red>You're currently in a guild. Please do <gold>/guild leave<red> if you're a member, or if you're an owner with members, <gold>/guild promote <player><red> then <gold>/guild leave<red>, or just an owner, <gold>/guild disband<red>.");
        addDefaultMessage("guildNotOwner", "<red>You're not the owner of this guild!");
        addDefaultMessage("guildNotMod", "<red>You're not a moderator of this guild!");
        addDefaultMessage("guildMemberNotFound", "<red>This guild member could not be found!");
        addDefaultMessage("guildAlphanumericName", "<red>Guild names may only be alphanumeric!");
        addDefaultMessage("guildOwnerSet", "<green>You have successfully promoted <dark_green>{0}<green> to be the new guild owner. You have been set to a default guild member.");

        addDefaultMessage("guildPrefixSet", "<green>You have changed the guild prefix to '<gold>{0}</gold><green>'", "0 - The new prefix");
        addDefaultMessage("guildPrefixCleared", "<green>Your guild's prefix has been cleared.");

        addDefaultMessage("guildMotdSet", "<green>You have changed the guild prefix to '<gold>{0}</gold><green>'", "0 - The new MOTD");
        addDefaultMessage("guildMotdCleared", "<green>Your guild's MOTD has been cleared.");
        addDefaultMessage("guildMotdExceededLimit", "<red>The MOTD character limit is 256 characters!");

        addDefaultMessage("guildWarpAlphanumeric", "<red>Warp names may only contain alphabetical and/or numerical characters.");
        addDefaultMessage("guildWarpExists", "<red>'<gold>{0}</gold>'<red> is already an existing warp!", "0 - The warp name");
        addDefaultMessage("guildWarpNotFound", "<red>'<gold>{0}</gold>'<red> is not a valid warp!", "0 - The warp name");
        addDefaultMessage("guildWarpCreated", "<green>You have created a warp called '<dark_green>{0}</dark_green><green>'", "0 - The warp name");
        addDefaultMessage("guildWarpSuccess", "<green>You have warped to '<dark_green>{0}<green>'", "0 - The warp name");
        addDefaultMessage("guildWarpRemoved", "<green>You have removed the '<dark_green>{0}<green>' warp!", "0 - The warp name");

        addDefaultMessage("guildHomeRemoved", "<green>You have removed the guild's home!");
        addDefaultMessage("guildHomeSet", "<green>You have changed the guild's home!");
        addDefaultMessage("guildHomeNotFound", "<red>This guild currently has no home set.");
        addDefaultMessage("guildHomeTeleport", "<green>You have teleported to the guild's home!");

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
        addDefaultMessage("guildLeft", "<green>Successfully left the guild");
        addDefaultMessage("guildDisbandNeeded", "<red>You need to disband your guild using <gold>/guild disband<red> or promote a new owner using <gold>/guild owner <player>");
        addDefaultMessage("guildDisbanded", "<green>Your guild has been disbanded.");
        addDefaultMessage("guildAutoDisbanded", "<green>Auto-disbanding your guild since there were no members");
        addDefaultMessage("guildActionConfirmation", "<gold>Are you sure you want to {0}? Type <yellow>/guild {0}</yellow> to confirm.", "0 - Action to confirm");

        sqlManager.createTables();
        sqlManager.getMembers().forEach(memberData::addMember);
        sqlManager.getGuilds().forEach(guildData::addGuild);
        PlexLog.log("GUILD SIZE: {0}", guildData.getGuilds().size());
        registerListener(new JoinListener());
        registerListener(new ChatListener());
        registerCommand(new GuildCommand());
    }

    @Override
    public void disable()
    {
        guildData.getGuilds().forEach(sqlManager::updateGuild);
        memberData.getMembers().forEach(sqlManager::updateMember);
    }

    public static Guilds get()
    {
        return module;
    }

    public static void logException(Throwable t)
    {
        Bukkit.getScheduler().runTask(get().getPlex(), () -> t.printStackTrace());
    }
}
