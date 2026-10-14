package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.BoneGiantEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Bone Giant: a hunched giant skeleton. A cracked skull with an underbite jaw, tusks and soul-blue eyes, a hollow
 * ribcage with soul fire burning between the ribs, bone spikes along the hunched spine, long knuckle-dragging arms, and a
 * femur club (studded with obsidian shards) trailing from its right hand.
 * <p>
 * The cubes come from {@link BoneGiantLayout} (generated with the textures by tools/art/generate_bone_giant.py, whose
 * preview poses mirror this class). Part tree: root > hips > torso > (ribs, spikes, soul, neck > head > jaw, r_arm >
 * r_forearm > r_hand > club, l_arm > l_forearm > l_hand), and r_leg > r_shin, l_leg > l_shin under the root.
 * <p>
 * Animation: a lumbering walk (long slow strides, knees bending on the forward swing, the body rolling, the club
 * dragging), the club swing (state.swingTicks, synced from BoneGiantEntity: the club goes up over the shoulder and comes
 * down on the ground in front), the bone quake (state.quakeTicks: it rears up roaring with both arms raised and a knee
 * up, trembles for the telegraph, then stomps and slams its fists down), the jaw chattering, the head swaying when it
 * stands still, and the soul fire flickering (its glow flickers in BoneGiantRenderer).
 */
public final class BoneGiantModel extends EntityModel<BoneGiantRenderState> {
    // pose vector, as BoneGiantLayout's key poses (deltas added to the rest pose): right arm x / z, right forearm, club,
    // left arm x / z, left forearm, torso lean, torso twist, head pitch, jaw, right leg, right shin, left leg, left shin,
    // hips drop (model pixels)
    private static final int RA = 0, RAZ = 1, RF = 2, CLUB = 3, LA = 4, LAZ = 5, LF = 6, LEAN = 7, TWIST = 8, HEAD = 9, JAW = 10,
            RL = 11, RS = 12, LL = 13, LS = 14, DROP = 15, KEYS = 16;

    private final ModelPart hips;
    private final ModelPart torso;
    private final ModelPart soul;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart club;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;

