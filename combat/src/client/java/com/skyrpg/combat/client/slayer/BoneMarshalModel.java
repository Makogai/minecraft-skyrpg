package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Bone Marshal: a skeletal war general with a cracked skull, ribcage over a dark breastplate, long bony arms,
 * a tattered cape, greaves, a quiver, a war banner and a huge greatbow held in the left hand. Everything is built
 * from {@link BoneMarshalLayout} (generated with the textures by tools/art/generate_bone_marshal.py).
 * <p>
 * Part tree: root > hips > (belt, waist > (spine, clavicle, plate, ribs, cuirass, chest_fire > flame_tip,
 * cape > cape_ext > cape_ext2, quiver > (quiver_band, quiver_extra), banner > flag > flag_tail, banner2 > flag2,
 * shield, head > (jaw, helm, helm_t3 > horns, crown), r_arm > (r_forearm, r_pauld1, r_pauld2),
 * l_arm > (l_forearm > bow > (bow_up > (plate, tip > cap), bow_dn > (plate, tip > cap), string_u, string_d, arrow),
 * l_pauld1, l_pauld2))), and r_leg / l_leg (> greave, knee) under the root.
 * <p>
 * Tiers: 1 bare bones, leather quiver, short cape, plain recurve; 2 iron helm with nose guard, pauldrons, belt,
 * greaves, longer cape, bone-tipped bow; 3 horned great-helm, layered spiked pauldrons, knee guards, war banner,
 * steel-plated greatbow, glowing ribcage; 4 skull crown, gilded cuirass with chest fire, two banners, longest cape,
 * massive rune-lit greatbow with a spectral string.
 * <p>
 * Poses: walk and cape sway always; the vanilla aiming state (state.aiming) raises the bow and pulls the string
 * as the draw progresses; a charged draw (state.drawTicks) is a full-draw raised pose; shielded crosses the arms
 * behind a bone shield; the melee swing (state.attackTime) bashes with the bow.
 */
public final class BoneMarshalModel extends EntityModel<BoneMarshalRenderState> {
    /** Greatbow size by tier (the bow part is scaled). */
    private static final float[] BOW_SCALE = {0.7f, 0.82f, 1.0f, 1.18f};
    /** Half the string's length and where its ends sit in the bow's own frame (see the generator's cube table). */
    private static final float STRING_HALF = 15.8f;
    private static final float STRING_REST_Z = -7.1f;
    private static final float MAX_PULL = 11f;
    /** Reach of the arms: shoulder to elbow, elbow to hand. */
    private static final float UPPER = 7.5f;
    private static final float FORE = 8f;
    private static final float BOW_HAND = 15.5f;

    private final ModelPart hips;
    private final ModelPart belt;
    private final ModelPart waist;
    private final ModelPart ribs;
    private final ModelPart cuirass;
    private final ModelPart chestFire;
    private final ModelPart flameTip;
    private final ModelPart cape;
    private final ModelPart capeExt;
    private final ModelPart capeExt2;
    private final ModelPart quiverBand;
    private final ModelPart quiverExtra;
    private final ModelPart banner;
    private final ModelPart flag;
    private final ModelPart flagTail;
    private final ModelPart banner2;
    private final ModelPart flag2;
    private final ModelPart shield;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart helm;
    private final ModelPart helmT3;
    private final ModelPart crown;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightPauld1;
    private final ModelPart rightPauld2;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftPauld1;
    private final ModelPart leftPauld2;
    private final ModelPart bow;
    private final ModelPart bowUpPlate;
    private final ModelPart bowDnPlate;
    private final ModelPart bowUpCap;
    private final ModelPart bowDnCap;
    private final ModelPart stringUp;
    private final ModelPart stringDn;
    private final ModelPart arrow;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightGreave;
    private final ModelPart leftGreave;
    private final ModelPart rightKnee;
    private final ModelPart leftKnee;

