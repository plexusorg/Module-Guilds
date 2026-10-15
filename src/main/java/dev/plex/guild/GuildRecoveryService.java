package dev.plex.guild;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.time.ZonedDateTime;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.guild.data.GuildTimeMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plex.Guilds;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.storage.HistoryPurge;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.Nullable;

public final class GuildRecoveryService
{
    private final Guilds module;
    private final Map<UUID, UUID> chatTargets = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> worldVisitors = new ConcurrentHashMap<>();

    public GuildRecoveryService(Guilds module)
    {
        this.module = module;
    }

    public boolean toggleChat(UUID player, UUID guild)
    {
        if (chatTargets.remove(player, guild))
        {
            return false;
        }
        chatTargets.put(player, guild);
        return true;
    }

    public Guild chatTarget(UUID player)
    {
        UUID guild = chatTargets.get(player);
        return guild == null ? null : module.getGuildHolder().guildById(guild).orElse(null);
    }

    public void visit(UUID player, Guild guild)
    {
        worldVisitors.put(player, guild.getGuildUuid());
    }

    public boolean canVisit(UUID player, Guild guild)
    {
        return guild.getGuildUuid().equals(worldVisitors.get(player));
    }

    public void clearPlayer(UUID player)
    {
        chatTargets.remove(player);
        worldVisitors.remove(player);
    }

    public CompletableFuture<List<GuildAction>> actions(Long session)
    {
        return module.getGuildRepository().actions(session, module.getRecoveryCutoff());
    }

    public List<Component> preview(GuildAction action)
    {
        List<Component> report = new ArrayList<>();
        Guild guild = restoreCandidate(action, report);
        report.add(message("guildRecoveryMembers", "count", Integer.toString(guild.getMembers().size())));
        if (!module.isGuildWorldsEnabled())
        {
            report.add(message("guildRecoveryWorldUnavailable"));
        }
        return report;
    }

    // The pending guild keeps its name and prefix, so only the members can conflict.
    private Guild restoreCandidate(GuildAction action, List<Component> report)
    {
        Guild guild = GuildState.guild(action.before(), module);
        for (Member member : List.copyOf(guild.getMembers()))
        {
            Guild current = module.getGuildHolder().guild(member.getUuid()).orElse(null);
            if (current != null)
            {
                guild.removeMember(member.getUuid());
                report.add(message("guildRecoveryMemberConflict", "player", member.getUuid().toString(), "guild", current.getName()));
            }
        }
        if (guild.getMembers().isEmpty())
        {
            report.add(message("guildRecoveryNoMembers"));
        }
        else if (!guild.isMember(guild.getOwnerUuid()))
        {
            Member owner = guild.getMembers().stream().min(Comparator.comparing(Member::getRole).thenComparing(Member::getUuid)).orElseThrow();
            guild.setOwnerUuid(owner.getUuid());
            owner.setRole(GuildRole.OWNER);
            report.add(message("guildRecoveryNewOwner", "player", owner.getUuid().toString()));
        }
        return guild;
    }

    private boolean prefixTaken(Guild guild)
    {
        return guild.getPrefixKey() != null && Stream.concat(module.getGuildHolder().guilds().stream(),
                        module.getGuildHolder().pending().stream().map(PendingGuild::guild))
                .anyMatch(other -> !other.getGuildUuid().equals(guild.getGuildUuid()) && guild.getPrefixKey().equals(other.getPrefixKey()));
    }

    public CompletableFuture<List<Component>> undo(List<GuildAction> actions, Actor actor)
    {
        return module.getGuildSessionListener().session(actor.uuid()).thenCompose(session ->
        {
            RecoveryActor recoveryActor = new RecoveryActor(actor, session);
            List<Component> report = new ArrayList<>();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (GuildAction action : actions)
            {
                chain = chain.thenCompose(unused -> undo(action, recoveryActor, report));
            }
            return chain.thenApply(unused -> report);
        });
    }

