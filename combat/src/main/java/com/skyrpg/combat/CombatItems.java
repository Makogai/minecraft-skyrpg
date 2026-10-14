package com.skyrpg.combat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Combat materials: the Elite Trophy (every Elite drops one), Valor Marks (the Combat Master's currency, from levelled
 * mobs; nine make a Valor Seal) and the rare drops, Dread Fangs and Nightmare Cores, for the best weapons and armour.
 * Plus the slayer bosses' tokens and rare drops, for slayer gear.
 */
public final class CombatItems {
    public static final Item ELITE_TROPHY = register("elite_trophy", Rarity.RARE);
    public static final Item VALOR_MARK = register("valor_mark", Rarity.UNCOMMON);
    public static final Item VALOR_SEAL = register("valor_seal", Rarity.RARE);
    public static final Item DREAD_FANG = register("dread_fang", Rarity.RARE);
    public static final Item NIGHTMARE_CORE = register("nightmare_core", Rarity.EPIC);
    // Slayer tokens (every boss drops some) and rare drops (tier III and up).
    public static final Item TYRANTS_ASH = register("tyrants_ash", Rarity.UNCOMMON);
    public static final Item BROOD_SILK = register("brood_silk", Rarity.UNCOMMON);
    public static final Item MARROW_SHARD = register("marrow_shard", Rarity.UNCOMMON);
    public static final Item VOID_FRAGMENT = register("void_fragment", Rarity.UNCOMMON);
    public static final Item PLAGUE_HEART = register("plague_heart", Rarity.EPIC);
    public static final Item VENOM_GLAND = register("venom_gland", Rarity.EPIC);
    public static final Item MARSHALS_SIGIL = register("marshals_sigil", Rarity.EPIC);
    public static final Item NULL_CORE = register("null_core", Rarity.EPIC);
    // Drops of our world mobs.
    public static final Item HAGS_CHARM = register("hags_charm", Rarity.UNCOMMON);
    public static final Item RIME_SHARD = register("rime_shard", Rarity.UNCOMMON);
    public static final Item EMBER_CORE = register("ember_core", Rarity.UNCOMMON);
    public static final Item WISP_ESSENCE = register("wisp_essence", Rarity.UNCOMMON);
    public static final Item TRAINING_DUMMY = Registry.register(BuiltInRegistries.ITEM, key("training_dummy"),
            new TrainingDummy.DummyItem(new Item.Properties().setId(key("training_dummy")).rarity(Rarity.UNCOMMON).stacksTo(16)));

    private CombatItems() {
    }

    static void init() {
        // Loads the class so the items register during mod init.
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, SkyRpgCombat.id(name));
    }

    private static Item register(String name, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(rarity)));
    }
}
