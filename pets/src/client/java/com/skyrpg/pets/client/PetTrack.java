package com.skyrpg.pets.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.Mth;

/**
 * Client-side smoothing for one player's pet: where it floats (in world space, so it trails when the player moves and
 * swings round when they turn) and where its head looks (lagging behind the player's). Kept per player id, updated from the
 * render layer with the render state's own time, so it works for every player the same and pauses with the game.
 */
final class PetTrack {
    /** Model-space resting spot beside the right shoulder (model space: y down, the player's right is -x). */
    static final float REST_X = -0.74f;
    static final float REST_Y = -0.1f;
    static final float REST_Z = 0.05f;
    /** Height of the model origin above the player's feet, in model units, and the player model's scale. */
    private static final float ORIGIN_HEIGHT = 1.501f;
    private static final float PLAYER_SCALE = 0.9375f;
    /** How far (blocks) the pet may fall behind before it is pulled along, and the distance at which it just snaps. */
    private static final float MAX_LAG = 1.4f;
    private static final float SNAP = 8f;

    private static final Int2ObjectOpenHashMap<PetTrack> TRACKS = new Int2ObjectOpenHashMap<>();

    boolean init;
    /** Smoothed pet position in world coordinates. */
    double wx, wy, wz;
    /** Smoothed head look, degrees. */
    float headYaw, headPitch;
    float lastAge = Float.NaN;
    long lastSeen;
    /** Pet position the last time it was drawn, for effects. */
    double lastX, lastY, lastZ;
    boolean hasPos;

    // results of update(): model-space position of the pet and its lag from the resting spot
    float px, py, pz;
    float lagX, lagY, lagZ;

    static PetTrack get(int key, long now) {
        PetTrack t = TRACKS.get(key);
        if (t == null) {
            t = new PetTrack();
            TRACKS.put(key, t);
        }
        t.lastSeen = now;
        return t;
    }

    static PetTrack find(int id) {
        return TRACKS.get(id);
    }

    /** Drops tracks nobody has drawn for a while (players who left, pets put away). */
    static void prune(long now) {
        TRACKS.values().removeIf(t -> now - t.lastSeen > 200);
    }

    static void clear() {
        TRACKS.clear();
    }

    private static float[] modelToWorld(float mx, float my, float mz, float bodyRot, float s) {
        float ex = -mx * s;
        float ey = (ORIGIN_HEIGHT - my) * s;
        float ez = mz * s;
        double a = Math.toRadians(180f - bodyRot);
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        return new float[]{ex * cos + ez * sin, ey, -ex * sin + ez * cos};
    }

    private static float[] worldToModel(float dx, float dy, float dz, float bodyRot, float s) {
        double a = Math.toRadians(-(180f - bodyRot));
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        float ex = dx * cos + dz * sin;
        float ez = -dx * sin + dz * cos;
        return new float[]{-ex / s, ORIGIN_HEIGHT - dy / s, ez / s};
    }

    /**
     * Moves the smoothed state towards where the pet should be now.
     *
     * @param follow false in menu previews: the pet just sits at its resting spot
     */
    void update(double ownerX, double ownerY, double ownerZ, float bodyRot, float scale, float age, float lookYaw, float lookPitch, boolean follow) {
        float s = PLAYER_SCALE * scale;
        float dt = Float.isNaN(lastAge) ? 0f : age - lastAge;
        if (dt < 0f || dt > 40f) {
            dt = 0f;
            init = false;
        }
        lastAge = age;
        float[] rest = modelToWorld(REST_X, REST_Y, REST_Z, bodyRot, s);
        double tx = ownerX + rest[0];
        double ty = ownerY + rest[1];
        double tz = ownerZ + rest[2];
        if (!init) {
            wx = tx;
            wy = ty;
            wz = tz;
            headYaw = lookYaw;
            headPitch = lookPitch;
            init = true;
        } else if (dt > 0f) {
            // position: exponential catch-up (about 3 ticks), snapping if the player teleported
            double dx = tx - wx;
            double dy = ty - wy;
            double dz = tz - wz;
            if (dx * dx + dy * dy + dz * dz > SNAP * SNAP) {
                wx = tx;
                wy = ty;
                wz = tz;
            } else {
                float k = 1f - (float) Math.exp(-dt / 3.2f);
                wx += dx * k;
                wy += dy * k;
                wz += dz * k;
            }
            // look: slower, so the pet's head trails the player's
            float kl = 1f - (float) Math.exp(-dt / 4.5f);
            headYaw += Mth.wrapDegrees(lookYaw - headYaw) * kl;
            headPitch += (lookPitch - headPitch) * kl;
        }
        if (follow) {
            // limit how far it can trail
            double dx = tx - wx;
            double dy = ty - wy;
            double dz = tz - wz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > MAX_LAG) {
                double f = MAX_LAG / dist;
                wx = tx - dx * f;
                wy = ty - dy * f;
                wz = tz - dz * f;
            }
            float[] m = worldToModel((float) (wx - ownerX), (float) (wy - ownerY), (float) (wz - ownerZ), bodyRot, s);
            px = m[0];
            py = m[1];
            pz = m[2];
        } else {
            wx = tx;
            wy = ty;
            wz = tz;
            px = REST_X;
            py = REST_Y;
            pz = REST_Z;
        }
        lagX = px - REST_X;
        lagY = py - REST_Y;
        lagZ = pz - REST_Z;
        lastX = wx;
        lastY = wy;
        lastZ = wz;
        hasPos = true;
    }
}
