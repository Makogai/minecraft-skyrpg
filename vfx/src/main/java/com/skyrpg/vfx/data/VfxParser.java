package com.skyrpg.vfx.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.skyrpg.vfx.particle.Orient;
import com.skyrpg.vfx.particle.VfxParticleKind;
import com.skyrpg.vfx.particle.VfxParticleOptions;
import com.skyrpg.vfx.particle.VfxParticles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Turns effect and binding JSON into {@link EffectDef}s and {@link BindingDef}s. Hard errors throw
 * {@link JsonParseException} (the file is skipped); soft problems (unknown keys, ids from mods that aren't installed)
 * are added to {@code warnings} and the rest still loads. The format is documented in docs/design/vfx.md.
 */
public final class VfxParser {
    private static final Set<String> EFFECT_KEYS = Set.of("emitters", "range", "description", "comment", "_comment");
    private static final Set<String> EMITTER_KEYS = Set.of("name", "comment", "_comment", "particle", "options", "effect",
            "color", "color_end", "alpha", "size", "size_jitter", "lifetime", "gravity", "drag", "spin", "sway", "fade_in", "fullbright",
            "collide", "orient", "count", "rate", "duration", "delay", "every", "chance", "shape", "anchor", "offset", "first_person",
            "velocity", "speed", "spread", "rise", "orbit_speed", "inherit", "follow", "conditions");
    private static final Set<String> SHAPE_KEYS = Set.of("type", "radius", "height", "turns", "arc", "from", "to", "size", "angle",
            "direction", "length", "surface", "even", "fit", "phase", "jitter");
    private static final Set<String> BINDING_KEYS = Set.of("effect", "rate", "range", "self", "conditions", "entity", "armor", "held",
            "description", "comment", "_comment");
    private static final Set<String> ARMOR_KEYS = Set.of("asset", "pieces");
    private static final Set<String> HELD_KEYS = Set.of("item", "items", "rarity", "hand", "except");

    private VfxParser() {
    }

    // ------------------------------------------------------------------------------------------------ effects

    public static EffectDef effect(Identifier id, JsonElement json, List<String> warnings) {
        JsonObject root = object(json, "effect");
        unknownKeys(root, EFFECT_KEYS, "", warnings);
        if (!root.has("emitters") || !root.get("emitters").isJsonArray()) {
            throw new JsonParseException("needs an \"emitters\" list");
        }
        List<EmitterDef> emitters = new ArrayList<>();
        int index = 0;
        for (JsonElement element : root.getAsJsonArray("emitters")) {
            index++;
            String where = "emitter " + index + ": ";
            try {
                emitters.add(emitter(object(element, "emitter"), where, warnings));
            } catch (JsonParseException | IllegalArgumentException e) {
                throw new JsonParseException(where + e.getMessage());
            }
        }
        if (emitters.isEmpty()) {
            throw new JsonParseException("\"emitters\" is empty");
        }
        String description = root.has("description") ? root.get("description").getAsString() : "";
        return EffectDef.of(id, description, emitters.toArray(EmitterDef[]::new), num(root, "range", 64));
    }

    static EmitterDef emitter(JsonObject o, String where, List<String> warnings) {
        unknownKeys(o, EMITTER_KEYS, where, warnings);
        EmitterDef e = new EmitterDef();
        e.name = o.has("name") ? o.get("name").getAsString() : "";
        if (o.has("effect")) {
            e.mode = EmitterDef.OptionMode.EFFECT;
            e.effectId = id(o.get("effect").getAsString(), "effect");
        } else if (o.has("particle")) {
            particle(e, o);
        } else {
            throw new JsonParseException("needs a \"particle\" (or an \"effect\" to play)");
        }

        // Look
        if (o.has("color")) {
            e.color = color(o.get("color"));
        }
        if (o.has("color_end")) {
            e.colorEnd = color(o.get("color_end"));
        }
        float[] alpha = pair(o, "alpha", 1, 0);
        e.alphaStart = alpha[0];
        e.alphaEnd = alpha[1];
        float[] size = pair(o, "size", 0.15f, Float.NaN);
        e.sizeStart = size[0];
        e.sizeEnd = Float.isNaN(size[1]) ? size[0] : size[1];
        e.sizeJitter = num(o, "size_jitter", 0);
        e.lifetime = range(o, "lifetime", Range.of(20));
        if (e.lifetime.min() < 1) {
            throw new JsonParseException("lifetime must be at least 1 tick");
        }
        e.gravity = range(o, "gravity", Range.ZERO);
        e.drag = num(o, "drag", 0.02f);
        e.spin = range(o, "spin", Range.ZERO);
        e.sway = num(o, "sway", 0);
        e.fadeIn = num(o, "fade_in", 0);
        e.fullbright = bool(o, "fullbright", e.kind != null && glowsByDefault(e.kind));
        e.collide = bool(o, "collide", false);
        if (o.has("orient")) {
            e.orient = Orient.parse(o.get("orient").getAsString());
            if (e.orient == null) {
                throw new JsonParseException("unknown orient \"" + o.get("orient").getAsString() + "\" (camera, vertical, flat, velocity)");
            }
        }

        // Spawning
        e.rate = num(o, "rate", 0);
        e.count = range(o, "count", e.rate > 0 ? Range.ZERO : Range.of(1));
        e.duration = (int) num(o, "duration", 0);
        e.delay = (int) num(o, "delay", 0);
        e.every = (int) num(o, "every", 0);
        e.chance = num(o, "chance", 1);

        // Where
        if (o.has("shape")) {
            e.shape = shape(o.get("shape"), where, warnings);
            e.hasShape = true;
        }
        if (o.has("anchor")) {
            e.anchor = Anchor.parse(o.get("anchor").getAsString());
            if (e.anchor == null) {
                throw new JsonParseException("unknown anchor \"" + o.get("anchor").getAsString()
                        + "\" (feet, center, head, eyes, right_hand, left_hand, main_hand, off_hand, hand, back)");
            }
        }
        float[] offset = vec3(o, "offset", 0, 0, 0);
        e.offsetX = offset[0];
        e.offsetY = offset[1];
        e.offsetZ = offset[2];
        e.firstPerson = bool(o, "first_person", false);

        // Motion
        float[] velocity = vec3(o, "velocity", 0, 0, 0);
        e.velocityX = velocity[0];
        e.velocityY = velocity[1];
        e.velocityZ = velocity[2];
        e.speed = range(o, "speed", Range.ZERO);
        e.spread = num(o, "spread", 0);
        e.rise = range(o, "rise", Range.ZERO);
        float orbitDefault = e.shape.type == Shape.Type.ORBIT ? 3 : 0;
        e.orbitSpeed = (float) Math.toRadians(num(o, "orbit_speed", orbitDefault));
        e.inherit = num(o, "inherit", 0);
        e.follow = bool(o, "follow", false);

        // When
        int[] conditions = conditions(o.get("conditions"));
        e.required = conditions[0];
        e.forbidden = conditions[1];
        return e;
    }

    private static boolean glowsByDefault(VfxParticleKind kind) {
        return switch (kind) {
            case SPARK, GLOW, EMBER, RUNE, STAR, WISP -> true;
            default -> false;
        };
    }

    private static void particle(EmitterDef e, JsonObject o) {
        Identifier particleId = id(o.get("particle").getAsString(), "particle");
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId)
                .orElseThrow(() -> new JsonParseException("unknown particle " + particleId));
        e.type = type;
        e.kind = VfxParticles.kind(type);
        if (e.kind != null) {
            e.mode = EmitterDef.OptionMode.VFX;
            if (o.has("options")) {
                throw new JsonParseException("\"options\" is only for vanilla particles; set colour, size... on the emitter itself");
            }
            return;
        }
        if (o.has("options")) {
            e.mode = EmitterDef.OptionMode.FIXED;
            e.fixed = decode(type, o.get("options"));
            if (e.fixed == null) {
                throw new JsonParseException("bad \"options\" for " + particleId);
            }
        } else if (type instanceof SimpleParticleType simple) {
            e.mode = EmitterDef.OptionMode.FIXED;
            e.fixed = simple;
        } else if (type == ParticleTypes.DUST) {
            e.mode = EmitterDef.OptionMode.DUST;
        } else if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
            e.mode = EmitterDef.OptionMode.DUST_TRANSITION;
        } else if (decode(type, colorProbe(false)) instanceof ColorParticleOption) {
            e.mode = EmitterDef.OptionMode.COLOR;
        } else if (decode(type, colorProbe(true)) instanceof SpellParticleOption) {
            e.mode = EmitterDef.OptionMode.SPELL;
        } else {
            throw new JsonParseException(particleId + " needs \"options\" (it isn't a simple particle)");
        }
    }

    private static JsonObject colorProbe(boolean power) {
        JsonObject probe = new JsonObject();
        probe.addProperty("color", -1);
        if (power) {
            probe.addProperty("power", 1.0f);
        }
        return probe;
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions decode(ParticleType<?> type, JsonElement options) {
        try {
            MapCodec<ParticleOptions> codec = (MapCodec<ParticleOptions>) type.codec();
            return codec.codec().parse(JsonOps.INSTANCE, options).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Shape shape(JsonElement json, String where, List<String> warnings) {
        JsonObject o;
        if (json.isJsonPrimitive()) {
            o = new JsonObject();
            o.add("type", json);
        } else {
            o = object(json, "shape");
            unknownKeys(o, SHAPE_KEYS, where + "shape ", warnings);
        }
        String typeName = o.has("type") ? o.get("type").getAsString().toUpperCase(Locale.ROOT) : "POINT";
        Shape.Type type;
        try {
            type = Shape.Type.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("unknown shape \"" + typeName.toLowerCase(Locale.ROOT)
                    + "\" (point, sphere, ring, disc, helix, line, cone, box, orbit)");
        }
        Shape s = new Shape(type);
        s.radius = num(o, "radius", type == Shape.Type.ORBIT ? 0.8f : 0.5f);
        s.height = num(o, "height", 1);
        s.turns = num(o, "turns", 1);
        s.arc = (float) Math.toRadians(num(o, "arc", 360));
        float[] from = vec3(o, "from", 0, 0, 0);
        float[] to = vec3(o, "to", 0, 1, 0);
        s.fromX = from[0];
        s.fromY = from[1];
        s.fromZ = from[2];
        s.toX = to[0];
        s.toY = to[1];
        s.toZ = to[2];
        if (o.has("size")) {
            float[] size = o.get("size").isJsonArray() ? vec3(o, "size", 1, 1, 1) : new float[]{num(o, "size", 1), num(o, "size", 1), num(o, "size", 1)};
            s.sizeX = size[0];
            s.sizeY = size[1];
            s.sizeZ = size[2];
        }
        s.angle = (float) Math.toRadians(num(o, "angle", 25));
        float[] direction = vec3(o, "direction", 0, 1, 0);
        float length = (float) Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        if (length < 1.0e-4f) {
            throw new JsonParseException("cone direction can't be [0, 0, 0]");
        }
        s.dirX = direction[0] / length;
        s.dirY = direction[1] / length;
        s.dirZ = direction[2] / length;
        s.length = num(o, "length", 0);
        s.surface = bool(o, "surface", false);
        s.even = bool(o, "even", false);
        s.fit = bool(o, "fit", false);
        s.phase = o.has("phase") ? (float) Math.toRadians(num(o, "phase", 0)) : Float.NaN;
        s.jitter = num(o, "jitter", 0);
        return s;
    }

    // ------------------------------------------------------------------------------------------------ bindings

    public static BindingDef binding(Identifier id, JsonElement json, List<String> warnings) {
        JsonObject o = object(json, "binding");
        unknownKeys(o, BINDING_KEYS, "", warnings);
        BindingDef b = new BindingDef(id);
        if (!o.has("effect")) {
            throw new JsonParseException("needs an \"effect\"");
        }
        b.effectId = id(o.get("effect").getAsString(), "effect");
        b.rate = num(o, "rate", 0.25f);
        b.range = num(o, "range", 32);
        b.self = bool(o, "self", true);
        int[] conditions = conditions(o.get("conditions"));
        b.required = conditions[0];
        b.forbidden = conditions[1];

        if (o.has("entity")) {
            b.entityTypes = new ArrayList<>();
            b.entityTags = new ArrayList<>();
            for (String entry : strings(o.get("entity"))) {
                if (entry.startsWith("#")) {
                    b.entityTags.add(TagKey.create(Registries.ENTITY_TYPE, id(entry.substring(1), "entity tag")));
                } else {
                    Identifier typeId = id(entry, "entity");
                    BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).ifPresentOrElse(b.entityTypes::add,
                            () -> warnings.add("unknown entity type " + typeId + " (is its mod installed?)"));
                }
            }
        }
        if (o.has("armor")) {
            JsonElement armor = o.get("armor");
            JsonObject a;
            if (armor.isJsonPrimitive()) {
                a = new JsonObject();
                a.add("asset", armor);
            } else {
                a = object(armor, "armor");
                unknownKeys(a, ARMOR_KEYS, "armor ", warnings);
            }
            if (!a.has("asset")) {
                throw new JsonParseException("armor needs an \"asset\" (the equipment asset id, e.g. skyrpg_items:cinderplate)");
            }
            b.armorAssets = new ArrayList<>();
            for (String entry : strings(a.get("asset"))) {
                b.armorAssets.add(id(entry, "armor asset"));
            }
            b.armorPieces = Math.max(1, Math.min(4, (int) num(a, "pieces", 4)));
        }
        if (o.has("held")) {
            JsonObject h = object(o.get("held"), "held");
            unknownKeys(h, HELD_KEYS, "held ", warnings);
            String hand = h.has("hand") ? h.get("hand").getAsString().toLowerCase(Locale.ROOT) : "main";
            b.hand = switch (hand) {
                case "main", "main_hand", "mainhand" -> BindingDef.Hand.MAIN;
                case "off", "off_hand", "offhand" -> BindingDef.Hand.OFF;
                case "either", "any", "both" -> BindingDef.Hand.EITHER;
                default -> throw new JsonParseException("held hand must be main, off or either");
            };
            JsonElement items = h.has("items") ? h.get("items") : h.get("item");
            if (items != null) {
                b.items = new ArrayList<>();
                b.itemTags = new ArrayList<>();
                readItems(items, b.items, b.itemTags, warnings);
            }
            if (h.has("except")) {
                b.exceptItems = new ArrayList<>();
                b.exceptTags = new ArrayList<>();
                readItems(h.get("except"), b.exceptItems, b.exceptTags, warnings);
            }
            if (h.has("rarity")) {
                b.rarities = new ArrayList<>();
                for (String rarity : strings(h.get("rarity"))) {
                    b.rarities.add(rarity.toLowerCase(Locale.ROOT));
                }
            }
            if (items == null && b.rarities == null) {
                throw new JsonParseException("held needs \"items\" or a \"rarity\"");
            }
        }
        if (!b.hasEntityFilter() && b.armorAssets == null && b.hand == null) {
            throw new JsonParseException("needs at least one of \"entity\", \"armor\" or \"held\"");
        }
        if (b.entityTypes != null && b.entityTypes.isEmpty() && b.entityTags.isEmpty()) {
            warnings.add("none of its entity types exist, so it never plays");
        }
        return b;
    }

    private static void readItems(JsonElement json, List<Item> items, List<TagKey<Item>> tags, List<String> warnings) {
        for (String entry : strings(json)) {
            if (entry.startsWith("#")) {
                tags.add(TagKey.create(Registries.ITEM, id(entry.substring(1), "item tag")));
            } else {
                Identifier itemId = id(entry, "item");
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(items::add,
                        () -> warnings.add("unknown item " + itemId + " (is its mod installed?)"));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static void unknownKeys(JsonObject o, Set<String> allowed, String where, List<String> warnings) {
        for (String key : o.keySet()) {
            if (!allowed.contains(key)) {
                warnings.add(where + "unknown key \"" + key + "\" (ignored)");
            }
        }
    }

    private static JsonObject object(JsonElement json, String what) {
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException(what + " must be a JSON object");
        }
        return json.getAsJsonObject();
    }

    private static Identifier id(String text, String what) {
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new JsonParseException("bad " + what + " id \"" + text + "\"");
        }
        return id;
    }

    private static List<String> strings(JsonElement json) {
        List<String> out = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                out.add(element.getAsString());
            }
        } else {
            out.add(json.getAsString());
        }
        return out;
    }

    private static float num(JsonObject o, String key, float fallback) {
        JsonElement value = o.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("\"" + key + "\" must be a number");
        }
        return value.getAsFloat();
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement value = o.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }

    /** A number, or [min, max] picked at random. */
    private static Range range(JsonObject o, String key, Range fallback) {
        JsonElement value = o.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != 2) {
                throw new JsonParseException("\"" + key + "\" range must be [min, max]");
            }
            float a = array.get(0).getAsFloat(), b = array.get(1).getAsFloat();
            return new Range(Math.min(a, b), Math.max(a, b));
        }
        return Range.of(num(o, key, 0));
    }

    /** A number (start = end), or [start, end] over the particle's life. */
    private static float[] pair(JsonObject o, String key, float start, float end) {
        JsonElement value = o.get(key);
        if (value == null) {
            return new float[]{start, end};
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != 2) {
                throw new JsonParseException("\"" + key + "\" must be a number or [start, end]");
            }
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()};
        }
        float v = num(o, key, 0);
        return new float[]{v, v};
    }

    private static float[] vec3(JsonObject o, String key, float x, float y, float z) {
        JsonElement value = o.get(key);
        if (value == null) {
            return new float[]{x, y, z};
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new JsonParseException("\"" + key + "\" must be [x, y, z]");
        }
        JsonArray array = value.getAsJsonArray();
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    static ColorSpec color(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return new ColorSpec(new int[]{colorValue(json.getAsJsonPrimitive())}, false);
        }
        boolean gradient = false;
        JsonArray list;
        if (json.isJsonObject()) {
            JsonObject o = json.getAsJsonObject();
            gradient = o.has("gradient");
            JsonElement stops = gradient ? o.get("gradient") : o.get("palette");
            if (stops == null || !stops.isJsonArray()) {
                throw new JsonParseException("colour object needs a \"gradient\" or \"palette\" list");
            }
            list = stops.getAsJsonArray();
        } else {
            list = json.getAsJsonArray();
        }
        if (list.isEmpty()) {
            throw new JsonParseException("empty colour list");
        }
        int[] colors = new int[list.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = colorValue(list.get(i).getAsJsonPrimitive());
        }
        return new ColorSpec(colors, gradient);
    }

    private static int colorValue(JsonPrimitive value) {
        if (value.isNumber()) {
            return value.getAsInt();
        }
        return VfxParticleOptions.readHex(value.getAsString()).getOrThrow(JsonParseException::new);
    }

    /** {required bits, forbidden bits}. */
    private static int[] conditions(JsonElement json) {
        int required = 0, forbidden = 0;
        if (json != null) {
            for (String entry : strings(json)) {
                boolean negated = entry.startsWith("!");
                Condition condition = Condition.parse(negated ? entry.substring(1) : entry);
                if (condition == null) {
                    throw new JsonParseException("unknown condition \"" + entry
                            + "\" (moving, still, sneaking, sprinting, in_water, on_ground, airborne, night, day, raining; ! negates)");
                }
                if (negated) {
                    forbidden |= condition.bit();
                } else {
                    required |= condition.bit();
                }
            }
        }
        return new int[]{required, forbidden};
    }
}
