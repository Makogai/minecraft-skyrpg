package com.skyrpg.world.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.world.WorldPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The small "rename this waystone" box opened from the waystone menu: a name of up to 24 characters. The server decides whether it is allowed. */
final class WaystoneRenameScreen extends Screen {
    private static final int W = 220;
    private static final int H = 84;
    private static final int MAX_NAME = 24;

    private final Screen parent;
    private final String current;
    private EditBox name;

    WaystoneRenameScreen(Screen parent, String current) {
        super(Component.literal("Rename waystone"));
        this.parent = parent;
        this.current = current;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        name = new EditBox(font, left() + 10, top() + 26, W - 20, 18, Component.literal("Name"));
        name.setMaxLength(MAX_NAME);
        name.setValue(current);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("Rename"), button -> confirm()).bounds(left() + 10, top() + H - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left() + W - 105, top() + H - 28, 95, 20).build());
    }

    private void confirm() {
        String value = name.getValue().strip();
        if (!value.isEmpty() && !value.equals(current)) {
            ClientPlayNetworking.send(new WorldPayloads.Rename(value));
            minecraft.gui.setScreen(null); // the server answers by opening the menu again, under the new name
        } else {
            onClose();
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        Draw.panel(g, left(), top(), W, H, 0xF0121620, Theme.BORDER);
        Draw.text(g, font, "Rename waystone", left() + 10, top() + 8, Theme.ACCENT);
        Draw.rightText(g, font, "up to " + MAX_NAME + " characters", left() + W - 10, top() + 8, Theme.MUTED);
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
