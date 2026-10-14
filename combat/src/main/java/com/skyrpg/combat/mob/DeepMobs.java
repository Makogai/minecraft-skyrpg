package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The monsters of The Deep (docs/design/the_deep.md, "New monsters"), each with its own 3D model (the client's
 * DeepMobRenderers):
 * <ul>
 *   <li>Sporeling (Fungal Depths): a knee-high mushroom creature, weak and quick, that keeps with its group; it bursts
 *       into a poison spore cloud when it dies.</li>
 *   <li>Fire Beetle (Magma Rifts): a big, tough, fire-immune beetle whose bites set you alight; every few seconds it
 *       winds up and charges.</li>
 * </ul>
 * They have no biome spawns: the world module's deep layers spawn them by id near players in their layer. Like any
 * monster they get a level (MobLevels, plus the layer's bonus), a nameplate and damage numbers. Drops:
 * {@code data/skyrpg_combat/loot_table/entities/<id>.json}.
 */
public final class DeepMobs {
    public static final EntityType<SporelingEntity> SPORELING = register("sporeling",
            EntityType.Builder.of(SporelingEntity::new, MobCategory.MONSTER).sized(0.6f, 0.9f).eyeHeight(0.6f).clientTrackingRange(8));
    public static final EntityType<FireBeetleEntity> FIRE_BEETLE = register("fire_beetle",
            EntityType.Builder.of(FireBeetleEntity::new, MobCategory.MONSTER).fireImmune().sized(1.4f, 0.9f).eyeHeight(0.55f)
                    .clientTrackingRange(10));

    private static final List<Item> EGGS = new ArrayList<>();

    private DeepMobs() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SPORELING, SporelingEntity.attributes());
        FabricDefaultAttributeRegistry.register(FIRE_BEETLE, FireBeetleEntity.attributes());
        // Spawn eggs next to the other world mobs' in the creative Spawn Eggs tab.
        egg("sporeling", SPORELING);
        egg("fire_beetle", FIRE_BEETLE);
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> EGGS.forEach(output::accept));
    }

    private static void egg(String mob, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id(mob + "_spawn_egg"));
        EGGS.add(Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type))));
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
