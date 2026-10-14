package com.skyrpg.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The hub's moving decor, listed in hub.json: floating item displays (the Skyheart crystal, windmill sails, sky
 * lanterns, the Wildgate veil...) and particle emitters. Decor isn't part of the saved structure; it is spawned
 * here wherever it's missing and animated by sending a new pose every few ticks, which the client smoothly
 * interpolates towards. The pose maths matches tools/builds/hub_skyhaven.py.
 */
final class HubDecor {
    static final String TAG = "skyrpg_hub_decor";
    private static final int STEP_TICKS = 3;
    private static final int SWEEP_TICKS = 200;
    private static final Map<String, Display.ItemDisplay> LIVE = new HashMap<>();
    /** The config the live displays were last matched to; a /reload brings a new one, and they're matched again. */
    private static HubConfig matched;

    /**
     * One display. Its pose at time t: turn by {@code phase + spin * t} degrees about {@code axis}, offset by
     * {@code lift} turned the same way, bob up and down by {@code bob}, and show the item rotated by {@code tilt}
     * then the turn.
     */
    record Piece(String id, Identifier item, Identifier model, Vec3 pos, Vector3f scale, float spin, Vector3f axis, float phase,
                 Vector3f lift, Quaternionf tilt, float bob, float bobPeriod, boolean glow) {
        /** The item shown; {@code model} swaps its look (e.g. a lantern as the 3D block instead of the flat icon). */
        ItemStack stack() {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item));
            if (model != null) {
                stack.set(DataComponents.ITEM_MODEL, model);
            }
            return stack;
        }

        boolean animated() {
            return spin != 0 || bob != 0;
        }

        Transformation pose(double seconds) {
            float angle = (float) Math.toRadians((phase + spin * seconds) % 360);
            Quaternionf turn = new Quaternionf().rotateAxis(angle, axis);
            Vector3f translation = turn.transform(new Vector3f(lift));
            translation.y += (float) (bob * Math.sin(2 * Math.PI * (seconds % bobPeriod) / bobPeriod + Math.toRadians(phase)));
            return new Transformation(translation, new Quaternionf(turn).mul(tilt), new Vector3f(scale), new Quaternionf());
        }
    }

    /** Puffs {@code count} particles around {@code pos} every {@code interval} ticks; {@code far} ones show from afar. */
    record Emitter(SimpleParticleType particle, Vec3 pos, Vec3 spread, int count, float speed, int interval, boolean far) {
    }

    private HubDecor() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HubDecor::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel hub = Hub.level(server);
        if (hub == null || hub.players().isEmpty()) {
            return;
        }
        HubConfig config = HubConfig.get();
        if (config != matched) {
            LIVE.clear();
            matched = config;
        }
        int tick = server.getTickCount();
        for (Emitter emitter : config.emitters()) {
            if (tick % emitter.interval() == 0) {
                Vec3 pos = emitter.pos();
                Vec3 spread = emitter.spread();
                hub.sendParticles(emitter.particle(), false, emitter.far(), pos.x, pos.y, pos.z, emitter.count(),
                        spread.x, spread.y, spread.z, emitter.speed());
            }
        }
        if (tick % SWEEP_TICKS == 0) {
            sweep(hub, config);
        }
        if (tick % STEP_TICKS != 0) {
            return;
        }
        // Aim for where each piece should be when the next update arrives; the client interpolates there.
        double next = (tick + STEP_TICKS) / 20.0;
        // Still pieces are only posed when spawned, so re-apply their pose now and then: a /reload that changes
        // their size or angle in hub.json then shows up without respawning them.
        boolean refreshStill = tick % (STEP_TICKS * SWEEP_TICKS) == 0;
        for (Piece piece : config.decor()) {
            Display.ItemDisplay display = find(hub, piece, tick / 20.0);
            if (display != null && (piece.animated() || refreshStill)) {
                DisplayAccess access = (DisplayAccess) display;
                access.skyrpg$setTransformation(piece.pose(next));
                access.skyrpg$setTransformationInterpolationDuration(STEP_TICKS);
                access.skyrpg$setTransformationInterpolationDelay(0);
            }
        }
    }

    /** The live display for a piece: remembered, found nearby, or spawned once its chunk's entities are loaded. */
    private static Display.ItemDisplay find(ServerLevel hub, Piece piece, double now) {
        Display.ItemDisplay cached = LIVE.get(piece.id());
        if (cached != null && !cached.isRemoved() && cached.level() == hub) {
            return cached;
        }
        LIVE.remove(piece.id());
        BlockPos pos = BlockPos.containing(piece.pos());
        if (!hub.areEntitiesLoaded(ChunkPos.pack(pos))) {
            return null;
        }
        String tag = TAG + "." + piece.id();
        List<Display.ItemDisplay> found = hub.getEntities(EntityTypes.ITEM_DISPLAY, new AABB(pos).inflate(4),
                display -> display.entityTags().contains(tag));
        Display.ItemDisplay display;
        if (found.isEmpty()) {
            display = spawn(hub, piece, now);
        } else {
            display = found.getFirst();
            found.subList(1, found.size()).forEach(Entity::discard);
            display.setPos(piece.pos());
            ((ItemDisplayAccess) display).skyrpg$setItemStack(piece.stack());
        }
        LIVE.put(piece.id(), display);
        return display;
    }

    private static Display.ItemDisplay spawn(ServerLevel hub, Piece piece, double now) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, hub);
        display.setPos(piece.pos());
        ((ItemDisplayAccess) display).skyrpg$setItemStack(piece.stack());
        DisplayAccess access = (DisplayAccess) display;
        access.skyrpg$setTransformation(piece.pose(now));
        access.skyrpg$setViewRange(2f);
        if (piece.glow()) {
            access.skyrpg$setBrightnessOverride(new Brightness(15, 15));
        }
        display.addTag(TAG);
        display.addTag(TAG + "." + piece.id());
        hub.addFreshEntity(display);
        return display;
    }

    /** Removes decor that's no longer configured, and duplicates (e.g. after a piece moved in hub.json). */
    private static void sweep(ServerLevel hub, HubConfig config) {
        Set<String> ids = new HashSet<>();
        config.decor().forEach(piece -> ids.add(piece.id()));
        LIVE.keySet().retainAll(ids);
        for (Display.ItemDisplay display : hub.getEntities(EntityTypes.ITEM_DISPLAY, display -> display.entityTags().contains(TAG))) {
            String id = display.entityTags().stream().filter(tag -> tag.startsWith(TAG + "."))
                    .map(tag -> tag.substring(TAG.length() + 1)).findFirst().orElse("");
            Display.ItemDisplay live = LIVE.get(id);
            if (!ids.contains(id) || (live != null && live != display)) {
                display.discard();
            }
        }
    }

    static List<Piece> parsePieces(JsonArray array) {
        List<Piece> pieces = new ArrayList<>();
        if (array == null) {
            return pieces;
        }
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            JsonArray tilt = json.getAsJsonArray("tilt");
            pieces.add(new Piece(
                    json.get("id").getAsString(),
                    Identifier.parse(json.get("item").getAsString()),
                    json.has("model") ? Identifier.parse(json.get("model").getAsString()) : null,
                    vec3(json.getAsJsonArray("pos")),
                    vector(json.getAsJsonArray("scale")),
                    number(json, "spin", 0),
                    switch (json.has("axis") ? json.get("axis").getAsString() : "y") {
                        case "x" -> new Vector3f(1, 0, 0);
                        case "z" -> new Vector3f(0, 0, 1);
                        default -> new Vector3f(0, 1, 0);
                    },
                    number(json, "phase", 0),
                    json.has("lift") ? vector(json.getAsJsonArray("lift")) : new Vector3f(),
                    tilt == null ? new Quaternionf()
                            : new Quaternionf(tilt.get(0).getAsFloat(), tilt.get(1).getAsFloat(), tilt.get(2).getAsFloat(),
                            tilt.get(3).getAsFloat()).normalize(),
                    number(json, "bob", 0),
                    Math.max(0.5f, number(json, "bob_period", 4)),
                    !json.has("glow") || json.get("glow").getAsBoolean()));
        }
        return List.copyOf(pieces);
    }

    static List<Emitter> parseEmitters(JsonArray array) {
        List<Emitter> emitters = new ArrayList<>();
        if (array == null) {
            return emitters;
        }
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            Identifier id = Identifier.parse(json.get("particle").getAsString());
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
            if (!(type instanceof SimpleParticleType particle)) {
                throw new IllegalArgumentException("emitter particle " + id + " is unknown or needs options");
            }
            emitters.add(new Emitter(particle, vec3(json.getAsJsonArray("pos")),
                    json.has("spread") ? vec3(json.getAsJsonArray("spread")) : Vec3.ZERO,
                    (int) number(json, "count", 1), number(json, "speed", 0), Math.max(1, (int) number(json, "interval", 10)),
                    json.has("far") && json.get("far").getAsBoolean()));
        }
        return List.copyOf(emitters);
    }

    private static float number(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static Vec3 vec3(JsonArray array) {
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
