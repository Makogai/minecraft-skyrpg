package com.skyrpg.chat.client;

import com.skyrpg.chat.client.ImageCache.Entry;
import com.skyrpg.chat.client.ImageCache.State;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** A picture from the chat, as big as fits the window. Esc (or a click) closes it and goes back to the chat. */
public final class ImageViewerScreen extends Screen {
    private static final int MARGIN = 24;
    private final Entry entry;
    private final Screen parent;

    ImageViewerScreen(Entry entry, Screen parent) {
        super(Component.literal("Image"));
        this.entry = entry;
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String caption = ChatImageRender.describe(entry.ref);
        if (entry.state != State.READY || entry.texture == null || entry.width() <= 0) {
            if (entry.state == State.READY && entry.texture == null) {
                ImageCache.reload(entry);
            }
            g.centeredText(font, "Loading...", width / 2, height / 2 - 4, 0xFFAAB4C8);
            return;
        }
        entry.lastUse = System.nanoTime();
        int availableWidth = width - MARGIN * 2;
        int availableHeight = height - MARGIN * 2 - 12;
        // Pixel-art stickers may grow a lot; photos stay at most twice their stored size.
        double limit = entry.ref.kind() == ImageRefs.Kind.STICKER ? 6.0 : 2.0;
        double scale = Math.min(limit, Math.min((double) availableWidth / entry.width(), (double) availableHeight / entry.height()));
        int dw = Math.max(1, (int) Math.round(entry.width() * scale));
        int dh = Math.max(1, (int) Math.round(entry.height() * scale));
        int x = (width - dw) / 2;
        int y = (height - 12 - dh) / 2;
        g.fill(x - 2, y - 2, x + dw + 2, y + dh + 2, 0xFF252C39);
        g.blit(RenderPipelines.GUI_TEXTURED, entry.texture, x, y, 0f, 0f, dw, dh, entry.width(), entry.height(), entry.width(), entry.height());
        g.centeredText(font, caption + "  -  " + entry.width() + "x" + entry.height() + "  -  Esc to close", width / 2, y + dh + 8, 0xFFAAB4C8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
