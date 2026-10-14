package com.skyrpg.core.client.menu;

import com.skyrpg.core.client.ui.Draw;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * What a {@link MenuPage} gets each frame: where to draw, the mouse, and how long the page has been
 * open (for intro animations).
 */
public record PageContext(GuiGraphicsExtractor graphics, Font font, LocalPlayer player,
                          int x, int y, int width, int height, int mouseX, int mouseY, long openedAtMillis) {

    /** Seconds since the page was shown. */
    public float age() {
        return (Util.getMillis() - openedAtMillis) / 1000f;
    }

    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** A tooltip by the mouse this frame, drawn by the menu on top of everything. */
    public void tooltip(List<Component> lines, int accent) {
        Draw.queueTooltip(lines, accent);
    }
}
