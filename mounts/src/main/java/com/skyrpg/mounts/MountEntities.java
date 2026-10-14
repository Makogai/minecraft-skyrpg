package com.skyrpg.mounts;

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

/** The griffin's entity type ({@code skyrpg_mounts:griffin}) and its spawn egg. */
public final class MountEntities {
    public static final EntityType<GriffinEntity> GRIFFIN = register("griffin",
            EntityType.Builder.of(GriffinEntity::new, MobCategory.CREATURE).sized(1.3f, 1.9f).eyeHeight(1.7f)
                    .passengerAttachments(1.3f).clientTrackingRange(10).updateInterval(2));

    private MountEntities() {
    }

    static void init() {
        FabricDefaultAttributeRegistry.register(GRIFFIN, GriffinEntity.attributes());
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgMounts.id("griffin_spawn_egg"));
        Item egg = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(GRIFFIN)));
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(egg));
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgMounts.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
