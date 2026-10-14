package com.skyrpg.magic;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/**
 * One spell, from {@code data/<ns>/skyrpg/spells/<id>.json}:
 * <pre>
 * {"name": "Firebolt", "element": "fire", "rarity": "common", "mana": 8, "cooldown": 0.5,
 *  "chargeable": false, "charge_seconds": 1.5, "description": "...",
 *  "params": {"damage": 35, ...}}   // numbers the spell's code reads, so balance is data
 * </pre>
 * The scroll item has the spell's path plus {@code _scroll} in the same namespace.
 *
 * @param json the file as loaded, sent to clients unchanged
 */
public record SpellDef(Identifier id, String name, Element element, SpellRarity rarity, double mana, double cooldownSeconds,
                       boolean chargeable, double chargeSeconds, String description, JsonObject params, JsonObject json) {

    public static SpellDef parse(Identifier id, JsonObject json) {
        return new SpellDef(id, json.get("name").getAsString(), Element.parse(json.get("element").getAsString()),
                json.has("rarity") ? SpellRarity.parse(json.get("rarity").getAsString()) : SpellRarity.COMMON,
                json.get("mana").getAsDouble(), json.get("cooldown").getAsDouble(),
                json.has("chargeable") && json.get("chargeable").getAsBoolean(),
                json.has("charge_seconds") ? json.get("charge_seconds").getAsDouble() : 1.5,
                json.has("description") ? json.get("description").getAsString() : "",
                json.has("params") ? json.getAsJsonObject("params") : new JsonObject(), json);
    }

    public double param(String key, double fallback) {
        return params.has(key) ? params.get(key).getAsDouble() : fallback;
    }

    public int cooldownTicks() {
        return (int) Math.round(cooldownSeconds * 20);
    }

    public int chargeTicks() {
        return (int) Math.round(chargeSeconds * 20);
    }

    /** The scroll item for this spell. */
    public Identifier scrollId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_scroll");
    }
}
