package com.skyrpg.core.client.hud;

/**
 * Which point of the screen a HUD widget is pinned to. The same point of the widget sits there (plus
 * its offset), so a widget pinned top-right stays in the corner at any window size.
 */
public enum HudAnchor {
    TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
    MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
    BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

    final float fx;
    final float fy;

    HudAnchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** The anchor for a widget whose centre is at (x, y): whichever third of the screen it's in. */
    static HudAnchor nearest(double x, double y, int screenWidth, int screenHeight) {
        int column = x < screenWidth / 3.0 ? 0 : x < screenWidth * 2 / 3.0 ? 1 : 2;
        int row = y < screenHeight / 3.0 ? 0 : y < screenHeight * 2 / 3.0 ? 1 : 2;
        return values()[row * 3 + column];
    }
}
