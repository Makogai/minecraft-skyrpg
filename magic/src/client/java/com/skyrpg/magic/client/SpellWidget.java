package com.skyrpg.magic.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.Magic;
import com.skyrpg.magic.SpellDef;
import com.skyrpg.magic.SkyRpgMagic;
import com.skyrpg.magic.WandData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The selected spell: its icon with the cooldown sweeping over it, its name and mana cost (flashing red when you cannot afford it),
 * a charge bar while you hold a chargeable spell, and a pip for every slot of the wand. Shown while a wand is in hand; move it in
 * the HUD editor like any other box.
 */
final class SpellWidget implements HudWidget {
    static final int WIDTH = 128;
    static final int HEIGHT = 34;

    @Override
    public Identifier id() {
        return SkyRpgMagic.id("spell");
    }

    @Override
    public Component name() {
        return Component.literal("Wand spell");
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.BOTTOM_RIGHT;
    }

    @Override
    public int defaultX() {
        return -6;
    }

    @Override
    public int defaultY() {
        return -6;
    }

    @Override
    public boolean visible() {
        return ClientMagic.holdingWand();
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        WandData data = editing && !ClientMagic.holdingWand() ? null : ClientMagic.wandData();
        SpellDef spell = data == null ? Magic.spell("skyrpg_magic:firebolt") : Magic.spell(data.selectedSpell());
        int accent = spell == null ? Theme.DIM : spell.element().color();
        Draw.rounded(g, 0, 0, WIDTH, HEIGHT, 0xB00C0F16);
        g.fill(0, 2, 1, HEIGHT - 2, accent);
        // Icon slot with the cooldown sweep.
        g.fill(5, 5, 29, 29, 0xFF080B10);
        Draw.outline(g, 4, 4, 26, 26, Draw.withAlpha(accent, 0.55f));
        if (spell == null) {
            String none = data == null ? "" : "No spell";
            Draw.text(g, font, "-", 15, 13, Theme.DIM);
            Draw.text(g, font, none, 36, 7, Theme.MUTED);
            Draw.text(g, font, "Sneak + right click", 36, 18, Theme.DIM);
            pips(g, data);
            return;
        }
        Draw.item(g, ClientMagic.icon(spell), 7, 7, 1.25f);
        float cooldown = editing ? 0 : ClientMagic.cooldownFraction(spell);
        if (cooldown > 0) {
            int h = Math.round(24 * cooldown);
            g.fill(5, 5, 29, 5 + h, 0xB0000000);
            g.fill(5, 5 + h, 29, 6 + h, Draw.withAlpha(accent, 0.8f));
            String secs = String.format("%.1f", ClientMagic.cooldownLeft(spell) / 20.0);
            Draw.text(g, font, secs, 17 - font.width(secs) / 2, 13, 0xFFFFFFFF);
        } else if (ClientMagic.readyFlash(spell)) {
            g.fill(5, 5, 29, 29, 0x55FFFFFF);
        }
        Draw.text(g, font, spell.name(), 36, 5, spell.rarity().color());
        double cost = ClientMagic.cost(spell);
        boolean broke = !editing && ClientMagic.mana() < cost;
        float flash = ClientMagic.manaFlash();
        int costColor = broke || flash > 0 ? Draw.mix(0xFFFF6464, 0xFFFFFFFF, flash * 0.5f) : 0xFF6FE6EC;
        int shake = flash > 0 ? Math.round((float) Math.sin(ClientMagic.now() * 2.5) * 2 * flash) : 0;
        Draw.text(g, font, "◆ " + Msg.number(Math.ceil(cost)), 36 + shake, 16, costColor);
        if (broke || flash > 0) {
            Draw.text(g, font, "No mana", 36 + font.width("◆ " + Msg.number(Math.ceil(cost))) + 6 + shake, 16, Draw.withAlpha(0xFFFF6464, Math.max(0.5f, flash)));
        } else {
            Draw.text(g, font, String.format("%.1fs", spell.cooldownSeconds()), 36 + font.width("◆ " + Msg.number(Math.ceil(cost))) + 6, 16, Theme.DIM);
        }
        // Charge bar.
        float charge = ClientMagic.charging() == spell ? ClientMagic.chargeProgress(0) : 0;
        if (spell.chargeable()) {
            int barColor = charge >= 1f ? 0xFFFFFFFF : accent;
            Draw.bar(g, 36, 26, 86, 4, editing ? 0.6f : charge, barColor);
        }
        pips(g, data);
    }

    private static void pips(GuiGraphicsExtractor g, WandData data) {
        if (data == null) {
            return;
        }
        int count = data.slots().size();
        int x = WIDTH - 6 - count * 6;
        for (int i = 0; i < count; i++) {
            SpellDef slot = Magic.spell(data.spellAt(i));
            int color = slot == null ? 0xFF2A3040 : slot.element().color();
            int top = i == data.selected() ? 4 : 6;
            g.fill(x + i * 6, top, x + i * 6 + 4, 10, color);
        }
    }

    /** Clamp helper kept here so the ring code reads the same numbers. */
    static float clamp(float value) {
        return Mth.clamp(value, 0, 1);
    }
}
