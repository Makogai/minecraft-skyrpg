package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mixin.SpawnPlacementsInvoker;
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
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Nether's own monsters (docs/design/nether.md), each with its own 3D model (the client's NetherMobRenderers):
 * <ul>
 *   <li>Bone Giant ({@code skyrpg_combat:bone_giant}): a hunched giant skeleton of the Bone Sea, slow, tough and
 *   fire-immune, with a heavy club swing and a telegraphed bone quake (see BoneGiantEntity). It spawns naturally: the
 *   world module adds it to the Bone Sea biome's monster spawns by id; this class gives it the on-ground spawn
 *   placement and the usual monster spawn rules (dark enough, solid floor). Spawn egg in the creative Spawn Eggs tab.</li>
 *   <li>The Magma Tyrant ({@code skyrpg_combat:magma_tyrant}): the Infernal Citadel's boss, spawned and driven by the
 *   world module's dungeon boss system (see MagmaTyrantEntity). No spawn egg, like the other bosses.</li>
 * </ul>
 * Drops: {@code data/skyrpg_combat/loot_table/entities/<id>.json} (the Tyrant's real loot comes from its boss JSON).
 */
public final class NetherMobs {
    public static final EntityType<BoneGiantEntity> BONE_GIANT = register("bone_giant",
            EntityType.Builder.of(BoneGiantEntity::new, MobCategory.MONSTER).fireImmune().sized(1.4f, 3.5f).eyeHeight(3.2f)
                    .clientTrackingRange(10));
    public static final EntityType<MagmaTyrantEntity> MAGMA_TYRANT = register("magma_tyrant",
            EntityType.Builder.of(MagmaTyrantEntity::new, MobCategory.MONSTER).fireImmune().sized(2.0f, 3.6f).eyeHeight(2.8f)
                    .clientTrackingRange(10));

    private static Item boneGiantEgg;

    private NetherMobs() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(BONE_GIANT, BoneGiantEntity.attributes());
        FabricDefaultAttributeRegistry.register(MAGMA_TYRANT, MagmaTyrantEntity.attributes());
        SpawnPlacementsInvoker.skyrpg$register(BONE_GIANT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id("bone_giant_spawn_egg"));
        boneGiantEgg = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(BONE_GIANT)));
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(boneGiantEgg));
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
