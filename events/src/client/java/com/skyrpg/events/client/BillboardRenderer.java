package com.skyrpg.events.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyrpg.core.client.image.RemoteImages;
import com.skyrpg.events.SkyRpgEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the server's billboards ({@code ShowcasePayloads.Billboards}): a dark backing panel with a coloured frame, and on it
 * the current picture of the slideshow, fitted inside keeping its shape. Full bright, so they glow at night like a sign.
 * Only billboards in the player's dimension and within {@link #RANGE} blocks are drawn. From behind you see the backing.
 */
public final class BillboardRenderer {
    private static final double RANGE = 192;
    private static final float FRAME = 0.12f;

    private record Board(String dim, Vec3 center, float yaw, float width, float height, List<String> images, int seconds, int frame) {
    }

    private static List<Board> boards = List.of();
    private static Identifier white;

    private BillboardRenderer() {
    }

    static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            try {
                render(context.submitNodeCollector(), context.poseStack(), context.levelState().cameraRenderState.pos);
            } catch (RuntimeException e) {
                SkyRpgEvents.LOGGER.warn("Billboard drawing failed", e);
                boards = List.of();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> boards = List.of());
    }

    static void receive(String json) {
        List<Board> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject b = element.getAsJsonObject();
            List<String> images = new ArrayList<>();
            b.getAsJsonArray("images").forEach(e -> images.add(e.getAsString()));
            list.add(new Board(b.get("dim").getAsString(), new Vec3(b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("z").getAsDouble()),
                    b.get("yaw").getAsFloat(), b.get("w").getAsFloat(), b.get("h").getAsFloat(), List.copyOf(images),
                    Math.max(2, b.get("seconds").getAsInt()), b.get("frame").getAsInt()));
        }
        boards = List.copyOf(list);
        // start loading every picture now, so slides are ready when their turn comes
        boards.forEach(board -> board.images().forEach(RemoteImages::get));
    }

    /** A 1x1 white texture for the backing and the frame (tinted by vertex colour). */
    private static Identifier white() {
        if (white == null) {
            NativeImage pixel = new NativeImage(1, 1, false);
            pixel.setPixelABGR(0, 0, 0xFFFFFFFF);
            white = Identifier.fromNamespaceAndPath(SkyRpgEvents.MOD_ID, "billboard_white");
            Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "skyrpg billboard white", pixel));
        }
        return white;
    }

    private static void render(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack pose, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (boards.isEmpty() || minecraft.level == null || camera == null) {
            return;
        }
        String dim = minecraft.level.dimension().identifier().toString();
        long now = System.currentTimeMillis();
        for (Board board : boards) {
            if (!board.dim().equals(dim) || board.center().distanceToSqr(camera) > RANGE * RANGE || board.images().isEmpty()) {
                continue;
            }
            double rad = Math.toRadians(board.yaw());
            // n: the way the picture faces; right: the viewer's right when looking at it
            float nx = (float) -Math.sin(rad);
            float nz = (float) Math.cos(rad);
            float rx = nz;
            float rz = -nx;
            float hw = board.width() / 2;
            float hh = board.height() / 2;

            pose.pushPose();
            pose.translate(board.center().x - camera.x, board.center().y - camera.y, board.center().z - camera.z);
            int light = LightCoordsUtil.FULL_BRIGHT;
            // backing (a little behind) with the frame around it
            float back = -0.02f;
            int frame = board.frame() == 0 ? 0xFF0B0712 : board.frame();
            quad(collector, pose, white(), rx, rz, nx, nz, -hw - FRAME, hh + FRAME, hw + FRAME, -hh - FRAME, back, 0, 0, 1, 1, frame);
            quad(collector, pose, white(), rx, rz, nx, nz, -hw, hh, hw, -hh, back / 2, 0, 0, 1, 1, 0xFF0B0712);

            String url = board.images().get((int) ((now / 1000 / board.seconds()) % board.images().size()));
            RemoteImages.Entry image = RemoteImages.get(url);
            if (image.ready()) {
                // fit the picture inside the board, keeping its shape
                float aspect = image.width() / (float) image.height();
                float w = board.width();
                float h = board.height();
                if (w / h > aspect) {
                    w = h * aspect;
                } else {
                    h = w / aspect;
                }
                quad(collector, pose, image.texture(), rx, rz, nx, nz, -w / 2, h / 2, w / 2, -h / 2, 0, 0, 0, 1, 1, 0xFFFFFFFF);
            }
            pose.popPose();
        }
    }

    /**
     * A flat rectangle in the board's plane: (left, top) to (right, bottom) along right/up, pushed {@code out} along the
     * facing; uv from (u0, v0) at the top left. Uses the text render type: textured, coloured, lit by the light map.
     */
    private static void quad(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack pose, Identifier texture,
                             float rx, float rz, float nx, float nz, float left, float top, float right, float bottom, float out,
                             float u0, float v0, float u1, float v1, int color) {
        collector.submitCustomGeometry(pose, RenderTypes.text(texture), (p, buffer) -> {
            vertex(buffer, p, rx, rz, nx, nz, left, top, out, u0, v0, color);
            vertex(buffer, p, rx, rz, nx, nz, left, bottom, out, u0, v1, color);
            vertex(buffer, p, rx, rz, nx, nz, right, bottom, out, u1, v1, color);
            vertex(buffer, p, rx, rz, nx, nz, right, top, out, u1, v0, color);
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float rx, float rz, float nx, float nz, float along, float up,
                               float out, float u, float v, int color) {
        buffer.addVertex(pose, rx * along + nx * out, up, rz * along + nz * out).setColor(color).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT);
    }
}