    private CompletableFuture<Void> undo(GuildAction action, RecoveryActor actor, List<Component> report)
    {
        Guild queue = module.getGuildHolder().guildById(action.guildUuid()).orElseGet(() ->
                new Guild(action.guildUuid(), ZonedDateTime.now(module.getZoneId())));
        return module.getGuildMutationService().serialize(queue, () -> actions(action.sessionId()).thenCompose(current ->
        {
            if (current.stream().noneMatch(entry -> entry.id() == action.id()))
            {
                return skip(action, report, "guildRecoveryAlreadyUndone");
            }
            if (action.type().equals("DISBAND"))
            {
                return restore(action, actor, report);
            }
            Guild guild = module.getGuildHolder().guildById(action.guildUuid()).orElse(null);
            if (guild == null)
            {
                return skip(action, report, "guildRecoveryGuildMissing");
            }
            if (action.type().equals("WORLD_GENERATE"))
            {
                return skip(action, report, "guildRecoveryGenerated");
            }
            if (action.type().equals("INVITE"))
            {
                return undoInvite(guild, action, actor, report);
            }
            if (!Objects.equals(normalized(currentState(guild, action)), normalized(action.after())))
            {
                return skip(action, report, "guildRecoveryStateChanged");
            }
            if (action.type().equals("CREATE"))
            {
                return disband(guild, action, actor, report);
            }
            return reverse(guild, action, actor, report);
        })).exceptionally(failure ->
        {
            module.getLogger().error("Failed to undo guild action {}", action.id(), failure);
            report.add(step(action, "guildRecoveryStepFailed"));
            return null;
        });
    }

    // Runs in the guild queue, which finalization shares, so the guild cannot be finalized under the restore.
    private CompletableFuture<Void> restore(GuildAction action, RecoveryActor actor, List<Component> report)
    {
        PendingGuild pending = module.getGuildHolder().pendingById(action.guildUuid()).orElse(null);
        if (pending == null || pending.isDue(module.getRecoveryCutoff()))
        {
            return skip(action, report, "guildRecoveryRestoreConflict");
        }
        Guild guild = restoreCandidate(action, report);
        if (guild.getMembers().isEmpty())
        {
            return skip(action, report, "guildRecoveryRestoreConflict");
        }
        return module.getGuildRepository().restoreGuild(guild, inverse(guild, actor, action, "CREATE", null, GuildState.snapshot(guild))).thenRun(() ->
        {
            guild.getInviteHistory().addAll(pending.guild().getInviteHistory());
            module.getGuildHolder().addGuild(guild);
            module.getGuildHolder().removePending(guild.getGuildUuid());
            module.getGuildWorldIndex().record("RESTORE", guild, actor.actor().uuid());
            if (module.isGuildWorldsEnabled())
            {
                module.getGuildWorldService().openWorld(guild.getWorldName());
            }
            report.add(step(action, "guildRecoveryStepDone"));
        });
    }

    private CompletableFuture<Void> disband(Guild guild, GuildAction action, RecoveryActor actor, List<Component> report)
    {
        return module.getGuildMutationService().disband(guild, actor.actor(), actor.sessionId(), action.id())
                .thenRun(() -> report.add(step(action, "guildRecoveryStepDone")));
    }

