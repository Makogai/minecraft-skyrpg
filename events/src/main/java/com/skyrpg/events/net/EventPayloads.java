package com.skyrpg.events.net;

import com.skyrpg.events.SkyRpgEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Everything the events mod sends to clients (server → client only; the client never asks):
 * <ul>
 *   <li>{@link State}: every running event with its clock, top 10 and your own score. Sent when
 *       scores change (at most every few ticks) and once a second.</li>
 *   <li>{@link Results}: the podium when an event ends, for the winner board.</li>
 *   <li>{@link Announce}: a banner (and sound) from /announce or an event.</li>
 *   <li>{@link ShowBoard}: the custom sidebar from /board, or {@code visible = false} to hide it.</li>
 * </ul>
 * Times are {@link System#currentTimeMillis()} on the server; {@link State#serverNow} lets the client
 * work out its clock offset.
 */
public final class EventPayloads {
    private EventPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Results.TYPE, Results.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Announce.TYPE, Announce.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowBoard.TYPE, ShowBoard.CODEC);
    }

    /** A leaderboard row. {@code place} 0 = no points yet. */
    public record Entry(UUID player, String name, int score, int place) {
        static void write(RegistryFriendlyByteBuf buf, Entry entry) {
            buf.writeUUID(entry.player);
            buf.writeUtf(entry.name);
            buf.writeVarInt(entry.score);
            buf.writeVarInt(entry.place);
        }

        static Entry read(RegistryFriendlyByteBuf buf) {
            return new Entry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /**
     * One running event as one player sees it.
     *
     * @param beginsAt  when the clock starts; before that it's in its join window
     * @param optIn     players must /event join to score; {@code chatJoin}: typing .join works too
     * @param joined    this player can score (always true for everyone-events)
     * @param top       the best 10, best first
     * @param selfScore this player's score; {@code selfPlace} 0 = unranked
     */
    public record EventView(String id, String name, String description, int color, Identifier icon, String unit,
                            long beginsAt, long endsAt, boolean optIn, boolean chatJoin, boolean joined, int participants,
                            List<Entry> top, int selfScore, int selfPlace) {
        static void write(RegistryFriendlyByteBuf buf, EventView view) {
            buf.writeUtf(view.id);
            buf.writeUtf(view.name);
            buf.writeUtf(view.description);
            buf.writeInt(view.color);
            buf.writeIdentifier(view.icon);
            buf.writeUtf(view.unit);
            buf.writeLong(view.beginsAt);
            buf.writeLong(view.endsAt);
            buf.writeBoolean(view.optIn);
            buf.writeBoolean(view.chatJoin);
            buf.writeBoolean(view.joined);
            buf.writeVarInt(view.participants);
            buf.writeVarInt(view.top.size());
            view.top.forEach(entry -> Entry.write(buf, entry));
            buf.writeVarInt(view.selfScore);
            buf.writeVarInt(view.selfPlace);
        }

        static EventView read(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String description = buf.readUtf();
            int color = buf.readInt();
            Identifier icon = buf.readIdentifier();
            String unit = buf.readUtf();
            long beginsAt = buf.readLong();
            long endsAt = buf.readLong();
            boolean optIn = buf.readBoolean();
            boolean chatJoin = buf.readBoolean();
            boolean joined = buf.readBoolean();
            int participants = buf.readVarInt();
            int count = buf.readVarInt();
            List<Entry> top = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                top.add(Entry.read(buf));
            }
            return new EventView(id, name, description, color, icon, unit, beginsAt, endsAt, optIn, chatJoin, joined, participants,
                    top, buf.readVarInt(), buf.readVarInt());
        }
    }

    /** All running events, soonest-ending first. Empty = none running. */
    public record State(long serverNow, List<EventView> events) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(SkyRpgEvents.id("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(State::write, State::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeLong(serverNow);
            buf.writeVarInt(events.size());
            events.forEach(view -> EventView.write(buf, view));
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            long serverNow = buf.readLong();
            int count = buf.readVarInt();
            List<EventView> events = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                events.add(EventView.read(buf));
            }
            return new State(serverNow, events);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One player on the podium. Several players can share a place. */
    public record Placing(int place, UUID player, String name, int score, String prize) {
        static void write(RegistryFriendlyByteBuf buf, Placing placing) {
            buf.writeVarInt(placing.place);
            buf.writeUUID(placing.player);
            buf.writeUtf(placing.name);
            buf.writeVarInt(placing.score);
            buf.writeUtf(placing.prize);
        }

        static Placing read(RegistryFriendlyByteBuf buf) {
            return new Placing(buf.readVarInt(), buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readUtf());
        }
    }

    /**
     * An event just ended with prizes handed out.
     *
     * @param podium    everyone placed 1st to 3rd (ties included), best first
     * @param selfPlace this player's place, 0 = didn't score; {@code selfPrize} what they won ("" = nothing)
     */
    public record Results(String id, String name, int color, Identifier icon, String unit, int participants,
                          List<Placing> podium, int selfPlace, int selfScore, String selfPrize) implements CustomPacketPayload {
        public static final Type<Results> TYPE = new Type<>(SkyRpgEvents.id("results"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Results> CODEC = CustomPacketPayload.codec(Results::write, Results::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(name);
            buf.writeInt(color);
            buf.writeIdentifier(icon);
            buf.writeUtf(unit);
            buf.writeVarInt(participants);
            buf.writeVarInt(podium.size());
            podium.forEach(placing -> Placing.write(buf, placing));
            buf.writeVarInt(selfPlace);
            buf.writeVarInt(selfScore);
            buf.writeUtf(selfPrize);
        }

        private static Results read(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            int color = buf.readInt();
            Identifier icon = buf.readIdentifier();
            String unit = buf.readUtf();
            int participants = buf.readVarInt();
            int count = buf.readVarInt();
            List<Placing> podium = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                podium.add(Placing.read(buf));
            }
            return new Results(id, name, color, icon, unit, participants, podium, buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A banner at the top of the screen. {@code sound} empty = silent. */
    public record Announce(String label, Component title, Component subtitle, int color, Identifier icon,
                           Optional<Identifier> sound, float volume, float pitch) implements CustomPacketPayload {
        public static final Type<Announce> TYPE = new Type<>(SkyRpgEvents.id("announce"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Announce> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Announce::label,
                ComponentSerialization.STREAM_CODEC, Announce::title,
                ComponentSerialization.STREAM_CODEC, Announce::subtitle,
                ByteBufCodecs.INT, Announce::color,
                Identifier.STREAM_CODEC, Announce::icon,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Announce::sound,
                ByteBufCodecs.FLOAT, Announce::volume,
                ByteBufCodecs.FLOAT, Announce::pitch,
                Announce::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BoardLine(Component left, Component right) {
        static final StreamCodec<RegistryFriendlyByteBuf, BoardLine> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, BoardLine::left,
                ComponentSerialization.STREAM_CODEC, BoardLine::right,
                BoardLine::new);
    }

    /** The /board sidebar. With {@code visible = false} the rest is ignored and the board hides. */
    public record ShowBoard(boolean visible, Component title, int color, List<BoardLine> lines, Component footer) implements CustomPacketPayload {
        public static final Type<ShowBoard> TYPE = new Type<>(SkyRpgEvents.id("board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowBoard> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ShowBoard::visible,
                ComponentSerialization.STREAM_CODEC, ShowBoard::title,
                ByteBufCodecs.INT, ShowBoard::color,
                BoardLine.CODEC.apply(ByteBufCodecs.list()), ShowBoard::lines,
                ComponentSerialization.STREAM_CODEC, ShowBoard::footer,
                ShowBoard::new);

        public static ShowBoard hidden() {
            return new ShowBoard(false, Component.empty(), 0, List.of(), Component.empty());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
