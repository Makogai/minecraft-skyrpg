package com.skyrpg.brainrots.client;

import com.skyrpg.brainrots.BrainrotKind;
import com.skyrpg.brainrots.client.model.BrainrotLayouts;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Every brainrot in one model: the root holds each kind's own model (baked from its generated layout, with its own
 * texture size), and only the one being drawn is visible. Animation is by part name, so any model drawn with the usual
 * names moves: legs walk ("leg_r", "leg_l", "leg_m", "leg_br", "leg_bl"), arms swing, wings flap, tails and trunks
 * sway, ears wiggle, rings spin, propellers whirl, tyre hubs roll, antennae twitch; the body bobs (or bounces, for
 * hoppers) and the head looks around.
 */
public final class BrainrotModel extends EntityModel<BrainrotRenderState> {
    private final Map<BrainrotKind, ModelPart> roots = new EnumMap<>(BrainrotKind.class);
    private final Map<BrainrotKind, Map<String, ModelPart>> parts = new EnumMap<>(BrainrotKind.class);

    public BrainrotModel(ModelPart root, Map<BrainrotKind, ModelPart> kinds) {
        super(root, RenderTypes::entityCutout);
        for (Map.Entry<BrainrotKind, ModelPart> entry : kinds.entrySet()) {
            roots.put(entry.getKey(), entry.getValue());
            BrainrotLayouts.Entry layout = BrainrotLayouts.get(entry.getKey().id);
            Map<String, ModelPart> byName = new HashMap<>();
            if (layout != null) {
                for (String path : layout.parts()) {
                    ModelPart part = entry.getValue();
                    for (String step : path.split("/")) {
                        part = part.getChild(step);
                    }
                    byName.put(path.substring(path.lastIndexOf('/') + 1), part);
                }
            }
            parts.put(entry.getKey(), byName);
        }
    }

