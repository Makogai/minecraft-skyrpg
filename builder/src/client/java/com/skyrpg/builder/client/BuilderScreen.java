package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.builder.Placement;
import com.skyrpg.builder.SkyRpgBuilder;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Builder Station menu. Left: the blueprint seen from above (scroll for its layers) with the controls that move,
 * turn and mirror its hologram. Right: the materials, delivered and still needed (click one to deliver it). Bottom:
 * what's going on and the big buttons (Build, Stop, Build again).
 */
public final class BuilderScreen extends Screen {
    static final int CYAN = 0xFF5CE1E6;
    private static final int ROW = 24;

    private BuilderPayloads.Menu menu;
    private final long openedAt = Util.getMillis();
    private int left;
    private int top;
    private int w;
    private int h;
    private double scroll;
    /** -1: seen from above; otherwise the layer shown. */
    private int viewLayer = -1;
    private boolean followBuild = true;
    private long lastRefresh = Util.getMillis();
    private final List<Btn> buttons = new ArrayList<>();
    private LayerView view;
    private boolean[] builtCells;
    private long builtCheckedAt;
    private int builtLayer = -1;

    /** A clickable box drawn this frame. */
    private record Btn(int x, int y, int w, int h, String label, int style, boolean enabled, Runnable action, List<Component> tooltip) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int NORMAL = 0;
    private static final int PRIMARY = 1;
    private static final int DANGER = 2;
    private static final int TOGGLE_ON = 3;

    BuilderScreen(BuilderPayloads.Menu menu) {
        super(Component.literal("Builder Station"));
        this.menu = menu;
        if (menu.state() == BuilderPayloads.Menu.BUILDING) {
            viewLayer = menu.layer();
        }
    }

    BlockPos station() {
        return menu.pos();
    }

    BuilderPayloads.Menu menu() {
        return menu;
    }

    void update(BuilderPayloads.Menu updated) {
        if (!updated.blueprintId().equals(menu.blueprintId()) || updated.turns() != menu.turns() || updated.mirrored() != menu.mirrored()) {
            view = null;
            builtCells = null;
        }
        if (updated.state() == BuilderPayloads.Menu.BUILDING && followBuild) {
            viewLayer = updated.layer();
        } else if (menu.state() == BuilderPayloads.Menu.BUILDING && updated.state() != BuilderPayloads.Menu.BUILDING) {
            viewLayer = -1;
            followBuild = true;
        }
        this.menu = updated;
    }

