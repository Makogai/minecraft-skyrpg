package com.skyrpg.combat.client.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Fire Beetle: a big six-legged beetle with a black basalt shell split by glowing magma cracks. A domed pair of
 * wing cases over a molten body, a pronotum with two short horns, a low head with hooked mandibles, three jointed legs
 * a side.
 * <p>
 * Animation: a tripod gait (right front, left middle and right back legs step together, then the other three), the
 * mandibles twitching and snapping shut on a bite, and the charge (see FireBeetleEntity): in the wind-up it crouches,
 * noses down and quivers, its wing cases lift and part to show the molten body, its mandibles open and a front leg
 * paws the ground; in the dash it runs low with its mandibles wide.
 * <p>
 * The cubes come from {@link FireBeetleLayout} (generated with the texture by tools/art/generate_fire_beetle.py).
 * Part tree: root > body > (horn_{r,l} > horn_{r,l}_tip, head > mand_{r,l} > mand_{r,l}_tip, elytra_r, elytra_l,
 * leg_{r,l}{0..2} > leg_*_a (femur) > leg_*_b (tibia)).
 */
public final class FireBeetleModel extends EntityModel<FireBeetleRenderState> {
    /** Femur lift per leg pair (front, middle, back) at full wind-up and full dash, so the feet stay on the ground. */
    private static final float[] WINDUP_LIFT = {0.45f, 0.23f, -0.02f};
    private static final float[] DASH_LIFT = {0.29f, 0.12f, -0.03f};

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart elytraR;
    private final ModelPart elytraL;
    /** Right then left. */
    private final ModelPart[] mandible = new ModelPart[2];
    /** Legs 0-2 are the right side (front to back), 3-5 the left side. */
    private final ModelPart[] leg = new ModelPart[6];
    private final ModelPart[] femur = new ModelPart[6];
    private final ModelPart[] tibia = new ModelPart[6];

    public FireBeetleModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        body = root.getChild("body");
        head = body.getChild("head");
        mandible[0] = head.getChild("mand_r");
        mandible[1] = head.getChild("mand_l");
        elytraR = body.getChild("elytra_r");
        elytraL = body.getChild("elytra_l");
        for (int k = 0; k < 6; k++) {
            String n = (k < 3 ? "r" : "l") + (k % 3);
            leg[k] = body.getChild("leg_" + n);
            femur[k] = leg[k].getChild("leg_" + n + "_a");
            tibia[k] = femur[k].getChild("leg_" + n + "_b");
        }
    }

    public static LayerDefinition createLayer() {
        return FireBeetleLayout.create();
    }

    @Override
    public void setupAnim(FireBeetleRenderState state) {
        super.setupAnim(state); // back to the layout's pose

        float t = state.ageInTicks;
        float windup = state.windup;
        float dash = state.dash;
        float still = 1f - windup; // it stops walking while it digs in
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.4f) * still;
        float pos = state.walkAnimationPos * 1.3f;

        // ---- body: bobs with the gait; crouches and noses down for the charge, quivering in the wind-up
        float quiver = Mth.sin(t * 2.7f) * 0.025f * windup;
        body.y += -Math.abs(Mth.sin(pos)) * 0.35f * speed + 1.3f * windup + 0.6f * dash;
        body.xRot += 0.16f * windup + 0.12f * dash + quiver;
        body.zRot += Mth.cos(pos) * 0.025f * speed + quiver;

        // ---- head: follows the look a little, dips for the charge
        float free = Math.max(0f, 1f - windup - dash);
        head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.5f, 0.5f) * 0.6f * free;
        head.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.4f, 0.4f) * 0.5f * free + 0.25f * windup + 0.2f * dash;

        // ---- mandibles: an idle twitch now and then, open-and-snap on a bite, wide open for the charge
        float a = state.attackTime;
        float bite = 0f;
        if (a > 0f) {
            bite = a < 0.5f ? smooth(a / 0.5f) : 1f - smooth((a - 0.5f) / 0.2f);
        }
        float twitch = Mth.clamp((Mth.sin(t * 0.7f) * Mth.sin(t * 0.13f) - 0.6f) * 3f, 0f, 1f);
        float open = 0.06f + 0.2f * twitch + 0.65f * bite + 0.55f * windup + 0.75f * dash;
        mandible[0].yRot += open;
        mandible[1].yRot -= open;

        // ---- wing cases: lift and part in the wind-up (showing the molten body), with a flutter
        float flutter = Mth.sin(t * 1.9f) * 0.05f * windup;
        elytraR.xRot += 0.3f * windup + flutter;
        elytraR.yRot += 0.12f * windup;
        elytraR.zRot += 0.32f * windup + flutter;
        elytraL.xRot += 0.3f * windup + flutter;
        elytraL.yRot -= 0.12f * windup;
        elytraL.zRot -= 0.32f * windup + flutter;

        // ---- legs: tripod gait (faster in the dash, as it covers ground fast); dug in and pawing in the wind-up
        for (int k = 0; k < 6; k++) {
            int side = k < 3 ? 0 : 1;
            int i = k % 3;
            float sg = side == 0 ? 1f : -1f;
            float c = pos + ((i + side) % 2) * Mth.PI;
            float lift = Math.max(0f, -Mth.cos(c)) * speed;
            leg[k].yRot += sg * 0.35f * Mth.sin(c) * speed;
            femur[k].zRot += sg * 0.35f * lift;
            tibia[k].zRot -= sg * 0.15f * lift;
            // the femurs rise to keep the feet planted as the body crouches and noses down; the back legs brace
            femur[k].zRot += sg * (WINDUP_LIFT[i] * windup + DASH_LIFT[i] * dash);
            if (i == 2) {
                leg[k].yRot += sg * 0.2f * windup;
            }
        }
        // the right front leg paws the ground like a bull
        float paw = Math.max(0f, Mth.sin(t * 0.8f)) * windup;
        femur[0].zRot += 0.45f * paw;
        leg[0].yRot -= 0.25f * paw;
    }

    static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
