package com.skyrpg.fishing.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.fishing.Bait;
import com.skyrpg.fishing.FishingItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/**
 * While you hold a fishing rod: the bait that would be used (offhand, then hotbar, then inventory) with how many are left,
 * or "No bait"; and, while the bobber floats in a hotspot, its buffs. Movable and hideable in the HUD editor.
 */
final class BaitWidget implements HudWidget {
    static String hotspot = "";

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("skyrpg_fishing", "bait");
    }

    @Override
    public Component name() {
        return Component.literal("Fishing Bait");
    }

    private static String baitLine(boolean editing) {
        Player player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? null : Bait.stack(player);
        if (stack == null) {
            return editing ? "Worm Bait x12" : "No bait";
        }
        return ((FishingItems.BaitItem) stack.getItem()).baitName() + " x" + Bait.count(player, stack.getItem());
    }

    private static String hotspotLine(boolean editing) {
        return !hotspot.isEmpty() ? "Hotspot: " + hotspot : editing ? "Hotspot: +30 Fishing Speed" : "";
    }

    @Override
    public int width(Font font) {
        return Math.min(220, Math.max(font.width(baitLine(true)), font.width(hotspotLine(false))) + 30);
    }

    @Override
    public int height(Font font) {
        return hotspot.isEmpty() ? 22 : 33;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.BOTTOM_CENTER;
    }

    @Override
    public int defaultX() {
        // right of the hotbar (91 px each side of the centre), centred in the room there so even the widest box (220)
        // clears it; the action bar above the hotbar stays free for messages like the sea creature alert
        return 91 + 8 + 110;
    }

    @Override
    public int defaultY() {
        return -46;
    }

    @Override
    public boolean visible() {
        Player player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().getItem() instanceof FishingRodItem
                || player.getOffhandItem().getItem() instanceof FishingRodItem);
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Player player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? null : Bait.stack(player);
        int width = width(font);
        int height = hotspot.isEmpty() && !editing ? 22 : 33;
        Draw.rounded(g, 0, 0, width, height, 0xE0121620);
        g.fill(0, 2, 2, height - 2, 0xFF55FFFF);
        if (stack != null || editing) {
            g.item(stack != null ? new ItemStack(stack.getItem()) : new ItemStack(FishingItems.WORM_BAIT), 5, 3);
        }
        Draw.fitText(g, font, baitLine(editing), 25, 7, width - 29, stack != null || editing ? Theme.TEXT : Theme.MUTED);
        String spot = hotspotLine(editing);
        if (!spot.isEmpty()) {
            Draw.fitText(g, font, spot, 6, 22, width - 10, Theme.ACCENT);
        }
    }
}
