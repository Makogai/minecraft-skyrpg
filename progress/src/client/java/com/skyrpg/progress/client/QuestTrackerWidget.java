package com.skyrpg.progress.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.quests.QuestData;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.progress.quests.QuestData.Quest;
import com.skyrpg.progress.quests.QuestState;
import com.skyrpg.progress.quests.Quests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The tracked quest on the HUD: its name and the objectives still open, each with a progress bar. */
final class QuestTrackerWidget implements HudWidget {
    private static final int WIDTH = 150;
    private static final int LINE = 17;
    private static final int MAX_LINES = 3;

    private record Row(String label, int value, int target) {
    }

    @Override
    public Identifier id() {
        return SkyRpgProgress.id("quest_tracker");
    }

    @Override
    public Component name() {
        return Component.literal("Quest Tracker");
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.MIDDLE_LEFT;
    }

    @Override
    public int defaultX() {
        return 4;
    }

    @Override
    public int defaultY() {
        return 70;
    }

    private static Quest tracked() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        QuestState state = QuestState.of(player);
        Quest quest = QuestData.quest(state.tracked());
        return quest != null && Quests.open(state, quest) ? quest : null;
    }

    private static List<Row> rows(Quest quest) {
        List<Row> rows = new ArrayList<>();
        QuestState state = QuestState.of(Minecraft.getInstance().player);
        if (Quests.offered(state, quest)) {
            rows.add(new Row("Speak to " + quest.giverName(), 0, 1));
            return rows;
        }
        if (Quests.ready(state, quest)) {
            rows.add(new Row("Return to " + quest.turnInName(), 0, 1));
            return rows;
        }
        Map<String, Integer> live = QuestState.live(Minecraft.getInstance().player);
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective o = quest.objectives().get(i);
            int value = live.getOrDefault(quest.id() + "#" + i, 0);
            if (value < o.target()) {
                rows.add(new Row(o.label(), value, o.target()));
            }
        }
        return rows;
    }

    @Override
    public boolean visible() {
        return tracked() != null;
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        Quest quest = tracked();
        int lines = quest == null ? 1 : Math.max(1, Math.min(MAX_LINES, rows(quest).size()));
        return 18 + lines * LINE;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Quest quest = tracked();
        Draw.panel(g, 0, 0, WIDTH, height(font), 0xE0121620, Theme.BORDER);
        g.fill(1, 1, 3, height(font) - 1, Theme.ACCENT);
        if (quest == null) {
            Draw.text(g, font, Component.literal("Quest").withColor(Theme.ACCENT), 8, 5, Theme.TEXT);
            Draw.text(g, font, "Your next quest", 8, 18, Theme.MUTED);
            return;
        }
        Draw.fitText(g, font, quest.title(), 8, 5, WIDTH - 14, Theme.ACCENT);
        List<Row> rows = rows(quest);
        int y = 17;
        for (int i = 0; i < Math.min(MAX_LINES, rows.size()); i++) {
            Row row = rows.get(i);
            Draw.fitText(g, font, row.label(), 8, y, WIDTH - 14, Theme.TEXT);
            if (row.target() > 1) {
                Draw.bar(g, 8, y + 10, WIDTH - 54, 3, Math.min(1f, row.value() / (float) row.target()), Theme.ACCENT);
                Draw.rightText(g, font, Draw.compactNumbers(Msg.number(row.value()) + "/" + Msg.number(row.target())), WIDTH - 6, y + 7, Theme.MUTED);
            }
            y += LINE;
        }
    }
}
