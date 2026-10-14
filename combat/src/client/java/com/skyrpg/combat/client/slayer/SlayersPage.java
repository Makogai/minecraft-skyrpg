package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.SlayerPayloads;
import com.skyrpg.combat.slayer.SlayerState;
import com.skyrpg.combat.slayer.Slayers;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The K menu's Slayers page. The overview shows a card for each slayer line (level, XP bar, boss kills per tier, the
 * running quest); click one for its detail: the level track (what each level gives and unlocks), the four tiers (the
 * boss, cost, rewards, attacks and defences) and your own stats. Static facts come from {@link SlayerPayloads.PageData}
 * (asked for each time the page opens; what a level unlocks is read from the bench recipes server-side); your XP,
 * kills and quest come from the synced {@link SlayerState}.
 */
public final class SlayersPage implements MenuPage {
    static volatile List<SlayerPayloads.PageLine> data = List.of();
    static volatile boolean received;

    private static final int GAP = 6;
    private static final int CARD_H = 80;
    private static final int HEADER_H = 38;

    /** The line shown in detail, or null for the overview. */
    private String selected;
    private final Scroller overviewScroll = new Scroller();
    private final Scroller trackScroll = new Scroller();
    private final Scroller infoScroll = new Scroller();
    private final List<Hit> hits = new ArrayList<>();
    private int overviewHeight;
    private int trackHeight;
    private int infoHeight;
    private int trackX;
    private int trackY;
    private int trackW;
    private int trackH;
    private int infoX;
    private int infoY;
    private int infoW;
    private int infoH;

    private record Hit(int x, int y, int w, int h, String line) {
    }

    /** One wrapped line of text in a tier card. */
    private record Row(FormattedCharSequence text, int indent, int color, int height) {
    }

    /** A scroll position that eases towards where the wheel sent it. */
    private static final class Scroller {
        double target;
        double pos;
        long last = Util.getMillis();

        double update(int max) {
            long now = Util.getMillis();
            double dt = Math.min(0.1, (now - last) / 1000.0);
            last = now;
            target = Mth.clamp(target, 0, Math.max(0, max));
            pos += (target - pos) * (1 - Math.exp(-dt * 16));
            if (Math.abs(target - pos) < 0.3) {
                pos = target;
            }
            return pos;
        }

        void reset() {
            target = 0;
            pos = 0;
        }
    }

    @Override
    public Identifier id() {
        return SkyRpgCombat.id("slayers");
    }

    @Override
    public Component title() {
        return Component.literal("Slayers");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your bosses, levels and what they unlock");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WITHER_SKELETON_SKULL);
    }

    @Override
    public int order() {
        return 12;
    }

    @Override
    public void opened() {
        selected = null;
        overviewScroll.reset();
        trackScroll.reset();
        infoScroll.reset();
        if (ClientPlayNetworking.canSend(SlayerPayloads.RequestPage.TYPE)) {
            ClientPlayNetworking.send(new SlayerPayloads.RequestPage());
        }
    }

    @Override
    public boolean showSubpage(String name) {
        for (SlayerPayloads.PageLine line : data) {
            if (line.id().equals(name)) {
                select(line.id());
                return true;
            }
        }
        // The data may not have arrived yet: remember the name and let the render check it.
        selected = name;
        trackScroll.reset();
        infoScroll.reset();
        return true;
    }

    private void select(String line) {
        selected = line;
        trackScroll.reset();
        infoScroll.reset();
    }

    @Override
    public boolean canGoBack() {
        return selected != null;
    }

    @Override
    public boolean back(PageContext ctx) {
        if (selected == null) {
            return false;
        }
        selected = null;
        return true;
    }

