package com.skyrpg.welcome.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * One row of a settings list: a label with a short description on the left and a control on the right (a switch, a
 * "< value >" cycler, a slider or a key cap). Used for the wizard's toggles, the SkyRPG settings and the key list.
 */
final class SkyRow extends AbstractWidget {
    private enum Kind { TOGGLE, CYCLE, SLIDER, KEY }

    private final Font font;
    private final Kind kind;
    private final Component description;
    private BooleanSupplier on = () -> false;
    private Consumer<Boolean> setOn = v -> { };
    private Supplier<String> value = () -> "";
    private IntConsumer step = d -> { };
    private DoubleSupplier progress = () -> 0;
    private DoubleConsumer setProgress = v -> { };
    private BooleanSupplier conflict = () -> false;
    private BooleanSupplier listening = () -> false;
    private Runnable listen = () -> { };
    private float hover;
    private float knob;
    private long lastFrame = Util.getMillis();

    private SkyRow(Font font, Kind kind, int x, int y, int w, int h, Component label, Component description) {
        super(x, y, w, h, label);
        this.font = font;
        this.kind = kind;
        this.description = description;
    }

    static SkyRow toggle(Font font, int x, int y, int w, int h, Component label, Component description, BooleanSupplier get, Consumer<Boolean> set) {
        SkyRow row = new SkyRow(font, Kind.TOGGLE, x, y, w, h, label, description);
        row.on = get;
        row.setOn = set;
        row.knob = get.getAsBoolean() ? 1 : 0;
        return row;
    }

    static SkyRow cycle(Font font, int x, int y, int w, int h, Component label, Component description, Supplier<String> value, IntConsumer step) {
        SkyRow row = new SkyRow(font, Kind.CYCLE, x, y, w, h, label, description);
        row.value = value;
        row.step = step;
        return row;
    }

    static SkyRow slider(Font font, int x, int y, int w, int h, Component label, Component description, DoubleSupplier progress,
                         DoubleConsumer set, Supplier<String> value) {
        SkyRow row = new SkyRow(font, Kind.SLIDER, x, y, w, h, label, description);
        row.progress = progress;
        row.setProgress = set;
        row.value = value;
        return row;
    }

    static SkyRow key(Font font, int x, int y, int w, int h, Component label, Supplier<String> keyName, BooleanSupplier conflict,
                      BooleanSupplier listening, Runnable listen) {
        SkyRow row = new SkyRow(font, Kind.KEY, x, y, w, h, label, Component.empty());
        row.value = keyName;
        row.conflict = conflict;
        row.listening = listening;
        row.listen = listen;
        return row;
    }

    /** A row for a registered SkyRPG setting, or null if no mod registered it (the mod is not installed). */
    static SkyRow forSetting(Font font, String id, int x, int y, int w, int h) {
        Setting setting = SkyRpgSettings.find(id);
        if (setting == null) {
            return null;
        }
        if (setting.isToggle()) {
            return toggle(font, x, y, w, h, setting.label(), setting.description(), setting::get, setting::set);
        }
        if (setting.isChoice()) {
            return cycle(font, x, y, w, h, setting.label(), setting.description(), setting::choice, setting::cycle);
        }
        if (setting.isSlider()) {
            return slider(font, x, y, w, h, setting.label(), setting.description(), setting::progress,
                    p -> setting.setNumber(setting.snap(setting.min() + p * (setting.max() - setting.min()))), setting::formatted);
        }
        return null;
    }

    boolean isKey() {
        return kind == Kind.KEY;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        switch (kind) {
            case TOGGLE -> {
                setOn.accept(!on.getAsBoolean());
                Ui.click(on.getAsBoolean() ? 1.4f : 1.0f);
            }
            case CYCLE -> {
                step.accept(event.button() == 1 ? -1 : 1);
                Ui.click(1.2f);
            }
            case SLIDER -> setFromMouse(event.x());
            case KEY -> {
                listen.run();
                Ui.click(1.2f);
            }
        }
    }

    private int controlWidth() {
        return switch (kind) {
            case TOGGLE -> 24;
            case CYCLE -> Math.min(getWidth() / 2 - 10, Math.max(54, font.width(value.get()) + 26));
            case SLIDER -> Math.min(100, getWidth() / 3);
            case KEY -> Math.min(getWidth() / 2 - 10, Math.max(40, font.width(value.get()) + 12));
        };
    }

