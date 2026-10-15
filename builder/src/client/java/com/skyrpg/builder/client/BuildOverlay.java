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
 * The build in progress, for everyone nearby: a small panel at the top of the screen with a 3D picture of the build
 * (what's built in colour, the rest as hologram), its name, a progress bar and the layer. It stays a few seconds
 * after the build finishes.
 */
final class BuildOverlay {
    private record Seen(BuilderPayloads.Progress progress, long at) {
    }

    private static final Map<BlockPos, Seen> SEEN = new HashMap<>();
    /** The last picture shown per station, kept while the next layer's one is drawn. */
    private static final Map<BlockPos, Previews.Preview> SHOWN = new HashMap<>();

    private BuildOverlay() {
    }

    static void update(BuilderPayloads.Progress progress) {
        SEEN.put(progress.station(), new Seen(progress, System.currentTimeMillis()));
    }

    static void clear() {
        SEEN.clear();
        SHOWN.clear();
    }

    /** Draws the panel ending at {@code bottom}; returns its height (0 when there's no build nearby). */
    static int render(GuiGraphicsExtractor g, int bottom) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || SEEN.isEmpty()) {
            return 0;
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
            return 0;
        }
        BuilderPayloads.Progress progress = best.progress();
        boolean finished = progress.done() >= progress.total();
        Font font = minecraft.font;
        int w = 220;
        int h = 62;
        int x = (g.guiWidth() - w) / 2;
        int y = bottom - h;
        Draw.shadow(g, x, y, w, h, 4, 0.5f);
        Draw.panel(g, x, y, w, h, 0xE0121620, finished ? 0xFF2E6B48 : 0xFF1F4A55);
        // The 3D picture.
        int px = x + 4;
        int py = y + 4;
        int pw = 70;
        int ph = h - 8;
        Draw.rounded(g, px, py, pw, ph, 0xFF0B1220);
        String id = progress.blueprintId();
        Previews.Preview wanted = Previews.get("build:" + id, () -> Ghosts.BLUEPRINTS.get(id), false,
                Previews.Spec.overlay(finished ? -1 : progress.layer()));
        Previews.Preview shown = Previews.ready(wanted, SHOWN.get(progress.station()));
        if (shown != null && shown.state == Previews.State.READY) {
            SHOWN.put(progress.station(), shown);
            Previews.draw(g, shown, px + 1, py + 1, pw - 2, ph - 2);
        }
        int tx = px + pw + 8;
        int tw = x + w - 8 - tx;
        Draw.fit(g, font, Component.literal(finished ? "FINISHED!" : "BUILDING"), tx, y + 6, tw, Draw.Align.LEFT, finished ? Theme.GREEN : BuilderScreen.CYAN);
        Draw.fit(g, font, Component.literal(progress.name()), tx, y + 17, tw, Draw.Align.LEFT, Theme.TEXT);
        float fraction = progress.total() <= 0 ? 1 : Math.min(1f, progress.done() / (float) progress.total());
        Draw.bar(g, tx, y + 30, tw, 6, fraction, finished ? Theme.GREEN : BuilderScreen.CYAN);
        String line;
        int color = Theme.MUTED;
        if (!progress.note().isEmpty() && !finished) {
            line = progress.note();
            color = Theme.ACCENT;
        } else if (finished) {
            line = String.format("%,d", progress.total()) + " blocks";
        } else {
            line = "Layer " + Math.min(progress.layers(), progress.layer() + 1) + " of " + progress.layers() + "  ·  " + (int) (fraction * 100) + "%";
        }
        Draw.fit(g, font, Component.literal(line), tx, y + 41, tw, Draw.Align.LEFT, color);
        return h + 4;
    }
}
