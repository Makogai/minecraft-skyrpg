package com.skyrpg.items.client;

import com.skyrpg.core.items.ToolTiers;
import com.skyrpg.core.items.ToolWear;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.ItemSet;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.backpack.Backpacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Builds the tooltip for SkyRPG items from their definition: name, stat lines with their icons,
 * description, ability, set bonus (with your worn pieces counted) and the rarity line.
 */
final class ItemTooltips {
    /** Paragraphs wrap at the name's width, but no narrower than this... */
    private static final int MIN_WRAP = 170;
    /** ...and no wider than this, so a long custom name doesn't make one very wide line. */
    private static final int MAX_WRAP = 220;

    /** Set per tooltip from its name line; tooltips are built one at a time on the render thread. */
    private static int wrapWidth = MIN_WRAP;

    private ItemTooltips() {
    }

    static void replace(ItemStack stack, TooltipFlag flag, List<Component> lines) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null && !lines.isEmpty() && hasTier(stack)) {
            lines.add(1, tierLine(stack)); // vanilla pickaxes and axes say their tier too
        }
        if (def == null || lines.isEmpty()) {
            return;
        }
        List<Component> out = new ArrayList<>();
        out.add(stack.has(DataComponents.CUSTOM_NAME) ? lines.getFirst() : Component.literal(def.name()).withColor(def.rarity().color()));
        wrapWidth = Mth.clamp(Minecraft.getInstance().font.width(out.getFirst()), MIN_WRAP, MAX_WRAP);

        // Weapon damage first, then the rest in the usual stat order.
        List<StatType> order = new ArrayList<>(StatTypes.all());
        order.remove(StatTypes.DAMAGE);
        order.addFirst(StatTypes.DAMAGE);
        for (StatType stat : order) {
            double value = def.stat(stat);
            if (value != 0) {
                out.add(Component.literal(stat.symbol() + " ").withColor(stat.color())
                        .append(Component.literal(stat.name() + " ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(stat.formatBonus(value)).withColor(stat.color())));
            }
        }

        if (hasTier(stack)) {
            out.add(tierLine(stack));
        }
        if (SkyItems.isTool(stack) && stack.isDamageableItem()) {
            out.add(durabilityLine(stack));
        }

        // Perks: what the item's gear effects do, in plain words.
        if (!def.perks().isEmpty()) {
            out.add(Component.empty());
            for (String perk : def.perks()) {
                List<String> wrapped = wrap(perk);
                for (int i = 0; i < wrapped.size(); i++) {
                    out.add(Component.literal((i == 0 ? "✔ " : "  ") + wrapped.get(i)).withStyle(ChatFormatting.GREEN));
                }
            }
        }

        // Keep what vanilla adds after the name (enchantments, lore).
        if (lines.size() > 1 && !flag.isAdvanced()) {
            out.add(Component.empty());
            out.addAll(lines.subList(1, lines.size()));
        }

        if (!def.description().isEmpty()) {
            out.add(Component.empty());
            wrap(def.description()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        if (Backpacks.isBackpack(stack)) {
            long used = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
            out.add(Component.literal("Holds ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(used + "/" + Backpacks.capacity(stack)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" stacks").withStyle(ChatFormatting.GRAY)));
            out.add(Component.literal("Carry it in a backpack slot (E), press B to open").withStyle(ChatFormatting.DARK_GRAY));
        }

        ItemDefinition.Ability ability = def.ability();
        if (ability != null) {
            out.add(Component.empty());
            out.add(Component.literal("\u2726 " + ability.name()).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" \u00B7 Right-click").withStyle(ChatFormatting.DARK_GRAY)));
            wrap(ability.description()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            MutableComponent cost = Component.empty();
            if (ability.mana() > 0) {
                cost.append(Component.literal(StatTypes.MANA_ICON + " " + Msg.number(ability.mana()) + " Mana").withStyle(ChatFormatting.AQUA));
            }
            if (ability.cooldownSeconds() > 0) {
                if (ability.mana() > 0) {
                    cost.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
                }
                cost.append(Component.literal("Recharge " + Msg.number(ability.cooldownSeconds()) + "s").withStyle(ChatFormatting.DARK_GREEN));
            }
            if (!cost.getSiblings().isEmpty()) {
                out.add(cost);
            }
        }

        ItemSet set = ItemRegistry.set(def.set());
        if (set != null) {
            Minecraft minecraft = Minecraft.getInstance();
            int worn = minecraft.player == null ? 0 : SkyItems.wornPieces(minecraft.player).getOrDefault(set.id(), 0);
            boolean complete = worn >= set.pieces();
            out.add(Component.empty());
            out.add(Component.literal(set.name() + " ").withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                    .append(Component.literal("(" + worn + "/" + set.pieces() + ")").withStyle(complete ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY)));
            out.add(Component.literal(set.bonusName()).withStyle(complete ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            wrap(set.bonusDescription()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY)));
            for (Map.Entry<StatType, Double> bonus : set.bonusStats().entrySet()) {
                StatType stat = bonus.getKey();
                out.add(Component.literal(stat.symbol() + " " + stat.formatBonus(bonus.getValue()) + " " + stat.name())
                        .withColor(complete ? stat.color() : 0xFF707070));
            }
        }

        out.add(Component.empty());
        out.add(Component.literal("\u25C6 " + label(def.rarity().name()) + " " + SkyItems.typeLabel(def)).withColor(def.rarity().color()));
        if (flag.isAdvanced()) {
            out.add(Component.literal(def.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.clear();
        lines.addAll(out);
    }

    private static boolean hasTier(ItemStack stack) {
        return (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) && ToolTiers.tier(stack) > 0;
    }

    /** "⛏ Tier IV pickaxe": which of our ores or woods it can break (see ToolTiers). */
    private static Component tierLine(ItemStack stack) {
        boolean axe = stack.is(ItemTags.AXES);
        return Component.literal(axe ? "⚒ " : "⛏ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Tier " + ToolTiers.roman(ToolTiers.tier(stack)) + (axe ? " axe" : " pickaxe")).withStyle(ChatFormatting.GRAY));
    }

    /** "Durability 450 / 600" (green, then yellow, then red as it wears), or "Broken: repair it" at 1. */
    private static Component durabilityLine(ItemStack stack) {
        int max = stack.getMaxDamage();
        int left = max - stack.getDamageValue();
        if (ToolWear.isBroken(stack)) {
            return Component.literal("⚒ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Broken: repair it").withStyle(ChatFormatting.RED));
        }
        float ratio = left / (float) max;
        ChatFormatting color = ratio > 0.5f ? ChatFormatting.GREEN : ratio > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("⚒ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Durability ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Msg.number(left) + " / " + Msg.number(max)).withStyle(color));
    }

    /** "UNCOMMON" -> "Uncommon". */
    private static String label(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Splits text into lines at word boundaries, as wide as the tooltip's name line (within limits). */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        for (FormattedText line : Minecraft.getInstance().font.getSplitter().splitLines(text, wrapWidth, Style.EMPTY)) {
            lines.add(line.getString());
        }
        return lines;
    }
}
