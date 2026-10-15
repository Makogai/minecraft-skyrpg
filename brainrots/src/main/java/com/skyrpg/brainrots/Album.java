package com.skyrpg.brainrots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's brainrot album: how many of each kind they've caught, and when they caught the first one (epoch millis).
 * Saved with the player, kept on death, and synced to its owner for the album page.
 */
public record Album(Map<String, Integer> caught, Map<String, Long> first) {
    public static final Album EMPTY = new Album(Map.of(), Map.of());

    static final Codec<Album> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("caught", Map.of()).forGetter(Album::caught),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("first", Map.of()).forGetter(Album::first)).apply(i, Album::new));
    static final StreamCodec<ByteBuf, Album> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), a -> new HashMap<>(a.caught()),
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG), a -> new HashMap<>(a.first()),
            (caught, first) -> new Album(Map.copyOf(caught), Map.copyOf(first)));

    public static final AttachmentType<Album> ATTACHMENT = AttachmentRegistry.create(SkyRpgBrainrots.id("album"), builder -> builder
            .initializer(() -> EMPTY)
            .persistent(CODEC)
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    static void init() {
        // Loads the class so the attachment is registered during mod init (the client announces the ones it knows).
    }

    public int count(BrainrotKind kind) {
        return caught.getOrDefault(kind.id, 0);
    }

    public boolean has(BrainrotKind kind) {
        return count(kind) > 0;
    }

    /** How many different kinds are in the album. */
    public int discovered() {
        int n = 0;
        for (BrainrotKind kind : BrainrotKind.values()) {
            if (has(kind)) {
                n++;
            }
        }
        return n;
    }

    public long firstCaught(BrainrotKind kind) {
        return first.getOrDefault(kind.id, 0L);
    }

    Album caught(BrainrotKind kind, long now) {
        Map<String, Integer> counts = new HashMap<>(caught);
        counts.merge(kind.id, 1, Integer::sum);
        Map<String, Long> firsts = new HashMap<>(first);
        firsts.putIfAbsent(kind.id, now);
        return new Album(Map.copyOf(counts), Map.copyOf(firsts));
    }
}
