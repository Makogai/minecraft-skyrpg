package com.skyrpg.chat.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tabs on the open chat (All / Chat / Whispers / SkyRPG). A tab is a view of the one chat history, not a separate
 * channel: it filters what's drawn while chat is open, and closing chat goes back to All, so nothing is ever missed
 * with chat closed. A dot marks tabs with messages that arrived while another tab was showing.
 */
public final class ChatTabs {
    public static final int HEADER_HEIGHT = 12;
    private static final int TAB_PADDING = 5;

    private static ChatTab selected = ChatTab.ALL;
    private static final Set<ChatTab> UNREAD = EnumSet.noneOf(ChatTab.class);

    private ChatTabs() {
    }

    public static boolean enabled() {
        return ChatSettings.tabs == null || ChatSettings.tabs.get();
    }

    public static ChatTab selected() {
        return enabled() ? selected : ChatTab.ALL;
    }

    public static boolean shows(ChatKind kind) {
        return selected().shows(kind);
    }

    static void markUnread(ChatKind kind) {
        for (ChatTab tab : ChatTab.values()) {
            if (tab != selected && tab.shows(kind)) {
                UNREAD.add(tab);
            }
        }
    }

    public static void select(ChatTab tab) {
        UNREAD.remove(tab);
        if (selected != tab) {
            selected = tab;
            ChatSettings.refresh();
        }
    }

    /** Back to All (chat closed, or tabs turned off). */
    public static void reset() {
        UNREAD.clear();
        if (selected != ChatTab.ALL) {
            selected = ChatTab.ALL;
            ChatSettings.refresh();
        }
    }

    // ------------------------------------------------------------------ drawing and clicks

    /** Draws the tab strip with its top-left corner at (x, y); {@code alpha} is the chat's text opacity. */
    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int right, int mouseX, int mouseY, float alpha) {
        int left = x;
        for (ChatTab tab : ChatTab.values()) {
            int width = font.width(tab.label()) + TAB_PADDING * 2;
            if (left + width > right) {
                break;
            }
            boolean active = tab == selected;
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + HEADER_HEIGHT;
            if (hovered && !active) {
                g.fill(left, y, left + width, y + HEADER_HEIGHT, Draw.withAlpha(Theme.HOVER, alpha));
            }
            int color = active ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.MUTED;
            g.text(font, tab.label(), left + TAB_PADDING, y + 2, Draw.withAlpha(color, alpha), false);
            if (active) {
                g.fill(left + 2, y + HEADER_HEIGHT - 1, left + width - 2, y + HEADER_HEIGHT, Draw.withAlpha(Theme.ACCENT, alpha));
            }
            if (UNREAD.contains(tab) && !active) {
                g.fill(left + width - 3, y + 2, left + width - 1, y + 4, Draw.withAlpha(Theme.ACCENT, alpha));
            }
            left += width;
        }
    }

    /** A click at (mouseX, mouseY) on the tab strip drawn at (x, y): selects that tab. */
    static boolean click(Font font, int x, int y, int right, double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + HEADER_HEIGHT) {
            return false;
        }
        int left = x;
        for (ChatTab tab : ChatTab.values()) {
            int width = font.width(tab.label()) + TAB_PADDING * 2;
            if (left + width > right) {
                break;
            }
            if (mouseX >= left && mouseX < left + width) {
                select(tab);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            left += width;
        }
        return false;
    }
}
