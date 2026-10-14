package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
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
 * The Dwarven Halls' own monsters (docs/design/the_deep.md), each with its own 3D model (the client's DwarvenMobRenderers):
 * <ul>
 *   <li>Dwarven Construct ({@code skyrpg_combat:dwarven_construct}): a stone-and-brass automaton, slow and armoured,
 *   projectiles do half damage, a heavy knockback punch. The world module spawns it in the Dwarven Halls layer and
 *   dungeon (scaled up as the Forgeguard champion); it has no natural biome spawns of its own. Spawn egg in the creative
 *   Spawn Eggs tab.</li>
 *   <li>The Iron Thane ({@code skyrpg_combat:iron_thane}): the Dwarven Halls boss, spawned and driven by the world module's
 *   dungeon boss system (see IronThaneEntity). No spawn egg, like the other bosses.</li>
 * </ul>
 * Drops: {@code data/skyrpg_combat/loot_table/entities/<id>.json} (the Thane's real loot comes from its boss JSON).
 */
public final class DwarvenMobs {
    public static final EntityType<DwarvenConstructEntity> DWARVEN_CONSTRUCT = register("dwarven_construct",
            EntityType.Builder.of(DwarvenConstructEntity::new, MobCategory.MONSTER).fireImmune().sized(1.0f, 2.1f).eyeHeight(1.75f)
                    .clientTrackingRange(8));
    public static final EntityType<IronThaneEntity> IRON_THANE = register("iron_thane",
            EntityType.Builder.of(IronThaneEntity::new, MobCategory.MONSTER).fireImmune().sized(1.6f, 3.0f).eyeHeight(2.45f)
                    .clientTrackingRange(10));

    private static Item constructEgg;

    private DwarvenMobs() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(DWARVEN_CONSTRUCT, DwarvenConstructEntity.attributes());
        FabricDefaultAttributeRegistry.register(IRON_THANE, IronThaneEntity.attributes());
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id("dwarven_construct_spawn_egg"));
        constructEgg = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(DWARVEN_CONSTRUCT)));
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(constructEgg));
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
