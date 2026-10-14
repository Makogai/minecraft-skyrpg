package com.skyrpg.items.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.vfx.Vfx;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The data-driven part of an ability's cast: when it works, {@link Abilities} plays the item definition's
 * {@code "cast"} object (inside the ability's {@code params}) on the caster.
 * <pre>
 * "params": {"seconds": 10, ...,
 *   "cast": {"fx": "skyrpg_abilities:boost_molten",                // an effect anchored to the caster (data/&lt;ns&gt;/skyrpg/vfx)
 *            "sounds": [{"sound": "minecraft:item.firecharge.use", "volume": 1.0, "pitch": 0.8}, ...],   // layered
 *            "shake": [0.4, 8],                                    // optional: strength (degrees), ticks
 *            "flash": "#33FFD27A"}}                                // optional: screen wash, #AARRGGBB, lasts 6 ticks
 * </pre>
 */
public final class AbilityFx {
    private AbilityFx() {
    }

    public static void cast(ServerPlayer player, ItemDefinition.Ability ability) {
        JsonObject cast = ability.params().has("cast") && ability.params().get("cast").isJsonObject() ? ability.params().getAsJsonObject("cast") : null;
        if (cast == null) {
            return;
        }
        if (cast.has("fx")) {
            Identifier fx = Identifier.tryParse(cast.get("fx").getAsString());
            if (fx != null) {
                Vfx.play(player, fx);
            }
        }
        if (cast.has("sounds")) {
            for (JsonElement element : cast.getAsJsonArray("sounds")) {
                JsonObject sound = element.getAsJsonObject();
                Vfx.sound(player.level(), player.position(), sound.get("sound").getAsString(),
                        sound.has("volume") ? sound.get("volume").getAsFloat() : 1.0f, sound.has("pitch") ? sound.get("pitch").getAsFloat() : 1.0f);
            }
        }
        if (cast.has("shake")) {
            var shake = cast.getAsJsonArray("shake");
            Vfx.shake(player, shake.get(0).getAsFloat(), shake.get(1).getAsInt());
        }
        if (cast.has("flash")) {
            Vfx.flash(player, (int) Long.parseLong(cast.get("flash").getAsString().replace("#", ""), 16), 6);
        }
    }
}
