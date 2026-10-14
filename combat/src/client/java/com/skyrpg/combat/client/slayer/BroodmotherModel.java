package com.skyrpg.combat.client.slayer;

import com.skyrpg.combat.slayer.boss.BroodmotherEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Broodmother: a bloated spider queen. A low wide cephalothorax, a huge swollen egg-sac abdomen that pulses,
 * eight long three-segment legs, big curved mandibles that open, a cluster of eight glowing eyes and bristly hair.
 * <p>
 * Tiers: I plain brown-black chitin; II armour plates on the back and knee pads, venom dripping from the fangs, faintly
 * glowing abdomen veins; III spiked carapace ridge, egg sacs on her back, hanging silk, brighter venom markings;
 * IV a crown of horn spikes, a glowing venom abdomen with spiderlings inside, bone-white markings, brightest eyes.
 * <p>
 * The cubes come from {@link BroodmotherLayout} (generated with the textures by tools/art/generate_broodmother.py).
 * Part tree: root > body > (plates, ridge, head > (mand_{r,l} > fang > tip > drip, crown),
 * abdomen > sac > (sac_plates, sac_ridge, back_eggs, silk0..3), leg_{r,l}{0..3} > leg_*_a > leg_*_b > leg_*_c
 * (+ pad_*_1 under _a, pad_*_2 under _b)), cocoon, shield > sl0..sl5.
 */
public final class BroodmotherModel extends EntityModel<BroodmotherRenderState> {
    /** Ticks of the pounce pose (BroodmotherEntity.POUNCE_POSE_TICKS): crouch 0-14, spring 14-19, recover 19-24. */
    private static final float POUNCE_TOTAL = BroodmotherEntity.POUNCE_POSE_TICKS;
    private static final float CROUCH_END = 14f;
    private static final float SPRING_END = 19f;

    private final ModelPart body;
    private final ModelPart plates;
    private final ModelPart ridge;
    private final ModelPart head;
    private final ModelPart[] mandible = new ModelPart[2];
    private final ModelPart[] fang = new ModelPart[2];
    private final ModelPart[] tip = new ModelPart[2];
    private final ModelPart[] drip = new ModelPart[2];
    private final ModelPart crown;
    private final ModelPart abdomen;
    private final ModelPart sac;
    private final ModelPart sacPlates;
    private final ModelPart sacRidge;
    private final ModelPart backEggs;
    private final ModelPart[] silk = new ModelPart[4];
    /** Legs 0-3 are the right side (front to back), 4-7 the left side. */
    private final ModelPart[] leg = new ModelPart[8];
    private final ModelPart[] femur = new ModelPart[8];
    private final ModelPart[] tibia = new ModelPart[8];
    private final ModelPart[] tarsus = new ModelPart[8];
    private final ModelPart[] pad1 = new ModelPart[8];
    private final ModelPart[] pad2 = new ModelPart[8];
    private final ModelPart cocoon;
    private final ModelPart shield;
    private final ModelPart[] spiderling = new ModelPart[6];

