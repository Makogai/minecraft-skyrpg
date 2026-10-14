package com.skyrpg.vfx.data;

import com.skyrpg.vfx.particle.Orient;
import com.skyrpg.vfx.particle.VfxParticleKind;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;

/**
 * One emitter of an effect, as parsed from JSON by {@link VfxParser} (see docs/design/vfx.md for every field). Treat it
 * as read-only once parsed; the fields are public so the client's per-tick loop reads them without getters.
 */
public final class EmitterDef {
    /** How the particle options are made for each spawned particle. */
    public enum OptionMode {
        /** One of ours: {@link com.skyrpg.vfx.particle.VfxParticleOptions} with this emitter's colour, size... */
        VFX,
        /** The same options every time ({@link #fixed}): a simple vanilla particle, or one given {@code "options"}. */
        FIXED,
        /** {@code minecraft:dust} in the emitter's colour, sized by {@code size}. */
        DUST,
        /** {@code minecraft:dust_color_transition} from {@code color} to {@code color_end}. */
        DUST_TRANSITION,
        /** A vanilla particle whose only option is a colour (entity_effect, tinted_leaves...). */
        COLOR,
        /** A vanilla spell particle (colour plus power). */
        SPELL,
        /** Not a particle: plays {@link #effect} at each spawn point. */
        EFFECT
    }

    public String name = "";

    // What
    public OptionMode mode = OptionMode.VFX;
    public ParticleType<?> type;
    public VfxParticleKind kind;
    public ParticleOptions fixed;
    public Identifier effectId;
    public EffectDef effect;

    // Look
    public ColorSpec color = ColorSpec.WHITE;
    /** Null: the particle keeps its start colour (alpha still follows {@link #alphaEnd}). */
    public ColorSpec colorEnd;
    public float alphaStart = 1;
    public float alphaEnd;
    public float sizeStart = 0.15f;
    public float sizeEnd = 0.15f;
    /** Each particle's size is scaled by a random 1 +- this. */
    public float sizeJitter;
    public Range lifetime = Range.of(20);
    public Range gravity = Range.ZERO;
    public float drag = 0.02f;
    /** Degrees per tick. */
    public Range spin = Range.ZERO;
    public float sway;
    public float fadeIn;
    public boolean fullbright;
    public boolean collide;
    public Orient orient = Orient.AUTO;

    // Spawning
    /** Particles at the start (and every {@link #every} ticks while it runs). */
    public Range count = Range.ZERO;
    /** Particles per tick while it runs. */
    public float rate;
    /** Ticks it runs; 0 = a single tick. */
    public int duration;
    public int delay;
    public int every;
    /** Chance the emitter runs at all each time the effect plays. */
    public float chance = 1;

    // Where
    public Shape shape = Shape.POINT;
    public boolean hasShape;
    public Anchor anchor = Anchor.CENTER;
    public float offsetX, offsetY, offsetZ;
    public boolean firstPerson;

    // Motion (blocks per tick, in the anchor's frame: x right, y up, z forwards)
    public float velocityX, velocityY, velocityZ;
    public Range speed = Range.ZERO;
    public float spread;
    public Range rise = Range.ZERO;
    /** Radians per tick around the anchor; 0 = no orbiting. */
    public float orbitSpeed;
    /** Fraction of the anchor's own movement added to every particle. */
    public float inherit;
    /** Particles keep moving with the anchor for their whole life. */
    public boolean follow;

    // When
    public int required;
    public int forbidden;

    /** The last tick (from the effect's start) this emitter does anything, plus one. */
    public int end() {
        return delay + Math.max(duration, 1);
    }

    /** True when it only spawns on its first tick (no delay, no duration). */
    public boolean instant() {
        return delay == 0 && duration <= 1;
    }
}
