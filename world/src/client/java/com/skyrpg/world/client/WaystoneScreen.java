package com.skyrpg.world.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.world.WorldContent;
import com.skyrpg.world.WorldPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The waystone menu: where you are, and a card for every place you can travel to (the hub first, then your attuned
 * waystones), with what it costs. Click a card to go.
 */
final class WaystoneScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int ROW = 30;
    private static final int HEADER = 46;
    private static final int GLOW = 0xFF5CE1E6;
    private static final ItemStack HUB_ICON = new ItemStack(Items.NETHER_STAR);
    private static final ItemStack WAYSTONE_ICON = new ItemStack(WorldContent.WAYSTONE_ITEM);

    private static final int RENAME_W = 46;

    private final WorldPayloads.WaystoneMenu menu;
    private final long openedAt = Util.getMillis();
    private int left;
    private int top;
    private int listHeight;
    private double scroll;

    WaystoneScreen(WorldPayloads.WaystoneMenu menu) {
        super(Component.literal("Waystone"));
        this.menu = menu;
    }

    @Override
    protected void init() {
        int rows = Math.max(1, menu.destinations().size());
        listHeight = Math.min(rows * ROW, height - HEADER - 50);
        int windowHeight = HEADER + listHeight + 22;
        left = (width - WIDTH) / 2;
        top = (height - windowHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x9006121A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        float time = (Util.getMillis() - openedAt) / 1000f;
        int windowHeight = HEADER + listHeight + 22;
        Draw.panel(g, left, top, WIDTH, windowHeight, Theme.WINDOW, 0xFF1F4A55);
        // A soft rune glow along the top edge, breathing slowly.
        int glowAlpha = (int) (40 + 30 * Math.sin(time * 2.2));
        g.fill(left + 1, top + 1, left + WIDTH - 1, top + 3, (glowAlpha << 24) | (GLOW & 0xFFFFFF));
        Draw.item(g, WAYSTONE_ICON, left + 10, top + 10, 1.5f);
        Draw.fit(g, font, Component.literal("WAYSTONE"), left + 40, top + 11, WIDTH - 50 - (menu.canRename() ? RENAME_W + 4 : 0), Draw.Align.LEFT, GLOW);
        Draw.fit(g, font, Component.literal(menu.here()), left + 40, top + 24, WIDTH - 50, Draw.Align.LEFT, Theme.TEXT);
        int playerLevels = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
        boolean creative = minecraft.player != null && minecraft.player.isCreative();
        Draw.fit(g, font, Component.literal("Your XP: " + playerLevels + " levels"), left + WIDTH - 10, top + 36, 120, Draw.Align.RIGHT, Theme.MUTED);
        if (menu.canRename()) {
            boolean over = overRename(mouseX, mouseY);
            Draw.panel(g, left + WIDTH - RENAME_W - 10, top + 8, RENAME_W, 14, over ? Theme.CARD_HOVER : Theme.CARD, over ? GLOW : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal("Rename"), left + WIDTH - 10 - RENAME_W / 2, top + 11, RENAME_W - 4, Draw.Align.CENTER,
                    over ? GLOW : Theme.TEXT);
        }

        int listTop = top + HEADER;
        List<WorldPayloads.Destination> destinations = menu.destinations();
        if (destinations.isEmpty()) {
            Draw.fit(g, font, Component.literal("Attune to other waystones to travel to them."), left + WIDTH / 2, listTop + 10, WIDTH - 20,
                    Draw.Align.CENTER, Theme.MUTED);
        }
        g.enableScissor(left + 4, listTop, left + WIDTH - 4, listTop + listHeight);
        for (int i = 0; i < destinations.size(); i++) {
            WorldPayloads.Destination destination = destinations.get(i);
            int y = listTop + i * ROW - (int) Math.round(scroll);
            if (y + ROW < listTop || y > listTop + listHeight) {
                continue;
            }
            int x = left + 8;
            int w = WIDTH - 16;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW - 3, listTop + listHeight);
            boolean affordable = creative || destination.cost() <= playerLevels;
            Draw.panel(g, x, y, w, ROW - 3, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? GLOW : Theme.CARD_BORDER);
            Draw.item(g, destination.kind() == WorldPayloads.Destination.HUB ? HUB_ICON : WAYSTONE_ICON, x + 5, y + 5, 1f);
            Draw.fit(g, font, Component.literal(destination.name()), x + 26, y + 4, w - 90, Draw.Align.LEFT, Theme.TEXT);
            Draw.fit(g, font, Component.literal(destination.where()), x + 26, y + 15, w - 90, Draw.Align.LEFT, Theme.DIM);
            String cost = destination.cost() == 0 ? "Free" : destination.cost() + (destination.cost() == 1 ? " level" : " levels");
            int pillWidth = font.width(cost) + 10;
            int pillX = x + w - pillWidth - 6;
            Draw.rounded(g, pillX, y + 7, pillWidth, 13, affordable ? 0x3055E07A : 0x30FF6464);
            Draw.fit(g, font, Component.literal(cost), pillX + pillWidth / 2, y + 10, pillWidth, Draw.Align.CENTER,
                    affordable ? Theme.GREEN : Theme.RED);
        }
        g.disableScissor();
        int contentHeight = destinations.size() * ROW;
        if (contentHeight > listHeight) {
            Draw.scrollbar(g, left + WIDTH - 5, listTop, listHeight, contentHeight, scroll, GLOW);
        }
        Draw.fit(g, font, Component.literal("Click a destination to travel · Esc to close"), left + WIDTH / 2, top + windowHeight - 14,
                WIDTH - 20, Draw.Align.CENTER, Theme.DIM);
    }

    private boolean overRename(double mx, double my) {
        return menu.canRename() && mx >= left + WIDTH - RENAME_W - 10 && mx < left + WIDTH - 10 && my >= top + 8 && my < top + 22;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (overRename(event.x(), event.y())) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            minecraft.gui.setScreen(new WaystoneRenameScreen(this, menu.here()));
            return true;
        }
        int listTop = top + HEADER;
        double mx = event.x();
        double my = event.y();
        if (mx >= left + 8 && mx < left + WIDTH - 8 && my >= listTop && my < listTop + listHeight) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < menu.destinations().size() && (my - listTop + scroll) % ROW < ROW - 3) {
                ClientPlayNetworking.send(new WorldPayloads.Travel(index));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = menu.destinations().size() * ROW;
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, contentHeight - listHeight));
        return true;
    }
}
