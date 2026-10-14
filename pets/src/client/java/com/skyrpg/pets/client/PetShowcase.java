package com.skyrpg.pets.client;

import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.client.model.PetModel;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * Draws a pet's 3D model straight into a GUI box (the K menu's Pets page). It goes through the same "player skin in a
 * menu" path vanilla uses for the skin preview, with the pet's own model parts: the model is moved and scaled (on its
 * root part) so its middle sits at the middle of the box and it fills it, whatever its kind. Because the picture is
 * drawn separately from the menu's scaled pose, the box, size and mouse are turned into GUI coordinates first, the
 * same idea as core's {@code Draw.entityFollowsMouse}. One model at a time per kind: the pose is set just before drawing.
 */
final class PetShowcase {
    /** Vanilla's skin preview shows a 2.125-block-tall player in a box of this height at this scale factor. */
    private static final float BOX_BLOCKS = 2.125f;
    private static final float BOX_FILL = 0.97f;
    /** Where the player model's middle is, in the preview's space (the pet is put there too). */
    private static final float PIVOT_Y = -1.0625f;
    private static final float PLAYER_ORIGIN_SHIFT = -1.601f;
    /** How big the pet is drawn, in blocks, along its largest side (the box is about 2 blocks tall). */
    private static final float PET_BLOCKS = 1.45f;

    private static final Map<PetType, Preview[]> PREVIEWS = new EnumMap<>(PetType.class);

    private PetShowcase() {
    }

    /** A pet model as a "simple" model, which is what the GUI skin picture takes; animates itself from {@link #anim}. */
    private static final class Preview extends Model.Simple {
        private final PetModel pet;
        PetModel.Anim anim;

        Preview(PetModel pet, java.util.function.Function<Identifier, net.minecraft.client.renderer.rendertype.RenderType> renderType) {
            super(pet.root(), renderType);
            this.pet = pet;
        }

        @Override
        public void setupAnim(Unit unit) {
            if (anim == null) {
                return;
            }
            pet.setupAnim(anim);
            float[] fit = pet.fit();
            float s = PET_BLOCKS / fit[0];
            ModelPart root = pet.root();
            root.xScale = s;
            root.yScale = s;
            root.zScale = s;
            // root position is in 1/16 blocks, applied before its scale
            root.setPos(-s * fit[1] * 16f, (PIVOT_Y - PLAYER_ORIGIN_SHIFT - s * fit[2]) * 16f, -s * fit[3] * 16f);
        }
    }

    private static Preview[] previews(PetType type) {
        return PREVIEWS.computeIfAbsent(type, t -> {
            PetModel model = PetModel.of(t);
            return new Preview[]{
                    new Preview(model, id -> model.translucent() ? RenderTypes.entityTranslucent(id) : RenderTypes.entityCutout(id)),
                    new Preview(model, RenderTypes::eyes)};
        });
    }

    /**
     * Draws the pet in the box (menu units), turning slowly and leaning towards the mouse.
     *
     * @param mouseX mouse in the same units as the box
     */
    static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, PetType type, PetRarity rarity, float mouseX, float mouseY) {
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
        float rotY = Mth.sin(ticks * 0.035f) * 24f + dx * 28f;
        float rotX = -dy * 8f - 4f;

        Preview[] views = previews(type);
        PetModel model = PetModel.of(type);
        PetModel.Anim anim = new PetModel.Anim(rarity, ticks, 0.37f, Mth.clamp(dx * 0.5f, -0.9f, 0.9f), Mth.clamp(dy * 0.35f, -0.5f, 0.5f), 0f, 0f, 0f);
        g.pose().pushMatrix();
        g.pose().identity();
        views[0].anim = anim;
        g.skin(views[0], model.texture(rarity), scale, rotX, rotY, PIVOT_Y, Math.round(topLeft.x), Math.round(topLeft.y),
                Math.round(bottomRight.x), Math.round(bottomRight.y));
        Identifier glow = model.glowTexture(rarity);
        if (glow != null) {
            views[1].anim = anim;
            g.skin(views[1], glow, scale, rotX, rotY, PIVOT_Y, Math.round(topLeft.x), Math.round(topLeft.y), Math.round(bottomRight.x),
                    Math.round(bottomRight.y));
        }
        g.pose().popMatrix();
    }
}
