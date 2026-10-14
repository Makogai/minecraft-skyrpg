package com.skyrpg.vfx.particle;

import java.util.Locale;

/**
 * The particle shapes SkyRPG VFX draws itself (textures in {@code assets/skyrpg_vfx/textures/particle/}, drawn by
 * {@code tools/art/generate_vfx.py}). Each is registered as {@code skyrpg_vfx:<name>} and takes
 * {@link VfxParticleOptions}. All textures are white so the colour option tints them.
 */
public enum VfxParticleKind {
    /** A short bright streak that lines up with its velocity (forge sparks, crits). */
    SPARK(SpriteMode.FIRST, Orient.VELOCITY),
    /** A soft round light. */
    GLOW(SpriteMode.FIRST, Orient.CAMERA),
    /** A hot mote that flickers. */
    EMBER(SpriteMode.RANDOM, Orient.CAMERA),
    /** One of eight carved glyphs, picked at random. */
    RUNE(SpriteMode.RANDOM, Orient.CAMERA),
    /** A crystal splinter. */
    SHARD(SpriteMode.RANDOM, Orient.CAMERA),
    /** A four-pointed glint that twinkles. */
    STAR(SpriteMode.RANDOM, Orient.CAMERA),
    /** A soft flame-like wisp, animated over its life. */
    WISP(SpriteMode.AGE, Orient.CAMERA),
    /** A hollow bubble that wobbles. */
    BUBBLE(SpriteMode.FIRST, Orient.CAMERA),
    /** A leaf that flutters. */
    LEAF(SpriteMode.RANDOM, Orient.CAMERA),
    /** A six-armed snow crystal. */
    SNOWFLAKE(SpriteMode.RANDOM, Orient.CAMERA),
    /** A puff of smoke that thins out over its life. */
    SMOKE_PUFF(SpriteMode.AGE, Orient.CAMERA),
    /** A thin circle: shockwaves (lay it flat with {@code "orient": "flat"}) and halos. */
    RING(SpriteMode.FIRST, Orient.CAMERA);

    /** How a particle picks its frame from the sprite set. */
    public enum SpriteMode {
        FIRST, RANDOM, AGE
    }

    private final SpriteMode spriteMode;
    private final Orient defaultOrient;
    private final String id;

    VfxParticleKind(SpriteMode spriteMode, Orient defaultOrient) {
        this.spriteMode = spriteMode;
        this.defaultOrient = defaultOrient;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** The registry path, e.g. "smoke_puff". */
    public String id() {
        return id;
    }

    public SpriteMode spriteMode() {
        return spriteMode;
    }

    /** What {@link Orient#AUTO} means for this kind. */
    public Orient defaultOrient() {
        return defaultOrient;
    }
}
