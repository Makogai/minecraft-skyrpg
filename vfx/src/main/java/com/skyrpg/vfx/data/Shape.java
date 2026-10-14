package com.skyrpg.vfx.data;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Where an emitter's particles appear, around its anchor. Coordinates are in the anchor's frame (x right, y up,
 * z forwards). Each sample also gives an "outward" direction that {@code "speed"} pushes particles along.
 */
public final class Shape {
    public enum Type {
        /** Exactly at the anchor; outward is a random direction. */
        POINT,
        /** In a ball ({@code radius}), or on its skin with {@code "surface": true}. */
        SPHERE,
        /** On a flat circle ({@code radius}, optional {@code arc} in degrees). */
        RING,
        /** Anywhere on a flat filled circle ({@code radius}). */
        DISC,
        /** Along a spiral rising {@code height} blocks in {@code turns} turns at {@code radius}. */
        HELIX,
        /** Along a straight line {@code from} -> {@code to}. */
        LINE,
        /** At the tip of a cone pointing along {@code direction}, flying out within {@code angle} degrees. */
        CONE,
        /** In a box of {@code size} [x, y, z] centred on the anchor. */
        BOX,
        /** On a ring, and the particles then circle the anchor ({@code orbit_speed} defaults to 3 degrees a tick). */
        ORBIT;

        private final String id = name().toLowerCase(Locale.ROOT);

        public String id() {
            return id;
        }
    }

    public static final Shape POINT = new Shape(Type.POINT);

    public final Type type;
    public float radius = 0.5f;
    public float height = 1;
    public float turns = 1;
    public float arc = (float) (Math.PI * 2);
    public float fromX, fromY, fromZ, toX, toY = 1, toZ;
    public float sizeX = 1, sizeY = 1, sizeZ = 1;
    /** Cone half-angle in radians. */
    public float angle = (float) Math.toRadians(25);
    /** Cone: particles start anywhere up to this far along it instead of at the tip. */
    public float length;
    public float dirX, dirY = 1, dirZ;
    public boolean surface;
    /** Spread particles evenly by their index in the burst instead of at random. */
    public boolean even;
    /** Horizontal sizes count in entity widths and vertical ones in entity heights. */
    public boolean fit;
    /** Start angle in radians; NaN = random each burst. */
    public float phase = Float.NaN;
    /** Extra random offset on every axis, in blocks. */
    public float jitter;

    public Shape(Type type) {
        this.type = type;
    }

    /**
     * Writes a local position to out[0..2] and an outward unit direction to out[3..5].
     *
     * @param index    this particle's index in the burst, for even spreads
     * @param count    the burst size
     * @param phase    the burst's start angle (see {@link #phase})
     * @param width    the anchor entity's width (1 without one)
     * @param height   the anchor entity's height (1 without one)
     */
    public void sample(RandomGenerator random, int index, int count, float phase, float width, float height, float[] out) {
        float hs = fit ? width : 1;
        float vs = fit ? height : 1;
        switch (type) {
            case POINT -> {
                out[0] = out[1] = out[2] = 0;
                randomDirection(random, out);
            }
            case SPHERE -> {
                randomDirection(random, out);
                float r = radius * (surface ? 1 : (float) Math.cbrt(random.nextFloat()));
                out[0] = out[3] * r * hs;
                out[1] = out[4] * r * vs;
                out[2] = out[5] * r * hs;
            }
            case RING, ORBIT, DISC -> {
                float a = phase + (even ? arc * (index + 0.5f) / Math.max(1, count) : arc * random.nextFloat());
                float r = radius * hs * (type == Type.DISC ? (float) Math.sqrt(random.nextFloat()) : 1);
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                out[0] = c * r;
                out[1] = 0;
                out[2] = s * r;
                out[3] = c;
                out[4] = 0;
                out[5] = s;
            }
            case HELIX -> {
                float t = even ? (count <= 1 ? 0.5f : (float) index / (count - 1)) : random.nextFloat();
                float a = phase + t * turns * (float) (Math.PI * 2);
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                out[0] = c * radius * hs;
                out[1] = t * this.height * vs;
                out[2] = s * radius * hs;
                out[3] = c;
                out[4] = 0;
                out[5] = s;
            }
            case LINE -> {
                float t = even ? (count <= 1 ? 0.5f : (float) index / (count - 1)) : random.nextFloat();
                out[0] = (fromX + (toX - fromX) * t) * hs;
                out[1] = (fromY + (toY - fromY) * t) * vs;
                out[2] = (fromZ + (toZ - fromZ) * t) * hs;
                randomDirection(random, out);
            }
            case CONE -> {
                float cosT = 1 - random.nextFloat() * (1 - (float) Math.cos(angle));
                float sinT = (float) Math.sqrt(Math.max(0, 1 - cosT * cosT));
                float phi = random.nextFloat() * (float) (Math.PI * 2);
                float lx = sinT * (float) Math.cos(phi), ly = cosT, lz = sinT * (float) Math.sin(phi);
                // Basis (u, d, w) with d = the cone's axis.
                float hx = Math.abs(dirY) < 0.99f ? 0 : 1, hy = Math.abs(dirY) < 0.99f ? 1 : 0;
                float ux = hy * dirZ, uy = -hx * dirZ, uz = hx * dirY - hy * dirX;
                float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                ux /= ul;
                uy /= ul;
                uz /= ul;
                float wx = dirY * uz - dirZ * uy, wy = dirZ * ux - dirX * uz, wz = dirX * uy - dirY * ux;
                out[3] = ux * lx + dirX * ly + wx * lz;
                out[4] = uy * lx + dirY * ly + wy * lz;
                out[5] = uz * lx + dirZ * ly + wz * lz;
                float along = length > 0 ? random.nextFloat() * length : 0;
                out[0] = out[3] * along * hs;
                out[1] = out[4] * along * vs;
                out[2] = out[5] * along * hs;
            }
            case BOX -> {
                out[0] = (random.nextFloat() - 0.5f) * sizeX * hs;
                out[1] = (random.nextFloat() - 0.5f) * sizeY * vs;
                out[2] = (random.nextFloat() - 0.5f) * sizeZ * hs;
                float len = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
                if (len > 1.0e-4f) {
                    out[3] = out[0] / len;
                    out[4] = out[1] / len;
                    out[5] = out[2] / len;
                } else {
                    randomDirection(random, out);
                }
            }
        }
        if (jitter > 0) {
            out[0] += (random.nextFloat() - 0.5f) * 2 * jitter;
            out[1] += (random.nextFloat() - 0.5f) * 2 * jitter;
            out[2] += (random.nextFloat() - 0.5f) * 2 * jitter;
        }
    }

    /** A uniformly random unit vector into out[3..5]. */
    private static void randomDirection(RandomGenerator random, float[] out) {
        float y = random.nextFloat() * 2 - 1;
        float a = random.nextFloat() * (float) (Math.PI * 2);
        float r = (float) Math.sqrt(1 - y * y);
        out[3] = r * (float) Math.cos(a);
        out[4] = y;
        out[5] = r * (float) Math.sin(a);
    }
}