    private void setFromMouse(double mouseX) {
        int width = controlWidth();
        int left = getRight() - 8 - width;
        double p = Mth.clamp((mouseX - left) / width, 0, 1);
        setProgress.accept(p);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (kind == Kind.SLIDER) {
            setFromMouse(event.x());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !visible || !isFocused()) {
            return false;
        }
        int key = event.key();
        boolean confirm = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_NUMPADENTER;
        switch (kind) {
            case TOGGLE -> {
                if (confirm) {
                    setOn.accept(!on.getAsBoolean());
                    Ui.click(1.2f);
                    return true;
                }
            }
            case CYCLE -> {
                if (confirm || key == InputConstants.KEY_RIGHT) {
                    step.accept(1);
                    return true;
                }
                if (key == InputConstants.KEY_LEFT) {
                    step.accept(-1);
                    return true;
                }
            }
            case SLIDER -> {
                if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_LEFT) {
                    setProgress.accept(Mth.clamp(progress.getAsDouble() + (key == InputConstants.KEY_RIGHT ? 0.125 : -0.125), 0, 1));
                    return true;
                }
            }
            case KEY -> {
                if (confirm) {
                    listen.run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        hover = Ui.approach(hover, isHoveredOrFocused() ? 1 : 0, dt, 14);
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Draw.rounded(g, x, y, w, h, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
        if (isFocused()) {
            Draw.outline(g, x, y, w, h, Theme.ACCENT);
        }
        int controlW = controlWidth();
        int textRoom = w - controlW - 24;
        boolean twoLines = h >= 21 && !description.getString().isEmpty();
        int labelY = twoLines ? y + 3 : y + (h - 8) / 2;
        if (Draw.fitText(g, font, message, x + 8, labelY, textRoom, Theme.TEXT)) {
            Draw.hoverTooltip(mouseX, mouseY, x, y, w, h, List.of(message, description), Theme.ACCENT);
        }
        if (twoLines && Draw.fitText(g, font, description, x + 8, y + h - 10, textRoom, Theme.DIM)) {
            Draw.hoverTooltip(mouseX, mouseY, x, y, w, h, List.of(message.copy().withColor(Theme.TEXT), description), Theme.ACCENT);
        }
        int cx = x + w - 8 - controlW;
        int cy = y + (h - 12) / 2;
        switch (kind) {
            case TOGGLE -> drawSwitch(g, cx, cy, dt);
            case CYCLE -> {
                Draw.panel(g, cx, cy - 1, controlW, 14, Theme.INPUT, Theme.CARD_BORDER);
                Draw.text(g, font, "<", cx + 4, cy + 2, Draw.mix(Theme.DIM, Theme.ACCENT, hover));
                Draw.rightText(g, font, ">", cx + controlW - 4, cy + 2, Draw.mix(Theme.DIM, Theme.ACCENT, hover));
                Draw.fit(g, font, Component.literal(value.get()), cx + controlW / 2, cy + 2, controlW - 24, Draw.Align.CENTER, Theme.TEXT);
            }
            case SLIDER -> {
                int trackY = cy + 4;
                Draw.bar(g, cx, trackY, controlW, 5, (float) progress.getAsDouble(), Theme.ACCENT);
                int knobX = cx + Math.round((controlW - 4) * (float) progress.getAsDouble());
                Draw.rounded(g, knobX, trackY - 2, 5, 9, Draw.lighten(Theme.ACCENT, 0.4f));
                Draw.rightText(g, font, value.get(), cx - 6, cy + 2, Theme.MUTED);
            }
            case KEY -> {
                boolean wait = listening.getAsBoolean();
                boolean bad = conflict.getAsBoolean();
                int border = wait ? Theme.ACCENT : bad ? Theme.RED : Theme.CARD_BORDER;
                Draw.panel(g, cx, cy - 1, controlW, 14, wait ? Theme.ACCENT_SOFT : Theme.KEY, border);
                String text = wait ? ((now / 400) % 2 == 0 ? "press a key" : "press a key.") : value.get();
                Draw.fit(g, font, Component.literal(text), cx + controlW / 2, cy + 2, controlW - 8, Draw.Align.CENTER, wait ? Theme.ACCENT : bad ? Theme.RED : Theme.TEXT);
            }
        }
    }

    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, float dt) {
        boolean enabled = on.getAsBoolean();
        knob = Ui.approach(knob, enabled ? 1 : 0, dt, 18);
        Draw.rounded(g, x, y, 24, 12, Draw.mix(Theme.TRACK, Theme.ACCENT, knob));
        int kx = x + 2 + Math.round(knob * 12);
        Draw.rounded(g, kx, y + 2, 8, 8, Draw.mix(Theme.MUTED, 0xFFFFFFFF, knob));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
        if (!description.getString().isEmpty()) {
            output.add(NarratedElementType.HINT, description);
        }
        String state = kind == Kind.TOGGLE ? (on.getAsBoolean() ? "on" : "off") : value.get();
        if (!state.isEmpty()) {
            output.add(NarratedElementType.USAGE, Component.literal(state));
        }
    }
}
