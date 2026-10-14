package com.skyrpg.combat.client.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Dwarven Construct: a stocky stone-and-brass automaton. Deepslate-brick body and limbs, oxidised-copper pauldrons,
 * boiler and smoke stacks, brass joints, a helmeted head sunk between the shoulders with glowing eye slits and an iron
 * braided-beard grille, and a cyan rune core in a brass housing on its chest.
 * <p>
 * The cubes come from {@link DwarvenConstructLayout} (generated with the textures by
 * tools/art/generate_dwarven_construct.py, whose preview poses mirror this class). Part tree: root > hips > torso >
 * (core, head > beard, r_arm > r_forearm, l_arm > l_forearm), and r_leg, l_leg under the root.
 * <p>
 * Animation, added to the rest pose: a stompy walk (short heavy strides, the body lifting mid-stride and dropping hard
 * on each footfall while it rolls onto the planted leg), a right-handed punch (wind-up, drive, recover) from the melee
 * swing, the head following the look, and the rune core swelling with each pulse (its glow pulses in
 * DwarvenConstructRenderer).
 */
public final class DwarvenConstructModel extends EntityModel<DwarvenConstructRenderState> {
    // the punch poses' order (DwarvenConstructLayout.PUNCH_BACK / PUNCH_HIT)
    private static final int RA = 0, RF = 1, TY = 2, TX = 3, LA = 4, LF = 5;

    private final ModelPart hips;
    private final ModelPart torso;
    private final ModelPart core;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public DwarvenConstructModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        hips = root.getChild("hips");
        torso = hips.getChild("torso");
        core = torso.getChild("core");
        head = torso.getChild("head");
        rightArm = torso.getChild("r_arm");
        rightForearm = rightArm.getChild("r_forearm");
        leftArm = torso.getChild("l_arm");
        leftForearm = leftArm.getChild("l_forearm");
        rightLeg = root.getChild("r_leg");
        leftLeg = root.getChild("l_leg");
    }

    public static LayerDefinition createLayer() {
        return DwarvenConstructLayout.create();
    }

    /** The rune core's pulse (0 to 1) at this age; the renderer brightens the core's glow with the same beat. */
    static float pulse(float ageInTicks) {
        return (float) Math.pow(Math.max(0f, Mth.sin(ageInTicks * 0.2f)), 4);
    }

    @Override
    public void setupAnim(DwarvenConstructRenderState state) {
        super.setupAnim(state);
        resetPose();
        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.6f);
        float pos = state.walkAnimationPos * 0.5f;
        float c = Mth.cos(pos);
        float s = Mth.sin(pos);
        float lift = (float) Math.pow(Math.abs(s), 0.6);

        // ---- the stompy walk
        rightLeg.xRot += c * 0.75f * speed;
        leftLeg.xRot -= c * 0.75f * speed;
        hips.y -= lift * 1.4f * speed;
        hips.zRot += s * 0.06f * speed;
        torso.yRot += c * 0.07f * speed;
        torso.xRot += Mth.sin(t * 0.06f) * 0.015f;
        rightArm.xRot -= c * 0.4f * speed;
        leftArm.xRot += c * 0.4f * speed;

        // ---- the head follows the look, steady against the torso's twist
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.8f, 0.8f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f);
        head.xRot += pitch;
        head.yRot += yaw - c * 0.07f * speed;

        // ---- the rune core swells with each pulse
        float swell = 1f + 0.12f * pulse(t);
        core.xScale = swell;
        core.yScale = swell;
        core.zScale = swell;

        // ---- the punch: wind up, drive the right fist forward, recover
        float a = state.attackTime;
        if (a > 0f) {
            float w;
            float k;
            if (a < 0.35f) {
                w = smooth(a / 0.35f);
                k = 0f;
            } else if (a < 0.6f) {
                w = 1f;
                k = smooth((a - 0.35f) / 0.25f);
            } else {
                w = 1f - smooth((a - 0.6f) / 0.4f);
                k = 1f;
            }
            float[] back = DwarvenConstructLayout.PUNCH_BACK;
            float[] hit = DwarvenConstructLayout.PUNCH_HIT;
            rightArm.xRot += Mth.lerp(k, back[RA], hit[RA]) * w;
            rightForearm.xRot += Mth.lerp(k, back[RF], hit[RF]) * w;
            torso.yRot += Mth.lerp(k, back[TY], hit[TY]) * w;
            torso.xRot += Mth.lerp(k, back[TX], hit[TX]) * w;
            leftArm.xRot += Mth.lerp(k, back[LA], hit[LA]) * w;
            leftForearm.xRot += Mth.lerp(k, back[LF], hit[LF]) * w;
        }
    }

    static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
