package com.skyrpg.utils.client.map;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A compass ribbon for the tracked target, top centre: the way you face runs along a strip, with N/E/S/W and a
 * diamond in the target's colour where it is; when it's more than 90 degrees to one side the diamond turns into an
 * arrow at that end of the strip. Under it: the name, the distance and whether it's above or below you.
 */
public final class WaypointRibbon implements HudWidget {
    private static final int W = 184;
    private static final int H = 30;
    private static final int HALF = 88;

    @Override
    public Identifier id() {
        return SkyRpgUtilsClient.id("waypoint_ribbon");
    }

    @Override
    public Component name() {
        return Component.literal("Waypoint compass");
    }

    @Override
    public int width(Font font) {
        return W;
    }

    @Override
    public int height(Font font) {
        return H;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_CENTER;
    }

    @Override
    public int defaultX() {
        return 0;
    }

    @Override
    public int defaultY() {
        return 36;
    }

    @Override
    public boolean visible() {
        return Waypoints.tracked() != null && MapCache.active();
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Minecraft minecraft = Minecraft.getInstance();
        Waypoints.Target target = Waypoints.tracked();
        if (minecraft.player == null || target == null && !editing) {
            return;
        }
        double dx = target == null ? 0 : target.x() - minecraft.player.getX();
        double dy = target == null ? 0 : target.y() - minecraft.player.getY();
        double dz = target == null ? 120 : target.z() - minecraft.player.getZ();
        int color = target == null ? Waypoints.GOLD : target.color();
        String label = target == null ? "Crypt" : target.name();

        float yaw = minecraft.player.getYRot();
        // Minecraft's yaw: 0 faces south (+z), 90 west (-x).
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(bearing - yaw);

        Draw.panel(g, 0, 0, W, H, 0xE0121620, Theme.BORDER);
        int mid = W / 2;
        // Ticks every 15 degrees, letters at the four directions.
        for (int deg = -180; deg < 180; deg += 15) {
            float rel = Mth.wrapDegrees(deg - yaw);
            if (Math.abs(rel) < HALF - 2) {
                int x = mid + Math.round(rel);
                g.fill(x, 11, x + 1, 14, Theme.DIM);
            }
        }
        String[] letters = {"S", "W", "N", "E"};
        for (int i = 0; i < 4; i++) {
            float rel = Mth.wrapDegrees(i * 90 - yaw);
            if (Math.abs(rel) < HALF - 6) {
                int x = mid + Math.round(rel);
                Draw.text(g, font, letters[i], x - font.width(letters[i]) / 2, 3, i == 2 ? Theme.ACCENT : Theme.MUTED);
            }
        }
        g.fill(mid, 2, mid + 1, 16, 0x60FFFFFF);

        if (Math.abs(diff) <= HALF - 5) {
            WaypointDraw.diamond(g, mid + diff, 9, 11, color);
        } else {
            WaypointDraw.arrow(g, mid + Math.signum(diff) * (HALF - 3), 9, diff > 0 ? 0 : (float) Math.PI, 8, color);
        }

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String height = Math.abs(dy) < 6 ? "" : dy > 0 ? "  " + Math.round(dy) + " above" : "  " + Math.round(-dy) + " below";
        Draw.fitText(g, font, label + " · " + WaypointHud.distance(distance) + height, 6, 19, W - 12, Theme.TEXT);
    }
}
