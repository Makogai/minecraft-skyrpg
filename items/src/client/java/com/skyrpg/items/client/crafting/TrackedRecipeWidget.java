package com.skyrpg.items.client.crafting;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.crafting.BenchPayloads;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The recipe you're working towards (right-click one at the Artisan Bench): each ingredient with how many you have of how
 * many you need, counting backpacks like the bench does. The server refreshes it every second. Movable in the HUD editor.
 */
public final class TrackedRecipeWidget implements HudWidget {
    private static final int WIDTH = 150;
    private static final int ROW = 12;
    private static final int TOP = 22;

    private static BenchPayloads.Entry tracked;

    public static void receive(BenchPayloads.Tracked payload) {
        tracked = payload.entry().isEmpty() ? null : payload.entry().getFirst();
    }

    /** The tracked recipe's id, or null. */
    public static Identifier trackedId() {
        return tracked == null ? null : tracked.id();
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("skyrpg_items", "tracked_recipe");
    }

    @Override
    public Component name() {
        return Component.literal("Tracked Recipe");
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        if (tracked == null) {
            return TOP + 2 * ROW + 4;
        }
        return TOP + tracked.ingredients().size() * ROW + (footer(tracked).isEmpty() ? 4 : 16);
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.MIDDLE_RIGHT;
    }

    @Override
    public int defaultX() {
        return -6;
    }

    @Override
    public int defaultY() {
        return 0;
    }

    @Override
    public boolean visible() {
        return tracked != null;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        BenchPayloads.Entry entry = tracked;
        if (entry == null && !editing) {
            return;
        }
        int height = height(font);
        Draw.rounded(g, 0, 0, WIDTH, height, 0xE0121620);
        g.fill(0, 2, 2, height - 2, Theme.ACCENT);
        if (entry == null) {
            Draw.fitText(g, font, "Tracked recipe", 6, 7, WIDTH - 10, Theme.TEXT);
            Draw.fitText(g, font, "Right-click a recipe at the", 6, TOP, WIDTH - 10, Theme.MUTED);
            Draw.fitText(g, font, "Artisan Bench to track it", 6, TOP + ROW, WIDTH - 10, Theme.MUTED);
            return;
        }
        ItemStack result = entry.result();
        g.item(result, 4, 3);
        Draw.fitText(g, font, Component.literal(result.getHoverName().getString()), 24, 4, WIDTH - 28, nameColor(result));
        Draw.fitText(g, font, "Tracking", 24, 13, WIDTH - 28, Theme.DIM);
        List<ItemStack> ingredients = entry.ingredients();
        int y = TOP;
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack ingredient = ingredients.get(i);
            int have = entry.have().get(i);
            boolean enough = have >= ingredient.getCount();
            Draw.item(g, ingredient, 6, y, 0.625f);
            String count = Draw.compactNumbers(Msg.number(Math.min(have, ingredient.getCount()))) + "/" + Draw.compactNumbers(Msg.number(ingredient.getCount()));
            int countWidth = font.width(count);
            Draw.fitText(g, font, Component.literal(ingredient.getHoverName().getString()), 19, y + 1, WIDTH - 26 - countWidth, enough ? Theme.MUTED : Theme.TEXT);
            Draw.text(g, font, count, WIDTH - 5 - countWidth, y + 1, enough ? Theme.GREEN : Theme.RED);
            y += ROW;
        }
        String footer = footer(entry);
        if (!footer.isEmpty()) {
            Draw.fitText(g, font, footer, 6, y + 3, WIDTH - 10, !entry.unlocked() ? Theme.RED : Theme.GREEN);
        }
    }

    /** The line under the ingredients: still locked, or ready to craft; nothing while you're collecting. */
    private static String footer(BenchPayloads.Entry entry) {
        return !entry.unlocked() ? "Needs " + entry.requirement() : entry.craftable() > 0 ? "✔ Ready at an Artisan Bench" : "";
    }

    private static int nameColor(ItemStack stack) {
        TextColor color = stack.getHoverName().getStyle().getColor();
        return color != null ? 0xFF000000 | color.getValue() : Theme.TEXT;
    }
}
