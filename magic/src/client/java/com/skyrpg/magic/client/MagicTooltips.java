package com.skyrpg.magic.client;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.Magic;
import com.skyrpg.magic.MagicItems;
import com.skyrpg.magic.SpellDef;
import com.skyrpg.magic.WandData;
import com.skyrpg.magic.WandDef;
import com.skyrpg.magic.WandItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Extra tooltip lines for scrolls (element, mana, recharge, charge) and wands (the spells in the slots and the wand's own bonuses),
 * added in a late phase so they land after the items mod has built its tooltip, just above the rarity line.
 */
final class MagicTooltips {
    private MagicTooltips() {
    }

    static void add(ItemStack stack, List<Component> lines) {
        List<Component> extra = new ArrayList<>();
        String spellId = MagicItems.spellOf(stack);
        if (spellId != null) {
            SpellDef spell = Magic.spell(spellId);
            if (spell == null) {
                return;
            }
            extra.add(Component.literal(spell.element().label() + " spell").withColor(spell.element().color())
                    .append(Component.literal(spell.chargeable() ? "  · hold to charge" : "").withStyle(ChatFormatting.DARK_GRAY)));
            extra.add(Component.literal(StatTypes.MANA_ICON + " " + Msg.number(spell.mana()) + " Mana").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("   Recharge " + Msg.number(spell.cooldownSeconds()) + "s").withStyle(ChatFormatting.DARK_GREEN)));
            extra.add(Component.literal("Install it in a wand: sneak + right click").withStyle(ChatFormatting.DARK_GRAY));
        } else if (WandItem.isWand(stack)) {
            WandData data = WandItem.data(stack);
            WandDef def = WandItem.def(stack);
            // The scrolls are the stack's container contents, which vanilla lists by itself: our own list replaces that.
            stack.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
                    .nonEmptyItemCopyStream().forEach(held -> {
                        String name = held.getHoverName().getString();
                        lines.removeIf(line -> line.getString().contains(name));
                    });
            extra.add(Component.literal("Spells").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + data.filled() + "/" + data.slots().size() + " slots").withStyle(ChatFormatting.DARK_GRAY)));
            for (int i = 0; i < data.slots().size(); i++) {
                SpellDef spell = Magic.spell(data.spellAt(i));
                Component marker = Component.literal(i == data.selected() ? "▶ " : "  ").withStyle(ChatFormatting.YELLOW);
                if (spell == null) {
                    extra.add(Component.empty().append(marker).append(Component.literal("Empty slot").withStyle(ChatFormatting.DARK_GRAY)));
                } else {
                    extra.add(Component.empty().append(marker).append(Component.literal(spell.name()).withColor(spell.rarity().color()))
                            .append(Component.literal("  " + Msg.number(Math.ceil(spell.mana() * (1 - (def == null ? 0 : def.manaDiscount())))) + " mana")
                                    .withStyle(ChatFormatting.AQUA)));
                }
            }
            extra.add(Component.literal("Left click casts  ·  hold right click: wheel").withStyle(ChatFormatting.DARK_GRAY));
            extra.add(Component.literal("Sneak + right click: spell slots").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (extra.isEmpty() || Minecraft.getInstance().player == null) {
            return;
        }
        // Just above the blank line that precedes the rarity line (the last blank, ignoring a trailing id line).
        int at = lines.size();
        for (int i = lines.size() - 1; i >= 1; i--) {
            if (lines.get(i).getString().isEmpty()) {
                at = i;
                break;
            }
        }
        List<Component> block = new ArrayList<>();
        block.add(Component.empty());
        block.addAll(extra);
        lines.addAll(at, block);
    }
}
