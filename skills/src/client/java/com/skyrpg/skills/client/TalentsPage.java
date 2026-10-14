package com.skyrpg.skills.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.Skill;
import com.skyrpg.skills.SkyRpgSkills;
import com.skyrpg.skills.talent.Talent;
import com.skyrpg.skills.talent.TalentPayloads;
import com.skyrpg.skills.talent.Talents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Talents: a tab per skill, each talent as a card with its ranks, what a rank gives and what it needs. Click a card to
 * spend a point (one per skill level); "Reset" refunds the skill's points (free once a day, then coins).
 */
public final class TalentsPage implements MenuPage {
    private static final int TABS = 20;
    private static final int HEADER = 24;
    private static final int CARD_HEIGHT = 44;
    private static final int GAP = 6;

    private Identifier selected;
    private double scroll;

    @Override
    public Identifier id() {
        return SkyRpgSkills.id("talents");
    }

    @Override
    public Component title() {
        return Component.literal("Talents");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Spend a point per skill level on perks");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    @Override
    public int order() {
        return 11;
    }

    private List<Skill> skills() {
        List<Skill> skills = new ArrayList<>();
        for (Skill skill : ClientSkills.all()) {
            if (Talents.trees().containsKey(skill.id())) {
                skills.add(skill);
            }
        }
        return skills;
    }

    private Skill current(List<Skill> skills) {
        for (Skill skill : skills) {
            if (skill.id().equals(selected)) {
                return skill;
            }
        }
        return skills.isEmpty() ? null : skills.getFirst();
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        List<Skill> skills = skills();
        Skill skill = current(skills);
        if (skill == null) {
            Draw.text(g, font, "Waiting for talent data from the server...", ctx.x(), ctx.y() + 4, Theme.MUTED);
            return;
        }
        selected = skill.id();

        // Tabs: one per skill.
        int tx = ctx.x();
        for (Skill tab : skills) {
            String label = tab.name();
            int w = font.width(label) + 26;
            boolean on = tab == skill;
            boolean hover = ctx.hovered(tx, ctx.y(), w, TABS - 4);
            int color = 0xFF000000 | tab.color().getValue();
            Draw.rounded(g, tx, ctx.y(), w, TABS - 4, on ? Draw.withAlpha(color, 0.22f) : hover ? Theme.CARD_HOVER : Theme.CARD);
            Draw.item(g, new ItemStack(BuiltInRegistries.ITEM.getValue(tab.icon())), tx + 3, ctx.y(), 1f);
            Draw.text(g, font, label, tx + 21, ctx.y() + 4, on ? color : hover ? Theme.TEXT : Theme.MUTED);
            if (hover) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            tx += w + 4;
        }

        // Header: points to spend, and the reset button.
        Minecraft minecraft = Minecraft.getInstance();
        int level = SkillLevels.level(minecraft.player, skill.id());
        int points = Talents.points(minecraft.player, skill.id());
        int hy = ctx.y() + TABS;
        Draw.text(g, font, Component.literal(skill.name() + " " + Msg.roman(level) + "  ·  ").withColor(Theme.MUTED)
                .append(Component.literal(points + (points == 1 ? " point" : " points") + " to spend").withColor(points > 0 ? Theme.ACCENT : Theme.DIM)),
                ctx.x(), hy + 6, Theme.TEXT);
        String reset = "Reset";
        int rw = font.width(reset) + 14;
        int rx = ctx.x() + ctx.width() - rw;
        boolean resetHover = ctx.hovered(rx, hy + 2, rw, 14);
        boolean anySpent = Talents.spent(minecraft.player, skill.id()) > 0;
        Draw.panel(g, rx, hy + 2, rw, 14, resetHover && anySpent ? Draw.withAlpha(Theme.RED, 0.18f) : Theme.TRACK,
                resetHover && anySpent ? Theme.RED : Theme.CARD_BORDER);
        Draw.text(g, font, reset, rx + 7, hy + 5, anySpent ? (resetHover ? Theme.RED : Theme.TEXT) : Theme.DIM);
        if (resetHover) {
            ctx.tooltip(List.of(Component.literal("Reset " + skill.name() + " talents").withColor(Theme.TEXT),
                    Component.literal("Gives back every point. Free once a day,").withColor(Theme.MUTED),
                    Component.literal("then " + Msg.number(Talents.RESET_COST) + " coins.").withColor(Theme.MUTED)), Theme.RED);
            g.requestCursor(CursorTypes.POINTING_HAND);
        }

        // Cards.
        List<Talent> talents = Talents.trees().get(skill.id());
        int top = hy + HEADER;
        int height = ctx.y() + ctx.height() - top;
        int columns = ctx.width() >= 320 ? 2 : 1;
        int cardWidth = (ctx.width() - GAP * (columns - 1)) / columns;
        int rows = (talents.size() + columns - 1) / columns;
        int content = rows * (CARD_HEIGHT + GAP) - GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        g.enableScissor(ctx.x(), top, ctx.x() + ctx.width(), top + height);
        int color = 0xFF000000 | skill.color().getValue();
        for (int i = 0; i < talents.size(); i++) {
            Talent talent = talents.get(i);
            int x = ctx.x() + (i % columns) * (cardWidth + GAP);
            int y = top + (i / columns) * (CARD_HEIGHT + GAP) - (int) Math.round(scroll);
            boolean hover = ctx.mouseY() >= top && ctx.mouseY() < top + height && ctx.hovered(x, y, cardWidth, CARD_HEIGHT);
            drawCard(ctx, talent, skill, level, points, x, y, cardWidth, hover, color);
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, top, height, content, scroll, Theme.DIM);
        }
    }

