package com.skyrpg.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dungeon traps ({@code trap} markers): arrows from wall slots, floor spikes, fire bursts and poison clouds. A trap
 * only fires while a player is within its radius, then waits {@code every} ticks. See docs/design/dungeons.md.
 */
final class DungeonTraps {
    /** When each trap may fire next (game time). Memory only: a restart just re-arms them. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private DungeonTraps() {
    }

    static void tick(ServerLevel level, Marker marker, DungeonMarkers.Info info) {
        long now = level.getGameTime();
        if (now < NEXT.getOrDefault(marker.getUUID(), 0L)) {
            return;
        }
        double radius = Dungeons.num(info.def(), "radius", 4);
        List<ServerPlayer> players = DungeonMarkers.playersNear(level, marker.position(), radius, radius);
        players.removeIf(player -> player.isCreative());
        if (players.isEmpty()) {
            return;
        }
        float damage = (float) Dungeons.num(info.def(), "damage", 4);
        boolean fired = switch (Dungeons.str(info.def(), "type", "spikes")) {
            case "arrows" -> arrows(level, marker, players, damage);
            case "flames" -> flames(level, marker, players, damage);
            case "poison" -> poison(level, marker, radius);
            default -> spikes(level, marker, players, damage);
        };
        if (fired) {
            NEXT.put(marker.getUUID(), now + (long) Dungeons.num(info.def(), "every", 40));
        }
    }

    /** Shoots at the nearest player it can see. */
    private static boolean arrows(ServerLevel level, Marker marker, List<ServerPlayer> players, float damage) {
        Vec3 from = marker.position().add(0, 0.5, 0);
        for (ServerPlayer player : players) {
            Vec3 to = player.getEyePosition();
            if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, marker)).getType() != HitResult.Type.MISS) {
                continue;
            }
            Vec3 aim = to.subtract(from);
            Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(damage / 1.8);
            arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.08, aim.z, 1.8f, 2.0f);
            level.addFreshEntity(arrow);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0f, 1.2f);
            return true;
        }
        return false;
    }

    /** Spikes jab up under everyone standing on the trap. */
    private static boolean spikes(ServerLevel level, Marker marker, List<ServerPlayer> players, float damage) {
        boolean hit = false;
        for (ServerPlayer player : players) {
            if (Math.abs(player.getY() - marker.getY()) > 1.5) {
                continue;
            }
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.3, 0.05, 0.3, 0.4);
            player.hurtServer(level, level.damageSources().stalagmite(), damage);
            hit = true;
        }
        if (hit) {
            level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 1.2f, 0.7f);
        }
        return hit;
    }

    /** A burst of fire from the trap's spot that sets nearby players alight. */
    private static boolean flames(ServerLevel level, Marker marker, List<ServerPlayer> players, float damage) {
        level.sendParticles(ParticleTypes.FLAME, marker.getX(), marker.getY() + 0.5, marker.getZ(), 40, 0.6, 0.8, 0.6, 0.06);
        level.sendParticles(ParticleTypes.LAVA, marker.getX(), marker.getY() + 0.5, marker.getZ(), 6, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0f, 0.6f);
        for (ServerPlayer player : players) {
            player.igniteForSeconds(3);
            player.hurtServer(level, level.damageSources().inFire(), damage);
        }
        return true;
    }

    /** A short-lived poison cloud on the trap. */
    private static boolean poison(ServerLevel level, Marker marker, double radius) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, marker.getX(), marker.getY(), marker.getZ());
        cloud.setRadius((float) Math.min(radius, 3.5));
        cloud.setRadiusOnUse(0);
        cloud.setRadiusPerTick(0);
        cloud.setDuration(60);
        cloud.setWaitTime(5);
        Holder<net.minecraft.world.effect.MobEffect> effect = MobEffects.POISON;
        cloud.addEffect(new MobEffectInstance(effect, 80, 1));
        level.addFreshEntity(cloud);
        level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 0.5f);
        return true;
    }
}
