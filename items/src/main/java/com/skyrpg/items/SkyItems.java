package com.skyrpg.items;

import com.skyrpg.core.stats.StatTypes;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Item stacks of SkyRPG items. A stack is a SkyRPG item when its item has a definition; its name colour,
 * attack damage and glint are re-applied from the current definition by {@link #normalize}, so balance
 * changes reach items players already own.
 */
public final class SkyItems {
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * Hides the vanilla tooltip lines our own tooltip replaces (attributes, unbreakable). Every SkyRPG
     * item has it by default (see {@link ModItems}).
     */
    static final TooltipDisplay TOOLTIP = new TooltipDisplay(false, hidden());

    /**
     * Before SkyRPG items were registered items they were vanilla items with
     * {@code custom_data: {skyrpg_item: "<old id>"}}. {@link #migrate} swaps those for the new items.
     */
    private static final String LEGACY_TAG = "skyrpg_item";
    private static final Map<String, String> LEGACY_IDS = new HashMap<>();

    static {
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        Map<String, String> sets = Map.of("farmer", "harvester", "miner", "prospector", "lumberjack", "woodsman", "warrior", "vanguard");
        sets.forEach((old, renamed) -> {
            for (String piece : pieces) {
                LEGACY_IDS.put(old + "_" + piece, renamed + "_" + piece);
            }
        });
        LEGACY_IDS.put("farmers_hoe", "harvester_hoe");
        LEGACY_IDS.put("miners_pickaxe", "prospector_pickaxe");
        LEGACY_IDS.put("lumberjacks_axe", "woodsman_axe");
        LEGACY_IDS.put("warriors_blade", "vanguard_blade");
        LEGACY_IDS.put("aspect_of_the_void", "riftblade");
        LEGACY_IDS.put("healing_wand", "mending_wand");
    }

    private SkyItems() {
    }

    private static SequencedSet<DataComponentType<?>> hidden() {
        SequencedSet<DataComponentType<?>> hidden = new LinkedHashSet<>();
        hidden.add(DataComponents.ATTRIBUTE_MODIFIERS);
        hidden.add(DataComponents.UNBREAKABLE);
        return hidden;
    }

    /** The id of the SkyRPG item in this stack, or null if it's a plain item. */
    public static Identifier idOf(ItemStack stack) {
        ItemDefinition def = definition(stack);
        return def == null ? null : def.id();
    }

    /** The definition behind this stack, or null if it isn't a (known) SkyRPG item. */
    public static ItemDefinition definition(ItemStack stack) {
        return stack.isEmpty() ? null : ItemRegistry.item(stack.getItem());
    }

    /** A fresh stack of this item, or EMPTY if it's unknown. */
    public static ItemStack create(Identifier id, int count) {
        ItemDefinition def = ItemRegistry.find(id);
        return def == null ? ItemStack.EMPTY : create(def.item(), count);
    }

    public static ItemStack create(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        normalize(stack);
        return stack;
    }

    /**
     * Brings a SkyRPG stack in line with its current definition. Only touches components that differ, so
     * calling it every second costs nothing for items that are already right. Returns false for plain items.
     */
    public static boolean normalize(ItemStack stack) {
        ItemDefinition def = definition(stack);
        if (def == null) {
            return false;
        }
        // Stackable items (materials, seeds, meals) stay plain, so a fresh drop still stacks with what you carry;
        // their tooltip comes from the definition all the same.
        if (def.item().getDefaultMaxStackSize() > 1) {
            return true;
        }
        apply(stack, DataComponents.ITEM_NAME, Component.literal(def.name()).withColor(def.rarity().color()));
        apply(stack, DataComponents.UNBREAKABLE, Unit.INSTANCE);
        apply(stack, DataComponents.ATTRIBUTE_MODIFIERS, attributes(def));
        apply(stack, DataComponents.TOOLTIP_DISPLAY, TOOLTIP);
        apply(stack, DataComponents.ENCHANTMENT_GLINT_OVERRIDE, def.glint() ? Boolean.TRUE : null);
        return true;
    }

    /**
     * The registered item that replaces an old custom-data stack (same count, enchantments and custom
     * name), or null if this stack isn't one.
     */
    public static ItemStack migrate(ItemStack stack) {
        if (stack.isEmpty() || definition(stack) != null) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        Identifier old = data.copyTag().getString(LEGACY_TAG).map(Identifier::tryParse).orElse(null);
        if (old == null) {
            return null;
        }
        Item item = ModItems.get(SkyRpgItems.id(LEGACY_IDS.getOrDefault(old.getPath(), old.getPath())));
        if (item == null) {
            return null;
        }
        ItemStack replacement = new ItemStack(item, stack.getCount());
        replacement.set(DataComponents.ENCHANTMENTS, stack.get(DataComponents.ENCHANTMENTS));
        replacement.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
        normalize(replacement);
        return replacement;
    }

    private static <T> void apply(ItemStack stack, DataComponentType<T> type, T value) {
        if (!Objects.equals(stack.get(type), value)) {
            if (value == null) {
                stack.remove(type);
            } else {
                stack.set(type, value);
            }
        }
    }

    /**
     * Armour gets no vanilla attributes, so SkyRPG Defense replaces vanilla armour points. Weapons with a
     * "damage" stat get it as attack damage (5 damage = 1 vanilla point, like health) and keep their
     * item's attack speed. Anything else keeps its item's own attributes.
     */
    private static ItemAttributeModifiers attributes(ItemDefinition def) {
        if (armorSlot(def.item()) != null) {
            return ItemAttributeModifiers.EMPTY;
        }
        ItemAttributeModifiers base = def.item().components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double damage = def.stat(StatTypes.DAMAGE);
        // A bow's damage is for its arrows; hitting with the bow itself stays a poke.
        if (damage <= 0 || ModItems.kind(def.item()) == ModItems.Kind.BOW) {
            return base;
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        damage / StatTypes.HEALTH_SCALE - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        base.forEach(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                builder.add(attribute, modifier, EquipmentSlotGroup.MAINHAND);
            }
        });
        return builder.build();
    }

    /** Shields count in the off hand too (their stats and effects), unlike other items, which only count in the main hand. */
    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && ModItems.kind(stack.getItem()) == ModItems.Kind.SHIELD;
    }

    /** The shield whose stats and effects count: the one in the off hand, unless the main hand holds one (it already counts as held). */
    public static ItemDefinition offhandShield(Player player) {
        return isShield(player.getOffhandItem()) && !isShield(player.getMainHandItem()) ? definition(player.getOffhandItem()) : null;
    }

    /** HEAD/CHEST/LEGS/FEET for armour, null for anything else. */
    public static EquipmentSlot armorSlot(Item item) {
        Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }
        EquipmentSlot slot = equippable.slot();
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
    }

    /** How many pieces of each set the player is wearing (in their proper slots). Works on both sides. */
    public static Map<Identifier, Integer> wornPieces(Player player) {
        Map<Identifier, Integer> counts = new HashMap<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemDefinition def = definition(player.getItemBySlot(slot));
            if (def != null && def.set() != null && armorSlot(def.item()) == slot) {
                counts.merge(def.set(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** "Helmet", "Sword"... as shown in the rarity line; a definition's {@code "type"} overrides it ("Scythe", "Seeds"). */
    public static String typeLabel(ItemDefinition def) {
        if (def.json().has("type")) {
            return def.json().get("type").getAsString();
        }
        ModItems.Kind kind = ModItems.kind(def.item());
        if (kind == null) {
            return "Item";
        }
        String name = kind.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
