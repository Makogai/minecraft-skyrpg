package com.skyrpg.devtools.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Dev only: shows an item's tooltip (main hand, or a given inventory slot) so screenshots can check
 * tooltips without a mouse.
 */
final class TooltipPreviewScreen extends Screen {
    private final int slot;

    /** {@code slot} is an inventory index, or -1 for the main hand. */
    TooltipPreviewScreen(int slot) {
        super(Component.literal("Tooltip preview"));
        this.slot = slot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY
                : slot < 0 ? minecraft.player.getMainHandItem() : minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            g.text(font, "Hold an item to preview its tooltip", 20, 20, 0xFFFFFFFF, true);
            return;
        }
        int x = width / 2 - 90;
        int y = 16;
        g.item(stack, x - 22, y);
        g.setTooltipForNextFrame(font, stack, x, y + 12);
    }
}
