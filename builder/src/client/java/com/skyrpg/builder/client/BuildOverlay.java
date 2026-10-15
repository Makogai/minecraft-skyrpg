package com.skyrpg.builder.client;

import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The build in progress, for everyone nearby: a card in the bottom-right corner with a big 3D model of the build
 * (what's built in colour, the rest as hologram), its name, a progress bar, the layer, blocks placed and about how
 * long is left. It stays a few seconds after the build finishes. On narrow screens it sits above the bars instead.
 */
final class BuildOverlay {
    private record Seen(BuilderPayloads.Progress progress, long at) {
    }

    private static final Map<BlockPos, Seen> SEEN = new HashMap<>();
    /** The last picture shown per station, kept while the next layer's one is drawn. */
    private static final Map<BlockPos, Previews.Preview> SHOWN = new HashMap<>();
    private static final int W = 214;
    private static final int PICTURE_H = 112;
    private static final int H = PICTURE_H + 58;

    private BuildOverlay() {
    }

    static void update(BuilderPayloads.Progress progress) {
        SEEN.put(progress.station(), new Seen(progress, System.currentTimeMillis()));
    }

    static void clear() {
        SEEN.clear();
        SHOWN.clear();
    }

    /** Draws the card; {@code centreBottom} is where it ends if it has to sit in the middle. */
    static void render(GuiGraphicsExtractor g, int centreBottom) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || SEEN.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Seen best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Seen seen : SEEN.values()) {
            boolean finished = seen.progress().done() >= seen.progress().total();
            if (now - seen.at() > (finished ? 5000 : 2000)) {
                continue;
            }
            double distance = minecraft.player.blockPosition().distSqr(seen.progress().station());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = seen;
            }
        }
        if (best == null) {
            return;
        }
        BuilderPayloads.Progress progress = best.progress();
        boolean finished = progress.done() >= progress.total();
        Font font = minecraft.font;
        int screenW = g.guiWidth();
        int x = screenW - W - 6;
        int y = g.guiHeight() - H - 6;
        if (x < screenW / 2 + 91 + 4) {
            // Too narrow for the corner (it would cover the hotbar): above the bars in the middle.
            x = (screenW - W) / 2;
            y = centreBottom - H;
        }
        Draw.shadow(g, x, y, W, H, 4, 0.5f);
        Draw.panel(g, x, y, W, H, 0xE6121620, finished ? 0xFF2E6B48 : 0xFF1F4A55);
        // The 3D picture on blueprint paper.
        int px = x + 4;
        int py = y + 4;
        int pw = W - 8;
        Draw.rounded(g, px, py, pw, PICTURE_H, 0xFF0B1220);
        for (int gx = px + 8; gx < px + pw - 1; gx += 8) {
            g.fill(gx, py + 1, gx + 1, py + PICTURE_H - 1, 0x0D5CA8FF);
        }
        for (int gy = py + 8; gy < py + PICTURE_H - 1; gy += 8) {
            g.fill(px + 1, gy, px + pw - 1, gy + 1, 0x0D5CA8FF);
        }
        String id = progress.blueprintId();
        Previews.Preview wanted = Previews.get("build:" + id, () -> Ghosts.BLUEPRINTS.get(id), false,
                Previews.Spec.overlay(finished ? -1 : progress.layer()));
        Previews.Preview shown = Previews.ready(wanted, SHOWN.get(progress.station()));
        if (shown != null && shown.state == Previews.State.READY) {
            SHOWN.put(progress.station(), shown);
            Previews.draw(g, shown, px + 2, py + 2, pw - 4, PICTURE_H - 4);
        }
        // The words under it.
        int tx = x + 8;
        int tw = W - 16;
        int ty = py + PICTURE_H + 5;
        String label = finished ? "FINISHED!" : "BUILDING";
        Draw.text(g, font, label, tx, ty, finished ? Theme.GREEN : BuilderScreen.CYAN);
        String time = finished ? "" : progress.note().isEmpty() ? timeLeft(progress.secondsLeft()) : "waiting";
        if (!time.isEmpty()) {
            Draw.fitRight(g, font, time, x + W - 8, ty, tw - font.width(label) - 8, progress.note().isEmpty() ? Theme.MUTED : Theme.ACCENT);
        }
        Draw.fit(g, font, Component.literal(progress.name()), tx, ty + 11, tw, Draw.Align.LEFT, Theme.TEXT);
        float fraction = progress.total() <= 0 ? 1 : Math.min(1f, progress.done() / (float) progress.total());
        Draw.bar(g, tx, ty + 23, tw, 7, fraction, finished ? Theme.GREEN : BuilderScreen.CYAN);
        if (!progress.note().isEmpty() && !finished) {
            Draw.fit(g, font, Component.literal(progress.note()), tx, ty + 34, tw, Draw.Align.LEFT, Theme.ACCENT);
        } else {
            String left = finished ? "All " + progress.layers() + " layers" : "Layer " + Math.min(progress.layers(), progress.layer() + 1) + " of "
                    + progress.layers() + "  ·  " + (int) (fraction * 100) + "%";
            String right = String.format("%,d", progress.done()) + " / " + String.format("%,d", progress.total());
            Draw.fit(g, font, Component.literal(left), tx, ty + 34, tw - font.width(right) - 6, Draw.Align.LEFT, Theme.MUTED);
            Draw.rightText(g, font, right, x + W - 8, ty + 34, Theme.DIM);
        }
    }

    /** "about 42 s left", "about 1 min 20 s left". */
    private static String timeLeft(int seconds) {
        if (seconds <= 0) {
            return "almost done";
        }
        if (seconds < 60) {
            return "about " + seconds + " s left";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return "about " + minutes + " min" + (rest > 0 ? " " + rest + " s" : "") + " left";
    }
}
