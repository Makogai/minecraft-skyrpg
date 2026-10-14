package com.skyrpg.pets.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.text.Msg;
import com.skyrpg.pets.PetItems;
import com.skyrpg.pets.PetPayloads;
import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.Pets;
import com.skyrpg.pets.SkyRpgPets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The K menu's Pets page. Left: a live 3D preview of the active pet with its rarity, name, level and XP bar. Right: what it
 * gives (stats, perks, the skill it levels with) and "Your pets", the pet storage (the active pet first); click one to preview
 * it, then Equip, Unequip or Take out. The server does the moving ({@link PetPayloads}).
 */
public final class PetsPage implements MenuPage {
    private static final int GAP = 6;
    private static final int TILE_H = 26;
    private static final int TILE_MIN_W = 96;
    private static final int HEADING = 14;

    private double scroll;
    private int contentHeight;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    /** What is selected: -2 nothing yet (the active pet, else the first stored), -1 the active pet, 0+ a storage index. */
    private int selected = -2;
    /** Clickable things drawn this frame. */
    private final List<Hit> hits = new ArrayList<>();

    private enum Action { SELECT, EQUIP, UNEQUIP, TAKE_OUT }

    private record Hit(int x, int y, int w, int h, Action action, int index) {
    }

    @Override
    public Identifier id() {
        return SkyRpgPets.id("pets");
    }

    @Override
    public Component title() {
        return Component.literal("Pets");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your companions, one for each skill");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(PetItems.GOLDEN_EGG);
    }

    @Override
    public int order() {
        return 35;
    }