    @Override
    public Component hint() {
        return Component.literal(selected == null ? "Click a slayer for details" : "Scroll the level track and tiers");
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(PageContext ctx) {
        hits.clear();
        SlayerPayloads.PageLine detail = null;
        if (selected != null) {
            for (SlayerPayloads.PageLine line : data) {
                if (line.id().equals(selected)) {
                    detail = line;
                }
            }
            if (detail == null && received) {
                selected = null;
            }
        }
        if (data.isEmpty()) {
            Draw.text(ctx.graphics(), ctx.font(), received ? "No slayers are set up." : "Loading slayers...", ctx.x() + 4, ctx.y() + 6, Theme.MUTED);
            return;
        }
        SlayerState state = ctx.player().getAttachedOrElse(Slayers.STATE, SlayerState.EMPTY);
        if (detail != null) {
            renderDetail(ctx, detail, state);
        } else {
            renderOverview(ctx, state);
        }
    }

    // ------------------------------------------------------------------ overview

    private void renderOverview(PageContext ctx, SlayerState state) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();
        double scroll = overviewScroll.update(overviewHeight - h);
        int cy = y - (int) Math.round(scroll);
        int start = cy;
        g.enableScissor(x, y, x + w, y + h);

        int totalKills = 0;
        int totalTokens = 0;
        for (SlayerPayloads.PageLine line : data) {
            for (int t = 1; t <= line.tiers().size(); t++) {
                totalKills += state.kills(line.id(), t);
            }
            totalTokens += state.tokens(line.id());
        }
        Draw.text(g, font, "Slay the bosses that rise from each kind of mob", x + 2, cy + 1, Theme.MUTED);
        Draw.rightText(g, font, Msg.number(totalKills) + " bosses slain  |  " + Msg.number(totalTokens) + " tokens", x + w - 2, cy + 1, Theme.DIM);
        cy += 14;

        int cols = w >= 330 ? 2 : 1;
        int cardW = (w - GAP * (cols - 1)) / cols;
        int cardH = CARD_H;
        if (cols == 2) {
            int rows = (data.size() + 1) / 2;
            cardH = Mth.clamp((h - 14 - GAP * (rows - 1)) / rows, CARD_H, CARD_H + 22);
        }
        for (int i = 0; i < data.size(); i++) {
            int cx = x + (i % cols) * (cardW + GAP);
            int cyy = cy + (i / cols) * (cardH + GAP);
            drawCard(ctx, data.get(i), state, cx, cyy, cardW, cardH);
        }
        cy += ((data.size() + cols - 1) / cols) * (cardH + GAP);
        overviewHeight = cy - start;
        g.disableScissor();
        if (overviewHeight > h) {
            Draw.scrollbar(g, x + w + 4, y, h, overviewHeight, scroll, Theme.DIM);
        }
    }

    private void drawCard(PageContext ctx, SlayerPayloads.PageLine line, SlayerState state, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = line.color();
        boolean hovered = ctx.hovered(x, y, w, h) && ctx.hovered(ctx.x(), ctx.y(), ctx.width(), ctx.height());
        Draw.panel(g, x, y, w, h, hovered ? Theme.CARD_HOVER : Theme.CARD, Draw.mix(Theme.CARD_BORDER, color, hovered ? 0.85f : 0.3f));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, Draw.withAlpha(color, hovered ? 0.9f : 0.5f));
        hits.add(new Hit(x, y, w, h, line.id()));
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
        double xp = state.xp(line.id());
        int level = level(line, xp);
        int max = line.levels().size();

        // icon box
        int box = 36;
        Draw.panel(g, x + 6, y + 7, box, box, Draw.mix(Theme.TRACK, color, 0.18f), Draw.mix(Theme.CARD_BORDER, color, 0.6f));
        Draw.item(g, line.icon(), x + 6 + 2, y + 7 + 2, 2f);

        int tx = x + box + 13;
        int room = w - (tx - x) - 6;
        Draw.fitText(g, font, line.name(), tx, y + 7, room - 40, color);
        String levelText = level >= max ? "MAX" : "Lv " + level;
        int pillW = font.width(levelText) + 8;
        Draw.rounded(g, x + w - 6 - pillW, y + 6, pillW, 11, Draw.withAlpha(color, 0.25f));
        Draw.text(g, font, levelText, x + w - 6 - pillW + 4, y + 8, level >= max ? Theme.ACCENT : Theme.TEXT);
        Draw.text(g, font, "Slayer level " + level + " / " + max, tx, y + 19, Theme.MUTED);
        // XP bar
        int next = nextThreshold(line, xp);
        int prev = level == 0 ? 0 : line.levels().get(level - 1);
        float progress = next < 0 ? 1f : (float) ((xp - prev) / Math.max(1, next - prev));
        Draw.bar(g, tx, y + 31, room, 7, progress, color);
        String xpText = next < 0 ? Msg.number(Math.floor(xp)) + " XP" : Msg.number(Math.floor(xp)) + " / " + Msg.number(next) + " XP";
        Draw.fitText(g, font, xpText, tx, y + 40, room, Theme.DIM);

