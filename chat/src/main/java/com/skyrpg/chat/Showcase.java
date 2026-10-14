package com.skyrpg.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Item showcase: {@code [item]} (or {@code [hand]}), {@code [offhand]}, {@code [armor]} and {@code [pet]} in a
 * message become the item's name in its rarity colour, with the full tooltip on hover.
 */
public final class Showcase {
    /** Tokens ChatBody looks for (lower case, with brackets). */
    public static final List<String> TOKENS = List.of("[item]", "[hand]", "[offhand]", "[armor]", "[armour]", "[pet]");
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private Showcase() {
    }

    /** What a token shows for this player. */
    public static Component token(ServerPlayer player, String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "[item]", "[hand]" -> item(player.getMainHandItem(), "nothing");
            case "[offhand]" -> item(player.getOffhandItem(), "nothing");
            case "[armor]", "[armour]" -> armor(player);
            case "[pet]" -> pet(player);
            default -> Component.literal(token);
        };
    }

    /** "[Skyiron Pickaxe]" in the item's colour, "x16" for stacks, with the item's tooltip on hover. */
    public static Component item(ItemStack stack, String emptyText) {
        if (stack.isEmpty()) {
            return Component.literal("[" + emptyText + "]").withStyle(ChatFormatting.DARK_GRAY);
        }
        Component name = stack.getStyledHoverName();
        TextColor color = firstColor(name);
        MutableComponent shown = Component.literal("[").append(ChatIcons.icon(ChatIcons.rarityGem(color)))
                .append(Component.literal(" ")).append(name);
        if (stack.getCount() > 1) {
            shown.append(Component.literal(" x" + stack.getCount()).withColor(ChatColors.MUTED));
        }
        shown.append(Component.literal("]"));
        HoverEvent hover = new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack));
        return shown.withStyle(style -> style.withColor(color).withHoverEvent(hover));
    }

    private static Component armor(ServerPlayer player) {
        MutableComponent out = Component.empty();
        boolean any = false;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.isEmpty()) {
                continue;
            }
            if (any) {
                out.append(Component.literal(" "));
            }
            out.append(item(piece, ""));
            any = true;
        }
        return any ? out : Component.literal("[no armor]").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component pet(ServerPlayer player) {
        ItemStack pet = Profiles.activePet(player);
        if (pet.isEmpty()) {
            return Component.literal("[no pet]").withStyle(ChatFormatting.DARK_GRAY);
        }
        // A pet's name already reads "[Lv 12] Rare Mole", so no extra brackets.
        HoverEvent hover = new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(pet));
        return Component.empty().append(pet.getHoverName()).withStyle(style -> style.withHoverEvent(hover));
    }

    /** The first colour anywhere in the text (a SkyRPG item's name carries its rarity colour inside). */
    static TextColor firstColor(Component text) {
        TextColor[] found = {null};
        text.visit((style, part) -> {
            if (!part.isEmpty() && style.getColor() != null) {
                found[0] = style.getColor();
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0] != null ? found[0] : TextColor.fromLegacyFormat(ChatFormatting.WHITE);
    }
}