    public BoneGiantModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        hips = root.getChild("hips");
        torso = hips.getChild("torso");
        soul = torso.getChild("soul");
        neck = torso.getChild("neck");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        rightArm = torso.getChild("r_arm");
        rightForearm = rightArm.getChild("r_forearm");
        club = rightForearm.getChild("r_hand").getChild("club");
        leftArm = torso.getChild("l_arm");
        leftForearm = leftArm.getChild("l_forearm");
        rightLeg = root.getChild("r_leg");
        rightShin = rightLeg.getChild("r_shin");
        leftLeg = root.getChild("l_leg");
        leftShin = leftLeg.getChild("l_shin");
    }

    public static LayerDefinition createLayer() {
        return BoneGiantLayout.create();
    }

    /** How brightly the soul fire burns at this age (0 to 1); the renderer brightens its glow with it. */
    static float flicker(float ageInTicks) {
        return Mth.clamp(0.55f + 0.3f * Mth.sin(ageInTicks * 0.21f) + 0.15f * Mth.sin(ageInTicks * 0.53f), 0f, 1f);
    }

    @Override
    public void setupAnim(BoneGiantRenderState state) {
        super.setupAnim(state);
        resetPose();
        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.5f);
        float pos = state.walkAnimationPos * 0.4f;
        float c = Mth.cos(pos);
        float s = Mth.sin(pos);
        float lift = (float) Math.pow(Math.abs(s), 0.6);
        float breath = Mth.sin(t * 0.05f);

        // ---- the lumbering walk: long slow strides, knees bending on the forward swing, the body rolling side to side
        float[] cur = new float[KEYS];
        cur[RL] = c * 0.55f * speed;
        cur[LL] = -c * 0.55f * speed;
        cur[RS] = Math.max(0f, s) * 0.75f * speed;
        cur[LS] = Math.max(0f, -s) * 0.75f * speed;
        cur[RA] = -c * 0.22f * speed;
        cur[LA] = c * 0.4f * speed;
        cur[LEAN] = 0.06f * speed + breath * 0.025f;
        cur[TWIST] = c * 0.1f * speed;
        cur[JAW] = 0.04f + 0.04f * Mth.sin(t * 0.09f);
        cur[DROP] = -lift * 1.4f * speed;
        float roll = Mth.sin(pos) * 0.06f * speed;

        // ---- the club swing or the bone quake over it
        float w = 0f;
        if (state.swingTicks > 0f) {
            w = swing(cur, BoneGiantEntity.SWING_TICKS - state.swingTicks);
        } else if (state.quakeTicks > 0f) {
            w = quake(cur, BoneGiantEntity.QUAKE_TICKS - state.quakeTicks, t);
        }
        roll *= 1f - w;

        // ---- apply
        hips.y += cur[DROP];
        hips.zRot += roll;
        torso.xRot += cur[LEAN];
        torso.yRot += cur[TWIST];
        rightArm.xRot += cur[RA];
        rightArm.zRot += cur[RAZ];
        rightForearm.xRot += cur[RF];
        club.xRot += cur[CLUB];
        leftArm.xRot += cur[LA];
        leftArm.zRot += cur[LAZ];
        leftForearm.xRot += cur[LF];
        jaw.xRot += cur[JAW];
        rightLeg.xRot += cur[RL];
        rightShin.xRot += cur[RS];
        leftLeg.xRot += cur[LL];
        leftShin.xRot += cur[LS];

        // the head looks where the giant does, levels out against the lean, and sways when it stands still
        float calm = (1f - speed) * (1f - w);
        float yaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.6f, 0.6f);
        float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.4f, 0.4f);
        head.xRot += cur[HEAD] + pitch - cur[LEAN] * 0.5f;
        head.yRot += yaw + Mth.sin(t * 0.03f) * 0.15f * calm - cur[TWIST] * 0.6f;
        neck.zRot -= roll * 0.6f;

        float swell = 1f + 0.06f * (float) Math.pow(Math.max(0f, Mth.sin(t * 0.21f)), 2);
        soul.xScale = swell;
        soul.yScale = swell;
        soul.zScale = swell;
    }

    /** Blends the club swing over {@code cur}: raise 0-8, smash 8-11, hold to 13, recover to 20. Returns its weight. */
    private static float swing(float[] cur, float e) {
        float[] raise = BoneGiantLayout.SWING_RAISE;
        float[] hit = BoneGiantLayout.SWING_HIT;
        float[] target;
        float w;
        if (e < 8f) {
            target = raise;
            w = smooth(e / 8f);
        } else if (e < 11f) {
            target = lerp(raise, hit, smooth((e - 8f) / 3f));
            w = 1f;
        } else if (e < 13f) {
            target = hit;
            w = 1f;
        } else {
            target = hit;
            w = 1f - smooth((e - 13f) / 7f);
        }
        blend(cur, target, w);
        return w;
    }

    /**
     * Blends the bone quake over {@code cur}: rear up 0-12, hold trembling to 20, stomp 20-23 (it lands at 22), hold to
     * 26, recover to 34. Returns its weight.
     */
    private static float quake(float[] cur, float e, float t) {
        float[] raise = BoneGiantLayout.QUAKE_RAISE;
        float[] hit = BoneGiantLayout.QUAKE_HIT;
        float[] target;
        float w;
        if (e < 12f) {
            target = raise;
            w = smooth(e / 12f);
        } else if (e < 20f) {
            target = raise.clone();
            float shake = Mth.sin(t * 2.6f) * 0.04f;
            target[LEAN] += shake;
            target[TWIST] += shake;
            w = 1f;
        } else if (e < 23f) {
            target = lerp(raise, hit, smooth((e - 20f) / 2.5f));
            w = 1f;
        } else if (e < 26f) {
            target = hit;
            w = 1f;
        } else {
            target = hit;
            w = 1f - smooth((e - 26f) / 8f);
        }
        blend(cur, target, w);
        return w;
    }

    private static void blend(float[] cur, float[] target, float w) {
        for (int i = 0; i < cur.length; i++) {
            cur[i] += (target[i] - cur[i]) * w;
        }
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
