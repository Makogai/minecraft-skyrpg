package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The full map (M): everything you've explored in this dimension. Drag to move, scroll to zoom towards the
 * mouse, C to follow yourself, M or Esc to close. Shows you, the other players and every marker (NPCs,
 * waypoints...): hover one to see what it is, and zoomed in their names show. Right-click drops a waypoint (name and colour);
 * click or right-click a marker for Track, Share and (your waypoints) Remove. The bottom bar shows the block coordinates under the mouse.
 */
public final class WorldMapScreen extends Screen {
    private static final float MIN_SCALE = 0.125f;
    private static final float MAX_SCALE = 8f;
    private static final int BAR = 18;
    /** From this zoom on, markers have their names under them. */
    private static final float LABEL_SCALE = 2.4f;

    /** The zoom you last used in each dimension, kept for next time. The map always opens centred on you. */
    private static final Map<Identifier, Float> SAVED_SCALE = new HashMap<>();
    /** The hub is small: it opens closer in. */
    private static final float HUB_SCALE = 3;

    private double centerX;
    private double centerZ;
    private float scale = 1;
    private boolean following;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private static final int POPUP_ROW = 14;
    private MapMarkers.Marker popup;
    private List<Action> popupActions = List.of();
    private int popupX;
    private int popupY;
    private int popupW;
    /** Dragged or zoomed away from where it opened (so resizing the window doesn't snap back). */
    private boolean moved;

