package com.skyrpg.core.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * An info box on the HUD (purse, skill progress, ...). Players move, scale and hide widgets in the HUD
 * editor; mods register them with {@link HudManager#register}.
 */
public interface HudWidget {
    Identifier id();

    /** Shown in the HUD editor. */
    Component name();

    /** Current size in unscaled pixels; may change as content changes. */
    int width(Font font);

    int height(Font font);

    HudAnchor defaultAnchor();

    /** Offset from the anchor point in pixels, positive = right/down. */
    int defaultX();

    int defaultY();

    default boolean enabledByDefault() {
        return true;
    }

    /** Whether there is something to show right now. The editor always draws widgets. */
    default boolean visible() {
        return true;
    }

    /** Draws the widget with its top-left at (0, 0). {@code editing} = the HUD editor is open, so show sample data if needed. */
    void render(GuiGraphicsExtractor g, Font font, boolean editing);
}
