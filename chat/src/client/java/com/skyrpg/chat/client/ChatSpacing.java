package com.skyrpg.chat.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/**
 * A gap between chat messages (not between the wrapped lines of one message), set by the Message spacing setting.
 *
 * <p>Vanilla walks the visible lines once per pass (backgrounds, text, clickable text) and gives each a slot index, 0 at
 * the bottom. Before each pass {@link #prepare} works out how far each slot moves up: the gap times the number of message
 * ends at or below it. While a line is handled its shift sits in {@link #current}, and the chat graphics mixins draw and
 * hit-test it that much higher. Lines pushed past the top of the chat box are skipped.
 */
public final class ChatSpacing {
    /** Where a skipped line goes: far off screen, so it is neither drawn nor clickable. */
    private static final int AWAY = -100_000;
    private static final int MAX_SLOTS = 256;
    private static final int[] SHIFT = new int[MAX_SLOTS];
    private static int slots;

    /** The upward shift (as a negative y offset) of the line being handled right now; 0 outside the chat passes. */
    public static int current;
    /** True while a graphics call is re-issued with the shifted position, so it isn't shifted twice. */
    public static boolean reissuing;

    private ChatSpacing() {
    }

    public static int gap() {
        return ChatSettings.spacing == null ? 0 : (int) Math.round(ChatSettings.spacing.number());
    }

    /** Before a pass: the shift of every visible slot. */
    public static void prepare(List<GuiMessage.Line> lines, int scroll, int linesPerPage) {
        int gap = gap();
        // vanilla's ChatComponent.getLineHeight()
        int lineHeight = (int) (9.0 * (Minecraft.getInstance().options.chatLineSpacing().get() + 1.0));
        slots = Math.min(MAX_SLOTS, Math.max(0, Math.min(lines.size() - scroll, linesPerPage)));
        int shift = 0;
        for (int slot = 0; slot < slots; slot++) {
            // a message ends on this line and the one below belongs to another: open a gap under this line
            if (gap > 0 && slot > 0 && lines.get(scroll + slot).endOfEntry()) {
                shift += gap;
            }
            boolean pastTop = (slot + 1) * lineHeight + shift > linesPerPage * lineHeight;
            SHIFT[slot] = pastTop ? AWAY : -shift;
        }
        current = 0;
    }

    /** Before a line is handed to the pass: remember its shift. */
    public static void enter(int slot) {
        current = slot >= 0 && slot < slots ? SHIFT[slot] : 0;
    }

    public static void leave() {
        current = 0;
    }

    /** Whether a graphics call must be re-issued at {@code y + current}. */
    public static boolean shouldShift() {
        return current != 0 && !reissuing;
    }
}
