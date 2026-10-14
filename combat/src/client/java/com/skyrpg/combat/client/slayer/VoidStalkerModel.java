package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Void Stalker: a tall, gaunt void wraith. An elongated faceless head with a vertical glowing slit, a narrow
 * torso whose cracked ribcage shows a violet void core, very long thin arms ending in finger blades, digitigrade
 * legs, a tattered cloak hanging from the waist, and void shards floating round the body.
 * <p>
 * Tiers: I matte body, dim slit, small core, 2 shards. II obsidian shoulder crystals, purple veins, brighter core,
 * 4 shards. III a second small pair of arms, a crystal spine, the cloak becomes a trailing starry rift cloak, 6
 * shards. IV a halo of shards over the head, a blazing white-violet core, cracks of light all over, 8 shards in
 * two rings.
 * <p>
 * The cubes come from {@link VoidStalkerLayout} (generated with the textures by tools/art/generate_void_stalker.py).
 * Part tree: root > (right_thigh > right_shin > right_foot, left_thigh > ..., hips > (cloak_front, cloak_back,
 * cloak_right, cloak_left, rift_back > rift_tail, rift_wing_right, rift_wing_left, waist > (spine, chest > (core,
 * head > halo > halo_s0..5, right_arm > (right_crys0/1, right_forearm > right_hand > right_f0..2), right_sarm >
 * right_sfore > right_sf0/1, and the same on the left))), shards > shard0..7, shield > tile0..31).
 * <p>
 * Driven by the state: walk position/speed (glide), ageInTicks (orbits, idle tilt, finger twitches, pulses),
 * attackTime (swipe), channel (arms up, head back, tight shard ring), phase (flicker) and shield (shard sphere).
 */
public final class VoidStalkerModel extends EntityModel<VoidStalkerRenderState> {
    private static final float[] CORE_SCALE = {0.6f, 0.8f, 1.0f, 1.25f};
    /** Centre of the orbits and of the shield sphere, in model pixels (root space, feet at y = 24). */
    private static final float ORBIT_Y = -3f;
    private static final float RING_Y = -35f;

