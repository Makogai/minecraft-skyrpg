package com.skyrpg.progress.client.dialogue;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.progress.dialogue.DialoguePayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The conversation screen: a panel along the bottom with the speaker's face and name, text that types itself out (click or
 * Space to show it all, again to go on) and, once the text is complete, the choices as buttons above the panel (click, or press
 * its number). Esc closes. Like the K menu it is laid out in its own units and scaled to the window, so it looks the same at any
 * GUI scale. The server sends one page at a time; this only shows it and reports the choice.
 */
public final class DialogueScreen extends Screen {
    private static final int PANEL_H = 104;
    private static final int MAX_PANEL_W = 540;
    private static final int PORTRAIT = 64;
    private static final int PAD = 12;
    private static final int BUTTON_H = 17;
    private static final int LINE = 11;
    private static final float CHARS_PER_SECOND = 70f;

    private DialoguePayloads.Show page;
    private List<FormattedText> lines = List.of();
    private int totalChars;
    private long shownAt;
    private long openedAt = Util.getMillis();
    private float shown;
    private long lastFrame = Util.getMillis();
    private int blips;
    private boolean waiting;
    private long waitingSince;
    private boolean closedByServer;
    private float scale = 1f;
    private int unitsW;
    private int unitsH;
    private final List<int[]> buttons = new ArrayList<>();

    public DialogueScreen(DialoguePayloads.Show first) {
        super(Component.literal("Dialogue"));
        update(first);
    }

    /** The server moved on to another page. */
    public void update(DialoguePayloads.Show next) {
        this.page = next;
        this.shownAt = Util.getMillis();
        this.shown = 0;
        this.blips = 0;
        this.waiting = false;
        this.lines = List.of();
        this.totalChars = 0;
        if (font != null && unitsW > 0) {
            wrap();
        }
    }

