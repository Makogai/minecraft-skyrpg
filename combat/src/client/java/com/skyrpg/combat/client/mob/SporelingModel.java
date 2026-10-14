package com.skyrpg.combat.client.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Sporeling: a knee-high mushroom creature. A pale stalk body with glowing eyes in the shadow of a big violet cap
 * (glowing spots on top, glowing gills underneath), little arms and stubby legs.
 * <p>
 * Animation: a waddle (short steps, the body rolling over the planted foot and rising as the feet pass), the cap
 * bobbing a beat behind the body and breathing when idle, arms swinging against the legs, and a head-butt with the cap
 * when it attacks.
 * <p>
 * The cubes come from {@link SporelingLayout} (generated with the texture by tools/art/generate_sporeling.py).
 * Part tree: root > body > (cap, arm_r, arm_l), leg_r, leg_l.
 */
public final class SporelingModel extends EntityModel<SporelingRenderState> {
    private final ModelPart body;
    private final ModelPart cap;
    private final ModelPart armR;
    private final ModelPart armL;
    private final ModelPart legR;
    private final ModelPart legL;

    public SporelingModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        body = root.getChild("body");
        cap = body.getChild("cap");
        armR = body.getChild("arm_r");
        armL = body.getChild("arm_l");
        legR = root.getChild("leg_r");
        legL = root.getChild("leg_l");
    }

    public static LayerDefinition createLayer() {
        return SporelingLayout.create();
    }

    @Override
    public void setupAnim(SporelingRenderState state) {
        super.setupAnim(state); // back to the layout's pose

        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.5f);
        float pos = state.walkAnimationPos * 0.9f;
        float s = Mth.sin(pos);
        float passing = 1f - Math.abs(s); // 1 while the feet pass each other

        // ---- the waddle: the planted foot stays on the ground, the one swinging forward lifts
        float c = Mth.cos(pos);
        legR.xRot = 0.6f * s * speed;
        legL.xRot = -0.6f * s * speed;
        legR.y -= sink(legR.xRot) + Math.max(0f, -c) * speed;
        legL.y -= sink(legL.xRot) + Math.max(0f, c) * speed;
        body.zRot = 0.16f * s * speed;
        body.xRot = 0.08f * speed;
        body.y -= passing * 0.7f * speed;

        // ---- the cap: bobs a beat behind the body, sways the other way, breathes when idle
        cap.y += Mth.sin(pos * 2f - 0.9f) * 0.45f * speed + Mth.sin(t * 0.09f) * 0.25f;
        cap.zRot = -0.09f * Mth.sin(pos - 0.6f) * speed;
        cap.xRot = -0.05f * speed;
        float breathe = 1f + 0.025f * Mth.sin(t * 0.09f);
        cap.xScale = breathe;
        cap.zScale = breathe;
        cap.yScale = 2f - breathe;

        // ---- arms swing against the legs and flap out a little at each step
        float flap = 0.25f * Math.abs(s) * speed + 0.05f * Mth.sin(t * 0.12f);
        armR.xRot = -0.7f * s * speed;
        armL.xRot = 0.7f * s * speed;
        armR.zRot += flap;
        armL.zRot -= flap;

        // ---- it turns its body (and cap) a little towards what it looks at
        body.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.8f, 0.8f) * 0.35f;
        cap.xRot += Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f) * 0.3f;

        // ---- attack: a head-butt with the cap, arms flung up
        float a = state.attackTime;
        if (a > 0f) {
            float k = Mth.sin(a * Mth.PI);
            body.xRot += 0.55f * k;
            cap.xRot += 0.25f * k;
            armR.xRot -= 2.0f * k;
            armL.xRot -= 2.0f * k;
        }
    }

    /** How far a 3-pixel leg swung by {@code xRot} would dip below the ground (its toe sticks out 1 pixel in front). */
    private static float sink(float xRot) {
        return 3f * Mth.cos(xRot) + (xRot > 0f ? 2.5f : 1.5f) * Math.abs(Mth.sin(xRot)) - 3f;
    }
}
