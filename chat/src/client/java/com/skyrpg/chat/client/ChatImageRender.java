package com.skyrpg.chat.client;

import com.skyrpg.chat.client.ImageCache.Entry;
import com.skyrpg.chat.client.ImageCache.State;
import com.skyrpg.chat.client.ImageRefs.Kind;
import com.skyrpg.chat.client.ImageRefs.Ref;
import com.skyrpg.chat.client.mixin.ChatComponentAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.joml.Vector2f;

/**
 * Pictures in the chat box. A message that carries pictures gets blank "placeholder" lines after its text, as many as the
 * picture is tall ({@link ImageLine}); the chat mixins draw one horizontal slice of the picture on each of them. Because
 * they are real chat lines, the picture scrolls, fades and takes its opacity from the chat exactly like text, and the
 * stripe runs beside it.
 */
public final class ChatImageRender {
    private static final int GREY = 0x8D96A8;

    /** One slice of a picture as drawn in the open chat, in GUI coordinates (for clicks). */
    private record Hit(float x0, float y0, float x1, float y1, Ref ref) {
    }

    private static final List<Hit> HITS = new ArrayList<>();
    private static boolean recording;

    private ChatImageRender() {
    }

    /** One chat line: slice {@code index} of a picture drawn {@code width} x {@code height} px. */
    public record ImageLine(Ref ref, int index, int width, int height) implements FormattedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return true;
        }
    }

    public enum Status { SHOW, LOADING, FAILED, ASK }

    public record Item(Ref ref, Status status, Entry entry) {
    }

    // ------------------------------------------------------------------ what a message shows

    /** The pictures of a message as the settings allow them. Requesting also starts their loads. */
    public static List<Item> items(String text) {
        List<Item> items = new ArrayList<>(2);
        if (!ChatSettings.imagesOn()) {
            return items;
        }
        for (Ref ref : ImageRefs.scan(text)) {
            switch (ref.kind()) {
                case STICKER -> {
                    if (ChatSettings.stickersOn()) {
                        items.add(new Item(ref, Status.SHOW, ImageCache.request(ref)));
                    }
                }
                case NET -> items.add(item(ref, ImageCache.request(ref)));
                case URL -> {
                    String host = ref.host();
                    if (ChatSettings.linkMode.is(ChatSettings.LINKS_OFF)) {
                        break;
                    }
                    if (ImageRefs.whitelisted(host) || ImageCache.approved(ref.value())) {
                        items.add(item(ref, ImageCache.request(ref)));
                    } else if (ChatSettings.linkMode.is(ChatSettings.LINKS_ANY)) {
                        items.add(new Item(ref, Status.ASK, null));
                    }
                }
            }
        }
        return items;
    }

    private static Item item(Ref ref, Entry entry) {
        return new Item(ref, switch (entry.state()) {
            case READY -> Status.SHOW;
            case LOADING -> Status.LOADING;
            case FAILED -> Status.FAILED;
        }, entry);
    }

    /** The text a message is laid out with: picture tokens taken out, and a note for pictures that aren't showing. */
    public static Component decorate(Component content) {
        String text = content.getString();
        if (text.length() < 6) {
            return content;
        }
        Component base = ImageRefs.stripTokens(content, ChatSettings.imagesOn() && ChatSettings.stickersOn());
        List<Item> items = items(text);
        MutableComponent notes = null;
        for (Item item : items) {
            Component note = switch (item.status()) {
                case SHOW -> null;
                case LOADING -> Component.literal(" [loading image...]").withColor(GREY);
                case FAILED -> Component.literal(" [image unavailable]").withStyle(style -> style.withColor(GREY)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(item.entry().error()))));
                case ASK -> Component.literal(" [image from " + item.ref().host() + " — click to load]").withStyle(style -> style
                        .withColor(0xFFC94D).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/skyrpgimg load " + item.ref().value()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Loads the picture from " + item.ref().host()
                                + ". Only click for sites you trust.").withColor(GREY))));
            };
            if (note != null) {
                if (notes == null) {
                    notes = Component.empty();
                }
                notes.append(note);
            }
        }
        if (notes == null) {
            return base;
        }
        return Component.empty().append(base).append(notes);
    }

    // ------------------------------------------------------------------ layout

    static int lineHeight() {
        return (int) (9.0 * (Minecraft.getInstance().options.chatLineSpacing().get() + 1.0));
    }

    /** The picture's size on screen: natural size, shrunk to fit the chat width and the "max image height" setting. */
    static int[] fit(Entry entry, int availableWidth) {
        int lineHeight = lineHeight();
        int maxHeight = Math.max(1, (int) Math.round(ChatSettings.imageHeight.number())) * lineHeight;
        int width = Math.max(1, entry.width());
        int height = Math.max(1, entry.height());
        double scale = Math.min(1.0, Math.min((double) Math.max(8, availableWidth) / width, (double) maxHeight / height));
        int dw = Math.max(1, (int) Math.round(width * scale));
        int dh = Math.max(1, (int) Math.round(height * scale));
        return new int[] {dw, dh};
    }

    /** The placeholder lines for a message's pictures (after its text). */
    public static List<FormattedCharSequence> linesFor(GuiMessage message, int chatWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        int lineHeight = lineHeight();
        for (Item item : items(message.content().getString())) {
            if (item.status() != Status.SHOW || item.entry() == null) {
                continue;
            }
            int[] size = fit(item.entry(), chatWidth - 6);
            int count = (size[1] + lineHeight - 1) / lineHeight;
            for (int i = 0; i < count; i++) {
                lines.add(new ImageLine(item.ref(), i, size[0], size[1]));
            }
        }
        return lines;
    }

    /** Lays every message out again (a picture arrived or a setting changed), keeping where the player had scrolled to. */
    static void relayoutChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || minecraft.gui.hud == null) {
            return;
        }
        ChatComponent chat = minecraft.gui.hud.getChat();
        ChatComponentAccess access = (ChatComponentAccess) chat;
        int scroll = access.skyrpg_chat$scroll();
        chat.rescaleChat();
        if (scroll > 0) {
            chat.scrollChat(Math.min(scroll, 10_000));
        }
    }

    // ------------------------------------------------------------------ drawing (called from the chat graphics mixins)

    static void beginFrame(boolean foreground) {
        recording = foreground;
        if (foreground) {
            HITS.clear();
        }
    }

    /**
     * Draws one slice. {@code textY} is where vanilla would put the text of the line; the line's box is
     * {@code lineHeight} tall and ends 8px (at default spacing) below it.
     */
    public static void draw(GuiGraphicsExtractor g, ImageLine line, int textY, float opacity) {
        Entry entry = ImageCache.peek(line.ref().key());
        if (entry == null || entry.state != State.READY || entry.texture == null || entry.width() <= 0) {
            if (entry != null && entry.state == State.READY && entry.texture == null) {
                ImageCache.reload(entry);
            }
            return;
        }
        entry.lastUse = System.nanoTime();
        double spacing = Minecraft.getInstance().options.chatLineSpacing().get();
        int lineHeight = lineHeight();
        int offset = (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing);
        int top = textY + offset - lineHeight;
        int sliceTop = line.index() * lineHeight;
        int sliceHeight = Math.min(lineHeight, line.height() - sliceTop);
        if (sliceHeight <= 0) {
            return;
        }
        // Texel rows for this slice: rounded at the shared edges so neighbouring slices meet without a gap.
        int texHeight = entry.height();
        int v0 = Math.round((float) sliceTop * texHeight / line.height());
        int v1 = Math.round((float) (sliceTop + sliceHeight) * texHeight / line.height());
        if (v1 <= v0) {
            v1 = Math.min(texHeight, v0 + 1);
            v0 = v1 - 1;
        }
        int color = ARGB.white(Math.max(0.02f, Math.min(1f, opacity)));
        g.blit(RenderPipelines.GUI_TEXTURED, entry.texture, 0, top, 0f, (float) v0, line.width(), sliceHeight, entry.width(), v1 - v0,
                entry.width(), entry.height(), color);
        if (recording) {
            var pose = g.pose();
            Vector2f a = pose.transformPosition(0f, top, new Vector2f());
            Vector2f b = pose.transformPosition(line.width(), top + sliceHeight, new Vector2f());
            HITS.add(new Hit(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y), line.ref()));
        }
    }

    /** A left click on the open chat: opens the viewer if it landed on a picture. */
    public static boolean click(double x, double y) {
        for (Hit hit : HITS) {
            if (x >= hit.x0() && x < hit.x1() && y >= hit.y0() && y < hit.y1()) {
                Entry entry = ImageCache.peek(hit.ref().key());
                if (entry != null && entry.state == State.READY) {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.gui.setScreen(new ImageViewerScreen(entry, minecraft.gui.screen()));
                    return true;
                }
            }
        }
        return false;
    }

    public static String describe(Ref ref) {
        return switch (ref.kind()) {
            case STICKER -> "sticker " + ref.value();
            case NET -> "shared screenshot";
            case URL -> ref.host() == null ? ref.value() : ref.host();
        };
    }

    static boolean isSticker(Ref ref) {
        return ref.kind() == Kind.STICKER;
    }
}
