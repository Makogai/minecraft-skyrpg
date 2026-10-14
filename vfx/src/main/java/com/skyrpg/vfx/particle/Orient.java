package com.skyrpg.vfx.particle;

import java.util.Locale;

/** How a VFX particle's quad faces. */
public enum Orient {
    /** The kind's default (sparks follow their velocity, everything else faces the camera). */
    AUTO,
    /** Always faces the camera. */
    CAMERA,
    /** Faces the camera but stays upright (turns only around the vertical axis). */
    VERTICAL,
    /** Lies flat on the ground, visible from above and below (shockwave rings). */
    FLAT,
    /** Faces the camera with its long side along the direction it moves (streaks). */
    VELOCITY;

    public static final Orient[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses "flat", "camera"...; null if unknown. */
    public static Orient parse(String name) {
        for (Orient orient : VALUES) {
            if (orient.id().equals(name.toLowerCase(Locale.ROOT))) {
                return orient;
            }
        }
        return null;
    }
}
