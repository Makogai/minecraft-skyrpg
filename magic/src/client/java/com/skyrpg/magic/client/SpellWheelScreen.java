package com.skyrpg.magic.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.Magic;
import com.skyrpg.magic.SpellDef;
import com.skyrpg.magic.WandData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The spell wheel: the held wand's spells around a circle centred on the screen. Hold the wheel key (right click), move the mouse
 * towards a spell and let go to pick it. It pops open with a little overshoot and shrinks away when it closes. Clicking a spell
 * also picks it, and the toggle key opens a wheel that stays until you pick or press Esc.
 */
final class SpellWheelScreen extends Screen {
    private static final int INNER = 30;
    private static final int OUTER = 96;
    private static final int OPEN_TICKS = 6;
    private static final int CLOSE_TICKS = 4;

    private final boolean toggled;
    private final int count;
    private final byte[] map;
    private int age;
    private int closing = -1;
    private int hovered = -1;
    private int chosen = -1;

    private SpellWheelScreen(boolean toggled, int count) {
        super(Component.literal("Spells"));
        this.toggled = toggled;
        this.count = count;
        this.map = buildMap(count);
    }

    static void open(boolean toggled) {
        Minecraft minecraft = Minecraft.getInstance();
        WandData data = ClientMagic.wandData();
        if (!ClientMagic.holdingWand() || data.slots().isEmpty()) {
            return;
        }
        minecraft.gui.setScreen(new SpellWheelScreen(toggled, data.slots().size()));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.7f));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 0.3f));
    }

    /** For each pixel of the ring (relative to its centre): the segment it belongs to, or -1 for outside, the hole and the gaps between. */
    private static byte[] buildMap(int count) {
        int size = OUTER * 2 + 1;
        byte[] map = new byte[size * size];
        double width = 2 * Math.PI / count;
        for (int y = -OUTER; y <= OUTER; y++) {
            for (int x = -OUTER; x <= OUTER; x++) {
                double r = Math.hypot(x, y);
                byte value = -1;
                if (r >= INNER && r <= OUTER) {
                    double a = Math.atan2(y, x) + Math.PI / 2 + width / 2;
                    a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
                    double into = a % width;
                    double edge = Math.min(into, width - into) * r;
                    if (count == 1 || edge >= 1.6) {
                        value = (byte) Math.min(count - 1, (int) (a / width));
                    }
                }
                map[(y + OUTER) * size + x + OUTER] = value;
            }
        }
        return map;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (closing >= 0) {
            if (++closing >= CLOSE_TICKS) {
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (!ClientMagic.holdingWand()) {
            minecraft.gui.setScreen(null);
        }
    }

    private int segmentAt(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (Math.hypot(dx, dy) < INNER * 0.55) {
            return -1;
        }
        double width = 2 * Math.PI / count;
        double a = Math.atan2(dy, dx) + Math.PI / 2 + width / 2;
        a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return Math.min(count - 1, (int) (a / width));
    }

    /** Picks the hovered slot (the current one if the pointer is in the middle) and starts closing. */
    private void choose() {
        if (closing >= 0) {
            return;
        }
        int slot = hovered >= 0 ? hovered : ClientMagic.wandData().selected();
        chosen = slot;
        if (slot != ClientMagic.wandData().selected()) {
            ClientMagic.select(slot);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f + 0.08f * slot, 0.8f));
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.5f));
        closing = 0;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!toggled && MagicInput.wheelKey.matchesMouse(event) || (!toggled && MagicInput.wheelKey.isDefault() && event.button() == 1)) {
            choose();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && hovered >= 0) {
            choose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!toggled && MagicInput.wheelKey.matches(event)) {
            choose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (toggled && MagicInput.wheelToggleKey.matches(event)) {
            choose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (closing < 0) {
            closing = 0;
        }
    }

    /** 0 to about 1.08 and back to 1: the pop on opening; closing runs it backwards. */
    private float scale(float partialTick) {
        if (closing >= 0) {
            float t = Mth.clamp((closing + partialTick) / CLOSE_TICKS, 0, 1);
            return 1 - t * t * 0.9f;
        }
        float t = Mth.clamp((age + partialTick) / OPEN_TICKS, 0, 1);
        float c1 = 1.9f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private float fade(float partialTick) {
        if (closing >= 0) {
            return 1 - Mth.clamp((closing + partialTick) / CLOSE_TICKS, 0, 1);
        }
        return Mth.clamp((age + partialTick) / (OPEN_TICKS - 2f), 0, 1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float fade = fade(partialTick);
        g.fillGradient(0, 0, width, height, Draw.withAlpha(0x88050810, fade), Draw.withAlpha(0xAA050810, fade));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = minecraft.font;
        float fade = fade(partialTick);
        WandData data = ClientMagic.wandData();
        if (closing < 0) {
            hovered = segmentAt(mouseX, mouseY);
        }
        int focus = closing >= 0 ? chosen : hovered >= 0 ? hovered : data.selected();
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f);
        float scale = scale(partialTick);
        g.pose().scale(scale, scale);

        // The ring, row by row: one fill per run of the same segment.
        int size = OUTER * 2 + 1;
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            SpellDef spell = Magic.spell(data.spellAt(i));
            int base = spell == null ? 0xFF0B0E14 : Draw.mix(0xFF0B0E14, spell.element().color(), 0.16f);
            boolean selected = i == data.selected();
            boolean hover = i == focus;
            if (hover) {
                base = spell == null ? 0xFF1A2030 : Draw.mix(0xFF0B0E14, spell.element().color(), 0.5f);
            } else if (selected) {
                base = Draw.mix(base, 0xFFFFFFFF, 0.08f);
            }
            if (spell != null && ClientMagic.cooldownLeft(spell) > 0) {
                base = Draw.mix(base, 0xFF000000, 0.35f);
            }
            colors[i] = Draw.withAlpha((base & 0xFFFFFF) | 0xD8000000, fade);
        }
        for (int row = 0; row < size; row++) {
            int runStart = 0;
            int runValue = map[row * size];
            for (int col = 1; col <= size; col++) {
                int value = col < size ? map[row * size + col] : -2;
                if (value != runValue) {
                    if (runValue >= 0) {
                        g.fill(runStart - OUTER, row - OUTER, col - OUTER, row - OUTER + 1, colors[runValue]);
                    }
                    runStart = col;
                    runValue = value;
                }
            }
        }

        // Icons, names and costs.
        double step = 2 * Math.PI / count;
        double mid = (INNER + OUTER) / 2.0;
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + i * step;
            int cx = (int) Math.round(Math.cos(angle) * mid);
            int cy = (int) Math.round(Math.sin(angle) * mid);
            SpellDef spell = Magic.spell(data.spellAt(i));
            boolean hover = i == focus;
            float lift = hover ? 1.15f : 1f;
            if (spell == null) {
                Draw.text(g, font, "+", cx - font.width("+") / 2, cy - 4, Draw.withAlpha(Theme.DIM, fade));
                String empty = "Empty";
                Draw.text(g, font, empty, cx - font.width(empty) / 2, cy + 8, Draw.withAlpha(Theme.DIM, fade));
                continue;
            }
            int cooldown = ClientMagic.cooldownLeft(spell);
            float item = 1.5f * lift;
            Draw.item(g, ClientMagic.icon(spell), Math.round(cx - 8 * item), Math.round(cy - 8 * item - 6), item);
            String name = spell.name();
            int nameWidth = font.width(name);
            int textColor = Draw.withAlpha(hover ? 0xFFFFFFFF : spell.rarity().color(), fade);
            Draw.text(g, font, name, cx - nameWidth / 2, cy + 8, textColor);
            String detail = cooldown > 0 ? String.format("%.1fs", cooldown / 20.0) : Msg.number(Math.ceil(ClientMagic.cost(spell)));
            int detailColor = cooldown > 0 ? 0xFFFF7A7A : (ClientMagic.mana() < ClientMagic.cost(spell) ? 0xFFFF7A7A : 0xFF6FE6EC);
            String shown = (cooldown > 0 ? "" : "◆ ") + detail;
            Draw.text(g, font, shown, cx - font.width(shown) / 2, cy + 18, Draw.withAlpha(detailColor, fade));
            if (i == data.selected()) {
                g.fill(cx - 3, cy - 36, cx + 3, cy - 34, Draw.withAlpha(spell.element().color(), fade));
            }
        }

        // The middle: what the pointer is over.
        SpellDef focused = Magic.spell(data.spellAt(focus));
        if (focused != null) {
            String title = focused.name();
            Draw.text(g, font, title, -font.width(title) / 2, -22, Draw.withAlpha(focused.rarity().color(), fade));
            String element = focused.element().label() + (focused.chargeable() ? " - charge" : "");
            Draw.text(g, font, element, -font.width(element) / 2, -11, Draw.withAlpha(focused.element().color(), fade));
            String cost = "◆ " + Msg.number(Math.ceil(ClientMagic.cost(focused))) + "   " + String.format("%.1fs", focused.cooldownSeconds());
            Draw.text(g, font, cost, -font.width(cost) / 2, 0, Draw.withAlpha(0xFF6FE6EC, fade));
        } else {
            String hint = "Empty slot";
            Draw.text(g, font, hint, -font.width(hint) / 2, -10, Draw.withAlpha(Theme.MUTED, fade));
        }
        g.pose().popMatrix();

        // The description and the hint go beside the wheel, clear of the HUD boxes above and below it.
        int textX = width / 2 + OUTER + 16;
        int room = Math.min(150, width - textX - 8);
        if (closing < 0 && room > 60) {
            int y = height / 2 + 14;
            if (focused != null) {
                for (FormattedCharSequence line : Draw.wrap(font, Component.literal(focused.description()), room)) {
                    g.text(font, line, textX, y, Draw.withAlpha(Theme.MUTED, fade), false);
                    y += 10;
                }
                y += 6;
            }
            String help = toggled ? "Click a spell to select it" : "Release to select";
            g.text(font, help, textX, y, Draw.withAlpha(Theme.DIM, fade), false);
        }
    }
}
