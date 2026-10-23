package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.builder.Materials;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Picks a stand-in for a blueprint block that's hard to get: any buildable block (search by name), the block in your hand,
 * or "skip it" (leave those spots empty); "Undo" goes back to the blueprint's own block. Sends the station's "replace"
 * action ("from|to") and returns to the station menu.
 */
final class ReplaceScreen extends Screen {
    private static final int CELL = 20;
    private static List<Block> all;

    private final BuilderScreen parent;
    private final Block from;
    private final String current;
    private EditBox search;
    private List<Block> shown = List.of();
    private double scroll;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int columns;

    ReplaceScreen(BuilderScreen parent, Block from) {
        super(Component.literal("Replace a block"));
        this.parent = parent;
        this.from = from;
        this.current = parent.menu().replacements().get(id(from));
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** Every block the station can build that has an item, by name. */
    private static List<Block> blocks() {
        if (all == null) {
            List<Block> list = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.asItem() != Items.AIR && Materials.buildable(block.defaultBlockState())) {
                    list.add(block);
                }
            }
            list.sort((a, b) -> a.getName().getString().compareToIgnoreCase(b.getName().getString()));
            all = List.copyOf(list);
        }
        return all;
    }

    BlockPos station() {
        return parent.station();
    }

    void parentUpdate(com.skyrpg.builder.BuilderPayloads.Menu menu) {
        parent.update(menu);
    }

    private int panelW() {
        return Math.min(420, width - 20);
    }

    private int panelH() {
        return Math.min(330, height - 20);
    }

    @Override
    protected void init() {
        int left = (width - panelW()) / 2;
        int top = (height - panelH()) / 2;
        int x = left + 10;
        int y = top + 40;
        List<Button> row = new ArrayList<>();
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        if (held.getItem() instanceof BlockItem item && item.getBlock() != from && Materials.buildable(item.getBlock().defaultBlockState())) {
            row.add(Button.builder(Component.literal("Use " + held.getHoverName().getString()), b -> pick(item.getBlock())).width(150).build());
        }
        row.add(Button.builder(Component.literal("Skip it"), b -> pick(Blocks.AIR)).width(70).build());
        if (current != null) {
            row.add(Button.builder(Component.literal("Undo"), b -> undo()).width(60).build());
        }
        int bx = x;
        for (Button button : row) {
            button.setPosition(bx, y);
            button.setHeight(18);
            addRenderableWidget(button);
            bx += button.getWidth() + 4;
        }
        y += 24;
        search = new EditBox(font, x, y, panelW() - 20, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search blocks (e.g. stone, oak, glass)"));
        search.setResponder(text -> filter());
        addRenderableWidget(search);
        setInitialFocus(search);
        gridX = x;
        gridY = y + 22;
        gridW = panelW() - 20;
        gridH = top + panelH() - 30 - gridY;
        columns = Math.max(1, gridW / CELL);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(left + panelW() - 70, top + panelH() - 24, 60, 18).build());
        filter();
    }

    private void filter() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Block> out = new ArrayList<>();
        for (Block block : blocks()) {
            if (block == from) {
                continue;
            }
            if (query.isEmpty() || block.getName().getString().toLowerCase(Locale.ROOT).contains(query) || id(block).contains(query)) {
                out.add(block);
            }
        }
        shown = out;
        scroll = 0;
    }

    private void pick(Block block) {
        parent.send("replace", 0, 0, 0, id(from) + "|" + id(block));
        onClose();
    }

    private void undo() {
        parent.send("replace", 0, 0, 0, id(from) + "|");
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = (width - panelW()) / 2;
        int top = (height - panelH()) / 2;
        Draw.shadow(g, left, top, panelW(), panelH(), 10, 0.6f);
        Draw.panel(g, left, top, panelW(), panelH(), Theme.WINDOW, Theme.BORDER);
        Draw.item(g, new ItemStack(from.asItem()), left + 10, top + 8, 1f);
        Draw.fit(g, font, Component.literal("Replace " + from.getName().getString()), left + 32, top + 8, panelW() - 44, Draw.Align.LEFT, Theme.TEXT);
        String now = current == null ? "Pick a block to build in its place. It keeps the same facing and shape where it can."
                : current.equals("minecraft:air") ? "Now: left out. Pick a block, or Undo."
                : "Now built as " + BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(current)).getName().getString() + ".";
        Draw.fit(g, font, Component.literal(now), left + 32, top + 20, panelW() - 44, 0.85f, 0.6f, Draw.Align.LEFT, Theme.MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int rows = (shown.size() + columns - 1) / columns;
        Block hovered = null;
        for (int i = 0; i < shown.size(); i++) {
            int cx = gridX + (i % columns) * CELL;
            int cy = gridY + (i / columns) * CELL - (int) scroll;
            if (cy + CELL < gridY || cy > gridY + gridH) {
                continue;
            }
            Block block = shown.get(i);
            boolean over = mouseX >= cx && mouseX < cx + CELL && mouseY >= Math.max(cy, gridY) && mouseY < Math.min(cy + CELL, gridY + gridH);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, over ? Theme.CARD_HOVER : 0xFF141924);
            Draw.item(g, new ItemStack(block.asItem()), cx + 2, cy + 2, 1f);
            if (over) {
                hovered = block;
            }
        }
        g.disableScissor();
        if (rows * CELL > gridH) {
            Draw.scrollbar(g, gridX + gridW - 3, gridY, gridH, rows * CELL, scroll, 0xFF5CE1E6);
        }
        Draw.fit(g, font, Component.literal(shown.size() + " blocks"), left + 10, top + panelH() - 19, 120, Draw.Align.LEFT, Theme.DIM);
        if (hovered != null) {
            Draw.queueTooltip(List.of(hovered.getName(), Component.literal("Click: build this instead").withColor(0xFF5CE1E6)), 0xFF5CE1E6);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= gridX && event.x() < gridX + gridW
                && event.y() >= gridY && event.y() < gridY + gridH) {
            int col = (int) ((event.x() - gridX) / CELL);
            int row = (int) ((event.y() - gridY + scroll) / CELL);
            int index = row * columns + col;
            if (col < columns && index >= 0 && index < shown.size()) {
                pick(shown.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = (shown.size() + columns - 1) / columns;
        scroll = Math.max(0, Math.min(Math.max(0, rows * CELL - gridH), scroll - scrollY * CELL * 2));
        return true;
    }
}
