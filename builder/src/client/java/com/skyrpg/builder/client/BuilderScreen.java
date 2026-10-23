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
 * The Builder Station menu. Left: the blueprint, as a 3D model you can turn by dragging, from the top, from the side,
 * or layer by layer; under it "Move in world" (placing happens in the world, see {@link PlacementMode}) and the
 * hologram and site options. Right: the materials, delivered and still needed (click one to deliver it), and who
 * delivered them. Bottom: what's going on and the big buttons. Pressing Build closes the menu so you can watch.
 */
public final class BuilderScreen extends Screen {
    static final int CYAN = 0xFF5CE1E6;
    private static final int ROW = 24;

    private enum View {
        MODEL("3D"), TOP("Top"), SIDE("Side"), LAYERS("Layers");

        final String label;

        View(String label) {
            this.label = label;
        }
    }

    /** Remembered while the game runs. */
    private static View lastView = View.MODEL;

    private BuilderPayloads.Menu menu;
    private final long openedAt = Util.getMillis();
    private int left;
    private int top;
    private int w;
    private int h;
    private double scroll;
    private View viewMode = lastView;
    /** The layer shown in the Layers view. */
    private int viewLayer;
    private boolean followBuild = true;
    private long lastRefresh = Util.getMillis();
    private final List<Btn> buttons = new ArrayList<>();
    private LayerView view;
    private boolean[] builtCells;
    private long builtCheckedAt;
    private int builtLayer = -1;
    /** The 3D model's turn, in frames, and whether the player is turning it by hand. */
    private double modelFrame;
    private long lastFrameAt = Util.getMillis();
    private long lastDragAt;
    private Previews.Preview lastModel;
    /** Where the canvas and the materials list were drawn, for clicks and scrolling. */
    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private int listX;
    private int listW;
    private int listTop;
    private int listBottom;

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
    private static final int ACTION = 4;

