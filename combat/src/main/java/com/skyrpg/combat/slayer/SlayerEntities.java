package com.skyrpg.combat.slayer;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.boss.BoneMarshalEntity;
import com.skyrpg.combat.slayer.boss.BroodmotherEntity;
import com.skyrpg.combat.slayer.boss.GraveTyrantEntity;
import com.skyrpg.combat.slayer.boss.VoidStalkerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** The slayer bosses' entity types: vanilla bodies and AI, our own looks (see the client's renderers). */
public final class SlayerEntities {
    public static final EntityType<GraveTyrantEntity> GRAVE_TYRANT = register("grave_tyrant",
            EntityType.Builder.of(GraveTyrantEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(10));
    public static final EntityType<BroodmotherEntity> BROODMOTHER = register("broodmother",
            EntityType.Builder.of(BroodmotherEntity::new, MobCategory.MONSTER).sized(1.4f, 0.9f).eyeHeight(0.65f).clientTrackingRange(10));
    public static final EntityType<BoneMarshalEntity> BONE_MARSHAL = register("bone_marshal",
            EntityType.Builder.of(BoneMarshalEntity::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(10));
    public static final EntityType<VoidStalkerEntity> VOID_STALKER = register("void_stalker",
            EntityType.Builder.of(VoidStalkerEntity::new, MobCategory.MONSTER).sized(0.6f, 2.9f).eyeHeight(2.55f).clientTrackingRange(10));

    private SlayerEntities() {
    }

    static void init() {
        FabricDefaultAttributeRegistry.register(GRAVE_TYRANT, Zombie.createAttributes());
        FabricDefaultAttributeRegistry.register(BROODMOTHER, Spider.createAttributes());
        FabricDefaultAttributeRegistry.register(BONE_MARSHAL, AbstractSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(VOID_STALKER, Enderman.createAttributes());
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
