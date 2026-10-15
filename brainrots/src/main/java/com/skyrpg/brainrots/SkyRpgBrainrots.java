package com.skyrpg.brainrots;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyRPG Brainrots: fifteen meme creatures wander the survival world. Find them, hear the narrator say their names,
 * and right-click to catch them for your album (K menu > Brainrots, or J). See docs/design/brainrots.md.
 */
public final class SkyRpgBrainrots implements ModInitializer {
    public static final String MOD_ID = "skyrpg_brainrots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EntityType<BrainrotEntity> BRAINROT = registerEntity();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(BRAINROT, BrainrotEntity.attributes());
        for (BrainrotKind kind : BrainrotKind.values()) {
            Identifier soundId = id("voice." + kind.id);
            kind.voice = Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, SoundEvent.createVariableRangeEvent(soundId));
        }
        Album.init();
        ServerTickEvents.END_SERVER_TICK.register(Brainrots::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Brainrots.registerCommands(dispatcher));
    }

    private static EntityType<BrainrotEntity> registerEntity() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("brainrot"));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(BrainrotEntity::new, MobCategory.CREATURE)
                .sized(0.7f, 1.4f).clientTrackingRange(10).build(key));
    }
}
