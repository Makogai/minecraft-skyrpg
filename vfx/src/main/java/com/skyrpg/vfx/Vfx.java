package com.skyrpg.vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side entry point for other mods: plays a data-defined effect ({@code data/<ns>/skyrpg/vfx/<id>.json}) for
 * every player who can see it. The particles themselves are simulated on the clients, so a call costs one small packet
 * per nearby player. Unknown ids are logged once and ignored. Call from the server thread.
 *
 * <pre>Vfx.play(level, Identifier.fromNamespaceAndPath("skyrpg_vfx", "slam_shockwave"), mob.position());</pre>
 */
public final class Vfx {
    private Vfx() {
    }

    /** Plays a one-shot effect at a point. */
    public static void play(ServerLevel level, Identifier effect, Vec3 pos) {
        VfxServer.play(level, effect, pos);
    }

    /** Plays an effect anchored to an entity, following it for the effect's lifetime. */
    public static void play(Entity anchor, Identifier effect) {
        VfxServer.play(anchor, effect);
    }

    /** True if an effect with this id is loaded on the server. */
    public static boolean exists(Identifier effect) {
        return VfxServer.library().effect(effect) != null;
    }
}
