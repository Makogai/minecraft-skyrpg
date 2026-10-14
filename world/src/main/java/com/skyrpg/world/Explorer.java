package com.skyrpg.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * What a player has found in the world: dungeons (shown on their map), bosses they've cleared at least once, secrets,
 * and the waystones they're attuned to. Saved with the player and synced to them (their map draws from it).
 */
public final class Explorer {
    /** A dungeon entrance the player has found. */
    public record Found(String dungeon, String dimension, long pos, String name, String icon, int color) {
        static final Codec<Found> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dungeon").forGetter(Found::dungeon), Codec.STRING.fieldOf("dimension").forGetter(Found::dimension),
                Codec.LONG.fieldOf("pos").forGetter(Found::pos), Codec.STRING.optionalFieldOf("name", "Dungeon").forGetter(Found::name),
                Codec.STRING.optionalFieldOf("icon", "minecraft:compass").forGetter(Found::icon),
                Codec.INT.optionalFieldOf("color", 0xFFB98CFF).forGetter(Found::color)).apply(i, Found::new));
        static final StreamCodec<ByteBuf, Found> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Found::dungeon,
                ByteBufCodecs.STRING_UTF8, Found::dimension, ByteBufCodecs.VAR_LONG, Found::pos, ByteBufCodecs.STRING_UTF8, Found::name,
                ByteBufCodecs.STRING_UTF8, Found::icon, ByteBufCodecs.INT, Found::color, Found::new);

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    /** A waystone the player is attuned to, with the name it had when they last saw the list. */
    public record Attuned(String dimension, long pos, String name) {
        static final Codec<Attuned> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Attuned::dimension), Codec.LONG.fieldOf("pos").forGetter(Attuned::pos),
                Codec.STRING.fieldOf("name").forGetter(Attuned::name)).apply(i, Attuned::new));
        static final StreamCodec<ByteBuf, Attuned> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Attuned::dimension,
                ByteBufCodecs.VAR_LONG, Attuned::pos, ByteBufCodecs.STRING_UTF8, Attuned::name, Attuned::new);

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }

        boolean at(String dim, BlockPos where) {
            return dimension.equals(dim) && pos == where.asLong();
        }
    }

    public record State(List<Found> dungeons, List<String> cleared, List<String> secrets, List<Attuned> waystones) {
        public static final State EMPTY = new State(List.of(), List.of(), List.of(), List.of());
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Found.CODEC.listOf().optionalFieldOf("dungeons", List.of()).forGetter(State::dungeons),
                Codec.STRING.listOf().optionalFieldOf("cleared", List.of()).forGetter(State::cleared),
                Codec.STRING.listOf().optionalFieldOf("secrets", List.of()).forGetter(State::secrets),
                Attuned.CODEC.listOf().optionalFieldOf("waystones", List.of()).forGetter(State::waystones)).apply(i, State::new));
        static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                Found.STREAM_CODEC.apply(ByteBufCodecs.list()), State::dungeons,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), State::cleared,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), State::secrets,
                Attuned.STREAM_CODEC.apply(ByteBufCodecs.list()), State::waystones, State::new);

        public boolean found(String dungeon, String dimension, BlockPos pos) {
            return dungeons.stream().anyMatch(f -> f.dungeon().equals(dungeon) && f.dimension().equals(dimension)
                    && BlockPos.of(f.pos()).closerThan(pos, 64));
        }

        public boolean attuned(String dimension, BlockPos pos) {
            return waystones.stream().anyMatch(w -> w.at(dimension, pos));
        }
    }

    public static final AttachmentType<State> STATE = AttachmentRegistry.create(SkyRpgWorld.id("explorer"), builder -> builder
            .persistent(State.CODEC)
            .syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    private static final long SECRET_COINS = 250;

    private Explorer() {
    }

    static void init() {
        // Loading the class registers the attachment on both sides.
    }

    public static State state(Player player) {
        return player.getAttachedOrElse(STATE, State.EMPTY);
    }

    /** A player walked up to a dungeon's entrance: the first time, a banner and a map marker. */
    static void discover(ServerPlayer player, Dungeons.Dungeon dungeon, BlockPos pos) {
        String dimension = player.level().dimension().identifier().toString();
        State state = state(player);
        if (state.found(dungeon.id().toString(), dimension, pos)) {
            return;
        }
        List<Found> next = new ArrayList<>(state.dungeons());
        next.add(new Found(dungeon.id().toString(), dimension, pos.asLong(), dungeon.name(), dungeon.icon().toString(), dungeon.color()));
        player.setAttached(STATE, new State(List.copyOf(next), state.cleared(), state.secrets(), state.waystones()));
        WorldPayloads.banner(player, dungeon.icon(), "DUNGEON DISCOVERED", Component.literal(dungeon.name()),
                Component.literal("Level " + dungeon.level() + (dungeon.description().isEmpty() ? "" : " · " + dungeon.description())), dungeon.color());
        DungeonRuns.delving(player, 100 + 5.0 * dungeon.level());
        Msg.playSound(player, SoundEvents.AMBIENT_CAVE.value(), 1.0f, 0.8f);
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 0.6f);
    }

    /** Records a boss kill; true the first time this player kills this boss. */
    static boolean clearBoss(ServerPlayer player, Identifier boss) {
        State state = state(player);
        if (state.cleared().contains(boss.toString())) {
            return false;
        }
        List<String> next = new ArrayList<>(state.cleared());
        next.add(boss.toString());
        player.setAttached(STATE, new State(state.dungeons(), List.copyOf(next), state.secrets(), state.waystones()));
        return true;
    }

    /** A player found a secret spot: the first time, a few coins and a banner. */
    static void secret(ServerPlayer player, String key, String name) {
        State state = state(player);
        if (state.secrets().contains(key)) {
            return;
        }
        List<String> next = new ArrayList<>(state.secrets());
        next.add(key);
        player.setAttached(STATE, new State(state.dungeons(), state.cleared(), List.copyOf(next), state.waystones()));
        Wallet.give(player, SECRET_COINS);
        DungeonRuns.delving(player, 150);
        WorldPayloads.banner(player, Identifier.withDefaultNamespace("spyglass"), "SECRET FOUND", Component.literal(name),
                Component.literal("+").append(Msg.coins(SECRET_COINS)), 0xFFF2B33D);
        Notice.of(Notice.Kind.INFO).icon(Notice.SPARKLE).tag("SECRET FOUND").color(0xFFC940).send(player,
                Component.literal(name).withStyle(Style.EMPTY.withBold(false).withColor(ChatFormatting.WHITE))
                        .append(Component.literal(" · +").withStyle(ChatFormatting.GRAY)).append(Msg.coins(SECRET_COINS)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    static void attune(ServerPlayer player, String dimension, BlockPos pos, String name) {
        State state = state(player);
        if (state.attuned(dimension, pos)) {
            return;
        }
        List<Attuned> next = new ArrayList<>(state.waystones());
        next.add(new Attuned(dimension, pos.asLong(), name));
        player.setAttached(STATE, new State(state.dungeons(), state.cleared(), state.secrets(), List.copyOf(next)));
    }

    /** Replaces the player's waystone list (refreshed names, destroyed waystones dropped). */
    static void setWaystones(ServerPlayer player, List<Attuned> waystones) {
        State state = state(player);
        if (!state.waystones().equals(waystones)) {
            player.setAttached(STATE, new State(state.dungeons(), state.cleared(), state.secrets(), List.copyOf(waystones)));
        }
    }

    static void reset(ServerPlayer player) {
        player.removeAttached(STATE);
    }
}
