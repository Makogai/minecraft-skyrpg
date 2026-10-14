package com.skyrpg.magic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Everything magic adds to the registries: the selected-spell component, seven wands, twelve spell scrolls and a creative tab. */
public final class MagicItems {
    /** Which slot of a wand is selected. The scrolls themselves are the stack's vanilla container contents, so an upgraded wand keeps them. */
    public static final DataComponentType<Integer> SELECTED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            SkyRpgMagic.id("selected_spell"), DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.intRange(0, 15)).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

    /** Wand item ids by path, weakest first. */
    public static final List<String> WAND_NAMES = List.of("apprentice_wand", "gilded_wand", "skyiron_rod", "emberheart_staff",
            "frostspire_staff", "tempest_scepter", "starweaver_staff");
    /** Spell paths: Fire, Frost, Lightning, Arcane, Nature. Each has a scroll item named {@code <path>_scroll}. */
    public static final List<String> SPELL_NAMES = List.of("firebolt", "fireball", "meteor", "frost_shard", "blizzard", "chain_lightning",
            "thunderclap", "arcane_missiles", "blink", "mana_shield", "healing_pulse", "entangle");

    public static final Map<String, Item> WANDS = new LinkedHashMap<>();
    public static final Map<String, Item> SCROLLS = new LinkedHashMap<>();

    static {
        for (String name : WAND_NAMES) {
            ResourceKey<Item> key = key(name);
            WANDS.put(name, Registry.register(BuiltInRegistries.ITEM, key,
                    new WandItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON))));
        }
        for (String name : SPELL_NAMES) {
            ResourceKey<Item> key = key(name + "_scroll");
            SCROLLS.put(name, Registry.register(BuiltInRegistries.ITEM, key,
                    new Item(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.UNCOMMON))));
        }
    }

    private MagicItems() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgMagic.id("magic")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_magic"))
                        .icon(() -> new ItemStack(WANDS.get("gilded_wand")))
                        .displayItems((parameters, output) -> {
                            WANDS.values().forEach(item -> output.accept(new ItemStack(item)));
                            SCROLLS.values().forEach(item -> output.accept(new ItemStack(item)));
                        })
                        .build());
    }

    private static ResourceKey<Item> key(String path) {
        return ResourceKey.create(Registries.ITEM, SkyRpgMagic.id(path));
    }

    /** The scroll item of a spell id such as {@code skyrpg_magic:firebolt}, or null. */
    public static Item scrollFor(String spellId) {
        Identifier id = Identifier.tryParse(spellId);
        return id == null || !id.getNamespace().equals(SkyRpgMagic.MOD_ID) ? null : SCROLLS.get(id.getPath());
    }

    /** The spell id a scroll item stands for, or null if the stack is not a scroll. */
    public static String spellOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Item> entry : SCROLLS.entrySet()) {
            if (stack.is(entry.getValue())) {
                return SkyRpgMagic.id(entry.getKey()).toString();
            }
        }
        return null;
    }
}
