package com.skyrpg.items.client.accessory;

import com.skyrpg.core.client.theme.ThemePack;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.accessory.Accessories;
import com.skyrpg.items.accessory.AccessoryMenuSlot;
import com.skyrpg.items.accessory.AccessorySlot;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The strip on the left of the inventory window that holds the accessory slots, one column next to the
 * armour. It is drawn in code in the window's own style: the theme pack's colours while the theme is on,
 * vanilla's otherwise. Screens draw it just before their window texture, so the window covers its last two
 * columns and the two read as one piece. The slots and their empty icons are drawn by the screen like any
 * other slot; this draws the wells under them.
 * <p>
 * Positions are relative to the window's corner (the screen's {@code leftPos, topPos}), the same in the
 * survival inventory and the creative inventory tab.
 */
public final class AccessoryPanel {
    private static final int PAD = 4;
    private static final int WELL = 18;
    /** The panel's corner: {@link #PAD} around the slot wells, which sit 1px outside the item. */
    private static final int X = AccessoryMenuSlot.X - 1 - PAD;
    private static final int Y = AccessoryMenuSlot.Y - 1 - PAD;
    /** Reaches 2px under the window, so there is no seam between them. */
    private static final int WIDTH = -X + 2;
    private static final int HEIGHT = WELL + AccessoryMenuSlot.SPACING * (AccessorySlot.COUNT - 1) + 2 * PAD;

    /** The colours of the window texture the panel sits against: its outline, fill and bevels, then the slots'. */
    private record Palette(int outline, int fill, int bevelLight, int bevelDark, int corner, int well, int wellShadow, int wellLight) {
    }

    /** The theme pack's inventory colours (tools/theme/generate_theme.py): a flat panel with a light dot in the corner. */
    private static final Palette THEMED = new Palette(0xFF2B3343, 0xFF161B24, 0xFF161B24, 0xFF161B24, 0xFF232A37,
            0xFF0C0F15, 0xFF06080C, 0xFF232A37);
    private static final Palette VANILLA = new Palette(0xFF000000, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555, 0xFFFFFFFF,
            0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF);

    private AccessoryPanel() {
    }

    /** Draws the panel and the slot wells. A well the carried item would go into is lit in the accent colour. */
    public static void draw(GuiGraphicsExtractor g, int leftPos, int topPos, ItemStack carried) {
        Palette p = ThemePack.isActive() ? THEMED : VANILLA;
        int x = leftPos + X;
        int y = topPos + Y;
        int w = WIDTH;
        int h = HEIGHT;

        // Outline with the window's corner steps; nothing on the right, that edge is under the window.
        g.fill(x + 2, y, x + w, y + 1, p.outline());
        g.fill(x + 1, y + 1, x + 2, y + 2, p.outline());
        g.fill(x, y + 2, x + 1, y + h - 3, p.outline());
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, p.outline());
        g.fill(x + 2, y + h - 2, x + 3, y + h - 1, p.outline());
        g.fill(x + 3, y + h - 1, x + w, y + h, p.outline());

        g.fill(x + 1, y + 2, x + w, y + h - 3, p.fill());
        // Bevel: two light rows and columns along the top and left, two dark rows along the bottom.
        g.fill(x + 2, y + 1, x + w, y + 2, p.bevelLight());
        g.fill(x + 1, y + 2, x + w, y + 3, p.bevelLight());
        g.fill(x + 1, y + 3, x + 3, y + h - 3, p.bevelLight());
        g.fill(x + 3, y + 3, x + 4, y + 4, p.corner());
        g.fill(x + 2, y + h - 3, x + 3, y + h - 2, p.fill());
        g.fill(x + 3, y + h - 3, x + w, y + h - 1, p.bevelDark());

        for (AccessorySlot slot : AccessorySlot.values()) {
            well(g, leftPos + AccessoryMenuSlot.X - 1, topPos + slotY(slot) - 1, p, slot.fits(carried));
        }
    }

    /** An 18x18 slot well like the window's: dark top-left edge, light bottom-right edge. */
    private static void well(GuiGraphicsExtractor g, int x, int y, Palette p, boolean lit) {
        g.fill(x, y, x + WELL, y + WELL, p.wellLight());
        g.fill(x, y, x + WELL - 1, y + WELL - 1, p.wellShadow());
        g.fill(x + 1, y + 1, x + WELL - 1, y + WELL - 1, p.well());
        g.fill(x + WELL - 1, y, x + WELL, y + 1, p.well());
        g.fill(x, y + WELL - 1, x + 1, y + WELL, p.well());
        if (lit) {
            g.outline(x, y, WELL, WELL, Theme.ACCENT);
            g.fill(x + 1, y + 1, x + WELL - 1, y + WELL - 1, Theme.ACCENT_SOFT);
        }
    }

    /** Whether the point (screen coordinates) is on the panel. */
    public static boolean contains(double mouseX, double mouseY, int leftPos, int topPos) {
        double x = mouseX - leftPos - X;
        double y = mouseY - topPos - Y;
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    /**
     * Names an empty slot under the mouse and says what goes in it, when nothing is carried. Uses the
     * vanilla tooltip, which the theme pack styles like the item tooltips around it.
     */
    public static void tooltip(GuiGraphicsExtractor g, Font font, Player player, ItemStack carried, int mouseX, int mouseY, int leftPos, int topPos) {
        AccessorySlot slot = slotAt(mouseX, mouseY, leftPos, topPos);
        if (slot == null || !carried.isEmpty() || !Accessories.get(player, slot).isEmpty()) {
            return;
        }
        List<Component> lines = List.of(
                Component.literal(slot.displayName()).withColor(Theme.ACCENT),
                Component.literal(slot.hint()).withStyle(ChatFormatting.GRAY));
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** The slot whose well is under the mouse, the same area the screen hovers a slot by. */
    private static AccessorySlot slotAt(double mouseX, double mouseY, int leftPos, int topPos) {
        double x = mouseX - leftPos - (AccessoryMenuSlot.X - 1);
        if (x < 0 || x >= WELL) {
            return null;
        }
        for (AccessorySlot slot : AccessorySlot.values()) {
            double y = mouseY - topPos - (slotY(slot) - 1);
            if (y >= 0 && y < WELL) {
                return slot;
            }
        }
        return null;
    }

    private static int slotY(AccessorySlot slot) {
        return AccessoryMenuSlot.Y + AccessoryMenuSlot.SPACING * slot.ordinal();
    }
}
