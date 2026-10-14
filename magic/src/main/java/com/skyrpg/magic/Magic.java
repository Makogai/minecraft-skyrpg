package com.skyrpg.magic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.resources.Identifier;

/**
 * The spells and wands currently loaded. On the server they come from data packs; on a client they are what the server sent
 * ({@link MagicPayloads.Definitions}), parsed by the same code.
 */
public final class Magic {
    public static final String SPELLS_DIRECTORY = "skyrpg/spells";
    public static final String WANDS_DIRECTORY = "skyrpg/wands";

    private static Map<Identifier, SpellDef> spells = Map.of();
    private static Map<Identifier, WandDef> wands = Map.of();

    private Magic() {
    }

    public static SpellDef spell(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : spells.get(parsed);
    }

    public static SpellDef spell(Identifier id) {
        return spells.get(id);
    }

    public static Collection<SpellDef> spells() {
        return spells.values();
    }

    /** The wand data of a wand item id, or null. */
    public static WandDef wand(Identifier itemId) {
        return wands.get(itemId);
    }

    public static Collection<WandDef> wands() {
        return wands.values();
    }

    /** The spell whose scroll has this item id, or null. */
    public static SpellDef spellOfScroll(Identifier scrollId) {
        for (SpellDef def : spells.values()) {
            if (def.scrollId().equals(scrollId)) {
                return def;
            }
        }
        return null;
    }

    static void loadSpells(Map<Identifier, JsonElement> files) {
        spells = parseAll(files, SpellDef::parse, "spell");
        SkyRpgMagic.LOGGER.info("Loaded {} spells", spells.size());
    }

    static void loadWands(Map<Identifier, JsonElement> files) {
        wands = parseAll(files, WandDef::parse, "wand");
        SkyRpgMagic.LOGGER.info("Loaded {} wands", wands.size());
    }

    /** Replaces everything with what the server sent (client side). */
    public static void replace(Map<Identifier, JsonElement> spellFiles, Map<Identifier, JsonElement> wandFiles) {
        spells = parseAll(spellFiles, SpellDef::parse, "spell");
        wands = parseAll(wandFiles, WandDef::parse, "wand");
    }

    private static <T> Map<Identifier, T> parseAll(Map<Identifier, JsonElement> files, BiFunction<Identifier, JsonObject, T> parser, String kind) {
        Map<Identifier, T> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                parsed.put(id, parser.apply(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgMagic.LOGGER.error("Skipping invalid {} {}: {}", kind, id, e.getMessage());
            }
        });
        return Collections.unmodifiableMap(parsed);
    }
}