    public WorldMapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        if (!moved) {
            Identifier dimension = minecraft.level == null ? null : minecraft.level.dimension().identifier();
            scale = dimension == null ? 1 : SAVED_SCALE.getOrDefault(dimension, dimension.getNamespace().equals("skyrpg_hub") ? HUB_SCALE : 1);
            centre();
        }
    }

    private void centre() {
        if (minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, MapRenderer.VOID);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (following && minecraft.player != null) {
            Vec3 pos = minecraft.player.getPosition(partialTick);
            centerX = pos.x;
            centerZ = pos.z;
        }
        MapRenderer.View view = new MapRenderer.View(centerX, centerZ, scale, 0, BAR, width, height - BAR * 2, 0);
        MapRenderer.drawMap(g, view, false);
        List<MapMarkers.Marker> markers = markers();
        int markerSize = markerSize();
        MapRenderer.drawMarkers(g, font, view, markers, markerSize, scale >= LABEL_SCALE);
        if (UtilsSettings.players().get()) {
            MapRenderer.drawPlayers(g, view, Mth.clamp(Math.round(4 + scale * 4), 8, 14), partialTick);
        }
        if (minecraft.player != null) {
            MapRenderer.drawSelf(g, view, minecraft.player, partialTick, 13);
        }

        // Top bar: what you're looking at; bottom bar: under the mouse, and how to use it.
        g.fill(0, 0, width, BAR, 0xF0121620);
        g.fill(0, BAR - 1, width, BAR, Theme.BORDER);
        g.fill(0, height - BAR, width, height, 0xF0121620);
        g.fill(0, height - BAR, width, height - BAR + 1, Theme.BORDER);
        Draw.text(g, font, Component.literal("World Map").withColor(Theme.ACCENT)
                .append(Component.literal("  ·  " + dimensionName()).withColor(Theme.MUTED)), 8, 5, Theme.TEXT);
        String scaleLabel = scale >= 1 ? fmt(scale) + " px per block" : fmt(1 / scale) + " blocks per px";
        Draw.rightText(g, font, scaleLabel, width - 8, 5, Theme.MUTED);
        if (mouseY > BAR && mouseY < height - BAR) {
            double[] world = view.toWorld(mouseX, mouseY);
            Draw.text(g, font, "X " + (int) Math.floor(world[0]) + "   Z " + (int) Math.floor(world[1]), 8, height - BAR + 5, Theme.TEXT);
        }
        Draw.rightText(g, font, "Drag to move  ·  Scroll to zoom  ·  C " + (following ? "stop following" : "follow you")
                + "  ·  Click a marker  ·  Right-click: waypoint  ·  M close", width - 8, height - BAR + 5, Theme.DIM);
        if (dragging) {
            g.requestCursor(CursorTypes.RESIZE_ALL);
        } else if (popup == null && mouseY > BAR && mouseY < height - BAR) {
            MapMarkers.Marker hovered = MapRenderer.hovered(view, markers, mouseX, mouseY, markerSize);
            if (hovered != null) {
                Draw.tooltip(g, font, tooltip(hovered), mouseX, mouseY, hovered.color());
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
        drawPopup(g, mouseX, mouseY);
    }

    /** Markers here, if they're switched on. */
    private List<MapMarkers.Marker> markers() {
        return UtilsSettings.markers().get() && minecraft.level != null ? MapMarkers.collect(minecraft.level.dimension().identifier()) : List.of();
    }

    private int markerSize() {
        return Mth.clamp(Math.round(6 + scale * 3), 8, 16);
    }

    private List<Component> tooltip(MapMarkers.Marker marker) {
        List<Component> lines = new ArrayList<>();
        lines.add(marker.name());
        lines.addAll(marker.details());
        String where = "X " + (int) Math.floor(marker.x()) + "  Y " + (int) Math.floor(marker.y()) + "  Z " + (int) Math.floor(marker.z());
        if (minecraft.player != null) {
            where += "  ·  " + Math.round(Math.sqrt(minecraft.player.distanceToSqr(marker.x(), marker.y(), marker.z()))) + " blocks away";
        }
        lines.add(Component.literal(where).withColor(Theme.DIM));
        return lines;
    }

    private String dimensionName() {
        if (minecraft.level == null) {
            return "";
        }
        Identifier dim = minecraft.level.dimension().identifier();
        return switch (dim.toString()) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            case "skyrpg_hub:hub" -> "Skyhaven";
            default -> dim.getPath().replace('_', ' ');
        };
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) {
            // A click on one of the popup's rows runs it; anywhere else just closes it.
            Action clicked = popupAction(event.x(), event.y());
            popup = null;
            if (clicked != null) {
                clicked.run().run();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = true;
            pressX = event.x();
            pressY = event.y();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.y() > BAR && event.y() < height - BAR) {
            // Right-click: options for the marker under the mouse, or a new waypoint there.
            MapMarkers.Marker hovered = MapRenderer.hovered(currentView(), markers(), event.x(), event.y(), markerSize());
            if (hovered != null) {
                openPopup(hovered, event.x(), event.y());
            } else {
                double[] world = currentView().toWorld(event.x(), event.y());
                int x = (int) Math.floor(world[0]);
                int z = (int) Math.floor(world[1]);
                // The ground there if it's loaded, else (or over the void) your own height.
                int y = minecraft.player != null ? minecraft.player.getBlockY() : 64;
                if (minecraft.level != null && minecraft.level.hasChunk(x >> 4, z >> 4)) {
                    int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    if (ground > minecraft.level.getMinY()) {
                        y = ground;
                    }
                }
                minecraft.gui.setScreen(new WaypointDialog(this, x, y, z));
            }
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private MapRenderer.View currentView() {
        return new MapRenderer.View(centerX, centerZ, scale, 0, BAR, width, height - BAR * 2, 0);
    }

    // ------------------------------------------------------------------ the marker popup (Track, Share, Remove)

    private record Action(String label, Runnable run) {
    }

    private List<Action> actions(MapMarkers.Marker marker) {
        List<Action> actions = new ArrayList<>();
        String name = marker.name().getString();
        int x = (int) Math.floor(marker.x());
        int y = (int) Math.floor(marker.y());
        int z = (int) Math.floor(marker.z());
        if (Waypoints.isTracked(marker.x(), marker.z())) {
            actions.add(new Action("Stop tracking", Waypoints::untrack));
        } else {
            actions.add(new Action("Track", () -> Waypoints.track(name, marker.x(), marker.y(), marker.z(), marker.color())));
        }
        actions.add(new Action("Share in chat", () -> Waypoints.share(name, x, y, z)));
        if (Waypoints.of(marker) != null) {
            actions.add(new Action("Remove", () -> Waypoints.remove(marker)));
        }
        return actions;
    }

    private void openPopup(MapMarkers.Marker marker, double x, double y) {
        popup = marker;
        popupActions = actions(marker);
        int widest = 0;
        for (Action action : popupActions) {
            widest = Math.max(widest, font.width(action.label()));
        }
        popupW = widest + 16;
        popupX = (int) Math.min(x, width - popupW - 2);
        popupY = (int) Math.min(y, height - BAR - popupActions.size() * POPUP_ROW - 4);
    }

    private Action popupAction(double mx, double my) {
        if (popup == null || mx < popupX || mx >= popupX + popupW || my < popupY + 2) {
            return null;
        }
        int row = (int) ((my - popupY - 2) / POPUP_ROW);
        return row >= 0 && row < popupActions.size() ? popupActions.get(row) : null;
    }

    private void drawPopup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (popup == null) {
            return;
        }
        int h = popupActions.size() * POPUP_ROW + 4;
        Draw.panel(g, popupX, popupY, popupW, h, 0xF0121620, popup.color());
        Action over = popupAction(mouseX, mouseY);
        for (int i = 0; i < popupActions.size(); i++) {
            Action action = popupActions.get(i);
            int y = popupY + 2 + i * POPUP_ROW;
            if (action == over) {
                g.fill(popupX + 2, y, popupX + popupW - 2, y + POPUP_ROW, 0x40FFFFFF);
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            Draw.text(g, font, action.label(), popupX + 8, y + 3, action == over ? Theme.TEXT : Theme.MUTED);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT && popup == null && event.y() > BAR && event.y() < height - BAR
                && Math.abs(event.x() - pressX) < 4 && Math.abs(event.y() - pressY) < 4) {
            // A click, not a drag: on a marker, it opens the popup.
            MapMarkers.Marker hovered = MapRenderer.hovered(currentView(), markers(), event.x(), event.y(), markerSize());
            if (hovered != null) {
                openPopup(hovered, event.x(), event.y());
            }
        }
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            following = false;
            moved = true;
            centerX -= dx / scale;
            centerZ -= dy / scale;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Zoom towards the mouse: the block under it stays under it.
        MapRenderer.View before = new MapRenderer.View(centerX, centerZ, scale, 0, BAR, width, height - BAR * 2, 0);
        double[] anchor = before.toWorld(mouseX, mouseY);
        scale = Mth.clamp(scale * (float) Math.pow(1.25, scrollY), MIN_SCALE, MAX_SCALE);
        if (!following) {
            moved = true;
            MapRenderer.View after = new MapRenderer.View(centerX, centerZ, scale, 0, BAR, width, height - BAR * 2, 0);
            double[] moved = after.toWorld(mouseX, mouseY);
            centerX += anchor[0] - moved[0];
            centerZ += anchor[1] - moved[1];
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SkyRpgUtilsClient.MAP_KEY.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_C) {
            following = !following;
            centre();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (minecraft.level != null) {
            SAVED_SCALE.put(minecraft.level.dimension().identifier(), scale);
        }
        super.removed();
    }
}
