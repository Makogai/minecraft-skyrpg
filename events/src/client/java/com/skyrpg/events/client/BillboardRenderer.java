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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the server's billboards ({@code ShowcasePayloads.Billboards}) as a real billboard: a 16:9 screen showing the current
 * picture of the slideshow (cropped to fill it), set in a deep dark cabinet with a glowing accent trim, a marquee of chasing
 * bulbs round the frame, a header cap with two spotlights on arms casting light down onto the screen, and either steel
 * legs with cross braces down to the ground ("stand") or brackets into the wall behind ("wall"). The screen, trim, bulbs
 * and lamps are full bright; the cabinet and legs take the world's light. Drawn within {@link #RANGE} blocks in the same
 * dimension.
 *
 * <p>Geometry is built in the board's own axes: {@code a} along the screen (the viewer's right), {@code u} up, {@code o} out
 * of the screen towards the viewer; the screen's centre is the origin.
 */
public final class BillboardRenderer {
    private static final double RANGE = 192;
    private static final int CABINET = 0xFF19141F;
    private static final int FACE = 0xFF251D31;
    private static final int METAL = 0xFF2C2634;
    private static final int BRACE = 0xFF3B3345;
    private static final int BULB_ON = 0xFFFFE8A6;
    private static final int BULB_OFF = 0xFF5A3F22;
    private static final int LAMP = 0xFFFFF4D0;

    private record Board(String id, String dim, Vec3 center, float yaw, float width, float height, List<String> images, int seconds,
                         int accent, boolean stand) {
    }

    /** One coloured quad on the white texture (four corners in board axes); {@code fade}: the last two corners go clear. */
    private record Quad(float[] corners, int color, boolean lit, boolean fade) {
        Quad(float[] corners, int color, boolean lit) {
            this(corners, color, lit, false);
        }
    }

