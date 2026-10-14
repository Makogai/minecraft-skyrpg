package com.skyrpg.vfx.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import com.skyrpg.vfx.SkyRpgVfx;

/**
 * Screen shake and screen flash (see {@code Vfx.shake} and {@code Vfx.flash} on the server). The shake nudges the camera
 * rotation after the game has aimed it (a mixin on Camera), so it never changes where you actually look or shoot. It is
 * scaled by the Distortion Effects accessibility slider and is off when Effects quality is Off. Client thread only.
 */
public final class ScreenFx {
    private static float shakeStrength;
    private static int shakeTotal;
    private static int shakeLeft;
    private static int flashColor;
    private static int flashTotal;
    private static int flashLeft;
    private static int clock;

    private ScreenFx() {
    }

    static void init() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SkyRpgVfx.id("flash"), (graphics, deltaTracker) -> {
            if (flashLeft <= 0 || flashTotal <= 0) {
                return;
            }
            float left = flashLeft - deltaTracker.getGameTimeDeltaPartialTick(false);
            float fade = Mth.clamp(left / flashTotal, 0, 1);
            int alpha = Math.round(((flashColor >>> 24) & 0xFF) * fade * fade * scale(Minecraft.getInstance()));
            if (alpha > 0) {
                Minecraft minecraft = Minecraft.getInstance();
                graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                        (alpha << 24) | (flashColor & 0xFFFFFF));
            }
        });
    }

    static void shake(float strength, int ticks) {
        // A stronger shake takes over; a weaker one never cuts a bigger one short.
        if (strength >= currentStrength() * 0.9f || shakeLeft <= 0) {
            shakeStrength = strength;
            shakeTotal = Math.max(1, ticks);
            shakeLeft = shakeTotal;
        }
    }

    static void flash(int argb, int ticks) {
        flashColor = argb;
        flashTotal = Math.max(1, ticks);
        flashLeft = flashTotal;
    }

    static void tick() {
        clock++;
        if (shakeLeft > 0) {
            shakeLeft--;
        }
        if (flashLeft > 0) {
            flashLeft--;
        }
    }

    static void clear() {
        shakeLeft = 0;
        flashLeft = 0;
    }

    private static float currentStrength() {
        return shakeTotal <= 0 ? 0 : shakeStrength * shakeLeft / shakeTotal;
    }

    private static float scale(Minecraft minecraft) {
        return VfxQuality.current().off() ? 0 : minecraft.options.screenEffectScale().get().floatValue();
    }

    /** The camera nudge right now in degrees: {yaw, pitch}. */
    public static void offset(float partial, float[] out) {
        out[0] = out[1] = 0;
        if (shakeLeft <= 0 || shakeTotal <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float amount = scale(minecraft);
        if (amount <= 0) {
            return;
        }
        float life = Mth.clamp((shakeLeft - partial) / shakeTotal, 0, 1);
        float amplitude = shakeStrength * life * life * amount;
        float t = clock + partial;
        out[0] = amplitude * (Mth.sin(t * 2.3f) * 0.6f + Mth.sin(t * 4.1f + 1.7f) * 0.4f);
        out[1] = amplitude * 0.8f * (Mth.sin(t * 2.9f + 0.6f) * 0.6f + Mth.sin(t * 5.3f + 2.9f) * 0.4f);
    }
}
