package com.skyrpg.enchants.client;

import com.skyrpg.enchants.Category;
import com.skyrpg.enchants.Enchant;
import com.skyrpg.enchants.EnchantContent;
import com.skyrpg.enchants.EnchantList;
import com.skyrpg.enchants.Enchants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Enchant lines under an item's name, Hypixel style: each enchant in its rarity's colour (the ultimate first, bold), with
 * what it does when there are only a few; past that, a compact list (Shift shows the details). Tomes describe their
 * enchant, what it goes on and how to use them.
 */
final class EnchantTooltips {
    private static final int WRAP = 200;
    private static final int DETAILED_UP_TO = 4;

    private EnchantTooltips() {
    }

    static void add(ItemStack stack, List<Component> lines) {
        if (lines.isEmpty() || !stack.has(EnchantContent.ENCHANTS)) {
            return;
        }
        if (EnchantContent.isTome(stack)) {
            tome(stack, lines);
            return;
        }
        List<Map.Entry<Enchant, Integer>> enchants = sorted(EnchantContent.enchants(stack));
        if (enchants.isEmpty()) {
            return;
        }
        List<Component> out = new ArrayList<>();
        boolean detailed = enchants.size() <= DETAILED_UP_TO || Minecraft.getInstance().hasShiftDown();
        if (detailed) {
            for (Map.Entry<Enchant, Integer> entry : enchants) {
                out.add(name(entry.getKey(), entry.getValue()));
                wrap(entry.getKey().describe(entry.getValue())).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            }
        } else {
            MutableComponent row = Component.empty();
            int width = 0;
            for (int i = 0; i < enchants.size(); i++) {
                Map.Entry<Enchant, Integer> entry = enchants.get(i);
                MutableComponent piece = name(entry.getKey(), entry.getValue());
                String text = piece.getString() + (i < enchants.size() - 1 ? ", " : "");
                int w = Minecraft.getInstance().font.width(text);
                if (width > 0 && width + w > WRAP) {
                    out.add(row);
                    row = Component.empty();
                    width = 0;
                }
                row.append(piece);
                if (i < enchants.size() - 1) {
                    row.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                }
                width += w;
            }
            out.add(row);
            out.add(Component.literal("Hold Shift for details").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        lines.addAll(1, out);
    }

    private static void tome(ItemStack stack, List<Component> lines) {
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(stack).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        List<Component> out = new ArrayList<>();
        if (def == null) {
            out.add(Component.literal("Its magic has faded").withStyle(ChatFormatting.DARK_GRAY));
            lines.addAll(1, out);
            return;
        }
        int level = inside.getValue();
        out.add(Component.literal(def.rarity().label() + " enchant  ·  " + def.appliesTo().stream().map(Category::label)
                .collect(Collectors.joining(", "))).withColor(def.rarity().color()));
        wrap(def.describe(level)).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
        out.add(Component.empty());
        if (def.ultimate()) {
            out.add(Component.literal("Ultimate: an item holds only one").withStyle(Style.EMPTY.withColor(def.rarity().color())));
        }
        if (level < def.maxLevel()) {
            out.add(Component.literal("Two of these combine into " + def.title(level + 1)).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            out.add(Component.literal("Already at its highest level").withStyle(ChatFormatting.DARK_AQUA));
        }
        out.add(Component.literal("Apply it at Seraphine or an enchanting table").withStyle(ChatFormatting.DARK_GRAY));
        lines.addAll(1, out);
    }

    /** Ultimate first, then rarer before commoner, then by name. Unknown enchants (removed from the data) are skipped. */
    static List<Map.Entry<Enchant, Integer>> sorted(EnchantList list) {
        List<Map.Entry<Enchant, Integer>> out = new ArrayList<>();
        list.levels().forEach((id, level) -> {
            Enchant def = Enchants.get(id);
            if (def != null) {
                out.add(Map.entry(def, level));
            }
        });
        out.sort(Comparator.comparing((Map.Entry<Enchant, Integer> e) -> e.getKey().rarity()).reversed()
                .thenComparing(e -> e.getKey().name()));
        return out;
    }

    static MutableComponent name(Enchant def, int level) {
        return Component.literal((def.ultimate() ? "✦ " : "") + def.title(level))
                .withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(def.ultimate()));
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        for (FormattedText line : Minecraft.getInstance().font.getSplitter().splitLines(text, WRAP, Style.EMPTY)) {
            out.add(line.getString());
        }
        return out;
    }
}