    BuilderScreen(BuilderPayloads.Menu menu) {
        super(Component.literal("Builder Station"));
        this.menu = menu;
        viewLayer = menu.layer();
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
        }
        this.menu = updated;
    }

    @Override
    protected void init() {
        w = Math.min(620, width - 12);
        h = Math.min(350, height - 12);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        lastView = viewMode;
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
        int leftW = Math.min(280, (w - 30) / 2 + 30);
        int rightX = left + 10 + leftW + 10;
        int rightW = left + w - 10 - rightX;
        if (menu.state() == BuilderPayloads.Menu.EMPTY) {
            drawWelcome(g, left + 10, bodyTop, leftW, bodyBottom - bodyTop);
        } else {
            drawBlueprint(g, left + 10, bodyTop, leftW, bodyBottom - bodyTop, mouseX, mouseY);
        }
        drawMaterials(g, rightX, bodyTop, rightW, bodyBottom - bodyTop, mouseX, mouseY);
        drawFooter(g, bodyBottom + 6);

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
                "Place its hologram in the world: it follows where you aim.",
                "Deliver the materials it lists. Friends can help.",
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
        int controlsH = planning ? 40 : 14;
        int canvasHeight = height - controlsH - 4;
        drawCanvas(g, x, y, width, canvasHeight, mouseX, mouseY);
        int cy = y + canvasHeight + 4;
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
        int by = cy + 15;
        int moveW = 104;
        buttons.add(new Btn(x, by, moveW, 20, "Move in world", ACTION, true, this::moveInWorld,
                List.of(Component.literal("Place the hologram in the world"), Component.literal("It follows where you aim; the keys are shown").withColor(Theme.MUTED),
                        Component.literal("at the top of the screen.").withColor(Theme.MUTED))));
        int half = (width - moveW - 8) / 2;
        buttons.add(new Btn(x + moveW + 4, by, half, 20, "Hologram " + (menu.preview() ? "on" : "off"), menu.preview() ? TOGGLE_ON : NORMAL, true,
                () -> send("preview"), List.of(Component.literal("Show the build in the world"), Component.literal("Everyone nearby sees it").withColor(Theme.MUTED))));
        buttons.add(new Btn(x + moveW + 8 + half, by, half, 20, "Clear site " + (menu.clear() ? "on" : "off"), menu.clear() ? TOGGLE_ON : NORMAL, true,
                () -> send("clear"), List.of(Component.literal("Clear the site while building"),
                Component.literal("Blocks in the way are removed (dirt, plants and").withColor(Theme.MUTED),
                Component.literal("logs go to the leftovers; stone and ores don't).").withColor(Theme.MUTED),
                Component.literal("Off: blocks in the way are left, the build skips them.").withColor(Theme.MUTED))));
    }

    private void moveInWorld() {
        Blueprint blueprint = Ghosts.BLUEPRINTS.get(menu.blueprintId());
        BlockPos origin = menu.pos().offset(menu.offX(), menu.offY(), menu.offZ());
        lastView = viewMode;
        ClientPlayNetworking.send(BuilderPayloads.Action.of(menu.pos(), "close"));
        PlacementMode.start(menu.pos(), menu.blueprintId(), blueprint == null ? menu.blueprintName() : blueprint.name, menu.turns(),
                menu.mirrored(), origin);
    }

    // ------------------------------------------------------------------ the blueprint canvas

    /** The blueprint as placed (turned/mirrored): colours from above, from the side, and per layer. */
    private static final class LayerView {
        final Blueprint blueprint;
        final Placement placement;
        final int[] topColor;
        final int[] topY;
        final int[] sideColor;
        final int[] sideDepth;
        final Map<Integer, int[]> layers = new HashMap<>();

        LayerView(Blueprint blueprint, int turns, boolean mirrored) {
            this.blueprint = blueprint;
            this.placement = Placement.of(blueprint, BlockPos.ZERO, turns, mirrored);
            int width = placement.width();
            int cells = width * placement.depth();
            topColor = new int[cells];
            topY = new int[cells];
            sideColor = new int[width * blueprint.sizeY];
            sideDepth = new int[width * blueprint.sizeY];
            java.util.Arrays.fill(topY, -1);
            java.util.Arrays.fill(sideDepth, -1);
            for (int entry = 0; entry < blueprint.count(); entry++) {
                int local = blueprint.positions[entry];
                int lx = blueprint.localX(local);
                int lz = blueprint.localZ(local);
                int px = placement.placedX(lx, lz);
                int pz = placement.placedZ(lx, lz);
                int y = blueprint.localY(local);
                int cell = px + pz * width;
                int color = color(blueprint.state(entry));
                if (y >= topY[cell]) {
                    topY[cell] = y;
                    topColor[cell] = color;
                }
                // From the south: the block nearest the viewer (largest z) in each column.
                int side = px + y * width;
                if (pz > sideDepth[side]) {
                    sideDepth[side] = pz;
                    sideColor[side] = color;
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
        canvasX = x;
        canvasY = y;
        canvasW = width;
        canvasH = height;
        Draw.panel(g, x, y, width, height, 0xFF0B1220, Theme.CARD_BORDER);
        // Blueprint paper: a faint grid behind everything.
        for (int gx = x + 8; gx < x + width - 1; gx += 8) {
            g.fill(gx, y + 1, gx + 1, y + height - 1, 0x0D5CA8FF);
        }
        for (int gy = y + 8; gy < y + height - 1; gy += 8) {
            g.fill(x + 1, gy, x + width - 1, gy + 1, 0x0D5CA8FF);
        }
        // View tabs.
        int tx = x + 5;
        for (View mode : View.values()) {
            int tw = font.width(mode.label) + 12;
            boolean active = mode == viewMode;
            View target = mode;
            buttons.add(new Btn(tx, y + 4, tw, 14, mode.label, active ? TOGGLE_ON : NORMAL, true, () -> {
                viewMode = target;
                if (target == View.LAYERS && menu.state() != BuilderPayloads.Menu.BUILDING) {
                    viewLayer = 0;
                }
            }, null));
            tx += tw + 3;
        }
        if (viewMode == View.MODEL) {
            // Real block models and textures, or simple coloured cubes (for every preview, the build card too).
            String style = Previews.realStyle ? "Real" : "Simple";
            int sw = font.width("Simple") + 12;
            buttons.add(new Btn(x + width - sw - 5, y + 4, sw, 14, style, Previews.realStyle ? TOGGLE_ON : NORMAL, true,
                    BuilderSettings::toggleStyle,
                    List.of(Component.literal("Real: every block as it looks"), Component.literal("Simple: plain coloured cubes").withColor(Theme.MUTED))));
        }
        Blueprint blueprint = Ghosts.BLUEPRINTS.get(menu.blueprintId());
        if (blueprint == null) {
            Draw.fit(g, font, Component.literal("Loading blueprint..."), x + width / 2, y + height / 2 - 4, width - 10, Draw.Align.CENTER, Theme.MUTED);
            return;
        }
        if (view == null || view.blueprint != blueprint) {
            view = new LayerView(blueprint, menu.turns(), menu.mirrored());
        }
        int areaX = x + 6;
        int areaY = y + 22;
        int areaW = width - 12;
        int areaH = height - 36;
        boolean building = menu.state() == BuilderPayloads.Menu.BUILDING;
        String hint;
        switch (viewMode) {
            case MODEL -> {
                drawModel(g, blueprint, areaX, areaY, areaW, areaH, building);
                hint = "Drag to turn it";
            }
            case SIDE -> {
                drawSide(g, areaX, areaY, areaW, areaH, mouseX, mouseY);
                hint = "Seen from the south";
            }
            case LAYERS -> {
                viewLayer = Mth.clamp(viewLayer, 0, blueprint.sizeY - 1);
                drawGrid(g, blueprint, areaX, areaY, areaW, areaH, mouseX, mouseY, viewLayer);
                hint = "Layer " + (viewLayer + 1) + " of " + blueprint.sizeY + (building && followBuild ? " · following the build" : " · scroll for others");
            }
            default -> {
                drawGrid(g, blueprint, areaX, areaY, areaW, areaH, mouseX, mouseY, -1);
                hint = "Seen from above · north is up";
            }
        }
        Draw.fit(g, font, Component.literal(hint), x + width / 2, y + height - 11, width - 10, Draw.Align.CENTER, Theme.DIM);
    }

    private void drawModel(GuiGraphicsExtractor g, Blueprint blueprint, int x, int y, int w, int h, boolean building) {
        String id = blueprint.id;
        Previews.Preview wanted = Previews.get("menu:" + id, () -> Ghosts.BLUEPRINTS.get(id), false,
                Previews.Spec.menu(building ? menu.layer() : -1));
        Previews.Preview model = Previews.ready(wanted, lastModel != null && lastModel.blueprint == blueprint ? lastModel : null);
        long now = Util.getMillis();
        if (now - lastDragAt > 2500) {
            modelFrame += (now - lastFrameAt) / 140.0;
        }
        lastFrameAt = now;
        if (model != null && model.state == Previews.State.READY) {
            lastModel = model;
            Previews.drawFrame(g, model, (int) Math.floor(modelFrame), x, y, w, h);
        } else {
            String dots = ".".repeat(1 + (int) ((now / 300) % 3));
            Draw.fit(g, font, Component.literal(dots), x + w / 2, y + h / 2 - 4, w, Draw.Align.CENTER, Theme.DIM);
        }
    }

    private void drawSide(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int gw = view.placement.width();
        int gh = view.blueprint.sizeY;
        int depth = view.placement.depth();
        float cell = Math.min(Math.min((float) w / gw, (float) h / gh), 14f);
        int ox = x + (int) ((w - gw * cell) / 2);
        int oy = y + (int) ((h - gh * cell) / 2);
        for (int ly = 0; ly < gh; ly++) {
            int row = gh - 1 - ly;
            int y0 = oy + (int) (row * cell);
            int y1 = Math.max(y0 + 1, oy + (int) ((row + 1) * cell));
            for (int px = 0; px < gw; px++) {
                int index = px + ly * gw;
                if (view.sideDepth[index] < 0) {
                    continue;
                }
                int x0 = ox + (int) (px * cell);
                int x1 = Math.max(x0 + 1, ox + (int) ((px + 1) * cell));
                float near = depth <= 1 ? 1 : view.sideDepth[index] / (float) (depth - 1);
                int color = shade(view.sideColor[index], 0.55f + 0.45f * near);
                if (menu.state() == BuilderPayloads.Menu.BUILDING && ly >= menu.layer()) {
                    color = Draw.withAlpha(color, 0.35f);
                }
                g.fill(x0, y0, x1, y1, color);
                if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
                    Draw.outline(g, x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFFFFFFFF);
                    Draw.queueTooltip(List.of(Component.literal("Layer " + (ly + 1))), CYAN);
                }
            }
        }
    }

    /** Top view ({@code layer} -1: the highest block of each column) or one layer. */
    private void drawGrid(GuiGraphicsExtractor g, Blueprint blueprint, int x, int y, int w, int h, int mouseX, int mouseY, int layer) {
        Placement placement = view.placement;
        int gw = placement.width();
        int gd = placement.depth();
        int layers = blueprint.sizeY;
        boolean building = menu.state() == BuilderPayloads.Menu.BUILDING;
        float cell = Math.min(Math.min((float) w / gw, (float) h / gd), 14f);
        int ox = x + (int) ((w - gw * cell) / 2);
        int oy = y + (int) ((h - gd * cell) / 2);
        int[] colors = layer < 0 ? view.topColor : view.layer(layer);
        int[] below = layer > 0 ? view.layer(layer - 1) : null;
        boolean[] built = building && layer == menu.layer() ? builtCells(blueprint, layer) : null;
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
                    if (layer < 0) {
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
            int at = layer < 0 ? view.topY[hovered] : layer;
            BlockState state = stateAt(blueprint, hovered, at);
            if (state != null) {
                Draw.queueTooltip(List.of(state.getBlock().getName(), Component.literal("Layer " + (at + 1)).withColor(Theme.MUTED)), CYAN);
            }
        }
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
            if (level.getBlockState(real.world(blueprint, local)).getBlock() == blueprint.state(entry).getBlock()) {
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

    /** Remembered while the game runs: materials as a list (false) or a grid of bigger icons (true). */
    private static boolean materialGrid;
    private static final int TILE_W = 46;
    private static final int TILE_H = 50;

    /** Where a material was drawn this frame, for clicks. */
    private record MatHit(int x, int y, int w, int h, BuilderPayloads.Line line) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<MatHit> matHits = new ArrayList<>();
    private int materialsContent;

    /** How many of this item are in the player's inventory (backpacks aren't counted here, but Deliver uses them too). */
    private int carried(Item item) {
        return minecraft.player == null ? 0 : minecraft.player.getInventory().countItem(item);
    }

    private void drawMaterials(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        matHits.clear();
        Draw.panel(g, x, y, width, height, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("MATERIALS"), x + 8, y + 7, 70, Draw.Align.LEFT, Theme.ACCENT);
        List<BuilderPayloads.Line> lines = menu.lines();
        long needed = 0;
        long have = 0;
        long deliverable = 0;
        for (BuilderPayloads.Line line : lines) {
            needed += line.need();
            have += Math.min(line.have(), line.need());
            int missing = Math.max(0, line.need() - line.have());
            deliverable += Math.min(missing, carried(item(line.item())));
        }
        // List | Grid.
        int vx = x + 8 + Math.min(70, font.width("MATERIALS")) + 8;
        for (int i = 0; i < 2; i++) {
            String label = i == 0 ? "List" : "Grid";
            int vw = font.width(label) + 10;
            boolean on = (i == 1) == materialGrid;
            boolean grid = i == 1;
            buttons.add(new Btn(vx, y + 5, vw, 12, label, on ? TOGGLE_ON : NORMAL, true, () -> {
                materialGrid = grid;
                scroll = 0;
            }, null));
            vx += vw + 2;
        }
        boolean building = menu.state() == BuilderPayloads.Menu.BUILDING;
        boolean canDeliver = menu.state() == BuilderPayloads.Menu.PLANNING || building;
        if (canDeliver && needed > have) {
            int bw = font.width("Deliver all") + 14;
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Deliver everything the build needs"));
            tip.add(Component.literal("from your inventory and backpacks").withColor(Theme.MUTED));
            tip.add(menu.creative() ? Component.literal("Creative: it's free").withColor(CYAN)
                    : Component.literal("In your inventory now: " + String.format("%,d", deliverable) + " of the "
                    + String.format("%,d", needed - have) + " still needed").withColor(deliverable > 0 ? Theme.GREEN : Theme.RED));
            buttons.add(new Btn(x + width - bw - 6, y + 4, bw, 14, "Deliver all", NORMAL, true, () -> send("deposit"), tip));
        }
        int top = y + 22;
        if (needed > 0) {
            Draw.bar(g, x + 8, top, width - 16, 5, (float) have / needed, have >= needed ? Theme.GREEN : Theme.ACCENT);
            String summary = have >= needed ? "Everything delivered" : String.format("%,d", have) + " / " + String.format("%,d", needed) + " delivered";
            Draw.fit(g, font, Component.literal(summary), x + 8, top + 8, width / 2, Draw.Align.LEFT, have >= needed ? Theme.GREEN : Theme.MUTED);
            if (have < needed && canDeliver && !menu.creative()) {
                Draw.fitRight(g, font, "you carry " + String.format("%,d", deliverable), x + width - 8, top + 8, width / 2 - 12,
                        deliverable > 0 ? Theme.GREEN : Theme.DIM);
            }
            top += 20;
        } else if (menu.state() == BuilderPayloads.Menu.EMPTY) {
            Draw.fit(g, font, Component.literal("Pick a blueprint to see what it takes."), x + width / 2, top + 20, width - 16,
                    Draw.Align.CENTER, Theme.DIM);
        } else if (lines.isEmpty() && menu.state() == BuilderPayloads.Menu.DONE) {
            Draw.fit(g, font, Component.literal("Every material went into the build."), x + width / 2, top + 20, width - 16,
                    Draw.Align.CENTER, Theme.GREEN);
        } else if (lines.isEmpty() && menu.state() == BuilderPayloads.Menu.PLANNING) {
            Draw.fit(g, font, Component.literal("Nothing to deliver: it's all in place."), x + width / 2, top + 20, width - 16,
                    Draw.Align.CENTER, Theme.GREEN);
        }
        if (!menu.helpers().isEmpty()) {
            Draw.fit(g, font, Component.literal("Delivered by  ").withColor(Theme.DIM).append(Component.literal(menu.helpers()).withColor(Theme.MUTED)),
                    x + 8, top, width - 16, Draw.Align.LEFT, Theme.DIM);
            top += 12;
        }
        int footer = menu.leftovers() > 0 ? 20 : 0;
        int listH = y + height - 4 - footer - top;
        listX = x + 4;
        listW = width - 12;
        listTop = top;
        listBottom = top + listH;
        g.enableScissor(x + 2, top, x + width - 2, top + listH);
        if (materialGrid) {
            int columns = Math.max(1, (width - 12) / TILE_W);
            int gap = ((width - 12) - columns * TILE_W) / Math.max(1, columns);
            for (int i = 0; i < lines.size(); i++) {
                int tx = x + 5 + (i % columns) * (TILE_W + gap);
                int ty = top + (i / columns) * TILE_H - (int) Math.round(scroll);
                if (ty + TILE_H < top || ty > top + listH) {
                    continue;
                }
                drawTile(g, lines.get(i), tx, ty, mouseX, mouseY, top, listH);
            }
            materialsContent = (lines.size() + columns - 1) / columns * TILE_H;
        } else {
            for (int i = 0; i < lines.size(); i++) {
                int ry = top + i * ROW - (int) Math.round(scroll);
                if (ry + ROW < top || ry > top + listH) {
                    continue;
                }
                drawLine(g, lines.get(i), x + 4, ry, width - 12, mouseX, mouseY, top, listH);
            }
            materialsContent = lines.size() * ROW;
        }
        g.disableScissor();
        if (materialsContent > listH) {
            Draw.scrollbar(g, x + width - 5, top, listH, materialsContent, scroll, CYAN);
        }
        if (footer > 0) {
            String label = "Collect leftovers (" + String.format("%,d", menu.leftovers()) + ")";
            buttons.add(new Btn(x + 6, y + height - 20, width - 12, 15, label, NORMAL, true, () -> send("collect"),
                    List.of(Component.literal("What clearing the site gave back,"), Component.literal("and the empty buckets").withColor(Theme.MUTED))));
        }
    }

    /** Colour for "you carry N": enough for what's missing, some, or none. */
    private static int carriedColor(int carried, int missing) {
        return missing <= 0 ? Theme.DIM : carried >= missing ? Theme.GREEN : carried > 0 ? Theme.ACCENT : Theme.DIM;
    }

    private void drawLine(GuiGraphicsExtractor g, BuilderPayloads.Line line, int x, int y, int width, int mouseX, int mouseY, int clipTop, int clipH) {
        Item item = item(line.item());
        ItemStack stack = new ItemStack(item);
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + ROW - 2, clipTop + clipH);
        boolean done = line.have() >= line.need();
        int missing = Math.max(0, line.need() - line.have());
        int carried = carried(item);
        if (hover) {
            Draw.rounded(g, x, y, width, ROW - 2, Theme.CARD_HOVER);
        }
        Draw.item(g, stack, x + 3, y + 3, 1f);
        String count = line.need() == 0 ? "+" + String.format("%,d", line.have()) : String.format("%,d", Math.min(line.have(), line.need()))
                + " / " + String.format("%,d", line.need());
        int countW = font.width(count);
        Draw.fit(g, font, stack.getHoverName(), x + 23, y + 3, width - 30 - countW, Draw.Align.LEFT, Theme.TEXT);
        Draw.text(g, font, count, x + width - 4 - countW, y + 3, line.need() == 0 ? Theme.DIM : done ? Theme.GREEN : Theme.TEXT);
        String you = "you " + String.format("%,d", carried);
        int youW = font.width(you);
        if (line.need() > 0) {
            Draw.bar(g, x + 23, y + 14, width - 31 - youW, 4, (float) line.have() / line.need(), done ? Theme.GREEN : Theme.ACCENT);
        } else {
            Draw.fit(g, font, Component.literal("not needed"), x + 23, y + 13, width - 31 - youW, Draw.Align.LEFT, Theme.DIM);
        }
        Draw.text(g, font, you, x + width - 4 - youW, y + 12, carriedColor(carried, missing));
        matHits.add(new MatHit(x, Math.max(y, clipTop), width, Math.min(y + ROW - 2, clipTop + clipH) - Math.max(y, clipTop), line));
        if (hover) {
            materialTooltip(stack, line, carried);
        }
    }

    /** A material as a tile: a big icon, delivered / needed under it, a thin bar, and how many you carry in the corner. */
    private void drawTile(GuiGraphicsExtractor g, BuilderPayloads.Line line, int x, int y, int mouseX, int mouseY, int clipTop, int clipH) {
        Item item = item(line.item());
        ItemStack stack = new ItemStack(item);
        int w = TILE_W - 3;
        int h = TILE_H - 3;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + h, clipTop + clipH);
        boolean done = line.have() >= line.need();
        int missing = Math.max(0, line.need() - line.have());
        int carried = carried(item);
        Draw.panel(g, x, y, w, h, hover ? Theme.CARD_HOVER : 0xFF141924, done && line.need() > 0 ? 0xFF2E6B48 : hover ? CYAN : Theme.CARD_BORDER);
        Draw.item(g, stack, x + (w - 24) / 2, y + 4, 1.5f);
        if (carried > 0 && missing > 0) {
            String you = compact(carried);
            Draw.rightText(g, font, you, x + w - 3, y + 2, carriedColor(carried, missing));
        }
        String count = line.need() == 0 ? "+" + compact(line.have()) : compact(Math.min(line.have(), line.need())) + "/" + compact(line.need());
        Draw.fit(g, font, Component.literal(count), x + w / 2, y + 31, w - 4, Draw.Align.CENTER,
                line.need() == 0 ? Theme.DIM : done ? Theme.GREEN : Theme.TEXT);
        if (line.need() > 0) {
            Draw.bar(g, x + 3, y + h - 6, w - 6, 3, (float) line.have() / line.need(), done ? Theme.GREEN : Theme.ACCENT);
        }
        matHits.add(new MatHit(x, Math.max(y, clipTop), w, Math.min(y + h, clipTop + clipH) - Math.max(y, clipTop), line));
        if (hover) {
            materialTooltip(stack, line, carried);
        }
    }

    private static String compact(int n) {
        return n >= 10_000 ? (n / 1000) + "k" : String.format("%,d", n);
    }

    private void materialTooltip(ItemStack stack, BuilderPayloads.Line line, int carried) {
        boolean done = line.have() >= line.need();
        int missing = Math.max(0, line.need() - line.have());
        List<Component> tip = new ArrayList<>();
        tip.add(stack.getHoverName());
        if (line.need() > 0) {
            tip.add(Component.literal("Delivered " + String.format("%,d", Math.min(line.have(), line.need())) + " of "
                    + String.format("%,d", line.need())).withColor(Theme.MUTED));
        } else {
            tip.add(Component.literal(String.format("%,d", line.have()) + " delivered, not needed any more").withColor(Theme.MUTED));
        }
        tip.add(Component.literal("You carry " + String.format("%,d", carried)
                + (missing > 0 ? (carried >= missing ? " (enough)" : carried > 0 ? " (" + String.format("%,d", missing - carried) + " short)" : "") : ""))
                .withColor(carriedColor(carried, missing) == Theme.DIM ? Theme.MUTED : carriedColor(carried, missing)));
        if (!done && menu.state() != BuilderPayloads.Menu.DONE) {
            tip.add(Component.literal("Click: deliver it").withColor(CYAN));
        }
        if (line.have() > 0 && menu.state() != BuilderPayloads.Menu.BUILDING) {
            tip.add(Component.literal("Shift-click: take it back").withColor(Theme.MUTED));
        }
        String standIn = standInFor(line);
        if (standIn != null) {
            tip.add(Component.literal("Stand-in for " + standIn).withColor(Theme.ACCENT));
        }
        if (replaceable(line) != null && menu.state() != BuilderPayloads.Menu.DONE) {
            tip.add(Component.literal("Right-click: build something else instead").withColor(Theme.MUTED));
        }
        Draw.queueTooltip(tip, CYAN);
    }

    /**
     * The blueprint block a material line stands for, to replace: the original when the line is already a stand-in,
     * else the block of the line's item; null for items that aren't blocks (buckets, dirt for paths...).
     */
    private net.minecraft.world.level.block.Block replaceable(BuilderPayloads.Line line) {
        for (java.util.Map.Entry<String, String> entry : menu.replacements().entrySet()) {
            net.minecraft.world.level.block.Block to = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.getValue()));
            if (to.asItem() == item(line.item())) {
                return BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.getKey()));
            }
        }
        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item(line.item()));
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    /** "Stand-in for Chiseled Tuff Bricks" when this line's item is a replacement. */
    private String standInFor(BuilderPayloads.Line line) {
        for (java.util.Map.Entry<String, String> entry : menu.replacements().entrySet()) {
            net.minecraft.world.level.block.Block to = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.getValue()));
            if (to.asItem() == item(line.item())) {
                return BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.getKey())).getName().getString();
            }
        }
        return null;
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

    private void drawFooter(GuiGraphicsExtractor g, int y) {
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
                row.add(new Btn(0, 0, 90, 20, "Build", PRIMARY, ready(), this::build,
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

    /** Starts the build and closes the menu, so you can watch it go up (the overlay shows how far it is). */
    private void build() {
        send("build");
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.3f, 0.6f));
        onClose();
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
            case ACTION -> {
                fill = over ? Draw.lighten(0xFF1E6F78, 0.15f) : 0xFF1E6F78;
                border = CYAN;
                text = 0xFFF0FFFF;
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

    private boolean overCanvas(double mx, double my) {
        return mx >= canvasX && mx < canvasX + canvasW && my >= canvasY + 20 && my < canvasY + canvasH;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        // Right-click a material: build something else in its place.
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && my >= listTop && my < listBottom && !menu.blueprintId().isEmpty()) {
            for (MatHit hit : matHits) {
                if (hit.over(mx, my)) {
                    net.minecraft.world.level.block.Block block = replaceable(hit.line());
                    if (block != null) {
                        click();
                        minecraft.gui.setScreen(new ReplaceScreen(this, block));
                        return true;
                    }
                }
            }
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
        // A material: deliver it (shift: take it back).
        if (my >= listTop && my < listBottom) {
            for (MatHit hit : matHits) {
                if (hit.over(mx, my)) {
                    click();
                    send(event.hasShiftDown() ? "withdraw" : "deposit", 0, 0, 0, hit.line().item());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (viewMode == View.MODEL && event.button() == InputConstants.MOUSE_BUTTON_LEFT && overCanvas(event.x(), event.y())) {
            modelFrame -= dragX / 4.0;
            lastDragAt = Util.getMillis();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overCanvas(mouseX, mouseY) && menu.state() != BuilderPayloads.Menu.EMPTY) {
            Blueprint blueprint = Ghosts.BLUEPRINTS.get(menu.blueprintId());
            if (blueprint != null) {
                if (viewMode == View.MODEL) {
                    modelFrame -= scrollY * 2;
                    lastDragAt = Util.getMillis();
                } else {
                    if (viewMode != View.LAYERS) {
                        viewMode = View.LAYERS;
                    }
                    followBuild = false;
                    viewLayer = Mth.clamp(viewLayer + (scrollY > 0 ? 1 : -1), 0, blueprint.sizeY - 1);
                }
            }
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, materialsContent - (listBottom - listTop)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menu.state() == BuilderPayloads.Menu.PLANNING && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            if (ready()) {
                build();
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
