package com.skyrpg.mounts.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.mounts.GriffinColor;
import com.skyrpg.mounts.MountPayloads;
import com.skyrpg.mounts.Mounts;
import com.skyrpg.mounts.SkyRpgMounts;
import java.util.ArrayList;
import java.util.List;
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
 * The K menu's Mounts page. Left: a live 3D preview of the selected griffin (in its feather colour; it spreads its wings when
 * the mouse is over it) with its name, colour and stats. Right: what its stats mean, and "Your stable", the mounts you own
 * (the one that is out first); click one to preview it, then Call (puts the one that is out away), Put away, or Take out
 * (moves it into your inventory as a whistle to trade). The server does the moving ({@link MountPayloads}).
 */
public final class MountsPage implements MenuPage {
    private static final int GAP = 6;
    private static final int TILE_H = 26;
    private static final int TILE_MIN_W = 110;
    private static final int HEADING = 14;

    private double scroll;
    private int contentHeight;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    /** What is selected: -2 nothing yet (the mount that is out, else the first stored), -1 the mount that is out, 0+ a stable index. */
    private int selected = -2;
    private float spread;
    private long lastFrame;
    private final List<Hit> hits = new ArrayList<>();

    private enum Action { SELECT, CALL, AWAY, TAKE_OUT }

    private record Hit(int x, int y, int w, int h, Action action, int index) {
    }

    @Override
    public Identifier id() {
        return SkyRpgMounts.id("mounts");
    }

    @Override
    public Component title() {
        return Component.literal("Mounts");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your griffins and your stable");
    }

    @Override
    public ItemStack icon() {
        return Mounts.create(GriffinColor.GOLDEN, "Aerie", 5, 10, null, "");
    }

    @Override
    public int order() {
        return 36;
    }

