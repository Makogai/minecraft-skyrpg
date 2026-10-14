package com.skyrpg.mounts.client.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The griffin: an eagle's head, neck ruff and wings on a lion's body. The cubes come from {@link GriffinLayout} (generated
 * with the textures by tools/art/generate_griffin.py, whose {@code anim_pose} mirrors this class: keep them in step).
 * Part tree: root > body > (neck > head > (beak > beak_tip, jaw, crest, tuft_{r,l}), r_leg_f > r_leg_f_b (forelegs, talons),
 * r_leg_h > r_leg_h_b (hind legs, paws), r_wing_a > r_wing_b > r_wing_c (arm, forearm, primaries), tail > tail_tip).
 * <p>
 * Poses: on the ground the wings fold in a zig-zag against the back and the legs walk (diagonal pairs); in flight (state.fly)
 * the legs tuck and the wings spread and flap, lagging down the arm; gliding (state.glide) holds them spread and still with
 * the neck stretched.
 */
public final class GriffinModel extends EntityModel<GriffinRenderState> {
    private static final float FOLD_A = 1.5f;
    private static final float FOLD_B = 3.0f;
    private static final float FOLD_C = -1.5f;

    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart[] tuft = new ModelPart[2];
    /** Right then left. */
    private final ModelPart[] legF = new ModelPart[2];
    private final ModelPart[] legFB = new ModelPart[2];
    private final ModelPart[] legH = new ModelPart[2];
    private final ModelPart[] legHB = new ModelPart[2];
    private final ModelPart[] wingA = new ModelPart[2];
    private final ModelPart[] wingB = new ModelPart[2];
    private final ModelPart[] wingC = new ModelPart[2];

    public GriffinModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        body = root.getChild("body");
        neck = body.getChild("neck");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        tail = body.getChild("tail");
        String[] sides = {"r", "l"};
        for (int i = 0; i < 2; i++) {
            String s = sides[i];
            tuft[i] = head.getChild(s + "_tuft");
            legF[i] = body.getChild(s + "_leg_f");
            legFB[i] = legF[i].getChild(s + "_leg_f_b");
            legH[i] = body.getChild(s + "_leg_h");
            legHB[i] = legH[i].getChild(s + "_leg_h_b");
            wingA[i] = body.getChild(s + "_wing_a");
            wingB[i] = wingA[i].getChild(s + "_wing_b");
            wingC[i] = wingB[i].getChild(s + "_wing_c");
        }
    }

    public static LayerDefinition createLayer() {
        return GriffinLayout.create();
    }

    @Override
    public void setupAnim(GriffinRenderState state) {
        super.setupAnim(state); // back to the layout's pose

        float t = state.ageInTicks;
        float fly = state.fly;
        float glide = state.glide * fly;
        float fold = 1f - fly;
        float walkSpeed = Math.min(1f, state.walkAnimationSpeed * 1.6f) * fold;
        float pos = state.walkAnimationPos * 0.9f;

        // ---- body: bobs with the gait, breathes, pitches a little in the air
        body.y += -Math.abs(Mth.sin(pos)) * 0.5f * walkSpeed + Mth.sin(t * 0.07f) * 0.2f;
        body.xRot += fly * (Mth.lerp(glide, -0.06f + 0.04f * Mth.sin(t * 0.6f), 0.06f));

        // ---- neck and head: the head follows where it looks (a little), the neck stretches in a glide, the beak clacks now and then
        neck.xRot += 0.05f * Mth.sin(t * 0.09f) + glide * 0.35f;
        head.xRot += -glide * 0.3f + Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f) * 0.6f;
        head.yRot += Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f) * 0.7f;
        jaw.xRot += Math.max(0f, Mth.sin(t * 0.21f + 1f) - 0.85f) * 1.5f;
        for (int i = 0; i < 2; i++) {
            tuft[i].zRot += (i == 0 ? 1 : -1) * 0.08f * Mth.sin(t * 0.13f + i);
        }

        // ---- tail: sways, lifts in the air
        tail.xRot += fly * 0.25f;
        tail.yRot += Mth.sin(t * 0.11f) * 0.12f + Mth.sin(pos) * 0.15f * walkSpeed;

        for (int i = 0; i < 2; i++) {
            float sg = i == 0 ? 1f : -1f;
            // ---- legs: diagonal gait on the ground; forelegs and haunches tuck in the air
            float c = pos + (i == 0 ? 0f : Mth.PI);
            legF[i].xRot += Mth.sin(c) * 0.7f * walkSpeed + fly * 0.9f;
            legFB[i].xRot += Math.max(0f, -Mth.sin(c)) * 0.6f * walkSpeed + fly * 1.0f;
            legH[i].xRot += -Mth.sin(c) * 0.7f * walkSpeed + fly * -0.5f;
            legHB[i].xRot += Math.max(0f, Mth.sin(c)) * 0.6f * walkSpeed + fly * 0.6f;

            // ---- wings: folded in a zig-zag on the ground; flapping (arm leads, forearm and primaries lag) or spread in flight
            float breathe = Mth.sin(t * 0.08f) * 0.03f;
            float f = t * 0.75f;
            float amp = 1f - 0.9f * glide;
            float flapA = Mth.sin(f) * 0.7f * amp + 0.12f + (1f - amp) * Mth.sin(t * 0.1f) * 0.02f;
            float flapB = Mth.sin(f - 0.55f) * 0.5f * amp - 0.03f;
            float flapC = Mth.sin(f - 1.1f) * 0.55f * amp - 0.05f - 0.05f * glide;
            wingA[i].yRot += sg * (fold * FOLD_A + fly * 0.08f * glide);
            wingA[i].zRot += sg * (fold * (0.1f + breathe) + fly * flapA);
            wingB[i].yRot += sg * (fold * FOLD_B + fly * (-0.05f - 0.05f * glide));
            wingB[i].zRot += sg * (fold * breathe + fly * flapB);
            wingB[i].y += -2.1f * fold;
            wingC[i].yRot += sg * (fold * FOLD_C + fly * (-0.1f - 0.1f * glide));
            wingC[i].zRot += sg * fly * flapC;
            wingC[i].y += -1.5f * fold;
        }
    }
}
