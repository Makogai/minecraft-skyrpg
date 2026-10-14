package com.skyrpg.core.client.menu;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One page of the SkyRPG menu (Profile, Skills, Stats, ...). Each mod registers its own pages with
 * {@link SkyRpgMenu#registerPage}, and the menu draws the window, sidebar, title and footer around them.
 *
 * <p>Pages draw in the menu's own units: the menu scales the whole window to the screen (see
 * {@link PageContext}), so a page just uses the area and mouse position it's given. Mouse coordinates passed to the
 * input methods are in the same units. Text, fills, items, textures and scissors all follow the scaling; a live entity
 * preview doesn't, so draw one with {@link com.skyrpg.core.client.ui.Draw#entityFollowsMouse}.
 */
public interface MenuPage {
    Identifier id();

    Component title();

    /** One line under the title. */
    default Component subtitle() {
        return Component.empty();
    }

    /** Shown in the sidebar. */
    ItemStack icon();

    /** Lower comes first in the sidebar. */
    default int order() {
        return 100;
    }

    /** Draws the page inside {@link PageContext}'s content area. */
    void render(PageContext ctx);

    /** Called each time the page is shown: when the menu opens on it, or it's picked in the sidebar. */
    default void opened() {
    }

    /**
     * Opens a part of the page by name, for links and dev tools, e.g. "hud" on the Settings page opens the HUD
     * category ({@code SkyRpgMenu.open("settings-hud")}). Return false if the name means nothing to this page.
     */
    default boolean showSubpage(String name) {
        return false;
    }

    /** {@code button} is 26.3's numbering: InputConstants.MOUSE_BUTTON_LEFT (1), _RIGHT (3)... */
    default boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        return false;
    }

    /** The mouse moved with a button held, after a click on this page (for sliders and scrollbars). */
    default boolean mouseDragged(PageContext ctx, double x, double y, int button) {
        return false;
    }

    default boolean mouseReleased(PageContext ctx, double x, double y, int button) {
        return false;
    }

    default boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        return false;
    }

    /**
     * Keys reach the page before the menu uses them (1-9 and the arrows switch pages, the menu key
     * closes it), so a page with a text field can take them. Return true to use the key. Esc never comes here:
     * it goes to {@link #back} instead.
     */
    default boolean keyPressed(PageContext ctx, KeyEvent event) {
        return false;
    }

    /** Typed characters, for pages with a text field. */
    default boolean charTyped(PageContext ctx, CharacterEvent event) {
        return false;
    }

    /**
     * Esc: step back inside the page (clear a search, leave a sub-view) and return true, or return false to let
     * the menu close.
     */
    default boolean back(PageContext ctx) {
        return false;
    }

    /** True while {@link #back} would do something, so the footer says "Esc back" rather than "Esc close". */
    default boolean canGoBack() {
        return false;
    }

    /** Keyboard hints for the footer's left side, e.g. "Tab switch pane". Empty for none. */
    default Component hint() {
        return Component.empty();
    }
}
