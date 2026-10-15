package com.skyrpg.builder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.builder.Placement;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Draws the holograms in the world: the blueprint's blocks see-through and tinted cyan (red where something is in
 * the way), an outline around the whole build, and while building a glowing plane sweeping up layer by layer. The
 * hologram being placed ({@link PlacementMode}) is drawn where it is now, red when it's too far from its station.
 */
final class GhostRenderer {
    private static final double RANGE = 192;
    private static final int OUTLINE = 0xC05CE1E6;
    private static final int OUTLINE_BUILD = 0xE0F2B33D;
    private static final int OUTLINE_BAD = 0xE0FF6464;

    private GhostRenderer() {
    }

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        BlockPos placing = PlacementMode.station();
        for (var entry : Ghosts.ACTIVE.entrySet()) {
            if (entry.getKey().equals(placing)) {
                continue;
            }
            Ghosts.Ghost ghost = entry.getValue();
            Placement placement = ghost.placement();
            if (placement == null || placement.origin().distToCenterSqr(camera) > RANGE * RANGE * 2) {
                continue;
            }
            Ghosts.Mesh mesh = Ghosts.mesh(ghost, level);
            boolean building = ghost.data.mode() == BuilderPayloads.Ghost.BUILD;
            draw(context, camera, mesh, placement, building ? OUTLINE_BUILD : OUTLINE, building ? ghost.data.layer() : -1);
        }
        Placement moving = PlacementMode.placement();
        if (moving != null) {
            Blueprint blueprint = Ghosts.BLUEPRINTS.get(PlacementMode.blueprintId());
            if (blueprint != null) {
                boolean ok = PlacementMode.inRange();
                Ghosts.Mesh mesh = Ghosts.plainMesh(blueprint, moving.turns(), moving.mirrored(), ok ? Ghosts.TINT : Ghosts.CONFLICT);
                draw(context, camera, mesh, moving, ok ? OUTLINE : OUTLINE_BAD, -1);
            }
        }
    }

    private static void draw(LevelRenderContext context, Vec3 camera, Ghosts.Mesh mesh, Placement placement, int outline, int buildLayer) {
        PoseStack poseStack = context.poseStack();
        BlockPos origin = placement.origin();
        if (mesh != null && mesh.count() > 0) {
            context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(),
                    (pose, consumer) -> emit(mesh, origin, pose, consumer, camera));
        }
        poseStack.pushPose();
        poseStack.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        int width = placement.width();
        int height = placement.sizeY();
        int depth = placement.depth();
        context.submitNodeCollector().submitShapeOutline(poseStack, Shapes.box(0, 0, 0, width, height, depth), RenderTypes.lines(), outline, 2.5f, false);
        if (buildLayer >= 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 220.0));
            int alpha = (int) (40 + 40 * pulse);
            float y = Math.min(height, buildLayer) + 0.02f;
            context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
                int color = (alpha << 24) | 0x5CE1E6;
                consumer.addVertex(pose, 0, y, 0).setColor(color);
                consumer.addVertex(pose, 0, y, depth).setColor(color);
                consumer.addVertex(pose, width, y, depth).setColor(color);
                consumer.addVertex(pose, width, y, 0).setColor(color);
                // And from below, so it shows from under the plane too.
                consumer.addVertex(pose, width, y, 0).setColor(color);
                consumer.addVertex(pose, width, y, depth).setColor(color);
                consumer.addVertex(pose, 0, y, depth).setColor(color);
                consumer.addVertex(pose, 0, y, 0).setColor(color);
            });
        }
        poseStack.popPose();
    }

    private static void emit(Ghosts.Mesh mesh, BlockPos origin, PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera) {
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        int light = LightCoordsUtil.FULL_BRIGHT;
        double ox = origin.getX() - camera.x;
        double oy = origin.getY() - camera.y;
        double oz = origin.getZ() - camera.z;
        for (int i = 0; i < mesh.count(); i++) {
            long packed = mesh.positions()[i];
            float bx = (float) (BlockPos.getX(packed) + ox);
            float by = (float) (BlockPos.getY(packed) + oy);
            float bz = (float) (BlockPos.getZ(packed) + oz);
            BakedQuad quad = mesh.quads()[i];
            int color = mesh.colors()[i];
            normal.set(quad.direction().getStepX(), quad.direction().getStepY(), quad.direction().getStepZ());
            normalMatrix.transform(normal);
            for (int v = 0; v < BakedQuad.VERTEX_COUNT; v++) {
                Vector3fc corner = quad.position(v);
                matrix.transformPosition(bx + corner.x(), by + corner.y(), bz + corner.z(), position);
                long uv = quad.packedUV(v);
                consumer.addVertex(position.x(), position.y(), position.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv), OverlayTexture.NO_OVERLAY,
                        light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
