package com.skyrpg.items.client.backpack;

import com.skyrpg.core.client.theme.ThemePack;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.backpack.Backpacks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The strip on the right of the inventory window that holds the three backpack slots, the mirror image of
 * the accessory panel on the left: drawn in the window's own style (theme or vanilla), its left edge tucked
 * under the window. {@code slotX} is where the slots' items sit in window coordinates: right of the 176px
 * survival window, or of the 195px creative one.
 */
public final class BackpackPanel {
    private static final int PAD = 4;
    private static final int WELL = 18;
    private static final int HEIGHT = WELL + BackpackMenuSlot.SPACING * (Backpacks.SLOTS - 1) + 2 * PAD;
    /** Where the creative inventory tab puts the slots: right of its wider window. */
    public static final int CREATIVE_X = 200;

    private record Palette(int outline, int fill, int bevelLight, int bevelDark, int well, int wellShadow, int wellLight) {
    }

    private static final Palette THEMED = new Palette(0xFF2B3343, 0xFF161B24, 0xFF161B24, 0xFF161B24, 0xFF0C0F15, 0xFF06080C, 0xFF232A37);
    private static final Palette VANILLA = new Palette(0xFF000000, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF);

    private BackpackPanel() {
    }

    public static void draw(GuiGraphicsExtractor g, int leftPos, int topPos, int slotX, ItemStack carried) {
        Palette p = ThemePack.isActive() ? THEMED : VANILLA;
        int right = leftPos + slotX - 1 + WELL + PAD;
        int x = leftPos + slotX - 1 - PAD - 2;  // 2px under the window
        int y = topPos + BackpackMenuSlot.Y - 1 - PAD;
        int w = right - x;
        int h = HEIGHT;

        // Outline with the window's corner steps on the right; the left edge is under the window.
        g.fill(x, y, x + w - 2, y + 1, p.outline());
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, p.outline());
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, p.outline());
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, p.outline());
        g.fill(x, y + h - 1, x + w - 2, y + h, p.outline());
        g.fill(x, y + 1, x + w - 2, y + h - 1, p.fill());
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, p.fill());
        g.fill(x, y + 1, x + w - 2, y + 3, p.bevelLight());
        g.fill(x, y + h - 3, x + w - 2, y + h - 1, p.bevelDark());
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 3, p.bevelDark());

        boolean fits = Backpacks.isBackpack(carried);
        for (int i = 0; i < Backpacks.SLOTS; i++) {
            well(g, leftPos + slotX - 1, topPos + slotY(i) - 1, p, fits);
        }
    }

    private static void well(GuiGraphicsExtractor g, int x, int y, Palette p, boolean lit) {
        g.fill(x, y, x + WELL, y + WELL, p.wellLight());
        g.fill(x, y, x + WELL - 1, y + WELL - 1, p.wellShadow());
        g.fill(x + 1, y + 1, x + WELL - 1, y + WELL - 1, p.well());
        if (lit) {
            g.outline(x, y, WELL, WELL, Theme.ACCENT);
            g.fill(x + 1, y + 1, x + WELL - 1, y + WELL - 1, Theme.ACCENT_SOFT);
        }
    }

    public static boolean contains(double mouseX, double mouseY, int leftPos, int topPos, int slotX) {
        double x = mouseX - (leftPos + slotX - 1 - PAD - 2);
        double y = mouseY - (topPos + BackpackMenuSlot.Y - 1 - PAD);
        return x >= 0 && x < WELL + 2 * PAD + 2 && y >= 0 && y < HEIGHT;
    }

    /** The backpack slot (0..2) under the mouse, or -1. */
    public static int slotAt(double mouseX, double mouseY, int leftPos, int topPos, int slotX) {
        double x = mouseX - leftPos - (slotX - 1);
        if (x < 0 || x >= WELL) {
            return -1;
        }
        for (int i = 0; i < Backpacks.SLOTS; i++) {
            double y = mouseY - topPos - (slotY(i) - 1);
            if (y >= 0 && y < WELL) {
                return i;
            }
        }
        return -1;
    }

    /** Names an empty slot, or says how to open a carried backpack. */
    public static void tooltip(GuiGraphicsExtractor g, Font font, Player player, ItemStack carried, int mouseX, int mouseY, int leftPos, int topPos, int slotX) {
        int slot = slotAt(mouseX, mouseY, leftPos, topPos, slotX);
        if (slot < 0 || !carried.isEmpty() || !Backpacks.get(player, slot).isEmpty()) {
            return;
        }
        g.setComponentTooltipForNextFrame(font, List.of(
                Component.literal("Backpack slot").withColor(Theme.ACCENT),
                Component.literal("Put a backpack here to carry it.").withStyle(ChatFormatting.GRAY),
                Component.literal("Right-click it (or press B) to open.").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
    }

    private static int slotY(int slot) {
        return BackpackMenuSlot.Y + BackpackMenuSlot.SPACING * slot;
    }
}
