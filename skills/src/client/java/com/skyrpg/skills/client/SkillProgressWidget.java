package com.skyrpg.skills.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.Skill;
import com.skyrpg.skills.SkyRpgSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The skill you're training right now: level, progress bar, last gain and XP per hour. Fades out when
 * idle. It widens a little for long lines; numbers that still don't fit get their short form
 * ("1.23M / 3.7M").
 */
final class SkillProgressWidget implements HudWidget {
    private static final int WIDTH = 150;
    private static final int MAX_WIDTH = 200;
    private static final int HEIGHT = 34;
    private static final int TEXT_X = 26;
    private static final int RIGHT_PAD = 7;
    private static final long SHOW_FOR_MS = 8_000;
    private static final long FADE_MS = 1_000;

    /** The four texts on the widget: title and gain on top, progress and rate below (rate may be empty). */
    private record Lines(String title, String gain, String progress, String rate) {
        int width(Font font) {
            int top = font.width(title) + 8 + font.width(gain);
            int bottom = font.width(progress) + (rate.isEmpty() ? 0 : 8 + font.width(rate));
            return TEXT_X + Math.max(top, bottom) + RIGHT_PAD;
        }
    }

    @Override
    public Identifier id() {
        return SkyRpgSkills.id("skill_progress");
    }

    @Override
    public Component name() {
        return Component.literal("Skill progress");
    }

    private static Skill skill() {
        Skill skill = SkillTracker.lastSkill();
        return skill == null && !ClientSkills.all().isEmpty() ? ClientSkills.all().getFirst() : skill;
    }

    /** What to show, with full numbers if that fits in {@link #MAX_WIDTH}, else short ones. */
    private static Lines lines(Font font, Skill skill, boolean editing) {
        Minecraft minecraft = Minecraft.getInstance();
        double xp = PlayerSkills.getXp(minecraft.player, skill);
        int level = skill.levelFor(xp);
        boolean maxed = level >= skill.maxLevel();
        boolean live = SkillTracker.millisSinceGain() < SHOW_FOR_MS && !editing;
        double rate = SkillTracker.xpPerHour(skill);
        String title = skill.name() + " " + Msg.roman(level);
        Lines full = new Lines(title, live ? "+" + Msg.number(SkillTracker.lastAmount()) : "+4",
                maxed ? "Maxed" : Msg.number(skill.xpIntoLevel(xp)) + " / " + Msg.number(skill.xpForNextLevel(level)),
                rate > 0 ? Msg.number(Math.round(rate)) + "/h" : "");
        if (full.width(font) <= MAX_WIDTH) {
            return full;
        }
        return new Lines(title, Draw.compactNumbers(full.gain()), Draw.compactNumbers(full.progress()), Draw.compactNumbers(full.rate()));
    }

    @Override
    public int width(Font font) {
        Skill skill = skill();
        if (skill == null || Minecraft.getInstance().player == null) {
            return WIDTH;
        }
        return Math.max(WIDTH, Math.min(MAX_WIDTH, lines(font, skill, false).width(font)));
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public HudAnchor defaultAnchor() {
        // Top left under the purse: vanilla keeps that corner free, and the sidebar and events stay on the right.
        return HudAnchor.TOP_LEFT;
    }

    @Override
    public int defaultX() {
        return 4;
    }

    @Override
    public int defaultY() {
        return 24;
    }

    @Override
    public boolean visible() {
        return SkillTracker.millisSinceGain() < SHOW_FOR_MS;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Minecraft minecraft = Minecraft.getInstance();
        Skill skill = skill();
        int width = width(font);
        if (skill == null || minecraft.player == null) {
            Draw.panel(g, 0, 0, width, HEIGHT, 0xE0121620, Theme.BORDER);
            Draw.text(g, font, "Skill progress", 8, 13, Theme.MUTED);
            return;
        }
        long since = SkillTracker.millisSinceGain();
        float alpha = editing ? 1 : Math.min(1, (SHOW_FOR_MS - since) / (float) FADE_MS);
        int color = 0xFF000000 | skill.color().getValue();
        double xp = PlayerSkills.getXp(minecraft.player, skill);
        int level = skill.levelFor(xp);
        boolean maxed = level >= skill.maxLevel();
        float progress = maxed ? 1 : (float) (skill.xpIntoLevel(xp) / skill.xpForNextLevel(level));
        Lines lines = lines(font, skill, editing);
        int right = width - RIGHT_PAD;
        int room = right - TEXT_X;

        Draw.panel(g, 0, 0, width, HEIGHT, Draw.withAlpha(0xE0121620, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, color, 0.4f), alpha));
        g.item(ClientSkills.icon(skill), 6, 5);
        Draw.rightText(g, font, lines.gain(), right, 5, Draw.withAlpha(Theme.GREEN, alpha));
        Draw.fitText(g, font, lines.title(), TEXT_X, 5, room - font.width(lines.gain()) - 6, Draw.withAlpha(color, alpha));
        int rateWidth = 0;
        if (!lines.rate().isEmpty()) {
            Draw.rightText(g, font, lines.rate(), right, 14, Draw.withAlpha(Theme.DIM, alpha));
            rateWidth = font.width(lines.rate()) + 6;
        }
        Draw.fitText(g, font, lines.progress(), TEXT_X, 14, room - rateWidth, Draw.withAlpha(Theme.MUTED, alpha));
        Draw.bar(g, 6, HEIGHT - 9, width - 12, 5, progress, maxed ? Theme.ACCENT : color);
    }
}
