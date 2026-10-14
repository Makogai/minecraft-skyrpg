package com.skyrpg.party;

import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Notice;
import com.skyrpg.party.PartyData.Member;
import com.skyrpg.party.PartyData.Party;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * The party rules: invites, joining, leaving, leadership, party chat, the state sent to the clients and no friendly fire.
 * Parties live in {@link PartyData}; invites and the chat toggle are short-lived and kept in memory. Everything here runs on
 * the server thread.
 */
public final class Parties {
    public static final int MAX_MEMBERS = 8;
    public static final long INVITE_MILLIS = 60_000;
    /** The party tag colour (a green-teal that no Notice kind uses). */
    public static final int COLOR = 0x4FD6A0;
    /** Marker key the chat mod reads (skyrpg_chat.party): it files the line under the Party tab. The fallback means no chat mod is needed. */
    private static final String CHAT_KEY = "skyrpg_chat.party";
    private static final int SYNC_TICKS = 10;

    private record Invite(UUID from, String fromName, UUID target, String targetName, long expires) {
    }

    private static MinecraftServer server;
    private static final List<Invite> INVITES = new ArrayList<>();
    private static final Set<UUID> PARTY_CHAT = new HashSet<>();
    /** Players who were sent a non-empty state, so they get one empty state when they stop having anything to show. */
    private static final Set<UUID> SYNCED = new HashSet<>();
    private static int tickCounter;

