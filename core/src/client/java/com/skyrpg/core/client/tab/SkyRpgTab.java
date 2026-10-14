package com.skyrpg.core.client.tab;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Replaces the vanilla player list (Tab) with the SkyRPG one: players on the left, info panels from
 * every mod on the right. Can be switched back to vanilla in Settings.
 */
public final class SkyRpgTab {
    /** Narrowest the players column gets, and the most it grows to for long names and titles. */
    private static final int PLAYERS_MIN = 110;
    private static final int PLAYERS_MAX = 320;
    /** Face and padding left of a name, and the ping bars plus padding right of the row. */
    private static final int ROW_LEFT = 19;
    private static final int ROW_RIGHT = 22;
    /** Widest the whole list gets, on big screens. */
    private static final int MAX_WIDTH = 640;
    private static final int BASE_WIDTH = 470;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int MAX_PLAYERS = 40;
    private static final List<TabPanel> PANELS = new ArrayList<>();
    private static final List<java.util.function.Function<java.util.UUID, Integer>> MARKS = new ArrayList<>();
    private static Setting enabled;

    private SkyRpgTab() {
    }

    public static void registerPanel(TabPanel panel) {
        PANELS.add(panel);
        PANELS.sort(Comparator.comparingInt(TabPanel::order));
    }

    /**
     * A coloured mark at the left edge of a player's row (the party mod marks party members). {@code mark} gets the player's
     * UUID and returns an ARGB colour, or null for no mark.
     */
    public static void registerMark(java.util.function.Function<java.util.UUID, Integer> mark) {
        MARKS.add(mark);
    }

