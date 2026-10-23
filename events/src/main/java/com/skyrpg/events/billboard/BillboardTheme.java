package com.skyrpg.events.billboard;

import java.util.List;

/**
 * A billboard's look: which vanilla block textures make its frame, back, truss and posts, and the glowing trim. Shared by
 * the server (to check a chosen index) and the client (to draw it).
 */
public record BillboardTheme(String name, String frame, String back, String truss, String posts, String trim) {
    public static final List<BillboardTheme> ALL = List.of(
            new BillboardTheme("Blackstone", "polished_blackstone_bricks", "dark_oak_planks", "stripped_dark_oak_log", "polished_blackstone", "gold_block"),
            new BillboardTheme("Dark Oak", "stripped_dark_oak_log", "dark_oak_planks", "dark_oak_log", "stripped_dark_oak_log", "gold_block"),
            new BillboardTheme("Quartz", "quartz_block_side", "birch_planks", "stripped_birch_log", "smooth_stone", "gold_block"),
            new BillboardTheme("Copper", "cut_copper", "spruce_planks", "stripped_spruce_log", "copper_block", "copper_block"),
            new BillboardTheme("Iron", "iron_block", "smooth_stone", "polished_deepslate", "polished_deepslate", "iron_block"));

    /** Screen widths in blocks the size picker offers; the height is always 9/16 of it. */
    public static final List<Integer> WIDTHS = List.of(3, 4, 6, 8, 12, 16);

    public static BillboardTheme get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    /** The frame thickness for a screen this wide. */
    public static float frame(float width) {
        return Math.max(0.25f, Math.min(0.7f, width * 0.05f));
    }

    /** How deep the cabinet is for a screen this wide. */
    public static float depth(float width) {
        return Math.max(0.375f, Math.min(0.625f, 0.25f + width * 0.025f));
    }

    /** Free-standing billboards stand this high on their posts. */
    public static final float POST_HEIGHT = 2f;
}
