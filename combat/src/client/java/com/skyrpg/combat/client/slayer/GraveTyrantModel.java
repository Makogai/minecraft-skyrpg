package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Grave Tyrant: a hunched plague giant with an open ribcage and glowing heart, bone spikes, a rusted
 * pauldron, a huge right arm carrying a chained gravestone club, a small clawed left arm, a skull head with a
 * jaw, a tattered shroud hanging from the hips and thick bandaged legs. Parts appear with the slayer tier: II the
 * iron pauldron, bracer and club chain, III the big spikes, the strapped back tombstone and shroud rags, IV the
 * crown, the second chain and the gold trim (see GraveTyrantLayout.applyTier).
 * <p>
 * The cubes come from {@link GraveTyrantLayout} (generated with the textures by tools/art/generate_tyrant.py).
 * Part tree: root > hips > (shroud_front, shroud_strip, shroud_back, shroud_right, shroud_left,
 * waist > (torso, ribs, heart, spine, head > (jaw, crown), right_arm > right_forearm > club > club_chain2,
 * left_arm > left_forearm)), and right_leg, left_leg under the root.
 * <p>
 * Poses are blended: a rest/walk pose, the melee swing (state.attackTime), the Grave Slam (state.slamTicks) and
 * the enraged roar (state.enraged) each weigh in over it.
 */
public final class GraveTyrantModel extends EntityModel<GraveTyrantRenderState> {
    // pose vector: waist lean, right arm x / z, right forearm x, club x, left arm x / z, left forearm x, head x, jaw
    private static final int LEAN = 0, RAX = 1, RAZ = 2, RFX = 3, CLUB = 4, LAX = 5, LAZ = 6, LFX = 7, HEAD = 8, JAW = 9;
    private static final float[] REST = {0.38f, -0.55f, 0f, -0.7f, 1.45f, -0.45f, -0.12f, -0.5f, -0.3f, 0.05f};
    private static final float[] RAISE = {-0.2f, -2.2f, 0.1f, -0.6f, 2.2f, -0.9f, -0.5f, -0.6f, 0.2f, 0.45f};
    private static final float[] SMASH = {0.75f, -1.4f, 0f, -0.5f, 2.9f, -0.9f, -0.3f, -0.6f, -0.1f, 0.2f};
    private static final float[] ROAR = {-0.3f, -0.5f, 1.0f, -0.3f, 1.8f, -0.5f, -1.0f, -0.4f, -0.95f, 0.8f};

    /** Ticks of the slam pose (GraveTyrantEntity.SLAM_POSE_TICKS): raise 0-10, hold to 26, smash to 30, hold to 35, recover to 40. */
    private static final float SLAM_TOTAL = 40f;

    /** Idle intensity per tier I..IV: breathing sway, breathing speed, heartbeat speed, heartbeat swell. */
    private static final float[] BREATH_AMP = {1.0f, 1.0f, 1.25f, 1.8f};
    private static final float[] BREATH_SPEED = {1.0f, 1.0f, 1.1f, 1.25f};
    private static final float[] BEAT_SPEED = {0.9f, 1.0f, 1.2f, 1.5f};
    private static final float[] BEAT_SWELL = {0.12f, 0.17f, 0.21f, 0.3f};

    private final ModelPart root;
    private final ModelPart hips;
    private final ModelPart shroudFront;
    private final ModelPart shroudStrip;
    private final ModelPart shroudBack;
    private final ModelPart shroudRight;
    private final ModelPart shroudLeft;
    private final ModelPart waist;
    private final ModelPart ribs;
    private final ModelPart heart;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart club;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public GraveTyrantModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.root = root;
        hips = root.getChild("hips");
        shroudFront = hips.getChild("shroud_front");
        shroudStrip = hips.getChild("shroud_strip");
        shroudBack = hips.getChild("shroud_back");
        shroudRight = hips.getChild("shroud_right");
        shroudLeft = hips.getChild("shroud_left");
        waist = hips.getChild("waist");
        ribs = waist.getChild("ribs");
        heart = waist.getChild("heart");
        head = waist.getChild("head");
        jaw = head.getChild("jaw");
        rightArm = waist.getChild("right_arm");
        rightForearm = rightArm.getChild("right_forearm");
        club = rightForearm.getChild("club");
        leftArm = waist.getChild("left_arm");
        leftForearm = leftArm.getChild("left_forearm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        return GraveTyrantLayout.create();
    }