    @Override
    public void setupAnim(BrainrotRenderState state) {
        super.setupAnim(state);
        for (Map.Entry<BrainrotKind, ModelPart> entry : roots.entrySet()) {
            entry.getValue().visible = entry.getKey() == state.kind;
        }
        Map<String, ModelPart> p = parts.get(state.kind);
        if (p == null) {
            return;
        }
        float t = state.ageInTicks;
        float speed = Math.min(1f, state.walkAnimationSpeed * 1.4f);
        float pos = state.walkAnimationPos;
        BrainrotKind.Move move = state.kind.move;
        boolean airborne = move == BrainrotKind.Move.FLY || move == BrainrotKind.Move.FLOAT;

        // ---- legs: a walk cycle (airborne ones paddle gently)
        float legSwing = airborne ? 0.25f : 0.7f * speed;
        float legPhase = airborne ? t * 0.15f : pos * 0.8f;
        swing(p.get("leg_r"), legPhase, legSwing);
        swing(p.get("leg_l"), legPhase + Mth.PI, legSwing);
        swing(p.get("leg_m"), legPhase + Mth.HALF_PI, legSwing);
        swing(p.get("leg_br"), legPhase + Mth.PI, legSwing);
        swing(p.get("leg_bl"), legPhase, legSwing);

        // ---- arms against the legs, plus a lazy idle sway
        ModelPart armR = p.get("arm_r");
        ModelPart armL = p.get("arm_l");
        if (armR != null) {
            armR.xRot += -Mth.sin(pos * 0.8f) * 0.6f * speed + Mth.sin(t * 0.07f) * 0.06f;
            armR.zRot += Mth.sin(t * 0.09f) * 0.05f;
        }
        if (armL != null) {
            armL.xRot += Mth.sin(pos * 0.8f) * 0.6f * speed - Mth.sin(t * 0.07f) * 0.06f;
            armL.zRot -= Mth.sin(t * 0.09f) * 0.05f;
        }

        // ---- wings flap (fast for fliers), tails and trunks sway, ears wiggle, antennae twitch
        float flap = move == BrainrotKind.Move.FLY ? 0.08f + 0.18f * Mth.sin(t * 0.45f) : 0.1f * Mth.sin(t * 0.2f);
        rotZ(p.get("wing_r"), flap);
        rotZ(p.get("wing_l"), -flap);
        rotY(p.get("tail"), Mth.sin(t * 0.18f) * 0.35f + Mth.sin(pos * 0.8f) * 0.2f * speed);
        rotY(p.get("tail_tip"), Mth.sin(t * 0.18f - 0.8f) * 0.4f);
        rotX(p.get("trunk"), Mth.sin(t * 0.12f) * 0.22f);
        rotZ(p.get("ear_r"), Mth.sin(t * 0.21f) * 0.16f);
        rotZ(p.get("ear_l"), -Mth.sin(t * 0.21f) * 0.16f);
        rotX(p.get("ant_r"), Mth.sin(t * 0.3f) * 0.18f);
        rotX(p.get("ant_l"), Mth.sin(t * 0.3f + 1.3f) * 0.18f);
        for (String peel : new String[]{"peel_f", "peel_b", "peel_r", "peel_l"}) {
            rotX(p.get(peel), Mth.sin(t * 0.15f + peel.hashCode()) * 0.08f);
        }
        rotY(p.get("seg2"), Mth.sin(t * 0.2f) * 0.1f);
        rotY(p.get("seg3"), Mth.sin(t * 0.2f - 0.5f) * 0.14f);
        rotY(p.get("seg4"), Mth.sin(t * 0.2f - 1.0f) * 0.18f);

        // ---- spinning bits
        rotY(p.get("ring"), t * 0.05f);
        rotZ(p.get("prop"), t * 1.4f);
        rotZ(p.get("prop_r"), t * 1.4f);
        rotZ(p.get("prop_l"), -t * 1.4f);
        rotX(p.get("hub_r"), pos * 0.6f);
        rotX(p.get("hub_l"), pos * 0.6f);

        // ---- the head looks at what it's looking at, and nods a little
        ModelPart head = p.get("head");
        if (head != null) {
            head.yRot += Mth.clamp(state.yRot, -50f, 50f) * Mth.DEG_TO_RAD * 0.7f;
            head.xRot += Mth.clamp(state.xRot, -30f, 30f) * Mth.DEG_TO_RAD * 0.6f + Mth.sin(t * 0.08f) * 0.04f;
        }

        // ---- the body: idle bob, a bounce for hoppers, a gentle float or bank for the airborne
        ModelPart body = p.get("body");
        if (body != null) {
            switch (move) {
                case HOP -> {
                    body.y -= Math.abs(Mth.sin(t * 0.3f)) * 1.2f;
                    body.zRot += Mth.sin(t * 0.3f) * 0.05f;
                }
                case FLY -> {
                    body.zRot += Mth.sin(t * 0.05f) * 0.18f;
                    body.xRot += Mth.sin(t * 0.07f) * 0.05f;
                }
                case FLOAT -> body.y += Mth.sin(t * 0.08f) * 0.8f;
                default -> {
                    body.y += Mth.sin(t * 0.1f) * 0.25f - Math.abs(Mth.sin(pos * 0.8f)) * 0.6f * speed;
                    body.zRot += Mth.sin(pos * 0.8f) * 0.06f * speed;
                }
            }
        }
    }

    private static void swing(ModelPart part, float phase, float amount) {
        if (part != null) {
            part.xRot += Mth.sin(phase) * amount;
        }
    }

    private static void rotX(ModelPart part, float r) {
        if (part != null) {
            part.xRot += r;
        }
    }

    private static void rotY(ModelPart part, float r) {
        if (part != null) {
            part.yRot += r;
        }
    }

    private static void rotZ(ModelPart part, float r) {
        if (part != null) {
            part.zRot += r;
        }
    }
}
