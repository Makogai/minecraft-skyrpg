package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The small "new waypoint" box opened by a right-click on the world map: a name and a colour. */
final class WaypointDialog extends Screen {
    private static final int W = 200;
    private static final int H = 104;
    private static final int SWATCH = 16;
    private static final int GAP = 6;

    private final Screen parent;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private EditBox name;
    private int color;

    WaypointDialog(Screen parent, int x, int y, int z) {
        super(Component.literal("New waypoint"));
        this.parent = parent;
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.color = Waypoints.COLORS[Waypoints.list().size() % Waypoints.COLORS.length];
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int swatchLeft() {
        return left() + (W - (Waypoints.COLORS.length * (SWATCH + GAP) - GAP)) / 2;
    }

    @Override
    protected void init() {
        name = new EditBox(font, left() + 10, top() + 26, W - 20, 18, Component.literal("Name"));
        name.setMaxLength(24);
        name.setValue(Waypoints.nextName());
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("Add"), button -> confirm()).bounds(left() + 10, top() + H - 28, 85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left() + W - 95, top() + H - 28, 85, 20).build());
    }

    private void confirm() {
        Waypoints.add(name.getValue(), blockX, blockY, blockZ, color);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        Draw.panel(g, left(), top(), W, H, 0xF0121620, Theme.BORDER);
        Draw.text(g, font, "New waypoint", left() + 10, top() + 8, Theme.ACCENT);
        Draw.rightText(g, font, blockX + ", " + blockY + ", " + blockZ, left() + W - 10, top() + 8, Theme.MUTED);
        for (int i = 0; i < Waypoints.COLORS.length; i++) {
            int x = swatchLeft() + i * (SWATCH + GAP);
            int y = top() + 52;
            int c = Waypoints.COLORS[i];
            if (c == color) {
                g.fill(x - 2, y - 2, x + SWATCH + 2, y + SWATCH + 2, 0xFFFFFFFF);
            }
            g.fill(x, y, x + SWATCH, y + SWATCH, c);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < Waypoints.COLORS.length; i++) {
            int x = swatchLeft() + i * (SWATCH + GAP);
            int y = top() + 52;
            if (event.x() >= x && event.x() < x + SWATCH && event.y() >= y && event.y() < y + SWATCH) {
                color = Waypoints.COLORS[i];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }
}
