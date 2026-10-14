package com.skyrpg.enchants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.data.JsonDataLoader;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The enchants and rules currently loaded. On the server they come from data packs; a client gets the same JSON from
 * the server ({@link Definitions}) on join and after every /reload, and parses it with the same code.
 */
public final class Enchants {
    public static final String DIRECTORY = "skyrpg/enchants";
    public static final String RULES_DIRECTORY = "skyrpg/enchanting";
    private static final Identifier RULES_ID = SkyRpgEnchants.id("rules");

    private static Map<Identifier, Enchant> enchants = Map.of();
    private static List<Enchant> sorted = List.of();
    private static EnchantRules rules = EnchantRules.DEFAULT;

    private Enchants() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgEnchants.id("enchants"), DIRECTORY, files -> {
            load(files);
            SkyRpgEnchants.LOGGER.info("Loaded {} enchants", enchants.size());
        });
        JsonDataLoader.register(SkyRpgEnchants.id("rules"), RULES_DIRECTORY, files -> {
            JsonElement json = files.get(RULES_ID);
            rules = json == null ? EnchantRules.DEFAULT : parseRules(json.getAsJsonObject());
        });
        PayloadTypeRegistry.clientboundPlay().register(Definitions.TYPE, Definitions.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(Enchants::send));
    }

    private static EnchantRules parseRules(JsonObject json) {
        try {
            return EnchantRules.parse(json);
        } catch (RuntimeException e) {
            SkyRpgEnchants.LOGGER.error("Invalid enchanting rules, using defaults: {}", e.getMessage());
            return EnchantRules.DEFAULT;
        }
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, Enchant> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, Enchant.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgEnchants.LOGGER.error("Skipping invalid enchant {}: {}", id, e.getMessage());
            }
        });
        enchants = Map.copyOf(loaded);
        List<Enchant> order = new ArrayList<>(loaded.values());
        order.sort(Comparator.comparing((Enchant def) -> def.appliesTo().getFirst()).thenComparing(Enchant::rarity).thenComparing(Enchant::name));
        sorted = List.copyOf(order);
    }

    public static Enchant get(Identifier id) {
        return enchants.get(id);
    }

    /** Every enchant, by what it goes on, then rarity, then name. */
    public static List<Enchant> all() {
        return sorted;
    }

    public static EnchantRules rules() {
        return rules;
    }

    /** Enchants that can go on this item, in display order. */
    public static List<Enchant> forItem(ItemStack stack) {
        return sorted.stream().filter(def -> def.appliesTo(stack)).toList();
    }

    /** This item's level of an enchant (0 if it has none). */
    public static int level(ItemStack stack, Identifier enchant) {
        return EnchantContent.enchants(stack).level(enchant);
    }

    private static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Definitions.TYPE)) {
            Map<String, String> json = new LinkedHashMap<>();
            enchants.values().forEach(def -> json.put(def.id().toString(), def.json().toString()));
            ServerPlayNetworking.send(player, new Definitions(json, rules.json().toString()));
        }
    }

    /** The enchants and rules as JSON, for clients (tooltips, the screen and the Codex). */
    public record Definitions(Map<String, String> enchants, String rules) implements CustomPacketPayload {
        public static final Type<Definitions> TYPE = new Type<>(SkyRpgEnchants.id("definitions"));
        public static final StreamCodec<ByteBuf, Definitions> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), Definitions::enchants,
                ByteBufCodecs.STRING_UTF8, Definitions::rules, Definitions::new);

        /** Replaces the client's enchants and rules with these. */
        public void apply() {
            Map<Identifier, JsonElement> files = new LinkedHashMap<>();
            enchants.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> files.put(Identifier.parse(entry.getKey()), JsonParser.parseString(entry.getValue())));
            load(files);
            Enchants.rules = parseRules(JsonParser.parseString(rules).getAsJsonObject());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static Collection<Enchant> values() {
        return enchants.values();
    }
}