    public static void init() {
        enabled = SkyRpgSettings.toggle("interface.tab_list", SkyRpgSettings.INTERFACE, "SkyRPG tab list",
                "Show players and your profile info when holding Tab", true, null);
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, vanilla -> (g, deltaTracker) -> {
            if (!enabled.get()) {
                vanilla.extractRenderState(g, deltaTracker);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.getConnection() != null && minecraft.options.keyPlayerList.isDown()) {
                render(g, minecraft);
            }
        });
        registerPanel(new TabPanel("World", Theme.GREEN, 100, player -> {
            Minecraft minecraft = Minecraft.getInstance();
            PlayerInfo self = minecraft.getConnection().getPlayerInfo(player.getUUID());
            return List.of(
                    new TabPanel.Line("Area", areaName(player.level().dimension().identifier()), Theme.TEXT),
                    new TabPanel.Line("Online", Integer.toString(minecraft.getConnection().getListedOnlinePlayers().size()), Theme.TEXT),
                    new TabPanel.Line("Ping", (self == null ? "?" : self.getLatency()) + " ms", Theme.TEXT));
        }));
    }

    private static final float BADGE_SCALE = 0.6f;

    /** A player's title from their team: the prefix without its brackets, its colour, and the team's name colour. */
    private record Title(String text, String icon, int color, int nameColor) {
    }

    private static Title title(PlayerInfo info) {
        PlayerTeam team = info.getTeam();
        if (team == null) {
            return null;
        }
        int nameColor = team.getColor().map(color -> 0xFF000000 | color.rgb()).orElse(0);
        Component prefix = team.getPlayerPrefix();
        String text = prefix.getString().strip();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        if (text.isEmpty()) {
            return new Title("", "", 0, nameColor);
        }
        int[] color = {nameColor == 0 ? Theme.ACCENT : nameColor};
        prefix.visit((style, part) -> {
            if (style.getColor() != null && !part.replaceAll("[\\[\\]\\s]", "").isEmpty()) {
                color[0] = 0xFF000000 | style.getColor().getValue();
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new Title(text, text.substring(0, text.offsetByCodePoints(0, 1)), color[0], nameColor);
    }

    private static Component badgeText(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withBold(true));
    }

    /** The pill's width for {@code text}: bold text at {@link #BADGE_SCALE}, plus 3px either side. Measured bold, as drawn. */
    private static int badgeWidth(Font font, String text) {
        return (int) Math.ceil(font.width(badgeText(text)) * BADGE_SCALE) + 6;
    }

    /** {@code text} cut to fit a pill of {@code maxWidth}, ending in an ellipsis when it has to be (at least one letter). */
    private static String badgeFit(Font font, String text, int maxWidth) {
        if (badgeWidth(font, text) <= maxWidth) {
            return text;
        }
        int[] points = text.codePoints().toArray();
        for (int n = points.length - 1; n >= 1; n--) {
            String cut = new String(points, 0, n).stripTrailing() + "…";
            if (badgeWidth(font, cut) <= maxWidth) {
                return cut;
            }
        }
        return new String(points, 0, 1);
    }

    private static void drawBadge(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        Draw.rounded(g, x, y, badgeWidth(font, text), 8, Draw.withAlpha(color, 0.22f));
        Draw.scaledText(g, font, badgeText(text), x + 3, y + 2, BADGE_SCALE, color);
    }

    /** What a row needs: face, name, a gap and the pill if there is one, ping bars. */
    private static int rowWidth(Font font, PlayerInfo info) {
        Title title = title(info);
        int content = font.width(info.getProfile().name());
        if (title != null && !title.text().isEmpty()) {
            content += 3 + badgeWidth(font, title.text());
        }
        return ROW_LEFT + content + ROW_RIGHT;
    }

    private static String areaName(Identifier dimension) {
        return switch (dimension.toString()) {
            case "skyrpg_hub:hub" -> "Hub";
            case "minecraft:overworld" -> "Survival World";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimension.getPath();
        };
    }

    private static void render(GuiGraphicsExtractor g, Minecraft minecraft) {
        Font font = minecraft.font;
        List<PlayerInfo> players = new ArrayList<>(minecraft.getConnection().getListedOnlinePlayers());
        players.sort(Comparator.comparing(info -> info.getProfile().name().toLowerCase()));

        // The players column is as wide as its widest row (name + title pill), so nothing is cut off; the list
        // grows with it on big screens. On a small screen the column keeps its content and the panels go single-file.
        int wanted = PLAYERS_MIN;
        for (int i = 0; i < Math.min(players.size(), MAX_PLAYERS); i++) {
            wanted = Math.max(wanted, rowWidth(font, players.get(i)));
        }
        wanted = Math.min(wanted, PLAYERS_MAX);
        int maxWidth = Math.max(160, Math.min(g.guiWidth() - 20, MAX_WIDTH));
        int width = Math.min(maxWidth, Math.max(BASE_WIDTH, wanted + PAD * 3 + 296));
        // Keep room for at least one panel column; only then does a very long row get cut (name shrinks, pill gets "...").
        int playersWidth = Mth.clamp(wanted, Math.min(PLAYERS_MIN, width / 2), Math.max(PLAYERS_MIN, width - PAD * 3 - 130));
        playersWidth = Math.min(playersWidth, Math.max(60, width - PAD * 3 - 100));
        int panelArea = width - playersWidth - PAD * 3;
        int columns = panelArea >= 260 ? 2 : 1;
        int panelWidth = (panelArea - (columns - 1) * PAD) / columns;

        // Lay panels out into the shortest column first.
        int[] columnHeights = new int[columns];
        List<int[]> placements = new ArrayList<>();
        List<List<TabPanel.Line>> contents = new ArrayList<>();
        for (TabPanel panel : PANELS) {
            List<TabPanel.Line> lines = panel.lines().apply(minecraft.player);
            int column = 0;
            for (int c = 1; c < columns; c++) {
                if (columnHeights[c] < columnHeights[column]) {
                    column = c;
                }
            }
            int height = 18 + lines.size() * LINE + 4;
            placements.add(new int[] {column, columnHeights[column], height});
            contents.add(lines);
            columnHeights[column] += height + PAD;
        }
        int headerHeight = 22;
        int panelsHeight = java.util.Arrays.stream(columnHeights).max().orElse(0) - PAD;
        // As many players as fit next to the panels (or the screen), then "+N more".
        int room = Math.max(panelsHeight, g.guiHeight() - 20 - headerHeight - PAD * 2);
        int fitRows = Math.max(1, (room - 18 - 4) / (LINE + 1));
        int shownPlayers = Math.min(players.size(), Math.min(MAX_PLAYERS, players.size() > fitRows ? fitRows - 1 : fitRows));
        int playersHeight = 18 + shownPlayers * (LINE + 1) + (players.size() > shownPlayers ? LINE : 0) + 4;
        int bodyHeight = Math.max(playersHeight, panelsHeight);
        int height = headerHeight + bodyHeight + PAD * 2;
        // Still taller than the screen (lots of panels): shrink the whole list to fit.
        float scale = Math.max(0.6f, Math.min(1f, (g.guiHeight() - 20) / (float) height));
        int x = (g.guiWidth() - width) / 2;
        int y = 10;
        g.pose().pushMatrix();
        g.pose().translate(g.guiWidth() / 2f, y);
        g.pose().scale(scale, scale);
        g.pose().translate(-g.guiWidth() / 2f, -y);

        Draw.panel(g, x, y, width, height, Theme.WINDOW, Theme.BORDER);
        g.fill(x + 2, y, x + width - 2, y + 1, Draw.withAlpha(Theme.ACCENT, 0.85f));
        Draw.text(g, font, Component.literal("SKY").withColor(Theme.TEXT).append(Component.literal("RPG").withColor(Theme.ACCENT)), x + PAD + 2, y + 8, Theme.TEXT);
        Draw.rightText(g, font, players.size() + (players.size() == 1 ? " player online" : " players online"), x + width - PAD - 2, y + 8, Theme.MUTED);
        g.fill(x + 1, y + headerHeight, x + width - 1, y + headerHeight + 1, Theme.BORDER);

        int bodyY = y + headerHeight + PAD;
        // Players
        int px = x + PAD;
        Draw.panel(g, px, bodyY, playersWidth, bodyHeight, Theme.CARD, Theme.CARD_BORDER);
        Draw.text(g, font, "PLAYERS", px + 7, bodyY + 6, Theme.DIM);
        for (int i = 0; i < shownPlayers; i++) {
            PlayerInfo info = players.get(i);
            int rowY = bodyY + 18 + i * (LINE + 1);
            PlayerFaceExtractor.extractRenderState(g, info.getSkin(), px + 7, rowY, 8);
            for (java.util.function.Function<java.util.UUID, Integer> mark : MARKS) {
                Integer markColor = mark.apply(info.getProfile().id());
                if (markColor != null) {
                    g.fill(px + 3, rowY, px + 5, rowY + 8, markColor);
                    break;
                }
            }
            boolean self = info.getProfile().id().equals(minecraft.player.getUUID());
            String name = info.getProfile().name();
            Title title = title(info);
            int nameColor = self ? Theme.ACCENT : title != null && title.nameColor() != 0 ? title.nameColor() : Theme.TEXT;
            int nameX = px + ROW_LEFT;
            int rowEnd = px + playersWidth - ROW_RIGHT + 2;
            int rowRoom = rowEnd - nameX;
            // The name comes first at full width. A title pill takes the rest; if both don't fit, the pill gives
            // up width first (down to 2/5 of the row, ending in "..."), then the name shrinks or is cut.
            boolean badge = title != null && !title.text().isEmpty();
            int nameWidth = font.width(name);
            String shown = null;
            int pillWidth = 0;
            if (badge) {
                int full = badgeWidth(font, title.text());
                pillWidth = nameWidth + 3 + full <= rowRoom ? full : Math.min(full, Math.max(rowRoom * 2 / 5, Math.min(full, 24)));
                shown = badgeFit(font, title.text(), pillWidth);
                pillWidth = badgeWidth(font, shown);
            }
            int nameRoom = rowRoom - (badge ? pillWidth + 3 : 0);
            Draw.fitText(g, font, name, nameX, rowY, nameRoom, nameColor);
            if (badge) {
                drawBadge(g, font, shown, nameX + Math.min(nameWidth, nameRoom) + 3, rowY, title.color());
            }
            drawPing(g, px + playersWidth - 15, rowY, info.getLatency());
        }
        if (players.size() > shownPlayers) {
            Draw.text(g, font, "+" + (players.size() - shownPlayers) + " more", px + 7, bodyY + 18 + shownPlayers * (LINE + 1), Theme.DIM);
        }

        // Panels
        int panelsX = px + playersWidth + PAD;
        for (int i = 0; i < PANELS.size(); i++) {
            TabPanel panel = PANELS.get(i);
            int[] place = placements.get(i);
            int cx = panelsX + place[0] * (panelWidth + PAD);
            int cy = bodyY + place[1];
            Draw.panel(g, cx, cy, panelWidth, place[2], Theme.CARD, Theme.CARD_BORDER);
            g.fill(cx + 1, cy + 5, cx + 3, cy + 14, panel.color());
            Draw.fitText(g, font, panel.title().toUpperCase(), cx + 8, cy + 6, panelWidth - 15, panel.color());
            List<TabPanel.Line> lines = contents.get(i);
            for (int l = 0; l < lines.size(); l++) {
                drawLine(g, font, lines.get(l), cx + 8, cy + 18 + l * LINE, panelWidth - 15);
            }
        }
        g.pose().popMatrix();
    }

    /**
     * "Label ......... value": the value keeps its full width (or shortens its numbers if even that is
     * too wide) and the label takes what's left, ending in "…" if it has to.
     */
    private static void drawLine(GuiGraphicsExtractor g, Font font, TabPanel.Line line, int x, int y, int width) {
        int labelMin = Math.min(font.width(line.label()), Math.max(36, width * 2 / 5));
        String value = Draw.numberFor(font, line.value(), width - labelMin - 6);
        int valueWidth = Math.min(font.width(value), width - labelMin - 6);
        if (!value.isEmpty()) {
            Draw.fitRight(g, font, value, x + width, y, Math.max(0, width - labelMin - 6), line.valueColor());
        }
        Draw.fitText(g, font, line.label(), x, y, width - valueWidth - (valueWidth > 0 ? 6 : 0), Theme.MUTED);
    }

    /** Four little bars, lit by connection quality. */
    private static void drawPing(GuiGraphicsExtractor g, int x, int y, int latency) {
        int lit = latency < 0 ? 0 : latency < 150 ? 4 : latency < 300 ? 3 : latency < 600 ? 2 : 1;
        int color = lit >= 4 ? Theme.GREEN : lit == 3 ? Theme.ACCENT : Theme.RED;
        for (int i = 0; i < 4; i++) {
            int barHeight = 2 + i * 2;
            g.fill(x + i * 3, y + 8 - barHeight, x + i * 3 + 2, y + 8, i < lit ? color : Theme.TRACK);
        }
    }

    static Identifier id() {
        return SkyRpgCore.id("tab_list");
    }
}
