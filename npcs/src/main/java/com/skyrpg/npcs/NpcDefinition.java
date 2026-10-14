package com.skyrpg.npcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * One NPC, loaded from {@code data/<ns>/skyrpg/npcs/<id>.json}:
 * <pre>
 * {
 *   "name": "Mabel Thornfield", "role": "Farmer", "color": "#9BE05A", "hint": "Shop",
 *   "skin": "skyrpg_npcs:entity/npc/farmer", "model": "wide",
 *   "dimension": "skyrpg_hub:hub", "pos": [-8.5, 65, -29.5], "yaw": -90,
 *   "sound": "minecraft:entity.villager.ambient",
 *   "greetings": ["Fresh from the field! Got crops? I pay fair."],
 *   "action": {"type": "open_shop", "params": {"shop": "skyrpg_economy:farmer"}}
 * }
 * </pre>
 * The skin is a texture in a resource pack ({@code assets/<ns>/textures/<path>.png}). The action is run
 * through core's {@code Actions}; with no action (or an unknown one) the NPC just talks.
 */
public record NpcDefinition(Identifier id, String name, String role, int color, String hint, Identifier skin, String model,
                            Identifier dimension, Vec3 pos, float yaw, Identifier sound, List<String> greetings,
                            String actionType, JsonObject actionParams) {

    static NpcDefinition parse(Identifier id, JsonObject json) {
        JsonArray pos = json.getAsJsonArray("pos");
        List<String> greetings = new ArrayList<>();
        if (json.has("greetings")) {
            for (JsonElement line : json.getAsJsonArray("greetings")) {
                greetings.add(line.getAsString());
            }
        }
        JsonObject action = json.has("action") ? json.getAsJsonObject("action") : new JsonObject();
        return new NpcDefinition(id,
                json.get("name").getAsString(),
                json.has("role") ? json.get("role").getAsString() : "",
                json.has("color") ? TextColor.parseColor(json.get("color").getAsString()).getOrThrow().getValue() : 0xFFFFFF,
                json.has("hint") ? json.get("hint").getAsString() : "Talk",
                Identifier.parse(json.get("skin").getAsString()),
                json.has("model") ? json.get("model").getAsString() : "wide",
                Identifier.parse(json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld"),
                new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble()),
                json.has("yaw") ? json.get("yaw").getAsFloat() : 0,
                Identifier.parse(json.has("sound") ? json.get("sound").getAsString() : "minecraft:entity.villager.ambient"),
                List.copyOf(greetings),
                action.has("type") ? action.get("type").getAsString() : "",
                action.has("params") ? action.getAsJsonObject("params") : new JsonObject());
    }
}
