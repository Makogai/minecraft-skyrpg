package com.skyrpg.economy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.economy.SkyRpgEconomy;
import com.skyrpg.economy.net.EconomyPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The SkyRPG menu's Pay page: pick an online player, type an amount ("2.5k" works) or tap a preset, and
 * send. Big payments (half your purse or more, or 10,000+) take a second click. The server checks it all
 * again ({@code Payments}) and answers with a {@link EconomyPayloads.Notice}, shown on the page, while the
 * purse line counts down as the coins leave.
 *
 * <p>Wide pages show the player list and the payment side by side. Narrow ones (big GUI scales) show the
 * list, then the payment once a player is picked, with a back arrow. While a player is picked, digits and
 * the k/m/b suffixes type into the amount instead of switching pages; Enter sends.
 */
public final class PayPage implements MenuPage {
    /** Content at least this wide shows the list and the payment side by side. */
    private static final int SPLIT_FROM = 280;
    private static final int LIST_HEADER = 18;
    private static final int ROW = 20;
    /** Card heights the roomy and full payment layouts need; shorter cards get the compact one. */
    private static final int ROOMY_HEIGHT = 176;
    private static final int FULL_HEIGHT = 140;
    /** Payments of at least this much, or of half the purse or more, take a second click. */
    private static final long BIG_PAYMENT = 10_000;
    private static final long CONFIRM_MILLIS = 4000;
    private static final long NOTICE_MILLIS = 3500;
    private static final long SENT_MILLIS = 1800;
    private static final long REPLY_TIMEOUT_MILLIS = 2500;
    private static final long FLOATER_MILLIS = 1500;
    private static final int MINUS = 0xFFFF8A65;
    private static final String[] PRESETS = {"100", "1k", "10k", "Half", "All"};
    /** The Send label may shrink this far to keep the whole name before it is cut. */
    private static final float LABEL_MIN_SCALE = 0.9f;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private record Floater(long amount, long at) {
    }

    private final AmountField field = new AmountField(true);
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    private final List<Floater> floaters = new ArrayList<>();
    private UUID target;
    private String targetName = "";
    private PlayerSkin targetSkin;
    private double listScroll;
    /** The scrolling rows (x, y, w, h), for the mouse wheel. */
    private final int[] listArea = new int[4];
    private long lastFrame = Util.getMillis();
    private float frameSeconds;
    private long lastPurse = -1;
    private double shownPurse;
    private long purseChangedAt = -1;
    private boolean purseRose;
    private long armedAt = -1;
    private long armedAmount;
    private UUID armedTarget;
    private long sentAt = -1;
    private long sentAmount;
    private long succeededAt = -1;
    private long succeededAmount;
    private Component notice;
    private boolean noticeGood;
    private long noticeAt = -1;
    private String flashKey;
    private long flashAt;
    private ItemStack emptyIcon;

    @Override
    public Identifier id() {
        return SkyRpgEconomy.id("pay");
    }

    @Override
    public Component title() {
        return Component.literal("Pay");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Send coins to another player");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLD_NUGGET);
    }

    @Override
    public int order() {
        return 50;
    }

    /** The server's answer to a payment (or a shop/bank notice that arrives while the menu is open). */
    void notice(Component text, boolean good) {
        long now = Util.getMillis();
        notice = text;
        noticeGood = good;
        noticeAt = now;
        if (sentAt >= 0 && good) {
            succeededAt = now;
            succeededAmount = sentAmount;
            field.clear();
        }
        sentAt = -1;
        armedAt = -1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        long now = Util.getMillis();
        boolean resumed = now - lastFrame > 300;
        frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        hits.clear();
        listArea[2] = 0;
        trackPurse(ctx.player(), now, resumed);
        if (sentAt >= 0 && now - sentAt > REPLY_TIMEOUT_MILLIS) {
            sentAt = -1;
        }

        List<PlayerInfo> players = others(ctx.player());
        PlayerInfo selected = null;
        for (PlayerInfo info : players) {
            if (info.getProfile().id().equals(target)) {
                selected = info;
                targetName = info.getProfile().name();
                targetSkin = info.getSkin();
            }
        }

        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int h = ctx.height();
        if (w >= SPLIT_FROM) {
            if (players.isEmpty() && target == null) {
                drawEmpty(ctx, x, y, w, h);
                return;
            }
            int listWidth = Mth.clamp(w * 3 / 8, 108, 136);
            drawList(ctx, players, x, y, listWidth, h, false);
            if (target == null) {
                drawPickHint(ctx, x + listWidth + 8, y, w - listWidth - 8, h);
            } else {
                drawPayment(ctx, selected, x + listWidth + 8, y, w - listWidth - 8, h, false, now);
            }
        } else if (target != null) {
            drawPayment(ctx, selected, x, y, w, h, true, now);
        } else if (players.isEmpty()) {
            drawEmpty(ctx, x, y, w, h);
        } else {
            drawList(ctx, players, x, y, w, h, true);
        }
    }