        // kills per tier
        int ky = y + 52;
        int tiers = line.tiers().size();
        int chipW = (w - 12 - (tiers - 1) * 3) / Math.max(1, tiers);
        for (int t = 1; t <= tiers; t++) {
            int kx = x + 6 + (t - 1) * (chipW + 3);
            Draw.rounded(g, kx, ky, chipW, 11, Theme.TRACK);
            String roman = Msg.roman(t);
            Draw.text(g, font, roman, kx + 3, ky + 2, Draw.mix(color, 0xFFFFFFFF, 0.2f));
            Draw.fitRight(g, font, String.valueOf(state.kills(line.id(), t)), kx + chipW - 3, ky + 2, chipW - font.width(roman) - 8, Theme.TEXT);
        }
        // the running quest
        int qy = y + 66;
        SlayerState.Quest quest = state.quest().filter(q -> q.line().equals(line.id())).orElse(null);
        if (quest != null && quest.tier() >= 1 && quest.tier() <= tiers) {
            int needed = line.tiers().get(quest.tier() - 1).tier().xp();
            String label = quest.bossUp() ? "Tier " + Msg.roman(quest.tier()) + ": BOSS UP!" : "Tier " + Msg.roman(quest.tier()) + ": " + (int) quest.progress() + " / " + needed;
            Draw.text(g, font, label, x + 6, qy, quest.bossUp() ? Theme.RED : Theme.GREEN);
            int bx = x + 6 + font.width(label) + 6;
            Draw.bar(g, bx, qy + 1, Math.max(10, x + w - 6 - bx), 6, quest.bossUp() ? 1f : (float) (quest.progress() / Math.max(1, needed)),
                    quest.bossUp() ? Theme.RED : Theme.GREEN);
        } else {
            Draw.text(g, font, "No active quest", x + 6, qy, Theme.DIM);
            if (hovered) {
                Draw.rightText(g, font, "Details >", x + w - 6, qy, color);
            }
        }
    }

    // ------------------------------------------------------------------ detail

    private void renderDetail(PageContext ctx, SlayerPayloads.PageLine line, SlayerState state) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();
        int color = line.color();
        double xp = state.xp(line.id());
        int level = level(line, xp);
        int max = line.levels().size();

        drawHeader(ctx, line, state, level, xp, x, y, w);

        int areaY = y + HEADER_H + GAP;
        int areaH = h - HEADER_H - GAP;
        int leftW = Mth.clamp(Math.round(w * 0.40f), 130, 210);
        trackX = x;
        trackY = areaY;
        trackW = leftW;
        trackH = areaH;
        infoX = x + leftW + GAP + 4;
        infoY = areaY;
        infoW = w - leftW - GAP - 4;
        infoH = areaH;

        // ---- the level track
        double ts = trackScroll.update(trackHeight - trackH);
        g.enableScissor(trackX, trackY, trackX + trackW, trackY + trackH);
        int ty = trackY - (int) Math.round(ts);
        int tstart = ty;
        Draw.text(g, font, "Level track", trackX + 2, ty + 1, Theme.MUTED);
        ty += 13;
        ty = drawTrack(ctx, line, level, xp, ty);
        trackHeight = ty - tstart + 2;
        g.disableScissor();
        if (trackHeight > trackH) {
            Draw.scrollbar(g, trackX + trackW + 1, trackY, trackH, trackHeight, ts, Theme.DIM);
        }

        // ---- your stats and the tiers
        double is = infoScroll.update(infoHeight - infoH);
        g.enableScissor(infoX, infoY, infoX + infoW, infoY + infoH);
        int iy = infoY - (int) Math.round(is);
        int istart = iy;
        iy = drawStats(ctx, line, state, level, xp, iy);
        iy += GAP;
        Draw.text(g, font, "Tiers", infoX + 2, iy + 1, Theme.MUTED);
        iy += 13;
        for (int t = 1; t <= line.tiers().size(); t++) {
            iy = drawTier(ctx, line, t, level, iy) + GAP;
        }
        infoHeight = iy - istart - GAP;
        g.disableScissor();
        if (infoHeight > infoH) {
            Draw.scrollbar(g, x + w + 4, infoY, infoH, infoHeight, is, Theme.DIM);
        }
        if (max == 0) {
            Draw.text(g, font, "No levels", x + 4, areaY, Theme.DIM);
        }
    }

    private void drawHeader(PageContext ctx, SlayerPayloads.PageLine line, SlayerState state, int level, double xp, int x, int y, int w) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = line.color();
        Draw.panel(g, x, y, w, HEADER_H, Draw.mix(Theme.CARD, color, 0.07f), Draw.mix(Theme.CARD_BORDER, color, 0.45f));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, Draw.withAlpha(color, 0.6f));
        // back button
        int bw = font.width("< Back") + 12;
        boolean backHover = ctx.hovered(x + 6, y + 6, bw, 13);
        Draw.panel(g, x + 6, y + 6, bw, 13, backHover ? Theme.CARD_HOVER : Theme.CARD, backHover ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Theme.CARD_BORDER);
        Draw.text(g, font, "< Back", x + 12, y + 9, backHover ? color : Theme.TEXT);
        hits.add(new Hit(x + 6, y + 6, bw, 13, ""));
        if (backHover) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
        int max = line.levels().size();
        int ix = x + bw + 14;
        Draw.panel(g, ix, y + 4, 30, 30, Draw.mix(Theme.TRACK, color, 0.18f), Draw.mix(Theme.CARD_BORDER, color, 0.6f));
        Draw.item(g, line.icon(), ix + 3, y + 7, 1.5f);
        int tx = ix + 38;
        int right = x + w - 8;
        Draw.scaledText(g, font, Component.literal(line.name()), tx, y + 5, 1.25f, color);
        Draw.fitText(g, font, "Slayer level " + level + " / " + max + (level >= max ? "  (max)" : ""), tx, y + 18, right - tx, Theme.TEXT);
        // XP bar to the next level
        int next = nextThreshold(line, xp);
        int prev = level == 0 ? 0 : line.levels().get(level - 1);
        float progress = next < 0 ? 1f : (float) ((xp - prev) / Math.max(1, next - prev));
        int barW = Math.min(150, Math.max(60, (right - tx) / 2));
        Draw.bar(g, tx, y + 28, barW, 6, progress, color);
        String xpText = next < 0 ? Msg.number(Math.floor(xp)) + " XP" : Msg.number(Math.floor(xp)) + " / " + Msg.number(next) + " XP to level " + (level + 1);
        Draw.fitText(g, font, xpText, tx + barW + 6, y + 28, right - (tx + barW + 6), Theme.MUTED);
        // the running quest, top right
        SlayerState.Quest quest = state.quest().filter(q -> q.line().equals(line.id())).orElse(null);
        if (quest != null && quest.tier() >= 1 && quest.tier() <= line.tiers().size()) {
            int needed = line.tiers().get(quest.tier() - 1).tier().xp();
            String label = quest.bossUp() ? "Tier " + Msg.roman(quest.tier()) + ": boss is up!" : "Quest, tier " + Msg.roman(quest.tier()) + ": " + (int) quest.progress() + " / " + needed;
            Draw.fitRight(g, font, label, right, y + 7, Math.max(40, right - tx - font.width(line.name()) * 5 / 4 - 8), quest.bossUp() ? Theme.RED : Theme.GREEN);
        }
    }

    /** The level rows. Returns the y after the last one. */
    private int drawTrack(PageContext ctx, SlayerPayloads.PageLine line, int level, double xp, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = line.color();
        int max = line.levels().size();
        int perRow = Math.max(1, (trackW - 26) / 18);
        boolean areaHover = ctx.hovered(trackX, trackY, trackW, trackH);
        String stat = statPerLevel(line);
        for (int lv = 1; lv <= max; lv++) {
            List<ItemStack> items = new ArrayList<>();
            for (SlayerPayloads.Unlock unlock : line.unlocks()) {
                if (unlock.level() == lv) {
                    items.add(unlock.item());
                }
            }
            boolean unlocked = level >= lv;
            int itemRows = (items.size() + perRow - 1) / perRow;
            int rowH = 24 + (items.isEmpty() ? 0 : itemRows * 18 + 2);
            int nodeX = trackX + 3;
            // the line down to the next node
            if (lv < max) {
                g.fill(nodeX + 4, y + 11, nodeX + 5, y + rowH + 2, level > lv ? color : Theme.BORDER);
            }
            Draw.rounded(g, nodeX, y + 1, 9, 9, unlocked ? color : Theme.TRACK);
            if (unlocked) {
                drawCheck(g, nodeX + 1, y + 2);
            } else {
                Draw.outline(g, nodeX, y + 1, 9, 9, Theme.BORDER);
            }
            int tx = trackX + 17;
            int room = trackW - 17 - 4;
            Draw.text(g, font, "Level " + lv, tx, y + 1, unlocked ? Theme.TEXT : Theme.DIM);
            String right = unlocked ? "Unlocked" : "Reach level " + lv;
            int rightW = font.width(right);
            if (room > font.width("Level " + lv) + rightW + 6) {
                Draw.rightText(g, font, right, tx + room, y + 1, unlocked ? Theme.GREEN : Theme.DIM);
            }
            Draw.fitText(g, font, stat.isEmpty() ? "-" : stat, tx, y + 11, room, unlocked ? Theme.GREEN : Draw.mix(Theme.DIM, Theme.GREEN, 0.15f));
            int itemY = y + 23;
            for (int i = 0; i < items.size(); i++) {
                int ix = tx + (i % perRow) * 18;
                int iy = itemY + (i / perRow) * 18;
                ItemStack stack = items.get(i);
                Draw.rounded(g, ix - 1, iy - 1, 18, 18, unlocked ? Draw.withAlpha(color, 0.2f) : Theme.TRACK);
                g.item(stack, ix, iy);
                if (!unlocked) {
                    g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xA0121620);
                }
                if (areaHover && ctx.hovered(ix, iy, 16, 16)) {
                    g.requestCursor(CursorTypes.POINTING_HAND);
                    List<Component> tip = new ArrayList<>(itemTooltip(ctx, stack));
                    tip.add(Component.empty());
                    tip.add(unlocked ? Component.literal("Unlocked at the Artisan Bench").withColor(Theme.GREEN)
                            : Component.literal("Reach " + line.name() + " Slayer " + lv).withColor(Theme.RED));
                    ctx.tooltip(tip, color);
                }
            }
            y += rowH;
        }
        return y;
    }

    private static void drawCheck(GuiGraphicsExtractor g, int x, int y) {
        int white = 0xFF101418;
        g.fill(x + 1, y + 3, x + 2, y + 4, white);
        g.fill(x + 2, y + 4, x + 3, y + 5, white);
        g.fill(x + 3, y + 3, x + 4, y + 4, white);
        g.fill(x + 4, y + 2, x + 5, y + 3, white);
        g.fill(x + 5, y + 1, x + 6, y + 2, white);
        g.fill(x + 6, y, x + 7, y + 1, white);
    }

    /** Your stats for the line, in a card. Returns the y below it. */
    private int drawStats(PageContext ctx, SlayerPayloads.PageLine line, SlayerState state, int level, double xp, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = line.color();
        int tiers = line.tiers().size();
        int rows = 1 + tiers + 3;
        int h = 8 + 12 + rows * 11 + 4;
        Draw.panel(g, infoX, y, infoW, h, Theme.CARD, Draw.mix(Theme.CARD_BORDER, color, 0.3f));
        Draw.text(g, font, "Your stats", infoX + 6, y + 5, color);
        int ry = y + 19;
        int left = infoX + 6;
        int right = infoX + infoW - 6;
        int total = 0;
        for (int t = 1; t <= tiers; t++) {
            total += state.kills(line.id(), t);
        }
        statRow(g, font, "Bosses slain", Msg.number(total), left, right, ry, Theme.TEXT);
        ry += 11;
        for (int t = 1; t <= tiers; t++) {
            int kills = state.kills(line.id(), t);
            int best = state.best(line.id(), t);
            String value = kills + (kills == 1 ? " kill" : " kills") + "  |  best " + (best > 0 ? time(best) : "-");
            statRow(g, font, "  Tier " + Msg.roman(t), value, left, right, ry, kills > 0 ? Theme.TEXT : Theme.DIM);
            ry += 11;
        }
        statRow(g, font, "Total slayer XP", Msg.number(Math.floor(xp)), left, right, ry, Theme.TEXT);
        ry += 11;
        statRow(g, font, "Boss tokens earned", Msg.number(state.tokens(line.id())), left, right, ry, Theme.TEXT);
        ry += 11;
        String bonus = bonusText(line, level);
        statRow(g, font, "Level bonus", bonus.isEmpty() ? "none yet" : bonus, left, right, ry, bonus.isEmpty() ? Theme.DIM : Theme.GREEN);
        return y + h;
    }

    private void statRow(GuiGraphicsExtractor g, Font font, String label, String value, int left, int right, int y, int valueColor) {
        Draw.text(g, font, label, left, y, Theme.MUTED);
        Draw.fitRight(g, font, value, right, y, Math.max(20, right - left - font.width(label) - 6), valueColor);
    }

    /** One tier's card. Returns the y below it. */
    private int drawTier(PageContext ctx, SlayerPayloads.PageLine line, int tierNo, int level, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = line.color();
        SlayerPayloads.PageTier pt = line.tiers().get(tierNo - 1);
        SlayerPayloads.TierView tier = pt.tier();
        boolean open = level >= tier.requires();
        int inner = infoW - 12;
        List<Row> rows = tierRows(ctx, line, pt, tierNo, level, inner);
        int rowsH = 0;
        for (Row row : rows) {
            rowsH += row.height();
        }
        int h = 6 + 16 + 3 + rowsH + 5;
        boolean areaHover = ctx.hovered(infoX, infoY, infoW, infoH);
        Draw.panel(g, infoX, y, infoW, h, open ? Theme.CARD : Draw.mix(Theme.CARD, Theme.TRACK, 0.4f),
                Draw.mix(Theme.CARD_BORDER, color, open ? 0.35f : 0.12f));
        // header: roman badge, title, reward icons
        int hx = infoX + 6;
        Draw.rounded(g, hx, y + 5, 16, 14, open ? Draw.withAlpha(color, 0.35f) : Theme.TRACK);
        String roman = Msg.roman(tierNo);
        Draw.text(g, font, roman, hx + 8 - font.width(roman) / 2, y + 8, open ? Theme.TEXT : Theme.DIM);
        int iconsW = (line.token().isEmpty() ? 0 : 18) + (line.rare().isEmpty() ? 0 : 18);
        String title = tier.title().isEmpty() ? line.name() + " " + roman : tier.title();
        Draw.fitText(g, font, title, hx + 22, y + 8, inner - 22 - iconsW, open ? color : Theme.MUTED);
        int ix = infoX + infoW - 6 - iconsW;
        if (!line.token().isEmpty()) {
            iconWithTip(ctx, line.token(), ix, y + 4, areaHover, color, "Dropped by every tier");
            ix += 18;
        }
        if (!line.rare().isEmpty()) {
            iconWithTip(ctx, line.rare(), ix, y + 4, areaHover, color, pt.rareChance() > 0 ? "Rare drop, " + chance(pt.rareChance()) + " chance" : "Doesn't drop from this tier");
        }
        int ry = y + 6 + 16 + 3;
        for (Row row : rows) {
            if (row.text() != null) {
                g.text(font, row.text(), infoX + 6 + row.indent(), ry, row.color(), false);
            }
            ry += row.height();
        }
        return y + h;
    }

    private void iconWithTip(PageContext ctx, ItemStack stack, int x, int y, boolean areaHover, int color, String extra) {
        ctx.graphics().item(stack, x, y);
        if (areaHover && ctx.hovered(x, y, 16, 16)) {
            ctx.graphics().requestCursor(CursorTypes.POINTING_HAND);
            List<Component> tip = new ArrayList<>(itemTooltip(ctx, stack));
            tip.add(Component.empty());
            tip.add(Component.literal(extra).withColor(Theme.MUTED));
            ctx.tooltip(tip, color);
        }
    }

    private List<Row> tierRows(PageContext ctx, SlayerPayloads.PageLine line, SlayerPayloads.PageTier pt, int tierNo, int level, int width) {
        Font font = ctx.font();
        SlayerPayloads.TierView tier = pt.tier();
        List<Row> rows = new ArrayList<>();
        wrap(font, rows, Component.literal("Boss: " + Msg.number(tier.bossHealth()) + " HP, level " + tier.bossLevel() + ", " + tier.minutes() + " min to kill it"),
                width, 0, Theme.TEXT);
        boolean ok = level >= tier.requires();
        Component cost = Component.literal("Cost: " + Msg.number(tier.fee()) + " coins").withColor(Theme.ACCENT)
                .append(Component.literal(tier.requires() <= 0 ? "" : "   Needs Slayer " + tier.requires()).withColor(ok ? Theme.GREEN : Theme.RED));
        wrap(font, rows, cost, width, 0, Theme.TEXT);
        wrap(font, rows, Component.literal("Summoned after " + Msg.number(tier.xp()) + " kill points of " + line.counts()), width, 0, Theme.MUTED);
        StringBuilder rewards = new StringBuilder("Rewards: " + Msg.number(tier.slayerXp()) + " Slayer XP");
        if (pt.tokensMax() > 0 && !line.token().isEmpty()) {
            rewards.append(", ").append(pt.tokensMin() == pt.tokensMax() ? String.valueOf(pt.tokensMin()) : pt.tokensMin() + "-" + pt.tokensMax())
                    .append("x ").append(line.token().getHoverName().getString());
        }
        if (pt.rareChance() > 0 && !line.rare().isEmpty()) {
            rewards.append(", ").append(chance(pt.rareChance())).append(" ").append(line.rare().getHoverName().getString());
        }
        if (pt.eggChance() > 0) {
            rewards.append(", ").append(chance(pt.eggChance())).append(" Golden Pet Egg");
        }
        wrap(font, rows, Component.literal(rewards.toString()), width, 0, Theme.MUTED);
        if (!tier.abilities().isEmpty()) {
            rows.add(new Row(null, 0, 0, 3));
            for (String ability : tier.abilities()) {
                String[] parts = ability.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                boolean attack = parts[0].equals("A");
                int accent = attack ? Theme.RED : Theme.AQUA;
                wrap(font, rows, Component.literal(attack ? "Attack  " : "Defence  ").withColor(accent)
                        .append(Component.literal(parts[1]).withColor(Theme.TEXT)), width, 0, Theme.TEXT);
                wrap(font, rows, Component.literal(parts[2]), width - 8, 8, Theme.DIM);
            }
        }
        return rows;
    }

    private static void wrap(Font font, List<Row> rows, Component text, int width, int indent, int color) {
        for (FormattedCharSequence seq : Draw.wrap(font, text, width)) {
            rows.add(new Row(seq, indent, color, 10));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !ctx.hovered(ctx.x(), ctx.y(), ctx.width(), ctx.height())) {
            return false;
        }
        for (Hit hit : hits) {
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                if (hit.line().isEmpty()) {
                    selected = null;
                } else {
                    select(hit.line());
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (selected == null) {
            overviewScroll.target -= amount * 28;
            return overviewHeight > ctx.height();
        }
        if (ctx.hovered(trackX, trackY, trackW + 4, trackH)) {
            trackScroll.target -= amount * 28;
            return true;
        }
        infoScroll.target -= amount * 28;
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static List<Component> itemTooltip(PageContext ctx, ItemStack stack) {
        try {
            return stack.getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL);
        } catch (RuntimeException e) {
            return List.of(stack.getHoverName());
        }
    }

    private static int level(SlayerPayloads.PageLine line, double xp) {
        int level = 0;
        for (int threshold : line.levels()) {
            if (xp >= threshold) {
                level++;
            }
        }
        return level;
    }

    private static int nextThreshold(SlayerPayloads.PageLine line, double xp) {
        for (int threshold : line.levels()) {
            if (xp < threshold) {
                return threshold;
            }
        }
        return -1;
    }

    private static String stat(SlayerPayloads.StatPer s, int levels) {
        return "+" + Msg.number(s.per() * levels) + (s.percent() ? "% " : " ") + s.name();
    }

    /** "+3 Health" per level. */
    private static String statPerLevel(SlayerPayloads.PageLine line) {
        List<String> parts = new ArrayList<>();
        for (SlayerPayloads.StatPer s : line.perLevel()) {
            parts.add(stat(s, 1));
        }
        return String.join(", ", parts);
    }

    /** What the levels you have give in total. */
    private static String bonusText(SlayerPayloads.PageLine line, int level) {
        if (level <= 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (SlayerPayloads.StatPer s : line.perLevel()) {
            parts.add(stat(s, level));
        }
        return String.join(", ", parts);
    }

    private static String chance(double fraction) {
        double percent = fraction * 100;
        String text = percent >= 1 ? String.format("%.1f", percent) : String.format("%.2f", percent);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text + "%";
    }

    private static String time(int ticks) {
        double seconds = ticks / 20.0;
        int minutes = (int) (seconds / 60);
        return String.format("%d:%04.1f", minutes, seconds - minutes * 60);
    }
}
