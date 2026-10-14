package com.skyrpg.vfx.data;

import net.minecraft.resources.Identifier;

/**
 * A parsed effect ({@code data/<ns>/skyrpg/vfx/<path>.json}): a list of emitters that run together.
 *
 * @param range       how far from the camera it is still drawn, in blocks
 * @param length      ticks until its last emitter is done
 * @param instant     everything happens on the first tick, so playing it needs no running instance
 */
public record EffectDef(Identifier id, String description, EmitterDef[] emitters, float range, int length, boolean instant) {
    public static EffectDef of(Identifier id, String description, EmitterDef[] emitters, float range) {
        int length = 1;
        boolean instant = true;
        for (EmitterDef emitter : emitters) {
            length = Math.max(length, emitter.end());
            instant &= emitter.instant();
        }
        return new EffectDef(id, description, emitters, range, length, instant);
    }
}