    @Override
    protected void init() {
        w = Math.min(540, width - 16);
        h = Math.min(310, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(BuilderPayloads.Action.of(menu.pos(), "close"));
        super.onClose();
    }

    void send(String action, int x, int y, int z, String text) {
        ClientPlayNetworking.send(new BuilderPayloads.Action(menu.pos(), action, x, y, z, text));
    }

    void send(String action) {
        send(action, 0, 0, 0, "");
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void tick() {
        // The site can change under us (someone builds by hand): ask for a fresh look now and then.
        if (Util.getMillis() - lastRefresh > 3000 && menu.state() == BuilderPayloads.Menu.PLANNING) {
            lastRefresh = Util.getMillis();
            send("refresh");
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x9006121A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        buttons.clear();
        float time = (Util.getMillis() - openedAt) / 1000f;
        Draw.shadow(g, left, top, w, h, 6, 0.6f);
        Draw.panel(g, left, top, w, h, Theme.WINDOW, 0xFF1F4A55);
        int glowAlpha = (int) (40 + 30 * Math.sin(time * 2.2));
        g.fill(left + 1, top + 1, left + w - 1, top + 3, (glowAlpha << 24) | (CYAN & 0xFFFFFF));

        drawHeader(g);
        int bodyTop = top + 38;
        int bodyBottom = top + h - 44;
        int leftW = Math.min(250, (w - 30) / 2 + 20);
        int rightX = left + 10 + leftW + 10;
        int rightW = left + w - 10 - rightX;
        if (menu.state() == BuilderPayloads.Menu.EMPTY) {
            drawWelcome(g, left + 10, bodyTop, leftW, bodyBottom - bodyTop);
        } else {
            drawBlueprint(g, left + 10, bodyTop, leftW, bodyBottom - bodyTop, mouseX, mouseY);
        }
        drawMaterials(g, rightX, bodyTop, rightW, bodyBottom - bodyTop, mouseX, mouseY);
        drawFooter(g, bodyBottom + 6, mouseX, mouseY);

        for (Btn button : buttons) {
            drawButton(g, button, mouseX, mouseY);
            if (button.tooltip() != null && button.over(mouseX, mouseY)) {
                Draw.queueTooltip(button.tooltip(), CYAN);
            }
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY, width, height);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        Draw.item(g, new ItemStack(SkyRpgBuilder.STATION_ITEM), left + 10, top + 9, 1.5f);
        Draw.fit(g, font, Component.literal("BUILDER STATION"), left + 40, top + 9, w - 200, Draw.Align.LEFT, CYAN);
        String name = menu.blueprintName().isEmpty() ? "No blueprint loaded" : menu.blueprintName();
        String by = menu.author().isEmpty() ? "" : "  by " + menu.author();
        Draw.fit(g, font, Component.literal(name).append(Component.literal(by).withColor(Theme.DIM)), left + 40, top + 21, w - 200,
                Draw.Align.LEFT, Theme.TEXT);
        String status;
        int color;
        switch (menu.state()) {
            case BuilderPayloads.Menu.PLANNING -> {
                status = ready() ? "READY TO BUILD" : "PLANNING";
                color = ready() ? Theme.GREEN : Theme.ACCENT;
            }
            case BuilderPayloads.Menu.BUILDING -> {
                status = "BUILDING " + percent(menu.done(), menu.total()) + "%";
                color = CYAN;
            }
            case BuilderPayloads.Menu.DONE -> {
                status = "FINISHED";
                color = Theme.GREEN;
            }
            default -> {
                status = "IDLE";
                color = Theme.MUTED;
            }
        }
        int pillW = font.width(status) + 14;
        int pillX = left + w - 10 - pillW;
        Draw.rounded(g, pillX, top + 12, pillW, 15, Draw.withAlpha(color, 0.18f));
        Draw.outline(g, pillX, top + 12, pillW, 15, Draw.withAlpha(color, 0.6f));
        Draw.fit(g, font, Component.literal(status), pillX + pillW / 2, top + 16, pillW, Draw.Align.CENTER, color);
    }

    private void drawWelcome(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        Draw.panel(g, x, y, width, height, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("How it works"), x + 10, y + 9, width - 20, Draw.Align.LEFT, Theme.ACCENT);
        String[] steps = {
                "Choose a blueprint: a village house, or your own .litematic, .schem or .nbt file.",
                "Move its hologram where you want it. Turn it, mirror it.",
                "Deliver the materials it lists.",
                "Press Build and watch it rise from the ground up."
        };
        int ty = y + 26;
        for (int i = 0; i < steps.length; i++) {
            Draw.rounded(g, x + 10, ty, 14, 14, Draw.withAlpha(CYAN, 0.2f));
            Draw.fit(g, font, Component.literal(String.valueOf(i + 1)), x + 17, ty + 3, 14, Draw.Align.CENTER, CYAN);
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(steps[i]), width - 44);
            for (int l = 0; l < lines.size(); l++) {
                g.text(font, lines.get(l), x + 30, ty + 3 + l * 10, Theme.TEXT, false);
            }
            ty += Math.max(20, lines.size() * 10 + 8);
        }
        int bw = Math.min(160, width - 20);
        buttons.add(new Btn(x + (width - bw) / 2, Math.min(ty + 8, y + height - 28), bw, 20, "Choose blueprint", PRIMARY, true,
                this::openPicker, null));
    }

    private void drawBlueprint(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean planning = menu.state() == BuilderPayloads.Menu.PLANNING;
        int controlsH = planning ? 46 : 14;
        int canvasH = height - controlsH - 4;
        drawCanvas(g, x, y, width, canvasH, mouseX, mouseY);
        int cy = y + canvasH + 4;
        String info = menu.sizeX() + " × " + menu.sizeY() + " × " + menu.sizeZ() + "  ·  " + String.format("%,d", menu.total()) + " blocks"
                + (menu.turns() != 0 ? "  ·  turned " + menu.turns() * 90 + "°" : "") + (menu.mirrored() ? "  ·  mirrored" : "")
                + (menu.skipped() > 0 ? "  ·  " + menu.skipped() + " skipped" : "");
        Draw.fit(g, font, Component.literal(info), x + 2, cy + 2, width - 4, Draw.Align.LEFT, Theme.DIM);
        if (menu.skipped() > 0) {
            Draw.hoverTooltip(mouseX, mouseY, x, cy, width, 12, List.of(Component.literal(menu.skipped() + " blocks can't be built"),
                    Component.literal("Spawners, bedrock, portals, command blocks and").withColor(Theme.MUTED),
                    Component.literal("blocks from other mods are left out.").withColor(Theme.MUTED)), CYAN);
        }
        if (!planning) {
            return;
        }
        int by = cy + 14;
        List<Component> moveTip = List.of(Component.literal("Move the hologram"), Component.literal("Arrow keys work too · Shift: 5 blocks").withColor(Theme.MUTED));
        int bx = x;
        bx = arrow(bx, by, "←", -1, 0, 0, moveTip);
        bx = arrow(bx, by, "↑", 0, 0, -1, moveTip);
        bx = arrow(bx, by, "↓", 0, 0, 1, moveTip);
        bx = arrow(bx, by, "→", 1, 0, 0, moveTip);
        bx += 4;
        bx = arrow(bx, by, "▲", 0, 1, 0, List.of(Component.literal("Raise it"), Component.literal("Page Up").withColor(Theme.MUTED)));
        bx = arrow(bx, by, "▼", 0, -1, 0, List.of(Component.literal("Lower it"), Component.literal("Page Down").withColor(Theme.MUTED)));
        bx += 4;
        int tw = 40;
        buttons.add(new Btn(bx, by, tw, 16, "Turn", NORMAL, true, () -> send("turn", 1, 0, 0, ""),
                List.of(Component.literal("Turn it 90° clockwise"), Component.literal("R").withColor(Theme.MUTED))));
        bx += tw + 3;
        buttons.add(new Btn(bx, by, tw, 16, "Flip", menu.mirrored() ? TOGGLE_ON : NORMAL, true, () -> send("mirror"),
                List.of(Component.literal("Mirror it"), Component.literal("M").withColor(Theme.MUTED))));
        int ty = by + 19;
        int half = (width - 4) / 2;
        buttons.add(new Btn(x, ty, half, 13, "Hologram: " + (menu.preview() ? "on" : "off"), menu.preview() ? TOGGLE_ON : NORMAL, true,
                () -> send("preview"), List.of(Component.literal("Show the build in the world"))));
        buttons.add(new Btn(x + half + 4, ty, half, 13, "Clear site: " + (menu.clear() ? "on" : "off"), menu.clear() ? TOGGLE_ON : NORMAL, true,
                () -> send("clear"), List.of(Component.literal("Clear the site while building"),
                Component.literal("Blocks in the way are removed (dirt, plants and").withColor(Theme.MUTED),
                Component.literal("logs go to the leftovers; stone and ores don't).").withColor(Theme.MUTED),
                Component.literal("Off: blocks in the way are left, the build skips them.").withColor(Theme.MUTED))));
    }

    private int arrow(int x, int y, String label, int dx, int dy, int dz, List<Component> tip) {
        buttons.add(new Btn(x, y, 18, 16, label, NORMAL, true, () -> {
            int step = hasShift() ? 5 : 1;
            send("move", dx * step, dy * step, dz * step, "");
        }, tip));
        return x + 21;
    }

    private boolean hasShift() {
        return minecraft.hasShiftDown();
    }

    // ------------------------------------------------------------------ the blueprint canvas

    /** The blueprint as placed (turned/mirrored), seen from above: colours, highest blocks, and each layer. */
    private static final class LayerView {
        final Blueprint blueprint;
        final Placement placement;
        final int[] topColor;
        final int[] topY;
        final Map<Integer, int[]> layers = new HashMap<>();

        LayerView(Blueprint blueprint, int turns, boolean mirrored) {
            this.blueprint = blueprint;
            this.placement = Placement.of(blueprint, BlockPos.ZERO, turns, mirrored);
            int cells = placement.width() * placement.depth();
            topColor = new int[cells];
            topY = new int[cells];
            java.util.Arrays.fill(topY, -1);
            for (int entry = 0; entry < blueprint.count(); entry++) {
                int local = blueprint.positions[entry];
                int cell = cell(local);
                int y = blueprint.localY(local);
                if (y >= topY[cell]) {
                    topY[cell] = y;
                    topColor[cell] = color(blueprint.state(entry));
                }
            }
        }

        int cell(int local) {
            int lx = blueprint.localX(local);
            int lz = blueprint.localZ(local);
            return placement.placedX(lx, lz) + placement.placedZ(lx, lz) * placement.width();
        }

        int[] layer(int y) {
            return layers.computeIfAbsent(y, layer -> {
                int[] colors = new int[placement.width() * placement.depth()];
                for (int entry = blueprint.layerStart[layer]; entry < blueprint.layerStart[layer + 1]; entry++) {
                    colors[cell(blueprint.positions[entry])] = color(blueprint.state(entry));
                }
                return colors;
            });
        }

        static int color(BlockState state) {
            MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return mapColor == MapColor.NONE ? 0xFFB8C4D6 : 0xFF000000 | mapColor.col;
        }
    }

    private void drawCanvas(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        Draw.panel(g, x, y, width, height, 0xFF0B1220, Theme.CARD_BORDER);
        // Blueprint paper: a faint grid behind everything.
        for (int gx = x + 8; gx < x + width - 1; gx += 8) {
            g.fill(gx, y + 1, gx + 1, y + height - 1, 0x0D5CA8FF);
        }
        for (int gy = y + 8; gy < y + height - 1; gy += 8) {
            g.fill(x + 1, gy, x + width - 1, gy + 1, 0x0D5CA8FF);
        }
        Blueprint blueprint = Ghosts.BLUEPRINTS.get(menu.blueprintId());
        if (blueprint == null) {
            Draw.fit(g, font, Component.literal("Loading blueprint..."), x + width / 2, y + height / 2 - 4, width - 10, Draw.Align.CENTER, Theme.MUTED);
            return;
        }
        if (view == null || view.blueprint != blueprint) {
            view = new LayerView(blueprint, menu.turns(), menu.mirrored());
        }
        Placement placement = view.placement;
        int gw = placement.width();
        int gd = placement.depth();
        int layers = blueprint.sizeY;
        viewLayer = Mth.clamp(viewLayer, -1, layers - 1);
        boolean building = menu.state() == BuilderPayloads.Menu.BUILDING;
        String title = viewLayer < 0 ? "Seen from above" : "Layer " + (viewLayer + 1) + " of " + layers;
        Draw.fit(g, font, Component.literal(title), x + 7, y + 6, width - 60, Draw.Align.LEFT, Theme.TEXT);
        Draw.fit(g, font, Component.literal("N ↑"), x + width - 8, y + 6, 40, Draw.Align.RIGHT, Theme.DIM);
        int areaY = y + 18;
        int areaH = height - 30;
        float cell = Math.min((width - 14f) / gw, (float) areaH / gd);
        cell = Math.min(cell, 14f);
        int ox = x + (int) ((width - gw * cell) / 2);
        int oy = areaY + (int) ((areaH - gd * cell) / 2);
        int[] colors = viewLayer < 0 ? view.topColor : view.layer(viewLayer);
        int[] below = viewLayer > 0 ? view.layer(viewLayer - 1) : null;
        boolean[] built = building && viewLayer == menu.layer() ? builtCells(blueprint, viewLayer) : null;
        int hovered = -1;
        for (int pz = 0; pz < gd; pz++) {
            int y0 = oy + (int) (pz * cell);
            int y1 = Math.max(y0 + 1, oy + (int) ((pz + 1) * cell));
            for (int px = 0; px < gw; px++) {
                int index = px + pz * gw;
                int x0 = ox + (int) (px * cell);
                int x1 = Math.max(x0 + 1, ox + (int) ((px + 1) * cell));
                int color = colors[index];
                if (color == 0) {
                    if (below != null && below[index] != 0) {
                        g.fill(x0, y0, x1, y1, Draw.withAlpha(below[index], 0.14f));
                    }
                } else {
                    if (viewLayer < 0) {
                        float height01 = layers <= 1 ? 1 : view.topY[index] / (float) (layers - 1);
                        color = shade(color, 0.55f + 0.45f * height01);
                        if (building && view.topY[index] >= menu.layer()) {
                            color = Draw.withAlpha(color, 0.35f);
                        }
                    } else if (built != null && !built[index]) {
                        color = Draw.withAlpha(color, 0.3f);
                    }
                    g.fill(x0, y0, x1, y1, color);
                    if (cell >= 6) {
                        g.fill(x0, y0, x1, y0 + 1, 0x22FFFFFF);
                    }
                }
                if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
                    hovered = index;
                    Draw.outline(g, x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFFFFFFFF);
                }
            }
        }
        if (hovered >= 0) {
            BlockState state = stateAt(blueprint, hovered, viewLayer < 0 ? view.topY[hovered] : viewLayer);
            if (state != null) {
                Draw.queueTooltip(List.of(state.getBlock().getName(), Component.literal("Layer " + ((viewLayer < 0 ? view.topY[hovered] : viewLayer) + 1))
                        .withColor(Theme.MUTED)), CYAN);
            }
        }
        String hint = building ? (followBuild ? "Following the build · scroll to look around" : "Scroll to change layer")
                : "Scroll for layers" + (viewLayer >= 0 ? " · right-click: from above" : "");
        Draw.fit(g, font, Component.literal(hint), x + width / 2, y + height - 11, width - 10, Draw.Align.CENTER, Theme.DIM);
    }

    private BlockState stateAt(Blueprint blueprint, int cell, int y) {
        if (y < 0) {
            return null;
        }
        for (int entry = blueprint.layerStart[y]; entry < blueprint.layerStart[y + 1]; entry++) {
            if (view.cell(blueprint.positions[entry]) == cell) {
                return view.placement.transform(blueprint.state(entry));
            }
        }
        return null;
    }

    /** Which cells of the layer being built are already in place, checked against the world twice a second. */
    private boolean[] builtCells(Blueprint blueprint, int y) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        long now = Util.getMillis();
        if (builtCells != null && builtLayer == y && now - builtCheckedAt < 500) {
            return builtCells;
        }
        Placement real = Placement.of(blueprint, menu.pos().offset(menu.offX(), menu.offY(), menu.offZ()), menu.turns(), menu.mirrored());
        boolean[] cells = new boolean[real.width() * real.depth()];
        for (int entry = blueprint.layerStart[y]; entry < blueprint.layerStart[y + 1]; entry++) {
            int local = blueprint.positions[entry];
            BlockState target = blueprint.state(entry);
            if (level.getBlockState(real.world(blueprint, local)).getBlock() == target.getBlock()) {
                cells[view.cell(local)] = true;
            }
        }
        builtCells = cells;
        builtLayer = y;
        builtCheckedAt = now;
        return cells;
    }

