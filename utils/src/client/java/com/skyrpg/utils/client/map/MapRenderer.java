package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the map for the minimap and the full map: the tiles (or the cave view) under a view centred on a
 * world position, at some pixels per block, optionally turned so the player's facing is up, plus markers
 * ({@link MapMarkers}: NPCs, waypoints...), other players and you.
 * Positions are worked out relative to the view's centre, so they stay exact far from 0, 0.
 */
public final class MapRenderer {
    /** Behind unmapped areas. */
    public static final int VOID = 0xFF0B0E14;
    private static final Identifier ARROW = SkyRpgUtilsClient.id("map/arrow");
    private static final int ARROW_SIZE = 16;
    private static final float LABEL_SCALE = 0.75f;
    private static boolean arrowMade;

    /** A view of the map: centre in blocks, scale, the screen box it fills, and its turn (radians, 0 = north up). */
    public record View(double centerX, double centerZ, float pixelsPerBlock, int left, int top, int width, int height, float turn) {
        float midX() {
            return left + width / 2f;
        }

        float midY() {
            return top + height / 2f;
        }

        /** Screen x, y of a world position. */
        public float[] toScreen(double x, double z) {
            float dx = (float) ((x - centerX) * pixelsPerBlock);
            float dz = (float) ((z - centerZ) * pixelsPerBlock);
            float cos = (float) Math.cos(turn);
            float sin = (float) Math.sin(turn);
            return new float[] {midX() + dx * cos - dz * sin, midY() + dx * sin + dz * cos};
        }

        /** World x, z under a screen position (unturned views only). */
        public double[] toWorld(double sx, double sy) {
            return new double[] {centerX + (sx - midX()) / pixelsPerBlock, centerZ + (sy - midY()) / pixelsPerBlock};
        }

        boolean contains(float sx, float sy, int margin) {
            return sx >= left + margin && sx < left + width - margin && sy >= top + margin && sy < top + height - margin;
        }
    }

    private MapRenderer() {
    }

    /** The turn that puts the way the player faces at the top. */
    public static float facingUp(float yaw) {
        return (float) Math.toRadians(180 - yaw);
    }

    /** The map itself, clipped to the view. With {@code caves}, the cave view where it has data. */
    public static void drawMap(GuiGraphicsExtractor g, View view, boolean caves) {
        g.enableScissor(view.left(), view.top(), view.left() + view.width(), view.top() + view.height());
        g.fill(view.left(), view.top(), view.left() + view.width(), view.top() + view.height(), VOID);
        g.pose().pushMatrix();
        g.pose().translate(view.midX(), view.midY());
        g.pose().rotate(view.turn());
        g.pose().scale(view.pixelsPerBlock(), view.pixelsPerBlock());
        if (caves && CaveView.active()) {
            float dx = (float) (CaveView.centerX - CaveView.RADIUS - view.centerX());
            float dz = (float) (CaveView.centerZ - CaveView.RADIUS - view.centerZ());
            g.pose().pushMatrix();
            g.pose().translate(dx, dz);
            g.blit(CaveView.ID, 0, 0, CaveView.SIZE, CaveView.SIZE, 0f, 1f, 0f, 1f);
            g.pose().popMatrix();
        } else {
            double reach = Math.hypot(view.width(), view.height()) / 2 / view.pixelsPerBlock() + 1;
            int minRx = Math.floorDiv((int) Math.floor(view.centerX() - reach), MapTile.SIZE);
            int maxRx = Math.floorDiv((int) Math.ceil(view.centerX() + reach), MapTile.SIZE);
            int minRz = Math.floorDiv((int) Math.floor(view.centerZ() - reach), MapTile.SIZE);
            int maxRz = Math.floorDiv((int) Math.ceil(view.centerZ() + reach), MapTile.SIZE);
            for (int rx = minRx; rx <= maxRx; rx++) {
                for (int rz = minRz; rz <= maxRz; rz++) {
                    MapTile tile = MapCache.tile(rx, rz, false);
                    if (tile == null) {
                        continue;
                    }
                    g.pose().pushMatrix();
                    g.pose().translate((float) ((double) rx * MapTile.SIZE - view.centerX()), (float) ((double) rz * MapTile.SIZE - view.centerZ()));
                    g.blit(tile.id, 0, 0, MapTile.SIZE, MapTile.SIZE, 0f, 1f, 0f, 1f);
                    g.pose().popMatrix();
                }
            }
        }
        g.pose().popMatrix();
        g.disableScissor();
    }

