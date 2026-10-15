package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.builder.Blueprint;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * The "real" turntable: every block drawn from its actual model faces with its actual textures (torches, flowers,
 * glass panes and stairs look like themselves), painted back to front in software like {@link Previews}' simple
 * cubes. Water and lava are a tinted surface. Faces hidden behind a neighbouring full block are left out. Too big a
 * build ({@link #MAX_FACES}) falls back to the simple cubes.
 */
final class RealPreview {
    static final int MAX_FACES = 60_000;
    private static final double TILT = Math.toRadians(32);
    private static final int GHOST = 0x5CE1E6;

    private RealPreview() {
    }

    /** A block texture's pixels (the first animation frame), ARGB. */
    private record Tex(int w, int h, int[] argb) {
    }

    /** One face of a block model: corners in block space, texture coordinates in the texture, and how it's lit. */
    private record Face(float[] pos, float[] uv, Direction normal, Direction cull, Tex tex, int tint, int flat) {
    }

    private static final Map<SpriteContents, Tex> TEXTURES = new ConcurrentHashMap<>();
    private static final Tex MISSING = new Tex(1, 1, new int[]{0xFFFF00FF});
    private static Field originalImage;

    static void clear() {
        TEXTURES.clear();
    }

    private static Tex texture(TextureAtlasSprite sprite) {
        return TEXTURES.computeIfAbsent(sprite.contents(), contents -> {
            try {
                if (originalImage == null) {
                    Field field = SpriteContents.class.getDeclaredField("originalImage");
                    field.setAccessible(true);
                    originalImage = field;
                }
                NativeImage image = (NativeImage) originalImage.get(contents);
                if (image == null || image.isClosed()) {
                    return MISSING;
                }
                int w = Math.min(contents.width(), image.getWidth());
                int h = Math.min(contents.height(), image.getHeight());
                int[] pixels = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        pixels[y * w + x] = image.getPixel(x, y);
                    }
                }
                return new Tex(w, h, pixels);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return MISSING;
            }
        });
    }

    private static List<Face> faces(BlockState state) {
        List<Face> faces = new ArrayList<>();
        if (state.getBlock() instanceof LiquidBlock) {
            int color = state.is(Blocks.LAVA) ? 0xFFD8661C : 0xA03F76E4;
            float top = 0.875f;
            faces.add(new Face(new float[]{0, top, 0, 0, top, 1, 1, top, 1, 1, top, 0}, null, Direction.UP, Direction.UP, null, 0xFFFFFF, color));
            faces.add(new Face(new float[]{0, 0, 1, 1, 0, 1, 1, top, 1, 0, top, 1}, null, Direction.SOUTH, Direction.SOUTH, null, 0xFFFFFF, color));
            faces.add(new Face(new float[]{1, 0, 0, 0, 0, 0, 0, top, 0, 1, top, 0}, null, Direction.NORTH, Direction.NORTH, null, 0xFFFFFF, color));
            faces.add(new Face(new float[]{1, 0, 1, 1, 0, 0, 1, top, 0, 1, top, 1}, null, Direction.EAST, Direction.EAST, null, 0xFFFFFF, color));
            faces.add(new Face(new float[]{0, 0, 0, 0, 0, 1, 0, top, 1, 0, top, 0}, null, Direction.WEST, Direction.WEST, null, 0xFFFFFF, color));
            return faces;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42), parts);
        Direction[] sides = Direction.values();
        for (int side = 0; side <= sides.length; side++) {
            Direction cull = side < sides.length ? sides[side] : null;
            for (BlockStateModelPart part : parts) {
                for (BakedQuad quad : part.getQuads(cull)) {
                    TextureAtlasSprite sprite = quad.materialInfo().sprite();
                    float u0 = sprite.getU0();
                    float du = Math.max(1e-6f, sprite.getU1() - u0);
                    float v0 = sprite.getV0();
                    float dv = Math.max(1e-6f, sprite.getV1() - v0);
                    float[] pos = new float[12];
                    float[] uv = new float[8];
                    for (int v = 0; v < 4; v++) {
                        Vector3fc corner = quad.position(v);
                        pos[v * 3] = corner.x();
                        pos[v * 3 + 1] = corner.y();
                        pos[v * 3 + 2] = corner.z();
                        long packed = quad.packedUV(v);
                        uv[v * 2] = (UVPair.unpackU(packed) - u0) / du;
                        uv[v * 2 + 1] = (UVPair.unpackV(packed) - v0) / dv;
                    }
                    int tint = 0xFFFFFF;
                    if (quad.materialInfo().isTinted()) {
                        try {
                            BlockTintSource source = minecraft.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
                            tint = source == null ? 0xFFFFFF : source.color(state) & 0xFFFFFF;
                        } catch (RuntimeException e) {
                            tint = 0x7FB238;
                        }
                    }
                    faces.add(new Face(pos, uv, quad.direction(), cull, texture(sprite), tint, 0));
                }
            }
        }
        return faces;
    }

    /** A face of one block of the blueprint. */
    private record Placed(int x, int y, int z, Face face, boolean ghost) {
    }

    /** The frame strip, or null when the build is too big for real faces (the caller draws simple cubes). */
    static NativeImage render(Blueprint blueprint, Previews.Spec spec) {
        int count = blueprint.count();
        List<List<Face>> byPalette = new ArrayList<>();
        boolean[] opaque = new boolean[blueprint.palette.size()];
        for (int i = 0; i < blueprint.palette.size(); i++) {
            BlockState state = blueprint.palette.get(i);
            byPalette.add(faces(state));
            opaque[i] = state.isSolidRender();
        }
        List<Placed> placed = new ArrayList<>();
        for (int e = 0; e < count; e++) {
            int local = blueprint.positions[e];
            int x = blueprint.localX(local), y = blueprint.localY(local), z = blueprint.localZ(local);
            boolean ghost = spec.cut() >= 0 && y >= spec.cut();
            for (Face face : byPalette.get(blueprint.states[e])) {
                if (face.cull() != null && hidden(blueprint, opaque, x, y, z, face.cull(), spec.cut())) {
                    continue;
                }
                placed.add(new Placed(x, y, z, face, ghost));
                if (placed.size() > MAX_FACES) {
                    return null;
                }
            }
        }
        int W = spec.w();
        int H = spec.h();
        int sx = blueprint.sizeX, sy = blueprint.sizeY, sz = blueprint.sizeZ;
        double radius = Math.sqrt(sx * sx + sz * sz) / 2.0;
        double scale = Math.min((W - 8) / (2 * radius), (H - 8) / (sy * Math.cos(TILT) + 2 * radius * Math.sin(TILT)));
        double tiltCos = Math.cos(TILT), tiltSin = Math.sin(TILT);
        NativeImage image = new NativeImage(W * spec.frames(), H, true);
        int[] pixels = new int[W * H];
        int n = placed.size();
        double[] depth = new double[n];
        double[] screen = new double[n * 8];
        Integer[] order = new Integer[n];
        for (int frame = 0; frame < spec.frames(); frame++) {
            Arrays.fill(pixels, 0);
            double angle = Math.PI / 4 + frame * 2 * Math.PI / spec.frames();
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double ox = W / 2.0, oy = H / 2.0 + (sy * tiltCos * scale) / 2.0;
            // Towards the viewer, in blueprint space: faces pointing away are skipped.
            double vx = sin * tiltCos, vy = tiltSin, vz = cos * tiltCos;
            int shown = 0;
            for (int i = 0; i < n; i++) {
                Placed p = placed.get(i);
                Direction normal = p.face().normal();
                if (normal.getStepX() * vx + normal.getStepY() * vy + normal.getStepZ() * vz < -0.02) {
                    continue;
                }
                float[] pos = p.face().pos();
                double sum = 0;
                for (int v = 0; v < 4; v++) {
                    double cx = p.x() + pos[v * 3] - sx / 2.0;
                    double cy = p.y() + pos[v * 3 + 1];
                    double cz = p.z() + pos[v * 3 + 2] - sz / 2.0;
                    double rx = cx * cos - cz * sin;
                    double rz = cx * sin + cz * cos;
                    screen[i * 8 + v * 2] = ox + rx * scale;
                    screen[i * 8 + v * 2 + 1] = oy + (-cy * tiltCos + rz * tiltSin) * scale;
                    sum += rz * tiltCos + cy * tiltSin;
                }
                depth[i] = sum / 4;
                order[shown++] = i;
            }
            Arrays.sort(order, 0, shown, (a, b) -> Double.compare(depth[a], depth[b]));
            for (int k = 0; k < shown; k++) {
                int i = order[k];
                Placed p = placed.get(i);
                double shade = shade(p.face().normal(), cos, sin);
                drawFace(pixels, W, H, screen, i * 8, p.face(), shade, p.ghost());
            }
            new Previews.Canvas(pixels, W, H).outline();
            for (int py = 0; py < H; py++) {
                for (int px = 0; px < W; px++) {
                    image.setPixel(frame * W + px, py, pixels[py * W + px]);
                }
            }
        }
        return image;
    }

    private static boolean hidden(Blueprint blueprint, boolean[] opaque, int x, int y, int z, Direction side, int cut) {
        int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
        if (nx < 0 || ny < 0 || nz < 0 || nx >= blueprint.sizeX || ny >= blueprint.sizeY || nz >= blueprint.sizeZ) {
            return false;
        }
        if (cut >= 0 && ny >= cut && y < cut) {
            return false;
        }
        int entry = blueprint.entryAt(nx + nz * blueprint.sizeX + ny * blueprint.sizeX * blueprint.sizeZ);
        return entry >= 0 && opaque[blueprint.states[entry]];
    }

    /** Tops bright, bottoms dark, sides lit from the viewer's left. */
    private static double shade(Direction normal, double cos, double sin) {
        if (normal == Direction.UP) {
            return 1.0;
        }
        if (normal == Direction.DOWN) {
            return 0.5;
        }
        double nx = normal.getStepX(), nz = normal.getStepZ();
        double facing = nx * sin + nz * cos;
        double side = nx * cos - nz * sin;
        return 0.66 + 0.16 * (side < 0 ? 1 : 0) + 0.06 * facing;
    }

    /** Paints one face as two triangles, texture-mapped (or flat), skipping see-through texels and blending translucent ones. */
    private static void drawFace(int[] pixels, int w, int h, double[] s, int at, Face face, double shade, boolean ghost) {
        triangle(pixels, w, h, s, at, 0, 1, 2, face, shade, ghost);
        triangle(pixels, w, h, s, at, 0, 2, 3, face, shade, ghost);
    }

    private static void triangle(int[] pixels, int w, int h, double[] s, int at, int a, int b, int c, Face face, double shade, boolean ghost) {
        double ax = s[at + a * 2], ay = s[at + a * 2 + 1];
        double bx = s[at + b * 2], by = s[at + b * 2 + 1];
        double cx = s[at + c * 2], cy = s[at + c * 2 + 1];
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-6) {
            return;
        }
        int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int y1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        float[] uv = face.uv();
        Tex tex = face.tex();
        double eps = -0.02 * Math.abs(area);
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                double qx = px + 0.5, qy = py + 0.5;
                double wa = (bx - qx) * (cy - qy) - (by - qy) * (cx - qx);
                double wb = (cx - qx) * (ay - qy) - (cy - qy) * (ax - qx);
                double wc = (ax - qx) * (by - qy) - (ay - qy) * (bx - qx);
                if (area > 0 ? (wa < eps || wb < eps || wc < eps) : (wa > -eps || wb > -eps || wc > -eps)) {
                    continue;
                }
                int argb;
                if (tex != null && uv != null) {
                    double la = wa / area, lb = wb / area, lc = wc / area;
                    double u = la * uv[a * 2] + lb * uv[b * 2] + lc * uv[c * 2];
                    double v = la * uv[a * 2 + 1] + lb * uv[b * 2 + 1] + lc * uv[c * 2 + 1];
                    int tx = Math.min(tex.w() - 1, Math.max(0, (int) (u * tex.w())));
                    int ty = Math.min(tex.h() - 1, Math.max(0, (int) (v * tex.h())));
                    argb = tex.argb()[ty * tex.w() + tx];
                } else {
                    argb = face.flat();
                }
                int alpha = argb >>> 24;
                if (alpha < 24) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
                int tint = face.tint();
                r = (int) (r * ((tint >> 16) & 0xFF) / 255.0 * shade);
                g = (int) (g * ((tint >> 8) & 0xFF) / 255.0 * shade);
                bl = (int) (bl * (tint & 0xFF) / 255.0 * shade);
                if (ghost) {
                    r = (r + ((GHOST >> 16) & 0xFF) * 2) / 3;
                    g = (g + ((GHOST >> 8) & 0xFF) * 2) / 3;
                    bl = (bl + (GHOST & 0xFF) * 2) / 3;
                    alpha = Math.min(alpha, 0x60);
                }
                int color = (alpha << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
                int index = py * w + px;
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
}