    /** The server ended the conversation: close without telling it. */
    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        int gui = Math.max(1, window.getGuiScale());
        int k = Math.max(2, Math.round(window.getHeight() / 540f));
        k = Math.max(1, Math.min(k, Math.max(1, window.getWidth() / 380)));
        scale = k / (float) gui;
        unitsW = window.getWidth() / k;
        unitsH = window.getHeight() / k;
        wrap();
    }

    private int panelW() {
        return Math.min(unitsW - 24, MAX_PANEL_W);
    }

    private int panelX() {
        return (unitsW - panelW()) / 2;
    }

    private int panelY() {
        int rise = Math.round((1 - Draw.easeOut((Util.getMillis() - openedAt) / 200f)) * 14);
        return unitsH - 14 - PANEL_H + rise;
    }

    private int textX() {
        return panelX() + PAD + PORTRAIT + PAD;
    }

    private int textW() {
        return panelW() - (textX() - panelX()) - PAD;
    }

    private void wrap() {
        lines = font.getSplitter().splitLines(markup(page.text()), textW(), Style.EMPTY);
        totalChars = 0;
        for (FormattedText line : lines) {
            totalChars += line.getString().length();
        }
    }

    /** {@code &6} style colour codes ({@code &r} resets, {@code &l} bold, {@code &o} italic) turned into a styled component. */
    static Component markup(String text) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            ChatFormatting code = c == '&' && i + 1 < text.length() ? ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1))) : null;
            if (code != null) {
                if (!run.isEmpty()) {
                    out.append(Component.literal(run.toString()).setStyle(style));
                    run.setLength(0);
                }
                style = code == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(code);
                i++;
            } else {
                run.append(c);
            }
        }
        if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).setStyle(style));
        }
        return out;
    }

    /** The first {@code limit} characters of a line, keeping its styles. */
    private static Component clip(FormattedText line, int limit) {
        MutableComponent out = Component.empty();
        int[] left = {limit};
        line.visit((style, string) -> {
            if (left[0] <= 0) {
                return java.util.Optional.of(Boolean.TRUE);
            }
            String part = string.length() <= left[0] ? string : string.substring(0, left[0]);
            left[0] -= part.length();
            out.append(Component.literal(part).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private boolean typed() {
        return shown >= totalChars;
    }

    private static String emote(String mood) {
        return switch (mood.toLowerCase(Locale.ROOT)) {
            case "happy", "smile" -> "smiles";
            case "laugh" -> "laughs";
            case "thinking", "think" -> "thinks";
            case "serious" -> "looks serious";
            case "excited" -> "is excited";
            case "worried" -> "looks worried";
            case "angry" -> "scowls";
            case "proud" -> "beams";
            case "sad" -> "sighs";
            case "surprised" -> "is surprised";
            case "wink" -> "winks";
            case "whisper" -> "lowers their voice";
            default -> mood.isEmpty() ? "" : mood;
        };
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // The world stays visible; only the bottom is darkened a little so the panel reads well.
        int fade = Math.round(Draw.easeOut((Util.getMillis() - openedAt) / 250f) * 0x58);
        g.fill(0, height * 2 / 3, width, height, (fade << 24) | 0x070910);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        if (waiting && now - waitingSince > 1500) {
            waiting = false;
        }
        float before = shown;
        if (!typed()) {
            shown = Math.min(totalChars, shown + dt * CHARS_PER_SECOND);
            if ((int) shown / 4 > blips) {
                blips = (int) shown / 4;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f + (blips % 3) * 0.1f, 0.06f));
            }
        }
        if (before < totalChars && typed()) {
            shownAt = now;
        }
        int mx = Math.round(mouseX / scale);
        int my = Math.round(mouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        buttons.clear();
        int px = panelX();
        int py = panelY();
        int pw = panelW();
        Draw.shadow(g, px, py, pw, PANEL_H, 6, 0.5f);
        Draw.panel(g, px, py, pw, PANEL_H, Theme.WINDOW, Theme.BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + 3, Draw.withAlpha(page.color(), 0.9f));
        drawPortrait(g, px + PAD, py + PAD + 4, now);
        drawText(g, py);
        drawFooter(g, px, py, pw, now);
        if (typed() && !page.choiceLabels().isEmpty()) {
            drawChoices(g, px, py, pw, mx, my);
        }
        g.pose().popMatrix();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawPortrait(GuiGraphicsExtractor g, int x, int y, long now) {
        // The face bobs a pixel while the speaker is talking.
        int bob = !typed() && (now / 160) % 2 == 0 ? -1 : 0;
        g.fill(x - 2, y - 2, x + PORTRAIT + 2, y + PORTRAIT + 2, page.color());
        g.fill(x - 1, y - 1, x + PORTRAIT + 1, y + PORTRAIT + 1, Theme.INPUT);
        if (page.skin().isEmpty()) {
            Draw.item(g, new ItemStack(Items.WRITABLE_BOOK), x + 8, y + 8 + bob, 3f);
        } else {
            PlayerFaceExtractor.extractRenderState(g, Identifier.parse(page.skin()), x, y + bob, PORTRAIT, true, false, -1);
        }
    }

    private void drawText(GuiGraphicsExtractor g, int py) {
        int x = textX();
        Component name = Component.literal(page.name()).withColor(page.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
        Draw.text(g, font, name, x, py + PAD - 2, Theme.TEXT);
        String emote = emote(page.mood());
        if (!emote.isEmpty()) {
            Draw.text(g, font, Component.literal("*" + emote + "*").withStyle(ChatFormatting.ITALIC), x + font.width(name) + 8, py + PAD - 2, Theme.DIM);
        }
        g.fill(x, py + PAD + 9, x + textW(), py + PAD + 10, Theme.BORDER);
        int left = (int) shown;
        int y = py + PAD + 16;
        for (FormattedText line : lines) {
            if (left <= 0) {
                break;
            }
            int length = line.getString().length();
            g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(left >= length ? line : clip(line, left)), x, y, Theme.TEXT, false);
            left -= length;
            y += LINE;
        }
    }

    private void drawFooter(GuiGraphicsExtractor g, int px, int py, int pw, long now) {
        if (page.pages() > 1) {
            String progress = (page.page() + 1) + "/" + page.pages();
            Draw.rightText(g, font, progress, px + pw - PAD, py + PANEL_H - PAD - 2, Theme.DIM);
        }
        if (typed() && page.choiceLabels().isEmpty()) {
            boolean last = page.page() + 1 >= page.pages();
            if ((now - shownAt) / 450 % 2 == 0) {
                Draw.rightText(g, font, page.readOnly() && last ? "Click to close  ▼" : "Click to continue  ▼", px + pw - PAD - (page.pages() > 1 ? 30 : 0),
                        py + PANEL_H - PAD - 2, Theme.MUTED);
            }
        } else if (!typed()) {
            Draw.text(g, font, "Click to skip", px + pw - PAD - font.width("Click to skip") - (page.pages() > 1 ? 30 : 0), py + PANEL_H - PAD - 2, Theme.DIM);
        }
    }

    private void drawChoices(GuiGraphicsExtractor g, int px, int py, int pw, int mx, int my) {
        int n = page.choiceLabels().size();
        int width = 120;
        for (String label : page.choiceLabels()) {
            width = Math.max(width, font.width(label) + 40);
        }
        width = Math.min(width, pw - 24);
        int x = px + pw - width;
        int y = py - 6 - n * (BUTTON_H + 3) + 3;
        for (int i = 0; i < n; i++) {
            String action = page.choiceActions().get(i);
            int accent = switch (action) {
                case "accept" -> Theme.GREEN;
                case "turn_in", "shop" -> Theme.ACCENT;
                case "close" -> Theme.MUTED;
                default -> Theme.BLUE;
            };
            boolean hover = !waiting && mx >= x && mx < x + width && my >= y && my < y + BUTTON_H;
            Draw.panel(g, x, y, width, BUTTON_H, hover ? Theme.CARD_HOVER : Theme.WINDOW, hover ? accent : Theme.BORDER);
            g.fill(x + 1, y + 1, x + 3, y + BUTTON_H - 1, accent);
            Draw.text(g, font, String.valueOf(i + 1), x + 9, y + 5, hover ? accent : Theme.DIM);
            Draw.fitText(g, font, markup(page.choiceLabels().get(i)), x + 22, y + 5, width - 28, hover ? Theme.TEXT : Theme.MUTED);
            if (hover) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            buttons.add(new int[]{x, y, width, BUTTON_H});
            y += BUTTON_H + 3;
        }
    }

    // ------------------------------------------------------------------ input

    private void send(int choice) {
        if (waiting) {
            return;
        }
        waiting = true;
        waitingSince = Util.getMillis();
        if (ClientPlayNetworking.canSend(DialoguePayloads.Choose.TYPE)) {
            ClientPlayNetworking.send(new DialoguePayloads.Choose(page.serial(), choice));
        }
    }

    /** Skips the typing, or goes on if the text is complete and there is nothing to choose. */
    private void advance() {
        if (!typed()) {
            shown = totalChars;
            shownAt = Util.getMillis();
        } else if (page.choiceLabels().isEmpty()) {
            send(DialoguePayloads.ADVANCE);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x() / scale;
        double my = event.y() / scale;
        if (typed()) {
            for (int i = 0; i < buttons.size(); i++) {
                int[] b = buttons.get(i);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    click();
                    send(i);
                    return true;
                }
            }
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            advance();
            return true;
        }
        int digit = key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 ? key - InputConstants.KEY_1 : -1;
        if (digit >= 0) {
            if (!typed()) {
                advance();
            } else if (digit < page.choiceLabels().size()) {
                click();
                send(digit);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.35f));
    }

    @Override
    public void removed() {
        super.removed();
        if (!closedByServer && ClientPlayNetworking.canSend(DialoguePayloads.Choose.TYPE)) {
            ClientPlayNetworking.send(new DialoguePayloads.Choose(page.serial(), DialoguePayloads.CANCEL));
        }
        closedByServer = true;
    }
}
