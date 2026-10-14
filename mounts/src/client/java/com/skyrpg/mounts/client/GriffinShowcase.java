package com.skyrpg.mounts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.mounts.GriffinColor;
import com.skyrpg.mounts.client.mob.GriffinModel;
import com.skyrpg.mounts.client.mob.GriffinRenderState;
import com.skyrpg.mounts.client.mob.GriffinRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.mounts.SkyRpgMounts;
import com.skyrpg.mounts.client.mob.GriffinFx;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Draws a griffin's 3D model straight into a GUI box (the K menu's Mounts page), through the same "player skin in a menu"
 * path vanilla uses for the skin preview (as the Pets page does): the model is moved and scaled on its root part so its middle
 * sits at the middle of the box, and the box, size and mouse are turned into GUI coordinates first because the picture is
 * drawn separately from the menu's scaled pose.
 */
final class GriffinShowcase {
    private static final float BOX_BLOCKS = 2.125f;
    private static final float BOX_FILL = 0.97f;
    private static final float PIVOT_Y = -1.0625f;
    private static final float PLAYER_ORIGIN_SHIFT = -1.601f;
    /** How big the griffin is drawn, in blocks, along its largest side. */
    private static final float BLOCKS = 2.0f;

    private static Preview preview;
    private static final Map<GriffinColor, Animated> ANIMATED = new EnumMap<>(GriffinColor.class);

    /**
     * An animated colour in the menu: the world renderer tints and adds glow layers per frame, but the menu draws one texture,
     * so the rainbow tint and the glow are composed into a dynamic copy of the base texture a few times a second.
     */
    private static final class Animated {
        private final GriffinColor color;
        private final DynamicTexture texture;
        private final Identifier id;
        private final int[] base;
        private final int[][] glow = new int[2][];
        private long lastFrame = Long.MIN_VALUE;

