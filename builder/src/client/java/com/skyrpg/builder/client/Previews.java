package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.SkyRpgBuilder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Turntable previews of blueprints for the picker's cards: each blueprint drawn as little shaded cubes from above at
 * {@link #FRAMES} angles, side by side in one texture, so a card just steps through them. Drawn off the render thread;
 * the most recently used {@link #KEEP} stay loaded.
 */
final class Previews {
    static final int W = 112;
    static final int H = 80;
    static final int FRAMES = 20;
    private static final int KEEP = 40;
    private static final double TILT = Math.toRadians(32);

    enum State { WAITING, RENDERING, READY, FAILED }

    static final class Preview {
        volatile State state = State.WAITING;
        volatile Blueprint blueprint;
        volatile String error = "";
        Identifier texture;
        boolean requested;
    }

    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static int counter;

    private Previews() {
    }

    /**
     * The preview for {@code key}; {@code source} gives its blueprint (null while it isn't here yet: asked again next
     * frame). A slow source (reading a file) runs on a background thread; its exception message is shown as the error.
     */
    static Preview get(String key, Supplier<Blueprint> source, boolean sourceIsSlow) {
        Preview preview = CACHE.get(key);
        if (preview == null) {
            preview = new Preview();
            CACHE.put(key, preview);
            trim();
        }
        if (preview.state == State.WAITING) {
            Preview target = preview;
            if (sourceIsSlow) {
                target.state = State.RENDERING;
                CompletableFuture.supplyAsync(() -> {
                    Blueprint blueprint = source.get();
                    target.blueprint = blueprint;
                    return render(blueprint);
                }, Util.backgroundExecutor()).whenCompleteAsync((image, error) -> finish(target, image, error), Minecraft.getInstance());
            } else {
                Blueprint blueprint = source.get();
                if (blueprint != null) {
                    target.state = State.RENDERING;
                    target.blueprint = blueprint;
                    CompletableFuture.supplyAsync(() -> render(blueprint), Util.backgroundExecutor())
                            .whenCompleteAsync((image, error) -> finish(target, image, error), Minecraft.getInstance());
                }
            }
        }
        return preview;
    }

    private static void finish(Preview preview, NativeImage image, Throwable error) {
        if (error != null || image == null) {
            Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
            preview.error = cause == null ? "Couldn't draw it." : cause.getMessage();
            preview.state = State.FAILED;
            if (image != null) {
                image.close();
            }
            return;
        }
        Identifier id = SkyRpgBuilder.id("preview/" + (counter++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
        preview.texture = id;
        preview.state = State.READY;
    }

    private static void trim() {
        while (CACHE.size() > KEEP) {
            Map.Entry<String, Preview> eldest = CACHE.entrySet().iterator().next();
            if (eldest.getValue().texture != null) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue().texture);
            }
            CACHE.remove(eldest.getKey());
        }
    }

    static void clear() {
        for (Preview preview : CACHE.values()) {
            if (preview.texture != null) {
                Minecraft.getInstance().getTextureManager().release(preview.texture);
            }
        }
        CACHE.clear();
    }

    /** Draws the preview's current turntable frame into the box (keeping its aspect), or a waiting/failed note. */
    static void draw(GuiGraphicsExtractor g, Preview preview, int x, int y, int w, int h) {
        if (preview == null || preview.state != State.READY || preview.texture == null) {
            return;
        }
        int frame = (int) ((Util.getMillis() / 140) % FRAMES);
        float scale = Math.min(w / (float) W, h / (float) H);
        int dw = Math.round(W * scale);
        int dh = Math.round(H * scale);
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        g.blit(preview.texture, dx, dy, dx + dw, dy + dh, frame / (float) FRAMES, (frame + 1) / (float) FRAMES, 0f, 1f);
    }

    // ------------------------------------------------------------------ drawing the cubes

    /** One frame strip: FRAMES pictures of W×H side by side. */
    static NativeImage render(Blueprint blueprint) {
        if (blueprint == null) {
            throw new IllegalStateException("No blueprint.");
        }
        int count = blueprint.count();
        // Only blocks with an open face can be seen.
        int[] colors = new int[blueprint.palette.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = color(blueprint.palette.get(i));
        }
        int sx = blueprint.sizeX, sy = blueprint.sizeY, sz = blueprint.sizeZ;
        int[] visible = new int[count];
        int[] openFaces = new int[count];
        int shown = 0;
        for (int e = 0; e < count; e++) {
            int local = blueprint.positions[e];
            int x = blueprint.localX(local), y = blueprint.localY(local), z = blueprint.localZ(local);
            int open = 0;
            if (!solid(blueprint, x + 1, y, z)) open |= 1;
            if (!solid(blueprint, x - 1, y, z)) open |= 2;
            if (!solid(blueprint, x, y + 1, z)) open |= 4;
            if (!solid(blueprint, x, y, z + 1)) open |= 8;
            if (!solid(blueprint, x, y, z - 1)) open |= 16;
            if (open != 0) {
                visible[shown] = e;
                openFaces[shown] = open;
                shown++;
            }
        }
        double radius = Math.sqrt(sx * sx + sz * sz) / 2.0;
        double scale = Math.min((W - 8) / (2 * radius), (H - 8) / (sy * Math.cos(TILT) + 2 * radius * Math.sin(TILT)));
        NativeImage image = new NativeImage(W * FRAMES, H, true);
        int[] pixels = new int[W * H];
        double[] depth = new double[shown];
        Integer[] order = new Integer[shown];
        for (int frame = 0; frame < FRAMES; frame++) {
            Arrays.fill(pixels, 0);
            double angle = Math.PI / 4 + frame * 2 * Math.PI / FRAMES;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double tiltCos = Math.cos(TILT), tiltSin = Math.sin(TILT);
            for (int i = 0; i < shown; i++) {
                int local = blueprint.positions[visible[i]];
                double cx = blueprint.localX(local) + 0.5 - sx / 2.0;
                double cz = blueprint.localZ(local) + 0.5 - sz / 2.0;
                double cy = blueprint.localY(local) + 0.5;
                double rz = cx * sin + cz * cos;
                depth[i] = rz * tiltCos + cy * tiltSin;
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Double.compare(depth[a], depth[b]));
            double ox = W / 2.0;
            double oy = H / 2.0 + (sy * tiltCos * scale) / 2.0;
            Projector projector = new Projector(cos, sin, tiltCos, tiltSin, scale, ox, oy, sx, sz);
            for (int k = 0; k < shown; k++) {
                int i = order[k];
                int entry = visible[i];
                int local = blueprint.positions[entry];
                int x = blueprint.localX(local), y = blueprint.localY(local), z = blueprint.localZ(local);
                int base = colors[blueprint.states[entry]];
                int open = openFaces[i];
                // Side faces that turn towards the viewer: +X/-X/+Z/-Z normals rotated.
                drawFaceIfFacing(pixels, projector, x, y, z, 1, 0, (open & 1) != 0, base, cos, sin);
                drawFaceIfFacing(pixels, projector, x, y, z, -1, 0, (open & 2) != 0, base, cos, sin);
                drawFaceIfFacing(pixels, projector, x, y, z, 0, 1, (open & 8) != 0, base, cos, sin);
                drawFaceIfFacing(pixels, projector, x, y, z, 0, -1, (open & 16) != 0, base, cos, sin);
                if ((open & 4) != 0) {
                    double[][] top = {projector.at(x, y + 1, z), projector.at(x + 1, y + 1, z), projector.at(x + 1, y + 1, z + 1),
                            projector.at(x, y + 1, z + 1)};
                    fill(pixels, top, shade(base, 1.0));
                }
            }
            outline(pixels);
            for (int py = 0; py < H; py++) {
                for (int px = 0; px < W; px++) {
                    image.setPixel(frame * W + px, py, pixels[py * W + px]);
                }
            }
        }
        return image;
    }

    private static boolean solid(Blueprint blueprint, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= blueprint.sizeX || y >= blueprint.sizeY || z >= blueprint.sizeZ) {
            return false;
        }
        int entry = blueprint.entryAt(x + z * blueprint.sizeX + y * blueprint.sizeX * blueprint.sizeZ);
        return entry >= 0 && blueprint.state(entry).canOcclude();
    }

    /** Projects blueprint corners into the picture (orthographic, turned and tilted). */
    private record Projector(double cos, double sin, double tiltCos, double tiltSin, double scale, double ox, double oy, int sx, int sz) {
        double[] at(double x, double y, double z) {
            double cx = x - sx / 2.0;
            double cz = z - sz / 2.0;
            double rx = cx * cos - cz * sin;
            double rz = cx * sin + cz * cos;
            return new double[]{ox + rx * scale, oy + (-y * tiltCos + rz * tiltSin) * scale};
        }
    }

    private static void drawFaceIfFacing(int[] pixels, Projector p, int x, int y, int z, int nx, int nz, boolean open, int base, double cos,
                                         double sin) {
        if (!open) {
            return;
        }
        // The face's normal turned with the blueprint; it shows if it points at the viewer (towards +rz).
        double facing = nx * sin + nz * cos;
        if (facing <= 0.02) {
            return;
        }
        double[][] quad;
        if (nx != 0) {
            double fx = nx > 0 ? x + 1 : x;
            quad = new double[][]{p.at(fx, y, z), p.at(fx, y, z + 1), p.at(fx, y + 1, z + 1), p.at(fx, y + 1, z)};
        } else {
            double fz = nz > 0 ? z + 1 : z;
            quad = new double[][]{p.at(x, y, fz), p.at(x + 1, y, fz), p.at(x + 1, y + 1, fz), p.at(x, y + 1, fz)};
        }
        // Light from the viewer's left: faces turned that way are brighter.
        double side = nx * cos - nz * sin;
        fill(pixels, quad, shade(base, 0.62 + 0.18 * (side < 0 ? 1 : 0) + 0.06 * facing));
    }

    /** Fills a convex quad (painter's order: later faces cover earlier ones), blending see-through colours. */
    private static void fill(int[] pixels, double[][] quad, int color) {
        double minX = W, maxX = 0, minY = H, maxY = 0;
        for (double[] v : quad) {
            minX = Math.min(minX, v[0]);
            maxX = Math.max(maxX, v[0]);
            minY = Math.min(minY, v[1]);
            maxY = Math.max(maxY, v[1]);
        }
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(H - 1, (int) Math.ceil(maxY));
        double area = 0;
        for (int i = 0; i < 4; i++) {
            double[] a = quad[i], b = quad[(i + 1) % 4];
            area += a[0] * b[1] - b[0] * a[1];
        }
        double sign = area >= 0 ? 1 : -1;
        int alpha = color >>> 24;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                double cx = px + 0.5, cy = py + 0.5;
                boolean inside = true;
                for (int i = 0; i < 4 && inside; i++) {
                    double[] a = quad[i], b = quad[(i + 1) % 4];
                    double cross = (b[0] - a[0]) * (cy - a[1]) - (b[1] - a[1]) * (cx - a[0]);
                    inside = cross * sign >= -0.35;
                }
                if (!inside) {
                    continue;
                }
                int index = py * W + px;
                pixels[index] = alpha >= 250 ? color : blend(pixels[index], color);
            }
        }
    }

    private static int blend(int under, int over) {
        float a = (over >>> 24) / 255f;
        int ua = under >>> 24;
        int r = (int) (((over >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (1 - a));
        int g = (int) (((over >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (1 - a));
        int b = (int) ((over & 0xFF) * a + (under & 0xFF) * (1 - a));
        int outA = Math.min(255, (int) ((over >>> 24) + ua * (1 - a)));
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    /** A dark one-pixel edge around the whole silhouette, so builds stand out like pixel art. */
    private static void outline(int[] pixels) {
        int[] copy = pixels.clone();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if ((copy[y * W + x] >>> 24) != 0) {
                    continue;
                }
                boolean edge = (x > 0 && (copy[y * W + x - 1] >>> 24) > 80) || (x < W - 1 && (copy[y * W + x + 1] >>> 24) > 80)
                        || (y > 0 && (copy[(y - 1) * W + x] >>> 24) > 80) || (y < H - 1 && (copy[(y + 1) * W + x] >>> 24) > 80);
                if (edge) {
                    pixels[y * W + x] = 0xB0060A12;
                }
            }
        }
    }

    private static int color(BlockState state) {
        if (!state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return 0xB03F76E4;
        }
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int rgb = mapColor == MapColor.NONE ? 0xC8D8E8 : mapColor.col;
        int alpha = state.canOcclude() ? 0xFF : 0xC0;
        return (alpha << 24) | rgb;
    }

    private static int shade(int argb, double f) {
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