    @Override
    public void setupAnim(GraveTyrantRenderState state) {
        super.setupAnim(state);
        resetPose();
        int tier = Mth.clamp(state.tier, 1, 4);
        int ti = tier - 1;
        GraveTyrantLayout.applyTier(root, tier);

        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.4f);
        float pos = state.walkAnimationPos * 0.5f;
        float cos = Mth.cos(pos);
        float sin = Mth.sin(pos);

        // how much each special pose weighs in
        float slamElapsed = SLAM_TOTAL - state.slamTicks;
        boolean slamming = state.slamTicks > 0;
        float attack = state.attackTime > 0 && !slamming ? state.attackTime : 0f;
        float roar = 0f;
        if (state.enraged && !slamming) {
            float cycle = t % 140f;
            roar = smooth(cycle / 10f) * (1f - smooth((cycle - 45f) / 15f));
        }

        // ---- rest pose plus the walk
        float[] pose = REST.clone();
        float enrage = state.enraged ? 1f : 0f;
        pose[LEAN] += 0.04f * enrage + Math.abs(cos) * 0.05f * speed;
        pose[RAX] += -cos * 0.35f * speed;
        pose[LAX] += cos * 0.6f * speed;
        pose[JAW] += 0.12f * enrage;
        rightLeg.xRot = cos * 0.85f * speed;
        leftLeg.xRot = -cos * 0.85f * speed;
        float waistYaw = cos * 0.14f * speed;
        float waistRoll = Mth.cos(pos + 1.2f) * 0.06f * speed;
        float bob = -Math.abs(sin) * 1.3f * speed;

        // idle breathing and the heartbeat
        float breath = Mth.sin(t * (state.enraged ? 0.11f : 0.07f) * BREATH_SPEED[ti]);
        pose[LEAN] += breath * 0.025f * BREATH_AMP[ti];
        float beatPhase = t * (state.enraged ? 0.5f : 0.35f) * BEAT_SPEED[ti];
        float beat = (float) (Math.pow(Math.max(0, Mth.sin(beatPhase)), 6) + 0.6 * Math.pow(Math.max(0, Mth.sin(beatPhase - 0.9f)), 6));
        float twitch = Mth.clamp((Mth.sin(t * 0.9f) * Mth.sin(t * 0.17f) - 0.55f) * 3f, 0f, 1f);
        pose[JAW] += twitch * 0.25f;

        // ---- special poses over it
        float special = 0f;
        float shake = 0f;
        float air = 0f;
        if (attack > 0f) {
            float[] up = lerp(REST, RAISE, 0.8f);
            float[] hit = lerp(REST, SMASH, 0.8f);
            float[] target;
            float w;
            if (attack < 0.4f) {
                target = up;
                w = smooth(attack / 0.4f);
            } else if (attack < 0.62f) {
                target = lerp(up, hit, smooth((attack - 0.4f) / 0.22f));
                w = 1f;
            } else {
                target = hit;
                w = 1f - smooth((attack - 0.62f) / 0.38f);
            }
            pose = lerp(pose, target, w);
            special = Math.max(special, w);
        }
        if (slamming) {
            float[] target;
            float w;
            if (slamElapsed < 10f) {
                target = RAISE;
                w = smooth(slamElapsed / 10f);
            } else if (slamElapsed < 26f) {
                target = RAISE;
                w = 1f;
                shake = Mth.sin(t * 1.9f) * 0.04f * smooth((slamElapsed - 10f) / 6f);
                air = 1f;
            } else if (slamElapsed < 30f) {
                float k = smooth((slamElapsed - 26f) / 4f);
                target = lerp(RAISE, SMASH, k);
                w = 1f;
                air = 1f - k;
            } else if (slamElapsed < 35f) {
                target = SMASH;
                w = 1f;
            } else {
                target = SMASH;
                w = 1f - smooth((slamElapsed - 35f) / 5f);
            }
            pose = lerp(pose, target, w);
            special = Math.max(special, w);
        }
        if (roar > 0f) {
            pose = lerp(pose, ROAR, roar);
            special = Math.max(special, roar * 0.8f);
            shake += Mth.sin(t * 1.7f) * 0.05f * roar;
        }