    private JsonElement currentState(Guild guild, GuildAction action)
    {
        JsonElement target = present(action.after()) ? action.after() : action.before();
        return switch (action.type())
        {
            case "CREATE" -> GuildState.snapshot(guild);
            case "JOIN", "REMOVE_MEMBER", "SET_ROLE" ->
            {
                UUID player = uuid(target, "player");
                if (!guild.isMember(player))
                {
                    yield JsonNull.INSTANCE;
                }
                JsonObject member = GuildState.object("player", player, "role", guild.getRole(player));
                if (target.getAsJsonObject().has("joined"))
                {
                    member.addProperty("joined", guild.getMember(player).getJoinedAt());
                }
                yield member;
            }
            case "TRANSFER_OWNER" -> GuildState.object("owner", guild.getOwnerUuid());
            case "GUEST_SET", "GUEST_REVOKE" -> GuildState.value(guild.getActiveGuest(uuid(target, "player")));
            case "PREFIX" -> GuildState.value(guild.getPrefix());
            case "SPAWN" -> GuildState.value(guild.getSpawn());
            case "WARP_SET", "WARP_DELETE" ->
            {
                String name = target.getAsJsonObject().get("name").getAsString();
                yield guild.getWarps().containsKey(name) || action.type().equals("WARP_SET")
                        ? GuildState.object("name", name, "location", guild.getWarps().get(name)) : JsonNull.INSTANCE;
            }
            case "TIME_MODE" -> GuildState.value(guild.getTimeMode());
            case "WEATHER_MODE" -> GuildState.value(guild.getWeatherMode());
            case "WORLD_RESET" -> GuildState.object("spawn", guild.getSpawn(), "warps", guild.getWarps());
            default -> throw new IllegalArgumentException("Unknown guild action: " + action.type());
        };
    }

    private CompletableFuture<Void> reverse(Guild guild, GuildAction action, RecoveryActor actor, List<Component> report)
    {
        Guild candidate = GuildState.guild(GuildState.snapshot(guild), module);
        String type = action.type();
        JsonElement before = action.before();
        JsonElement after = action.after();
        switch (type)
        {
            case "JOIN" -> candidate.removeMember(uuid(after, "player"));
            case "REMOVE_MEMBER" ->
            {
                UUID player = uuid(before, "player");
                if (module.getGuildHolder().guild(player).isPresent())
                {
                    return skip(action, report, "guildRecoveryPlayerInGuild");
                }
                candidate.addMember(new Member(player, GuildRole.valueOf(before.getAsJsonObject().get("role").getAsString()),
                        before.getAsJsonObject().get("joined").getAsLong()));
            }
            case "SET_ROLE" -> candidate.getMember(uuid(before, "player")).setRole(GuildRole.valueOf(before.getAsJsonObject().get("role").getAsString()));
            case "TRANSFER_OWNER" ->
            {
                UUID owner = uuid(before, "owner");
                UUID player = uuid(before, "player");
                if (!candidate.isMember(owner) || !candidate.isMember(player))
                {
                    return skip(action, report, "guildRecoveryStateChanged");
                }
                candidate.setOwnerUuid(owner);
                candidate.getMember(owner).setRole(GuildRole.OWNER);
                candidate.getMember(player).setRole(GuildRole.valueOf(before.getAsJsonObject().get("role").getAsString()));
            }
            case "GUEST_SET", "GUEST_REVOKE" ->
            {
                Guest guest = GuildState.guest(before);
                if (guest != null && (!guest.isActive(Instant.now()) || candidate.isMember(guest.playerUuid())))
                {
                    return skip(action, report, "guildRecoveryGuestExpired");
                }
                if (guest == null)
                {
                    candidate.getGuests().remove(uuid(after, "player"));
                }
                else
                {
                    candidate.getGuests().put(guest.playerUuid(), guest);
                }
            }
            case "PREFIX" -> candidate.setPrefix(GuildPrefix.parse(GuildState.text(before), module.api().messages()::playerText));
            case "SPAWN" -> candidate.setSpawn(GuildState.location(before));
            case "WARP_SET", "WARP_DELETE" ->
            {
                String name = before.getAsJsonObject().get("name").getAsString();
                var location = GuildState.location(before.getAsJsonObject().get("location"));
                if (location == null)
                {
                    candidate.getWarps().remove(name);
                }
                else
                {
                    candidate.getWarps().put(name, location);
                }
            }
            case "TIME_MODE" -> candidate.setTimeMode(GuildTimeMode.valueOf(before.getAsString()));
            case "WEATHER_MODE" -> candidate.setWeatherMode(GuildWeatherMode.valueOf(before.getAsString()));
            case "WORLD_RESET" ->
            {
                return reverseWorld(guild, candidate, action, actor, report);
            }
            default -> throw new IllegalArgumentException("Unknown guild action: " + type);
        }
        if (prefixTaken(candidate))
        {
            return skip(action, report, "guildRecoveryPrefixTaken");
        }
        return save(guild, candidate, inverseAction(guild, candidate, action, actor), action, report, null);
    }