    public BoneMarshalModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        hips = root.getChild("hips");
        belt = hips.getChild("belt");
        waist = hips.getChild("waist");
        ribs = waist.getChild("ribs");
        cuirass = waist.getChild("cuirass");
        chestFire = waist.getChild("chest_fire");
        flameTip = chestFire.getChild("flame_tip");
        cape = waist.getChild("cape");
        capeExt = cape.getChild("cape_ext");
        capeExt2 = capeExt.getChild("cape_ext2");
        ModelPart quiver = waist.getChild("quiver");
        quiverBand = quiver.getChild("quiver_band");
        quiverExtra = quiver.getChild("quiver_extra");
        banner = waist.getChild("banner");
        flag = banner.getChild("flag");
        flagTail = flag.getChild("flag_tail");
        banner2 = waist.getChild("banner2");
        flag2 = banner2.getChild("flag2");
        shield = waist.getChild("shield");
        head = waist.getChild("head");
        jaw = head.getChild("jaw");
        helm = head.getChild("helm");
        helmT3 = head.getChild("helm_t3");
        crown = head.getChild("crown");
        rightArm = waist.getChild("r_arm");
        rightForearm = rightArm.getChild("r_forearm");
        rightPauld1 = rightArm.getChild("r_pauld1");
        rightPauld2 = rightArm.getChild("r_pauld2");
        leftArm = waist.getChild("l_arm");
        leftForearm = leftArm.getChild("l_forearm");
        leftPauld1 = leftArm.getChild("l_pauld1");
        leftPauld2 = leftArm.getChild("l_pauld2");
        bow = leftForearm.getChild("bow");
        ModelPart bowUp = bow.getChild("bow_up");
        ModelPart bowDn = bow.getChild("bow_dn");
        bowUpPlate = bowUp.getChild("bow_up_plate");
        bowDnPlate = bowDn.getChild("bow_dn_plate");
        bowUpCap = bowUp.getChild("bow_up_tip").getChild("bow_up_cap");
        bowDnCap = bowDn.getChild("bow_dn_tip").getChild("bow_dn_cap");
        stringUp = bow.getChild("string_u");
        stringDn = bow.getChild("string_d");
        arrow = bow.getChild("arrow");
        rightLeg = root.getChild("r_leg");
        leftLeg = root.getChild("l_leg");
        rightGreave = rightLeg.getChild("r_greave");
        leftGreave = leftLeg.getChild("l_greave");
        rightKnee = rightLeg.getChild("r_knee");
        leftKnee = leftLeg.getChild("l_knee");
    }

    public static LayerDefinition createLayer() {
        return BoneMarshalLayout.create();
    }

    @Override
    public void setupAnim(BoneMarshalRenderState state) {
        super.setupAnim(state);
        resetPose();
        int tier = Mth.clamp(state.tier, 1, 4);
        applyTierVisibility(tier, state.bannerPlanted);

        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.5f);
        float pos = state.walkAnimationPos * 0.6662f;
        float cos = Mth.cos(pos);
        float sin = Mth.sin(pos);
        float breath = Mth.sin(t * 0.07f);

        // ---- how much each special pose weighs in
        float drawW = drawWeight(state);
        boolean shielded = state.shielded;
        boolean aim = (state.aiming || drawW > 0f) && !shielded;
        float bash = state.attackTime > 0f && !shielded ? Mth.sin(state.attackTime * Mth.PI) : 0f;
        float bashSweep = state.attackTime;

        // ---- walk
        rightLeg.xRot = cos * 0.9f * speed;
        leftLeg.xRot = -cos * 0.9f * speed;
        hips.y += -Math.abs(sin) * 0.9f * speed;
        waist.yRot = cos * 0.1f * speed;
        waist.xRot = 0.05f + Math.abs(cos) * 0.03f * speed + breath * 0.012f;

        // ---- the bow arm: bow-ready idle, aiming, full draw
        float laIdle = -0.85f + cos * 0.18f * speed;
        float lyIdle = 0.22f;
        float lfIdle = -0.12f;
        float raIdle = -0.1f - cos * 0.55f * speed + breath * 0.02f;
        float rfIdle = -0.5f;

        float laAim = -1.42f - 0.3f * drawW;
        float lyAim = 0.5f;
        float scale = BOW_SCALE[tier - 1];
        float pullBase = state.usingItem ? Mth.clamp(0.25f + state.ticksUsingItem / 24f, 0.25f, 1f) : 0.3f;
        float pull = drawW > 0f ? Mth.lerp(drawW, pullBase, 1f) : pullBase;
        float pullLocal = aim ? pull * MAX_PULL : 0f;
        if (drawW > 0f) {
            // the full-draw strain: the bow arm trembles
            laAim += Mth.sin(t * 2.3f) * 0.012f * drawW;
        }

        float la = aim ? laAim : laIdle;
        float ly = aim ? lyAim : lyIdle;
        float lf = aim ? 0f : lfIdle;
        float[] right = null;
        if (aim) {
            // where the nocked string is, in waist space; the right hand has to get there
            float zLocal = (STRING_REST_Z + pullLocal) * scale;
            float hx = 6.5f + BOW_HAND * Mth.sin(la) * Mth.sin(ly);
            float hy = -11f + BOW_HAND * Mth.cos(la);
            float hz = BOW_HAND * Mth.sin(la) * Mth.cos(ly);
            float nx = hx + zLocal * Mth.sin(ly);
            float nz = hz + zLocal * Mth.cos(ly);
            right = solveArm(-6.5f, -11f, 0f, nx, hy, nz, -1f, -0.35f, 0.35f);
            waist.xRot -= 0.1f * drawW;
            waist.yRot += 0.12f;
        }

        // ---- shield: both arms crossed over the chest behind a bone-plate shield
        float[] crossedRight = null;
        float[] crossedLeft = null;
        if (shielded) {
            crossedRight = solveArm(-6.5f, -11f, 0f, 3.5f, -7.5f, -5.2f, -1f, 0.7f, 0.1f);
            crossedLeft = solveArm(6.5f, -11f, 0f, -3.5f, -8.2f, -6.2f, 1f, 0.7f, 0.1f);
            waist.xRot += 0.08f;
            waist.yRot = 0f;
        }

        // ---- melee: a bow bash with the left arm
        if (bash > 0f) {
            la = Mth.lerp(bash, la, -2.0f);
            ly = Mth.lerp(bash, ly, 0.9f - 1.9f * bashSweep);
            waist.yRot += (0.5f - bashSweep) * 0.9f * bash;
            right = null;
        }

        // ---- apply the arms
        leftArm.xRot = la;
        leftArm.yRot = ly;
        leftForearm.xRot = lf;
        if (shielded) {
            rightArm.xRot = crossedRight[0];
            rightArm.yRot = crossedRight[1];
            rightForearm.xRot = crossedRight[2];
            rightForearm.zRot = crossedRight[3];
            leftArm.xRot = crossedLeft[0];
            leftArm.yRot = crossedLeft[1];
            leftForearm.xRot = crossedLeft[2];
            leftForearm.zRot = crossedLeft[3];
        } else if (right != null) {
            rightArm.xRot = right[0];
            rightArm.yRot = right[1];
            rightForearm.xRot = right[2];
            rightForearm.zRot = right[3];
        } else {
            rightArm.xRot = Mth.lerp(bash, raIdle, -0.9f);
            rightArm.zRot = 0.06f;
            rightForearm.xRot = rfIdle * (1f - bash);
        }

        // ---- the bow keeps standing upright against the arm's lift
        bow.visible = !shielded;
        bow.xScale = scale;
        bow.yScale = scale;
        bow.zScale = scale;
        bow.xRot = -(la + lf) * (1f - 0.7f * bash);
        float stringAngle = (float) Mth.atan2(pullLocal, STRING_HALF);
        float stringLen = Mth.sqrt(STRING_HALF * STRING_HALF + pullLocal * pullLocal) / STRING_HALF;
        stringUp.xScale = 0.5f;
        stringUp.zScale = 0.5f;
        stringDn.xScale = 0.5f;
        stringDn.zScale = 0.5f;
        stringUp.xRot = stringAngle;
        stringDn.xRot = -stringAngle;
        stringUp.yScale = stringLen;
        stringDn.yScale = stringLen;
        arrow.visible = aim && pull > 0.2f;
        arrow.z = STRING_REST_Z + pullLocal;

        // ---- head follows the boss's look, the jaw clacks when it draws
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f);
        head.xRot = pitch - 0.05f - 0.12f * drawW;
        head.yRot = yaw - waist.yRot * 0.7f;
        head.y += -0.25f * breath;
        jaw.xRot = 0.06f + (aim ? 0.14f : 0f) + 0.28f * drawW
                + Math.max(0f, Mth.sin(t * 0.8f) * Mth.sin(t * 0.13f)) * 0.12f;
        ribs.y -= 0.3f * breath;

        // ---- chest fire flicker (tier 4)
        float flick = Mth.sin(t * 0.6f) * 0.5f + Mth.sin(t * 1.3f + 1f) * 0.5f;
        flameTip.yScale = 1.2f + 0.45f * flick;
        flameTip.xScale = 1f - 0.12f * flick;
        flameTip.xRot = Mth.sin(t * 0.37f) * 0.15f;
        chestFire.xScale = 1f + 0.06f * flick;

        // ---- cape and banners sway with the walk
        float lift = speed * 0.4f + Math.abs(cos) * 0.06f * speed;
        cape.xRot = 0.1f + lift + sin * 0.1f * speed + flutter(t, 0f) + 0.25f * drawW;
        capeExt.xRot = 0.05f + speed * 0.28f - cos * 0.12f * speed + flutter(t, 1.3f);
        capeExt2.xRot = 0.05f + speed * 0.3f + sin * 0.14f * speed + flutter(t, 2.6f);
        cape.zRot = cos * 0.05f * speed + Mth.sin(t * 0.05f) * 0.015f;
        capeExt.zRot = -cos * 0.06f * speed + flutter(t, 3.9f);
        capeExt2.zRot = cos * 0.08f * speed + flutter(t, 5.2f);
        banner.xRot = 0.02f + speed * 0.05f;
        banner2.xRot = 0.02f + speed * 0.05f;
        flag.yRot = Mth.sin(t * 0.12f) * 0.12f + speed * 0.3f;
        flag.xRot = flutter(t, 0.7f) + speed * 0.15f;
        flagTail.yRot = Mth.sin(t * 0.15f + 0.8f) * 0.2f;
        flagTail.xRot = flutter(t, 2.1f) * 2f + speed * 0.2f;
        flag2.yRot = Mth.sin(t * 0.11f + 2f) * 0.12f - speed * 0.3f;
        flag2.xRot = flutter(t, 3.3f) + speed * 0.15f;

        // ---- the shield hovers and pulses
        float pulse = 1f + Mth.sin(t * 0.2f) * 0.025f;
        shield.visible = shielded;
        shield.xScale = pulse;
        shield.yScale = pulse;
        shield.y += Mth.sin(t * 0.1f) * 0.4f;
    }

    /** 0 outside a charged draw; ramps in over the draw's start, holds while ticks remain, snaps off at the release. */
    private static float drawWeight(BoneMarshalRenderState state) {
        if (state.drawTicks <= 0f) {
            return 0f;
        }
        float elapsed = Math.max(0f, state.drawTotal - state.drawTicks);
        float ramp = smooth(elapsed / Math.max(4f, Math.min(14f, state.drawTotal * 0.5f)));
        float release = smooth(state.drawTicks / 3f);
        return Math.min(ramp, release);
    }

    private void applyTierVisibility(int tier, boolean bannerPlanted) {
        belt.visible = tier >= 2;
        rightPauld1.visible = tier >= 2;
        leftPauld1.visible = tier >= 2;
        rightGreave.visible = tier >= 2;
        leftGreave.visible = tier >= 2;
        capeExt.visible = tier >= 2;
        helm.visible = tier >= 2 && tier <= 3;
        bowUpCap.visible = tier >= 2;
        bowDnCap.visible = tier >= 2;
        quiverBand.visible = tier >= 2;

        helmT3.visible = tier == 3;
        rightPauld2.visible = tier >= 3;
        leftPauld2.visible = tier >= 3;
        rightKnee.visible = tier >= 3;
        leftKnee.visible = tier >= 3;
        capeExt2.visible = tier >= 3;
        quiverExtra.visible = tier >= 3;
        bowUpPlate.visible = tier >= 3;
        bowDnPlate.visible = tier >= 3;
        ribs.visible = tier <= 3;

        crown.visible = tier >= 4;
        cuirass.visible = tier >= 4;
        chestFire.visible = tier >= 4;

        // the war banners ride on the back unless they are planted in the ground
        banner.visible = tier >= 3 && !bannerPlanted;
        banner2.visible = tier >= 4 && !bannerPlanted;
    }

    private static float flutter(float t, float phase) {
        return Mth.sin(t * 0.09f + phase) * 0.035f;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    /**
     * Two-bone solver for an arm: where the shoulder is (waist space), where the hand should be and which way the
     * elbow points. Returns the arm's xRot and yRot, then the forearm's xRot and zRot. The bone runs along the
     * part's local +y and the rotation order is Rz*Ry*Rx.
     */
    private static float[] solveArm(float sx, float sy, float sz, float tx, float ty, float tz, float px, float py, float pz) {
        float dx = tx - sx;
        float dy = ty - sy;
        float dz = tz - sz;
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = dx / d;
        float ny = dy / d;
        float nz = dz / d;
        float reach = Mth.clamp(d, Math.abs(UPPER - FORE) + 0.5f, (UPPER + FORE) * 0.995f);
        // the pole's component perpendicular to the shoulder-hand line decides which way the elbow bends
        float dot = px * nx + py * ny + pz * nz;
        float mx = px - dot * nx;
        float my = py - dot * ny;
        float mz = pz - dot * nz;
        float ml = Mth.sqrt(mx * mx + my * my + mz * mz);
        if (ml < 1e-4f) {
            mx = 0f;
            my = -1f;
            mz = 0f;
            ml = 1f;
        }
        mx /= ml;
        my /= ml;
        mz /= ml;
        float cosA = Mth.clamp((UPPER * UPPER + reach * reach - FORE * FORE) / (2f * UPPER * reach), -1f, 1f);
        float sinA = Mth.sqrt(1f - cosA * cosA);
        float ax = cosA * nx + sinA * mx;
        float ay = cosA * ny + sinA * my;
        float az = cosA * nz + sinA * mz;
        float ex = ax * UPPER;
        float ey = ay * UPPER;
        float ez = az * UPPER;
        // the forearm runs from the elbow to where the hand ends up (the target, or as far as the arm reaches)
        float bx = nx * reach - ex;
        float by = ny * reach - ey;
        float bz = nz * reach - ez;
        float bl = Mth.sqrt(bx * bx + by * by + bz * bz);
        bx /= bl;
        by /= bl;
        bz /= bl;
        float armX = -(float) Math.acos(Mth.clamp(ay, -1f, 1f));
        float armY = (float) Mth.atan2(-ax, -az);
        // the forearm's direction in the arm's own frame: undo Ry(armY), then Rx(armX)
        float cy = Mth.cos(-armY);
        float sY = Mth.sin(-armY);
        float rx = bx * cy + bz * sY;
        float rz = -bx * sY + bz * cy;
        float cx = Mth.cos(-armX);
        float sX = Mth.sin(-armX);
        float fy = by * cx - rz * sX;
        float fz = by * sX + rz * cx;
        float foreX = (float) Math.asin(Mth.clamp(fz, -1f, 1f));
        float foreZ = (float) Mth.atan2(-rx, fy);
        return new float[] {armX, armY, foreX, foreZ};
    }
}
