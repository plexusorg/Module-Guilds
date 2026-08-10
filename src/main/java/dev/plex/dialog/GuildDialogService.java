package dev.plex.dialog;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class GuildDialogService
{
    public void openHome(Player player, Guild guild)
    {
        player.showDialog(dialog(
                title(guild.getName()),
                List.of(
                        body("Welcome to your guild panel.", NamedTextColor.GRAY),
                        body("Manage members, enter your guild world, or configure member permissions.", NamedTextColor.DARK_AQUA)
                ),
                List.of(
                        button("Members", "View online status and manage members", audience -> player(audience, viewer -> openMembers(viewer, guild))),
                        button("Guild World", "Load and enter your ASP guild world", audience -> player(audience, viewer -> enterWorld(viewer, guild))),
                        button("Permissions", "Configure role block permissions", audience -> player(audience, viewer -> openPermissions(viewer, guild)))
                ),
                1
        ));
    }

    public void openMembers(Player player, Guild guild)
    {
        List<ActionButton> buttons = new ArrayList<>();
        for (Member member : guild.getMembers())
        {
            boolean online = Bukkit.getPlayer(member.getUuid()) != null;
            buttons.add(button(
                    member.getName(),
                    (online ? "Online" : "Offline") + " - " + (guild.isOwner(member.getUuid()) ? "Owner" : "Member"),
                    audience -> player(audience, viewer -> openMember(viewer, guild, member.getUuid()))
            ));
        }
        buttons.add(button("Back", "Return to the guild menu", audience -> player(audience, viewer -> openHome(viewer, guild))));
        player.showDialog(dialog(
                title(guild.getName() + " Members"),
                List.of(body("Select a member to view or manage them.", NamedTextColor.GRAY)),
                buttons,
                1
        ));
    }

    public void openMember(Player player, Guild guild, UUID memberUuid)
    {
        Member member = guild.getMember(memberUuid);
        if (member == null)
        {
            openMembers(player, guild);
            return;
        }
        boolean online = Bukkit.getPlayer(member.getUuid()) != null;
        List<DialogBody> body = List.of(
                body("Status: " + (online ? "Online" : "Offline"), online ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                body("Role: " + (guild.isOwner(memberUuid) ? "Owner" : "Member"), guild.isOwner(memberUuid) ? NamedTextColor.GOLD : NamedTextColor.AQUA)
        );
        List<ActionButton> buttons = new ArrayList<>();
        if (guild.isOwner(player.getUniqueId()) && !guild.isOwner(memberUuid))
        {
            buttons.add(button("Kick Member", "Remove this member from the guild", audience -> player(audience, viewer -> kickMember(viewer, guild, member))));
            buttons.add(button("Transfer Ownership", "Make this member the guild owner", audience -> player(audience, viewer -> transferOwner(viewer, guild, member))));
        }
        if (guild.isOwner(player.getUniqueId()))
        {
            buttons.add(button("Permissions", "Edit member role permissions", audience -> player(audience, viewer -> openPermissions(viewer, guild))));
        }
        buttons.add(button("Back", "Return to members", audience -> player(audience, viewer -> openMembers(viewer, guild))));
        player.showDialog(dialog(title(member.getName()), body, buttons, 1));
    }

    public void openPermissions(Player player, Guild guild)
    {
        if (!guild.isOwner(player.getUniqueId()))
        {
            player.sendMessage(Guilds.get().messageComponent("guildNotOwner"));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (GuildRole role : GuildRole.values())
        {
            buttons.add(button(
                    role.name(),
                    "Edit " + role.name().toLowerCase() + " permissions",
                    audience -> player(audience, viewer -> openRolePermissions(viewer, guild, role))
            ));
        }
        buttons.add(button("Back", "Return to the guild menu", audience -> player(audience, viewer -> openHome(viewer, guild))));
        player.showDialog(dialog(
                title("Role Permissions"),
                List.of(
                        body("Choose a guild role to configure.", NamedTextColor.GRAY),
                        body("Permissions apply inside the guild world.", NamedTextColor.DARK_AQUA)
                ),
                buttons,
                1
        ));
    }

    private void openRolePermissions(Player player, Guild guild, GuildRole role)
    {
        if (!guild.isOwner(player.getUniqueId()))
        {
            player.sendMessage(Guilds.get().messageComponent("guildNotOwner"));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (GuildPermission permission : GuildPermission.values())
        {
            boolean enabled = guild.permissions(role).hasPermission(permission);
            buttons.add(button(
                    permission.displayName() + ": " + (enabled ? "Enabled" : "Disabled"),
                    "Click to toggle " + permission.displayName().toLowerCase(),
                    audience -> player(audience, viewer -> togglePermission(viewer, guild, role, permission))
            ));
        }
        buttons.add(button("Back", "Return to role list", audience -> player(audience, viewer -> openPermissions(viewer, guild))));
        player.showDialog(dialog(
                title(role.name() + " Permissions"),
                List.of(body("Configure " + role.name().toLowerCase() + " permissions for this guild world.", NamedTextColor.GRAY)),
                buttons,
                1
        ));
    }

    private void enterWorld(Player player, Guild guild)
    {
        player.sendMessage(Guilds.get().messageComponent("guildWorldLoading"));
        Guilds.get().getGuildWorldService().ensureWorld(guild).whenComplete((world, throwable) ->
        {
            if (throwable != null)
            {
                Guilds.get().api().scheduler().executeGlobal(() ->
                {
                    throwable.printStackTrace();
                    player.sendMessage(Guilds.get().messageComponent("guildWorldLoadFailed"));
                });
                return;
            }
            Guilds.get().api().scheduler().executeEntity(player, () -> player.teleportAsync(world.getSpawnLocation().toCenterLocation()), 1L);
        });
    }

    private void kickMember(Player player, Guild guild, Member member)
    {
        if (!guild.isOwner(player.getUniqueId()) || guild.isOwner(member.getUuid()))
        {
            return;
        }
        Guilds.get().getGuildRepository().removeMember(guild.getGuildUuid(), member.getUuid()).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                Guilds.get().getLogger().error("Failed to update guild role permission", throwable);
                player.sendMessage(Guilds.get().messageComponent("guildStorageFailed"));
                return;
            }
            guild.removeMember(member.getUuid());
            Guilds.get().getGuildHolder().unindexMember(member.getUuid());
            Guilds.get().getGuildWorldService().ejectNonMembers(guild);
            player.sendMessage(Guilds.get().messageComponent("guildMemberKicked", member.getName()));
            openMembers(player, guild);
        });
    }

    private void transferOwner(Player player, Guild guild, Member member)
    {
        if (!guild.isOwner(player.getUniqueId()) || guild.isOwner(member.getUuid()))
        {
            return;
        }
        Member previousOwner = guild.getMember(player.getUniqueId());
        Guilds.get().getGuildRepository().transferOwner(guild.getGuildUuid(), member.getUuid(), player.getUniqueId()).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(Guilds.get().messageComponent("guildStorageFailed"));
                return;
            }
            guild.setOwnerUuid(member.getUuid());
            member.setRole(GuildRole.OWNER);
            if (previousOwner != null)
            {
                previousOwner.setRole(GuildRole.MEMBER);
            }
            player.sendMessage(Guilds.get().messageComponent("guildOwnerSet", member.getName()));
            openMember(player, guild, previousOwner == null ? member.getUuid() : previousOwner.getUuid());
        });
    }

    private void togglePermission(Player player, Guild guild, GuildRole role, GuildPermission permission)
    {
        boolean enabled = !guild.permissions(role).hasPermission(permission);
        guild.setPermission(role, permission, enabled);
        Guilds.get().getGuildRepository().updateRolePermission(guild.getGuildUuid(), role, permission, enabled).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(Guilds.get().messageComponent("guildStorageFailed"));
                return;
            }
            openRolePermissions(player, guild, role);
        });
    }

    private Dialog dialog(Component title, List<DialogBody> body, List<ActionButton> actions, int columns)
    {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions)
                        .columns(columns)
                        .build()));
    }

    private ActionButton button(String label, String tooltip, java.util.function.Consumer<Audience> callback)
    {
        return ActionButton.builder(text(label, NamedTextColor.AQUA))
                .tooltip(text(tooltip, NamedTextColor.GRAY))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> callback.accept(audience), ClickCallback.Options.builder()
                        .uses(1)
                        .lifetime(Duration.ofMinutes(5))
                        .build()))
                .build();
    }

    private DialogBody body(String text, NamedTextColor color)
    {
        return DialogBody.plainMessage(text(text, color));
    }

    private Component title(String value)
    {
        return text(value, NamedTextColor.DARK_AQUA);
    }

    private Component text(String value, NamedTextColor color)
    {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private void player(Audience audience, java.util.function.Consumer<Player> playerConsumer)
    {
        if (audience instanceof Player player)
        {
            playerConsumer.accept(player);
        }
    }
}
