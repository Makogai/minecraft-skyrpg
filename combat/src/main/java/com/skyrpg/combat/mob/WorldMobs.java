package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mixin.SpawnPlacementsInvoker;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Our own world mobs (see docs/design/slayers.md, section 4): new kinds on vanilla bodies, with their own looks (the
 * client's renderers), spawns, tricks and drops (loot tables {@code data/skyrpg_combat/loot_table/entities/<id>.json}).
 * They get mob levels and can be Elites like any natural spawn, and several count for slayer quests.
 * <ul>
 *   <li>Glimmer Crawler: caves below y 0, anywhere in the overworld. Quick.</li>
 *   <li>Bog Hag: swamps at night. Calls bog slimes.</li>
 *   <li>Frost Revenant: snowy biomes. Its arrows freeze you.</li>
 *   <li>Ember Imp: badlands and the Nether. A small, quick blaze.</li>
 *   <li>Grove Sentinel: forests at night. Slow and tough; its hits root you.</li>
 *   <li>Tide Lurker: rivers and beaches at night. Hauls you towards it with a tide hook.</li>
 *   <li>Hollow Wisp: plains and meadows at night. Flits through walls.</li>
 *   <li>Gravecaller: dark forests at night. Raises the dead.</li>
 * </ul>
 */
public final class WorldMobs {
    public static final EntityType<GlimmerCrawlerEntity> GLIMMER_CRAWLER = register("glimmer_crawler",
            EntityType.Builder.of(GlimmerCrawlerEntity::new, MobCategory.MONSTER).sized(0.7f, 0.5f).eyeHeight(0.45f).clientTrackingRange(8));
    public static final EntityType<BogHagEntity> BOG_HAG = register("bog_hag",
            EntityType.Builder.of(BogHagEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(8));
    public static final EntityType<FrostRevenantEntity> FROST_REVENANT = register("frost_revenant",
            EntityType.Builder.of(FrostRevenantEntity::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8));
    public static final EntityType<EmberImpEntity> EMBER_IMP = register("ember_imp",
            EntityType.Builder.of(EmberImpEntity::new, MobCategory.MONSTER).fireImmune().sized(0.6f, 1.8f).clientTrackingRange(8));
    public static final EntityType<GroveSentinelEntity> GROVE_SENTINEL = register("grove_sentinel",
            EntityType.Builder.of(GroveSentinelEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(8));
    public static final EntityType<TideLurkerEntity> TIDE_LURKER = register("tide_lurker",
            EntityType.Builder.of(TideLurkerEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(8));
    public static final EntityType<HollowWispEntity> HOLLOW_WISP = register("hollow_wisp",
            EntityType.Builder.of(HollowWispEntity::new, MobCategory.MONSTER).sized(0.4f, 0.8f).eyeHeight(0.51f).clientTrackingRange(8));
    public static final EntityType<GravecallerEntity> GRAVECALLER = register("gravecaller",
            EntityType.Builder.of(GravecallerEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(8));

    private static final TagKey<Biome> SNOWY = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_snowy"));

    private WorldMobs() {
    }

    public static void init() {
        MobEggs.init();
        FabricDefaultAttributeRegistry.register(GLIMMER_CRAWLER, GlimmerCrawlerEntity.attributes());
        FabricDefaultAttributeRegistry.register(BOG_HAG, BogHagEntity.attributes());
        FabricDefaultAttributeRegistry.register(FROST_REVENANT, FrostRevenantEntity.attributes());
        FabricDefaultAttributeRegistry.register(EMBER_IMP, EmberImpEntity.attributes());
        FabricDefaultAttributeRegistry.register(GROVE_SENTINEL, GroveSentinelEntity.attributes());
        FabricDefaultAttributeRegistry.register(TIDE_LURKER, TideLurkerEntity.attributes());
        FabricDefaultAttributeRegistry.register(HOLLOW_WISP, HollowWispEntity.attributes());
        FabricDefaultAttributeRegistry.register(GRAVECALLER, GravecallerEntity.attributes());

        // Where they may stand: on the ground in the dark, like most monsters (crawlers only below y 0).
        onGround(GLIMMER_CRAWLER, (type, level, reason, pos, random) -> pos.getY() < 0 && Monster.checkMonsterSpawnRules(type, level, reason, pos, random));
        onGround(BOG_HAG, Monster::checkMonsterSpawnRules);
        onGround(FROST_REVENANT, Monster::checkMonsterSpawnRules);
        onGround(EMBER_IMP, Monster::checkMonsterSpawnRules);
        onGround(GROVE_SENTINEL, Monster::checkMonsterSpawnRules);
        onGround(TIDE_LURKER, Monster::checkMonsterSpawnRules);
        onGround(HOLLOW_WISP, Monster::checkMonsterSpawnRules);
        onGround(GRAVECALLER, Monster::checkMonsterSpawnRules);

        // Where they spawn (weights next to vanilla's: zombies 95, skeletons and spiders 100, witches 5).
        spawn(BiomeSelectors.foundInOverworld(), GLIMMER_CRAWLER, 40, 1, 3);
        spawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP), BOG_HAG, 25, 1, 1);
        spawn(BiomeSelectors.tag(SNOWY), FROST_REVENANT, 40, 1, 2);
        spawn(BiomeSelectors.tag(BiomeTags.IS_BADLANDS), EMBER_IMP, 30, 1, 3);
        spawn(BiomeSelectors.foundInTheNether(), EMBER_IMP, 10, 1, 3);
        spawn(BiomeSelectors.tag(BiomeTags.IS_FOREST).and(BiomeSelectors.excludeByKey(Biomes.DARK_FOREST, Biomes.PALE_GARDEN)), GROVE_SENTINEL, 30, 1, 2);
        spawn(BiomeSelectors.tag(BiomeTags.IS_RIVER).or(BiomeSelectors.tag(BiomeTags.IS_BEACH)), TIDE_LURKER, 35, 1, 2);
        spawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW), HOLLOW_WISP, 15, 1, 2);
        spawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.PALE_GARDEN), GRAVECALLER, 35, 1, 1);

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0 || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            Entity attacker = source.getEntity();
            // Frost Revenant arrows: you start to freeze (as in powder snow).
            if (attacker instanceof FrostRevenantEntity && source.getDirectEntity() instanceof AbstractArrow) {
                entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 100, entity.getTicksFrozen() + 120));
                level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY(1), entity.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
            }
            // Grove Sentinel hits: rooted for two seconds.
            if (attacker instanceof GroveSentinelEntity && source.getDirectEntity() == attacker && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3), attacker);
                level.sendParticles(ParticleTypes.COMPOSTER, entity.getX(), entity.getY() + 0.1, entity.getZ(), 14, 0.4, 0.05, 0.4, 0);
            }
        });
    }

    private static <T extends Mob> void onGround(EntityType<T> type, SpawnPlacements.SpawnPredicate<T> predicate) {
        SpawnPlacementsInvoker.skyrpg$register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate);
    }

    private static void spawn(Predicate<BiomeSelectionContext> biomes, EntityType<?> type, int weight, int min, int max) {
        BiomeModifications.addSpawn(biomes, MobCategory.MONSTER, type, weight, min, max);
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