    private static int shade(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ materials

    private void drawMaterials(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        Draw.panel(g, x, y, width, height, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("MATERIALS"), x + 8, y + 7, width - 100, Draw.Align.LEFT, Theme.ACCENT);
        List<BuilderPayloads.Line> lines = menu.lines();
        long needed = 0;
        long have = 0;
        for (BuilderPayloads.Line line : lines) {
            needed += line.need();
            have += Math.min(line.have(), line.need());
        }
        boolean building = menu.state() == BuilderPayloads.Menu.BUILDING;
        boolean canDeliver = menu.state() == BuilderPayloads.Menu.PLANNING || building;
        if (canDeliver && needed > have) {
            int bw = font.width("Deliver all") + 14;
            buttons.add(new Btn(x + width - bw - 6, y + 4, bw, 14, "Deliver all", NORMAL, true, () -> send("deposit"),
                    List.of(Component.literal("Deliver everything the build needs"), Component.literal("from your inventory and backpacks")
                            .withColor(Theme.MUTED))));
        }
        int listTop = y + 22;
        if (needed > 0) {
            Draw.bar(g, x + 8, listTop, width - 16, 5, (float) have / needed, have >= needed ? Theme.GREEN : Theme.ACCENT);
            String summary = have >= needed ? "Everything delivered" : String.format("%,d", have) + " / " + String.format("%,d", needed) + " delivered";
            Draw.fit(g, font, Component.literal(summary), x + 8, listTop + 8, width - 16, Draw.Align.LEFT, have >= needed ? Theme.GREEN : Theme.MUTED);
            listTop += 21;
        } else if (menu.state() == BuilderPayloads.Menu.EMPTY) {
            Draw.fit(g, font, Component.literal("Pick a blueprint to see what it takes."), x + width / 2, listTop + 20, width - 16,
                    Draw.Align.CENTER, Theme.DIM);
        } else if (lines.isEmpty() && menu.state() == BuilderPayloads.Menu.DONE) {
            Draw.fit(g, font, Component.literal("Every material went into the build."), x + width / 2, listTop + 20, width - 16,
                    Draw.Align.CENTER, Theme.GREEN);
        } else if (lines.isEmpty() && menu.state() == BuilderPayloads.Menu.PLANNING) {
            Draw.fit(g, font, Component.literal("Nothing to deliver: it's all in place."), x + width / 2, listTop + 20, width - 16,
                    Draw.Align.CENTER, Theme.GREEN);
        }
        int footer = menu.leftovers() > 0 ? 20 : 0;
        int listH = y + height - 4 - footer - listTop;
        g.enableScissor(x + 2, listTop, x + width - 2, listTop + listH);
        for (int i = 0; i < lines.size(); i++) {
            int ry = listTop + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < listTop || ry > listTop + listH) {
                continue;
            }
            drawLine(g, lines.get(i), x + 4, ry, width - 12, mouseX, mouseY, listTop, listH);
        }
        g.disableScissor();
        int content = lines.size() * ROW;
        if (content > listH) {
            Draw.scrollbar(g, x + width - 5, listTop, listH, content, scroll, CYAN);
        }
        if (footer > 0) {
            String label = "Collect leftovers (" + String.format("%,d", menu.leftovers()) + ")";
            buttons.add(new Btn(x + 6, y + height - 20, width - 12, 15, label, NORMAL, true, () -> send("collect"),
                    List.of(Component.literal("What clearing the site gave back,"), Component.literal("and the empty buckets").withColor(Theme.MUTED))));
        }
    }