    private final ModelPart[] shards = new ModelPart[8];
    private final ModelPart[] tiles = new ModelPart[VoidStalkerLayout.SHIELD_TILES];
    private final ModelPart shardGroup;
    private final ModelPart shieldGroup;
    private final ModelPart hips;
    private final ModelPart cloakFront;
    private final ModelPart cloakBack;
    private final ModelPart cloakRight;
    private final ModelPart cloakLeft;
    private final ModelPart riftBack;
    private final ModelPart riftTail;
    private final ModelPart riftWingR;
    private final ModelPart riftWingL;
    private final ModelPart waist;
    private final ModelPart spine;
    private final ModelPart chest;
    private final ModelPart core;
    private final ModelPart head;
    private final ModelPart halo;
    private final ModelPart[] haloShards = new ModelPart[6];
    /** Index 0 right, 1 left. */
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] crys = new ModelPart[4];
    private final ModelPart[] forearm = new ModelPart[2];
    private final ModelPart[] hand = new ModelPart[2];
    private final ModelPart[] fingers = new ModelPart[6];
    private final ModelPart[] sarm = new ModelPart[2];
    private final ModelPart[] sfore = new ModelPart[2];
    private final ModelPart[] sfingers = new ModelPart[4];
    private final ModelPart[] thigh = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];

    public VoidStalkerModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        shardGroup = root.getChild("shards");
        for (int i = 0; i < shards.length; i++) {
            shards[i] = shardGroup.getChild("shard" + i);
        }
        shieldGroup = root.getChild("shield");
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = shieldGroup.getChild("tile" + i);
        }
        hips = root.getChild("hips");
        cloakFront = hips.getChild("cloak_front");
        cloakBack = hips.getChild("cloak_back");
        cloakRight = hips.getChild("cloak_right");
        cloakLeft = hips.getChild("cloak_left");
        riftBack = hips.getChild("rift_back");
        riftTail = riftBack.getChild("rift_tail");
        riftWingR = hips.getChild("rift_wing_right");
        riftWingL = hips.getChild("rift_wing_left");
        waist = hips.getChild("waist");
        spine = waist.getChild("spine");
        chest = waist.getChild("chest");
        core = chest.getChild("core");
        head = chest.getChild("head");
        halo = head.getChild("halo");
        for (int i = 0; i < haloShards.length; i++) {
            haloShards[i] = halo.getChild("halo_s" + i);
        }
        String[] sides = {"right", "left"};
        for (int s = 0; s < 2; s++) {
            String n = sides[s];
            arm[s] = chest.getChild(n + "_arm");
            crys[s * 2] = arm[s].getChild(n + "_crys0");
            crys[s * 2 + 1] = arm[s].getChild(n + "_crys1");
            forearm[s] = arm[s].getChild(n + "_forearm");
            hand[s] = forearm[s].getChild(n + "_hand");
            for (int k = 0; k < 3; k++) {
                fingers[s * 3 + k] = hand[s].getChild(n + "_f" + k);
            }
            sarm[s] = chest.getChild(n + "_sarm");
            sfore[s] = sarm[s].getChild(n + "_sfore");
            sfingers[s * 2] = sfore[s].getChild(n + "_sf0");
            sfingers[s * 2 + 1] = sfore[s].getChild(n + "_sf1");
            thigh[s] = root.getChild(n + "_thigh");
            shin[s] = thigh[s].getChild(n + "_shin");
            foot[s] = shin[s].getChild(n + "_foot");
        }
    }

    public static LayerDefinition createLayer() {
        return VoidStalkerLayout.create();
    }

    private static boolean has(String part, int tier) {
        return tier >= VoidStalkerLayout.MIN_TIER.getOrDefault(part, 1);
    }

    @Override
    public void setupAnim(VoidStalkerRenderState state) {
        super.setupAnim(state);
        resetPose();
        int tier = Mth.clamp(state.tier, 1, 4);
        float t = state.ageInTicks;
        float c = state.channel;
        float shield = state.shield;
        float phase = state.phase;

        // ---- what exists at this tier
        riftBack.visible = has("rift_back", tier);
        riftWingR.visible = has("rift_wing_right", tier);
        riftWingL.visible = has("rift_wing_left", tier);
        cloakBack.visible = !riftBack.visible;
        cloakRight.visible = !riftWingR.visible;
        cloakLeft.visible = !riftWingL.visible;
        spine.visible = has("spine", tier);
        halo.visible = has("halo", tier);
        for (ModelPart p : crys) {
            p.visible = has("right_crys0", tier);
        }
        for (int s = 0; s < 2; s++) {
            sarm[s].visible = has("right_sarm", tier);
        }
        for (int i = 0; i < shards.length; i++) {
            shards[i].visible = i < tier * 2;
        }
        shieldGroup.visible = shield > 0.02f;
        for (ModelPart p : tiles) {
            p.visible = shieldGroup.visible;
        }

        // ---- the glide walk
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.3f);
        float pos = state.walkAnimationPos * 0.4f;
        float cos = Mth.cos(pos);
        float sin = Mth.sin(pos);
        float walk = 1f - 0.7f * c;
        for (int s = 0; s < 2; s++) {
            float swing = (s == 0 ? cos : -cos) * 0.7f * speed * walk;
            float knee = Math.max(0f, -swing);
            thigh[s].xRot += swing;
            shin[s].xRot += knee;
            foot[s].xRot -= swing + knee;
        }
        hips.y += -Math.abs(sin) * 0.9f * speed;
        // always hovering a little; a bigger lift while channelling
        root.y += Mth.sin(t * 0.05f) * 0.35f - 0.35f - 2.5f * c;
        float breath = Mth.sin(t * 0.07f);

        // ---- swipe: raise over the shoulder, then rake down across the body; the left arm follows a beat later
        float atk = state.attackTime;
        float lag = Mth.clamp((atk - 0.12f) / 0.88f, 0f, 1f);
        float[] raise = {raise(atk), atk > 0f ? raise(lag) : 0f};
        float[] strike = {strike(atk), atk > 0f ? strike(lag) : 0f};
        float swipe = Math.max(raise[0] + strike[0], 0f);

        waist.xRot += 0.1f + speed * 0.14f + breath * 0.02f - 0.4f * c + 0.3f * swipe;
        waist.yRot += cos * 0.12f * speed * walk + Mth.sin(t * 1.9f) * 0.04f * c;
        waist.zRot += Mth.sin(t * 0.03f) * 0.03f;
        chest.xRot += 0.05f + breath * 0.02f;

        // ---- head: idle tilt, looks where the boss looks, thrown back while channelling
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f);
        head.xRot += (0.08f + pitch) * (1f - c) - 1.0f * c;
        head.yRot += yaw * (1f - c) - waist.yRot * 0.7f;
        head.zRot += (Mth.sin(t * 0.037f) * 0.14f + Mth.sin(t * 0.013f + 1f) * 0.1f) * (1f - c);
        head.y += -0.3f * breath;

        // ---- arms
        float armSwing = cos * 0.3f * speed * walk;
        for (int s = 0; s < 2; s++) {
            float sgn = s == 0 ? -1f : 1f; // the +x side of the body is 1
            float x = (s == 0 ? armSwing : -armSwing) + 0.05f + Mth.sin(t * 0.045f + s * 2f) * 0.04f;
            float z = Mth.sin(t * 0.04f + s) * 0.03f;
            float fx = Mth.sin(t * 0.05f + 1f + s) * 0.06f;
            x += -2.4f * raise[s] - 0.9f * strike[s];
            fx += -0.5f * raise[s];
            z += -sgn * (0.5f * raise[s] - 0.6f * strike[s]); // outward when raised, across the body on the strike
            // channelling: arms thrown up and apart
            x = Mth.lerp(c, x, -2.75f);
            z = Mth.lerp(c, z, sgn * 0.45f);
            fx = Mth.lerp(c, fx, -0.3f);
            arm[s].xRot += x;
            arm[s].zRot += z;
            forearm[s].xRot += fx;
            hand[s].xRot += -0.1f * (1f - c);
            // finger blades: idle twitches, a splay when swiping or channelling
            float spread = Mth.clamp(raise[s] + strike[s] + c, 0f, 1f);
            for (int k = 0; k < 3; k++) {
                int i = s * 3 + k;
                float tw = Mth.clamp((Mth.sin(t * 0.8f + i * 1.7f) * Mth.sin(t * 0.13f + i) - 0.45f) * 3f, 0f, 1f);
                fingers[i].xRot += -0.12f - tw * 0.6f * (1f - spread) + Mth.sin(t * 1.6f + i) * 0.25f * c;
                fingers[i].zRot += (k - 1) * (0.1f + 0.45f * spread);
            }
            // tier III small arms: they reach out and wave on their own, and rise with the channel
            float sx = -0.3f + Mth.sin(t * 0.07f + s * 3f) * 0.25f - 0.5f * (raise[s] + strike[s]);
            sarm[s].xRot += Mth.lerp(c, sx, -1.9f);
            sfore[s].xRot += Mth.sin(t * 0.09f + s) * 0.2f - 0.2f * c;
            for (int k = 0; k < 2; k++) {
                sfingers[s * 2 + k].xRot += Mth.sin(t * 0.5f + s * 2 + k * 2) * 0.25f - 0.1f;
                sfingers[s * 2 + k].zRot += (k == 0 ? -1 : 1) * 0.2f;
            }
        }

        // ---- core: pulses, bigger with the tier and while channelling
        float beat = 0.5f + 0.5f * Mth.sin(t * (0.14f + 0.04f * tier) * (1f + c));
        float coreScale = CORE_SCALE[tier - 1] * (1f + 0.15f * beat + 0.5f * c);
        core.xScale = coreScale;
        core.yScale = coreScale;
        core.zScale = coreScale;

        // ---- cloak
        float flare = 0.3f * c + 0.2f * swipe;
        float flap = Mth.sin(t * 0.09f) * 0.04f;
        cloakFront.xRot += -0.05f - speed * 0.3f * walk - cos * 0.2f * speed * walk - flare + flap;
        cloakBack.xRot += 0.06f + speed * 0.3f * walk - cos * 0.2f * speed * walk + flare * 0.6f + flap;
        cloakRight.zRot += 0.05f + speed * 0.2f + flare + Mth.sin(t * 0.08f + 1f) * 0.03f;
        cloakLeft.zRot -= 0.05f + speed * 0.2f + flare + Mth.sin(t * 0.08f + 2f) * 0.03f;
        riftBack.xRot += 0.12f + speed * 0.45f * walk + flare * 0.5f + flap;
        riftTail.xRot += 0.08f + speed * 0.3f + Mth.sin(t * 0.12f) * 0.1f;
        float wing = 0.1f + speed * 0.25f + flare + Mth.sin(t * 0.1f) * 0.06f;
        riftWingR.zRot += wing;
        riftWingL.zRot -= wing;
        riftWingR.xRot += speed * 0.2f;
        riftWingL.xRot += speed * 0.2f;

        // ---- the halo over the head (tier IV)
        halo.yRot += t * (0.04f + 0.14f * c);
        halo.y += Mth.sin(t * 0.07f) * 0.8f - 3f * c;
        halo.zRot += Mth.sin(t * 0.05f) * 0.06f;
        float haloScale = 1f + 0.3f * c;
        halo.xScale = haloScale;
        halo.yScale = haloScale;
        halo.zScale = haloScale;
        for (int i = 0; i < haloShards.length; i++) {
            haloShards[i].y += Mth.sin(t * 0.1f + i * 1.1f);
        }

        // ---- the shards: orbit, a tight ring above the head while channelling, a sphere while shielded
        float omega = 0.05f + 0.02f * tier;
        int count = tier * 2;
        for (int i = 0; i < count; i++) {
            float ox;
            float oy;
            float oz;
            if (tier == 4) {
                int ring = i % 2;
                int j = i / 2;
                float a = ring == 0 ? t * omega + j * Mth.HALF_PI : -t * omega * 0.8f + j * Mth.HALF_PI + Mth.PI / 4f;
                float r = ring == 0 ? 12f : 19f;
                float tilt = ring == 0 ? 0.55f : -0.55f;
                ox = r * Mth.cos(a);
                float zz = r * Mth.sin(a);
                oy = ORBIT_Y + zz * Mth.sin(tilt);
                oz = zz * Mth.cos(tilt);
            } else {
                float a = t * omega + i * Mth.TWO_PI / count;
                float r = 12f + tier * 1.5f;
                ox = r * Mth.cos(a);
                float zz = r * Mth.sin(a);
                oy = ORBIT_Y + zz * 0.12f + Mth.sin(t * 0.06f + i * 2.1f) * 4f;
                oz = zz;
            }
            // channelling: pulled into a tight, fast ring over the head
            float ca = t * 0.6f + i * Mth.TWO_PI / count;
            float cr = 5.5f + 0.6f * Mth.sin(t * 0.4f + i);
            float px = Mth.lerp(c, ox, cr * Mth.cos(ca));
            float py = Mth.lerp(c, oy, RING_Y + Mth.sin(ca * 2f) * 0.8f);
            float pz = Mth.lerp(c, oz, cr * Mth.sin(ca));
            // shielded: one of the sphere's facets
            float[] sp = ellipsoid(i + 0.5f, 8, t * 0.03f);
            ModelPart shard = shards[i];
            shard.x = Mth.lerp(shield, px, sp[0]);
            shard.y = Mth.lerp(shield, py, sp[1]);
            shard.z = Mth.lerp(shield, pz, sp[2]);
            shard.yRot = t * (0.1f + 0.03f * tier) + i * 1.3f;
            shard.xRot = Mth.sin(t * 0.05f + i) * 0.4f;
            shard.zRot = Mth.cos(t * 0.04f + i * 2f) * 0.3f;
            float sc = 1f + 0.5f * shield + 0.25f * c + 0.1f * Mth.sin(t * 0.2f + i);
            shard.xScale = sc;
            shard.yScale = sc;
            shard.zScale = sc;
        }

        // ---- the null shield: facets on an ellipsoid round the whole body, slowly turning
        if (shieldGroup.visible) {
            for (int k = 0; k < tiles.length; k++) {
                float[] sp = ellipsoid(k + 0.5f, tiles.length, t * 0.03f);
                ModelPart tile = tiles[k];
                tile.x = sp[0];
                tile.y = sp[1];
                tile.z = sp[2];
                tile.xRot = sp[3];
                tile.yRot = sp[4];
                float sc = (0.3f + 0.7f * shield) * (1f + 0.06f * Mth.sin(t * 0.3f + k));
                tile.xScale = sc;
                tile.yScale = sc;
                tile.zScale = sc;
            }
        }

        // ---- phased out: the body glitches (the renderer makes it translucent); limbs drop out on alternate frames
        if (phase > 0.05f) {
            int frame = (int) (t * 1.7f);
            root.x += (hash(frame, 1) - 0.5f) * 2.4f * phase;
            hips.x += (hash(frame, 2) - 0.5f) * 1.6f * phase;
            head.x += (hash(frame, 3) - 0.5f) * 1.2f * phase;
            if (phase > 0.5f) {
                arm[0].visible = hash(frame, 4) > 0.3f;
                arm[1].visible = hash(frame, 5) > 0.3f;
                boolean cloakOn = hash(frame, 6) > 0.35f;
                cloakFront.visible = cloakOn;
                cloakBack.visible = cloakBack.visible && cloakOn;
                riftBack.visible = riftBack.visible && cloakOn;
                shardGroup.visible = hash(frame, 7) > 0.2f;
            }
        } else {
            arm[0].visible = true;
            arm[1].visible = true;
            cloakFront.visible = true;
            shardGroup.visible = true;
        }
    }

    /** Swipe wind-up weight: 0 at rest, 1 at the top of the raise (attack progress 0.3), then falls as the strike lands. */
    private static float raise(float w) {
        if (w <= 0f) {
            return 0f;
        }
        return smooth(w / 0.3f) * (1f - smooth((w - 0.3f) / 0.3f));
    }

    /** Swipe strike weight: peaks around attack progress 0.6 and fades out in the recovery. */
    private static float strike(float w) {
        if (w <= 0f) {
            return 0f;
        }
        return smooth((w - 0.3f) / 0.3f) * (1f - smooth((w - 0.65f) / 0.35f));
    }

    /**
     * A point on the shield ellipsoid for item k of n (Fibonacci spiral), as {x, y, z, xRot, yRot}: the two angles
     * turn a plate whose front (-z) faces outward so it lies tangent to the surface there.
     */
    private static float[] ellipsoid(float k, int n, float spin) {
        float rx = 14f;
        float ry = 27f;
        float cy = ORBIT_Y + 3f;
        float yf = 1f - 2f * k / n;
        float ring = Mth.sqrt(Math.max(0f, 1f - yf * yf));
        float a = k * 2.3999632f + spin;
        float x = rx * ring * Mth.cos(a);
        float y = cy + ry * yf;
        float z = rx * ring * Mth.sin(a);
        float nx = x / (rx * rx);
        float ny = (y - cy) / (ry * ry);
        float nz = z / (rx * rx);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        return new float[]{x, y, z, (float) Math.asin(ny), (float) Math.atan2(-nx, -nz)};
    }

    private static float hash(int a, int b) {
        int n = a * 374761393 + b * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