    public BroodmotherModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        body = root.getChild("body");
        plates = body.getChild("plates");
        ridge = body.getChild("ridge");
        head = body.getChild("head");
        for (int s = 0; s < 2; s++) {
            String n = s == 0 ? "r" : "l";
            mandible[s] = head.getChild("mand_" + n);
            fang[s] = mandible[s].getChild("fang_" + n);
            tip[s] = fang[s].getChild("tip_" + n);
            drip[s] = tip[s].getChild("drip_" + n);
        }
        crown = head.getChild("crown");
        abdomen = body.getChild("abdomen");
        sac = abdomen.getChild("sac");
        sacPlates = sac.getChild("sac_plates");
        sacRidge = sac.getChild("sac_ridge");
        backEggs = sac.getChild("back_eggs");
        for (int i = 0; i < 4; i++) {
            silk[i] = sac.getChild("silk" + i);
        }
        for (int k = 0; k < 8; k++) {
            String n = (k < 4 ? "r" : "l") + (k % 4);
            leg[k] = body.getChild("leg_" + n);
            femur[k] = leg[k].getChild("leg_" + n + "_a");
            tibia[k] = femur[k].getChild("leg_" + n + "_b");
            tarsus[k] = tibia[k].getChild("leg_" + n + "_c");
            pad1[k] = femur[k].getChild("pad_" + n + "_1");
            pad2[k] = tibia[k].getChild("pad_" + n + "_2");
        }
        cocoon = root.getChild("cocoon");
        shield = root.getChild("shield");
        for (int i = 0; i < 6; i++) {
            spiderling[i] = shield.getChild("sl" + i);
        }
    }

    public static LayerDefinition createLayer() {
        return BroodmotherLayout.create();
    }

    @Override
    public void setupAnim(BroodmotherRenderState state) {
        super.setupAnim(state);
        resetPose();

        int tier = state.tier;
        float t = state.ageInTicks;
        float f = state.cocoon;
        float walkFade = 1f - f;
        float speed = Math.min(1f, state.walkAnimationSpeed * 2f) * walkFade;
        float pos = state.walkAnimationPos * 1.2f;

        // ---- what each tier shows
        plates.visible = tier >= 2;
        sacPlates.visible = tier >= 2;
        ridge.visible = tier >= 3;
        sacRidge.visible = tier >= 3;
        backEggs.visible = tier >= 3;
        for (ModelPart s : silk) {
            s.visible = tier >= 3;
        }
        crown.visible = tier >= 4;
        for (int s = 0; s < 2; s++) {
            drip[s].visible = tier >= 2;
        }
        for (int k = 0; k < 8; k++) {
            pad1[k].visible = tier >= 2;
            pad2[k].visible = tier >= 2;
        }
        cocoon.visible = f > 0.02f;
        shield.visible = state.shield > 0.02f;

        // ---- special poses
        float crouch = 0f;
        float air = 0f;
        if (state.pounceTicks > 0f) {
            float e = Math.max(0f, POUNCE_TOTAL - state.pounceTicks);
            if (e < CROUCH_END) {
                crouch = smooth(e / CROUCH_END);
            } else if (e < SPRING_END) {
                crouch = 1f - smooth((e - CROUCH_END) / 2f);
                air = smooth((e - CROUCH_END) / 3f);
            } else {
                air = 1f - smooth((e - SPRING_END) / (POUNCE_TOTAL - SPRING_END));
            }
        }
        crouch *= walkFade;
        air *= walkFade;
        float quiver = Mth.sin(t * 2.6f) * 0.03f * crouch;

        // the bite: mandibles open wide, then snap shut around attackTime 0.5-0.62
        float a = state.attackTime;
        float bite = 0f;
        if (a > 0f && f < 0.5f) {
            bite = a < 0.45f ? smooth(a / 0.45f) : 1f - smooth((a - 0.45f) / 0.17f);
        }
        float snap = a >= 0.45f && a < 0.8f ? 1f - smooth((a - 0.45f) / 0.35f) : 0f;

        // idle: the abdomen pulses, the mandibles twitch now and then
        float pulseRate = 0.1f + 0.025f * tier;
        float pulse = 1f + (0.045f + 0.01f * tier) * Mth.sin(t * pulseRate) + 0.02f * Mth.sin(t * 0.31f + 1f);
        float twitch = Mth.clamp((Mth.sin(t * 0.9f) * Mth.sin(t * 0.17f) - 0.55f) * 3f, 0f, 1f);
        float open = 0.08f + twitch * 0.25f + bite * 0.7f + crouch * 0.3f + air * 0.9f;

        // ---- body
        body.y += -Math.abs(Mth.sin(pos)) * 0.7f * speed + Mth.sin(t * 0.07f) * 0.3f * walkFade + 5f * crouch - 3f * air + 1.5f * f;
        body.xRot = 0.12f * crouch - 0.4f * air + (-0.1f * bite) + quiver;
        body.zRot = Mth.cos(pos) * 0.04f * speed + quiver;
        body.z += -1.5f * bite;

        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.7f, 0.7f) * walkFade;
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.5f, 0.5f) * walkFade;
        head.yRot = yaw;
        head.xRot = pitch + 0.3f * snap - 0.15f * air;
        mandible[0].yRot += open;
        mandible[1].yRot -= open;
        mandible[0].xRot += 0.2f * snap;
        mandible[1].xRot += 0.2f * snap;
        // venom drips down the fangs
        for (int s = 0; s < 2; s++) {
            float frac = (t * 0.035f + s * 0.5f) % 1f;
            drip[s].y += frac * 2f;
            drip[s].yScale = 1f - frac * 0.6f;
        }

        // ---- abdomen
        abdomen.xRot = Mth.sin(t * 0.05f) * 0.03f + Mth.cos(pos) * 0.05f * speed + 0.25f * crouch - 0.3f * air;
        abdomen.yRot = Mth.sin(pos * 0.5f) * 0.06f * speed;
        sac.xScale = pulse;
        sac.zScale = pulse;
        sac.yScale = 1f + (pulse - 1f) * 1.3f;
        for (int i = 0; i < 4; i++) {
            silk[i].xRot = Mth.sin(t * 0.08f + i * 1.7f) * 0.12f + (speed * 0.5f + 0.25f * crouch - 0.2f * air);
            silk[i].zRot = Mth.cos(t * 0.06f + i) * 0.1f;
        }

        // ---- legs: tetrapod gait from the limb swing, folded away while cocooned
        for (int k = 0; k < 8; k++) {
            int side = k < 4 ? 0 : 1;
            float sg = side == 0 ? 1f : -1f;
            float c = pos + ((k % 4 + side) % 2) * Mth.PI;
            float lift = Math.max(0f, -Mth.cos(c)) * speed;
            leg[k].yRot += sg * 0.3f * Mth.sin(c) * speed;
            femur[k].zRot += sg * (lift * 0.5f + crouch * 0.35f - air * 0.4f + Mth.sin(t * 0.05f + k) * 0.03f * walkFade);
            tibia[k].zRot += sg * (-lift * 0.4f + air * 0.5f);
            tarsus[k].zRot += sg * (lift * 0.2f);
            if (f > 0f) {
                leg[k].yRot = Mth.lerp(f, leg[k].yRot, leg[k].yRot * 0.4f);
                femur[k].zRot = Mth.lerp(f, femur[k].zRot, sg * 1.35f);
                tibia[k].zRot = Mth.lerp(f, tibia[k].zRot, sg * -2.6f);
                tarsus[k].zRot = Mth.lerp(f, tarsus[k].zRot, sg * -0.3f);
            }
            float ls = 1f - 0.4f * f;
            leg[k].xScale = ls;
            leg[k].yScale = ls;
            leg[k].zScale = ls;
        }

        // ---- the cocoon closes in around her, and breathes a little
        if (cocoon.visible) {
            float grow = 0.6f + 0.4f * f + Mth.sin(t * 0.3f) * 0.012f;
            cocoon.xScale = grow;
            cocoon.yScale = grow;
            cocoon.zScale = grow;
        }

        // ---- the spiderling ring circles her and bobs
        if (shield.visible) {
            float s = state.shield;
            shield.yRot = t * 0.06f;
            for (int i = 0; i < 6; i++) {
                spiderling[i].y += Mth.sin(t * 0.12f + i * 1.1f) * 1.5f;
                spiderling[i].zRot = Mth.sin(t * 0.2f + i) * 0.1f;
                spiderling[i].xScale = s;
                spiderling[i].yScale = s;
                spiderling[i].zScale = s;
            }
        }
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
