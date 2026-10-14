package com.skyrpg.progress.titles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.progress.SkyRpgProgress;
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
 * A player's earned titles, saved with the player and synced to them: the ids unlocked (permanent), the one equipped, the
 * flags other mods granted ({@code TitleFlags}) and the counters the title hooks keep ({@code boss:<id>} and
 * {@code kind:<KIND>} kills, Blood Moon seconds and kills). The client only needs the unlocked ids and the equipped one.
 */
public record TitleState(Set<String> unlocked, String active, Set<String> flags, Map<String, Integer> counters) {
    public static final TitleState EMPTY = new TitleState(Set.of(), "", Set.of(), Map.of());

    public static final Codec<TitleState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(s -> List.copyOf(s.unlocked)),
            Codec.STRING.optionalFieldOf("active", "").forGetter(TitleState::active),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(s -> List.copyOf(s.flags)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(TitleState::counters))
            .apply(i, (unlocked, active, flags, counters) -> new TitleState(Set.copyOf(unlocked), active, Set.copyOf(flags), Map.copyOf(counters))));

    private static final StreamCodec<io.netty.buffer.ByteBuf, TitleState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), s -> List.copyOf(s.unlocked),
            ByteBufCodecs.STRING_UTF8, TitleState::active,
            (unlocked, active) -> new TitleState(Set.copyOf(unlocked), active, Set.of(), Map.of()));

    public static final AttachmentType<TitleState> STATE = AttachmentRegistry.create(SkyRpgProgress.id("titles"), builder -> builder
            .persistent(CODEC)
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    /** What each locked title is at ({@code <id>} current, {@code <id>#} the goal), for the K page's bars. Recomputed by the server, not saved. */
    public static final AttachmentType<Map<String, Integer>> LIVE = AttachmentRegistry.create(SkyRpgProgress.id("title_live"), builder -> builder
            .initializer(Map::of)
            .syncWith(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).map(Map::copyOf, HashMap::new),
                    AttachmentSyncPredicate.targetOnly()));

    /** Loads the class so the attachments are registered on both sides during mod init (the client needs them before the first sync). */
    public static void touch() {
    }

    public static TitleState of(Player player) {
        return player.getAttachedOrElse(STATE, EMPTY);
    }

    public int counter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public TitleState withCounter(String key, int value) {
        Map<String, Integer> next = new HashMap<>(counters);
        next.put(key, value);
        return new TitleState(unlocked, active, flags, Map.copyOf(next));
    }

    public TitleState withFlag(String flag) {
        Set<String> next = new HashSet<>(flags);
        next.add(flag);
        return new TitleState(unlocked, active, Set.copyOf(next), counters);
    }

    public TitleState withUnlocked(Set<String> add) {
        Set<String> next = new HashSet<>(unlocked);
        next.addAll(add);
        return new TitleState(Set.copyOf(next), active, flags, counters);
    }

    public TitleState without(Set<String> remove) {
        Set<String> next = new HashSet<>(unlocked);
        next.removeAll(remove);
        return new TitleState(Set.copyOf(next), remove.contains(active) ? "" : active, flags, counters);
    }

    public TitleState withActive(String id) {
        return new TitleState(unlocked, id, flags, counters);
    }
}
