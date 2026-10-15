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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 3D turntable previews of blueprints: each blueprint drawn as little shaded cubes from above at several angles, side
 * by side in one texture, so the picture just steps through them (or follows a drag). Drawn off the render thread;
 * the most recently used {@link #KEEP} stay loaded. With a {@link Spec#cut} layer, everything from that layer up is
 * drawn as a faint cyan hologram: a build in progress.
 */
final class Previews {
    private static final int KEEP = 40;
    private static final double TILT = Math.toRadians(32);
    private static final int GHOST = 0x585CE1E6;

    /** Picture size, number of angles, and the first layer drawn as hologram (-1: none). */
    record Spec(int w, int h, int frames, int cut) {
        static final Spec CARD = new Spec(112, 80, 20, -1);

        static Spec menu(int cut) {
            return new Spec(232, 176, 36, cut);
        }

        static Spec overlay(int cut) {
            return new Spec(96, 72, 20, cut);
        }
    }

    enum State { WAITING, RENDERING, READY, FAILED }

    static final class Preview {
        final Spec spec;
        volatile State state = State.WAITING;
        volatile Blueprint blueprint;
        volatile String error = "";
        Identifier texture;

        Preview(Spec spec) {
            this.spec = spec;
        }
    }

    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static int counter;

    private Previews() {
    }

    static Preview get(String key, Supplier<Blueprint> source, boolean sourceIsSlow) {
        return get(key, source, sourceIsSlow, Spec.CARD);
    }

    /**
     * The preview for {@code key}; {@code source} gives its blueprint (null while it isn't here yet: asked again next
     * frame). A slow source (reading a file) runs on a background thread; its exception message is shown as the error.
     */
    static Preview get(String key, Supplier<Blueprint> source, boolean sourceIsSlow, Spec spec) {
        String cacheKey = key + "|" + spec;
        Preview preview = CACHE.get(cacheKey);
        if (preview == null) {
            preview = new Preview(spec);
            CACHE.put(cacheKey, preview);
            trim();
        }
        if (preview.state == State.WAITING) {
            Preview target = preview;
            if (sourceIsSlow) {
                target.state = State.RENDERING;
                CompletableFuture.supplyAsync(() -> {
                    Blueprint blueprint = source.get();
                    target.blueprint = blueprint;
                    return render(blueprint, spec);
                }, Util.backgroundExecutor()).whenCompleteAsync((image, error) -> finish(target, image, error), Minecraft.getInstance());
            } else {
                Blueprint blueprint = source.get();
                if (blueprint != null) {
                    target.state = State.RENDERING;
                    target.blueprint = blueprint;
                    CompletableFuture.supplyAsync(() -> render(blueprint, spec), Util.backgroundExecutor())
                            .whenCompleteAsync((image, error) -> finish(target, image, error), Minecraft.getInstance());
                }
            }
        }
        return preview;
    }

    /** The newest READY preview among these keys' specs, so a build-in-progress picture doesn't blink while the next one draws. */
    static Preview ready(Preview wanted, Preview fallback) {
        if (wanted != null && wanted.state == State.READY) {
            return wanted;
        }
        return fallback != null && fallback.state == State.READY ? fallback : wanted;
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

    /** The turntable frame for now: one turn every few seconds. */
    static int frameNow(Preview preview) {
        return (int) ((Util.getMillis() / 140) % preview.spec.frames());
    }

    /** Draws the preview's current turntable frame into the box (keeping its aspect). */
    static void draw(GuiGraphicsExtractor g, Preview preview, int x, int y, int w, int h) {
        if (preview != null) {
            drawFrame(g, preview, frameNow(preview), x, y, w, h);
        }
    }

    static void drawFrame(GuiGraphicsExtractor g, Preview preview, int frame, int x, int y, int w, int h) {
        if (preview == null || preview.state != State.READY || preview.texture == null) {
            return;
        }
        Spec spec = preview.spec;
        int f = Math.floorMod(frame, spec.frames());
        float scale = Math.min(w / (float) spec.w(), h / (float) spec.h());
        int dw = Math.round(spec.w() * scale);
        int dh = Math.round(spec.h() * scale);
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        g.blit(preview.texture, dx, dy, dx + dw, dy + dh, f / (float) spec.frames(), (f + 1) / (float) spec.frames(), 0f, 1f);
    }

    // ------------------------------------------------------------------ drawing the cubes

    /** One frame strip: {@code frames} pictures side by side. */
    static NativeImage render(Blueprint blueprint, Spec spec) {
        if (blueprint == null) {
            throw new IllegalStateException("No blueprint.");
        }
        int W = spec.w();
        int H = spec.h();
        int frames = spec.frames();
        int count = blueprint.count();
        int[] colors = new int[blueprint.palette.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = color(blueprint.palette.get(i));
        }
        int sx = blueprint.sizeX, sy = blueprint.sizeY, sz = blueprint.sizeZ;
        // Only blocks with an open face can be seen.
        int[] visible = new int[count];
        int[] openFaces = new int[count];
        int shown = 0;
        for (int e = 0; e < count; e++) {
            int local = blueprint.positions[e];
            int x = blueprint.localX(local), y = blueprint.localY(local), z = blueprint.localZ(local);
            int open = 0;
            if (!solid(blueprint, x + 1, y, z, spec.cut())) open |= 1;
            if (!solid(blueprint, x - 1, y, z, spec.cut())) open |= 2;
            if (!solid(blueprint, x, y + 1, z, spec.cut())) open |= 4;
            if (!solid(blueprint, x, y, z + 1, spec.cut())) open |= 8;
            if (!solid(blueprint, x, y, z - 1, spec.cut())) open |= 16;
            if (open != 0) {
                visible[shown] = e;
                openFaces[shown] = open;
                shown++;
            }
        }
        double radius = Math.sqrt(sx * sx + sz * sz) / 2.0;
        double scale = Math.min((W - 8) / (2 * radius), (H - 8) / (sy * Math.cos(TILT) + 2 * radius * Math.sin(TILT)));
        NativeImage image = new NativeImage(W * frames, H, true);
        int[] pixels = new int[W * H];
        double[] depth = new double[shown];
        Integer[] order = new Integer[shown];
        double tiltCos = Math.cos(TILT), tiltSin = Math.sin(TILT);
        for (int frame = 0; frame < frames; frame++) {
            Arrays.fill(pixels, 0);
            double angle = Math.PI / 4 + frame * 2 * Math.PI / frames;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int i = 0; i < shown; i++) {
                int local = blueprint.positions[visible[i]];
                double cx = blueprint.localX(local) + 0.5 - sx / 2.0;
                double cz = blueprint.localZ(local) + 0.5 - sz / 2.0;
                double cy = blueprint.localY(local) + 0.5;
                depth[i] = (cx * sin + cz * cos) * tiltCos + cy * tiltSin;
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Double.compare(depth[a], depth[b]));
            Projector projector = new Projector(cos, sin, tiltCos, tiltSin, scale, W / 2.0, H / 2.0 + (sy * tiltCos * scale) / 2.0, sx, sz);
            Canvas canvas = new Canvas(pixels, W, H);
            for (int k = 0; k < shown; k++) {
                int i = order[k];
                int entry = visible[i];
                int local = blueprint.positions[entry];
                int x = blueprint.localX(local), y = blueprint.localY(local), z = blueprint.localZ(local);
                boolean ghost = spec.cut() >= 0 && y >= spec.cut();
                int base = ghost ? GHOST : colors[blueprint.states[entry]];
                int open = openFaces[i];
                face(canvas, projector, x, y, z, 1, 0, (open & 1) != 0, base, cos, sin);
                face(canvas, projector, x, y, z, -1, 0, (open & 2) != 0, base, cos, sin);
                face(canvas, projector, x, y, z, 0, 1, (open & 8) != 0, base, cos, sin);
                face(canvas, projector, x, y, z, 0, -1, (open & 16) != 0, base, cos, sin);
                if ((open & 4) != 0) {
                    double[][] top = {projector.at(x, y + 1, z), projector.at(x + 1, y + 1, z), projector.at(x + 1, y + 1, z + 1),
                            projector.at(x, y + 1, z + 1)};
                    canvas.fill(top, shade(base, 1.0));
                }
            }
            canvas.outline();
            for (int py = 0; py < H; py++) {
                for (int px = 0; px < W; px++) {
                    image.setPixel(frame * W + px, py, pixels[py * W + px]);
                }
            }
        }
        return image;
    }

    /** Whether a neighbour hides a face. Hologram layers (from the cut up) never hide the built part. */
    private static boolean solid(Blueprint blueprint, int x, int y, int z, int cut) {
        if (x < 0 || y < 0 || z < 0 || x >= blueprint.sizeX || y >= blueprint.sizeY || z >= blueprint.sizeZ) {
            return false;
        }
        if (cut >= 0 && y >= cut) {
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

    private static void face(Canvas canvas, Projector p, int x, int y, int z, int nx, int nz, boolean open, int base, double cos, double sin) {
        if (!open) {
            return;
        }
        // The face's normal turned with the blueprint; it shows if it points at the viewer.
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
        canvas.fill(quad, shade(base, 0.62 + 0.18 * (side < 0 ? 1 : 0) + 0.06 * facing));
    }

    /** A picture being drawn. */
    private record Canvas(int[] pixels, int w, int h) {
        /** Fills a convex quad (painter's order: later faces cover earlier ones), blending see-through colours. */
        void fill(double[][] quad, int color) {
            double minX = w, maxX = 0, minY = h, maxY = 0;
            for (double[] v : quad) {
                minX = Math.min(minX, v[0]);
                maxX = Math.max(maxX, v[0]);
                minY = Math.min(minY, v[1]);
                maxY = Math.max(maxY, v[1]);
            }
            int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(w - 1, (int) Math.ceil(maxX));
            int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(h - 1, (int) Math.ceil(maxY));
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
                    if (inside) {
                        int index = py * w + px;
                        pixels[index] = alpha >= 250 ? color : blend(pixels[index], color);
                    }
                }
            }
        }

        /** A dark one-pixel edge around the whole silhouette, so builds stand out like pixel art. */
        void outline() {
            int[] copy = pixels.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((copy[y * w + x] >>> 24) != 0) {
                        continue;
                    }
                    boolean edge = (x > 0 && (copy[y * w + x - 1] >>> 24) > 80) || (x < w - 1 && (copy[y * w + x + 1] >>> 24) > 80)
                            || (y > 0 && (copy[(y - 1) * w + x] >>> 24) > 80) || (y < h - 1 && (copy[(y + 1) * w + x] >>> 24) > 80);
                    if (edge) {
                        pixels[y * w + x] = 0xB0060A12;
                    }
                }
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

    static int color(BlockState state) {
        if (!state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock) {
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