        Animated(GriffinColor color) {
            this.color = color;
            this.base = load(GriffinRenderer.texture(color));
            if (GriffinFx.glows(color)) {
                glow[0] = load(GriffinRenderer.glowTexture(color, 0));
                glow[1] = load(GriffinRenderer.glowTexture(color, 1));
            }
            this.texture = new DynamicTexture(() -> "griffin showcase " + color.id(), 128, 128, true);
            this.id = SkyRpgMounts.id("dynamic/griffin_showcase_" + color.id());
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        private static int[] load(Identifier id) {
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(); NativeImage image = NativeImage.read(in)) {
                int[] out = new int[128 * 128];
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        out[y * 128 + x] = image.getPixel(x, y);
                    }
                }
                return out;
            } catch (java.io.IOException e) {
                return new int[128 * 128];
            }
        }

        Identifier frame() {
            float ticks = Util.getMillis() / 50f;
            long step = (long) (ticks / 1.5f);
            if (step != lastFrame) {
                lastFrame = step;
                int tint = GriffinFx.tint(color, ticks);
                int glowTint = GriffinFx.glowTint(color, ticks);
                float[] bright = {GriffinFx.brightness(color, 0, ticks), GriffinFx.brightness(color, 1, ticks)};
                NativeImage pixels = texture.getPixels();
                for (int i = 0; i < base.length; i++) {
                    int p = base[i];
                    int a = p >>> 24;
                    int r = (p >> 16 & 255) * (tint >> 16 & 255) / 255;
                    int g = (p >> 8 & 255) * (tint >> 8 & 255) / 255;
                    int b = (p & 255) * (tint & 255) / 255;
                    for (int l = 0; l < 2 && glow[l] != null; l++) {
                        int q = glow[l][i];
                        if ((q >>> 24) != 0) {
                            r += (int) ((q >> 16 & 255) * (glowTint >> 16 & 255) / 255f * bright[l]);
                            g += (int) ((q >> 8 & 255) * (glowTint >> 8 & 255) / 255f * bright[l]);
                            b += (int) ((q & 255) * (glowTint & 255) / 255f * bright[l]);
                            a = 255;
                        }
                    }
                    pixels.setPixel(i & 127, i >> 7, a << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b));
                }
                texture.upload();
            }
            return id;
        }
    }

    private static Identifier texture(GriffinColor color) {
        return GriffinFx.animated(color) ? ANIMATED.computeIfAbsent(color, Animated::new).frame() : GriffinRenderer.texture(color);
    }

    private GriffinShowcase() {
    }

    private static final class Preview extends Model.Simple {
        private final GriffinModel griffin;
        private final GriffinRenderState state = new GriffinRenderState();
        private float[] fit;
        float fly;

        Preview(GriffinModel griffin) {
            super(griffin.root(), RenderTypes::entityCutout);
            this.griffin = griffin;
        }

        /** {largest extent, x, y, z of the middle} in blocks, of the folded standing pose. */
        private float[] fit() {
            if (fit == null) {
                state.fly = 0;
                griffin.setupAnim(state);
                ModelPart root = griffin.root();
                Vector3f lo = new Vector3f(Float.MAX_VALUE);
                Vector3f hi = new Vector3f(-Float.MAX_VALUE);
                root.visit(new PoseStack(), (pose, path, index, cube) -> {
                    for (int i = 0; i < 8; i++) {
                        Vector3f p = pose.pose().transformPosition((i & 1) == 0 ? cube.minX / 16f : cube.maxX / 16f,
                                (i & 2) == 0 ? cube.minY / 16f : cube.maxY / 16f, (i & 4) == 0 ? cube.minZ / 16f : cube.maxZ / 16f, new Vector3f());
                        lo.min(p);
                        hi.max(p);
                    }
                });
                float extent = Math.max(0.05f, Math.max(hi.x - lo.x, Math.max(hi.y - lo.y, hi.z - lo.z)));
                fit = new float[]{extent, (lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f};
            }
            return fit;
        }

        @Override
        public void setupAnim(Unit unit) {
            float[] fit = fit();
            state.ageInTicks = Util.getMillis() / 50f;
            state.fly = fly;
            state.glide = 0;
            state.walkAnimationSpeed = 0;
            state.walkAnimationPos = 0;
            griffin.setupAnim(state);
            float s = BLOCKS / fit[0] * (1f - 0.35f * fly);
            ModelPart root = griffin.root();
            root.xScale = s;
            root.yScale = s;
            root.zScale = s;
            root.setPos(-s * fit[1] * 16f, (PIVOT_Y - PLAYER_ORIGIN_SHIFT - s * fit[2]) * 16f, -s * fit[3] * 16f);
        }
    }

    private static Preview preview() {
        if (preview == null) {
            preview = new Preview(new GriffinModel(Minecraft.getInstance().getEntityModels().bakeLayer(GriffinRenderer.LAYER)));
        }
        return preview;
    }

    /**
     * Draws the griffin in the box (menu units), turning slowly and leaning towards the mouse; {@code spread} 0 to 1 opens
     * the wings (for hovering the card).
     */
    static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, GriffinColor color, float spread, float mouseX, float mouseY) {
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        Vector2f topLeft = pose.transformPosition(new Vector2f(x0, y0));
        Vector2f bottomRight = pose.transformPosition(new Vector2f(x1, y1));
        float boxW = bottomRight.x - topLeft.x;
        float boxH = bottomRight.y - topLeft.y;
        if (boxW < 4 || boxH < 4) {
            return;
        }
        float scale = BOX_FILL * Math.min(boxH, boxW * 1.25f) / BOX_BLOCKS;
        float ticks = Util.getMillis() / 50f;
        float dx = Mth.clamp((mouseX - (x0 + x1) / 2f) / Math.max(1f, (x1 - x0) / 2f), -1.5f, 1.5f);
        float dy = Mth.clamp((mouseY - (y0 + y1) / 2f) / Math.max(1f, (y1 - y0) / 2f), -1.5f, 1.5f);
        float rotY = 40f + Mth.sin(ticks * 0.035f) * 16f + dx * 24f;
        float rotX = -dy * 8f - 6f;
        Preview view = preview();
        view.fly = Mth.clamp(spread, 0f, 1f);
        g.pose().pushMatrix();
        g.pose().identity();
        g.skin(view, texture(color), scale, rotX, rotY, PIVOT_Y, Math.round(topLeft.x), Math.round(topLeft.y),
                Math.round(bottomRight.x), Math.round(bottomRight.y));
        g.pose().popMatrix();
    }
}