        // the walk fades while a special pose takes over
        float walkFade = 1f - special;
        rightLeg.xRot *= walkFade;
        leftLeg.xRot *= walkFade;
        rightLeg.xRot -= 0.6f * air;
        leftLeg.xRot -= 0.3f * air;
        waistYaw *= walkFade;
        waistRoll *= walkFade;
        bob = bob * walkFade - 2.5f * air;

        // ---- apply
        hips.y += bob;
        waist.xRot = pose[LEAN] + Mth.sin(t * 2.3f) * 0.02f * roar;
        waist.yRot = waistYaw + shake;
        waist.zRot = waistRoll;
        rightArm.xRot = pose[RAX] + Mth.sin(t * 1.3f) * 0.05f * roar;
        rightArm.zRot = pose[RAZ];
        rightForearm.xRot = pose[RFX];
        club.xRot = pose[CLUB];
        leftArm.xRot = pose[LAX] + Mth.sin(t * 0.05f) * 0.04f * (1f - special) + Mth.sin(t * 1.5f + 1f) * 0.06f * roar;
        leftArm.zRot = pose[LAZ];
        leftForearm.xRot = pose[LFX] + Mth.sin(t * 0.06f + 1f) * 0.05f;
        // the head keeps looking where the boss does, and stays level against the torso's sway
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f);
        head.xRot = pose[HEAD] + pitch * (1f - roar);
        head.yRot = yaw * (1f - roar) - waistYaw * 0.6f + shake * 2f;
        head.y += -0.3f * breath * BREATH_AMP[ti];
        jaw.xRot = pose[JAW];
        ribs.y -= 0.4f * breath * BREATH_AMP[ti];
        float pulse = 1f + BEAT_SWELL[ti] * beat;
        heart.xScale = pulse;
        heart.yScale = pulse;
        heart.zScale = pulse;

        // ---- the shroud swings with the legs
        float flare = special * 0.35f + air * 0.2f;
        shroudFront.xRot = -0.06f - speed * 0.3f * walkFade - cos * 0.28f * speed * walkFade - flare + flutter(t, 0f);
        shroudBack.xRot = 0.06f + speed * 0.3f * walkFade - cos * 0.28f * speed * walkFade + flare * 0.6f + flutter(t, 1.7f);
        shroudStrip.xRot = -0.08f - speed * 0.45f * walkFade + Mth.sin(pos + 0.6f) * 0.3f * speed * walkFade - flare * 1.3f + flutter(t, 3.1f);
        float side = 0.06f + speed * 0.2f * walkFade + Math.abs(cos) * 0.12f * speed * walkFade + flare * 0.7f;
        shroudRight.zRot = side + flutter(t, 4.3f);
        shroudLeft.zRot = -side - flutter(t, 5.9f);
    }

    private static float flutter(float t, float phase) {
        return Mth.sin(t * 0.09f + phase) * 0.03f;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static float[] lerp(float[] a, float[] b, float w) {
        float[] out = new float[a.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * w;
        }
        return out;
    }
}
