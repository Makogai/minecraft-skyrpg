package com.skyrpg.wiki.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

/** The wiki's search box, styled like the other SkyRPG search fields (typing and deleting at the end; Ctrl+V pastes). */
final class WikiField {
    static final int HEIGHT = 16;
    private static final int MAX_LENGTH = 48;

    private String text = "";
    private long lastEdit = Util.getMillis();

    String text() {
        return text;
    }

    boolean isEmpty() {
        return text.isBlank();
    }

    void set(String value) {
        text = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        lastEdit = Util.getMillis();
    }

    static boolean overClear(int x, int w, double mouseX) {
        return mouseX >= x + w - 14;
    }

    void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, boolean focused, boolean hovered, int mouseX) {
        int border = focused ? Theme.ACCENT : hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.35f) : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, HEIGHT, Theme.INPUT, border);
        int iconColor = focused || !text.isEmpty() ? Theme.ACCENT : Theme.DIM;
        magnifier(g, x + 5, y + 4, iconColor);
        int textX = x + 16;
        int room = w - 16 - (text.isEmpty() ? 5 : 16);
        int textY = y + (HEIGHT - 8) / 2;
        boolean blink = focused && ((Util.getMillis() - lastEdit) / 530) % 2 == 0;
        if (text.isEmpty()) {
            Draw.fitText(g, font, "Search the wiki", textX, textY, room, Theme.DIM);
            if (blink) {
                g.fill(textX, textY - 1, textX + 1, textY + 9, Theme.ACCENT);
            }
        } else {
            int start = 0;
            while (start < text.length() && font.width(text.substring(start)) > room - 2) {
                start++;
            }
            String shown = text.substring(start);
            Draw.text(g, font, shown, textX, textY, Theme.TEXT);
            if (blink) {
                int caretX = textX + font.width(shown);
                g.fill(caretX, textY - 1, caretX + 1, textY + 9, Theme.ACCENT);
            }
            boolean overClear = hovered && overClear(x, w, mouseX);
            Draw.text(g, font, "×", x + w - 11, textY, overClear ? Theme.TEXT : Theme.DIM);
        }
    }

    private static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x + 1, y + 4, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 1, y + 4, color);
        g.fill(x + 4, y + 1, x + 5, y + 4, color);
        g.fill(x + 5, y + 5, x + 6, y + 6, color);
        g.fill(x + 6, y + 6, x + 7, y + 7, color);
    }

    /** Editing keys; returns true if the text changed. */
    boolean keyPressed(KeyEvent event) {
        String before = text;
        switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    if (event.hasControlDown()) {
                        int i = text.length();
                        while (i > 0 && text.charAt(i - 1) == ' ') {
                            i--;
                        }
                        while (i > 0 && text.charAt(i - 1) != ' ') {
                            i--;
                        }
                        text = text.substring(0, i);
                    } else {
                        text = text.substring(0, text.length() - 1);
                    }
                }
            }
            case InputConstants.KEY_DELETE -> text = "";
            case InputConstants.KEY_V -> {
                if (event.hasControlDown()) {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]+", " "));
                }
            }
            case InputConstants.KEY_A -> {
                if (event.hasControlDown()) {
                    text = "";
                }
            }
            default -> { }
        }
        lastEdit = Util.getMillis();
        return !text.equals(before);
    }

    boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        insert(event.codepointAsString());
        return true;
    }

    private void insert(String part) {
        text = text + part.substring(0, Math.min(part.length(), MAX_LENGTH - text.length()));
        lastEdit = Util.getMillis();
    }
}
