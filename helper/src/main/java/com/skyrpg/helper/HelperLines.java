package com.skyrpg.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.chat.ChatIcons;
import com.skyrpg.chat.ChatLines;
import com.skyrpg.chat.Showcase;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyItems;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SkyHelper's chat lines: a sparkle and "SkyHelper" in a cyan-violet-pink gradient with a glow, an "AI" badge, and the
 * answer with every item it names shown like a chat [item] (rarity colour, full tooltip on hover). Each line is a
 * {@code skyrpg_chat.helper} marker line, so the chat mod paints the AI glow behind it.
 */
final class HelperLines {
    static final int CYAN = 0x7FE7FF;
    static final int VIOLET = 0xB58CFF;
    static final int PINK = 0xFF9BE6;
    private static final int TEXT = 0xE9EDF7;
    private static final int MUTED = 0x9AA3B8;
    private static final int ACTION = 0x7CFFB2;
    private static final int MAX_ITEMS = 5;

    private HelperLines() {
    }

    /** A whole line: header, then the body, marked as SkyHelper's. */
    static Component line(Component body, String asker) {
        return ChatLines.single(ChatLines.HELPER, header().append(body), asker);
    }

    /** "✦ SkyHelper [AI] " with a hover that says what it is. */
    static MutableComponent header() {
        Component about = Component.literal("SkyHelper").withStyle(Style.EMPTY.withColor(CYAN).withBold(true))
                .append(Component.literal("\nSkyRPG's AI assistant. It answers from the wiki,\nruns on the server and can make mistakes."
                        + "\n\nAsk with @skyhelper <question>, or /ask to ask privately.")
                        .withStyle(Style.EMPTY.withColor(MUTED).withBold(false)));
        MutableComponent out = Component.empty().withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(about)));
        out.append(ChatIcons.tinted(Notice.SPARKLE, CYAN));
        out.append(Component.literal(" "));
        String name = "SkyHelper";
        for (int i = 0; i < name.length(); i++) {
            int rgb = gradient(i / (float) (name.length() - 1));
            out.append(Component.literal(String.valueOf(name.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withBold(true).withShadowColor(0xCC000000 | darken(rgb, 0.45f))));
        }
        out.append(Component.literal(" "));
        out.append(ChatIcons.tinted(ChatIcons.BADGE_LEFT, 0x6E5AA8));
        out.append(Component.literal("AI").withStyle(Style.EMPTY.withColor(VIOLET).withShadowColor(0xCC2A1B4D)));
        out.append(ChatIcons.tinted(ChatIcons.BADGE_RIGHT, 0x6E5AA8));
        out.append(Component.literal(" "));
        return out;
    }

    /**
     * The answer: "@Asker " (or "(only you) "), the text with its items, the [Wiki↗] button and, when {@code actions},
     * the commands SkyHelper offers as buttons. A button runs its command as whoever clicks it, with their own permissions.
     */
    static Component answer(String asker, JsonObject reply, boolean pub, boolean actions) {
        MutableComponent body = Component.empty();
        body.append(Component.literal(pub ? "@" + asker + " " : "(only you) ").withColor(MUTED));
        String text = reply.get("answer").getAsString();
        JsonArray items = reply.has("items") && reply.get("items").isJsonArray() ? reply.getAsJsonArray("items") : new JsonArray();
        int cursor = 0;
        int shown = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (JsonElement element : items) {
            if (shown == MAX_ITEMS || !element.isJsonObject()) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            String found = item.has("text") ? item.get("text").getAsString() : "";
            ItemStack stack = stack(item.has("id") ? item.get("id").getAsString() : "");
            int at = found.isEmpty() ? -1 : lower.indexOf(found.toLowerCase(Locale.ROOT), cursor);
            if (at < 0 || stack.isEmpty()) {
                continue;
            }
            if (at > cursor) {
                body.append(Component.literal(text.substring(cursor, at)).withColor(TEXT));
            }
            body.append(Showcase.item(stack, found));
            cursor = at + found.length();
            shown++;
        }
        if (cursor < text.length()) {
            body.append(Component.literal(text.substring(cursor)).withColor(TEXT));
        }
        if (actions && reply.has("actions") && reply.get("actions").isJsonArray()) {
            for (JsonElement element : reply.getAsJsonArray("actions")) {
                String command = element.getAsString();
                if (!command.startsWith("/") || command.length() > 120) {
                    continue;
                }
                Component hover = Component.literal("Click to run this as you:\n").withColor(MUTED)
                        .append(Component.literal(command).withColor(ACTION))
                        .append(Component.literal("\nIt uses your own permissions.").withColor(MUTED));
                body.append(Component.literal(" "));
                body.append(Component.literal("[▶ " + command + "]").withStyle(Style.EMPTY.withColor(ACTION)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(hover))));
            }
        }
        if (reply.has("page") && !reply.get("page").isJsonNull()) {
            String page = reply.get("page").getAsString();
            String anchor = reply.has("anchor") && !reply.get("anchor").isJsonNull() ? reply.get("anchor").getAsString() : null;
            String title = reply.has("title") && !reply.get("title").isJsonNull() ? reply.get("title").getAsString() : page;
            body.append(Component.literal(" "));
            body.append(Component.literal("[Wiki↗]").withStyle(Style.EMPTY.withColor(CYAN).withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/wiki " + page + (anchor != null ? "#" + anchor : "")))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open \"" + title + "\" in the wiki").withColor(CYAN)))));
        }
        return body;
    }

    /** A plain message from SkyHelper (usage, "asleep"), in the muted colour. */
    static Component note(String text) {
        return Component.literal(text).withColor(MUTED);
    }

    /** A fresh stack for a SkyRPG or vanilla item id, with its name, rarity and stats; EMPTY when it isn't an item. */
    private static ItemStack stack(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        ItemStack sky = SkyItems.create(key, 1);
        if (!sky.isEmpty()) {
            return sky;
        }
        var item = BuiltInRegistries.ITEM.getValue(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int gradient(float t) {
        return t < 0.5f ? lerp(CYAN, VIOLET, t * 2) : lerp(VIOLET, PINK, (t - 0.5f) * 2);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int darken(int rgb, float f) {
        return Math.round((rgb >> 16 & 0xFF) * f) << 16 | Math.round((rgb >> 8 & 0xFF) * f) << 8 | Math.round((rgb & 0xFF) * f);
    }
}
