package com.skyrpg.magic;

import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * What a wand does besides its stats (those are in the item's definition): {@code data/<ns>/skyrpg/wands/<item path>.json}
 * <pre>
 * {"slots": 3, "mana_discount": 0.1, "element_power": {"fire": 0.25}, "slow_bonus": 0.5, "chain_bonus": 1, "cooldown_discount": 0.2}
 * </pre>
 *
 * @param slots            how many scrolls it holds
 * @param manaDiscount     share taken off every spell's mana cost
 * @param elementPower     extra damage per element (0.25 = 25% more)
 * @param slowBonus        extra slow duration for frost spells (0.5 = 50% longer)
 * @param chainBonus       extra jumps for Chain Lightning
 * @param cooldownDiscount share taken off every spell's cooldown
 */
public record WandDef(Identifier id, int slots, double manaDiscount, Map<Element, Double> elementPower, double slowBonus,
                      int chainBonus, double cooldownDiscount) {

    /** No bonuses at all: what a cast made without a wand (a command) uses. */
    public static final WandDef NONE = new WandDef(Identifier.fromNamespaceAndPath("skyrpg_magic", "none"), 0, 0, Map.of(), 0, 0, 0);

    public static WandDef parse(Identifier id, JsonObject json) {
        Map<Element, Double> power = new EnumMap<>(Element.class);
        if (json.has("element_power")) {
            json.getAsJsonObject("element_power").entrySet().forEach(e -> power.put(Element.parse(e.getKey()), e.getValue().getAsDouble()));
        }
        return new WandDef(id, json.get("slots").getAsInt(),
                json.has("mana_discount") ? json.get("mana_discount").getAsDouble() : 0, Map.copyOf(power),
                json.has("slow_bonus") ? json.get("slow_bonus").getAsDouble() : 0,
                json.has("chain_bonus") ? json.get("chain_bonus").getAsInt() : 0,
                json.has("cooldown_discount") ? json.get("cooldown_discount").getAsDouble() : 0);
    }

    public double power(Element element) {
        return elementPower.getOrDefault(element, 0.0);
    }
}