    private GuildAction inverseAction(Guild guild, Guild candidate, GuildAction action, RecoveryActor actor)
    {
        String type = switch (action.type())
        {
            case "JOIN" -> "REMOVE_MEMBER";
            case "REMOVE_MEMBER" -> "JOIN";
            case "GUEST_REVOKE" -> "GUEST_SET";
            case "GUEST_SET" -> present(action.before()) ? "GUEST_SET" : "GUEST_REVOKE";
            case "WARP_DELETE" -> "WARP_SET";
            case "WARP_SET" -> present(action.before().getAsJsonObject().get("location")) ? "WARP_SET" : "WARP_DELETE";
            default -> action.type();
        };
        JsonElement before = action.after();
        JsonElement after = action.before();
        if (action.type().equals("TRANSFER_OWNER"))
        {
            before = GuildState.object("owner", guild.getOwnerUuid(), "player", candidate.getOwnerUuid(), "role", guild.getRole(candidate.getOwnerUuid()));
            after = GuildState.object("owner", candidate.getOwnerUuid());
        }
        if (type.equals("WARP_DELETE"))
        {
            after = null;
        }
        if (action.type().equals("WARP_DELETE"))
        {
            before = GuildState.object("name", action.before().getAsJsonObject().get("name"), "location", null);
        }
        return inverse(guild, actor, action, type, before, after);
    }

    private CompletableFuture<Void> reverseWorld(Guild guild, Guild candidate, GuildAction action, RecoveryActor actor, List<Component> report)
    {
        if (!module.isGuildWorldsEnabled())
        {
            return skip(action, report, "guildRecoveryWorldUnavailable");
        }
        String backup = action.before().getAsJsonObject().get("backup").getAsString();
        boolean empty = action.before().getAsJsonObject().has("empty") && action.before().getAsJsonObject().get("empty").getAsBoolean();
        CompletableFuture<Boolean> available = empty ? CompletableFuture.completedFuture(true) : module.getGuildWorldService().hasBackup(backup);
        return available.thenCompose(exists ->
        {
            if (!exists)
            {
                return skip(action, report, "guildRecoveryBackupMissing");
            }
            JsonObject locations = action.before().getAsJsonObject().getAsJsonObject("locations");
            candidate.setSpawn(GuildState.location(locations.get("spawn")));
            candidate.getWarps().clear();
            locations.getAsJsonObject("warps").entrySet().forEach(entry -> candidate.getWarps().put(entry.getKey(), GuildState.location(entry.getValue())));
            // The replaced world is unloaded and saved here. Its edits must leave Oasis, or a rollback would write them over the restored blocks.
            return module.getGuildWorldService().restoreBackup(guild, backup).thenCompose(current -> save(guild, candidate,
                    inverse(guild, actor, action, "WORLD_RESET", GuildState.object("backup", current, "empty", current.equals("none"), "locations", action.after()), locations), action, report,
                    new HistoryPurge(guild.getWorldName(), System.currentTimeMillis())));
        });
    }

    private CompletableFuture<Void> undoInvite(Guild guild, GuildAction action, RecoveryActor actor, List<Component> report)
    {
        JsonElement state = present(action.after()) ? action.after() : action.before();
        UUID player = uuid(state, "player");
        return module.getGuildRepository().invitesFor(player).thenCompose(invites ->
        {
            var invite = invites.stream().filter(entry -> entry.getGuildUuid().equals(guild.getGuildUuid().toString())).findFirst().orElse(null);
            if (present(action.after()))
            {
                if (invite == null || invite.getExpiresAt() != state.getAsJsonObject().get("expires").getAsLong())
                {
                    return skip(action, report, "guildRecoveryStateChanged");
                }
                return module.getGuildRepository().deleteInvite(guild.getGuildUuid(), player,
                        inverse(guild, actor, action, "INVITE", state, null)).thenRun(() -> report.add(step(action, "guildRecoveryStepDone")));
            }
            long expires = state.getAsJsonObject().get("expires").getAsLong();
            if (invite != null || expires <= System.currentTimeMillis())
            {
                return skip(action, report, "guildRecoveryStateChanged");
            }
            return module.getGuildRepository().createInvite(guild.getGuildUuid(), uuid(state, "inviter"), player,
                    Instant.now(), Instant.ofEpochMilli(expires), inverse(guild, actor, action, "INVITE", null, state))
                    .thenRun(() -> report.add(step(action, "guildRecoveryStepDone")));
        });
    }

