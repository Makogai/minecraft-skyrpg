package com.skyrpg.progress.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.quests.QuestData;
import com.skyrpg.progress.quests.QuestData.Chapter;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.progress.quests.QuestData.Quest;
import com.skyrpg.progress.quests.QuestPayloads;
import com.skyrpg.progress.quests.QuestState;
import com.skyrpg.progress.quests.Quests;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The K menu's Journal page. Left: the Skyhaven Chronicle's chapters with how much of each you have finished; click a chapter
 * to open it and a quest to read it. Right: the quest's story, its objectives with progress bars, its rewards and a Track
 * button that points your waypoint compass at the next place to go.
 */
public final class JournalPage implements MenuPage {
    private static final int GAP = 6;
    private static final int CHAPTER_H = 20;
    private static final int QUEST_H = 14;
    private static final int DONE = 0xFF5CD68A;
    private static final int GOLD = 0xFFFFC940;

    private final Set<Identifier> expanded = new HashSet<>();
    private Identifier selected;
    private double scrollLeft;
    private double scrollRight;
    private int leftContent;
    private int rightContent;
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int top;
    private int areaH;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Identifier chapter, Identifier quest, String action, int objective) {
    }

    @Override
    public Identifier id() {
        return SkyRpgProgress.id("journal");
    }

    @Override
    public Component title() {
        return Component.literal("Journal");
    }

    @Override
    public Component subtitle() {
        return Component.literal("The Skyhaven Chronicle");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public int order() {
        return 28;
    }

    @Override
    public void opened() {
        scrollLeft = 0;
        scrollRight = 0;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            QuestState state = QuestState.of(player);
            if (state.counter("journal") == 0 && ClientPlayNetworking.canSend(QuestPayloads.Action.TYPE)) {
                ClientPlayNetworking.send(new QuestPayloads.Action("opened", "", 0));
            }
            Quest tracked = QuestData.quest(state.tracked());
            if (tracked != null && selected == null) {
                selected = tracked.id();
                expanded.add(tracked.chapter());
            }
        }
    }

    @Override
    public Component hint() {
        return Component.literal("Click a chapter or quest");
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        hits.clear();
        QuestState state = QuestState.of(ctx.player());
        top = ctx.y();
        areaH = ctx.height();
        leftX = ctx.x();
        leftW = Mth.clamp(Math.round(ctx.width() * 0.36f), 120, 190);
        rightX = leftX + leftW + GAP + 4;
        rightW = ctx.width() - leftW - GAP - 4;
        if (QuestData.chapters().isEmpty()) {
            Draw.text(g, ctx.font(), "Loading the Chronicle...", leftX + 6, top + 6, Theme.MUTED);
            return;
        }
        if (selected == null || QuestData.quest(selected) == null) {
            Quest first = Quests.firstActive(state);
            selected = first == null ? null : first.id();
            if (first != null) {
                expanded.add(first.chapter());
            }
        }
        drawLeft(ctx, state);
        drawRight(ctx, state);
    }

    private void drawLeft(PageContext ctx, QuestState state) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, leftX, top, leftW, areaH, Theme.CARD, Theme.CARD_BORDER);
        scrollLeft = Mth.clamp(scrollLeft, 0, Math.max(0, leftContent - areaH + 8));
        g.enableScissor(leftX + 1, top + 1, leftX + leftW - 1, top + areaH - 1);
        int y = top + 4 - (int) Math.round(scrollLeft);
        int start = y;
        for (Chapter chapter : QuestData.chapters().values()) {
            List<Quest> quests = QuestData.inChapter(chapter.id());
            int done = 0;
            boolean unlocked = false;
            for (Quest quest : quests) {
                done += state.isDone(quest.id().toString()) ? 1 : 0;
                unlocked |= Quests.available(state, quest);
            }
            boolean complete = !quests.isEmpty() && done == quests.size();
            boolean open = expanded.contains(chapter.id()) && unlocked;
            boolean hovered = ctx.hovered(leftX, y, leftW, CHAPTER_H) && ctx.hovered(leftX, top, leftW, areaH);
            if (hovered && unlocked) {
                g.fill(leftX + 2, y, leftX + leftW - 2, y + CHAPTER_H, Theme.HOVER);
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            ItemStack icon = new ItemStack(item(chapter.icon()));
            Draw.item(g, icon, leftX + 5, y + 2, 1f);
            int color = !unlocked ? Theme.DIM : complete ? DONE : Theme.TEXT;
            Draw.fitText(g, font, chapter.title(), leftX + 26, y + 2, leftW - 34, color);
            if (unlocked) {
                Draw.bar(g, leftX + 26, y + 13, leftW - 66, 3, quests.isEmpty() ? 0 : done / (float) quests.size(), complete ? DONE : Theme.ACCENT);
                Draw.rightText(g, font, done + "/" + quests.size(), leftX + leftW - 6, y + 11, Theme.MUTED);
            } else {
                Draw.text(g, font, "Locked", leftX + 26, y + 11, Theme.DIM);
            }
            hits.add(new Hit(leftX, y, leftW, CHAPTER_H, chapter.id(), null, "chapter", 0));
            y += CHAPTER_H;
            if (open) {
                for (Quest quest : quests) {
                    String key = quest.id().toString();
                    boolean isDone = state.isDone(key);
                    boolean isOpen = Quests.open(state, quest);
                    if (!isDone && !isOpen) {
                        continue;
                    }
                    boolean sel = quest.id().equals(selected);
                    boolean qHover = ctx.hovered(leftX, y, leftW, QUEST_H) && ctx.hovered(leftX, top, leftW, areaH);
                    if (sel) {
                        g.fill(leftX + 8, y, leftX + leftW - 2, y + QUEST_H, Theme.ACCENT_SOFT);
                    } else if (qHover) {
                        g.fill(leftX + 8, y, leftX + leftW - 2, y + QUEST_H, Theme.HOVER);
                    }
                    if (qHover) {
                        g.requestCursor(CursorTypes.POINTING_HAND);
                    }
                    int dot = isDone ? DONE : Quests.ready(state, quest) || Quests.offered(state, quest) ? GOLD : key.equals(state.tracked()) ? Theme.ACCENT : Theme.BLUE;
                    g.fill(leftX + 12, y + 5, leftX + 17, y + 10, dot);
                    Draw.fitText(g, font, quest.title(), leftX + 22, y + 3, leftW - 30, isDone ? Theme.MUTED : Theme.TEXT);
                    hits.add(new Hit(leftX, y, leftW, QUEST_H, null, quest.id(), "quest", 0));
                    y += QUEST_H;
                }
                y += 3;
            }
        }
        leftContent = y - start + 8;
        g.disableScissor();
        if (leftContent > areaH) {
            Draw.scrollbar(g, leftX + leftW - 3, top + 1, areaH - 2, leftContent, scrollLeft, Theme.DIM);
        }
    }

    private void drawRight(PageContext ctx, QuestState state) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Quest quest = selected == null ? null : QuestData.quest(selected);
        if (quest == null) {
            Draw.text(g, font, "Every quest is done. Well met, traveller.", rightX + 4, top + 6, Theme.MUTED);
            return;
        }
        scrollRight = Mth.clamp(scrollRight, 0, Math.max(0, rightContent - areaH));
        g.enableScissor(rightX, top, rightX + rightW, top + areaH);
        int x = rightX;
        int w = rightW;
        int y = top - (int) Math.round(scrollRight);
        int start = y;
        String key = quest.id().toString();
        boolean isDone = state.isDone(key);
        boolean active = Quests.open(state, quest);
        boolean offered = Quests.offered(state, quest);
        boolean ready = Quests.ready(state, quest);
        Map<String, Integer> live = QuestState.live(ctx.player());
        Chapter chapter = QuestData.chapters().get(quest.chapter());

        // Title
        if (chapter != null) {
            Draw.text(g, font, chapter.title().toUpperCase() + (quest.side() ? "  ·  SIDE QUEST" : ""), x + 2, y + 2, Theme.DIM);
        }
        y += 13;
        Draw.scaledText(g, font, Component.literal(quest.title()).withColor(isDone ? DONE : Theme.ACCENT), x + 2, y, 1.25f, Theme.TEXT);
        y += 16;
        if (isDone) {
            Draw.text(g, font, "Completed", x + 2, y, DONE);
            y += 12;
        }

        // Who to see
        if (offered || ready) {
            String who = offered ? quest.giverName() : quest.turnInName();
            String line = offered ? "Speak to " + who + " to begin." : "Objectives complete. Return to " + who + " to hand in.";
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(line), w - 16);
            int boxH = 8 + lines.size() * 10;
            Draw.panel(g, x, y, w, boxH, Theme.ACCENT_SOFT, GOLD);
            int ly = y + 5;
            for (FormattedCharSequence l : lines) {
                g.text(font, l, x + 8, ly, GOLD, false);
                ly += 10;
            }
            y += boxH + GAP;
        } else if (!isDone && quest.hasGiver()) {
            Draw.text(g, font, "Given by " + quest.giverName() + (quest.hasTurnIn() && !quest.turnInNpc().equals(quest.giverNpc()) ? "  ·  hand in to " + quest.turnInName() : ""),
                    x + 2, y, Theme.MUTED);
            y += 13;
        }

        // Story
        if (!quest.giverText().isEmpty()) {
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal("\"" + quest.giverText() + "\"").withStyle(net.minecraft.ChatFormatting.ITALIC), w - 16);
            int boxH = 18 + lines.size() * 10;
            Draw.panel(g, x, y, w, boxH, Theme.CARD, Theme.CARD_BORDER);
            g.fill(x + 1, y + 1, x + 3, y + boxH - 1, Theme.ACCENT);
            Draw.text(g, font, quest.giverName(), x + 9, y + 4, Theme.ACCENT);
            int ly = y + 15;
            for (FormattedCharSequence line : lines) {
                g.text(font, line, x + 9, ly, Theme.MUTED, false);
                ly += 10;
            }
            y += boxH + GAP;
        }
        if (!quest.description().isEmpty()) {
            for (FormattedCharSequence line : Draw.wrap(font, Component.literal(quest.description()), w - 4)) {
                g.text(font, line, x + 2, y, Theme.TEXT, false);
                y += 10;
            }
            y += GAP;
        }

        // Objectives
        y = heading(ctx, "Objectives", x, y, w);
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective o = quest.objectives().get(i);
            int value = isDone ? o.target() : live.getOrDefault(key + "#" + i, 0);
            boolean objectiveDone = value >= o.target();
            int color = objectiveDone ? DONE : Theme.TEXT;
            g.fill(x + 3, y + 2, x + 8, y + 7, objectiveDone ? DONE : Theme.DIM);
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(o.label()), w - 70);
            int ly = y;
            for (FormattedCharSequence line : lines) {
                g.text(font, line, x + 13, ly, color, false);
                ly += 10;
            }
            if (o.target() > 1) {
                Draw.bar(g, x + w - 52, y + 3, 48, 4, Mth.clamp(value / (float) o.target(), 0, 1), objectiveDone ? DONE : Theme.ACCENT);
                Draw.rightText(g, font, Draw.compactNumbers(Msg.number(Math.min(value, o.target())) + "/" + Msg.number(o.target())), x + w - 4, y + 9, Theme.MUTED);
                ly = Math.max(ly, y + 20);
            }
            y = ly + 3;
        }
        y += 3;

        // Rewards
        y = heading(ctx, "Rewards", x, y, w);
        QuestData.Rewards r = quest.rewards();
        if (r.coins() > 0) {
            Draw.text(g, font, Msg.number(r.coins()) + " coins", x + 13, y, GOLD);
            g.fill(x + 3, y + 2, x + 8, y + 7, GOLD);
            y += 12;
        }
        if (r.xp() > 0) {
            Draw.text(g, font, "+" + r.xp() + " SkyRPG XP", x + 13, y, Theme.AQUA);
            g.fill(x + 3, y + 2, x + 8, y + 7, Theme.AQUA);
            y += 12;
        }
        for (QuestData.RewardItem reward : r.items()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(reward.item()).orElse(Items.BARRIER), reward.count());
            Draw.item(g, stack, x + 1, y - 3, 0.75f);
            Draw.text(g, font, (reward.count() > 1 ? reward.count() + "x " : "") + stack.getHoverName().getString(), x + 15, y, Theme.TEXT);
            y += 12;
        }
        if (!r.hint().isEmpty()) {
            for (FormattedCharSequence line : Draw.wrap(font, Component.literal(r.hint()), w - 16)) {
                g.text(font, line, x + 13, y, Theme.MUTED, false);
                y += 10;
            }
        }
        y += GAP;

        // Buttons
        if (active) {
            boolean trackable = false;
            for (int i = 0; i < quest.objectives().size(); i++) {
                trackable |= quest.objectives().get(i).trackable() && live.getOrDefault(key + "#" + i, 0) < quest.objectives().get(i).target();
            }
            trackable &= !ready;
            int bx = x + 2;
            trackable |= offered || ready;
            if (trackable) {
                bx += button(ctx, "Track", bx, y, Theme.ACCENT, new Hit(0, 0, 0, 0, null, quest.id(), "track", -1)) + 6;
            }
            if (!offered && quest.hasDialogue("offer")) {
                bx += button(ctx, "Re-read", bx, y, Theme.BLUE, new Hit(0, 0, 0, 0, null, quest.id(), "reread", 0)) + 6;
            }
            if (quest.side()) {
                button(ctx, "Abandon", bx, y, Theme.RED, new Hit(0, 0, 0, 0, null, quest.id(), "abandon", 0));
            }
            y += 20;
        } else if (isDone) {
            int bx = x + 2;
            if (quest.hasDialogue("offer")) {
                bx += button(ctx, "Re-read intro", bx, y, Theme.BLUE, new Hit(0, 0, 0, 0, null, quest.id(), "reread", 0)) + 6;
            }
            if (quest.hasDialogue("complete")) {
                button(ctx, "Re-read hand-in", bx, y, Theme.BLUE, new Hit(0, 0, 0, 0, null, quest.id(), "reread", 1)) ;
            }
            y += 20;
        }
        rightContent = y - start + 6;
        g.disableScissor();
        if (rightContent > areaH) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, top, areaH, rightContent, scrollRight, Theme.DIM);
        }
    }

    private int heading(PageContext ctx, String text, int x, int y, int w) {
        Draw.text(ctx.graphics(), ctx.font(), text.toUpperCase(), x + 2, y, Theme.MUTED);
        ctx.graphics().fill(x + 2, y + 10, x + w, y + 11, Theme.BORDER);
        return y + 15;
    }

    /** Draws a button and registers its click box; returns its width. */
    private int button(PageContext ctx, String label, int x, int y, int accent, Hit action) {
        Font font = ctx.font();
        int w = font.width(label) + 20;
        boolean hovered = ctx.hovered(x, y, w, 15) && ctx.hovered(rightX, top, rightW, areaH);
        Draw.panel(ctx.graphics(), x, y, w, 15, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? accent : Theme.CARD_BORDER);
        Draw.text(ctx.graphics(), font, label, x + 10, y + 4, hovered ? accent : Theme.TEXT);
        if (hovered) {
            ctx.graphics().requestCursor(CursorTypes.POINTING_HAND);
        }
        hits.add(new Hit(x, y, w, 15, null, action.quest(), action.action(), action.objective()));
        return w;
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(Items.BOOK);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x < hit.x() || x >= hit.x() + hit.w() || y < hit.y() || y >= hit.y() + hit.h() || y < top || y >= top + areaH) {
                continue;
            }
            click();
            switch (hit.action()) {
                case "chapter" -> {
                    QuestState state = QuestState.of(ctx.player());
                    boolean unlocked = QuestData.inChapter(hit.chapter()).stream().anyMatch(q -> Quests.available(state, q));
                    if (unlocked && !expanded.remove(hit.chapter())) {
                        expanded.add(hit.chapter());
                    }
                }
                case "quest" -> {
                    selected = hit.quest();
                    scrollRight = 0;
                }
                default -> ClientPlayNetworking.send(new QuestPayloads.Action(hit.action(), hit.quest().toString(), hit.objective()));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (x < rightX - 2) {
            scrollLeft = Mth.clamp(scrollLeft - amount * 14, 0, Math.max(0, leftContent - areaH + 8));
        } else {
            scrollRight = Mth.clamp(scrollRight - amount * 14, 0, Math.max(0, rightContent - areaH));
        }
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.35f));
    }
}