    /** Everyone online but you, by name. */
    private static List<PlayerInfo> others(LocalPlayer self) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        List<PlayerInfo> players = new ArrayList<>();
        if (connection == null) {
            return players;
        }
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            if (!info.getProfile().id().equals(self.getUUID())) {
                players.add(info);
            }
        }
        players.sort(Comparator.comparing(info -> info.getProfile().name().toLowerCase(Locale.ROOT)));
        return players;
    }

    private void drawList(PageContext ctx, List<PlayerInfo> players, int x, int y, int w, int h, boolean drillIn) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        Draw.text(g, font, "PLAYERS", x + 8, y + 6, Theme.DIM);
        Draw.rightText(g, font, players.size() + " online", x + w - 8, y + 6, Theme.DIM);
        g.fill(x + 6, y + LIST_HEADER, x + w - 6, y + LIST_HEADER + 1, Theme.BORDER);
        if (players.isEmpty()) {
            // Only in the side-by-side layout, while the player you picked has gone offline.
            Draw.fit(g, font, Component.literal("Nobody else is online"), x + w / 2, y + LIST_HEADER + (h - LIST_HEADER) / 2 - 4, w - 12,
                    Draw.Align.CENTER, Theme.DIM);
            return;
        }

        int top = y + LIST_HEADER + 3;
        int bottom = y + h - 3;
        int viewport = bottom - top;
        int content = players.size() * ROW;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, content - viewport));
        boolean scrolls = content > viewport;
        int rowX = x + 4;
        int rowWidth = w - 8 - (scrolls ? 5 : 0);
        listArea[0] = x;
        listArea[1] = top;
        listArea[2] = w;
        listArea[3] = viewport;
        boolean mouseIn = ctx.hovered(x, top, w, viewport);

        g.enableScissor(x + 1, top, x + w - 1, bottom);
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo info = players.get(i);
            int rowY = top + i * ROW - (int) listScroll;
            if (rowY + ROW < top || rowY > bottom) {
                continue;
            }
            UUID id = info.getProfile().id();
            String name = info.getProfile().name();
            boolean active = id.equals(target);
            boolean hovered = mouseIn && ctx.hovered(rowX, rowY, rowWidth, ROW - 2);
            float hv = hover(g, "row:" + id, hovered && !active);
            if (active) {
                Draw.rounded(g, rowX, rowY, rowWidth, ROW - 2, Theme.ACCENT_SOFT);
                g.fill(rowX, rowY + 5, rowX + 2, rowY + ROW - 7, Theme.ACCENT);
            } else if (hv > 0.02f) {
                Draw.rounded(g, rowX, rowY, rowWidth, ROW - 2, Draw.withAlpha(Theme.HOVER, hv));
            }
            int nudge = Math.round(hv * 1.5f);
            PlayerFaceExtractor.extractRenderState(g, info.getSkin(), rowX + 6 + nudge, rowY + 3, 12);
            int nameX = rowX + 24 + nudge;
            int nameRoom = rowX + rowWidth - (drillIn ? 14 : 5) - nameX;
            boolean cut = Draw.fitText(g, font, name, nameX, rowY + 5, nameRoom, Theme.TEXT);
            if (drillIn) {
                Draw.text(g, font, "›", rowX + rowWidth - 9 + nudge, rowY + 5, Draw.mix(Theme.DIM, Theme.ACCENT, hv));
            }
            if (hovered && cut) {
                ctx.tooltip(List.of(Component.literal(name).withColor(Theme.TEXT)), Theme.ACCENT);
            }
            int hitTop = Math.max(rowY, top);
            int hitBottom = Math.min(rowY + ROW - 2, bottom);
            hit(rowX, hitTop, rowWidth, hitBottom - hitTop, () -> select(info));
        }
        g.disableScissor();
        if (scrolls) {
            Draw.scrollbar(g, x + w - 6, top, viewport, content, listScroll, Theme.DIM);
        }
    }

    /** Nobody else is online. */
    private void drawEmpty(PageContext ctx, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        if (emptyIcon == null) {
            emptyIcon = new ItemStack(Items.SPYGLASS);
        }
        List<FormattedCharSequence> body = Draw.wrap(font, Component.literal("Players you can pay show up here as soon as they're online."),
                Math.min(w - 24, 210));
        boolean icon = h >= 96;
        int block = (icon ? 30 : 0) + 14 + body.size() * 10 + 8 + 9;
        int top = y + Math.max(6, (h - block) / 2);
        int cx = x + w / 2;
        if (icon) {
            int bob = Math.round(Mth.sin(ctx.age() * 2.2f) * 1.5f);
            Draw.item(g, emptyIcon, cx - 12, top + bob, 1.5f);
            top += 30;
        }
        Draw.fit(g, font, Component.literal("Nobody else is online"), cx, top, w - 16, Draw.Align.CENTER, Theme.TEXT);
        top += 14;
        for (FormattedCharSequence line : body) {
            g.text(font, line, cx - font.width(line) / 2, top, Theme.MUTED, false);
            top += 10;
        }
        drawPurseCentered(ctx, cx, top + 8, w - 16);
    }

    /** Side by side, before a player is picked. */
    private void drawPickHint(PageContext ctx, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        int cx = x + w / 2;
        int top = y + h / 2 - 24;
        int nudge = Math.round(Mth.sin(ctx.age() * 4f) * 2f);
        Draw.scaledText(g, font, Component.literal("←"), cx - 6 + nudge, top, 1.5f, Theme.ACCENT);
        Draw.fit(g, font, Component.literal("Pick a player"), cx, top + 18, w - 16, Draw.Align.CENTER, Theme.TEXT);
        Draw.fit(g, font, Component.literal("to send them coins"), cx, top + 29, w - 16, Draw.Align.CENTER, Theme.MUTED);
        drawPurseCentered(ctx, cx, top + 46, w - 16);
    }

    private void drawPayment(PageContext ctx, PlayerInfo info, int x, int y, int w, int h, boolean back, long now) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        long purse = Purse.get(ctx.player());
        long amount = field.value();
        boolean online = info != null;
        boolean supported = ClientPlayNetworking.canSend(EconomyPayloads.Pay.TYPE);
        boolean roomy = h >= ROOMY_HEIGHT;
        boolean full = h >= FULL_HEIGHT;
        int ix = x + 8;
        int iw = w - 16;

        // Each part's height; the whole stack is centred in the card.
        int headerHeight = roomy ? 28 : full ? 16 : 14;
        int fieldHeight = full ? 20 : 18;
        int presetHeight = full ? 16 : 14;
        int sendHeight = full ? 20 : 18;
        int stack = full ? headerHeight + 12 + fieldHeight + 5 + presetHeight + 8 + 9 + 6 + sendHeight + 6 + 9
                : headerHeight + 4 + fieldHeight + 4 + presetHeight + 4 + sendHeight;
        int cy = y + Math.max(full ? 8 : 5, (h - stack) / 2);

        // Header: back arrow (narrow layout), face, name; compact layouts put the purse on the right.
        int hx = ix;
        if (back) {
            int bw = 14;
            boolean hovered = ctx.hovered(hx, cy, bw, headerHeight);
            float hv = hover(g, "back", hovered);
            Draw.rounded(g, hx, cy, bw, headerHeight, Draw.mix(Theme.TRACK, Theme.ACCENT, 0.06f + 0.22f * hv));
            Draw.text(g, font, "←", hx + (bw - font.width("←")) / 2, cy + (headerHeight - 8) / 2, Draw.mix(Theme.MUTED, Theme.TEXT, hv));
            hit(hx, cy, bw, headerHeight, this::back);
            if (hovered) {
                ctx.tooltip(List.of(Component.literal("Back to players").withColor(Theme.TEXT)), Theme.ACCENT);
            }
            hx += bw + 6;
        }
        int face = roomy ? 24 : full ? 16 : 12;
        int faceY = cy + (headerHeight - face) / 2;
        if (targetSkin != null) {
            PlayerFaceExtractor.extractRenderState(g, targetSkin, hx, faceY, face);
            if (!online) {
                g.fill(hx, faceY, hx + face, faceY + face, 0xA0171C26);
            }
        }
        int nameX = hx + face + 6;
        int right = ix + iw;
        int nameRoom = right - nameX;
        if (!full) {
            int purseWidth = drawPurseRight(ctx, right, cy + (headerHeight - 8) / 2, Math.max(40, (right - nameX) / 2), purse, now);
            nameRoom -= purseWidth + 8;
        } else if (!roomy && !online) {
            Draw.rightText(g, font, "Offline", right, cy + 4, Theme.RED);
            nameRoom -= font.width("Offline") + 8;
        }
        Component name = Component.literal(targetName);
        boolean cut;
        if (roomy) {
            cut = Draw.fit(g, font, name, nameX, cy + 2, nameRoom, 1.5f, 1f, Draw.Align.LEFT, online ? Theme.TEXT : Theme.MUTED, false);
            Draw.fitText(g, font, online ? "Online now" : "Went offline", nameX, cy + 17, nameRoom, online ? Theme.GREEN : Theme.RED);
        } else {
            cut = Draw.fitText(g, font, name, nameX, cy + (headerHeight - 8) / 2, nameRoom, online ? Theme.TEXT : Theme.MUTED);
        }
        if (cut) {
            Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), nameX, cy, nameRoom, headerHeight, List.of(name.copy().withColor(Theme.TEXT)), Theme.ACCENT);
        }
        int rowY = cy + headerHeight + 4;
        if (full) {
            g.fill(ix, cy + headerHeight + 6, right, cy + headerHeight + 7, Theme.BORDER);
            rowY = cy + headerHeight + 12;
        }

        drawField(g, font, ix, rowY, iw, fieldHeight, amount);
        rowY += fieldHeight + (full ? 5 : 4);

        boolean armed = armed(now, amount);
        boolean status = noticeShowing(now) || armed || !supported;
        if (full || !status) {
            drawPresets(ctx, ix, rowY, iw, presetHeight, purse, amount);
        } else {
            drawStatus(ctx, ix, rowY + (presetHeight - 8) / 2, iw, now, purse, amount, supported, armed, false);
        }
        rowY += presetHeight + (full ? 8 : 4);

        if (full) {
            drawPurseLine(ctx, ix, rowY, iw, purse, amount, now);
            rowY += 9 + 6;
        }

        drawSend(ctx, ix, rowY, iw, sendHeight, purse, amount, online, supported, armed, now);
        rowY += sendHeight + 6;

        if (full) {
            drawStatus(ctx, ix, rowY, iw, now, purse, amount, supported, armed, true);
        }
    }

    /** The amount box: coin icon, what you typed (or a placeholder), "= ⛁ 2,500" for shorthand, and a caret. */
    private void drawField(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, long amount) {
        boolean invalid = amount < 0;
        Draw.panel(g, x, y, w, h, Theme.TRACK, invalid ? Theme.RED : Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.55f));
        int textY = y + (h - 8) / 2;
        Draw.text(g, font, StatTypes.COIN_ICON, x + 6, textY, Theme.ACCENT);
        int textX = x + 6 + font.width(StatTypes.COIN_ICON) + 4;
        int room = w - (textX - x) - 8;
        if (field.isEmpty()) {
            Draw.fitText(g, font, Draw.firstFitting(font, room, "Amount, e.g. 2.5k", "Amount", ""), textX, textY, room, Theme.DIM);
        } else {
            String typed = Draw.ellipsize(font, field.text(), room);
            Draw.text(g, font, typed, textX, textY, invalid ? Theme.RED : Theme.TEXT);
            int hintRoom = room - font.width(typed) - 10;
            if (!invalid && !field.text().equals(Msg.number(amount))) {
                String equals = "= " + Draw.numberFor(font, coins(amount), hintRoom - font.width("= "));
                if (font.width(equals) <= hintRoom) {
                    Draw.rightText(g, font, equals, x + w - 6, textY, Theme.DIM);
                }
            }
            textX += font.width(typed);
        }
        if ((Util.getMillis() / 500) % 2 == 0) {
            g.fill(textX + 1, textY - 1, textX + 2, textY + 9, Theme.ACCENT);
        }
    }

    /** 100, 1k, 10k, Half and All; the one matching the amount is lit, ones you can't afford are greyed. */
    private void drawPresets(PageContext ctx, int x, int y, int w, int h, long purse, long amount) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        long[] values = {100, 1_000, 10_000, purse / 2, purse};
        int gap = 3;
        int chipWidth = (w - gap * (PRESETS.length - 1)) / PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) {
            int cx = x + i * (chipWidth + gap);
            int cw = i == PRESETS.length - 1 ? x + w - cx : chipWidth;
            long value = values[i];
            boolean enabled = value >= 1 && value <= purse;
            boolean chosen = enabled && amount == value;
            String key = "preset" + i;
            boolean hovered = enabled && ctx.hovered(cx, y, cw, h);
            float hv = hover(g, key, hovered);
            int fill = chosen ? Draw.mix(Theme.CARD, Theme.ACCENT, 0.3f)
                    : enabled ? Draw.mix(Theme.TRACK, Theme.ACCENT, 0.05f + 0.13f * hv) : Theme.TRACK;
            int border = chosen ? Theme.ACCENT : enabled ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.2f + 0.5f * hv) : Theme.CARD_BORDER;
            Draw.panel(g, cx, y, cw, h, fill, border);
            float pressed = flash(key);
            if (pressed > 0) {
                Draw.rounded(g, cx + 1, y + 1, cw - 2, h - 2, Draw.withAlpha(0xFFFFFFFF, 0.25f * pressed));
            }
            int color = chosen ? Theme.TEXT : enabled ? Draw.mix(Theme.MUTED, Theme.TEXT, Math.max(hv, 0.4f)) : Theme.DIM;
            Draw.fit(g, font, Component.literal(PRESETS[i]), cx + cw / 2, y + (h - 8) / 2, cw - 4, Draw.Align.CENTER, color);
            if (hovered && i >= 3) {
                ctx.tooltip(List.of(Component.literal(i == 3 ? "Half your purse  " : "Your whole purse  ").withColor(Theme.TEXT)
                        .append(Component.literal(coins(value)).withColor(Theme.ACCENT))), Theme.ACCENT);
            }
            if (enabled) {
                String text = i < 3 ? PRESETS[i] : grouped(value);
                hit(cx, y, cw, h, () -> {
                    flash(key, true);
                    setAmount(text, value);
                });
            }
        }
    }

    /** "You have ⛁ 50,000" counting down as coins leave, with "⛁ 47,500 left" (or what's missing) on the right. */
    private void drawPurseLine(PageContext ctx, int x, int y, int w, long purse, long amount, long now) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        String label = "You have ";
        String preview = "";
        int previewColor = Theme.DIM;
        if (amount > purse) {
            preview = "Need " + coins(amount - purse) + " more";
            previewColor = Theme.RED;
        } else if (amount > 0) {
            preview = coins(purse - amount) + " left";
        }
        // The purse keeps at least half the line; the preview shortens its number, then gives way.
        int previewRoom = w / 2 - 6;
        String shownPreview = preview.isEmpty() ? "" : Draw.numberFor(font, preview, previewRoom);
        if (font.width(shownPreview) > previewRoom) {
            shownPreview = "";
        }
        int purseRoom = w - (shownPreview.isEmpty() ? 0 : font.width(shownPreview) + 8);
        String amountText = Draw.numberFor(font, coins(Math.round(shownPurse)), purseRoom - font.width(label));
        if (font.width(label + amountText) > purseRoom) {
            label = "";
            amountText = Draw.numberFor(font, coins(Math.round(shownPurse)), purseRoom);
        }
        Draw.text(g, font, label, x, y, Theme.MUTED);
        int amountX = x + font.width(label);
        Draw.fitText(g, font, amountText, amountX, y, purseRoom - font.width(label), purseColor(now));
        if (!shownPreview.isEmpty()) {
            Draw.rightText(g, font, shownPreview, x + w, y, previewColor);
        }
        if (!amountText.equals(coins(purse))) {
            Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), x, y - 1, purseRoom, 10,
                    List.of(Component.literal("Purse  ").withColor(Theme.MUTED).append(Component.literal(coins(purse)).withColor(Theme.ACCENT))), Theme.ACCENT);
        }
        drawFloaters(g, font, amountX + Math.min(font.width(amountText), purseRoom - font.width(label)) + 5, y, false, now);
    }

    /** The purse at the right end of a compact header; returns how wide it was drawn. */
    private int drawPurseRight(PageContext ctx, int right, int y, int room, long purse, long now) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        String amountText = Draw.numberFor(font, coins(Math.round(shownPurse)), room);
        String label = font.width("You have " + amountText) <= room ? "You have " : "";
        int amountWidth = Math.min(font.width(amountText), room);
        Draw.fitRight(g, font, amountText, right, y, room, purseColor(now));
        Draw.rightText(g, font, label, right - amountWidth, y, Theme.MUTED);
        int width = amountWidth + font.width(label);
        if (!amountText.equals(coins(purse))) {
            Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), right - width, y - 1, width, 10,
                    List.of(Component.literal("Purse  ").withColor(Theme.MUTED).append(Component.literal(coins(purse)).withColor(Theme.ACCENT))), Theme.ACCENT);
        }
        drawFloaters(g, font, right - width - 5, y, true, now);
        return width;
    }

    private void drawPurseCentered(PageContext ctx, int cx, int y, int room) {
        Font font = ctx.font();
        String amountText = Draw.numberFor(font, coins(Math.round(shownPurse)), room - font.width("You have "));
        Component line = Component.literal("You have ").withColor(Theme.MUTED).append(Component.literal(amountText).withColor(Theme.ACCENT));
        Draw.fit(ctx.graphics(), font, line, cx, y, room, Draw.Align.CENTER, Theme.MUTED);
    }

    /** Gold, flashing green or orange for a moment when the purse changes. */
    private int purseColor(long now) {
        if (purseChangedAt < 0) {
            return Theme.ACCENT;
        }
        float t = Mth.clamp((now - purseChangedAt) / 900f, 0, 1);
        return Draw.mix(purseRose ? Theme.GREEN : MINUS, Theme.ACCENT, Draw.easeOut(t));
    }

    /** "+⛁ 500" / "−⛁ 2,500" drifting up and fading next to the purse; {@code leftOf}: they end at {@code x}. */
    private void drawFloaters(GuiGraphicsExtractor g, Font font, int x, int y, boolean leftOf, long now) {
        floaters.removeIf(floater -> now - floater.at() > FLOATER_MILLIS);
        for (Floater floater : floaters) {
            float t = (now - floater.at()) / (float) FLOATER_MILLIS;
            int alpha = Math.round(255 * (1 - t * t));
            if (alpha <= 8) {
                continue;
            }
            String text = Draw.compactNumbers((floater.amount() > 0 ? "+" : "−") + coins(Math.abs(floater.amount())));
            int color = (alpha << 24) | ((floater.amount() > 0 ? Theme.GREEN : MINUS) & 0xFFFFFF);
            int fy = y - Math.round(Draw.easeOut(t) * 9);
            if (leftOf) {
                Draw.rightText(g, font, text, x, fy, color);
            } else {
                Draw.text(g, font, text, x, fy, color);
            }
        }
    }

    private void drawSend(PageContext ctx, int x, int y, int w, int h, long purse, long amount, boolean online, boolean supported,
                          boolean armed, long now) {
        Font font = ctx.font();
        int room = w - 10;
        Component label;
        int color = Theme.GREEN;
        boolean lit = false;
        boolean clickable = false;
        float pulse = 0;
        if (succeededAt >= 0 && now - succeededAt < SENT_MILLIS && field.isEmpty()) {
            label = Component.literal("✔ Sent ").append(Component.literal(Draw.numberFor(font, coins(succeededAmount), room - font.width("✔ Sent ")))
                    .withColor(Theme.ACCENT));
            lit = true;
        } else if (!supported) {
            label = Component.literal("Use /pay on this server");
        } else if (!online) {
            label = Component.literal(Draw.ellipsize(font, targetName, Math.max(24, room - font.width(" is offline"))) + " is offline");
        } else if (sentAt >= 0) {
            label = Component.literal("Sending…");
        } else if (amount == 0) {
            label = Component.literal("Enter an amount");
        } else if (amount < 0) {
            label = Component.literal("That's not an amount");
        } else if (amount > purse) {
            label = Component.literal("Not enough coins");
        } else {
            lit = true;
            clickable = true;
            if (armed) {
                color = Theme.ACCENT;
                pulse = 0.5f + 0.5f * Mth.sin((now - armedAt) / 140f);
            }
            label = sendLabel(font, armed ? "Confirm" : "Send", amount, targetName, room);
        }
        button(ctx, "send", x, y, w, h, label, color, lit, clickable, pulse, () -> pressSend(false));
    }

    /**
     * "Send ⛁ 2,500 to PhilipSGamer", made to fit: a little too long is left for {@link Draw#fit} to shrink
     * slightly, then the number shortens ("⛁ 1.23M"), then the name is cut, then it's just "Send ⛁ 2,500".
     */
    private static Component sendLabel(Font font, String verb, long amount, String name, int room) {
        String full = coins(amount);
        String compact = Draw.compactNumbers(full);
        for (String number : new String[] {full, compact}) {
            Component label = sendLabel(verb, number, " to " + name);
            if (font.width(label) * LABEL_MIN_SCALE <= room) {
                return label;
            }
        }
        int nameRoom = room - font.width(verb + " " + compact + " to ");
        if (nameRoom >= font.width("Abc…")) {
            return sendLabel(verb, compact, " to " + Draw.ellipsize(font, name, nameRoom));
        }
        return sendLabel(verb, compact, "");
    }

    private static Component sendLabel(String verb, String number, String rest) {
        return Component.literal(verb + " ").append(Component.literal(number).withColor(Theme.ACCENT)).append(Component.literal(rest));
    }

    /** Under the Send button (or instead of the presets when space is short): the server's answer, the confirm prompt, or a hint. */
    private void drawStatus(PageContext ctx, int x, int y, int w, long now, long purse, long amount, boolean supported, boolean armed,
                            boolean showHint) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        if (noticeShowing(now)) {
            long age = now - noticeAt;
            float in = Draw.easeOut(age / 160f);
            float out = Mth.clamp((NOTICE_MILLIS - age) / 400f, 0, 1);
            float alpha = Math.min(in, out);
            int color = noticeGood ? Theme.GREEN : Theme.RED;
            int shift = Math.round((1 - in) * 6);
            Draw.rounded(g, x, y, 3, 8, Draw.withAlpha(color, alpha));
            if (Draw.fitText(g, font, notice, x + 7 + shift, y, w - 7 - shift, Draw.withAlpha(Theme.TEXT, Math.max(alpha, 0.05f)))) {
                Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), x, y - 1, w, 10, List.of(notice), color);
            }
            return;
        }
        if (armed) {
            long percent = Math.round(amount * 100.0 / Math.max(1, purse));
            String why = percent >= 50 ? "That's " + percent + "% of your purse." : "That's a big payment.";
            String text = Draw.firstFitting(font, w - 7, why + " Click again to confirm.", "Click again to confirm.", "Click again");
            Draw.rounded(g, x, y, 3, 8, Theme.ACCENT);
            Draw.fitText(g, font, text, x + 7, y, w - 7, Theme.ACCENT);
            return;
        }
        if (!supported) {
            Draw.fitText(g, font, Draw.firstFitting(font, w, "This server needs an update first. /pay works.", "Server needs an update: use /pay",
                    "Use /pay"), x, y, w, Theme.MUTED);
            return;
        }
        if (showHint) {
            Draw.fitText(g, font, Draw.firstFitting(font, w, "Enter to send  ·  2.5k and 1m work", "Enter to send", ""), x, y, w, Theme.DIM);
        }
    }

    /**
     * A flat SkyRPG button tinted with {@code color} (the shop and bank buttons' look). Unlit ones are grey;
     * only clickable ones take clicks. {@code pulse} (0..1) brightens the border, for the confirm step.
     */
    private void button(PageContext ctx, String key, int x, int y, int w, int h, Component label, int color, boolean lit, boolean clickable,
                        float pulse, Runnable action) {
        GuiGraphicsExtractor g = ctx.graphics();
        boolean hovered = clickable && ctx.hovered(x, y, w, h);
        float hv = hover(g, key, hovered);
        int fill = lit ? Draw.mix(Theme.CARD, color, 0.18f + 0.16f * hv + 0.08f * pulse) : Draw.mix(Theme.CARD, Theme.TRACK, 0.5f);
        int border = lit ? Draw.mix(Theme.CARD_BORDER, color, 0.45f + 0.4f * Math.max(hv, pulse)) : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, h, fill, border);
        if (lit) {
            g.fill(x + 2, y + 1, x + w - 2, y + 2, Draw.withAlpha(Draw.lighten(color, 0.4f), 0.16f + 0.2f * hv));
        }
        float pressed = flash(key);
        if (pressed > 0) {
            Draw.rounded(g, x + 1, y + 1, w - 2, h - 2, Draw.withAlpha(0xFFFFFFFF, 0.28f * pressed));
        }
        boolean cut = Draw.fit(g, ctx.font(), label, x + w / 2, y + (h - 8) / 2, w - 8, Draw.Align.CENTER, lit ? Theme.TEXT : Theme.DIM);
        if (cut) {
            Draw.hoverTooltip(ctx.mouseX(), ctx.mouseY(), x, y, w, h, List.of(label), color);
        }
        if (clickable) {
            hit(x, y, w, h, () -> {
                flash(key, true);
                action.run();
            });
        }
    }

    // ------------------------------------------------------------------ state

    private void trackPurse(LocalPlayer player, long now, boolean resumed) {
        long purse = Purse.get(player);
        if (lastPurse < 0 || resumed) {
            // First frame, or back from another page: no floaters for changes made meanwhile.
            shownPurse = purse;
        } else if (purse != lastPurse) {
            floaters.add(new Floater(purse - lastPurse, now));
            purseChangedAt = now;
            purseRose = purse > lastPurse;
        }
        lastPurse = purse;
        shownPurse = Mth.lerp(Math.min(1, frameSeconds * 7), shownPurse, purse);
        if (Math.abs(shownPurse - purse) < 0.5) {
            shownPurse = purse;
        }
    }

    private boolean noticeShowing(long now) {
        return notice != null && now - noticeAt < NOTICE_MILLIS;
    }

    /** Waiting for the second click on this amount and player. */
    private boolean armed(long now, long amount) {
        return armedAt >= 0 && now - armedAt < CONFIRM_MILLIS && armedAmount == amount && target != null && target.equals(armedTarget);
    }

    private void select(PlayerInfo info) {
        UUID id = info.getProfile().id();
        if (!id.equals(target)) {
            target = id;
            armedAt = -1;
            succeededAt = -1;
        }
        targetName = info.getProfile().name();
        targetSkin = info.getSkin();
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f);
    }

    private void back() {
        target = null;
        armedAt = -1;
        succeededAt = -1;
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.35f);
    }

    /** Puts a preset into the field: "1k" as typed, purse amounts with commas (digits if that's too long for the field). */
    private void setAmount(String text, long value) {
        field.clear();
        for (char c : text.toCharArray()) {
            if (!field.type(c)) {
                field.set(value);
                break;
            }
        }
        amountChanged();
    }

    private void amountChanged() {
        armedAt = -1;
        succeededAt = -1;
    }

    /** Send, or for a big payment ask for a second click first. From the keyboard, unaffordable amounts go to the server, which says why. */
    private void pressSend(boolean keyboard) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (minecraft.player == null || connection == null || target == null || sentAt >= 0
                || connection.getPlayerInfo(target) == null || !ClientPlayNetworking.canSend(EconomyPayloads.Pay.TYPE)) {
            return;
        }
        long amount = field.value();
        if (amount <= 0) {
            return;
        }
        long purse = Purse.get(minecraft.player);
        long now = Util.getMillis();
        if (amount > purse) {
            if (keyboard) {
                send(amount, now);
            }
            return;
        }
        boolean big = amount >= BIG_PAYMENT || amount >= (purse + 1) / 2;
        if (big && !armed(now, amount)) {
            armedAt = now;
            armedAmount = amount;
            armedTarget = target;
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 0.35f);
            return;
        }
        send(amount, now);
    }

    private void send(long amount, long now) {
        ClientPlayNetworking.send(new EconomyPayloads.Pay(target, amount));
        sentAt = now;
        sentAmount = amount;
        armedAt = -1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (listArea[2] > 0 && x >= listArea[0] && x < listArea[0] + listArea[2] && y >= listArea[1] && y < listArea[1] + listArea[3]) {
            listScroll -= amount * ROW;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        if (target == null) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            long amount = field.value();
            if (sentAt < 0 && amount > 0 && amount <= Purse.get(ctx.player())) {
                flash("send", true);
            }
            pressSend(true);
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            field.backspace(event.hasControlDown());
            amountChanged();
            return true;
        }
        // Digits and suffixes are for the amount (their characters arrive in charTyped), not for switching
        // pages. With nothing typed yet, K still closes the menu.
        boolean digit = (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_0)
                || (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD0);
        boolean suffix = key == InputConstants.KEY_K || key == InputConstants.KEY_M || key == InputConstants.KEY_B
                || key == InputConstants.KEY_PERIOD || key == InputConstants.KEY_COMMA;
        return digit || (suffix && !field.isEmpty());
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        if (target == null || !Character.isBmpCodePoint(event.codepoint())) {
            return false;
        }
        char c = Character.toLowerCase((char) event.codepoint());
        // A suffix or comma can't start an amount (".5k" can).
        if (field.isEmpty() && (c == 'k' || c == 'm' || c == 'b' || c == ',')) {
            return false;
        }
        if (field.type(c)) {
            amountChanged();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private void hit(int x, int y, int w, int h, Runnable action) {
        if (w > 0 && h > 0) {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    /** Eases towards 1 while hovered, so highlights fade instead of snapping, and shows the pointing hand. */
    private float hover(GuiGraphicsExtractor g, String key, boolean hovered) {
        float value = Mth.lerp(Math.min(1, frameSeconds * 16), hover.getOrDefault(key, 0f), hovered ? 1 : 0);
        hover.put(key, value);
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
        return value;
    }

    private float flash(String key) {
        if (!key.equals(flashKey)) {
            return 0;
        }
        float t = (Util.getMillis() - flashAt) / 220f;
        return t >= 1 ? 0 : 1 - t;
    }

    private void flash(String key, boolean sound) {
        flashKey = key;
        flashAt = Util.getMillis();
        if (sound) {
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f);
        }
    }

    private static void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    /** "⛁ 1,234" with the SkyRPG coin icon. */
    private static String coins(long amount) {
        return StatTypes.COIN_ICON + " " + Msg.number(amount);
    }

    /** "25,000" if it fits the amount field, else plain digits. */
    private static String grouped(long value) {
        String withCommas = Msg.number(value);
        return withCommas.length() <= 14 ? withCommas : Long.toString(value);
    }
}
