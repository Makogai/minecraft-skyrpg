package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Spawn eggs for our world mobs, in the creative Spawn Eggs tab. Mobs from eggs get a level like natural spawns (see
 * MobLevels). The slayer bosses have none: outside a quest they have no owner and leave at once.
 */
public final class MobEggs {
    private static final List<Item> EGGS = new ArrayList<>();

    private MobEggs() {
    }

    static void init() {
        register("glimmer_crawler", WorldMobs.GLIMMER_CRAWLER);
        register("bog_hag", WorldMobs.BOG_HAG);
        register("frost_revenant", WorldMobs.FROST_REVENANT);
        register("ember_imp", WorldMobs.EMBER_IMP);
        register("grove_sentinel", WorldMobs.GROVE_SENTINEL);
        register("tide_lurker", WorldMobs.TIDE_LURKER);
        register("hollow_wisp", WorldMobs.HOLLOW_WISP);
        register("gravecaller", WorldMobs.GRAVECALLER);
        ResourceKey<net.minecraft.world.item.CreativeModeTab> tab =
                ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> EGGS.forEach(output::accept));
    }

    private static void register(String mob, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id(mob + "_spawn_egg"));
        EGGS.add(Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type))));
    }
}
