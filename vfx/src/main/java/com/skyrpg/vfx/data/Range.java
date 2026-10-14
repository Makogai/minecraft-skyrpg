package com.skyrpg.vfx.data;

import java.util.random.RandomGenerator;

/** A number picked at random between min and max (equal for a fixed value). JSON: {@code 3} or {@code [2, 5]}. */
public record Range(float min, float max) {
    public static final Range ZERO = new Range(0, 0);

    public static Range of(float value) {
        return new Range(value, value);
    }

    public float sample(RandomGenerator random) {
        return min == max ? min : min + random.nextFloat() * (max - min);
    }

    public boolean isZero() {
        return min == 0 && max == 0;
    }

    public float average() {
        return (min + max) * 0.5f;
    }
}
