package com.skyrpg.utils.client.map;

import com.google.gson.JsonObject;
import com.skyrpg.core.client.config.ClientConfig;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The minimap: a square of the map around you, north up (or turned with you), NPCs and waypoints, other
 * players as faces, you as an arrow, and caves when you're underground. Zoom with the zoom keys; size and place it in the HUD
 * editor like any other box.
 */
public final class MinimapWidget implements HudWidget {
    static final int MAP = 96;
    private static final int PAD = 3;
    private static final int SIZE = MAP + PAD * 2;
    /** Pixels per block at each zoom step. */
    private static final float[] ZOOMS = {0.25f, 0.5f, 1f, 2f, 4f};
    private static final int DEFAULT_ZOOM = 2;
    /** The hub is small and busy: closer in by default. */
    private static final int HUB_ZOOM = 3;
    private static final long ZOOM_LABEL_MS = 1200;

    private static long zoomedAt;

    @Override
    public Identifier id() {
        return SkyRpgUtilsClient.id("minimap");
    }

    @Override
    public Component name() {
        return Component.literal("Minimap");
    }

    @Override
    public int width(Font font) {
        return SIZE;
    }

    @Override
    public int height(Font font) {
        return SIZE;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_RIGHT;
    }

    @Override
    public int defaultX() {
        return -4;
    }

    @Override
    public int defaultY() {
        return 4;
    }

    @Override
    public boolean visible() {
        return UtilsSettings.minimap().get() && MapCache.active();
    }

    /** The zoom is remembered per dimension. */
    private static String zoomKey() {
        return MapCache.active() ? "minimap_zoom/" + MapCache.dimension().identifier() : "minimap_zoom";
    }

    static int zoomIndex() {
        JsonObject utils = ClientConfig.section("utils");
        String key = zoomKey();
        if (utils.has(key)) {
            return Mth.clamp(utils.get(key).getAsInt(), 0, ZOOMS.length - 1);
        }
        return MapCache.active() && MapCache.dimension().identifier().getNamespace().equals("skyrpg_hub") ? HUB_ZOOM : DEFAULT_ZOOM;
    }

    /** One zoom step in (positive) or out (negative). */
    public static void zoom(int steps) {
        int index = Mth.clamp(zoomIndex() + steps, 0, ZOOMS.length - 1);
        ClientConfig.section("utils").addProperty(zoomKey(), index);
        ClientConfig.save();
        zoomedAt = Util.getMillis();
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Draw.panel(g, 0, 0, SIZE, SIZE, 0xF0121620, Theme.BORDER);
        if (player == null || !MapCache.active()) {
            g.fill(PAD, PAD, PAD + MAP, PAD + MAP, MapRenderer.VOID);
            Draw.text(g, font, "Minimap", (SIZE - font.width("Minimap")) / 2, SIZE / 2 - 4, Theme.MUTED);
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 pos = player.getPosition(partialTick);
        float turn = UtilsSettings.rotate().get() ? MapRenderer.facingUp(player.getYRot(partialTick)) : 0;
        float scale = ZOOMS[zoomIndex()];
        MapRenderer.View view = new MapRenderer.View(pos.x, pos.z, scale, PAD, PAD, MAP, MAP, turn);
        boolean caves = UtilsSettings.caves().get() && CaveView.active();
        MapRenderer.drawMap(g, view, caves);

        if (UtilsSettings.markers().get()) {
            var markers = new java.util.ArrayList<>(MapMarkers.collect(MapCache.dimension().identifier()));
            if (!UtilsSettings.wpMinimap().get()) {
                markers.removeIf(marker -> Waypoints.of(marker) != null || marker.id().getPath().equals("target"));
            }
            MapRenderer.drawMarkers(g, font, view, markers, scale >= 2 ? 8 : 6, false);
        }
        if (UtilsSettings.wpMinimap().get()) {
            WaypointDraw.edgeArrows(g, view);
        }
        if (UtilsSettings.players().get()) {
            MapRenderer.drawPlayers(g, view, 8, partialTick);
        }
        MapRenderer.drawSelf(g, view, player, partialTick, 11);
        drawNorth(g, font, turn);

        if (caves) {
            Draw.text(g, font, "Caves", PAD + 3, PAD + MAP - 10, Theme.AQUA);
        }
        long since = Util.getMillis() - zoomedAt;
        if (since < ZOOM_LABEL_MS) {
            String label = scale >= 1 ? (int) scale + " px / block" : Math.round(1 / scale) + " blocks / px";
            int w = font.width(label) + 6;
            int x = (SIZE - w) / 2;
            Draw.panel(g, x, PAD + MAP - 14, w, 12, 0xE0121620, Theme.BORDER);
            Draw.text(g, font, label, x + 3, PAD + MAP - 12, Theme.TEXT);
        }
    }

    /** An "N" on the frame where north is. */
    private static void drawNorth(GuiGraphicsExtractor g, Font font, float turn) {
        float sin = (float) Math.sin(turn);
        float cos = (float) -Math.cos(turn);
        float reach = (MAP / 2f - 5) / Math.max(Math.abs(sin), Math.abs(cos));
        int x = Math.round(SIZE / 2f + sin * reach);
        int y = Math.round(SIZE / 2f + cos * reach);
        g.fill(x - 4, y - 5, x + 4, y + 4, 0xC0101318);
        Draw.text(g, font, "N", x - font.width("N") / 2 + 1, y - 4, Theme.ACCENT);
    }
}
