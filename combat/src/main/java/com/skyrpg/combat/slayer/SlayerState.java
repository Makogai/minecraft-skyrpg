package com.skyrpg.combat.slayer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's slayer progress: XP in each line, and the quest they're on (if any). Saved with the player and synced to
 * them, so the client knows its slayer levels (for recipe requirements and the HUD).
 */
public record SlayerState(Map<String, Double> xp, Optional<Quest> quest, Map<String, Integer> kills, Map<String, Integer> best,
                          Map<String, Integer> tokens) {
    public static final SlayerState EMPTY = new SlayerState(Map.of(), Optional.empty(), Map.of(), Map.of(), Map.of());

    public SlayerState(Map<String, Double> xp, Optional<Quest> quest) {
        this(xp, quest, Map.of(), Map.of(), Map.of());
    }

    /** The quest on hand: kills so far towards the bar, and whether its boss is up. */
    public record Quest(String line, int tier, double progress, boolean bossUp) {
        public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("line").forGetter(Quest::line),
                Codec.INT.fieldOf("tier").forGetter(Quest::tier),
                Codec.DOUBLE.fieldOf("progress").forGetter(Quest::progress),
                Codec.BOOL.optionalFieldOf("boss_up", false).forGetter(Quest::bossUp)).apply(instance, Quest::new));
        public static final StreamCodec<ByteBuf, Quest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Quest::line, ByteBufCodecs.VAR_INT, Quest::tier, ByteBufCodecs.DOUBLE, Quest::progress,
                ByteBufCodecs.BOOL, Quest::bossUp, Quest::new);
    }

    public static final Codec<SlayerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("xp", Map.of()).forGetter(SlayerState::xp),
            Quest.CODEC.optionalFieldOf("quest").forGetter(SlayerState::quest),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("kills", Map.of()).forGetter(SlayerState::kills),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("best", Map.of()).forGetter(SlayerState::best),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tokens", Map.of()).forGetter(SlayerState::tokens))
            .apply(instance, SlayerState::new));
    public static final StreamCodec<ByteBuf, SlayerState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE), SlayerState::xp,
            ByteBufCodecs.optional(Quest.STREAM_CODEC), SlayerState::quest,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SlayerState::kills,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SlayerState::best,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SlayerState::tokens, SlayerState::new);

    /** The key of a line's tier in {@link #kills} and {@link #best}. */
    public static String key(String line, int tier) {
        return line + ":" + tier;
    }

    /** Bosses of this line and tier slain. */
    public int kills(String line, int tier) {
        return kills.getOrDefault(key(line, tier), 0);
    }

    /** The fastest kill of this line and tier in ticks, or 0 if none yet. */
    public int best(String line, int tier) {
        return best.getOrDefault(key(line, tier), 0);
    }

    /** Boss tokens earned from a line. */
    public int tokens(String line) {
        return tokens.getOrDefault(line, 0);
    }

    /** A boss fell after {@code ticks} ticks, dropping {@code tokenCount} tokens. */
    public SlayerState withKill(String line, int tier, int ticks, int tokenCount) {
        Map<String, Integer> nextKills = new HashMap<>(kills);
        nextKills.merge(key(line, tier), 1, Integer::sum);
        Map<String, Integer> nextBest = new HashMap<>(best);
        nextBest.merge(key(line, tier), Math.max(1, ticks), Math::min);
        Map<String, Integer> nextTokens = new HashMap<>(tokens);
        nextTokens.merge(line, tokenCount, Integer::sum);
        return new SlayerState(xp, quest, Map.copyOf(nextKills), Map.copyOf(nextBest), Map.copyOf(nextTokens));
    }

    public double xp(String line) {
        return xp.getOrDefault(line, 0.0);
    }

    public SlayerState withQuest(Quest quest) {
        return new SlayerState(xp, Optional.ofNullable(quest), kills, best, tokens);
    }

    public SlayerState addXp(String line, double amount) {
        Map<String, Double> next = new HashMap<>(xp);
        next.merge(line, amount, Double::sum);
        return new SlayerState(Map.copyOf(next), quest, kills, best, tokens);
    }
}
