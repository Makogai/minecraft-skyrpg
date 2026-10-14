package com.skyrpg.vfx.client;

import com.skyrpg.vfx.data.EffectDef;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Client-side entry point for other mods' client code: plays a data-defined effect locally (no network). Cheap to call
 * often (e.g. a pet aura every few ticks): with Effects quality on Off, or the effect out of range, it returns at once.
 * Effects come from the server's data ({@code data/<ns>/skyrpg/vfx/<id>.json}); unknown ids are logged once.
 *
 * <pre>VfxClient.play(Identifier.fromNamespaceAndPath("skyrpg_vfx", "pet_aura_rare"), pet.getX(), pet.getY(), pet.getZ());</pre>
 */
public final class VfxClient {
    private VfxClient() {
    }

    /** Plays a one-shot effect at a point. */
    public static void play(Identifier effect, double x, double y, double z) {
        if (!onClientThread(() -> play(effect, x, y, z)) || !enabled()) {
            return;
        }
        EffectDef def = VfxRuntime.find(effect);
        if (def != null) {
            VfxRuntime.play(def, null, x, y, z, 0, 0, false);
        }
    }

    /** Plays an effect anchored to an entity, following it for the effect's lifetime. */
    public static void play(Identifier effect, Entity anchor) {
        if (anchor == null || !onClientThread(() -> play(effect, anchor)) || !enabled()) {
            return;
        }
        EffectDef def = VfxRuntime.find(effect);
        if (def != null) {
            VfxRuntime.play(def, anchor, anchor.getX(), anchor.getY(), anchor.getZ(), 0, 0, false);
        }
    }

    /** False when the player has Effects quality on Off: skip any work you'd only do for effects. */
    public static boolean enabled() {
        return !VfxQuality.current().off();
    }

    /** True if the server has sent an effect with this id. */
    public static boolean exists(Identifier effect) {
        return VfxRuntime.library().effect(effect) != null;
    }

    /** Runs on the client thread: true to go ahead now, false if it was handed over to run later. */
    private static boolean onClientThread(Runnable later) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            return true;
        }
        minecraft.execute(later);
        return false;
    }
}
