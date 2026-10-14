package com.skyrpg.enchants;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The enchants on one item (the {@code skyrpg_enchants:enchants} component): enchant id to level. A tome holds exactly
 * one. Unknown ids (an enchant removed from the data) are kept but do nothing, so a data mistake never deletes them.
 */
public record EnchantList(Map<Identifier, Integer> levels) {
    public static final EnchantList EMPTY = new EnchantList(Map.of());
    public static final Codec<EnchantList> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 255))
            .xmap(EnchantList::new, EnchantList::levels);
    public static final StreamCodec<ByteBuf, EnchantList> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, Identifier, Integer, Map<Identifier, Integer>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT)
                    .map(EnchantList::new, list -> new HashMap<>(list.levels()));

    public EnchantList {
        levels = Map.copyOf(levels);
    }

    public int level(Identifier id) {
        return levels.getOrDefault(id, 0);
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    public EnchantList with(Identifier id, int level) {
        Map<Identifier, Integer> next = new LinkedHashMap<>(levels);
        next.put(id, level);
        return new EnchantList(next);
    }

    public EnchantList without(Identifier id) {
        Map<Identifier, Integer> next = new LinkedHashMap<>(levels);
        next.remove(id);
        return new EnchantList(next);
    }
}
