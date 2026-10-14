package com.skyrpg.chat.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * The SkyRPG chat box. With chat closed, each line's background is the menu's dark navy instead of black. With chat
 * open, the history sits in a framed panel with the tab strip on top, and the input line gets the same look. Every
 * background keeps vanilla's Text Background Opacity, and text keeps Chat Text Opacity.
 *
 * <p>Timestamps and "(x3)" are added when a message is laid out (GuiMessage.splitLines), so switching them on or
 * off re-lays out the whole history.
 */
public final class ChatLook {
    private static final int NAVY = 0x121620;
    private static final int NAVY_DARK = 0x0C0F16;
    private static final int BORDER = 0x252C39;
    private static final int TIME_COLOR = 0x6B7385;
    private static final int REPEAT_COLOR = 0x8D96A8;
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    /** True while the open chat is being drawn (its line backgrounds are replaced by the panel). */
    private static boolean focusedPass;

    /** Where the chat sits on screen, in GUI pixels. */
    record Layout(int left, int right, int top, int bottom) {
        int headerTop() {
            return Math.max(2, top - ChatTabs.HEADER_HEIGHT);
        }
    }

    private ChatLook() {
    }

    public static boolean themed() {
        return ChatSettings.theme == null || ChatSettings.theme.get();
    }

    /** The chat history box as vanilla lays it out (ChatComponent's own maths, in GUI pixels). */
    static Layout layout(ChatComponent chat, int guiHeight) {
        Options options = Minecraft.getInstance().options;
        double scale = options.chatScale().get();
        int width = ChatComponent.getWidth(options.chatWidth().get());
        int maxWidth = Mth.ceil(width / scale);
        int lineHeight = (int) (9.0 * (options.chatLineSpacing().get() + 1.0));
        int chatBottom = Mth.floor((guiHeight - 40) / scale);
        int lines = chat.getLinesPerPage();
        int right = Mth.ceil((maxWidth + 12) * scale);
        int bottom = (int) Math.round(chatBottom * scale);
        int top = (int) Math.round((chatBottom - lines * lineHeight) * scale);
        return new Layout(0, right, top, bottom);
    }

    // ------------------------------------------------------------------ ChatComponent.extractRenderState

    /** Before vanilla draws the chat: the open chat's panel and tabs, behind the text. */
    public static void beforeChat(GuiGraphicsExtractor g, ChatComponent chat, ChatComponent.DisplayMode mode, int mouseX, int mouseY) {
        ChatImageRender.beginFrame(mode.foreground);
        boolean themed = themed();
        boolean tabs = ChatTabs.enabled();
        focusedPass = mode.foreground && themed;
        if (!mode.foreground || (!themed && !tabs)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        float background = options.textBackgroundOpacity().get().floatValue();
        float textAlpha = options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
        Layout layout = layout(chat, g.guiHeight());
        int top = tabs ? layout.headerTop() : layout.top() - 2;
        int bottom = layout.bottom() + 2;

        if (themed) {
            // Body, then the header strip a shade darker, then a 1px frame (drawn as edges, so the translucent body
            // doesn't show a border colour underneath), then the gold top edge.
            g.fill(layout.left(), top, layout.right(), bottom, argb(background, NAVY));
            if (tabs) {
                g.fill(layout.left(), top, layout.right(), layout.top(), argb(Math.min(1f, background + 0.15f), NAVY_DARK));
                g.fill(layout.left(), layout.top() - 1, layout.right(), layout.top(), argb(Math.min(1f, background + 0.3f), BORDER));
            }
            frame(g, layout.left(), top, layout.right() - layout.left(), bottom - top, argb(Math.min(1f, background + 0.35f), BORDER));
            g.fill(layout.left() + 1, top, layout.right() - 1, top + 1, Draw.withAlpha(Theme.ACCENT, Math.min(1f, textAlpha)));
        } else {
            // Plain vanilla look: just a black strip behind the tabs.
            g.fill(layout.left(), top, layout.right(), layout.top(), argb(background, 0x000000));
        }
        if (tabs) {
            ChatTabs.draw(g, minecraft.font, layout.left() + 3, top, layout.right() - 3, mouseX, mouseY, textAlpha);
        }
    }

    public static void afterChat() {
        focusedPass = false;
    }

    /** A click on the open chat's tab strip; true if it hit a tab. */
    public static boolean clickTabs(double mouseX, double mouseY) {
        if (!ChatTabs.enabled()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Layout layout = layout(minecraft.gui.hud.getChat(), minecraft.getWindow().getGuiScaledHeight());
        return ChatTabs.click(minecraft.font, layout.left() + 3, layout.headerTop(), layout.right() - 3, mouseX, mouseY);
    }

    /**
     * Vanilla's chat background fills: pure black at some alpha. Closed chat gets navy at the same alpha; open chat
     * drops them (the panel is the background). Other fills (the scrollbar) pass through in theme colours.
     */
    public static int lineFill(int color) {
        if (!themed()) {
            return color;
        }
        int rgb = color & 0xFFFFFF;
        int alpha = color & 0xFF000000;
        return switch (rgb) {
            case 0x000000 -> focusedPass ? 0 : alpha | NAVY;
            case 0x3333AA -> alpha | (Theme.ACCENT & 0xFFFFFF);
            case 0xCCCCCC -> alpha | 0xFFE7A8;
            case 0xCC3333 -> alpha | (Theme.RED & 0xFFFFFF);
            default -> color;
        };
    }

    // ------------------------------------------------------------------ ChatScreen's input line

    /** The input line's background: vanilla's fill, or a framed SkyRPG field with a gold edge. */
    public static void inputBox(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (!themed()) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        float alpha = Math.max(0.55f, ((color >>> 24) & 0xFF) / 255f);
        g.fill(x0, y0, x1, y1, argb(alpha, NAVY));
        frame(g, x0, y0, x1 - x0, y1 - y0, argb(Math.min(1f, alpha + 0.3f), BORDER));
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, Theme.ACCENT);
    }

    // ------------------------------------------------------------------ GuiMessage.splitLines

    /** The text a message is laid out from: "12:34 " before it and " (x3)" after it, when those apply. */
    public static FormattedText decorate(GuiMessage message, FormattedText content) {
        if (content instanceof Component picture) {
            content = ChatImageRender.decorate(picture);
        }
        ChatMeta.Meta meta = ChatMeta.get(message);
        if (meta == null || !(content instanceof Component component)) {
            return content;
        }
        boolean time = ChatSettings.timestampsOn();
        boolean repeat = meta.count() > 1;
        if (!time && !repeat) {
            return content;
        }
        MutableComponent out = Component.empty();
        if (time) {
            DateTimeFormatter format = ChatSettings.timestamps.is(ChatSettings.TIME_SECONDS) ? SECONDS : MINUTES;
            String when = LocalTime.ofInstant(Instant.ofEpochMilli(meta.time()), ZoneId.systemDefault()).format(format);
            out.append(Component.literal(when + " ").withColor(TIME_COLOR));
        }
        out.append(component);
        if (repeat) {
            out.append(Component.literal(" (x" + meta.count() + ")").withColor(REPEAT_COLOR));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static int argb(float alpha, int rgb) {
        return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A 1px outline, edges only. */
    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
