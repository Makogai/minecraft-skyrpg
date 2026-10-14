package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.EnderWyrmEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Ender Wyrm: a horned head with a glowing void maw and a chain of body segments, small wings on the first three, a
 * tail fin. The cubes come from {@link EnderWyrmLayout} (generated with the textures by tools/art/generate_ender_wyrm.py,
 * whose preview mirrors {@link #place}).
 * <p>
 * Every segment is a root part that {@link #setupAnim} puts at its own position along the head's path: the renderer does
 * not rotate the model with the entity, the parts carry world-aligned positions and headings (state.body), so the body
 * can curve like a snake. Convention (model space is mirrored in x and y against the world, see the generator):
 * x = -dx * 16, y = 24 - dy * 16, z = dz * 16, yRot = yaw - pi, xRot = pitch.
 */
public final class EnderWyrmModel extends EntityModel<EnderWyrmRenderState> {
    private static final float HEAD_SCALE = 1.5f;

    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] segments = new ModelPart[EnderWyrmLayout.SEGMENTS];
    private final ModelPart[] rightWings = new ModelPart[EnderWyrmLayout.SEGMENTS];
    private final ModelPart[] leftWings = new ModelPart[EnderWyrmLayout.SEGMENTS];
    private final ModelPart[] rightWebs = new ModelPart[EnderWyrmLayout.SEGMENTS];
    private final ModelPart[] leftWebs = new ModelPart[EnderWyrmLayout.SEGMENTS];

    public EnderWyrmModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        for (int i = 0; i < segments.length; i++) {
            segments[i] = root.getChild("seg" + i);
        }
        for (int i : EnderWyrmLayout.WINGED) {
            rightWings[i] = segments[i].getChild("r_wing" + i);
            leftWings[i] = segments[i].getChild("l_wing" + i);
            rightWebs[i] = rightWings[i].getChild("r_web" + i);
            leftWebs[i] = leftWings[i].getChild("l_web" + i);
        }
    }

    public static LayerDefinition createLayer() {
        return EnderWyrmLayout.create();
    }

    /** The glow's pulse (0 to 1) at this age, quickened by the phase. */
    static float pulse(float ageInTicks, int phase) {
        return 0.5f + 0.5f * Mth.sin(ageInTicks * (0.1f + 0.04f * phase));
    }

    private static void place(ModelPart part, EnderWyrmEntity.BodyPoint p) {
        part.x = (float) (-p.dx() * 16);
        part.y = (float) (24 - p.dy() * 16);
        part.z = (float) (p.dz() * 16);
        part.yRot = p.yaw() * Mth.DEG_TO_RAD - Mth.PI;
        part.xRot = p.pitch() * Mth.DEG_TO_RAD;
        part.zRot = 0;
    }

    @Override
    public void setupAnim(EnderWyrmRenderState state) {
        super.setupAnim(state);
        resetPose();
        if (state.body.length < segments.length + 1) {
            return;
        }
        float t = state.ageInTicks;
        place(head, state.body[0]);
        head.xScale = HEAD_SCALE;
        head.yScale = HEAD_SCALE;
        head.zScale = HEAD_SCALE;
        boolean open = switch (state.mode) {
            case BREATH_WIND, BREATH, SUMMON_WIND, STORM_WIND, DIVE_WIND -> true;
            default -> false;
        };
        jaw.xRot = open ? (state.mode == EnderWyrmEntity.Mode.BREATH ? 0.75f : 0.55f) : 0.08f + 0.05f * Mth.sin(t * 0.1f);
        for (int i = 0; i < segments.length; i++) {
            place(segments[i], state.body[1 + i]);
        }
        for (int i : EnderWyrmLayout.WINGED) {
            float flap = Mth.sin(t * 0.18f - i * 0.5f) * 0.45f + 0.15f;
            rightWings[i].zRot = flap + 0.1f * i;
            leftWings[i].zRot = -(flap + 0.1f * i);
            rightWebs[i].zRot = flap * 0.4f;
            leftWebs[i].zRot = -flap * 0.4f;
        }
    }
}