    private Parties() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            INVITES.clear();
            PARTY_CHAT.clear();
            SYNCED.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> onLeaveServer(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(Parties::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !isFriendlyFire(entity, source));
        // Default phase, so it runs before the chat mod's late phase: a player with party chat on never reaches public chat.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            if (!PARTY_CHAT.contains(sender.getUUID())) {
                return true;
            }
            if (partyOf(sender.getUUID()) == null) {
                PARTY_CHAT.remove(sender.getUUID());
                return true;
            }
            chat(sender, message.signedContent());
            return false;
        });
    }

    // ------------------------------------------------------------------ queries (public API for other mods)

    static PartyData data() {
        return PartyData.get(server);
    }

    /** The party the player is in, or null. */
    public static Party partyOf(UUID player) {
        return server == null ? null : data().of(player);
    }

    /** Whether two different players are in the same party. */
    public static boolean sameParty(UUID a, UUID b) {
        if (a.equals(b)) {
            return false;
        }
        Party party = partyOf(a);
        return party != null && party.member(b) != null;
    }

    /**
     * Whether {@code victim} is a player in the same party as whoever caused {@code source}: the attacker is resolved through
     * projectiles, thrown potions, area effects and pets to their owner.
     */
    public static boolean isFriendlyFire(Entity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer target) || server == null) {
            return false;
        }
        Entity cause = source.getEntity();
        if (cause == null) {
            cause = source.getDirectEntity();
        }
        ServerPlayer attacker = owner(cause);
        return attacker != null && sameParty(attacker.getUUID(), target.getUUID());
    }

    private static ServerPlayer owner(Entity entity) {
        for (int depth = 0; entity != null && depth < 4; depth++) {
            if (entity instanceof ServerPlayer player) {
                return player;
            }
            if (entity instanceof Projectile projectile) {
                entity = projectile.getOwner();
            } else if (entity instanceof TamableAnimal pet) {
                entity = pet.getOwner();
            } else {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ text

    private static MutableComponent name(String name) {
        // An empty parent, so what is appended after the name does not inherit its bold.
        return Component.empty().append(Component.literal(name).withStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true)));
    }

    private static MutableComponent plain(String text) {
        return Component.literal(text);
    }

    private static Notice notice() {
        return Notice.of(Notice.Kind.INFO).icon(Notice.SHIELD).tag("PARTY").color(COLOR);
    }

    private static Component wrap(Component notice) {
        return Component.translatableWithFallback(CHAT_KEY, "%1$s%2$s", notice, Component.empty(), "");
    }

    private static MutableComponent crown() {
        return Component.literal(Notice.CROWN).withStyle(Style.EMPTY.withFont(Notice.FONT).withColor(0xFFFFFF).withBold(false));
    }

    /** A party line (tag, then the body) for one player. */
    static void say(ServerPlayer to, Component body) {
        to.sendSystemMessage(wrap(notice().build(body)));
    }

    private static void error(ServerPlayer to, String text) {
        to.sendSystemMessage(wrap(Notice.of(Notice.Kind.WARN).icon(Notice.SHIELD).tag("PARTY").build(Component.literal(text))));
    }

    private static List<ServerPlayer> online(Party party) {
        List<ServerPlayer> list = new ArrayList<>();
        for (Member member : party.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    private static void announce(Party party, Component body) {
        for (ServerPlayer player : online(party)) {
            say(player, body);
        }
    }

    // ------------------------------------------------------------------ invites

    private static void expireInvites() {
        long now = System.currentTimeMillis();
        INVITES.removeIf(invite -> {
            if (invite.expires > now) {
                return false;
            }
            ServerPlayer from = server.getPlayerList().getPlayer(invite.from);
            if (from != null) {
                say(from, plain("Your invite to ").append(name(invite.targetName)).append(plain(" expired.")));
            }
            ServerPlayer target = server.getPlayerList().getPlayer(invite.target);
            if (target != null) {
                say(target, plain("The invite from ").append(name(invite.fromName)).append(plain(" expired.")));
            }
            return true;
        });
    }

    public static void invite(ServerPlayer actor, ServerPlayer target) {
        expireInvites();
        if (actor.getUUID().equals(target.getUUID())) {
            error(actor, "You can't invite yourself.");
            return;
        }
        Party mine = partyOf(actor.getUUID());
        if (mine != null && mine.members.size() >= MAX_MEMBERS) {
            error(actor, "Your party is full (" + MAX_MEMBERS + " members).");
            return;
        }
        String targetName = target.getGameProfile().name();
        if (mine != null && mine.member(target.getUUID()) != null) {
            error(actor, targetName + " is already in your party.");
            return;
        }
        if (partyOf(target.getUUID()) != null) {
            error(actor, targetName + " is already in a party.");
            return;
        }
        for (Invite invite : INVITES) {
            if (invite.from.equals(actor.getUUID()) && invite.target.equals(target.getUUID())) {
                error(actor, "You already invited " + targetName + ".");
                return;
            }
        }
        String actorName = actor.getGameProfile().name();
        INVITES.add(new Invite(actor.getUUID(), actorName, target.getUUID(), targetName, System.currentTimeMillis() + INVITE_MILLIS));
        say(actor, plain("Invited ").append(name(targetName)).append(plain(". It expires in 60 seconds.")));
        MutableComponent body = name(actorName).append(plain(" invited you to a party. "))
                .append(button("[Accept]", 0x55DD77, "/party accept " + actorName, "Join " + actorName + "'s party"))
                .append(plain(" "))
                .append(button("[Deny]", 0xFF5555, "/party deny " + actorName, "Decline the invite"));
        say(target, body);
        com.skyrpg.core.text.Msg.playSound(target, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        sync();
    }

    private static Component button(String label, int color, String command, String tip) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tip).withColor(color))));
    }

    private static Invite pick(ServerPlayer target, String fromName) {
        Invite found = null;
        for (Invite invite : INVITES) {
            if (!invite.target.equals(target.getUUID())) {
                continue;
            }
            if (fromName == null || fromName.isEmpty() || invite.fromName.equalsIgnoreCase(fromName)) {
                found = invite; // the most recent one when no name is given
            }
        }
        return found;
    }

    public static void accept(ServerPlayer actor, String fromName) {
        expireInvites();
        Invite invite = pick(actor, fromName);
        if (invite == null) {
            error(actor, fromName == null || fromName.isEmpty() ? "You have no party invite." : "You have no invite from " + fromName + ".");
            return;
        }
        if (partyOf(actor.getUUID()) != null) {
            error(actor, "You are already in a party. Leave it first with /party leave.");
            return;
        }
        PartyData data = data();
        Party party = data.of(invite.from);
        if (party == null) {
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.from);
            if (inviter == null) {
                INVITES.remove(invite);
                error(actor, invite.fromName + " is no longer online.");
                return;
            }
            party = new Party(UUID.randomUUID(), invite.from, List.of(newMember(inviter)));
            data.add(party);
        }
        if (party.members.size() >= MAX_MEMBERS) {
            error(actor, "That party is full (" + MAX_MEMBERS + " members).");
            return;
        }
        INVITES.removeIf(other -> other.target.equals(actor.getUUID()));
        party.members.add(newMember(actor));
        data.changed();
        announce(party, name(actor.getGameProfile().name()).append(plain(" joined the party.")));
        sync();
        com.skyrpg.core.event.TitleFlags.grant(actor, "party_joined");
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leader);
        if (leader != null) {
            com.skyrpg.core.event.TitleFlags.grant(leader, "party_joined");
            if (party.members.size() >= 4) {
                com.skyrpg.core.event.TitleFlags.grant(leader, "party_leader4");
            }
        }
    }

    public static void deny(ServerPlayer actor, String fromName) {
        expireInvites();
        Invite invite = pick(actor, fromName);
        if (invite == null) {
            error(actor, fromName == null || fromName.isEmpty() ? "You have no party invite." : "You have no invite from " + fromName + ".");
            return;
        }
        INVITES.remove(invite);
        say(actor, plain("Declined the invite from ").append(name(invite.fromName)).append(plain(".")));
        ServerPlayer from = server.getPlayerList().getPlayer(invite.from);
        if (from != null) {
            say(from, name(actor.getGameProfile().name()).append(plain(" declined your invite.")));
        }
        sync();
    }

    private static Member newMember(ServerPlayer player) {
        Member member = new Member(player.getUUID(), player.getGameProfile().name(), System.currentTimeMillis(), 0, 0, "", 0, 0, 0, 0);
        snapshot(member, player);
        return member;
    }

    private static void snapshot(Member member, ServerPlayer player) {
        member.name = player.getGameProfile().name();
        member.hp = (float) (player.getHealth() * StatTypes.HEALTH_SCALE);
        member.maxHp = (float) (player.getMaxHealth() * StatTypes.HEALTH_SCALE);
        member.dimension = player.level().dimension().identifier().toString();
        member.x = player.getBlockX();
        member.y = player.getBlockY();
        member.z = player.getBlockZ();
        member.lastSeen = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ leaving, kicking, leadership

    public static void leave(ServerPlayer actor) {
        Party party = partyOf(actor.getUUID());
        if (party == null) {
            error(actor, "You are not in a party.");
            return;
        }
        say(actor, plain("You left the party."));
        remove(party, actor.getUUID(), name(actor.getGameProfile().name()).append(plain(" left the party.")));
    }

    /** Takes a member out; the longest-standing member leads if the leader went; a party of one is disbanded. */
    private static void remove(Party party, UUID player, Component why) {
        PartyData data = data();
        party.members.removeIf(member -> member.id.equals(player));
        PARTY_CHAT.remove(player);
        data.changed();
        announce(party, why);
        if (party.members.size() < 2) {
            if (!party.members.isEmpty()) {
                announce(party, plain("The party was disbanded: nobody left to party with."));
            }
            data.remove(party);
        } else if (party.isLeader(player)) {
            promoteTo(party, party.members.getFirst());
        }
        sync();
    }

    private static void promoteTo(Party party, Member member) {
        party.leader = member.id;
        data().changed();
        announce(party, name(member.name).append(plain(" is now the party leader.")));
    }

    public static void kick(ServerPlayer actor, String targetName) {
        Party party = leading(actor);
        if (party == null) {
            return;
        }
        Member target = byName(party, targetName);
        if (target == null) {
            error(actor, targetName + " is not in your party.");
            return;
        }
        if (target.id.equals(actor.getUUID())) {
            error(actor, "You can't kick yourself. Use /party leave or /party disband.");
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target.id);
        if (online != null) {
            say(online, plain("You were kicked from the party."));
        }
        remove(party, target.id, name(target.name).append(plain(" was kicked from the party.")));
    }

    public static void promote(ServerPlayer actor, String targetName) {
        Party party = leading(actor);
        if (party == null) {
            return;
        }
        Member target = byName(party, targetName);
        if (target == null) {
            error(actor, targetName + " is not in your party.");
            return;
        }
        if (target.id.equals(actor.getUUID())) {
            error(actor, "You already lead the party.");
            return;
        }
        promoteTo(party, target);
        sync();
    }

    public static void disband(ServerPlayer actor) {
        Party party = leading(actor);
        if (party == null) {
            return;
        }
        announce(party, name(actor.getGameProfile().name()).append(plain(" disbanded the party.")));
        for (Member member : party.members) {
            PARTY_CHAT.remove(member.id);
        }
        data().remove(party);
        sync();
    }

    /** The party the player leads, or null after telling them why not. */
    private static Party leading(ServerPlayer actor) {
        Party party = partyOf(actor.getUUID());
        if (party == null) {
            error(actor, "You are not in a party.");
            return null;
        }
        if (!party.isLeader(actor.getUUID())) {
            error(actor, "Only the party leader can do that.");
            return null;
        }
        return party;
    }

    private static Member byName(Party party, String name) {
        for (Member member : party.members) {
            if (member.name.equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ chat and list

    public static void chat(ServerPlayer sender, String text) {
        Party party = partyOf(sender.getUUID());
        if (party == null) {
            error(sender, "You are not in a party.");
            return;
        }
        String who = sender.getGameProfile().name();
        MutableComponent header = Component.empty();
        if (party.isLeader(sender.getUUID())) {
            header.append(crown()).append(plain(" "));
        }
        header.append(name(who));
        header.append(Component.literal(": ").withStyle(Style.EMPTY.withColor(0x8D96A8).withBold(false)));
        Component line = Component.translatableWithFallback(CHAT_KEY, "%1$s%2$s", notice().build(header),
                Component.literal(text).withStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(false)), who);
        for (ServerPlayer member : online(party)) {
            member.sendSystemMessage(line);
        }
        SkyRpgParty.LOGGER.info("[Party] <{}> {}", who, text);
    }

    public static void toggleChat(ServerPlayer player) {
        if (partyOf(player.getUUID()) == null) {
            error(player, "You are not in a party.");
            return;
        }
        boolean on = PARTY_CHAT.add(player.getUUID());
        if (!on) {
            PARTY_CHAT.remove(player.getUUID());
        }
        say(player, plain(on ? "Your chat now goes to the party. Run " : "Your chat is public again. Run ")
                .append(Component.literal("/party chat").withStyle(Style.EMPTY.withColor(COLOR)))
                .append(plain(on ? " to switch back." : " to talk to the party.")));
        sync();
    }

    public static void list(ServerPlayer actor) {
        Party party = partyOf(actor.getUUID());
        if (party == null) {
            error(actor, "You are not in a party. Invite someone with /party invite <player>.");
            return;
        }
        say(actor, plain("Party members (" + party.members.size() + "/" + MAX_MEMBERS + "):"));
        for (Member member : party.members) {
            boolean online = server.getPlayerList().getPlayer(member.id) != null;
            MutableComponent line = Component.literal("   ");
            if (party.isLeader(member.id)) {
                line.append(crown()).append(plain(" "));
            }
            line.append(Component.literal(member.name).withStyle(Style.EMPTY.withColor(online ? 0xFFFFFF : 0x8D96A8).withBold(online)));
            line.append(Component.literal(online ? "  online" : "  offline").withStyle(Style.EMPTY.withColor(online ? 0x55DD77 : 0x6B7385)));
            actor.sendSystemMessage(wrap(line));
        }
    }

    // ------------------------------------------------------------------ actions from the K menu

    static void action(ServerPlayer player, String action, String arg) {
        String name = arg == null ? "" : arg.trim();
        switch (action) {
            case "invite" -> {
                ServerPlayer target = name.isEmpty() ? null : server.getPlayerList().getPlayerByName(name);
                if (target == null) {
                    error(player, name.isEmpty() ? "Type a player name first." : name + " is not online.");
                } else {
                    invite(player, target);
                }
            }
            case "accept" -> accept(player, name);
            case "deny" -> deny(player, name);
            case "kick" -> kick(player, name);
            case "promote" -> promote(player, name);
            case "leave" -> leave(player);
            case "disband" -> disband(player);
            case "chat" -> toggleChat(player);
            case "track" -> track(player, name);
            default -> { }
        }
    }

    private static void track(ServerPlayer actor, String targetName) {
        Party party = partyOf(actor.getUUID());
        Member member = party == null ? null : byName(party, targetName);
        if (member == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(member.id);
        if (online != null) {
            snapshot(member, online);
        } else if (member.dimension.isEmpty()) {
            error(actor, member.name + " has not been seen yet.");
            return;
        }
        Identifier dimension = Identifier.tryParse(member.dimension);
        WaypointTarget.send(actor, member.name, new Vec3(member.x + 0.5, member.y, member.z + 0.5), 0xFF000000 | COLOR, dimension);
        say(actor, plain("Tracking ").append(name(member.name)).append(plain(online == null ? " (where they were last seen)." : ".")));
    }

    // ------------------------------------------------------------------ login, logout, sync

    private static void onJoin(ServerPlayer player) {
        if (server == null) {
            return;
        }
        Party party = partyOf(player.getUUID());
        if (party == null) {
            return;
        }
        snapshot(party.member(player.getUUID()), player);
        data().changed();
        String me = player.getGameProfile().name();
        List<String> mates = new ArrayList<>();
        for (Member m : party.members) {
            if (!m.id.equals(player.getUUID())) {
                mates.add(m.name);
            }
        }
        for (ServerPlayer other : online(party)) {
            if (other != player) {
                say(other, name(me).append(Component.literal(" is online.").withStyle(Style.EMPTY.withColor(0x55DD77))));
            }
        }
        say(player, plain("You are in a party with " + String.join(", ", mates) + "."));
        sync();
    }

    private static void onLeaveServer(ServerPlayer player) {
        PARTY_CHAT.remove(player.getUUID());
        SYNCED.remove(player.getUUID());
        if (server == null) {
            return;
        }
        Party party = partyOf(player.getUUID());
        if (party == null) {
            return;
        }
        snapshot(party.member(player.getUUID()), player);
        data().changed();
        String me = player.getGameProfile().name();
        for (ServerPlayer other : online(party)) {
            if (other != player) {
                say(other, name(me).append(Component.literal(" went offline.").withStyle(Style.EMPTY.withColor(0x8D96A8))));
            }
        }
    }

    private static void tick(MinecraftServer s) {
        if (++tickCounter % SYNC_TICKS == 0) {
            expireInvites();
            sync();
        }
    }

    /** Sends every player with a party or a pending invite their state (and one empty state to those who just lost theirs). */
    private static void sync() {
        if (server == null) {
            return;
        }
        PartyData data = data();
        Map<Party, List<PartyPayloads.MemberInfo>> infos = new HashMap<>();
        long time = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Party party = data.of(player.getUUID());
            List<PartyPayloads.InviteInfo> invites = new ArrayList<>();
            for (Invite invite : INVITES) {
                if (invite.target.equals(player.getUUID())) {
                    invites.add(new PartyPayloads.InviteInfo(invite.from, invite.fromName, (int) Math.max(0, (invite.expires - time + 999) / 1000)));
                }
            }
            if (!ServerPlayNetworking.canSend(player, PartyPayloads.State.TYPE)) {
                continue;
            }
            if (party == null && invites.isEmpty()) {
                if (SYNCED.remove(player.getUUID())) {
                    ServerPlayNetworking.send(player, PartyPayloads.State.EMPTY);
                }
                continue;
            }
            List<PartyPayloads.MemberInfo> members = List.of();
            UUID leader = new UUID(0, 0);
            if (party != null) {
                members = infos.computeIfAbsent(party, Parties::infos);
                leader = party.leader;
            }
            ServerPlayNetworking.send(player, new PartyPayloads.State(leader, MAX_MEMBERS, PARTY_CHAT.contains(player.getUUID()), members, invites));
            SYNCED.add(player.getUUID());
        }
    }

    private static List<PartyPayloads.MemberInfo> infos(Party party) {
        List<PartyPayloads.MemberInfo> list = new ArrayList<>();
        for (Member member : party.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player != null) {
                snapshot(member, player);
            }
            list.add(new PartyPayloads.MemberInfo(member.id, member.name, player != null, member.hp, member.maxHp, member.dimension, member.x, member.y, member.z));
        }
        return list;
    }

    /** Op command output: every party, its leader and members with online state and last known place. Goes to the command source (RCON too). */
    static int adminList(net.minecraft.commands.CommandSourceStack source) {
        PartyData data = data();
        source.sendSuccess(() -> Component.literal(data.parties.size() + " part" + (data.parties.size() == 1 ? "y" : "ies")), false);
        for (Party party : data.parties) {
            StringBuilder line = new StringBuilder("party " + party.id.toString().substring(0, 8) + ":");
            for (Member member : party.members) {
                boolean online = server.getPlayerList().getPlayer(member.id) != null;
                line.append(' ').append(member.name).append(party.isLeader(member.id) ? "(leader)" : "").append(online ? "[online]" : "[offline]")
                        .append(String.format(java.util.Locale.ROOT, "{%.0f/%.0f %s %d,%d,%d}", member.hp, member.maxHp, member.dimension, member.x, member.y, member.z));
            }
            source.sendSuccess(() -> Component.literal(line.toString()), false);
        }
        return data.parties.size();
    }

    /** Pending invite names for this player, for command suggestions. */
    static List<String> inviteNames(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        for (Invite invite : INVITES) {
            if (invite.target.equals(player.getUUID())) {
                names.add(invite.fromName);
            }
        }
        return names;
    }

    /** Member names of the player's party (not counting them), for command suggestions. */
    static List<String> memberNames(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        Party party = partyOf(player.getUUID());
        if (party != null) {
            for (Member member : party.members) {
                if (!member.id.equals(player.getUUID())) {
                    names.add(member.name);
                }
            }
        }
        return names;
    }
}
