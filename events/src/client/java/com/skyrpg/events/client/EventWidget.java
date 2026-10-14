package com.skyrpg.events.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.events.SkyRpgEvents;
import com.skyrpg.events.net.EventPayloads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The running events, one card each (soonest-ending first, up to three): name with an accent stripe,
 * a countdown bar that turns gold in the last minute and red in the last ten seconds, the top five
 * and your own row, which flashes and floats a "+N" when you score. Rows slide when places change.
 *
 * <p>It widens a little for long event and player names (up to about two fifths of the screen) and
 * never grows past about half the screen's height: with less room it shows the top three per card,
 * then fewer cards with a "+N more events" line.
 */
final class EventWidget implements HudWidget {
    static final int WIDTH = 164;
    private static final int MAX_WIDTH = 210;
    private static final int HEADER = 26;
    private static final int ROW = 11;
    private static final int TOP_ROWS = 5;
    private static final int SHORT_ROWS = 3;
    private static final int MAX_CARDS = 3;
    private static final int GAP = 4;
    private static final int PANEL = 0xE0121620;
    /** Share of the screen height the cards may take; the sidebar goes below them. */
    private static final float MAX_SCREEN_SHARE = 0.55f;
    private static final UUID SAMPLE_A = new UUID(1, 1);
    private static final UUID SAMPLE_B = new UUID(2, 2);
    private static final UUID SAMPLE_C = new UUID(3, 3);

    /** Which events get a card and how many rows each shows. */
    private record Plan(List<EventPayloads.EventView> cards, int rows, int width) {
    }

    /** Where each row is drawn, in rows from the top, easing towards its real place. */
    private final Map<String, Map<UUID, Float>> rowPositions = new HashMap<>();
    /** The longest join-window wait seen per event, to draw how far along it is. */
    private final Map<String, Long> lobbyLengths = new HashMap<>();
    private long lastFrame;

    @Override
    public Identifier id() {
        return SkyRpgEvents.id("events");
    }

    @Override
    public Component name() {
        return Component.literal("Events");
    }

    @Override
    public int width(Font font) {
        return plan(font, true).width();
    }

    @Override
    public int height(Font font) {
        Plan plan = plan(font, true);
        return Math.max(20, cardsHeight(plan) + moreLine(plan));
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_RIGHT;
    }

    @Override
    public int defaultX() {
        return -4;
    }

    @Override
    public int defaultY() {
        return 4;
    }

    @Override
    public boolean visible() {
        return !ClientEvents.events().isEmpty();
    }

    /** The events that could get a card; in the HUD editor a sample one if nothing is running. */
    private static List<EventPayloads.EventView> candidates(boolean editing) {
        List<EventPayloads.EventView> events = ClientEvents.events();
        if (events.isEmpty()) {
            return editing ? List.of(sample()) : List.of();
        }
        return events.subList(0, Math.min(MAX_CARDS, events.size()));
    }

    /**
     * As many cards as fit the height limit with the top five each, or with the top three if that fits
     * more (or is the only way to fit one). The limit leaves room for the sidebar under the cards when
     * it shares their column.
     */
    private Plan plan(Font font, boolean editing) {
        List<EventPayloads.EventView> candidates = candidates(editing);
        Minecraft minecraft = Minecraft.getInstance();
        float scale = HudManager.layout(this).scale;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int maxHeight = Math.round(screenHeight * MAX_SCREEN_SHARE / scale);
        int sidebarRoom = Sidebar.roomLeftBelow(font, this, Math.round(WIDTH * scale), screenWidth, screenHeight);
        if (sidebarRoom >= 0) {
            maxHeight = Math.min(maxHeight, Math.round(sidebarRoom / scale));
        }
        int maxWidth = Mth.clamp(Math.round(screenWidth * 0.4f / scale), WIDTH, MAX_WIDTH);
        Plan best = null;
        int bestHeight = 0;
        for (int rows : new int[] {TOP_ROWS, SHORT_ROWS}) {
            int count = 0;
            int height = 0;
            for (EventPayloads.EventView view : candidates) {
                int next = height + (count > 0 ? GAP : 0) + cardHeight(view, rows);
                int more = count + 1 < ClientEvents.events().size() ? ROW : 0;
                if (count > 0 && next + more > maxHeight) {
                    break;
                }
                height = next;
                count++;
            }
            boolean better = best == null || count > best.cards().size()
                    || (count == best.cards().size() && bestHeight > maxHeight && height < bestHeight);
            if (better) {
                List<EventPayloads.EventView> cards = candidates.subList(0, count);
                best = new Plan(cards, rows, Mth.clamp(neededWidth(font, cards, rows), WIDTH, maxWidth));
                bestHeight = height;
            }
            if (count == candidates.size() && height <= maxHeight) {
                break;
            }
        }
        return best;
    }