    /** Other players' faces where they are, framed in their team colour (or white). */
    public static void drawPlayers(GuiGraphicsExtractor g, View view, int size, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (AbstractClientPlayer other : minecraft.level.players()) {
            if (other == minecraft.player || other.isInvisible() || other.isSpectator()) {
                continue;
            }
            Vec3 pos = other.getPosition(partialTick);
            float[] at = view.toScreen(pos.x, pos.z);
            if (!view.contains(at[0], at[1], size / 2)) {
                continue;
            }
            int x = Math.round(at[0]) - size / 2;
            int y = Math.round(at[1]) - size / 2;
            int frame = other.getTeamColor() == 0xFFFFFF ? 0xFFFFFFFF : 0xFF000000 | other.getTeamColor();
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, frame);
            PlayerFaceExtractor.extractRenderState(g, other.getSkin(), x, y, size);
        }
    }

    /**
     * Every marker inside the view: its icon ({@code size} px), and with {@code labels} its name under it (or
     * above, if that's where there's room; a name with no room is left for the hover tooltip).
     */
    public static void drawMarkers(GuiGraphicsExtractor g, Font font, View view, List<MapMarkers.Marker> markers, int size, boolean labels) {
        List<float[]> placed = new ArrayList<>();
        for (MapMarkers.Marker marker : markers) {
            float[] at = view.toScreen(marker.x(), marker.z());
            if (!view.contains(at[0], at[1], size / 2)) {
                continue;
            }
            int x = Math.round(at[0]) - size / 2;
            int y = Math.round(at[1]) - size / 2;
            drawIcon(g, marker, x, y, size);
            placed.add(new float[] {x - 1, y - 1, x + size + 1, y + size + 1});
        }
        if (!labels) {
            return;
        }
        for (MapMarkers.Marker marker : markers) {
            float[] at = view.toScreen(marker.x(), marker.z());
            if (!view.contains(at[0], at[1], size / 2)) {
                continue;
            }
            // Small, on a dark backing so it reads over any terrain.
            float width = (font.width(marker.name()) + 4) * LABEL_SCALE;
            float height = 10 * LABEL_SCALE;
            float left = at[0] - width / 2;
            float below = Math.round(at[1]) - size / 2f + size + 2;
            float above = Math.round(at[1]) - size / 2f - 2 - height;
            float top = free(placed, left, below, width, height) ? below : free(placed, left, above, width, height) ? above : Float.NaN;
            if (Float.isNaN(top)) {
                continue;
            }
            placed.add(new float[] {left, top, left + width, top + height});
            g.pose().pushMatrix();
            g.pose().translate(left, top);
            g.pose().scale(LABEL_SCALE, LABEL_SCALE);
            int textWidth = font.width(marker.name());
            g.fill(0, 0, textWidth + 4, 10, 0xB0101318);
            g.text(font, marker.name(), 2, 1, Theme.TEXT, false);
            g.pose().popMatrix();
        }
    }

    private static boolean free(List<float[]> placed, float x, float y, float w, float h) {
        for (float[] box : placed) {
            if (x < box[2] && box[0] < x + w && y < box[3] && box[1] < y + h) {
                return false;
            }
        }
        return true;
    }

    private static void drawIcon(GuiGraphicsExtractor g, MapMarkers.Marker marker, int x, int y, int size) {
        switch (marker.icon()) {
            case MapMarkers.Face face -> {
                g.fill(x - 1, y - 1, x + size + 1, y + size + 1, marker.color());
                PlayerFaceExtractor.extractRenderState(g, face.skin(), x, y, size, true, false, -1);
            }
            case MapMarkers.Item item -> {
                Draw.rounded(g, x - 1, y - 1, size + 2, size + 2, 0xC0101318);
                Draw.item(g, item.stack(), x, y, size / 16f);
            }
            case MapMarkers.Dot dot -> {
                int inset = Math.max(1, size / 5);
                Draw.rounded(g, x + inset - 1, y + inset - 1, size - inset * 2 + 2, size - inset * 2 + 2, 0xFF101318);
                Draw.rounded(g, x + inset, y + inset, size - inset * 2, size - inset * 2, dot.color());
            }
        }
    }

    /** The marker under the mouse (within its icon), nearest first, or null. */
    public static MapMarkers.Marker hovered(View view, List<MapMarkers.Marker> markers, double mouseX, double mouseY, int size) {
        MapMarkers.Marker best = null;
        double bestDistance = size * size;
        for (MapMarkers.Marker marker : markers) {
            float[] at = view.toScreen(marker.x(), marker.z());
            double distance = (at[0] - mouseX) * (at[0] - mouseX) + (at[1] - mouseY) * (at[1] - mouseY);
            if (distance <= bestDistance && view.contains(at[0], at[1], 0)) {
                best = marker;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The player as an arrow pointing the way they face. */
    public static void drawSelf(GuiGraphicsExtractor g, View view, Player player, float partialTick, int size) {
        ensureArrow();
        Vec3 pos = player.getPosition(partialTick);
        float[] at = view.toScreen(pos.x, pos.z);
        // Turned with the view, plus the player's own heading on the map.
        float heading = (float) Math.toRadians(player.getYRot(partialTick) - 180) + view.turn();
        g.pose().pushMatrix();
        g.pose().translate(at[0], at[1]);
        g.pose().rotate(heading);
        g.blit(ARROW, -size / 2, -size / 2, size - size / 2, size - size / 2, 0f, 1f, 0f, 1f);
        g.pose().popMatrix();
    }

    /** A gold arrowhead with a dark outline, drawn once into a texture. */
    private static void ensureArrow() {
        if (arrowMade) {
            return;
        }
        arrowMade = true;
        NativeImage image = new NativeImage(ARROW_SIZE, ARROW_SIZE, true);
        boolean[][] inside = new boolean[ARROW_SIZE][ARROW_SIZE];
        for (int y = 0; y < ARROW_SIZE; y++) {
            for (int x = 0; x < ARROW_SIZE; x++) {
                inside[y][x] = inArrow(x + 0.5f, y + 0.5f);
            }
        }
        for (int y = 0; y < ARROW_SIZE; y++) {
            for (int x = 0; x < ARROW_SIZE; x++) {
                int color = 0;
                if (inside[y][x]) {
                    color = y < 7 ? Theme.ACCENT : 0xFFD9952A;
                } else if (near(inside, x, y)) {
                    color = 0xFF14100A;
                }
                image.setPixel(x, y, color);
            }
        }
        Minecraft.getInstance().getTextureManager().register(ARROW, new DynamicTexture(ARROW::toString, image));
    }

    /** An arrowhead pointing up: tip at the top, notched at the bottom. */
    private static boolean inArrow(float x, float y) {
        float cx = ARROW_SIZE / 2f;
        float tip = 2;
        float base = ARROW_SIZE - 2.5f;
        if (y < tip || y > base) {
            return false;
        }
        float halfWidth = (y - tip) / (base - tip) * (ARROW_SIZE / 2f - 2);
        float notch = Math.max(0, (y - (base - 4)) / 4f) * (ARROW_SIZE / 2f - 2);
        float dx = Math.abs(x - cx);
        return dx <= halfWidth && !(y > base - 4 && dx < notch);
    }

    private static boolean near(boolean[][] inside, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < ARROW_SIZE && ny < ARROW_SIZE && inside[ny][nx]) {
                    return true;
                }
            }
        }
        return false;
    }
}
