package com.skyrpg.core.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import java.util.List;

/**
 * Drag widgets to move them, scroll over one to resize it, right-click to hide or show it, R resets
 * the one under the mouse (or all). Esc saves and goes back.
 */
public class HudEditorScreen extends Screen {
    private static final String HELP = "Drag to move  ·  Scroll to resize  ·  Right-click to hide  ·  R to reset  ·  Esc to finish";
    private static final String HELP_TOP = "Drag to move  ·  Scroll to resize  ·  Right-click to hide";
    private static final String HELP_BOTTOM = "R to reset  ·  Esc to finish";
    private final Screen parent;
    private HudWidget dragging;
    private double grabX;
    private double grabY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66070910);
        // Thirds grid: widgets pin to whichever third their centre is in.
        for (int i = 1; i < 3; i++) {
            g.fill(width * i / 3, 0, width * i / 3 + 1, height, 0x18FFFFFF);
            g.fill(0, height * i / 3, width, height * i / 3 + 1, 0x18FFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        HudWidget hovered = widgetAt(mouseX, mouseY);
        for (HudWidget widget : HudManager.widgets()) {
            HudManager.Box box = HudManager.box(widget, font, width, height);
            boolean enabled = HudManager.layout(widget).enabled;
            boolean active = widget == dragging || (dragging == null && widget == hovered);
            if (enabled) {
                HudManager.draw(g, font, widget, true);
            }
            int border = active ? Theme.ACCENT : enabled ? Draw.withAlpha(Theme.TEXT, 0.35f) : Draw.withAlpha(Theme.RED, 0.6f);
            g.outline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, border);
            if (!enabled) {
                g.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0x40000000);
            }
            if (active || !enabled) {
                String label = widget.name().getString() + (enabled ? "  " + Math.round(HudManager.layout(widget).scale * 100) + "%" : "  (hidden)");
                int labelY = box.y() > 12 ? box.y() - 11 : box.y() + box.height() + 2;
                // Keep the label on screen for widgets in the right-hand corner.
                int labelX = Mth.clamp(box.x() - 1, 1, Math.max(1, width - font.width(label) - 7));
                Draw.rounded(g, labelX, labelY - 1, font.width(label) + 6, 11, 0xE00C0F16);
                Draw.text(g, font, label, labelX + 3, labelY + 1, active ? Theme.ACCENT : Theme.MUTED);
            }
        }
        drawHelp(g, mouseX, mouseY);
        if (hovered != null) {
            g.requestCursor(dragging != null ? CursorTypes.RESIZE_ALL : CursorTypes.POINTING_HAND);
        }
    }

    /**
     * The key help at the top, wrapped onto two lines on narrow screens. It fades while it covers a
     * widget or the mouse, so it never hides what's being arranged.
     */
    private void drawHelp(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = font.width(HELP) + 16 <= width - 8 ? List.of(Component.literal(HELP).getVisualOrderText())
                : List.of(Component.literal(HELP_TOP).getVisualOrderText(), Component.literal(HELP_BOTTOM).getVisualOrderText());
        int helpWidth = 0;
        for (FormattedCharSequence line : lines) {
            helpWidth = Math.max(helpWidth, font.width(line));
        }
        helpWidth = Math.min(width - 8, helpWidth + 16);
        int helpHeight = 8 + lines.size() * 10;
        int x = (width - helpWidth) / 2;
        int y = 8;
        HudManager.Box help = new HudManager.Box(x, y, helpWidth, helpHeight);
        boolean covering = dragging != null || help.contains(mouseX, mouseY);
        for (HudWidget widget : HudManager.widgets()) {
            covering |= HudManager.box(widget, font, width, height).intersects(help);
        }
        float alpha = covering ? 0.35f : 1f;
        Draw.panel(g, x, y, helpWidth, helpHeight, Draw.withAlpha(0xF0121620, alpha), Draw.withAlpha(Theme.BORDER, alpha));
        for (int i = 0; i < lines.size(); i++) {
            int lineWidth = font.width(lines.get(i));
            g.text(font, lines.get(i), (width - lineWidth) / 2, y + 5 + i * 10, Draw.withAlpha(Theme.TEXT, alpha), false);
        }
    }

    private HudWidget widgetAt(double x, double y) {
        for (int i = HudManager.widgets().size() - 1; i >= 0; i--) {
            HudWidget widget = HudManager.widgets().get(i);
            if (HudManager.box(widget, font, width, height).contains(x, y)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudWidget widget = widgetAt(event.x(), event.y());
        if (widget == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            HudManager.Layout layout = HudManager.layout(widget);
            layout.enabled = !layout.enabled;
            click(layout.enabled ? 1.3f : 0.9f);
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        HudManager.Box box = HudManager.box(widget, font, width, height);
        dragging = widget;
        grabX = event.x() - box.x();
        grabY = event.y() - box.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        HudManager.moveTo(dragging, font, (int) Math.round(event.x() - grabX), (int) Math.round(event.y() - grabY), width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudWidget widget = widgetAt(mouseX, mouseY);
        if (widget == null) {
            return false;
        }
        HudManager.Layout layout = HudManager.layout(widget);
        HudManager.Box before = HudManager.box(widget, font, width, height);
        layout.scale = Mth.clamp(Math.round((layout.scale + (float) Math.signum(scrollY) * 0.1f) * 10) / 10f, HudManager.MIN_SCALE, HudManager.MAX_SCALE);
        // Scale around the widget's centre rather than its corner.
        HudManager.Box after = HudManager.box(widget, font, width, height);
        HudManager.moveTo(widget, font, before.x() + (before.width() - after.width()) / 2, before.y() + (before.height() - after.height()) / 2, width, height);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_R) {
            HudWidget widget = widgetAt(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()), minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
            if (widget != null) {
                HudManager.reset(widget);
            } else {
                HudManager.widgets().forEach(HudManager::reset);
            }
            click(1.0f);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        HudManager.save();
        minecraft.gui.setScreen(parent);
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }
}
