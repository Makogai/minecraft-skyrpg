package com.skyrpg.events.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudEditorScreen;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.events.SkyRpgEvents;
import com.skyrpg.events.net.EventPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The sidebar on the right: the /board one when the server shows it, otherwise the vanilla
 * scoreboard sidebar redrawn in the SkyRPG look (switch that off in Settings to get vanilla's).
 *
 * <p>It's a HUD widget so it can be moved and scaled in the HUD editor, but it draws in vanilla's
 * scoreboard slot rather than with the other widgets, so it layers like the sidebar always has.
 * Hiding it in the editor brings the vanilla sidebar back.
 */
final class Sidebar implements HudWidget {
    static final Sidebar INSTANCE = new Sidebar();
    private static final int HEADER = 19;
    private static final int LINE = 10;
    private static final int MAX_VANILLA_LINES = 15;
    private static final long FADE_MS = 260;
    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static Setting restyle;
    /** The /board one; null when hidden. */
    private static Content board;
    private static long boardChangedAt;
    /** The board that was just hidden, drawn fading out. */
    private static Content fading;
    private static long fadingSince;

    /** What a sidebar shows. {@code right} of a line may be empty. */
    private record Content(Component title, int color, List<EventPayloads.BoardLine> lines, Component footer) {
    }

    private Sidebar() {
    }

    static void init() {
        restyle = SkyRpgSettings.toggle("interface.sidebar", SkyRpgSettings.INTERFACE, "SkyRPG sidebar",
                "Draw the scoreboard sidebar in the SkyRPG look", true, null);
        HudManager.register(INSTANCE);
        HudManager.drawsItself(INSTANCE);
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (g, deltaTracker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof HudEditorScreen) {
                return; // the editor draws it where it's being dragged
            }
            boolean enabled = HudManager.layout(INSTANCE).enabled;
            long now = Util.getMillis();
            Content content = enabled ? current() : null;
            if (content != null) {
                float alpha = board != null && content == board ? Draw.easeOut((now - boardChangedAt) / (float) FADE_MS) : 1;
                drawAt(g, minecraft.font, content, alpha);
                return;
            }
            if (enabled && fading != null && now - fadingSince < FADE_MS) {
                drawAt(g, minecraft.font, fading, 1 - Draw.easeOut((now - fadingSince) / (float) FADE_MS));
            }
            vanilla.extractRenderState(g, deltaTracker);
        });
    }

    static void onBoard(EventPayloads.ShowBoard payload) {
        long now = Util.getMillis();
        if (!payload.visible()) {
            if (board != null) {
                fading = board;
                fadingSince = now;
            }
            board = null;
            return;
        }
        // Only fade in when it appears, not on every update of a board that's already up.
        if (board == null) {
            boardChangedAt = now;
        }
        board = new Content(payload.title(), payload.color(), payload.lines(), payload.footer());
    }

    static void clear() {
        board = null;
        fading = null;
    }

    /** The /board one, else the vanilla objective (if restyling), else null. */
    private static Content current() {
        if (board != null) {
            return board;
        }
        return restyle != null && restyle.get() ? vanillaContent() : null;
    }

    private static Content vanillaContent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
        if (team != null && team.getColor().isPresent()) {
            objective = scoreboard.getDisplayObjective(team.getColor().get().displaySlot());
        }
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return null;
        }
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective).stream().filter(entry -> !entry.isHidden()).toList());
        entries.sort(ORDER);
        List<EventPayloads.BoardLine> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries.subList(0, Math.min(MAX_VANILLA_LINES, entries.size()))) {
            Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
            lines.add(new EventPayloads.BoardLine(name, entry.formatValue(objective.numberFormatOrDefault(StyledFormat.NO_STYLE))));
        }
        return new Content(objective.getDisplayName(), Theme.ACCENT, lines, Component.empty());
    }

    private static Content sample() {
        return new Content(Component.literal("Sidebar"), Theme.ACCENT, List.of(
                new EventPayloads.BoardLine(Component.literal("Steve"), Component.literal("26")),
                new EventPayloads.BoardLine(Component.literal("Alex"), Component.literal("19")),
                new EventPayloads.BoardLine(Component.literal("Notch"), Component.literal("12"))), Component.empty());
    }

    private Content shown(boolean editing) {
        Content content = current();
        return content == null && editing ? sample() : content;
    }

    /** Draws in the widget's box, the way HudManager places widgets (moved below the events widget if they'd overlap). */
    private static void drawAt(GuiGraphicsExtractor g, Font font, Content content, float alpha) {
        HudManager.drawsItself(INSTANCE);
        HudManager.Box box = HudManager.placedBox(INSTANCE, font, g.guiWidth(), g.guiHeight());
        float scale = HudManager.layout(INSTANCE).scale;
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate(box.x() + (1 - alpha) * 10, box.y());
        g.pose().scale(scale, scale);
        draw(g, font, content, alpha);
        g.pose().popMatrix();
    }

    /** The screen in the widget's own (scaled) pixels, so it can stay within it. */
    private static int screenWidth() {
        return Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / HudManager.layout(INSTANCE).scale);
    }

    private static int screenHeight() {
        return Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / HudManager.layout(INSTANCE).scale);
    }

    private static boolean hasFooter(Content content) {
        return !content.footer().getString().isEmpty();
    }

    /** Lines that fit on screen; a /board can send more than a short window has room for. */
    private static int shownLines(Content content) {
        int room = (screenHeight() - 8 - HEADER - 4 - (hasFooter(content) ? 13 : 0)) / LINE;
        int shown = Math.max(1, Math.min(content.lines().size(), room));
        // Sharing a column with the events widget: only what fits between it and the bottom (or the hotbar).
        Minecraft minecraft = Minecraft.getInstance();
        EventWidget events = eventWidget();
        float scale = HudManager.layout(INSTANCE).scale;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        if (events != null && events.visible() && HudManager.layout(events).enabled) {
            int width = Math.round(width(minecraft.font, content) * scale);
            int x = HudManager.left(INSTANCE, width, screenWidth);
            HudManager.Box box = HudManager.box(events, minecraft.font, screenWidth, screenHeight);
            if (box.x() < x + width && x < box.x() + box.width() && box.y() < screenHeight / 2) {
                int bottom = screenHeight - (HudManager.overHotbar(x, width, screenWidth) ? HudManager.HOTBAR_BAND : 4);
                int below = Math.round((bottom - (box.y() + box.height() + 2)) / scale);
                int fit = (below - HEADER - 4 - (hasFooter(content) ? 13 : 0)) / LINE;
                shown = Math.max(Math.min(3, shown), Math.min(shown, fit));
            }
        }
        return shown;
    }

    private static EventWidget eventWidget() {
        for (HudWidget widget : HudManager.widgets()) {
            if (widget instanceof EventWidget events) {
                return events;
            }
        }
        return null;
    }

    /**
     * Room (in GUI pixels) the events widget at {@code eventsWidth} can take above the sidebar when
     * they share a column, so all of the sidebar still fits below it; -1 if they don't share one or
     * there's no sidebar.
     */
    static int roomLeftBelow(Font font, HudWidget events, int eventsWidth, int screenWidth, int screenHeight) {
        Content content = current();
        if (content == null || !HudManager.layout(INSTANCE).enabled) {
            return -1;
        }
        float scale = HudManager.layout(INSTANCE).scale;
        int width = Math.round(width(font, content) * scale);
        int x = HudManager.left(INSTANCE, width, screenWidth);
        int eventsX = HudManager.left(events, eventsWidth, screenWidth);
        if (!(eventsX < x + width && x < eventsX + eventsWidth)) {
            return -1;
        }
        int lines = Math.max(1, Math.min(content.lines().size(), (screenHeight() - 8 - HEADER - 4 - (hasFooter(content) ? 13 : 0)) / LINE));
        int sidebarHeight = Math.round((HEADER + lines * LINE + 4 + (hasFooter(content) ? 13 : 0)) * scale);
        int bottom = screenHeight - (HudManager.overHotbar(x, width, screenWidth) ? HudManager.HOTBAR_BAND : 4);
        return bottom - sidebarHeight - 2 - 4;
    }

    private static int width(Font font, Content content) {
        int width = font.width(content.title()) + 24;
        for (EventPayloads.BoardLine line : content.lines()) {
            int right = font.width(line.right());
            width = Math.max(width, font.width(line.left()) + (right > 0 ? right + 12 : 0) + 15);
        }
        width = Math.max(width, font.width(content.footer()) + 16);
        int max = Math.max(96, Math.min(260, screenWidth() * 45 / 100));
        return Math.min(max, Math.max(96, width));
    }

    private static int height(Content content) {
        return HEADER + Math.max(1, shownLines(content)) * LINE + 4 + (hasFooter(content) ? 13 : 0);
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Content content, float alpha) {
        int width = width(font, content);
        int height = height(content);
        int color = content.color();
        Draw.panel(g, 0, 0, width, height, Draw.withAlpha(0xE0121620, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, color, 0.4f), alpha));
        g.fill(2, 0, width - 2, 1, Draw.withAlpha(color, alpha));
        g.fillGradient(1, 1, width - 1, HEADER - 1, Draw.withAlpha(color, 0.14f * alpha), 0);
        Draw.fit(g, font, content.title(), width / 2, 6, width - 12, Draw.Align.CENTER, Draw.withAlpha(color, alpha));
        g.fill(5, HEADER - 1, width - 5, HEADER, Draw.withAlpha(Theme.BORDER, alpha));

        int y = HEADER + 4;
        if (content.lines().isEmpty()) {
            Draw.text(g, font, "—", 7, y, Draw.withAlpha(Theme.DIM, alpha));
        }
        // The value on the right keeps its room (up to half the width); the label ends in "…" if it must.
        int shown = shownLines(content);
        for (EventPayloads.BoardLine line : content.lines().subList(0, Math.min(shown, content.lines().size()))) {
            int rightWidth = Math.min(font.width(line.right()), (width - 14) / 2);
            if (rightWidth > 0) {
                Draw.fit(g, font, line.right(), width - 7, y, rightWidth, 1f, 1f, Draw.Align.RIGHT, Draw.withAlpha(color, alpha), false);
            }
            int leftRoom = width - 14 - (rightWidth > 0 ? rightWidth + 6 : 0);
            g.text(font, Draw.ellipsize(font, line.left(), leftRoom), 7, y, Draw.withAlpha(Theme.TEXT, alpha), false);
            y += LINE;
        }
        if (hasFooter(content)) {
            int footerY = height - 12;
            g.fill(5, footerY - 3, width - 5, footerY - 2, Draw.withAlpha(Theme.BORDER, alpha));
            Draw.fit(g, font, content.footer(), width / 2, footerY, width - 12, Draw.Align.CENTER, Draw.withAlpha(Theme.DIM, alpha));
        }
    }

    @Override
    public Identifier id() {
        return SkyRpgEvents.id("sidebar");
    }

    @Override
    public Component name() {
        return Component.literal("Sidebar");
    }

    @Override
    public int width(Font font) {
        Content content = shown(true);
        return width(font, content);
    }

    @Override
    public int height(Font font) {
        return height(shown(true));
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.MIDDLE_RIGHT;
    }

    @Override
    public int defaultX() {
        return -4;
    }

    @Override
    public int defaultY() {
        return 0;
    }

    /**
     * Showing whenever there's a board or a restyled scoreboard, so HudManager lays it out with the
     * other widgets. It still draws in its own slot (see the class comment and {@link HudManager#drawsItself}).
     */
    @Override
    public boolean visible() {
        return current() != null;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        draw(g, font, shown(editing), 1);
    }
}
