package com.skyrpg.progress.quests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.progress.SkyRpgProgress;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * A player's questline progress, saved with the player and synced to them: the quests done, the quests they've been told
 * about, a few counters the event hooks bump (kills, bench crafts, talks, slayer tiers), and the tracked quest.
 *
 * <p>Counter keys: {@code k:<quest>#<n>} kills for an objective while its quest is active, {@code c:<item>} Artisan Bench
 * crafts, {@code t:<npc>} talks, {@code s:<line>} (and {@code s:any}) the highest slayer tier finished, {@code p:<line>} the
 * highest started, {@code v:<quest>#<n>} a visit made, {@code journal} the Journal opened.
 *
 * <p>Quest flow keys: {@code a:<quest>} accepted (offered by its NPC, or started by itself), {@code r:<quest>} ready to
 * hand in, {@code x:<quest>} abandoned, {@code t:<quest>#<n>} the talk objective's dialogue was finished, {@code qv} the
 * questline version the state was migrated to.
 */
public record QuestState(Set<String> done, Set<String> seen, Map<String, Integer> counters, String tracked) {
    public static final QuestState EMPTY = new QuestState(Set.of(), Set.of(), Map.of(), "");

    public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(s -> List.copyOf(s.done)),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(s -> List.copyOf(s.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(QuestState::counters),
            Codec.STRING.optionalFieldOf("tracked", "").forGetter(QuestState::tracked)).apply(i,
            (done, seen, counters, tracked) -> new QuestState(Set.copyOf(done), Set.copyOf(seen), Map.copyOf(counters), tracked)));

    public static final StreamCodec<ByteBuf, QuestState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), s -> List.copyOf(s.done),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), s -> List.copyOf(s.seen),
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), QuestState::counters,
            ByteBufCodecs.STRING_UTF8, QuestState::tracked,
            (done, seen, counters, tracked) -> new QuestState(Set.copyOf(done), Set.copyOf(seen), Map.copyOf(counters), tracked));

    /** The player's saved progress. */
    public static final AttachmentType<QuestState> STATE = AttachmentRegistry.create(SkyRpgProgress.id("quests"), builder -> builder
            .persistent(CODEC)
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    /** Where each active objective stands ({@code <quest>#<n>} to its value), recomputed by the server and synced; not saved. */
    public static final AttachmentType<Map<String, Integer>> LIVE = AttachmentRegistry.create(SkyRpgProgress.id("quest_live"), builder -> builder
            .initializer(Map::of)
            .syncWith(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).map(Map::copyOf, HashMap::new),
                    AttachmentSyncPredicate.targetOnly()));

    /**
     * Registers the attachments. Must run during mod init on both sides: a client tells the server which synced
     * attachments it knows while connecting, and one it doesn't know yet is never sent to it (the Journal then stays empty).
     */
    public static void init() {
        // Touching the class creates STATE and LIVE above.
    }

    public static QuestState of(Player player) {
        return player.getAttachedOrElse(STATE, EMPTY);
    }

    public static Map<String, Integer> live(Player player) {
        return player.getAttachedOrElse(LIVE, Map.of());
    }

    public boolean isDone(String quest) {
        return done.contains(quest);
    }

    public int counter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public QuestState withCounter(String key, int value) {
        Map<String, Integer> next = new HashMap<>(counters);
        next.put(key, value);
        return new QuestState(done, seen, Map.copyOf(next), tracked);
    }

    public QuestState withDone(String quest) {
        Set<String> next = new HashSet<>(done);
        next.add(quest);
        return new QuestState(Set.copyOf(next), seen, counters, tracked);
    }

    public QuestState withSeen(String quest) {
        Set<String> next = new HashSet<>(seen);
        next.add(quest);
        return new QuestState(done, Set.copyOf(next), counters, tracked);
    }

    public QuestState withTracked(String quest) {
        return new QuestState(done, seen, counters, quest);
    }

    /** Wipes one quest's progress (done flag, kill and visit counters) for {@code /quest reset}. */
    public QuestState without(String quest) {
        Set<String> nextDone = new HashSet<>(done);
        nextDone.remove(quest);
        Set<String> nextSeen = new HashSet<>(seen);
        nextSeen.remove(quest);
        Map<String, Integer> nextCounters = new HashMap<>(counters);
        nextCounters.keySet().removeIf(key -> key.contains(":" + quest + "#") || key.equals("a:" + quest) || key.equals("r:" + quest)
                || key.equals("x:" + quest));
        return new QuestState(Set.copyOf(nextDone), Set.copyOf(nextSeen), Map.copyOf(nextCounters), tracked.equals(quest) ? "" : tracked);
    }
}
