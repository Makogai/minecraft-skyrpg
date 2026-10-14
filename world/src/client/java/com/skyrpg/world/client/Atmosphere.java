package com.skyrpg.world.client;

import com.skyrpg.world.RegionShape;
import com.skyrpg.world.bloodmoon.BloodMoonSync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * What the sky and the fog look like: a Blood Moon turns the sky, the clouds, the horizon and the fog red (fading in and out
 * over about ten seconds) and makes the moon full, and a danger region blends the fog towards its own colour (and pulls it
 * in, by the region's density) while you're inside. {@code EnvironmentAttributeProbeMixin} routes the camera's sky and fog
 * values through {@link #tint}.
 */
public final class Atmosphere {
    private static final Vector3fc BLOOD_SKY = new Vector3f(0.36f, 0.02f, 0.04f);
    private static final Vector3fc BLOOD_FOG = new Vector3f(0.30f, 0.02f, 0.03f);
    private static final Vector3fc BLOOD_HORIZON = new Vector3f(0.95f, 0.10f, 0.06f);
    private static final float MOON_FADE_TICKS = 200;
    private static final float REGION_FADE_TICKS = 60;
    private static final float EDGE = 30;

    private static boolean moonOn;
    private static long moonSyncedAt;
    private static int moonTicksLeft;
    /** 0 to 1: how red the world is right now. */
    private static float moon;
    /** 0 to 1: how much of the current region's fog is in; its colour and density are those of the region last inside. */
    private static float region;
    private static final Vector3f REGION_COLOR = new Vector3f();
    private static float regionDensity;

    private Atmosphere() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BloodMoonSync.TYPE, (payload, context) -> {
            moonOn = payload.active();
            moonTicksLeft = payload.ticksLeft();
            moonSyncedAt = Util.getMillis();
        });
        ClientTickEvents.END_CLIENT_TICK.register(Atmosphere::tick);
    }

    /** Whether a Blood Moon is on (as the server last said). */
    static boolean bloodMoon() {
        return moonOn;
    }

    /** Game ticks until dawn, counting down between the server's updates. */
    static int ticksLeft() {
        return Math.max(0, moonTicksLeft - (int) ((Util.getMillis() - moonSyncedAt) / 50));
    }

    /** How red the world is, 0 to 1 (the HUD fades with it). */
    static float moonStrength() {
        return moon;
    }

    private static void tick(Minecraft minecraft) {
        boolean overworld = minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD;
        float targetMoon = moonOn && overworld ? 1 : 0;
        moon += Mth.clamp(targetMoon - moon, -1 / MOON_FADE_TICKS, 1 / MOON_FADE_TICKS);
        float targetRegion = 0;
        if (overworld && minecraft.player != null && RegionMap.dimension().equals(minecraft.level.dimension().identifier().toString())) {
            RegionShape inside = null;
            double best = 0;
            for (RegionShape shape : RegionMap.regions()) {
                double depth = shape.depth(minecraft.player.getBlockX(), minecraft.player.getBlockZ());
                if (depth > best) {
                    best = depth;
                    inside = shape;
                }
            }
            if (inside != null && inside.fogDensity() > 0) {
                targetRegion = (float) Mth.clamp(best / EDGE, 0, 1);
                Vector3f color = new Vector3f((inside.fog() >> 16 & 0xFF) / 255f, (inside.fog() >> 8 & 0xFF) / 255f, (inside.fog() & 0xFF) / 255f);
                if (region <= 0.01f) {
                    REGION_COLOR.set(color);
                } else {
                    REGION_COLOR.lerp(color, 0.05f);
                }
                regionDensity = inside.fogDensity() / 100f;
            }
        }
        region += Mth.clamp(targetRegion - region, -1 / REGION_FADE_TICKS, 1 / REGION_FADE_TICKS);
    }

    /** The probe's value for this attribute with the blood and the fog painted on; the value itself when nothing applies. */
    @SuppressWarnings("unchecked")
    public static <Value> Value tint(EnvironmentAttribute<Value> attribute, Value value) {
        if (moon <= 0.002f && region <= 0.002f) {
            return value;
        }
        if (attribute == EnvironmentAttributes.SKY_COLOR && value instanceof Vector3fc color) {
            Vector3f out = new Vector3f(color);
            blend(out, REGION_COLOR, region * 0.45f);
            blend(out, BLOOD_SKY, moon * 0.9f);
            return (Value) out;
        }
        if (attribute == EnvironmentAttributes.FOG_COLOR && value instanceof Vector3fc color) {
            Vector3f out = new Vector3f(color);
            blend(out, REGION_COLOR, region * 0.92f);
            blend(out, BLOOD_FOG, moon * 0.9f);
            return (Value) out;
        }
        if (attribute == EnvironmentAttributes.CLOUD_COLOR && value instanceof Vector4fc color) {
            Vector3f rgb = new Vector3f(color.x(), color.y(), color.z());
            blend(rgb, REGION_COLOR, region * 0.6f);
            blend(rgb, BLOOD_HORIZON, moon * 0.6f);
            return (Value) new Vector4f(rgb.x, rgb.y, rgb.z, color.w());
        }
        if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR && value instanceof Vector4fc color) {
            Vector3f rgb = new Vector3f(color.x(), color.y(), color.z());
            blend(rgb, BLOOD_HORIZON, moon);
            return (Value) new Vector4f(rgb.x, rgb.y, rgb.z, Math.max(color.w(), moon * 0.7f));
        }
        if (attribute == EnvironmentAttributes.MOON_PHASE && moon > 0.5f) {
            return (Value) MoonPhase.FULL_MOON;
        }
        if ((attribute == EnvironmentAttributes.FOG_START_DISTANCE || attribute == EnvironmentAttributes.FOG_END_DISTANCE
                || attribute == EnvironmentAttributes.SKY_FOG_END_DISTANCE) && value instanceof Float distance) {
            // Pull the fog in: past the render distance it would do nothing, so measure from there.
            float reach = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            float factor = (1 - region * regionDensity * 0.5f) * (1 - moon * 0.25f);
            if (attribute == EnvironmentAttributes.FOG_START_DISTANCE) {
                return (Value) Float.valueOf(distance * factor);
            }
            return (Value) Float.valueOf(Math.min(distance, reach) * factor);
        }
        return value;
    }

    private static void blend(Vector3f color, Vector3fc towards, float amount) {
        if (amount > 0) {
            color.lerp(towards, Math.min(1, amount));
        }
    }
}
