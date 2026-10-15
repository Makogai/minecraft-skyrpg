package com.skyrpg.crates;

import com.google.gson.JsonObject;
import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.stats.StatTypes;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crate scythes' abilities, both a full swing round you: Reap (Reaper's Scythe) cuts every monster within reach and
 * harvests the grown crops around you; Soul Sweep (Soulreaper) does it bigger, sets them on soul fire and heals you a
 * little for each one hit. Crops go through the normal harvest (so the scythe's replanting and fortune apply). Numbers
 * are the item definitions' ability params: radius, damage, max_targets, harvest (square radius), fire (s), heal.
 */
final class ScytheAbilities {
    private ScytheAbilities() {
    }

    static void register() {
        AbilityHandlers.register("skyrpg_crates:reap", (player, params) -> sweep(player, params, false));
        AbilityHandlers.register("skyrpg_crates:soul_sweep", (player, params) -> sweep(player, params, true));
    }

    private static boolean sweep(ServerPlayer player, JsonObject params, boolean soul) {
        ServerLevel level = player.level();
        double radius = number(params, "radius", 4.5);
        float damage = (float) (number(params, "damage", 50) / StatTypes.HEALTH_SCALE);
        int maxTargets = (int) number(params, "max_targets", 10);
        Vec3 center = player.position();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2.5, radius),
                        mob -> mob instanceof Enemy && mob.isAlive() && mob != player && mob.position().distanceTo(center) <= radius + mob.getBbWidth() / 2)
                .stream().sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(player))).limit(maxTargets).toList();
        for (LivingEntity mob : targets) {
            mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
            Vec3 away = mob.position().subtract(center).normalize();
            double resist = 1 - Math.min(1, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            mob.push(away.x * 0.6 * resist, 0.25 * resist, away.z * 0.6 * resist);
            mob.syncVelocity = true;
            Vec3 at = mob.getBoundingBox().getCenter();
            level.sendParticles(soul ? ParticleTypes.SOUL : ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.4, 0.3, 0.05);
            if (soul) {
                mob.setRemainingFireTicks((int) (number(params, "fire", 4) * 20));
            }
        }
        if (soul && !targets.isEmpty()) {
            float heal = (float) (player.getMaxHealth() * number(params, "heal", 0.03) * targets.size());
            player.heal(Math.min(heal, player.getMaxHealth() * 0.3f));
        }

        // The swing: a ring of sweeps (or soul fire) all the way round.
        int points = soul ? 28 : 16;
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            double r = radius * 0.75;
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, center.y + 1, z, 1, 0, 0, 0, 0);
            }
            if (soul) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.y + 0.3, z, 3, 0.15, 0.1, 0.15, 0.02);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(angle) * radius, center.y + 0.2,
                        center.z + Math.sin(angle) * radius, 2, 0.1, 0.05, 0.1, 0.01);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, soul ? 0.6f : 0.85f);
        if (soul) {
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.4f, 1.6f);
        }

        harvest(player, (int) number(params, "harvest", 0));
        return true;
    }

    /** Breaks every grown crop in the square around you as if you'd harvested it with the scythe. */
    private static void harvest(ServerPlayer player, int range) {
        if (range <= 0) {
            return;
        }
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-range, -1, -range), feet.offset(range, 1, range))) {
            BlockState state = player.level().getBlockState(pos);
            if (grown(state)) {
                player.gameMode.destroyBlock(pos.immutable());
            }
        }
    }

    private static boolean grown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= 3;
    }

    private static double number(JsonObject params, String key, double fallback) {
        return params != null && params.has(key) ? params.get(key).getAsDouble() : fallback;
    }
}