    private void drawCard(PageContext ctx, Talent talent, Skill skill, int level, int points, int x, int y, int w, boolean hover, int color) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int rank = Talents.rank(Minecraft.getInstance().player, skill.id(), talent.id());
        boolean locked = level < talent.level();
        boolean maxed = rank >= talent.ranks();
        boolean canSpend = !locked && !maxed && points > 0;
        int border = maxed ? Draw.mix(Theme.CARD_BORDER, color, 0.7f) : canSpend && hover ? Theme.ACCENT : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, CARD_HEIGHT, hover ? Theme.CARD_HOVER : Theme.CARD, border);
        Draw.rounded(g, x + 5, y + 5, 22, 22, Draw.withAlpha(rank > 0 ? color : 0xFF2A3140, rank > 0 ? 0.25f : 1f));
        Draw.item(g, new ItemStack(BuiltInRegistries.ITEM.getValue(talent.icon())), x + 8, y + 8, 1f);
        int textX = x + 33;
        int textWidth = w - 33 - 6;
        Draw.fitText(g, font, Component.literal(talent.name()), textX, y + 6, textWidth - 30, locked ? Theme.DIM : rank > 0 ? color : Theme.TEXT);
        Draw.rightText(g, font, rank + "/" + talent.ranks(), x + w - 6, y + 6, maxed ? Theme.GREEN : rank > 0 ? Theme.ACCENT : Theme.DIM);
        Draw.fitText(g, font, Component.literal(talent.description()), textX, y + 17, textWidth, Theme.MUTED);
        // Rank pips, or what unlocks it.
        if (locked) {
            Draw.fitText(g, font, Component.literal("Needs " + skill.name() + " " + talent.level()), textX, y + 29, textWidth, Theme.RED);
        } else {
            for (int r = 0; r < talent.ranks(); r++) {
                int px = textX + r * 9;
                g.fill(px, y + 31, px + 7, y + 36, r < rank ? color : Theme.TRACK);
            }
        }
        if (hover) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(talent.name()).withColor(color));
            lines.add(Component.literal(talent.description()).withColor(Theme.MUTED));
            lines.add(Component.literal("Rank " + rank + " of " + talent.ranks()).withColor(Theme.DIM));
            if (locked) {
                lines.add(Component.literal("Unlocks at " + skill.name() + " " + talent.level()).withColor(Theme.RED));
            } else if (maxed) {
                lines.add(Component.literal("Maxed").withColor(Theme.GREEN));
            } else if (points > 0) {
                lines.add(Component.literal("Click to spend a point").withColor(Theme.ACCENT));
            } else {
                lines.add(Component.literal("No points left: level up " + skill.name()).withColor(Theme.RED));
            }
            ctx.tooltip(lines, color);
            if (canSpend) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        List<Skill> skills = skills();
        Skill skill = current(skills);
        if (skill == null) {
            return false;
        }
        Font font = ctx.font();
        int tx = ctx.x();
        for (Skill tab : skills) {
            int w = font.width(tab.name()) + 26;
            if (mouseX >= tx && mouseX < tx + w && mouseY >= ctx.y() && mouseY < ctx.y() + TABS - 4) {
                selected = tab.id();
                scroll = 0;
                click(1.2f);
                return true;
            }
            tx += w + 4;
        }
        int hy = ctx.y() + TABS;
        int rw = font.width("Reset") + 14;
        int rx = ctx.x() + ctx.width() - rw;
        if (mouseX >= rx && mouseX < rx + rw && mouseY >= hy + 2 && mouseY < hy + 16) {
            ClientPlayNetworking.send(new TalentPayloads.Action(skill.id(), "", true));
            click(0.8f);
            return true;
        }
        List<Talent> talents = Talents.trees().get(skill.id());
        int top = hy + HEADER;
        int columns = ctx.width() >= 320 ? 2 : 1;
        int cardWidth = (ctx.width() - GAP * (columns - 1)) / columns;
        if (mouseY < top) {
            return false;
        }
        for (int i = 0; i < talents.size(); i++) {
            int x = ctx.x() + (i % columns) * (cardWidth + GAP);
            int y = top + (i / columns) * (CARD_HEIGHT + GAP) - (int) Math.round(scroll);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                ClientPlayNetworking.send(new TalentPayloads.Action(skill.id(), talents.get(i).id(), false));
                click(1.4f);
                return true;
            }
        }
        return false;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scroll -= amount * 24;
        return true;
    }
}
