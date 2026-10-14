package com.skyrpg.core.client.menu;


/**
 * Where the SkyRPG menu window goes and how big it's drawn, worked out from the window's real pixel size.
 *
 * <p>The menu lays itself out in its own units and is drawn {@link #scale()} times the GUI's size, chosen so that
 * one unit is a whole number of real pixels ({@link #pixelScale()}): text and icons stay as crisp as at any GUI
 * scale, but the window keeps about the same share of the screen whatever the GUI scale is. "Auto" aims for a
 * screen about 600 units tall (2x up to 1440p, 4x at 4K), so text stays near normal GUI size and a bigger screen gets
 * a roomier window (up to 900x520 units, about 78% of the screen) rather than bigger letters. Players found the old
 * 360-unit target (4x at 1440p) far too big in fullscreen. "Large" draws bigger (about 420 units tall), "Compact"
 * smaller (about 720) with a smaller window. Auto and Large never draw smaller than the player's own GUI scale,
 * unless the window wouldn't fit.
 *
 * @param scale       GUI pixels per menu unit (the pose scale)
 * @param pixelScale  real pixels per menu unit
 * @param screenWidth the whole screen in menu units
 */
record MenuLayout(float scale, int pixelScale, int screenWidth, int screenHeight, int left, int top, int width, int height,
                  Size size) {
    /** The smallest window the pages are designed for. */
    static final int MIN_WIDTH = 400;
    static final int MIN_HEIGHT = 240;
    /** On a small screen the window may squeeze down to this to stay at a bigger scale (720p at GUI scale 3). */
    private static final int FIT_WIDTH = 380;
    private static final int FIT_HEIGHT = 220;
    /** Rather than drop to 1x (tiny text), a very small window squeezes the menu down to this at 2x. */
    private static final int TINY_WIDTH = 300;
    private static final int TINY_HEIGHT = 200;
    /** Space kept around the window. */
    static final int MARGIN = 8;
    /** The window is at most this wide for its height (ultrawide screens), and at most this tall for its width. */
    private static final float MAX_ASPECT = 1.9f;
    private static final float MAX_TALL = 0.75f;

    enum Size {
        AUTO("Auto", 600, 0.78f, 0.80f, 900, 520, true),
        COMPACT("Compact", 720, 0.72f, 0.76f, 640, 370, false),
        LARGE("Large", 420, 0.88f, 0.88f, 820, 480, true);

        final String label;
        /** The screen's height in menu units this size aims for. */
        final int targetHeight;
        final float widthShare;
        final float heightShare;
        final int maxWidth;
        final int maxHeight;
        /** Never draw smaller than the GUI scale (if the window fits). */
        final boolean atLeastGuiScale;

        Size(String label, int targetHeight, float widthShare, float heightShare, int maxWidth, int maxHeight, boolean atLeastGuiScale) {
            this.label = label;
            this.targetHeight = targetHeight;
            this.widthShare = widthShare;
            this.heightShare = heightShare;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.atLeastGuiScale = atLeastGuiScale;
        }

        static Size of(String label) {
            for (Size size : values()) {
                if (size.label.equals(label)) {
                    return size;
                }
            }
            return AUTO;
        }
    }

    /**
     * @param pixelWidth  the window's framebuffer width in real pixels
     * @param pixelHeight its height
     * @param guiScale    the GUI scale in use (real pixels per GUI pixel)
     */
    static MenuLayout compute(int pixelWidth, int pixelHeight, int guiScale, Size size) {
        int gui = Math.max(1, guiScale);
        // The biggest whole scale at which the smallest window still fits on screen.
        int fits = Math.max(1, Math.min(pixelWidth / (FIT_WIDTH + 2 * MARGIN), pixelHeight / (FIT_HEIGHT + 2 * MARGIN)));
        if (fits < 2 && pixelWidth / 2 >= TINY_WIDTH + 2 * MARGIN && pixelHeight / 2 >= TINY_HEIGHT + 2 * MARGIN) {
            fits = 2;
        }
        // 1x text is very small, so at least 2x whenever that fits.
        int k = Math.max(2, Math.round(pixelHeight / (float) size.targetHeight));
        if (size.atLeastGuiScale) {
            k = Math.max(k, gui);
        }
        k = Math.clamp(k, 1, fits);

        int screenWidth = pixelWidth / k;
        int screenHeight = pixelHeight / k;
        int width = Math.clamp(Math.round(screenWidth * size.widthShare), MIN_WIDTH, size.maxWidth);
        int height = Math.clamp(Math.round(screenHeight * size.heightShare), MIN_HEIGHT, size.maxHeight);
        width = Math.max(MIN_WIDTH, Math.min(width, Math.round(height * MAX_ASPECT)));
        height = Math.max(MIN_HEIGHT, Math.min(height, Math.round(width * MAX_TALL)));
        // Always fully on screen, even on a window too small for the minimum.
        width = Math.min(width, screenWidth - 2 * MARGIN);
        height = Math.min(height, screenHeight - 2 * MARGIN);
        int left = (screenWidth - width) / 2;
        int top = (screenHeight - height) / 2;
        return new MenuLayout(k / (float) gui, k, screenWidth, screenHeight, left, top, width, height, size);
    }

    int right() {
        return left + width;
    }

    int bottom() {
        return top + height;
    }
}
