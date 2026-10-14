package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends the server's item and set definitions to a client, as the original JSON, so tooltips can be built
 * from them by the same parsing code. Sent on join and after every /reload.
 */
public record ItemDefinitionsPayload(Map<String, String> items, Map<String, String> sets) implements CustomPacketPayload {
    public static final Type<ItemDefinitionsPayload> TYPE = new Type<>(SkyRpgItems.id("definitions"));

    private static final StreamCodec<ByteBuf, Map<String, String>> JSON_MAP =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);
    public static final StreamCodec<ByteBuf, ItemDefinitionsPayload> CODEC = StreamCodec.composite(
            JSON_MAP, ItemDefinitionsPayload::items,
            JSON_MAP, ItemDefinitionsPayload::sets,
            ItemDefinitionsPayload::new);

    public static ItemDefinitionsPayload current() {
        Map<String, String> items = new LinkedHashMap<>();
        ItemRegistry.items().forEach(def -> items.put(def.id().toString(), def.json().toString()));
        Map<String, String> sets = new LinkedHashMap<>();
        ItemRegistry.sets().forEach(set -> sets.put(set.id().toString(), set.json().toString()));
        return new ItemDefinitionsPayload(items, sets);
    }

    /** Loads these definitions into the client's {@link ItemRegistry}. */
    public void apply() {
        ItemRegistry.replace(parse(items), parse(sets));
    }

    private static Map<Identifier, JsonElement> parse(Map<String, String> files) {
        Map<Identifier, JsonElement> parsed = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> parsed.put(Identifier.parse(entry.getKey()), JsonParser.parseString(entry.getValue())));
        return parsed;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
