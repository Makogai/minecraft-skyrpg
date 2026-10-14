package com.skyrpg.utils.client.map;

import com.skyrpg.utils.client.UtilsSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shapes and edge arrows for waypoints: the diamond, the arrow, and the arrows on the minimap's rim. */
public final class WaypointDraw {
    private static final int OUTLINE = 0xFF101318;

    private WaypointDraw() {
    }

    /** A diamond centred on (cx, cy), about {@code size} px across, with a dark outline. */
    public static void diamond(GuiGraphicsExtractor g, float cx, float cy, int size, int color) {
        int half = Math.max(2, size / 2);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) (Math.PI / 4));
        int inner = Math.round(half * 0.72f);
        g.fill(-inner - 1, -inner - 1, inner + 1, inner + 1, OUTLINE);
        g.fill(-inner, -inner, inner, inner, color);
        g.pose().popMatrix();
    }

    /** An arrow centred on (cx, cy) pointing {@code angle} radians clockwise from "right", with a dark outline. */
    public static void arrow(GuiGraphicsExtractor g, float cx, float cy, float angle, int size, int color) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(angle);
        triangle(g, size + 2, OUTLINE);
        triangle(g, size, color);
        g.pose().popMatrix();
    }

    /** Points right: a tall base on the left narrowing to the tip. */
    private static void triangle(GuiGraphicsExtractor g, int size, int color) {
        for (int i = 0; i < size; i++) {
            int half = (int) Math.ceil((size - i) * 0.5);
            g.fill(-size / 2 + i, -half, -size / 2 + i + 1, half, color);
        }
    }

    /**
     * Arrows on the rim of the minimap for everything it points at that's off the map: your waypoints (within the
     * distance setting) and the tracked target, which is drawn bigger.
     */
    static void edgeArrows(GuiGraphicsExtractor g, MapRenderer.View view) {
        double max = UtilsSettings.wpMaxDistance();
        double px = view.centerX();
        double pz = view.centerZ();
        List<double[]> points = new ArrayList<>();
        for (Waypoints.Waypoint waypoint : Waypoints.list()) {
            if (!Waypoints.isTracked(waypoint.x() + 0.5, waypoint.z() + 0.5)) {
                points.add(new double[] {waypoint.x() + 0.5, waypoint.z() + 0.5, waypoint.color(), 0});
            }
        }
        Waypoints.Target target = Waypoints.tracked();
        if (target != null) {
            points.add(new double[] {target.x(), target.z(), target.color(), 1});
        }
        float half = Math.min(view.width(), view.height()) / 2f - 5;
        for (double[] point : points) {
            boolean tracked = point[3] > 0;
            if (!tracked && Math.hypot(point[0] - px, point[1] - pz) > max) {
                continue;
            }
            float[] at = view.toScreen(point[0], point[1]);
            if (view.contains(at[0], at[1], 4)) {
                continue;
            }
            float dx = at[0] - view.midX();
            float dy = at[1] - view.midY();
            float t = half / Math.max(Math.abs(dx), Math.abs(dy));
            g.pose().pushMatrix();
            arrow(g, view.midX() + dx * t, view.midY() + dy * t, (float) Math.atan2(dy, dx), tracked ? 9 : 7, (int) point[2]);
            g.pose().popMatrix();
        }
    }
}