    private CompletableFuture<Void> save(Guild guild, Guild candidate, GuildAction inverse, GuildAction original, List<Component> report,
                                         @Nullable HistoryPurge purge)
    {
        return module.getGuildRepository().saveGuild(candidate, inverse, purge).thenRun(() ->
        {
            List<UUID> visitors = new ArrayList<>(guild.getGuests().keySet());
            guild.getMembers().forEach(member ->
            {
                visitors.add(member.getUuid());
                module.getGuildHolder().unindexMember(member.getUuid());
            });
            guild.setOwnerUuid(candidate.getOwnerUuid());
            guild.setPrefix(GuildPrefix.parse(candidate.getPrefix(), module.api().messages()::playerText));
            guild.setSpawn(candidate.getSpawn());
            guild.setTimeMode(candidate.getTimeMode());
            guild.setWeatherMode(candidate.getWeatherMode());
            candidate.getMembers().forEach(member ->
            {
                Member current = guild.getMember(member.getUuid());
                if (current != null)
                {
                    member.setChat(current.isChat());
                }
            });
            guild.getMembers().clear();
            guild.getMembers().addAll(candidate.getMembers());
            guild.getWarps().clear();
            guild.getWarps().putAll(candidate.getWarps());
            guild.getGuests().clear();
            guild.getGuests().putAll(candidate.getGuests());
            module.getGuildHolder().addGuild(guild);
            visitors.forEach(module.getGuildWorldAccessListener()::revoke);
            report.add(step(original, "guildRecoveryStepDone"));
            if (purge != null)
            {
                module.getHistoryPurgeQueue().drain();
            }
        }).thenCompose(unused -> module.isGuildWorldsEnabled() ? module.getGuildWorldService().applySettings(guild)
                : CompletableFuture.completedFuture(null));
    }

    private GuildAction inverse(Guild guild, RecoveryActor actor, GuildAction original, String type, JsonElement before, JsonElement after)
    {
        return new GuildAction(0, actor.sessionId(), actor.actor(), guild.getGuildUuid(), type, System.currentTimeMillis(), before, after, null, original.id());
    }

    private record RecoveryActor(Actor actor, Long sessionId)
    {
    }

    private static UUID uuid(JsonElement state, String key)
    {
        return UUID.fromString(state.getAsJsonObject().get(key).getAsString());
    }

    private static boolean present(JsonElement value)
    {
        return value != null && !value.isJsonNull();
    }

    private static JsonElement normalized(JsonElement value)
    {
        return value == null ? JsonNull.INSTANCE : JsonParser.parseString(value.toString());
    }

    private CompletableFuture<Void> skip(GuildAction action, List<Component> report, String reason)
    {
        report.add(message("guildRecoveryStepSkipped", "id", Long.toString(action.id()), "type", action.type())
                .append(message(reason)));
        return CompletableFuture.completedFuture(null);
    }

    private Component step(GuildAction action, String key)
    {
        return message(key, "id", Long.toString(action.id()), "type", action.type());
    }

    private Component message(String key, String... values)
    {
        var placeholders = new TagResolver[values.length / 2];
        for (int i = 0; i < values.length; i += 2)
        {
            placeholders[i / 2] = Placeholder.unparsed(values[i], values[i + 1]);
        }
        return module.messageComponent(key, placeholders);
    }
}
