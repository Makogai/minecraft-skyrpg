package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.MagmaTyrantEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Magma Tyrant: a huge molten-rock titan. Cracked basalt and blackstone plates over a molten core that shows between
 * the chest plates and glows through every seam, boulder shoulders with basalt horns, smoking vent spires on its back,
 * giant fists with lava strands hanging from them, a low brutish head with burning eyes and a molten maw, and a crown of
 * flame (a molten circlet ringed with flames).
 * <p>
 * The cubes come from {@link MagmaTyrantLayout} (generated with the textures by tools/art/generate_magma_tyrant.py, whose
 * preview poses mirror this class). Part tree: root > hips > torso > (core, vents, head > (jaw, crown > flame0..N),
 * r_arm > (r_horn, r_forearm > r_drip), l_arm > ...), and r_leg, l_leg under the root.
 * <p>
 * Animation: a heavy walk (short strides, the body dropping onto each footfall, shoulders rolling, fists swinging), the
 * slam (state.slamTicks, synced from MagmaTyrantEntity: both fists raised overhead, then smashed into the ground in front
 * while the body drops into the blow), the core swelling with its pulse (its glow and every crack pulse with it in
 * MagmaTyrantRenderer), the crown's flames flickering each on its own beat, and the lava strands stretching and snapping
 * back under the fists.
 */
public final class MagmaTyrantModel extends EntityModel<MagmaTyrantRenderState> {
    // pose vector, as MagmaTyrantLayout.SLAM_RAISE / SLAM_HIT (deltas added to the rest pose): right arm x / z, right
    // forearm, left arm x / z, left forearm, torso lean, head pitch, jaw, hips drop (model pixels)
    private static final int RA = 0, RAZ = 1, RF = 2, LA = 3, LAZ = 4, LF = 5, LEAN = 6, HEAD = 7, JAW = 8, DROP = 9, KEYS = 10;
    private static final float SLAM_TOTAL = MagmaTyrantEntity.SLAM_POSE_TICKS;

    private final ModelPart hips;
    private final ModelPart torso;
    private final ModelPart core;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart crown;
    private final ModelPart[] flames = new ModelPart[MagmaTyrantLayout.FLAMES];
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightDrip;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftDrip;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MagmaTyrantModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        hips = root.getChild("hips");
        torso = hips.getChild("torso");
        core = torso.getChild("core");
        head = torso.getChild("head");
        jaw = head.getChild("jaw");
        crown = head.getChild("crown");
        for (int i = 0; i < flames.length; i++) {
            flames[i] = crown.getChild("flame" + i);
        }
        rightArm = torso.getChild("r_arm");
        rightForearm = rightArm.getChild("r_forearm");
        rightDrip = rightForearm.getChild("r_drip");
        leftArm = torso.getChild("l_arm");
        leftForearm = leftArm.getChild("l_forearm");
        leftDrip = leftForearm.getChild("l_drip");
        rightLeg = root.getChild("r_leg");
        leftLeg = root.getChild("l_leg");
    }

    public static LayerDefinition createLayer() {
        return MagmaTyrantLayout.create();
    }

    /** The core's pulse (0 to 1) at this age; the renderer brightens the cracks and the core with the same beat. */
    static float pulse(float ageInTicks) {
        return (float) Math.pow(Math.max(0f, Mth.sin(ageInTicks * 0.12f)), 3);
    }

    @Override
    public void setupAnim(MagmaTyrantRenderState state) {
        super.setupAnim(state);
        resetPose();
        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.5f);
        float pos = state.walkAnimationPos * 0.38f;
        float c = Mth.cos(pos);
        float s = Mth.sin(pos);
        float lift = (float) Math.pow(Math.abs(s), 0.6);
        float breath = Mth.sin(t * 0.05f);

        // ---- the heavy walk: short strides, the body dropping onto each footfall, shoulders rolling, fists swinging
        float[] cur = new float[KEYS];
        cur[RA] = -c * 0.32f * speed;
        cur[LA] = c * 0.32f * speed;
        cur[LEAN] = 0.05f * speed + breath * 0.02f;
        cur[JAW] = 0.03f + 0.03f * Mth.sin(t * 0.07f);
        cur[DROP] = (1f - lift) * 1.5f * speed - 0.5f * speed;
        float roll = s * 0.07f * speed;

        // ---- the slam over it: raise 0-12, smash 12-15, hold to 18, recover to 28
        float w = 0f;
        if (state.slamTicks > 0f) {
            float e = SLAM_TOTAL - state.slamTicks;
            float[] raise = MagmaTyrantLayout.SLAM_RAISE;
            float[] hit = MagmaTyrantLayout.SLAM_HIT;
            float[] target;
            if (e < 12f) {
                target = raise;
                w = smooth(e / 12f);
            } else if (e < 15f) {
                target = lerp(raise, hit, smooth((e - 12f) / 3f));
                w = 1f;
            } else if (e < 18f) {
                target = hit;
                w = 1f;
            } else {
                target = hit;
                w = 1f - smooth((e - 18f) / 10f);
            }
            for (int i = 0; i < KEYS; i++) {
                cur[i] += (target[i] - cur[i]) * w;
            }
            roll *= 1f - w;
        }

        // ---- apply
        hips.y += cur[DROP];
        hips.zRot += roll;
        torso.xRot += cur[LEAN];
        torso.yRot += c * 0.08f * speed * (1f - w);
        rightArm.xRot += cur[RA];
        rightArm.zRot += cur[RAZ];
        rightForearm.xRot += cur[RF];
        leftArm.xRot += cur[LA];
        leftArm.zRot += cur[LAZ];
        leftForearm.xRot += cur[LF];
        jaw.xRot += cur[JAW];
        rightLeg.xRot += c * 0.5f * speed;
        leftLeg.xRot -= c * 0.5f * speed;

        // the head looks where the Tyrant does, levels out against the lean, and slowly surveys the hall when it stands
        float calm = (1f - speed) * (1f - w);
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.6f, 0.6f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.4f, 0.4f);
        head.xRot += cur[HEAD] + pitch - cur[LEAN] * 0.5f;
        head.yRot += yaw + Mth.sin(t * 0.02f) * 0.12f * calm;
        crown.zRot -= roll * 0.8f;

        // ---- the core swells with its pulse, the flames flicker, the lava strands stretch and snap back
        float swell = 1f + 0.1f * pulse(t);
        core.xScale = swell;
        core.yScale = swell;
        core.zScale = swell;
        for (int i = 0; i < flames.length; i++) {
            flames[i].yScale = 0.8f + 0.25f * Mth.sin(t * 0.45f + i * 1.7f) + 0.1f * Mth.sin(t * 1.1f + i * 2.9f);
        }
        rightDrip.yScale = 0.7f + 0.6f * frac(t * 0.04f);
        leftDrip.yScale = 0.7f + 0.6f * frac(t * 0.04f + 0.5f);
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
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
