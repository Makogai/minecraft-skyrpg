package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.IronThaneEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Iron Thane: a huge crowned dwarf-king construct. Massive gold-trimmed iron pauldrons, a gold crown with glowing rune
 * gems, a brass face mask with burning eyes, a long braided steel beard that splits into two ringed braids around a molten
 * forge-core belly behind an iron grate, smoke stacks on its back, and a two-handed forge hammer (part of the model)
 * held upright in its right hand.
 * <p>
 * The cubes come from {@link IronThaneLayout} (generated with the textures by tools/art/generate_iron_thane.py, whose
 * preview poses mirror this class). Part tree: root > hips > (r_tasset, l_tasset, torso > (core, head > (crown, beard >
 * (r_braid, l_braid)), r_arm > r_forearm > hammer, l_arm > l_forearm)), and r_leg, l_leg under the root.
 * <p>
 * Animation: a heavy walk (slow strides, the body sinking on each footfall), the hammer slam (state.slamTicks, synced
 * from IronThaneEntity: both arms raise the hammer overhead, swing it over the top and smash it into the ground in front
 * while the body drops into the blow), the beard and braids swaying, the crown tilting against the body's roll, the head
 * slowly surveying the hall when it stands still, and the forge core swelling with its pulse (its glow pulses in
 * IronThaneRenderer).
 */
public final class IronThaneModel extends EntityModel<IronThaneRenderState> {
    // pose vector, as IronThaneLayout.SLAM_RAISE / SLAM_HIT: right arm x / z, right forearm, hammer x / z, left arm x / z,
    // left forearm, torso lean
    private static final int RA = 0, RAZ = 1, RF = 2, HM = 3, HZ = 4, LA = 5, LAZ = 6, LF = 7, LEAN = 8;
    private static final float SLAM_TOTAL = IronThaneEntity.SLAM_POSE_TICKS;

    private final ModelPart hips;
    private final ModelPart rightTasset;
    private final ModelPart leftTasset;
    private final ModelPart torso;
    private final ModelPart core;
    private final ModelPart head;
    private final ModelPart crown;
    private final ModelPart beard;
    private final ModelPart rightBraid;
    private final ModelPart leftBraid;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart hammer;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public IronThaneModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        hips = root.getChild("hips");
        rightTasset = hips.getChild("r_tasset");
        leftTasset = hips.getChild("l_tasset");
        torso = hips.getChild("torso");
        core = torso.getChild("core");
        head = torso.getChild("head");
        crown = head.getChild("crown");
        beard = head.getChild("beard");
        rightBraid = beard.getChild("r_braid");
        leftBraid = beard.getChild("l_braid");
        rightArm = torso.getChild("r_arm");
        rightForearm = rightArm.getChild("r_forearm");
        hammer = rightForearm.getChild("hammer");
        leftArm = torso.getChild("l_arm");
        leftForearm = leftArm.getChild("l_forearm");
        rightLeg = root.getChild("r_leg");
        leftLeg = root.getChild("l_leg");
    }

    public static LayerDefinition createLayer() {
        return IronThaneLayout.create();
    }

    /** The forge core's pulse (0 to 1) at this age; the renderer brightens the core's glow with the same beat. */
    static float pulse(float ageInTicks) {
        return (float) Math.pow(Math.max(0f, Mth.sin(ageInTicks * 0.15f)), 3);
    }

    @Override
    public void setupAnim(IronThaneRenderState state) {
        super.setupAnim(state);
        resetPose();
        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.5f);
        float pos = state.walkAnimationPos * 0.45f;
        float c = Mth.cos(pos);
        float s = Mth.sin(pos);
        float lift = (float) Math.pow(Math.abs(s), 0.6);
        float breath = Mth.sin(t * 0.06f);

        // ---- the rest pose (from the layout) plus the heavy walk
        float[] cur = {rightArm.xRot, rightArm.zRot, rightForearm.xRot, hammer.xRot, hammer.zRot, leftArm.xRot, leftArm.zRot, leftForearm.xRot, 0f};
        cur[RA] -= c * 0.12f * speed;
        cur[LA] += c * 0.35f * speed;
        cur[LEAN] += 0.05f * speed + breath * 0.02f;
        rightLeg.xRot += c * 0.6f * speed;
        leftLeg.xRot -= c * 0.6f * speed;
        rightTasset.xRot -= Math.max(0f, -c) * 0.5f * speed;
        leftTasset.xRot -= Math.max(0f, c) * 0.5f * speed;
        float hipsY = -lift * 1.6f * speed;
        float hipsRoll = s * 0.05f * speed;

        // ---- the hammer slam over it: raise 0-10, smash 10-13, hold to 16, recover to 24
        float w = 0f;
        if (state.slamTicks > 0f) {
            float e = SLAM_TOTAL - state.slamTicks;
            float[] raise = IronThaneLayout.SLAM_RAISE;
            float[] hit = IronThaneLayout.SLAM_HIT;
            float[] target;
            float impact;
            if (e < 10f) {
                target = raise;
                w = smooth(e / 10f);
                impact = 0f;
            } else if (e < 13f) {
                float k = smooth((e - 10f) / 3f);
                target = lerp(raise, hit, k);
                w = 1f;
                impact = k;
            } else if (e < 16f) {
                target = hit;
                w = 1f;
                impact = 1f;
            } else {
                target = hit;
                w = 1f - smooth((e - 16f) / 8f);
                impact = w;
            }
            for (int i = 0; i < cur.length; i++) {
                cur[i] += (target[i] - cur[i]) * w;
            }
            hipsY = hipsY * (1f - w) + IronThaneLayout.SLAM_DROP * impact;
            hipsRoll *= 1f - w;
        }

        // ---- apply
        hips.y += hipsY;
        hips.zRot += hipsRoll;
        torso.xRot += cur[LEAN];
        torso.yRot += c * 0.05f * speed;
        rightArm.xRot = cur[RA];
        rightArm.zRot = cur[RAZ];
        rightForearm.xRot = cur[RF];
        hammer.xRot = cur[HM];
        hammer.zRot = cur[HZ];
        leftArm.xRot = cur[LA];
        leftArm.zRot = cur[LAZ];
        leftForearm.xRot = cur[LF];

        // the head looks where the Thane does, stays level as it leans, and surveys the hall when it stands still
        float calm = (1f - speed) * (1f - w);
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.4f, 0.4f);
        head.xRot += pitch - cur[LEAN] * 0.6f;
        head.yRot += yaw + Mth.sin(t * 0.025f) * 0.18f * calm;
        crown.zRot -= hipsRoll * 0.8f;
        beard.xRot += -cur[LEAN] * 0.5f + Mth.sin(t * 0.05f) * 0.03f;
        float sway = c * 0.08f * speed + Mth.sin(t * 0.07f) * 0.04f;
        rightBraid.zRot += sway;
        leftBraid.zRot += sway;

        float swell = 1f + 0.08f * pulse(t);
        core.xScale = swell;
        core.yScale = swell;
        core.zScale = swell;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static float[] lerp(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * k;
        }
        return out;
    }
}