    private static List<Board> boards = List.of();
    /** Ground height under each leg, found once its chunk is loaded. */
    private static final Map<String, Double> GROUND = new HashMap<>();
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boards = List.of();
            GROUND.clear();
        });
    }

    static void receive(String json) {
        List<Board> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject b = element.getAsJsonObject();
            List<String> images = new ArrayList<>();
            b.getAsJsonArray("images").forEach(e -> images.add(e.getAsString()));
            int frame = b.get("frame").getAsInt();
            list.add(new Board(b.get("id").getAsString(), b.get("dim").getAsString(),
                    new Vec3(b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("z").getAsDouble()), b.get("yaw").getAsFloat(),
                    b.get("w").getAsFloat(), b.get("h").getAsFloat(), List.copyOf(images), Math.max(2, b.get("seconds").getAsInt()),
                    frame == 0 || frame == 0xFF2A1A40 ? 0xFFFF7A1A : frame, !b.has("style") || b.get("style").getAsString().equals("stand")));
        }
        boards = List.copyOf(list);
        GROUND.clear();
        boards.forEach(board -> board.images().forEach(RemoteImages::get));
    }

    private static Identifier white() {
        if (white == null) {
            NativeImage pixel = new NativeImage(1, 1, false);
            pixel.setPixelABGR(0, 0, 0xFFFFFFFF);
            white = Identifier.fromNamespaceAndPath(SkyRpgEvents.MOD_ID, "billboard_white");
            Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "skyrpg billboard white", pixel));
        }
        return white;
    }

    // ---------------------------------------------------------------- one frame

    private static void render(SubmitNodeCollector collector, PoseStack pose, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (boards.isEmpty() || level == null || camera == null) {
            return;
        }
        String dim = level.dimension().identifier().toString();
        long now = System.currentTimeMillis();
        for (Board board : boards) {
            if (!board.dim().equals(dim) || board.center().distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            double rad = Math.toRadians(board.yaw());
            float nx = (float) -Math.sin(rad);
            float nz = (float) Math.cos(rad);
            BlockPos front = BlockPos.containing(board.center().add(nx * 1.5, 0, nz * 1.5));
            int worldLight = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, front), level.getBrightness(LightLayer.SKY, front));

            List<Quad> quads = new ArrayList<>();
            build(board, level, nx, nz, now, quads);

            pose.pushPose();
            pose.translate(board.center().x - camera.x, board.center().y - camera.y, board.center().z - camera.z);
            float rx = nz;
            float rz = -nx;
            collector.submitCustomGeometry(pose, RenderTypes.text(white()), (p, buffer) -> {
                for (Quad quad : quads) {
                    emit(buffer, p, rx, rz, nx, nz, quad, quad.lit() ? LightCoordsUtil.FULL_BRIGHT : worldLight);
                }
            });
            screen(collector, pose, board, rx, rz, nx, nz, now);
            pose.popPose();
        }
    }

    /** The picture on the screen, cropped to fill 16:9 (or whatever the board is), full bright. */
    private static void screen(SubmitNodeCollector collector, PoseStack pose, Board board, float rx, float rz, float nx, float nz, long now) {
        if (board.images().isEmpty()) {
            return;
        }
        String url = board.images().get((int) ((now / 1000 / board.seconds()) % board.images().size()));
        RemoteImages.Entry image = RemoteImages.get(url);
        if (!image.ready()) {
            return;
        }
        float hw = board.width() / 2;
        float hh = board.height() / 2;
        float boardAspect = board.width() / board.height();
        float aspect = image.width() / (float) image.height();
        float u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;
        if (aspect > boardAspect) {
            float keep = boardAspect / aspect;
            u0 = (1 - keep) / 2;
            u1 = 1 - u0;
        } else {
            float keep = aspect / boardAspect;
            v0 = (1 - keep) / 2;
            v1 = 1 - v0;
        }
        float fu0 = u0;
        float fu1 = u1;
        float fv0 = v0;
        float fv1 = v1;
        collector.submitCustomGeometry(pose, RenderTypes.text(image.texture()), (p, buffer) -> {
            float o = 0.012f;
            vertex(buffer, p, rx, rz, nx, nz, -hw, hh, o, fu0, fv0, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
            vertex(buffer, p, rx, rz, nx, nz, -hw, -hh, o, fu0, fv1, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
            vertex(buffer, p, rx, rz, nx, nz, hw, -hh, o, fu1, fv1, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
            vertex(buffer, p, rx, rz, nx, nz, hw, hh, o, fu1, fv0, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
        });
    }

    /** Everything but the picture, as coloured quads. */
    private static void build(Board board, ClientLevel level, float nx, float nz, long now, List<Quad> out) {
        float w = board.width();
        float h = board.height();
        float hw = w / 2;
        float hh = h / 2;
        float f = clamp(w * 0.045f, 0.28f, 0.9f);       // frame thickness
        float depth = clamp(w * 0.045f, 0.35f, 0.75f);  // cabinet depth behind the screen
        float lip = 0.09f;                              // the front frame sticks out this far
        int accent = board.accent();

        // the cabinet and the black glass behind the picture
        box(out, -hw - f, hw + f, -hh - f, hh + f, -depth, 0, CABINET, false);
        face(out, -hw, hw, -hh, hh, 0.006f, 0xFF050307, true);
        // front frame bars
        box(out, -hw - f, hw + f, hh, hh + f, 0, lip, FACE, false);
        box(out, -hw - f, hw + f, -hh - f, -hh, 0, lip, FACE, false);
        box(out, -hw - f, -hw, -hh, hh, 0, lip, FACE, false);
        box(out, hw, hw + f, -hh, hh, 0, lip, FACE, false);
        // glowing accent trim hugging the screen
        float t = Math.max(0.05f, f * 0.16f);
        float o = lip + 0.004f;
        face(out, -hw - t, hw + t, hh, hh + t, o, accent, true);
        face(out, -hw - t, hw + t, -hh - t, -hh, o, accent, true);
        face(out, -hw - t, -hw, -hh, hh, o, accent, true);
        face(out, hw, hw + t, -hh, hh, o, accent, true);

        // marquee bulbs round the frame, chasing
        float bulb = clamp(f * 0.36f, 0.09f, 0.2f);
        float ring = f * 0.58f;
        int step = (int) (now / 160);
        int index = 0;
        float spacing = Math.max(0.5f, f * 1.6f);
        int across = Math.max(2, (int) ((w + 2 * ring) / spacing));
        int down = Math.max(2, (int) ((h + 2 * ring) / spacing));
        for (int i = 0; i <= across; i++) {
            float a = -hw - ring + (w + 2 * ring) * i / across;
            bulb(out, a, hh + ring, lip, bulb, (index++ + step) % 3 != 0, accent);
            bulb(out, -a, -hh - ring, lip, bulb, (index++ + step) % 3 != 0, accent);
        }
        for (int i = 1; i < down; i++) {
            float u = -hh - ring + (h + 2 * ring) * i / down;
            bulb(out, hw + ring, -u, lip, bulb, (index++ + step) % 3 != 0, accent);
            bulb(out, -hw - ring, u, lip, bulb, (index++ + step) % 3 != 0, accent);
        }

        // the back: a steel truss of vertical ribs and two rails, like the back of a real billboard
        float rib = Math.max(0.12f, f * 0.4f);
        int ribs = Math.max(3, Math.round(w / 2.2f));
        for (int i = 0; i <= ribs; i++) {
            float a = -hw - f + rib / 2 + (w + 2 * f - rib) * i / ribs;
            box(out, a - rib / 2, a + rib / 2, -hh - f, hh + f, -depth - rib, -depth, METAL, false);
        }
        for (float u : new float[] {hh * 0.55f, -hh * 0.55f}) {
            box(out, -hw - f, hw + f, u - rib / 2, u + rib / 2, -depth - rib * 1.8f, -depth - rib, BRACE, false);
        }

        // header cap, overhanging a little
        float capTop = hh + f + 0.22f;
        box(out, -hw - f - 0.18f, hw + f + 0.18f, hh + f, capTop, -depth - 0.08f, lip + 0.22f, METAL, false);
        face(out, -hw - f - 0.18f, hw + f + 0.18f, hh + f + 0.03f, hh + f + 0.07f, lip + 0.221f, accent, true);

        // two spotlights on arms, lighting the screen
        for (float side : new float[] {-1, 1}) {
            float x = side * w * 0.27f;
            float reach = clamp(h * 0.22f, 0.7f, 2.2f);
            box(out, x - 0.05f, x + 0.05f, capTop, capTop + 0.08f, lip, lip + reach, METAL, false);
            box(out, x - 0.05f, x + 0.05f, capTop - 0.05f, capTop + 0.3f, lip + reach - 0.06f, lip + reach + 0.04f, METAL, false);
            box(out, x - 0.16f, x + 0.16f, capTop + 0.12f, capTop + 0.4f, lip + reach - 0.12f, lip + reach + 0.2f, BRACE, false);
            // the lamp's glowing face points back at the screen
            face(out, x - 0.13f, x + 0.13f, capTop + 0.15f, capTop + 0.37f, lip + reach - 0.121f, LAMP, true);
            // the light: a soft fan from the lamp down onto the picture
            cone(out, x, capTop + 0.26f, lip + reach - 0.13f, w * 0.24f, hh - h * 0.62f, hh + 0.02f, 0.03f);
        }

        if (board.stand()) {
            legs(out, board, level, nx, nz, hw, hh, f, depth);
        } else {
            // brackets into the wall behind
            for (float u : new float[] {hh * 0.6f, -hh * 0.6f}) {
                for (float a : new float[] {-hw * 0.7f, hw * 0.7f}) {
                    box(out, a - 0.12f, a + 0.12f, u - 0.12f, u + 0.12f, -depth - 0.35f, -depth, METAL, false);
                }
            }
        }
    }

    /** Two steel legs to the ground with braces and base plates. */
    private static void legs(List<Quad> out, Board board, ClientLevel level, float nx, float nz, float hw, float hh, float f, float depth) {
        float post = clamp(board.width() * 0.028f, 0.22f, 0.45f);
        float oc = -depth / 2;
        float bottom = -hh - f;
        float lowest = bottom;
        for (float side : new float[] {-1, 1}) {
            float a = side * hw * 0.62f;
            double groundY = ground(board, level, a, oc, nx, nz, bottom);
            float g = (float) (groundY - board.center().y);
            lowest = Math.min(lowest, g);
            box(out, a - post / 2, a + post / 2, g, bottom, oc - post / 2, oc + post / 2, METAL, false);
            box(out, a - post * 1.6f, a + post * 1.6f, g, g + 0.12f, oc - post * 1.6f, oc + post * 1.6f, BRACE, false);
        }
        // braces between the legs every couple of blocks
        float a0 = -hw * 0.62f;
        float a1 = hw * 0.62f;
        for (float u = bottom - 1.2f; u > lowest + 0.6f; u -= 2.2f) {
            box(out, a0, a1, u - 0.07f, u + 0.07f, oc - 0.06f, oc + 0.06f, BRACE, false);
        }
    }

    /** The ground under a leg (first non-air block below, up to 48 blocks), cached once found. */
    private static double ground(Board board, ClientLevel level, float a, float o, float nx, float nz, float bottom) {
        String key = board.id() + ":" + a;
        Double cached = GROUND.get(key);
        if (cached != null) {
            return cached;
        }
        double x = board.center().x + nz * a + nx * o;
        double z = board.center().z - nx * a + nz * o;
        int top = (int) Math.floor(board.center().y + bottom);
        for (int y = top; y > top - 48; y--) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return board.center().y + bottom - 3;
            }
            if (!level.getBlockState(pos).isAir()) {
                double found = y + 1;
                GROUND.put(key, found);
                return found;
            }
        }
        double fallback = board.center().y + bottom - 3;
        GROUND.put(key, fallback);
        return fallback;
    }

    private static void bulb(List<Quad> out, float a, float u, float lip, float size, boolean on, int accent) {
        float s = size / 2;
        box(out, a - s, a + s, u - s, u + s, lip, lip + size, on ? BULB_ON : BULB_OFF, on);
        if (on) {
            // a soft halo
            int halo = (0x55 << 24) | (Draw3.mix(BULB_ON, accent) & 0xFFFFFF);
            face(out, a - size * 1.4f, a + size * 1.4f, u - size * 1.4f, u + size * 1.4f, lip + 0.002f, halo, true);
        }
    }

    /** A see-through fan of light from a lamp at (a, u, o) down to the screen between heights low and high. */
    private static void cone(List<Quad> out, float a, float u, float o, float spread, float low, float high, float screenO) {
        out.add(new Quad(new float[] {a - 0.12f, u, o, a + 0.12f, u, o, a + spread, low, screenO, a - spread, low, screenO},
                0x38FFF4D0, true, true));
    }

    // ---------------------------------------------------------------- shapes

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** A flat rectangle facing the viewer at depth {@code o}. */
    private static void face(List<Quad> out, float a0, float a1, float u0, float u1, float o, int color, boolean lit) {
        out.add(new Quad(new float[] {a0, u1, o, a0, u0, o, a1, u0, o, a1, u1, o}, color, lit));
    }

    /** A box with simple shading per side (top brightest, bottom darkest). */
    private static void box(List<Quad> out, float a0, float a1, float u0, float u1, float o0, float o1, int color, boolean lit) {
        out.add(new Quad(new float[] {a0, u1, o1, a0, u0, o1, a1, u0, o1, a1, u1, o1}, shade(color, 0.92f), lit));   // front
        out.add(new Quad(new float[] {a1, u1, o0, a1, u0, o0, a0, u0, o0, a0, u1, o0}, shade(color, 0.62f), lit));   // back
        out.add(new Quad(new float[] {a0, u1, o0, a0, u1, o1, a1, u1, o1, a1, u1, o0}, shade(color, 1.0f), lit));    // top
        out.add(new Quad(new float[] {a0, u0, o1, a0, u0, o0, a1, u0, o0, a1, u0, o1}, shade(color, 0.5f), lit));    // bottom
        out.add(new Quad(new float[] {a0, u1, o0, a0, u0, o0, a0, u0, o1, a0, u1, o1}, shade(color, 0.75f), lit));   // left
        out.add(new Quad(new float[] {a1, u1, o1, a1, u0, o1, a1, u0, o0, a1, u1, o0}, shade(color, 0.75f), lit));   // right
    }

    private static int shade(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, float rx, float rz, float nx, float nz, Quad quad, int light) {
        float[] c = quad.corners();
        int color = quad.color();
        for (int i = 0; i < 4; i++) {
            int corner = quad.fade() && i >= 2 ? color & 0x00FFFFFF : color;
            vertex(buffer, pose, rx, rz, nx, nz, c[i * 3], c[i * 3 + 1], c[i * 3 + 2], 0.5f, 0.5f, corner, light);
        }
        // and the other way round, so it shows from both sides whatever the culling
        for (int i = 3; i >= 0; i--) {
            int corner = quad.fade() && i >= 2 ? color & 0x00FFFFFF : color;
            vertex(buffer, pose, rx, rz, nx, nz, c[i * 3], c[i * 3 + 1], c[i * 3 + 2], 0.5f, 0.5f, corner, light);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float rx, float rz, float nx, float nz, float a, float u, float o,
                               float tu, float tv, int color, int light) {
        buffer.addVertex(pose, rx * a + nx * o, u, rz * a + nz * o).setColor(color).setUv(tu, tv).setLight(light);
    }

    /** Small colour helper (kept here so the renderer has no client UI dependency). */
    private static final class Draw3 {
        static int mix(int a, int b) {
            int r = (((a >> 16) & 0xFF) * 3 + ((b >> 16) & 0xFF)) / 4;
            int g = (((a >> 8) & 0xFF) * 3 + ((b >> 8) & 0xFF)) / 4;
            int bl = ((a & 0xFF) * 3 + (b & 0xFF)) / 4;
            return 0xFF000000 | (r << 16) | (g << 8) | bl;
        }
    }
}
