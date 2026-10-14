package com.skyrpg.core.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

/**
 * A one-line text box in the SkyRPG style, for searches: a magnifier, the text with a blinking caret (or a
 * placeholder), and an × to clear it. Left/Right/Home/End move the caret; Backspace/Delete erase (Ctrl for a
 * whole word); Ctrl+V pastes. The owner says when it's focused and passes it keys and characters.
 */
final class SearchField {
    static final int HEIGHT = 16;
    private static final int MAX_LENGTH = 48;

    private final String placeholder;
    private String text = "";
    private int caret;
    private long lastEdit = Util.getMillis();

    SearchField(String placeholder) {
        this.placeholder = placeholder;
    }

    String text() {
        return text;
    }

    boolean isEmpty() {
        return text.isBlank();
    }

    void set(String value) {
        text = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        caret = text.length();
        lastEdit = Util.getMillis();
    }

    void clear() {
        set("");
    }

    /** Where the × that clears the text is, relative to the field's right edge. */
    static boolean overClear(int fieldX, int fieldWidth, double mouseX) {
        return mouseX >= fieldX + fieldWidth - 14;
    }

    void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, boolean focused, boolean hovered, int mouseX) {
        int border = focused ? Theme.ACCENT : hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.35f) : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, HEIGHT, Theme.INPUT, border);
        int iconColor = focused || !text.isEmpty() ? Theme.ACCENT : Theme.DIM;
        magnifier(g, x + 5, y + 4, iconColor);
        int textX = x + 16;
        int room = w - 16 - (text.isEmpty() ? 5 : 16);
        int textY = y + (HEIGHT - 8) / 2;
        if (text.isEmpty()) {
            Draw.fitText(g, font, placeholder, textX, textY, room, Theme.DIM);
        } else {
            // Keep the caret in view: show the end of the text if it's too long.
            String before = text.substring(0, caret);
            int start = 0;
            while (start < caret && font.width(text.substring(start, caret)) > room - 2) {
                start++;
            }
            String shown = font.plainSubstrByWidth(text.substring(start), room);
            Draw.text(g, font, shown, textX, textY, Theme.TEXT);
            if (focused && ((Util.getMillis() - lastEdit) / 530) % 2 == 0) {
                int caretX = textX + font.width(before.substring(Math.min(start, before.length())));
                g.fill(caretX, textY - 1, caretX + 1, textY + 9, Theme.ACCENT);
            }
            boolean overClear = hovered && overClear(x, w, mouseX);
            Draw.text(g, font, "×", x + w - 11, textY, overClear ? Theme.TEXT : Theme.DIM);
        }
        if (focused && text.isEmpty() && ((Util.getMillis() - lastEdit) / 530) % 2 == 0) {
            g.fill(textX, textY - 1, textX + 1, textY + 9, Theme.ACCENT);
        }
    }

    /** A little magnifying glass, 7x7, drawn with fills so it needs no texture. */
    private static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x + 1, y + 4, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 1, y + 4, color);
        g.fill(x + 4, y + 1, x + 5, y + 4, color);
        g.fill(x + 4, y + 4, x + 5, y + 5, color);
        g.fill(x + 5, y + 5, x + 6, y + 6, color);
        g.fill(x + 6, y + 6, x + 7, y + 7, color);
    }

    /** Editing keys. Returns true if the text changed. */
    boolean keyPressed(KeyEvent event) {
        boolean word = event.hasControlDown();
        String before = text;
        switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> {
                if (caret > 0) {
                    int from = word ? wordStart(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                }
            }
            case InputConstants.KEY_DELETE -> {
                if (caret < text.length()) {
                    int to = word ? wordEnd(caret) : caret + 1;
                    text = text.substring(0, caret) + text.substring(to);
                }
            }
            case InputConstants.KEY_LEFT -> caret = word ? wordStart(caret) : Math.max(0, caret - 1);
            case InputConstants.KEY_RIGHT -> caret = word ? wordEnd(caret) : Math.min(text.length(), caret + 1);
            case InputConstants.KEY_HOME -> caret = 0;
            case InputConstants.KEY_END -> caret = text.length();
            case InputConstants.KEY_V -> {
                if (event.hasControlDown()) {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]+", " "));
                }
            }
            case InputConstants.KEY_A -> {
                if (event.hasControlDown()) {
                    // Select-all isn't drawn, so Ctrl+A clears instead: the usual next step after selecting all.
                    clear();
                }
            }
            default -> { }
        }
        lastEdit = Util.getMillis();
        return !text.equals(before);
    }

    /** A typed character. Returns true if it was taken. */
    boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        insert(event.codepointAsString());
        return true;
    }

    private void insert(String part) {
        String added = part.substring(0, Math.min(part.length(), MAX_LENGTH - text.length()));
        text = text.substring(0, caret) + added + text.substring(caret);
        caret += added.length();
        lastEdit = Util.getMillis();
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        return i;
    }
}
