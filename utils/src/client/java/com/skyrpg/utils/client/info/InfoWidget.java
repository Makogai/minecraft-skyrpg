package com.skyrpg.utils.client.info;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * A compact box of small facts, each line switchable in Settings: coordinates and facing, biome, the day and
 * time, and FPS. Sits under the minimap by default.
 */
public final class InfoWidget implements HudWidget {
    private static final int PAD = 5;
    private static final int LINE = 10;
    private static final int LABEL = 30;
    /** As wide as the minimap, so the two line up when stacked. */
    private static final int MIN_WIDTH = 102;

    private record Line(String label, Component value) {
    }

    @Override
    public Identifier id() {
        return SkyRpgUtilsClient.id("info");
    }

    @Override
    public Component name() {
        return Component.literal("Info (coordinates, biome, time)");
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
        return 108;
    }

    @Override
    public boolean visible() {
        return !lines(false).isEmpty();
    }

    private static List<Line> lines(boolean editing) {
        List<Line> lines = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            if (editing) {
                lines.add(new Line("XYZ", Component.literal("120 64 -45  N")));
                lines.add(new Line("Biome", Component.literal("Plains")));
                lines.add(new Line("Day", Component.literal("3  ·  14:30")));
            }
            return lines;
        }
        if (UtilsSettings.coords().get()) {
            lines.add(new Line("XYZ", Component.literal(player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ() + "  ")
                    .append(Component.literal(facing(player.getDirection())).withColor(Theme.ACCENT))));
        }
        if (UtilsSettings.biome().get()) {
            level.getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> lines.add(new Line("Biome", biomeName(key.identifier()))));
        }
        if (UtilsSettings.time().get()) {
            long ticks = level.getDefaultClockTime();
            long day = ticks / 24000 + 1;
            long time = ticks % 24000;
            int hours = (int) ((time / 1000 + 6) % 24);
            int minutes = (int) (time % 1000 * 60 / 1000);
            lines.add(new Line("Day", Component.literal(day + "  ·  " + String.format(Locale.ROOT, "%02d:%02d", hours, minutes))));
        }
        if (UtilsSettings.fps().get()) {
            lines.add(new Line("FPS", Component.literal(Integer.toString(minecraft.getFps()))));
        }
        return lines;
    }

    /** The biome's translated name, or its id made readable if no language file names it. */
    private static Component biomeName(Identifier id) {
        String key = Util.makeDescriptionId("biome", id);
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        String path = id.getPath().replace('_', ' ');
        return Component.literal(path.isEmpty() ? path : path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1));
    }

    private static String facing(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "";
        };
    }

    @Override
    public int width(Font font) {
        int widest = 0;
        for (Line line : lines(true)) {
            widest = Math.max(widest, font.width(line.value()));
        }
        return Math.max(MIN_WIDTH, PAD + LABEL + widest + PAD);
    }

    @Override
    public int height(Font font) {
        return PAD * 2 + Math.max(1, lines(true).size()) * LINE - 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        List<Line> lines = lines(editing);
        int width = width(font);
        Draw.panel(g, 0, 0, width, height(font), 0xE0121620, Theme.BORDER);
        int y = PAD;
        for (Line line : lines) {
            Draw.text(g, font, line.label(), PAD, y, Theme.DIM);
            Draw.fitText(g, font, line.value(), PAD + LABEL, y, width - PAD * 2 - LABEL, Theme.TEXT);
            y += LINE;
        }
    }
}
