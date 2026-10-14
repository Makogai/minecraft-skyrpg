package com.skyrpg.core.client.ui;

/** Colours (ARGB) shared by every SkyRPG screen, so pages from different mods look like one app. */
public final class Theme {
    public static final int OVERLAY = 0x99070910;
    public static final int WINDOW = 0xF0121620;
    public static final int SIDEBAR = 0xF00C0F16;
    public static final int BORDER = 0xFF252C39;
    public static final int CARD = 0xFF171C26;
    public static final int CARD_HOVER = 0xFF1D2330;
    public static final int CARD_BORDER = 0xFF29303E;
    public static final int TRACK = 0xFF0A0D12;
    public static final int HOVER = 0x12FFFFFF;
    /** The menu's footer strip, a shade darker than the window. */
    public static final int FOOTER = 0x50070A10;
    /** Key caps in key hints ("[Esc] close"). */
    public static final int KEY = 0xFF1A202B;
    /** Text fields (search boxes). */
    public static final int INPUT = 0xFF0C1017;

    public static final int TEXT = 0xFFE9EDF4;
    public static final int MUTED = 0xFF8D96A8;
    public static final int DIM = 0xFF586174;

    /** SkyRPG gold: the brand accent (selection, coins, highlights). */
    public static final int ACCENT = 0xFFF2B33D;
    public static final int ACCENT_SOFT = 0x26F2B33D;

    public static final int RED = 0xFFFF6464;
    public static final int GREEN = 0xFF5CD68A;
    public static final int BLUE = 0xFF5CA8FF;
    public static final int AQUA = 0xFF5CE1E6;
    public static final int PURPLE = 0xFFB98CFF;

    private Theme() {
    }
}