    private void drawLine(GuiGraphicsExtractor g, BuilderPayloads.Line line, int x, int y, int width, int mouseX, int mouseY, int clipTop, int clipH) {
        Item item = item(line.item());
        ItemStack stack = new ItemStack(item);
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + ROW - 2, clipTop + clipH);
        boolean done = line.have() >= line.need();
        if (hover) {
            Draw.rounded(g, x, y, width, ROW - 2, Theme.CARD_HOVER);
        }
        Draw.item(g, stack, x + 3, y + 3, 1f);
        String count = line.need() == 0 ? "+" + String.format("%,d", line.have()) : String.format("%,d", Math.min(line.have(), line.need()))
                + " / " + String.format("%,d", line.need());
        int countW = font.width(count);
        Draw.fit(g, font, stack.getHoverName(), x + 23, y + 3, width - 30 - countW, Draw.Align.LEFT, Theme.TEXT);
        Draw.text(g, font, count, x + width - 4 - countW, y + 3, line.need() == 0 ? Theme.DIM : done ? Theme.GREEN : Theme.TEXT);
        if (line.need() > 0) {
            Draw.bar(g, x + 23, y + 14, width - 27, 4, (float) line.have() / line.need(), done ? Theme.GREEN : Theme.ACCENT);
        } else {
            Draw.fit(g, font, Component.literal("not needed"), x + 23, y + 13, width - 27, Draw.Align.LEFT, Theme.DIM);
        }
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(stack.getHoverName());
            if (line.need() > 0) {
                tip.add(Component.literal("Delivered " + String.format("%,d", Math.min(line.have(), line.need())) + " of "
                        + String.format("%,d", line.need())).withColor(Theme.MUTED));
            }
            if (!done && menu.state() != BuilderPayloads.Menu.DONE) {
                tip.add(Component.literal("Click: deliver from your inventory").withColor(CYAN));
            }
            if (line.have() > 0 && menu.state() != BuilderPayloads.Menu.BUILDING) {
                tip.add(Component.literal("Shift-click: take back").withColor(Theme.MUTED));
            }
            Draw.queueTooltip(tip, CYAN);
        }
    }

    static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.BARRIER : BuiltInRegistries.ITEM.getValue(key);
    }

    private boolean ready() {
        if (menu.state() != BuilderPayloads.Menu.PLANNING || menu.blueprintId().isEmpty()) {
            return false;
        }
        for (BuilderPayloads.Line line : menu.lines()) {
            if (line.have() < line.need()) {
                return false;
            }
        }
        return true;
    }

    private static int percent(int done, int total) {
        return total <= 0 ? 0 : Math.min(100, (int) (done * 100L / total));
    }

    // ------------------------------------------------------------------ footer

    private void drawFooter(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
        g.fill(left + 1, y - 2, left + w - 1, top + h - 1, Theme.FOOTER);
        int x = left + 10;
        int right = left + w - 10;
        List<Btn> row = new ArrayList<>();
        switch (menu.state()) {
            case BuilderPayloads.Menu.PLANNING -> {
                row.add(new Btn(0, 0, 82, 20, "Blueprint...", NORMAL, true, this::openPicker, List.of(Component.literal("Pick another blueprint"))));
                boolean anyStored = menu.lines().stream().anyMatch(l -> l.have() > 0);
                row.add(new Btn(0, 0, 76, 20, "Take back", NORMAL, anyStored, () -> send("withdraw"),
                        List.of(Component.literal("Take every delivered material back"))));
                row.add(new Btn(0, 0, 90, 20, "Build", PRIMARY, ready(), () -> send("build"),
                        ready() ? List.of(Component.literal("Start building"), Component.literal("Enter").withColor(Theme.MUTED))
                                : List.of(Component.literal("Deliver every material first").withColor(Theme.RED))));
            }
            case BuilderPayloads.Menu.BUILDING -> row.add(new Btn(0, 0, 76, 20, "Stop", DANGER, true, () -> send("cancel"),
                    List.of(Component.literal("Stop building"), Component.literal("Press Build later to carry on").withColor(Theme.MUTED))));
            case BuilderPayloads.Menu.DONE -> {
                row.add(new Btn(0, 0, 96, 20, "New blueprint", NORMAL, true, this::openPicker, null));
                row.add(new Btn(0, 0, 90, 20, "Build again", PRIMARY, true, () -> send("again"),
                        List.of(Component.literal("Plan the same blueprint again"))));
            }
            default -> {
            }
        }
        int bx = right;
        for (int i = row.size() - 1; i >= 0; i--) {
            Btn b = row.get(i);
            bx -= b.w();
            buttons.add(new Btn(bx, y + 8, b.w(), b.h(), b.label(), b.style(), b.enabled(), b.action(), b.tooltip()));
            bx -= 5;
        }
        int textW = bx - x - 6;
        if (menu.state() == BuilderPayloads.Menu.BUILDING) {
            float progress = menu.total() <= 0 ? 0 : menu.done() / (float) menu.total();
            Draw.bar(g, x, y + 9, textW, 8, progress, CYAN);
            String status = "Layer " + (menu.layer() + 1) + " of " + Math.max(1, menu.sizeY()) + "  ·  " + percent(menu.done(), menu.total()) + "%";
            Draw.fit(g, font, Component.literal(status), x, y + 21, textW, Draw.Align.LEFT, Theme.TEXT);
            if (!menu.note().startsWith("Building")) {
                Draw.fit(g, font, Component.literal(menu.note()), x + textW, y + 21, textW / 2, Draw.Align.RIGHT, Theme.ACCENT);
            }
        } else {
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(menu.note()), textW);
            int ty = y + (lines.size() > 1 ? 8 : 13);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.text(font, lines.get(i), x, ty + i * 10, Theme.MUTED, false);
            }
        }
    }

    private void drawButton(GuiGraphicsExtractor g, Btn b, int mouseX, int mouseY) {
        boolean over = b.enabled() && b.over(mouseX, mouseY);
        int fill;
        int border;
        int text;
        switch (b.style()) {
            case PRIMARY -> {
                fill = b.enabled() ? (over ? Draw.lighten(Theme.ACCENT, 0.15f) : Theme.ACCENT) : Theme.CARD;
                border = b.enabled() ? Draw.lighten(Theme.ACCENT, 0.3f) : Theme.CARD_BORDER;
                text = b.enabled() ? 0xFF1A1206 : Theme.DIM;
            }
            case DANGER -> {
                fill = over ? 0xFF2A1518 : Theme.CARD;
                border = over ? Theme.RED : 0xFF5A2A30;
                text = Theme.RED;
            }
            case TOGGLE_ON -> {
                fill = over ? Theme.CARD_HOVER : 0xFF132A30;
                border = CYAN;
                text = CYAN;
            }
            default -> {
                fill = over ? Theme.CARD_HOVER : Theme.CARD;
                border = over ? CYAN : Theme.CARD_BORDER;
                text = b.enabled() ? Theme.TEXT : Theme.DIM;
            }
        }
        Draw.panel(g, b.x(), b.y(), b.w(), b.h(), fill, border);
        Draw.fit(g, font, Component.literal(b.label()), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, b.w() - 6, Draw.Align.CENTER, text);
    }

    // ------------------------------------------------------------------ input

    private void openPicker() {
        minecraft.gui.setScreen(new BlueprintScreen(this));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && viewLayer >= 0 && menu.state() != BuilderPayloads.Menu.EMPTY) {
            viewLayer = -1;
            followBuild = false;
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        for (Btn button : buttons) {
            if (button.enabled() && button.over(mx, my)) {
                click();
                button.action().run();
                return true;
            }
        }
        // A material row: deliver it (shift: take it back).
        int leftW = Math.min(250, (w - 30) / 2 + 20);
        int rx = left + 10 + leftW + 10;
        int rw = left + w - 10 - rx;
        int listTop = top + 38 + 22 + (menu.lines().stream().anyMatch(l -> l.need() > 0) ? 21 : 0);
        int listBottom = top + h - 44 - 4 - (menu.leftovers() > 0 ? 20 : 0);
        if (mx >= rx + 4 && mx < rx + rw - 8 && my >= listTop && my < listBottom) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < menu.lines().size()) {
                BuilderPayloads.Line line = menu.lines().get(index);
                click();
                if (event.hasShiftDown()) {
                    send("withdraw", 0, 0, 0, line.item());
                } else {
                    send("deposit", 0, 0, 0, line.item());
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int leftW = Math.min(250, (w - 30) / 2 + 20);
        if (mouseX < left + 10 + leftW && menu.state() != BuilderPayloads.Menu.EMPTY) {
            Blueprint blueprint = Ghosts.BLUEPRINTS.get(menu.blueprintId());
            if (blueprint != null) {
                followBuild = false;
                viewLayer = Mth.clamp(viewLayer + (scrollY > 0 ? 1 : -1), -1, blueprint.sizeY - 1);
            }
            return true;
        }
        int content = menu.lines().size() * ROW;
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, content - (h - 120)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menu.state() == BuilderPayloads.Menu.PLANNING) {
            int step = event.hasShiftDown() ? 5 : 1;
            switch (event.key()) {
                case InputConstants.KEY_LEFT -> send("move", -step, 0, 0, "");
                case InputConstants.KEY_RIGHT -> send("move", step, 0, 0, "");
                case InputConstants.KEY_UP -> send("move", 0, 0, -step, "");
                case InputConstants.KEY_DOWN -> send("move", 0, 0, step, "");
                case InputConstants.KEY_PAGEUP -> send("move", 0, step, 0, "");
                case InputConstants.KEY_PAGEDOWN -> send("move", 0, -step, 0, "");
                case InputConstants.KEY_R -> send("turn", 1, 0, 0, "");
                case InputConstants.KEY_M -> send("mirror");
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    if (ready()) {
                        send("build");
                    }
                }
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
