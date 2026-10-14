package com.skyrpg.utils.client.map;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Waypoints floating in the world, drawn on the HUD: the camera's view is worked out by hand (no 3D drawing), so a
 * diamond in the waypoint's colour sits where it is on screen with "Name · 142m" under it. It fades when you're
 * close, and off-screen it turns into an arrow on the screen edge pointing the way to turn. The tracked target is
 * always shown, with a ring round it; your other waypoints follow the "show in world" and distance settings.
 */
public final class WaypointHud {
    private static final int EDGE = 14;

    private record Point(String name, double x, double y, double z, int color, boolean tracked) {
    }

    private WaypointHud() {
    }

    static void init() {
        HudElementRegistry.addLast(SkyRpgUtilsClient.id("waypoints"), WaypointHud::render);
    }

    private static List<Point> points() {
        List<Point> points = new ArrayList<>();
        Waypoints.Target target = Waypoints.tracked();
        if (UtilsSettings.wpWorld().get()) {
            for (Waypoints.Waypoint waypoint : Waypoints.list()) {
                double x = waypoint.x() + 0.5;
                double z = waypoint.z() + 0.5;
                if (!Waypoints.isTracked(x, z)) {
                    points.add(new Point(waypoint.name(), x, waypoint.y() + 1.5, z, waypoint.color(), false));
                }
            }
        }
        if (target != null) {
            points.add(new Point(target.name(), target.x(), target.y() + 1.5, target.z(), target.color(), true));
        }
        return points;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !MapCache.active()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        List<Point> points = points();
        if (points.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector();
        Vector3f up = camera.rotation().transform(new Vector3f(0, 1, 0));
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        double tanY = Math.tan(Math.toRadians(camera.getFov()) / 2);
        double tanX = tanY * w / (double) h;
        double max = UtilsSettings.wpMaxDistance();
        int size = UtilsSettings.wpSize();

        for (Point point : points) {
            double dx = point.x() - eye.x;
            double dy = point.y() - eye.y;
            double dz = point.z() - eye.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!point.tracked() && distance > max) {
                continue;
            }
            double cz = dx * forward.x() + dy * forward.y() + dz * forward.z();
            double cx = dx * right.x + dy * right.y + dz * right.z;
            double cy = dx * up.x + dy * up.y + dz * up.z;
            float fade = Mth.clamp((float) (distance - 2) / 14f, 0.15f, 1f);
            int color = Draw.withAlpha(point.color(), fade);

            if (cz > 0.1) {
                double nx = cx / cz / tanX;
                double ny = cy / cz / tanY;
                float sx = (float) (w / 2.0 * (1 + nx));
                float sy = (float) (h / 2.0 * (1 - ny));
                if (sx > EDGE && sx < w - EDGE && sy > EDGE && sy < h - EDGE) {
                    marker(g, font, point, sx, sy, size, color, fade, distance);
                    continue;
                }
            }
            // Off screen: an arrow on the edge, pointing where to turn.
            double ax = Math.abs(cx) + Math.abs(cy) < 1e-6 ? 1 : cx;
            double ay = -cy;
            double reach = Math.min((w / 2.0 - EDGE) / Math.max(Math.abs(ax), 1e-6), (h / 2.0 - EDGE) / Math.max(Math.abs(ay), 1e-6));
            float px = (float) (w / 2.0 + ax * reach);
            float py = (float) (h / 2.0 + ay * reach);
            WaypointDraw.arrow(g, px, py, (float) Math.atan2(ay, ax), Math.max(8, size), color);
            String text = distance(distance);
            int tx = Mth.clamp(Math.round(px) - font.width(text) / 2, 2, w - font.width(text) - 2);
            int ty = Mth.clamp(Math.round(py) + (py < h / 2f ? 8 : -16), 2, h - 12);
            g.text(font, text, tx, ty, Draw.withAlpha(Theme.TEXT, fade), true);
        }
    }

    private static void marker(GuiGraphicsExtractor g, Font font, Point point, float sx, float sy, int size, int color, float fade, double distance) {
        if (point.tracked()) {
            WaypointDraw.diamond(g, sx, sy, size + 6, Draw.withAlpha(0xFFFFFFFF, fade * 0.8f));
        }
        WaypointDraw.diamond(g, sx, sy, size, color);
        String text = point.name() + " · " + distance(distance);
        int width = font.width(text);
        int x = Math.round(sx) - width / 2;
        int y = Math.round(sy) + size / 2 + 4;
        g.fill(x - 2, y - 2, x + width + 2, y + 10, Draw.withAlpha(0xFF101318, fade * 0.7f));
        g.text(font, text, x, y, Draw.withAlpha(Theme.TEXT, fade), false);
    }

    /** "142m", or "1.4km" from a thousand. */
    static String distance(double blocks) {
        return blocks >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fkm", blocks / 1000) : Math.round(blocks) + "m";
    }
}
