package com.skyrpg.enchants.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.enchants.SkyRpgEnchants;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

/**
 * The enchanting altar's magic, drawn in screen space: a soft glow, two rune rings turning against each other, runes of
 * the enchanting table's alphabet circling the item, motes rising off the ring, and a burst when something is bound.
 * Purely visual: one instance per screen, advanced once per frame.
 */
final class ArcaneFx {
    static final Identifier OUTER = SkyRpgEnchants.id("textures/gui/enchanting/rune_ring_outer.png");
    static final Identifier INNER = SkyRpgEnchants.id("textures/gui/enchanting/rune_ring_inner.png");
    static final Identifier GLOW = SkyRpgEnchants.id("textures/gui/enchanting/glow.png");
    static final Identifier SPARK = SkyRpgEnchants.id("textures/gui/enchanting/spark.png");
    /** The enchanting table's alphabet. */
    static final Style RUNES = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("alt")));
    static final int LAVENDER = 0xFFB98CFF;
    static final int CYAN = 0xFF7AD4FF;
    static final int GOLD = 0xFFFFD27A;
    static final int VIOLET = 0xFF7B3FE4;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final int ORBITERS = 16;
    private static final long FLASH_MILLIS = 1300;

    private static final class Mote {
        float x;
        float y;
        float vx;
        float vy;
        float age;
        float life;
        float size;
        int color;
        char glyph;
    }

    private final List<Mote> motes = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final char[] orbit = new char[ORBITERS];
    private final float[] orbitOffset = new float[ORBITERS];
    private long last = Util.getMillis();
    private float spawnDebt;
    private long flashAt = -FLASH_MILLIS;
    private int flashColor = GOLD;
    private long swapAt;

    ArcaneFx() {
        for (int i = 0; i < ORBITERS; i++) {
            orbit[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            orbitOffset[i] = random.nextFloat();
        }
    }

    static float seconds() {
        return (Util.getMillis() % 3_600_000L) / 1000f;
    }

    /** Something was bound (or merged, or broken down): a ring of light and a burst of motes from the centre. */
    void flash(float cx, float cy, int color) {
        flashAt = Util.getMillis();
        flashColor = color;
        for (int i = 0; i < 70; i++) {
            Mote mote = new Mote();
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 40 + random.nextFloat() * 110;
            mote.x = cx;
            mote.y = cy;
            mote.vx = (float) Math.cos(angle) * speed;
            mote.vy = (float) Math.sin(angle) * speed;
            mote.life = 0.7f + random.nextFloat() * 0.8f;
            mote.size = 3 + random.nextFloat() * 5;
            mote.color = random.nextFloat() < 0.6f ? color : random.nextBoolean() ? 0xFFFFFFFF : LAVENDER;
            mote.glyph = random.nextFloat() < 0.35f ? ALPHABET.charAt(random.nextInt(ALPHABET.length())) : 0;
            motes.add(mote);
        }
    }

    /** Advances the motes; {@code rate} is new motes per second off the ring (more while something is about to be bound). */
    void update(float cx, float cy, float radius, float rate) {
        long now = Util.getMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        spawnDebt += rate * dt;
        while (spawnDebt >= 1) {
            spawnDebt--;
            Mote mote = new Mote();
            double angle = random.nextDouble() * Math.PI * 2;
            float r = radius * (0.75f + random.nextFloat() * 0.2f);
            mote.x = cx + (float) Math.cos(angle) * r;
            mote.y = cy + (float) Math.sin(angle) * r;
            mote.vx = (cx - mote.x) * 0.15f + (random.nextFloat() - 0.5f) * 8;
            mote.vy = -14 - random.nextFloat() * 18;
            mote.life = 1.4f + random.nextFloat() * 1.4f;
            mote.size = 2 + random.nextFloat() * 4;
            float pick = random.nextFloat();
            mote.color = pick < 0.5f ? LAVENDER : pick < 0.85f ? CYAN : GOLD;
            mote.glyph = random.nextFloat() < 0.25f ? ALPHABET.charAt(random.nextInt(ALPHABET.length())) : 0;
            motes.add(mote);
        }
        for (Iterator<Mote> it = motes.iterator(); it.hasNext(); ) {
            Mote mote = it.next();
            mote.age += dt;
            if (mote.age >= mote.life) {
                it.remove();
                continue;
            }
            mote.x += mote.vx * dt;
            mote.y += mote.vy * dt;
            mote.vx *= 1 - dt * 1.6f;
            mote.vy = mote.vy * (1 - dt * 1.2f) - dt * 4;
        }
        // Now and then a circling rune changes into another.
        if (now - swapAt > 180) {
            swapAt = now;
            orbit[random.nextInt(ORBITERS)] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
    }

    /** Glow, rings and circling runes: the altar behind the item. {@code accent} tints the inner ring. */
    void drawAltar(GuiGraphicsExtractor g, Font font, float cx, float cy, float radius, int accent) {
        float t = seconds();
        float pulse = 0.5f + 0.5f * Mth.sin(t * 1.7f);
        texture(g, GLOW, cx, cy, radius * 2.7f, 0, Draw.withAlpha(VIOLET, 0.30f + 0.18f * pulse), 128);
        texture(g, OUTER, cx, cy, radius * 2, t * 0.12f, Draw.withAlpha(LAVENDER, 0.85f), 256);
        texture(g, INNER, cx, cy, radius * 1.42f, -t * 0.32f, Draw.withAlpha(Draw.mix(CYAN, accent, 0.45f), 0.8f), 256);
        texture(g, GLOW, cx, cy, radius * 0.9f, 0, Draw.withAlpha(Draw.mix(LAVENDER, accent, 0.5f), 0.22f + 0.14f * pulse), 128);
        for (int i = 0; i < ORBITERS; i++) {
            double angle = (i / (double) ORBITERS) * Math.PI * 2 + t * 0.45;
            float r = radius * (0.6f + 0.05f * Mth.sin(t * 2 + i));
            int x = Math.round(cx + (float) Math.cos(angle) * r) - 3;
            int y = Math.round(cy + (float) Math.sin(angle) * r) - 4;
            float flicker = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(t * 3.1f + orbitOffset[i] * 12));
            g.text(font, Component.literal(String.valueOf(orbit[i])).withStyle(RUNES), x, y,
                    Draw.withAlpha(i % 3 == 0 ? CYAN : LAVENDER, flicker), false);
        }
    }

    /** The rising motes (and a flash's burst), over the item. */
    void drawMotes(GuiGraphicsExtractor g, Font font) {
        for (Mote mote : motes) {
            float life = mote.age / mote.life;
            float alpha = life < 0.15f ? life / 0.15f : 1 - (life - 0.15f) / 0.85f;
            if (mote.glyph != 0) {
                g.text(font, Component.literal(String.valueOf(mote.glyph)).withStyle(RUNES), Math.round(mote.x) - 3, Math.round(mote.y) - 4,
                        Draw.withAlpha(mote.color, alpha * 0.9f), false);
            } else {
                texture(g, SPARK, mote.x, mote.y, mote.size * (1 - life * 0.4f), 0, Draw.withAlpha(mote.color, alpha), 16);
            }
        }
    }

    /** The light of a flash: an expanding ring and a fading bloom. */
    void drawFlash(GuiGraphicsExtractor g, float cx, float cy, float radius) {
        float t = (Util.getMillis() - flashAt) / (float) FLASH_MILLIS;
        if (t < 0 || t >= 1) {
            return;
        }
        float eased = Draw.easeOut(t);
        texture(g, OUTER, cx, cy, radius * 2 * (1 + eased * 0.9f), t * 1.5f, Draw.withAlpha(flashColor, (1 - t) * 0.9f), 256);
        texture(g, GLOW, cx, cy, radius * (1.2f + eased * 1.6f), 0, Draw.withAlpha(0xFFFFFFFF, (1 - t) * (1 - t) * 0.8f), 128);
    }

    /** How much the item swells right after a flash (1 = normal). */
    float flashScale() {
        float t = (Util.getMillis() - flashAt) / 500f;
        return t < 0 || t >= 1 ? 1 : 1 + 0.25f * Mth.sin(t * (float) Math.PI);
    }

    /** A square texture centred on (cx, cy), {@code size} across, turned by {@code angle} radians and tinted. */
    static void texture(GuiGraphicsExtractor g, Identifier texture, float cx, float cy, float size, float angle, int color, int textureSize) {
        int s = Math.max(1, Math.round(size));
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        if (angle != 0) {
            g.pose().rotate(angle);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, texture, -s / 2, -s / 2, 0, 0, s, s, textureSize, textureSize, textureSize, textureSize, color);
        g.pose().popMatrix();
    }
}
