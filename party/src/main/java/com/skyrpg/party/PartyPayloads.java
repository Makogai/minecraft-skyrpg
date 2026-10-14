package com.skyrpg.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The party on the wire. {@link State} goes to every member (and to anyone with a pending invite) about twice a second:
 * who is in the party and how they are doing. {@link Action} is what the K menu page asks for; the server checks it all.
 */
public final class PartyPayloads {
    private PartyPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) ->
                context.server().execute(() -> Parties.action(context.player(), payload.action(), payload.arg())));
    }

    /** One member as the client sees them. {@code hp} and {@code maxHp} are SkyRPG health (vanilla health times the stat scale). */
    public record MemberInfo(UUID id, String name, boolean online, float hp, float maxHp, String dimension, int x, int y, int z) {
        static void write(RegistryFriendlyByteBuf buf, MemberInfo m) {
            buf.writeUUID(m.id);
            buf.writeUtf(m.name, 32);
            buf.writeBoolean(m.online);
            buf.writeFloat(m.hp);
            buf.writeFloat(m.maxHp);
            buf.writeUtf(m.dimension, 96);
            buf.writeVarInt(m.x);
            buf.writeVarInt(m.y);
            buf.writeVarInt(m.z);
        }

        static MemberInfo read(RegistryFriendlyByteBuf buf) {
            return new MemberInfo(buf.readUUID(), buf.readUtf(32), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readUtf(96),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** A pending invite for the receiving player: who sent it and how many seconds are left. */
    public record InviteInfo(UUID from, String fromName, int secondsLeft) {
    }

    /**
     * The receiver's party (empty member list = not in one), the leader's id, the party's size limit, and the receiver's
     * pending invites.
     */
    public record State(UUID leader, int capacity, boolean chatOn, List<MemberInfo> members, List<InviteInfo> invites) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(SkyRpgParty.id("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(State::write, State::read);
        public static final State EMPTY = new State(new UUID(0, 0), Parties.MAX_MEMBERS, false, List.of(), List.of());

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeUUID(state.leader);
            buf.writeVarInt(state.capacity);
            buf.writeBoolean(state.chatOn);
            buf.writeVarInt(state.members.size());
            state.members.forEach(m -> MemberInfo.write(buf, m));
            buf.writeVarInt(state.invites.size());
            for (InviteInfo invite : state.invites) {
                buf.writeUUID(invite.from);
                buf.writeUtf(invite.fromName, 32);
                buf.writeVarInt(invite.secondsLeft);
            }
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            UUID leader = buf.readUUID();
            int capacity = buf.readVarInt();
            boolean chatOn = buf.readBoolean();
            int count = buf.readVarInt();
            List<MemberInfo> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                members.add(MemberInfo.read(buf));
            }
            int invites = buf.readVarInt();
            List<InviteInfo> list = new ArrayList<>(invites);
            for (int i = 0; i < invites; i++) {
                list.add(new InviteInfo(buf.readUUID(), buf.readUtf(32), buf.readVarInt()));
            }
            return new State(leader, capacity, chatOn, members, list);
        }

        public boolean inParty() {
            return !members.isEmpty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A button on the K menu page. {@code action}: invite, accept, deny, kick, promote, leave, disband, track, chat. {@code arg} is a player
     * name (or empty).
     */
    public record Action(String action, String arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(SkyRpgParty.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action, ByteBufCodecs.STRING_UTF8, Action::arg, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