    @Override
    public void opened() {
        scroll = 0;
        selected = -2;
        spread = 0;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        int x = ctx.x();
        int y = ctx.y();
        hits.clear();
        ItemStack active = Mounts.active(ctx.player());
        List<ItemStack> stored = Mounts.storage(ctx.player());
        boolean hasActive = Mounts.isWhistle(active);
        if (selected == -2 || selected >= stored.size() || (selected == -1 && !hasActive)) {
            selected = hasActive ? -1 : (stored.isEmpty() ? -2 : 0);
        }
        ItemStack shown = selected == -1 ? active : selected >= 0 ? stored.get(selected) : ItemStack.EMPTY;
        boolean has = Mounts.isWhistle(shown);

        int leftW = Mth.clamp(Math.round(ctx.width() * 0.36f), 116, 180);
        int cardH = !has ? ctx.height() : Math.min(ctx.height(), Math.round(leftW * 0.8f) + 56 + 14);
        drawCard(ctx, x, y, leftW, cardH, shown, has, selected == -1);

        areaX = x + leftW + GAP + 4;
        areaW = ctx.width() - leftW - GAP - 4;
        areaY = y;
        areaH = ctx.height();
        double maxScroll = Math.max(0, contentHeight - areaH);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        int cy = areaY - (int) Math.round(scroll);
        int startY = cy;
        if (has) {
            cy = drawDetails(ctx, shown, cy, selected == -1);
            cy += GAP;
        }
        cy = drawStable(ctx, active, stored, has, cy);
        contentHeight = cy - startY;
        g.disableScissor();
        if (contentHeight > areaH) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, areaY, areaH, contentHeight, scroll, Theme.DIM);
        }
    }

    // ------------------------------------------------------------------ left: the selected griffin

    private void drawCard(PageContext ctx, int x, int y, int w, int h, ItemStack mount, boolean has, boolean isActive) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        GriffinColor color = has ? Mounts.color(mount) : GriffinColor.GOLDEN;
        int accent = has ? 0xFF000000 | color.nameRgb() : Theme.DIM;
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Draw.withAlpha(accent, 0.7f));
        int cx = x + w / 2;
        int infoH = has ? 56 : 30;
        int infoTop = y + h - infoH - 4;
        if (!has) {
            int mid = y + (infoTop - y) / 2;
            Draw.item(g, icon(), cx - 16, mid - 26, 2f);
            Draw.fit(g, font, Component.literal("No mount selected"), cx, infoTop + 6, w - 12, Draw.Align.CENTER, Theme.TEXT);
            Draw.fit(g, font, Component.literal("Pick one from your stable"), cx, infoTop + 18, w - 12, Draw.Align.CENTER, Theme.MUTED);
            return;
        }
        // the live model
        int bx0 = x + 6;
        int by0 = y + 8;
        int bx1 = x + w - 6;
        int by1 = infoTop - 6;
        Draw.panel(g, x + 4, y + 6, w - 8, infoTop - y - 10, Draw.mix(Theme.CARD, accent, 0.06f), Draw.mix(Theme.CARD_BORDER, accent, 0.25f));
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean over = ctx.hovered(bx0, by0, bx1 - bx0, by1 - by0);
        spread = Mth.clamp(spread + (over ? 1 : -1) * dt * 4f, 0f, 1f);
        GriffinShowcase.draw(g, bx0, by0, bx1, by1, color, spread, ctx.mouseX(), ctx.mouseY());

        // colour badge, name, stats
        String label = (color.rarity() == com.skyrpg.mounts.GriffinRarity.COMMON ? color.label() + " griffin" : color.rarity().label() + " " + color.label())
                .toUpperCase(java.util.Locale.ROOT);
        int pillW = font.width(label) + 12;
        Draw.rounded(g, cx - pillW / 2, infoTop, pillW, 11, Draw.withAlpha(accent, 0.25f));
        Draw.text(g, font, label, cx - font.width(label) / 2, infoTop + 2, accent);
        if (isActive) {
            Draw.text(g, font, "OUT", x + 8, y + 10, Theme.GREEN);
        }
        Draw.fit(g, font, Component.literal(Mounts.name(mount)), cx, infoTop + 15, w - 12, Draw.Align.CENTER, Theme.TEXT);
        Draw.fit(g, font, Component.literal("Speed +" + Mounts.speedBonus(mount) + "%"), cx, infoTop + 27, w - 12, Draw.Align.CENTER, Theme.AQUA);
        Draw.fit(g, font, Component.literal("Flight Stamina +" + Mounts.staminaBonus(mount) + "%"), cx, infoTop + 39, w - 12, Draw.Align.CENTER, Theme.GREEN);
    }

    // ------------------------------------------------------------------ right: what it gives

    private int heading(PageContext ctx, String text, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Draw.text(g, ctx.font(), text, areaX + 2, y + 4, Theme.DIM);
        int lineX = areaX + 2 + ctx.font().width(text) + 6;
        g.fill(lineX, y + 8, areaX + areaW - 2, y + 9, Theme.BORDER);
        return y + HEADING;
    }

    private int drawDetails(PageContext ctx, ItemStack mount, int y, boolean isActive) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        y = heading(ctx, isActive ? "MOUNT OUT" : "SELECTED MOUNT", y);
        int speed = Mounts.speedBonus(mount);
        int stamina = Mounts.staminaBonus(mount);
        // stat bars: the bonus against the best a griffin can roll
        y = statBar(ctx, "Speed", "+" + speed + "%", speed / 15f, Theme.AQUA, y);
        y = statBar(ctx, "Flight Stamina", "+" + stamina + "%  (" + Math.round(Mounts.flightSeconds(stamina)) + " s)", stamina / 25f, Theme.GREEN, y);
        y += 3;
        String owner = Mounts.ownerName(mount);
        if (!owner.isEmpty()) {
            Draw.text(g, font, Component.literal("Tamed by ").withColor(Theme.MUTED).append(Component.literal(owner).withColor(Theme.TEXT)), areaX + 4, y + 1, Theme.MUTED);
            y += 12;
        }
        y = wrapped(ctx, "Call it to stand next to you, right-click it to mount. Jump takes off and climbs, sneak descends, sprint glides faster. "
                + "It flies on stamina, which refills on the ground. Walk away from it, or get off for 30 seconds, and it goes back to the stable.",
                areaX + 4, y, areaW - 8, Theme.DIM);
        return y + 2;
    }

    private int statBar(PageContext ctx, String name, String value, float fraction, int color, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.text(g, font, name, areaX + 6, y + 1, Theme.MUTED);
        Draw.rightText(g, font, value, areaX + areaW - 6, y + 1, color);
        Draw.bar(g, areaX + 6, y + 11, areaW - 12, 4, Mth.clamp(fraction, 0.04f, 1f), color);
        return y + 19;
    }

    private int wrapped(PageContext ctx, String text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : Draw.wrap(ctx.font(), Component.literal(text), width)) {
            ctx.graphics().text(ctx.font(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ right: your stable

    private int drawStable(PageContext ctx, ItemStack active, List<ItemStack> stored, boolean hasSelection, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int max = Mounts.maxStorage();
        y = heading(ctx, "YOUR STABLE", y);
        Draw.rightText(g, font, stored.size() + " / " + max, areaX + areaW - 4, y - HEADING + 4, stored.size() >= max ? Theme.RED : Theme.MUTED);

        boolean hasActive = Mounts.isWhistle(active);
        if (hasSelection) {
            boolean activeSel = selected == -1;
            boolean invFull = ctx.player().getInventory().getFreeSlot() < 0;
            int bx = areaX + 2;
            bx += button(ctx, "Call", bx, y, !activeSel, Theme.GREEN, Action.CALL, selected,
                    hasActive ? "Calls it next to you; the mount that is out goes back to the stable" : "Calls it next to you") + 4;
            bx += button(ctx, "Put away", bx, y, activeSel, Theme.RED, Action.AWAY, -1, null) + 4;
            button(ctx, "Take out", bx, y, !activeSel && !invFull, Theme.AQUA, Action.TAKE_OUT, selected,
                    invFull && !activeSel ? "Inventory is full" : "Moves it into your inventory as a Griffin Whistle so you can trade it");
            y += 17;
        }
        if (!hasActive && stored.isEmpty()) {
            return drawEmpty(ctx, y);
        }
        int columns = Mth.clamp((areaW + GAP) / (TILE_MIN_W + GAP), 1, 3);
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

    /** A mount in the grid; {@code index} is its stable index, or -1 for the one that is out. */
    private void drawTile(PageContext ctx, ItemStack mount, int index, int tx, int ty, int w) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = 0xFF000000 | Mounts.color(mount).nameRgb();
        boolean hovered = ctx.hovered(tx, ty, w, TILE_H) && ctx.hovered(areaX, areaY, areaW, areaH);
        boolean chosen = index == selected;
        boolean isActive = index == -1;
        Draw.panel(g, tx, ty, w, TILE_H, hovered || chosen ? Theme.CARD_HOVER : Theme.CARD,
                hovered || chosen ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Draw.mix(Theme.CARD_BORDER, color, 0.3f));
        if (isActive) {
            g.fill(tx + 1, ty + 1, tx + w - 1, ty + 2, Theme.GREEN);
        }
        g.fill(tx + 1, ty + 4, tx + 3, ty + TILE_H - 4, Draw.withAlpha(color, 0.9f));
        g.item(mount, tx + 7, ty + 5);
        int textX = tx + 27;
        int room = w - 27 - 4;
        Draw.fitText(g, font, Component.literal(Mounts.name(mount)), textX, ty + 5, room, color);
        Draw.fitText(g, font, "Spd +" + Mounts.speedBonus(mount) + "%  Stm +" + Mounts.staminaBonus(mount) + "%" + (isActive ? "  OUT" : ""), textX, ty + 15, room,
                isActive ? Theme.GREEN : Theme.MUTED);
        hits.add(new Hit(tx, ty, w, TILE_H, Action.SELECT, index));
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
            List<Component> lines = new ArrayList<>();
            lines.add(mount.getHoverName());
            ItemLore lore = mount.get(DataComponents.LORE);
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
        Draw.item(g, icon(), areaX + 8, y + 6, 1.5f);
        Draw.text(g, font, "Your stable is empty", areaX + 40, y + 10, Theme.TEXT);
        int ty = y + 28;
        ty = wrapped(ctx, "Wild griffins nest on the high ledges of the Shattered Highlands. Feed one raw beef, mutton, chicken, porkchop or rabbit: "
                + "each feeding has a 1 in 5 chance to win it over, and it turns into a Griffin Whistle.", areaX + 8, ty, areaW - 20, Theme.MUTED);
        wrapped(ctx, "Right-click a whistle to add it to your stable and call it.", areaX + 8, ty + 3, areaW - 20, Theme.DIM);
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
                    case CALL -> {
                        ClientPlayNetworking.send(new MountPayloads.Summon(hit.index()));
                        selected = -1;
                    }
                    case AWAY -> {
                        ClientPlayNetworking.send(new MountPayloads.Away());
                        selected = -2;
                    }
                    case TAKE_OUT -> {
                        ClientPlayNetworking.send(new MountPayloads.TakeOut(hit.index()));
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