    @Override
    public void opened() {
        scroll = 0;
        selected = -2;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        hits.clear();
        ItemStack active = ctx.player().getAttachedOrElse(Pets.ACTIVE, ItemStack.EMPTY);
        List<ItemStack> stored = Pets.storage(ctx.player());
        boolean hasActive = Pets.type(active) != null;
        if (selected == -2 || selected >= stored.size() || (selected == -1 && !hasActive)) {
            selected = hasActive ? -1 : (stored.isEmpty() ? -2 : 0);
        }
        // the pet the left card and the details show: the selection
        ItemStack shown = selected == -1 ? active : selected >= 0 ? stored.get(selected) : ItemStack.EMPTY;
        PetType type = Pets.type(shown);

        int leftW = Mth.clamp(Math.round(ctx.width() * 0.36f), 116, 180);
        // A wider, shorter card fitted to the model: a model box about as tall as the card is wide, plus the info lines.
        int cardH = type == null ? ctx.height() : Math.min(ctx.height(), Math.round(leftW * 0.8f) + 56 + 14);
        drawActiveCard(ctx, x, y, leftW, cardH, shown, type, selected == -1);

        areaX = x + leftW + GAP + 4;
        areaW = ctx.width() - leftW - GAP - 4;
        areaY = y;
        areaH = ctx.height();
        double maxScroll = Math.max(0, contentHeight - areaH);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        int cy = areaY - (int) Math.round(scroll);
        int startY = cy;
        if (type != null) {
            cy = drawDetails(ctx, shown, type, cy, selected == -1);
            cy += GAP;
        }
        cy = drawPets(ctx, active, stored, type != null, cy);
        contentHeight = cy - startY;
        g.disableScissor();
        if (contentHeight > areaH) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, areaY, areaH, contentHeight, scroll, Theme.DIM);
        }
    }

    // ------------------------------------------------------------------ left: the active pet

    private void drawActiveCard(PageContext ctx, int x, int y, int w, int h, ItemStack active, PetType type, boolean isActive) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        PetRarity rarity = type == null ? null : Pets.rarity(active);
        int accent = rarity == null ? Theme.DIM : 0xFF000000 | Pets.rgb(rarity);
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Draw.withAlpha(accent, 0.7f));
        int cx = x + w / 2;
        int infoH = type == null ? 30 : 56;
        int infoTop = y + h - infoH - 4;
        if (type == null) {
            int mid = y + (infoTop - y) / 2;
            Draw.item(g, new ItemStack(PetItems.GOLDEN_EGG), cx - 16, mid - 26, 2f);
            Draw.fit(g, font, Component.literal("No pet selected"), cx, infoTop + 6, w - 12, Draw.Align.CENTER, Theme.TEXT);
            Draw.fit(g, font, Component.literal("Pick one from your storage"), cx, infoTop + 18, w - 12, Draw.Align.CENTER, Theme.MUTED);
            return;
        }
        // the live model
        Draw.panel(g, x + 4, y + 6, w - 8, infoTop - y - 10, Draw.mix(Theme.CARD, accent, 0.06f), Draw.mix(Theme.CARD_BORDER, accent, 0.25f));
        PetShowcase.draw(g, x + 6, y + 8, x + w - 6, infoTop - 6, type, rarity, ctx.mouseX(), ctx.mouseY());

        // rarity badge, name, level, XP
        String label = rarity.label().toUpperCase(java.util.Locale.ROOT);
        int pillW = font.width(label) + 12;
        Draw.rounded(g, cx - pillW / 2, infoTop, pillW, 11, Draw.withAlpha(accent, 0.25f));
        Draw.text(g, font, label, cx - font.width(label) / 2, infoTop + 2, accent);
        if (isActive) {
            Draw.text(g, font, "ACTIVE", x + 8, y + 10, Theme.GREEN);
        }
        Draw.fit(g, font, Component.literal(type.displayName()), cx, infoTop + 15, w - 12, Draw.Align.CENTER, Theme.TEXT);
        int level = Pets.level(active);
        Draw.fit(g, font, Component.literal("Level " + level), cx, infoTop + 27, w - 12, Draw.Align.CENTER, Theme.AQUA);
        if (level >= Pets.MAX_LEVEL) {
            Draw.bar(g, x + 10, infoTop + 40, w - 20, 6, 1f, Theme.ACCENT);
            Draw.fit(g, font, Component.literal("MAX LEVEL"), cx, infoTop + 48, w - 12, Draw.Align.CENTER, Theme.ACCENT);
        } else {
            double into = Pets.xp(active) - Pets.xpFor(level);
            double need = Pets.xpFor(level + 1) - Pets.xpFor(level);
            Draw.bar(g, x + 10, infoTop + 40, w - 20, 6, (float) (into / need), Theme.AQUA);
            Draw.fit(g, font, Component.literal(Msg.number(Math.floor(into)) + " / " + Msg.number(need) + " XP"), cx, infoTop + 48, w - 12,
                    Draw.Align.CENTER, Theme.MUTED);
        }
    }

    // ------------------------------------------------------------------ right: what it gives

    private int heading(PageContext ctx, String text, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Draw.text(g, ctx.font(), text, areaX + 2, y + 4, Theme.DIM);
        int lineX = areaX + 2 + ctx.font().width(text) + 6;
        g.fill(lineX, y + 8, areaX + areaW - 2, y + 9, Theme.BORDER);
        return y + HEADING;
    }

    private int drawDetails(PageContext ctx, ItemStack active, PetType type, int y, boolean isActive) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        PetRarity rarity = Pets.rarity(active);
        int level = Pets.level(active);
        y = heading(ctx, isActive ? "ACTIVE PET" : "SELECTED PET", y);

        // the skill it levels with
        String skill = SkillLevels.displayName(type.skill());
        Draw.text(g, font, Component.literal("Levels with ").withColor(Theme.MUTED).append(Component.literal(skill).withColor(Theme.TEXT)),
                areaX + 4, y + 1, Theme.MUTED);
        y += 11;
        y = wrapped(ctx, "Earns 25% of the XP you get in " + skill + " and 5% from other skills, up to level " + Pets.MAX_LEVEL + ". Only the active pet earns XP and gives its stats.",
                areaX + 4, y, areaW - 8, Theme.DIM);
        y += 4;

        // stats at this level
        int statsTop = y;
        Map<StatType, Double> stats = type.stats(level, rarity);
        for (Map.Entry<StatType, Double> stat : stats.entrySet()) {
            StatType s = stat.getKey();
            Draw.text(g, font, s.symbol(), areaX + 6, y + 1, s.color());
            Draw.text(g, font, s.name(), areaX + 18, y + 1, Theme.MUTED);
            Draw.rightText(g, font, s.formatBonus(stat.getValue()), areaX + areaW - 6, y + 1, s.color());
            y += 11;
        }
        if (stats.size() > 0) {
            g.fill(areaX + 2, statsTop - 1, areaX + 3, y - 1, Draw.withAlpha(0xFF000000 | Pets.rgb(rarity), 0.8f));
        }
        y += 3;

        // perks
        y = perk(ctx, level >= 25, "Lv 25", type.perk25Text(), y);
        y = perk(ctx, level >= 50, "Lv 50", type.perk50Text(), y);
        return y;
    }

    private int perk(PageContext ctx, boolean unlocked, String level, String text, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        int color = unlocked ? Theme.GREEN : Theme.DIM;
        Draw.text(g, ctx.font(), unlocked ? "✔" : "✘", areaX + 6, y + 1, color);
        Draw.text(g, ctx.font(), level, areaX + 18, y + 1, unlocked ? Theme.TEXT : Theme.DIM);
        int textX = areaX + 18 + ctx.font().width("Lv 25") + 6;
        return Math.max(y + 11, wrapped(ctx, text, textX, y + 1, areaX + areaW - textX - 4, unlocked ? Theme.TEXT : Theme.DIM) + 2);
    }

    /** Wraps a line of text into the width; returns the y below it. */
    private int wrapped(PageContext ctx, String text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : Draw.wrap(ctx.font(), Component.literal(text), width)) {
            ctx.graphics().text(ctx.font(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ right: your pets

    private int drawPets(PageContext ctx, ItemStack active, List<ItemStack> stored, boolean hasSelection, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int max = Pets.maxStorage();
        String count = stored.size() + " / " + max;
        y = heading(ctx, "PET STORAGE", y);
        Draw.rightText(g, font, count, areaX + areaW - 4, y - HEADING + 4, stored.size() >= max ? Theme.RED : Theme.MUTED);

        boolean hasActive = Pets.type(active) != null;
        if (hasSelection) {
            boolean activeSel = selected == -1;
            boolean invFull = ctx.player().getInventory().getFreeSlot() < 0;
            int bx = areaX + 2;
            bx += button(ctx, "Equip", bx, y, !activeSel, Theme.GREEN, Action.EQUIP, selected, null) + 4;
            bx += button(ctx, "Unequip", bx, y, activeSel && stored.size() < max, Theme.RED, Action.UNEQUIP, -1,
                    activeSel && stored.size() >= max ? "Pet storage is full" : null) + 4;
            button(ctx, "Take out", bx, y, !activeSel && !invFull, Theme.AQUA, Action.TAKE_OUT, selected,
                    invFull && !activeSel ? "Inventory is full" : "Moves it into your inventory so you can trade it");
            y += 17;
        }
        if (!hasActive && stored.isEmpty()) {
            return drawEmpty(ctx, y);
        }
        int columns = Mth.clamp((areaW + GAP) / (TILE_MIN_W + GAP), 1, 4);
        int tileW = (areaW - 4 - GAP * (columns - 1)) / columns;
        int n = 0;
        if (hasActive) {
            drawTile(ctx, active, -1, areaX + 2, y, tileW);
            n = 1;
        }
        for (int i = 0; i < stored.size(); i++, n++) {
            drawTile(ctx, stored.get(i), i, areaX + 2 + (n % columns) * (tileW + GAP), y + (n / columns) * (TILE_H + 4), tileW);
        }
        int rows = (n + columns - 1) / columns;
        return y + rows * (TILE_H + 4) + 2;
    }

    /** One action button; returns its width. A disabled one is dimmed and does nothing. */
    private int button(PageContext ctx, String label, int bx, int by, boolean enabled, int color, Action action, int index, String tip) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int bw = font.width(label) + 16;
        boolean hovered = ctx.hovered(bx, by, bw, 13) && ctx.hovered(areaX, areaY, areaW, areaH);
        int border = enabled && hovered ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Theme.CARD_BORDER;
        Draw.panel(g, bx, by, bw, 13, enabled && hovered ? Theme.CARD_HOVER : Theme.CARD, border);
        Draw.text(g, font, label, bx + 8, by + 3, enabled ? (hovered ? color : Theme.TEXT) : Theme.DIM);
        if (enabled) {
            hits.add(new Hit(bx, by, bw, 13, action, index));
        }
        if (hovered) {
            if (enabled) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            if (tip != null) {
                ctx.tooltip(List.of(Component.literal(tip)), color);
            }
        }
        return bw;
    }

    /** A pet in the grid; {@code index} is its storage index, or -1 for the active pet. */
    private void drawTile(PageContext ctx, ItemStack pet, int index, int tx, int ty, int w) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        PetRarity rarity = Pets.rarity(pet);
        PetType type = Pets.type(pet);
        int color = 0xFF000000 | Pets.rgb(rarity);
        boolean hovered = ctx.hovered(tx, ty, w, TILE_H) && ctx.hovered(areaX, areaY, areaW, areaH);
        boolean chosen = index == selected;
        boolean isActive = index == -1;
        Draw.panel(g, tx, ty, w, TILE_H, hovered || chosen ? Theme.CARD_HOVER : Theme.CARD,
                hovered || chosen ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Draw.mix(Theme.CARD_BORDER, color, 0.3f));
        if (isActive) {
            g.fill(tx + 1, ty + 1, tx + w - 1, ty + 2, Theme.GREEN);
        }
        g.fill(tx + 1, ty + 4, tx + 3, ty + TILE_H - 4, Draw.withAlpha(color, 0.9f));
        g.item(pet, tx + 7, ty + 5);
        int textX = tx + 27;
        int room = w - 27 - 4;
        Draw.fitText(g, font, Component.literal(rarity.label() + " " + type.displayName()), textX, ty + 5, room, color);
        Draw.fitText(g, font, "Level " + Pets.level(pet) + (isActive ? "  ACTIVE" : ""), textX, ty + 15, room, isActive ? Theme.GREEN : Theme.MUTED);
        hits.add(new Hit(tx, ty, w, TILE_H, Action.SELECT, index));
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
            List<Component> lines = new ArrayList<>();
            lines.add(pet.getHoverName());
            ItemLore lore = pet.get(DataComponents.LORE);
            if (lore != null) {
                lines.addAll(lore.lines());
            }
            ctx.tooltip(lines, color);
        }
    }

    private int drawEmpty(PageContext ctx, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int boxH = 76;
        Draw.panel(g, areaX + 2, y, areaW - 4, boxH, Theme.CARD, Theme.CARD_BORDER);
        Draw.item(g, new ItemStack(PetItems.egg(PetType.MOLE)), areaX + 8, y + 6, 1f);
        Draw.item(g, new ItemStack(PetItems.egg(PetType.OWL)), areaX + 26, y + 6, 1f);
        Draw.item(g, new ItemStack(PetItems.GOLDEN_EGG), areaX + 44, y + 6, 1f);
        Draw.text(g, font, "Your pet storage is empty", areaX + 66, y + 10, Theme.TEXT);
        int ty = y + 28;
        ty = wrapped(ctx, "Pet eggs are sold by the skill masters from level 10 and drop from dungeons and world bosses.",
                areaX + 8, ty, areaW - 20, Theme.MUTED);
        wrapped(ctx, "Right-click an egg to hatch it, or a pet item to store it. Stored pets can be equipped here or taken out to trade.",
                areaX + 8, ty + 3, areaW - 20, Theme.DIM);
        return y + boxH + 2;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !ctx.hovered(areaX, areaY, areaW, areaH)) {
            return false;
        }
        for (Hit hit : hits) {
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                switch (hit.action()) {
                    case SELECT -> selected = hit.index();
                    case EQUIP -> {
                        ClientPlayNetworking.send(new PetPayloads.Equip(hit.index()));
                        selected = -1;
                    }
                    case UNEQUIP -> {
                        ClientPlayNetworking.send(new PetPayloads.Unequip());
                        selected = -2;
                    }
                    case TAKE_OUT -> {
                        ClientPlayNetworking.send(new PetPayloads.TakeOut(hit.index()));
                        selected = -2;
                    }
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (contentHeight <= areaH) {
            return false;
        }
        scroll = Mth.clamp(scroll - amount * 14, 0, contentHeight - areaH);
        return true;
    }
}
