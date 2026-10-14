package com.skyrpg.stats.client;

import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.stats.PlayerStats;
import com.skyrpg.stats.SkyRpgStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every stat as a tile, grouped; hovering one shows what it does and where the bonuses come from. */
final class StatsPage implements MenuPage {
    private static final int HEADER_HEIGHT = 16;
    private static final int TILE_HEIGHT = 22;
    private static final int GAP = 4;
    private static final int MIN_TILE_WIDTH = 120;

    private static final Map<String, List<StatType>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Combat", List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH, StatTypes.CRIT_CHANCE,
                StatTypes.CRIT_DAMAGE, StatTypes.SPEED, StatTypes.INTELLIGENCE));
        GROUPS.put("Gathering", List.of(StatTypes.MINING_SPEED, StatTypes.MINING_FORTUNE, StatTypes.FARMING_FORTUNE, StatTypes.FORAGING_FORTUNE,
                StatTypes.FISHING_SPEED, StatTypes.SEA_CREATURE_CHANCE, StatTypes.FISHING_FORTUNE));
        GROUPS.put("Wisdom", List.of(StatTypes.FARMING_WISDOM, StatTypes.MINING_WISDOM, StatTypes.FORAGING_WISDOM, StatTypes.COMBAT_WISDOM,
                StatTypes.FISHING_WISDOM));
        GROUPS.put("Other", List.of(StatTypes.MAGIC_FIND));
    }

    private double scroll;

    @Override
    public Identifier id() {
        return SkyRpgStats.id("stats");
    }

    @Override
    public Component title() {
        return Component.literal("Stats");
    }

    @Override
    public Component subtitle() {
        return Component.literal("What makes you stronger. Hover a stat for details");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        // Two columns when the names and values fit side by side, else one.
        int columns = columns(ctx);
        int tileWidth = (ctx.width() - GAP * (columns - 1)) / columns;
        int contentHeight = contentHeight(columns);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - ctx.height()));
        boolean mouseInArea = ctx.mouseY() >= ctx.y() && ctx.mouseY() < ctx.y() + ctx.height();

        StatType hovered = null;
        g.enableScissor(ctx.x(), ctx.y(), ctx.x() + ctx.width(), ctx.y() + ctx.height());
        int y = ctx.y() - (int) scroll;
        int index = 0;
        for (Map.Entry<String, List<StatType>> group : GROUPS.entrySet()) {
            Draw.text(g, font, group.getKey().toUpperCase(Locale.ROOT), ctx.x() + 2, y + 4, Theme.DIM);
            y += HEADER_HEIGHT;
            List<StatType> stats = group.getValue();
            for (int i = 0; i < stats.size(); i++) {
                StatType stat = stats.get(i);
                float appear = Draw.easeOut((ctx.age() - index++ * 0.02f) / 0.25f);
                int x = ctx.x() + (i % columns) * (tileWidth + GAP);
                int ty = y + (i / columns) * (TILE_HEIGHT + GAP) + Math.round((1 - appear) * 4);
                boolean isHovered = mouseInArea && ctx.hovered(x, ty, tileWidth, TILE_HEIGHT);
                drawTile(ctx, stat, x, ty, tileWidth, isHovered);
                if (isHovered) {
                    hovered = stat;
                }
            }
            y += ((stats.size() + columns - 1) / columns) * (TILE_HEIGHT + GAP) + 2;
        }
        g.disableScissor();

        if (contentHeight > ctx.height()) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, ctx.y(), ctx.height(), contentHeight, scroll, Theme.DIM);
        }
        if (hovered != null) {
            drawTooltip(ctx, hovered);
        }
    }

    private void drawTile(PageContext ctx, StatType stat, int x, int y, int width, boolean hovered) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        double value = PlayerStats.total(ctx.player(), stat);
        boolean boosted = !PlayerStats.breakdown(ctx.player(), stat).isEmpty();
        Draw.panel(g, x, y, width, TILE_HEIGHT, hovered ? Theme.CARD_HOVER : Theme.CARD,
                hovered ? Draw.mix(Theme.CARD_BORDER, stat.color(), 0.6f) : Theme.CARD_BORDER);
        Draw.text(g, font, stat.symbol(), x + 8, y + 7, stat.color());
        // The value keeps its width (numbers shorten if even that won't fit); the name takes the rest.
        int room = width - 20 - 8;
        String shown = Draw.numberFor(font, stat.format(value), room - 40);
        int valueWidth = Math.min(font.width(shown), room - 40);
        Draw.fitNumber(g, font, stat.format(value), x + width - 8, y + 7, room - 40, Draw.Align.RIGHT, boosted ? stat.color() : Theme.TEXT);
        Draw.fitText(g, font, stat.name(), x + 20, y + 7, room - valueWidth - 6, boosted ? Theme.TEXT : Theme.MUTED);
    }

    /** Two tiles a row unless that makes them too narrow for a name and a value (names shrink a little first). */
    private static int columns(PageContext ctx) {
        return (ctx.width() - GAP) / 2 >= MIN_TILE_WIDTH ? 2 : 1;
    }

    private void drawTooltip(PageContext ctx, StatType stat) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(stat.symbol() + " " + stat.name() + "  ").withColor(stat.color())
                .append(Component.literal(stat.format(PlayerStats.total(ctx.player(), stat))).withColor(Theme.TEXT)));
        lines.add(Component.literal(stat.description()).withColor(Theme.MUTED));
        lines.add(Component.empty());
        lines.add(Component.literal("Base  ").withColor(Theme.DIM).append(Component.literal(stat.format(stat.base())).withColor(Theme.TEXT)));
        PlayerStats.breakdown(ctx.player(), stat).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(source -> lines.add(Component.literal(source.getKey() + "  ").withColor(Theme.DIM)
                        .append(Component.literal(stat.formatBonus(source.getValue())).withColor(source.getValue() >= 0 ? Theme.GREEN : Theme.RED))));
        if (stat == StatTypes.INTELLIGENCE) {
            lines.add(Component.literal("Max mana  ").withColor(Theme.DIM)
                    .append(Component.literal(Msg.number(PlayerStats.maxMana(ctx.player()))).withColor(Theme.AQUA)));
        }
        ctx.tooltip(lines, stat.color());
    }

    private static int contentHeight(int columns) {
        int height = 0;
        for (List<StatType> stats : GROUPS.values()) {
            height += HEADER_HEIGHT + ((stats.size() + columns - 1) / columns) * (TILE_HEIGHT + GAP) + 2;
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scroll -= amount * 24;
        return true;
    }
}