    /** Wide enough for the longest event name next to its clock, and the longest shown player name next to their score. */
    private static int neededWidth(Font font, List<EventPayloads.EventView> cards, int rows) {
        int width = WIDTH;
        for (EventPayloads.EventView view : cards) {
            width = Math.max(width, 22 + font.width(view.name()) + 12 + font.width("Starts 0:00") + 7);
            List<EventPayloads.Entry> top = view.top().subList(0, Math.min(rows, view.top().size()));
            for (EventPayloads.Entry entry : top) {
                width = Math.max(width, 31 + font.width(entry.name()) + 10 + font.width(Msg.number(entry.score())) + 7);
            }
        }
        return width;
    }

    private static int cardsHeight(Plan plan) {
        int height = 0;
        for (EventPayloads.EventView view : plan.cards()) {
            height += cardHeight(view, plan.rows()) + GAP;
        }
        return Math.max(0, height - GAP);
    }

    private static int moreLine(Plan plan) {
        return ClientEvents.events().size() > plan.cards().size() && !plan.cards().isEmpty() ? GAP + ROW - 2 : 0;
    }

    private static EventPayloads.EventView sample() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID self = minecraft.player != null ? minecraft.player.getUUID() : new UUID(4, 4);
        String name = minecraft.player != null ? minecraft.player.getScoreboardName() : "You";
        long now = ClientEvents.serverNow();
        return new EventPayloads.EventView("sample", "Timber Rush", "Chop the most logs!", 0xFFC8913C,
                Identifier.withDefaultNamespace("oak_log"), "logs", now - 108_000, now + 192_000, false, true, true, 4,
                List.of(new EventPayloads.Entry(SAMPLE_A, "Steve", 26, 1), new EventPayloads.Entry(self, name, 19, 2),
                        new EventPayloads.Entry(SAMPLE_B, "Alex", 12, 3), new EventPayloads.Entry(SAMPLE_C, "Notch", 4, 4)),
                19, 2);
    }

    private static int shownRows(EventPayloads.EventView view, int rows) {
        return Math.max(1, Math.min(rows, view.top().size()));
    }

    /** Your own row goes under the top rows when you're not in them. */
    private static boolean needsSelfRow(EventPayloads.EventView view, int rows) {
        if (!view.joined()) {
            return false;
        }
        int shown = Math.min(rows, view.top().size());
        for (int i = 0; i < shown; i++) {
            if (Ui.isSelf(view.top().get(i).player())) {
                return false;
            }
        }
        return true;
    }

    private static String hint(EventPayloads.EventView view) {
        boolean lobby = ClientEvents.millisToStart(view) > 0;
        if (view.optIn() && !view.joined()) {
            return view.chatJoin() ? "Type .join in chat to play" : "Type /event join to play";
        }
        if (lobby) {
            return view.optIn() ? "You're in! Get ready…" : "Get ready…";
        }
        return null;
    }

    private static int cardHeight(EventPayloads.EventView view, int rows) {
        int height = HEADER + shownRows(view, rows) * ROW;
        if (needsSelfRow(view, rows)) {
            height += 3 + ROW;
        }
        if (hint(view) != null) {
            height += ROW;
        }
        return height + 3;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Plan plan = plan(font, editing);
        int width = plan.width();
        Set<String> ids = new HashSet<>();
        int y = 0;
        for (EventPayloads.EventView view : plan.cards()) {
            ids.add(view.id());
            long age = now - ClientEvents.firstSeen(view.id());
            float in = view.id().equals("sample") ? 1 : Draw.easeOut(age / 320f);
            g.pose().pushMatrix();
            g.pose().translate((1 - in) * 14, 0);
            drawCard(g, font, view, y, in, now, dt, plan.rows(), width);
            g.pose().popMatrix();
            y += cardHeight(view, plan.rows()) + GAP;
        }
        int more = ClientEvents.events().size() - plan.cards().size();
        if (more > 0 && !plan.cards().isEmpty()) {
            String text = "+" + more + (more == 1 ? " more event" : " more events");
            int textWidth = font.width(text);
            Draw.rounded(g, width - textWidth - 9, y - 1, textWidth + 9, ROW, Draw.withAlpha(PANEL, 0.85f));
            Draw.rightText(g, font, text, width - 5, y + 1, Theme.MUTED);
        }
        rowPositions.keySet().retainAll(ids);
        lobbyLengths.keySet().retainAll(ids);
    }

    private void drawCard(GuiGraphicsExtractor g, Font font, EventPayloads.EventView view, int y0, float alpha, long now, float dt,
                          int rows, int width) {
        int color = view.color();
        int height = cardHeight(view, rows);
        Draw.panel(g, 0, y0, width, height, Draw.withAlpha(PANEL, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, color, 0.4f), alpha));
        g.fill(2, y0, width - 2, y0 + 1, Draw.withAlpha(color, alpha));
        g.fillGradient(1, y0 + 1, width - 1, y0 + 16, Draw.withAlpha(color, 0.12f * alpha), 0);
        g.fill(1, y0 + 4, 3, y0 + 14, Draw.withAlpha(color, alpha));

        // Header: icon, name, clock.
        if (alpha > 0.6f) {
            Draw.item(g, Ui.icon(view.icon()), 6, y0 + 4, 0.75f);
        }
        long toStart = ClientEvents.millisToStart(view);
        String time;
        int timeColor;
        float progress;
        float blink = 1;
        if (toStart > 0) {
            long lobby = lobbyLengths.merge(view.id(), toStart, Math::max);
            time = "Starts " + Ui.clock(toStart);
            timeColor = Theme.AQUA;
            progress = 1 - toStart / (float) lobby;
        } else {
            long left = ClientEvents.millisLeft(view);
            long seconds = (left + 999) / 1000;
            time = Ui.clock(left);
            progress = left / (float) Math.max(1, view.endsAt() - view.beginsAt());
            if (seconds <= 10) {
                timeColor = Theme.RED;
                blink = 0.55f + 0.45f * (float) Math.abs(Math.cos(now / 180.0));
            } else {
                timeColor = seconds <= 60 ? Theme.ACCENT : Draw.lighten(color, 0.15f);
            }
        }
        int timeWidth = font.width(time);
        Draw.fitText(g, font, view.name(), 22, y0 + 6, width - 22 - timeWidth - 12, Draw.withAlpha(Draw.lighten(color, 0.2f), alpha));
        Draw.rightText(g, font, time, width - 7, y0 + 6, Draw.withAlpha(timeColor, alpha * blink));
        int barColor = timeColor == Theme.RED ? Draw.lighten(Theme.RED, 0.3f * (1 - blink)) : timeColor;
        Draw.bar(g, 6, y0 + 17, width - 12, 4, progress, Draw.withAlpha(barColor, alpha));

        // Rows: the top five (or three), sliding when places change.
        int rowsY = y0 + HEADER;
        List<EventPayloads.Entry> top = view.top().subList(0, Math.min(rows, view.top().size()));
        Map<UUID, Float> positions = rowPositions.computeIfAbsent(view.id(), id -> new HashMap<>());
        Set<UUID> present = new HashSet<>();
        if (top.isEmpty()) {
            Draw.fitText(g, font, toStart > 0 ? "Nobody's in yet" : "No scores yet — be the first!", 8, rowsY + 1, width - 14,
                    Draw.withAlpha(Theme.DIM, alpha));
        }
        for (int i = 0; i < top.size(); i++) {
            EventPayloads.Entry entry = top.get(i);
            present.add(entry.player());
            float target = i;
            float position = positions.getOrDefault(entry.player(), target);
            position += (target - position) * Math.min(1, dt * 12);
            if (Math.abs(target - position) < 0.01f) {
                position = target;
            }
            positions.put(entry.player(), position);
            drawRow(g, font, view, entry, rowsY + Math.round(position * ROW), alpha, now, width);
        }
        positions.keySet().retainAll(present);
        int y = rowsY + shownRows(view, rows) * ROW;

        if (needsSelfRow(view, rows)) {
            for (int x = 6; x < width - 6; x += 3) {
                g.fill(x, y + 1, x + 1, y + 2, Draw.withAlpha(Theme.BORDER, alpha));
            }
            Minecraft minecraft = Minecraft.getInstance();
            UUID self = minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
            String name = minecraft.player != null ? minecraft.player.getScoreboardName() : "You";
            drawRow(g, font, view, new EventPayloads.Entry(self, name, view.selfScore(), view.selfPlace()), y + 3, alpha, now, width);
            y += 3 + ROW;
        }
        String hint = hint(view);
        if (hint != null) {
            Draw.fitText(g, font, hint, 8, y + 1, width - 14, Draw.withAlpha(view.joined() ? Theme.MUTED : Theme.AQUA, alpha));
        }
    }

    private static void drawRow(GuiGraphicsExtractor g, Font font, EventPayloads.EventView view, EventPayloads.Entry entry, int y, float alpha,
                                long now, int width) {
        boolean self = Ui.isSelf(entry.player());
        ClientEvents.Pulse pulse = self && !view.id().equals("sample") ? ClientEvents.pulse(view.id()) : null;
        float t = pulse == null ? 1 : (now - pulse.at()) / 1000f;
        if (self) {
            float flash = t < 0.6f ? 0.4f * (1 - t / 0.6f) : 0;
            Draw.rounded(g, 3, y - 2, width - 6, ROW + 1, Draw.withAlpha(view.color(), (0.16f + flash) * alpha));
        }
        int place = entry.place();
        // Places past 99 would run into the face; "99+" never does.
        String placeText = place <= 0 ? "-" : place > 99 ? "99+" : Integer.toString(place);
        Draw.fit(g, font, Component.literal(placeText), 16, y, 15, Draw.Align.RIGHT, Draw.withAlpha(place > 0 ? Ui.medal(place) : Theme.DIM, alpha));
        Ui.face(g, entry.player(), 19, y - 1, 8, Draw.withAlpha(0xFFFFFFFF, alpha));

        String score = Draw.numberFor(font, Msg.number(entry.score()), (width - 31 - 10) / 2);
        int scoreWidth = font.width(score);
        int nameColor = self ? Draw.lighten(view.color(), 0.45f) : Theme.TEXT;
        Draw.fitText(g, font, entry.name(), 31, y, width - 31 - 10 - scoreWidth, Draw.withAlpha(nameColor, alpha));

        // The score bumps up a size when it grows, and the gain floats away above it.
        float bump = t < 0.35f ? 1 + 0.35f * (1 - t / 0.35f) : 1;
        int scoreColor = self ? Theme.TEXT : Theme.MUTED;
        if (bump > 1) {
            g.pose().pushMatrix();
            g.pose().translate(width - 7 - scoreWidth * bump, y - (bump - 1) * 4);
            g.pose().scale(bump, bump);
            g.text(font, score, 0, 0, Draw.withAlpha(Theme.GREEN, alpha), false);
            g.pose().popMatrix();
        } else {
            Draw.rightText(g, font, score, width - 7, y, Draw.withAlpha(scoreColor, alpha));
        }
        if (pulse != null && t < 1) {
            String gain = "+" + Msg.number(pulse.delta());
            Draw.text(g, font, gain, width - 7 - scoreWidth - 4 - font.width(gain), y - Math.round(t * 9), Draw.withAlpha(Theme.GREEN, (1 - t) * alpha));
        }
    }
}
