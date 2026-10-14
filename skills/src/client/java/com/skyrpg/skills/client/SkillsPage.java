package com.skyrpg.skills.client;

import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.Skill;
import com.skyrpg.skills.SkyRpgSkills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every skill as a card: level, animated progress bar and XP, with details on hover. */
public final class SkillsPage implements MenuPage {
    private static final int SUMMARY_HEIGHT = 30;
    private static final int CARD_HEIGHT = 58;
    private static final int GAP = 6;
    /** Below this the cards go one per row. */
    private static final int MIN_CARD_WIDTH = 140;
    private double scroll;

    @Override
    public Identifier id() {
        return SkyRpgSkills.id("skills");
    }

    @Override
    public Component title() {
        return Component.literal("Skills");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Level up by farming, mining, chopping and fighting");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        List<Skill> skills = ClientSkills.all();
        if (skills.isEmpty()) {
            Draw.text(g, font, "Waiting for skill data from the server...", ctx.x(), ctx.y() + 4, Theme.MUTED);
            return;
        }
        drawSummary(ctx);

        int areaY = ctx.y() + SUMMARY_HEIGHT + GAP;
        int areaHeight = ctx.height() - SUMMARY_HEIGHT - GAP;
        int columns = (ctx.width() - GAP) / 2 >= MIN_CARD_WIDTH ? 2 : 1;
        int cardWidth = (ctx.width() - GAP * (columns - 1)) / columns;
        int rows = (skills.size() + columns - 1) / columns;
        int contentHeight = rows * (CARD_HEIGHT + GAP) - GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - areaHeight));
        boolean mouseInArea = ctx.mouseY() >= areaY && ctx.mouseY() < areaY + areaHeight;

        Skill hovered = null;
        g.enableScissor(ctx.x(), areaY, ctx.x() + ctx.width(), areaY + areaHeight);
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            float appear = Draw.easeOut((ctx.age() - i * 0.05f) / 0.3f);
            int x = ctx.x() + (i % columns) * (cardWidth + GAP);
            int y = areaY + (i / columns) * (CARD_HEIGHT + GAP) - (int) scroll + Math.round((1 - appear) * 6);
            boolean isHovered = mouseInArea && ctx.hovered(x, y, cardWidth, CARD_HEIGHT);
            drawCard(ctx, skill, i, x, y, cardWidth, isHovered);
            if (isHovered) {
                hovered = skill;
            }
        }
        g.disableScissor();

        if (contentHeight > areaHeight) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, areaY, areaHeight, contentHeight, scroll, Theme.DIM);
        }
        if (hovered != null) {
            drawTooltip(ctx, hovered);
        }
    }

    private void drawSummary(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, ctx.x(), ctx.y(), ctx.width(), SUMMARY_HEIGHT, Theme.CARD, Theme.CARD_BORDER);
        String[][] entries = {
                {"SKILL AVERAGE", String.format(Locale.ROOT, "%.1f", ClientSkills.average(ctx.player()))},
                {"TOTAL LEVELS", Integer.toString(ClientSkills.totalLevels(ctx.player()))},
                {"TOTAL XP", Msg.number(ClientSkills.totalXp(ctx.player()))},
        };
        int column = ctx.width() / entries.length;
        for (int i = 0; i < entries.length; i++) {
            int x = ctx.x() + i * column + 10;
            if (i > 0) {
                g.fill(ctx.x() + i * column, ctx.y() + 7, ctx.x() + i * column + 1, ctx.y() + SUMMARY_HEIGHT - 7, Theme.CARD_BORDER);
            }
            int room = column - 16;
            boolean cut = Draw.fitText(g, font, entries[i][0], x, ctx.y() + 6, room, Theme.DIM);
            cut |= Draw.fitNumber(g, font, entries[i][1], x, ctx.y() + 17, room, Draw.Align.LEFT, i == 0 ? Theme.ACCENT : Theme.TEXT);
            if (cut) {
                Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), ctx.x() + i * column, ctx.y(), column, SUMMARY_HEIGHT,
                        List.of(Component.literal(entries[i][0]).withColor(Theme.MUTED), Component.literal(entries[i][1]).withColor(Theme.TEXT)), Theme.AQUA);
            }
        }
    }

    private void drawCard(PageContext ctx, Skill skill, int index, int x, int y, int width, boolean hovered) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = 0xFF000000 | skill.color().getValue();
        double xp = PlayerSkills.getXp(ctx.player(), skill);
        int level = skill.levelFor(xp);
        boolean maxed = level >= skill.maxLevel();

        Draw.panel(g, x, y, width, CARD_HEIGHT, hovered ? Theme.CARD_HOVER : Theme.CARD,
                hovered ? Draw.mix(Theme.CARD_BORDER, color, 0.7f) : Theme.CARD_BORDER);
        Draw.rounded(g, x + 8, y + 8, 22, 22, Draw.withAlpha(color, hovered ? 0.22f : 0.14f));
        g.item(ClientSkills.icon(skill), x + 11, y + 11);

        // The big numeral on the right keeps its place (shrinking a little for long ones like XXXVIII);
        // the name and level line take the room left of it.
        int textRight = x + width - 8;
        if (level > 0) {
            Component roman = Component.literal(Msg.roman(level));
            int romanRoom = Math.max(24, (width - 44) / 2);
            float scale = Draw.fitScale(font, roman, romanRoom, 1.5f, 1f);
            int romanWidth = Math.round(font.width(roman) * scale);
            Draw.fit(g, font, roman, x + width - 10, y + 9, romanRoom, 1.5f, 1f, Draw.Align.RIGHT, maxed ? Theme.ACCENT : Theme.TEXT, false);
            textRight = x + width - 10 - Math.min(romanWidth, romanRoom) - 6;
        }
        Draw.fitText(g, font, skill.name(), x + 36, y + 9, textRight - x - 36, color);
        Draw.fitText(g, font, maxed ? "Maxed" : "Level " + level, x + 36, y + 20, textRight - x - 36, Theme.MUTED);

        float progress = maxed ? 1 : (float) (skill.xpIntoLevel(xp) / skill.xpForNextLevel(level));
        float grow = Draw.easeOut((ctx.age() - index * 0.05f) / 0.7f);
        Draw.bar(g, x + 8, y + 33, width - 16, 6, progress * grow, maxed ? Theme.ACCENT : color);
        // "1,234,567 / 3,700,000 XP" when it fits next to the percentage, else "1.23M / 3.7M XP".
        String percent = maxed ? "MAX" : Math.round(progress * 100) + "%";
        Draw.rightText(g, font, percent, x + width - 8, y + 44, Theme.MUTED);
        String detail = maxed ? Msg.number(xp) + " XP" : Msg.number(skill.xpIntoLevel(xp)) + " / " + Msg.number(skill.xpForNextLevel(level)) + " XP";
        Draw.fitNumber(g, font, detail, x + 8, y + 44, width - 16 - font.width(percent) - 6, Draw.Align.LEFT, Theme.DIM);
    }

    private void drawTooltip(PageContext ctx, Skill skill) {
        double xp = PlayerSkills.getXp(ctx.player(), skill);
        int level = skill.levelFor(xp);
        int color = 0xFF000000 | skill.color().getValue();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(skill.name() + " " + Msg.roman(level)).withColor(color));
        if (!skill.description().isEmpty()) {
            lines.add(Component.literal(skill.description()).withColor(Theme.MUTED));
        }
        if (!skill.rewardsPerLevel().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal("Every level").withColor(Theme.DIM));
            skill.rewardsPerLevel().forEach((statId, perLevel) -> {
                StatType stat = StatTypes.get(statId);
                if (stat != null) {
                    lines.add(Component.literal("  " + stat.formatBonus(perLevel) + " " + stat.symbol() + " " + stat.name()).withColor(stat.color()));
                }
            });
        }
        lines.add(Component.empty());
        lines.add(Component.literal("Total XP  ").withColor(Theme.DIM).append(Component.literal(Msg.number(xp)).withColor(Theme.TEXT)));
        if (level >= skill.maxLevel()) {
            lines.add(Component.literal("Max level reached").withColor(Theme.ACCENT));
        } else {
            double remaining = skill.xpForNextLevel(level) - skill.xpIntoLevel(xp);
            lines.add(Component.literal("To " + Msg.roman(level + 1) + "  ").withColor(Theme.DIM)
                    .append(Component.literal(Msg.number(remaining) + " XP").withColor(Theme.TEXT)));
        }
        ctx.tooltip(lines, color);
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scroll -= amount * 24;
        return true;
    }
}
