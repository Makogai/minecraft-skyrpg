package com.skyrpg.enchants;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * What an enchant goes on, as data files name it ({@code "applies_to": ["sword"]}). Each kind is recognised the vanilla
 * way (sword tag, bow class, armour slot...) or by the item tag {@code skyrpg_enchants:enchantable/<kind>}, so new items
 * can join by data. Only single items are enchantable: never a stack of 64 pumpkins.
 */
public enum Category {
    SWORD("Swords", "⚔"),
    BOW("Bows", "➶"),
    HELMET("Helmets", "⛨"),
    CHESTPLATE("Chestplates", "⛨"),
    LEGGINGS("Leggings", "⛨"),
    BOOTS("Boots", "⛨"),
    ARMOR("Armor", "⛨"),
    PICKAXE("Pickaxes", "⛏"),
    AXE("Axes", "⚒"),
    HOE("Hoes", "☘"),
    SHOVEL("Shovels", "⛏"),
    TOOL("Tools", "⚒"),
    ROD("Fishing rods", "≈"),
    WAND("Wands", "✦");

    private final String label;
    private final String symbol;
    private final TagKey<Item> tag;

    Category(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
        this.tag = TagKey.create(Registries.ITEM, SkyRpgEnchants.id("enchantable/" + name().toLowerCase(Locale.ROOT)));
    }

    public String label() {
        return label;
    }

    public String symbol() {
        return symbol;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Category parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() > 1) {
            return false;
        }
        if (stack.is(tag)) {
            return true;
        }
        Item item = stack.getItem();
        return switch (this) {
            case SWORD -> stack.is(ItemTags.SWORDS);
            case BOW -> item instanceof BowItem || item instanceof CrossbowItem;
            case HELMET -> armorSlot(stack) == EquipmentSlot.HEAD;
            case CHESTPLATE -> armorSlot(stack) == EquipmentSlot.CHEST;
            case LEGGINGS -> armorSlot(stack) == EquipmentSlot.LEGS;
            case BOOTS -> armorSlot(stack) == EquipmentSlot.FEET;
            case ARMOR -> armorSlot(stack) != null;
            case PICKAXE -> stack.is(ItemTags.PICKAXES);
            case AXE -> stack.is(ItemTags.AXES);
            case HOE -> stack.is(ItemTags.HOES);
            case SHOVEL -> stack.is(ItemTags.SHOVELS);
            case TOOL -> PICKAXE.matches(stack) || AXE.matches(stack) || HOE.matches(stack) || SHOVEL.matches(stack);
            case ROD -> item instanceof FishingRodItem;
            case WAND -> false;
        };
    }

    /** HEAD/CHEST/LEGS/FEET for armour, null for anything else. */
    public static EquipmentSlot armorSlot(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }
        EquipmentSlot slot = equippable.slot();
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
    }

    /** Whether any enchant category takes this item. */
    public static boolean enchantable(ItemStack stack) {
        for (Category category : values()) {
            if (category.matches(stack)) {
                return true;
            }
        }
        return false;
    }
}
