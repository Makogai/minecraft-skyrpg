package com.skyrpg.enchants;

import java.util.Comparator;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What enchanting adds to the game's registries: the enchants component on items, the Arcane Tome (one enchant,
 * waiting to be applied) and Runic Dust (from salvaged tomes, spent on the strongest work), plus a creative tab
 * with a tome of every enchant.
 */
public final class EnchantContent {
    public static final DataComponentType<EnchantList> ENCHANTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            SkyRpgEnchants.id("enchants"), DataComponentType.<EnchantList>builder()
                    .persistent(EnchantList.CODEC).networkSynchronized(EnchantList.STREAM_CODEC).cacheEncoding().build());

    public static final Item TOME = register("arcane_tome", new Item.Properties().stacksTo(1)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final Item RUNIC_DUST = register("runic_dust", new Item.Properties());

    private EnchantContent() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgEnchants.id("enchanting")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_enchants"))
                        .icon(() -> new ItemStack(TOME))
                        .displayItems((parameters, output) -> {
                            output.accept(new ItemStack(RUNIC_DUST));
                            Enchants.all().stream()
                                    .sorted(Comparator.comparing((Enchant def) -> def.appliesTo().getFirst()).thenComparing(Enchant::rarity)
                                            .thenComparing(Enchant::name))
                                    .forEach(def -> output.accept(tome(def, def.maxLevel())));
                        })
                        .build());
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgEnchants.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }

    /** A tome of this enchant: "Tome of Keen V", in the enchant's colour. */
    public static ItemStack tome(Enchant def, int level) {
        ItemStack stack = new ItemStack(TOME);
        stack.set(ENCHANTS, EnchantList.EMPTY.with(def.id(), level));
        stack.set(DataComponents.ITEM_NAME, Component.literal("Tome of " + def.title(level))
                .withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(def.ultimate())));
        return stack;
    }

    public static boolean isTome(ItemStack stack) {
        return stack.is(TOME) && stack.has(ENCHANTS);
    }

    public static EnchantList enchants(ItemStack stack) {
        return stack.isEmpty() ? EnchantList.EMPTY : stack.getOrDefault(ENCHANTS, EnchantList.EMPTY);
    }

    /** Sets (or clears) an item's enchants. */
    public static void setEnchants(ItemStack stack, EnchantList list) {
        if (list.isEmpty()) {
            stack.remove(ENCHANTS);
        } else {
            stack.set(ENCHANTS, list);
        }
    }
}
