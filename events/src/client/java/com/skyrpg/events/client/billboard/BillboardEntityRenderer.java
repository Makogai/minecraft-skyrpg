package com.skyrpg.events.client.billboard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyrpg.core.client.image.RemoteImages;
import com.skyrpg.events.billboard.BillboardEntity;
import com.skyrpg.events.billboard.BillboardTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Draws a billboard from real block textures (tiled one texture per block, shaded by the world's light): a cabinet with a
 * planked back and a log truss, a front frame standing proud of a recessed 16:9 screen, a gold (or copper, iron) trim,
 * chasing redstone-lamp bulbs along the top and bottom bars, a header cap with two sea-lantern spotlights on arms casting a
 * soft fan of light down the screen, and posts with a brace when it stands on its own. The picture, lit bulbs and lamp faces
 * are full bright.
 *
 * <p>Local axes: {@code a} along the screen (the viewer's right), {@code u} up from the entity's position, {@code o} out of
 * the screen towards the viewer, centred on the cabinet's depth.
 */
public final class BillboardEntityRenderer extends EntityRenderer<BillboardEntity, BillboardEntityRenderer.State> {
    private static final float TILE = 1f;

    public static final class State extends EntityRenderState {
        Direction facing = Direction.SOUTH;
        float width = 6;
        boolean stand;
        int theme;
        int seconds = 8;
        List<String> images = List.of();
    }

    public BillboardEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BillboardEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.facing = entity.facing();
        state.width = entity.screenWidth();
        state.stand = entity.standing();
        state.theme = entity.theme();
        state.seconds = Math.max(2, entity.seconds());
        state.images = entity.images();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        BillboardTheme theme = BillboardTheme.get(state.theme);
        Frame frame = new Frame(state.facing);
        long now = System.currentTimeMillis();

        float w = state.width;
        float h = w * 9 / 16;
        float f = BillboardTheme.frame(w);
        float d = BillboardTheme.depth(w);
        float base = state.stand ? BillboardTheme.POST_HEIGHT : 0;
        float hw = w / 2;
        float front = d / 2;
        float screenO = front - 0.05f;
        float lip = front + 0.07f;
        float top = base + h + 2 * f;
        int light = state.lightCoords;

        List<Part> solid = new ArrayList<>();
        List<Part> glow = new ArrayList<>();
        TextureAtlasSprite back = atlas.getSprite(block(theme.back()));
        TextureAtlasSprite frameTex = atlas.getSprite(block(theme.frame()));
        TextureAtlasSprite truss = atlas.getSprite(block(theme.truss()));
        TextureAtlasSprite posts = atlas.getSprite(block(theme.posts()));
        TextureAtlasSprite trim = atlas.getSprite(block(theme.trim()));
        TextureAtlasSprite lampOn = atlas.getSprite(block("redstone_lamp_on"));
        TextureAtlasSprite lampOff = atlas.getSprite(block("redstone_lamp"));
        TextureAtlasSprite lantern = atlas.getSprite(block("sea_lantern"));
        TextureAtlasSprite white = atlas.getSprite(block("white_concrete"));
        TextureAtlasSprite black = atlas.getSprite(block("black_concrete"));

        // cabinet: planked body, the screen sits in its face
        box(solid, back, -hw - f, hw + f, base, top, -front, screenO, light);
        // front frame bars standing proud of the screen
        box(solid, frameTex, -hw - f, hw + f, base + f + h, top, screenO, lip, light);
        box(solid, frameTex, -hw - f, hw + f, base, base + f, screenO, lip, light);
        box(solid, frameTex, -hw - f, -hw, base + f, base + f + h, screenO, lip, light);
        box(solid, frameTex, hw, hw + f, base + f, base + f + h, screenO, lip, light);
        // trim round the screen's edge, on the bars' inner lip
        float t = Math.max(0.0625f, f * 0.18f);
        box(solid, trim, -hw - t, hw + t, base + f + h, base + f + h + t, lip, lip + 0.02f, light);
        box(solid, trim, -hw - t, hw + t, base + f - t, base + f, lip, lip + 0.02f, light);
        box(solid, trim, -hw - t, -hw, base + f, base + f + h, lip, lip + 0.02f, light);
        box(solid, trim, hw, hw + t, base + f, base + f + h, lip, lip + 0.02f, light);

        // back truss: log ribs and two rails
        float rib = Math.max(0.1875f, f * 0.45f);
        int ribs = Math.max(2, Math.round(w / 2.5f));
        for (int i = 0; i <= ribs; i++) {
            float a = -hw - f + rib / 2 + (w + 2 * f - rib) * i / ribs;
            box(solid, truss, a - rib / 2, a + rib / 2, base, top, -front - rib, -front, light);
        }
        for (float u : new float[] {base + (top - base) * 0.28f, base + (top - base) * 0.72f}) {
            box(solid, truss, -hw - f, hw + f, u - rib / 2, u + rib / 2, -front - rib * 2, -front - rib, light);
        }

        // header cap
        float capTop = top + 0.25f;
        box(solid, posts, -hw - f - 0.125f, hw + f + 0.125f, top, capTop, -front - 0.125f, lip + 0.25f, light);

        // marquee bulbs on the top and bottom bars, chasing
        float bulb = Math.max(0.125f, Math.min(0.25f, f * 0.42f));
        int step = (int) (now / 170);
        int across = Math.max(3, Math.round((w + 2 * f) / Math.max(0.6f, bulb * 3.2f)));
        for (int i = 0; i <= across; i++) {
            float a = -hw - f + bulb + (w + 2 * f - 2 * bulb) * i / across;
            for (int row = 0; row < 2; row++) {
                float u = row == 0 ? base + f + h + f / 2 : base + f / 2;
                boolean on = (i + row + step) % 3 != 0;
                box(on ? glow : solid, on ? lampOn : lampOff, a - bulb / 2, a + bulb / 2, u - bulb / 2, u + bulb / 2, lip, lip + bulb,
                        on ? LightCoordsUtil.FULL_BRIGHT : light);
            }
        }

        // two spotlights on arms over the screen
        float reach = Math.max(0.75f, Math.min(1.75f, h * 0.24f));
        for (float side : new float[] {-1, 1}) {
            float x = side * w * 0.26f;
            box(solid, posts, x - 0.0625f, x + 0.0625f, capTop, capTop + 0.125f, lip, lip + reach, light);
            box(solid, posts, x - 0.1875f, x + 0.1875f, capTop - 0.0625f, capTop + 0.3125f, lip + reach - 0.25f, lip + reach + 0.0625f, light);
            // the glowing lens faces down and back at the screen
            quad(glow, lantern, new float[] {x - 0.15f, capTop - 0.0626f, lip + reach - 0.22f}, new float[] {0.3f, 0, 0},
                    new float[] {0, 0, 0.25f}, LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0xFFFFFFFF);
            // the light: a soft fan from the lamp down the picture
            int bright = 0x40FFF4D8;
            int clear = 0x00FFF4D8;
            fan(glow, white, x, capTop - 0.07f, lip + reach - 0.1f, w * 0.23f, base + f + h * 0.35f, screenO + 0.01f, bright, clear);
        }

        if (state.stand) {
            float post = Math.max(0.25f, Math.min(0.5f, w * 0.035f));
            float px = w * 0.3f;
            for (float side : new float[] {-1, 1}) {
                box(solid, posts, side * px - post / 2, side * px + post / 2, 0, base, -post / 2, post / 2, light);
                box(solid, frameTex, side * px - post * 1.4f, side * px + post * 1.4f, 0, 0.125f, -post * 1.4f, post * 1.4f, light);
            }
            box(solid, truss, -px, px, base * 0.45f, base * 0.45f + 0.1875f, -0.09f, 0.09f, light);
        }

        submitParts(pose, collector, solid, frame, true);
        submitParts(pose, collector, glow, frame, false);

        // the picture (or a dark screen while there is none)
        RemoteImages.Entry image = state.images.isEmpty() ? null
                : RemoteImages.get(state.images.get((int) ((now / 1000 / state.seconds) % state.images.size())));
        if (image != null && image.ready()) {
            picture(pose, collector, frame, image, -hw, hw, base + f, base + f + h, screenO + 0.002f);
        } else {
            List<Part> dark = new ArrayList<>();
            quad(dark, black, new float[] {-hw, base + f, screenO + 0.002f}, new float[] {w, 0, 0}, new float[] {0, h, 0},
                    LightCoordsUtil.FULL_BRIGHT, 0xFF101014, 0xFF101014);
            submitParts(pose, collector, dark, frame, false);
        }
        super.submit(state, pose, collector, camera);
    }

    private static Identifier block(String name) {
        return Identifier.withDefaultNamespace("block/" + name);
    }

    // ---------------------------------------------------------------- geometry

    /** World directions of the local axes for a billboard facing {@code facing}. */
    private record Frame(float rx, float rz, float nx, float nz) {
        Frame(Direction facing) {
            this(facing.getStepZ(), -facing.getStepX(), facing.getStepX(), facing.getStepZ());
        }

        float x(float a, float o) {
            return rx * a + nx * o;
        }

        float z(float a, float o) {
            return rz * a + nz * o;
        }
    }

    /**
     * A textured rectangle: corner p, edges e1 and e2 (e1 x e2 points out), tiled in TILE-sized cells. With {@code corners}
     * (four points, 12 floats) it is that exact quad instead, stretched over one texture, fading from color to colorFar.
     */
    private record Part(TextureAtlasSprite sprite, float[] p, float[] e1, float[] e2, int light, int color, int colorFar, float[] corners) {
        Part(TextureAtlasSprite sprite, float[] p, float[] e1, float[] e2, int light, int color, int colorFar) {
            this(sprite, p, e1, e2, light, color, colorFar, null);
        }
    }

    private static void quad(List<Part> out, TextureAtlasSprite sprite, float[] p, float[] e1, float[] e2, int light, int color, int colorFar) {
        out.add(new Part(sprite, p, e1, e2, light, color, colorFar));
    }

    private static void box(List<Part> out, TextureAtlasSprite s, float a0, float a1, float u0, float u1, float o0, float o1, int light) {
        float da = a1 - a0;
        float du = u1 - u0;
        float dO = o1 - o0;
        int c = 0xFFFFFFFF;
        quad(out, s, new float[] {a0, u0, o1}, new float[] {da, 0, 0}, new float[] {0, du, 0}, light, c, c);       // front
        quad(out, s, new float[] {a1, u0, o0}, new float[] {-da, 0, 0}, new float[] {0, du, 0}, light, c, c);      // back
        quad(out, s, new float[] {a0, u1, o1}, new float[] {da, 0, 0}, new float[] {0, 0, -dO}, light, c, c);      // top
        quad(out, s, new float[] {a0, u0, o0}, new float[] {da, 0, 0}, new float[] {0, 0, dO}, light, c, c);       // bottom
        quad(out, s, new float[] {a1, u0, o1}, new float[] {0, 0, -dO}, new float[] {0, du, 0}, light, c, c);      // right
        quad(out, s, new float[] {a0, u0, o0}, new float[] {0, 0, dO}, new float[] {0, du, 0}, light, c, c);       // left
    }

    /** A see-through fan of light from a lamp at (x, u, o) down to height {@code low} on the screen. */
    private static void fan(List<Part> out, TextureAtlasSprite white, float x, float u, float o, float spread, float low, float screenO,
                            int bright, int clear) {
        // corners: lamp left, lamp right, screen right, screen left; the colour fades from the lamp
        out.add(new Part(white, null, null, null, LightCoordsUtil.FULL_BRIGHT, bright, clear,
                new float[] {x - 0.12f, u, o, x + 0.12f, u, o, x + spread, low, screenO, x - spread, low, screenO}));
    }

    private static void submitParts(PoseStack pose, SubmitNodeCollector collector, List<Part> parts, Frame frame, boolean shaded) {
        if (parts.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(pose, shaded ? RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS) : RenderTypes.text(TextureAtlas.LOCATION_BLOCKS),
                (p, buffer) -> {
                    for (Part part : parts) {
                        emit(buffer, p, frame, part, shaded);
                    }
                });
    }

    /** Splits a part into texture-sized cells so textures keep their real size, and writes the vertices. */
    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, Frame frame, Part part, boolean shaded) {
        if (part.corners() != null) {
            float[] c = part.corners();
            TextureAtlasSprite s = part.sprite();
            float[][] uv = {{s.getU0(), s.getV0()}, {s.getU1(), s.getV0()}, {s.getU1(), s.getV1()}, {s.getU0(), s.getV1()}};
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, frame.x(c[i * 3], c[i * 3 + 2]), c[i * 3 + 1], frame.z(c[i * 3], c[i * 3 + 2]))
                        .setColor(i < 2 ? part.color() : part.colorFar()).setUv(uv[i][0], uv[i][1]).setLight(part.light());
            }
            return;
        }
        float[] p = part.p();
        float[] e1 = part.e1();
        float[] e2 = part.e2();
        float l1 = (float) Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]);
        float l2 = (float) Math.sqrt(e2[0] * e2[0] + e2[1] * e2[1] + e2[2] * e2[2]);
        // normal = e1 x e2 (in local axes), turned into world axes
        float na = e1[1] * e2[2] - e1[2] * e2[1];
        float nu = e1[2] * e2[0] - e1[0] * e2[2];
        float no = e1[0] * e2[1] - e1[1] * e2[0];
        float nl = (float) Math.sqrt(na * na + nu * nu + no * no);
        float wx = nl == 0 ? 0 : frame.x(na, no) / nl;
        float wy = nl == 0 ? 1 : nu / nl;
        float wz = nl == 0 ? 0 : frame.z(na, no) / nl;
        boolean fade = part.color() != part.colorFar();
        int cells1 = fade ? 1 : Math.max(1, (int) Math.ceil(l1 / TILE - 1e-4));
        int cells2 = fade ? 1 : Math.max(1, (int) Math.ceil(l2 / TILE - 1e-4));
        TextureAtlasSprite s = part.sprite();
        for (int i = 0; i < cells1; i++) {
            float s0 = i * TILE / Math.max(l1, 1e-4f);
            float s1 = Math.min(1, (i + 1) * TILE / Math.max(l1, 1e-4f));
            if (fade) {
                s0 = 0;
                s1 = 1;
            }
            for (int j = 0; j < cells2; j++) {
                float t0 = j * TILE / Math.max(l2, 1e-4f);
                float t1 = Math.min(1, (j + 1) * TILE / Math.max(l2, 1e-4f));
                if (fade) {
                    t0 = 0;
                    t1 = 1;
                }
                // texture fraction of this cell: a full tile, or the part of one that fits
                float fu = fade ? 1 : (s1 - s0) * l1 / TILE;
                float fv = fade ? 1 : (t1 - t0) * l2 / TILE;
                float u0 = s.getU0();
                float u1 = s.getU0() + (s.getU1() - s.getU0()) * fu;
                float vTop = s.getV0();
                float vBottom = s.getV0() + (s.getV1() - s.getV0()) * fv;
                int near = part.color();
                int far = part.colorFar();
                corner(buffer, pose, frame, p, e1, e2, s0, t0, u0, vBottom, near, part.light(), wx, wy, wz, shaded);
                corner(buffer, pose, frame, p, e1, e2, s1, t0, u1, vBottom, near, part.light(), wx, wy, wz, shaded);
                corner(buffer, pose, frame, p, e1, e2, s1, t1, u1, vTop, far, part.light(), wx, wy, wz, shaded);
                corner(buffer, pose, frame, p, e1, e2, s0, t1, u0, vTop, far, part.light(), wx, wy, wz, shaded);
            }
        }
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Frame frame, float[] p, float[] e1, float[] e2, float s, float t,
                               float u, float v, int color, int light, float wx, float wy, float wz, boolean shaded) {
        float a = p[0] + e1[0] * s + e2[0] * t;
        float up = p[1] + e1[1] * s + e2[1] * t;
        float o = p[2] + e1[2] * s + e2[2] * t;
        buffer.addVertex(pose, frame.x(a, o), up, frame.z(a, o)).setColor(color).setUv(u, v);
        if (shaded) {
            buffer.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, wx, wy, wz);
        } else {
            buffer.setLight(light);
        }
    }

    /** The picture on the screen, cropped to fill it, full bright. */
    private static void picture(PoseStack pose, SubmitNodeCollector collector, Frame frame, RemoteImages.Entry image, float a0, float a1, float u0,
                                float u1, float o) {
        float boardAspect = (a1 - a0) / (u1 - u0);
        float aspect = image.width() / (float) image.height();
        float tu0 = 0;
        float tu1 = 1;
        float tv0 = 0;
        float tv1 = 1;
        if (aspect > boardAspect) {
            float keep = boardAspect / aspect;
            tu0 = (1 - keep) / 2;
            tu1 = 1 - tu0;
        } else {
            float keep = aspect / boardAspect;
            tv0 = (1 - keep) / 2;
            tv1 = 1 - tv0;
        }
        float fu0 = tu0;
        float fu1 = tu1;
        float fv0 = tv0;
        float fv1 = tv1;
        int light = LightCoordsUtil.FULL_BRIGHT;
        collector.submitCustomGeometry(pose, RenderTypes.text(image.texture()), (p, buffer) -> {
            buffer.addVertex(p, frame.x(a0, o), u0, frame.z(a0, o)).setColor(0xFFFFFFFF).setUv(fu0, fv1).setLight(light);
            buffer.addVertex(p, frame.x(a1, o), u0, frame.z(a1, o)).setColor(0xFFFFFFFF).setUv(fu1, fv1).setLight(light);
            buffer.addVertex(p, frame.x(a1, o), u1, frame.z(a1, o)).setColor(0xFFFFFFFF).setUv(fu1, fv0).setLight(light);
            buffer.addVertex(p, frame.x(a0, o), u1, frame.z(a0, o)).setColor(0xFFFFFFFF).setUv(fu0, fv0).setLight(light);
        });
    }
}
