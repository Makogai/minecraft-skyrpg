package com.skyrpg.vfx.data;

import java.util.Locale;

/**
 * The point on an entity an emitter is attached to. Effects played at a plain position ignore it. Offsets are added in
 * the entity's frame: x to its right, y up, z forwards, turning with its body.
 */
public enum Anchor {
    FEET, CENTER, HEAD, EYES, RIGHT_HAND, LEFT_HAND,
    /** The hand that holds the main-hand item (right for most players). */
    MAIN_HAND,
    OFF_HAND,
    /** The hand holding the item a held-item binding matched; the main hand otherwise. */
    HAND,
    BACK;

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() {
        return id;
    }

    /**
     * Points right in front of the camera when it's this entity in first person; emitters there are skipped for
     * yourself in first person unless they say {@code "first_person": true}.
     */
    public boolean nearFace() {
        return this != FEET && this != CENTER && this != BACK;
    }

    public static Anchor parse(String name) {
        for (Anchor anchor : values()) {
            if (anchor.id.equals(name.toLowerCase(Locale.ROOT))) {
                return anchor;
            }
        }
        return null;
    }
}
