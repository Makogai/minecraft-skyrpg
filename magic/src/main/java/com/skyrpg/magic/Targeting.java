package com.skyrpg.magic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Who a spell may hit and where the caster is aiming. */
public final class Targeting {
    private static MethodHandle sameParty;
    private static boolean partyLookedUp;

    private Targeting() {
    }

    /** What a spell hit by aim: an entity, or a point on a block (or the end of the range). */
    public record Aim(Vec3 point, LivingEntity entity, boolean hitBlock) {
    }

    /** Whether the party mod says these two players are in the same party (false without the mod). */
    public static boolean inSameParty(UUID a, UUID b) {
        if (!partyLookedUp) {
            partyLookedUp = true;
            try {
                Class<?> parties = Class.forName("com.skyrpg.party.Parties");
                sameParty = MethodHandles.publicLookup().findStatic(parties, "sameParty", MethodType.methodType(boolean.class, UUID.class, UUID.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                sameParty = null;
            }
        }
        if (sameParty == null) {
            return false;
        }
        try {
            return (boolean) sameParty.invokeExact(a, b);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Anything a spell may damage: not you, not your party, your pets, villagers, armour stands or things that cannot be hurt. */
    public static boolean canHit(ServerPlayer caster, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == caster || !living.isAlive() || living.isSpectator()
                || entity instanceof ArmorStand || entity instanceof AbstractVillager || living.isInvulnerable()) {
            return false;
        }
        if (living instanceof TamableAnimal pet && pet.isOwnedBy(caster)) {
            return false;
        }
        if (living instanceof Player other) {
            return caster.canHarmPlayer(other) && !inSameParty(caster.getUUID(), other.getUUID());
        }
        return true;
    }

    /** What an area spell or a homing missile goes for: hostile mobs, mobs that are after someone, training dummies, other players (pvp). */
    public static boolean hostile(ServerPlayer caster, Entity entity) {
        if (!canHit(caster, entity)) {
            return false;
        }
        return entity instanceof Enemy || entity instanceof Player || entity instanceof net.minecraft.world.entity.decoration.Mannequin
                || (entity instanceof Mob mob && mob.getTarget() != null);
    }

    /** Hostile things within a sphere, nearest first. */
    public static List<LivingEntity> around(ServerLevel level, ServerPlayer caster, Vec3 center, double radius) {
        List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> hostile(caster, e) && e.position().add(0, e.getBbHeight() / 2, 0).distanceToSqr(center) <= (radius + e.getBbWidth() / 2) * (radius + e.getBbWidth() / 2)));
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return found;
    }

    /** The middle of an entity: where bolts and arcs aim. */
    public static Vec3 center(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.55, 0);
    }

    /** What the caster's crosshair is on within {@code range}: the first hittable entity or block in the way. */
    public static Aim aim(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double limit = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
        LivingEntity best = null;
        double bestDistance = limit;
        Vec3 bestPoint = null;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> canHit(caster, e))) {
            var hit = candidate.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                    bestPoint = hit.get();
                }
            }
        }
        if (best != null) {
            return new Aim(bestPoint, best, false);
        }
        return new Aim(block.getType() == HitResult.Type.MISS ? end : block.getLocation(), null, block.getType() != HitResult.Type.MISS);
    }

    /** The point on the ground at (or just below) an aim point, for spells that land on the floor. */
    public static Vec3 ground(ServerLevel level, ServerPlayer caster, Vec3 point) {
        BlockHitResult down = level.clip(new ClipContext(point.add(0, 1.5, 0), point.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return down.getType() == HitResult.Type.MISS ? point : down.getLocation();
    }

    /** The best target for a homing spell or a chain: the nearest hostile thing close to where the caster is looking, within {@code range}. */
    public static LivingEntity assist(ServerPlayer caster, double range, double minDot) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestScore = -2;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range), e -> hostile(caster, e))) {
            Vec3 to = center(candidate).subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 0.5) {
                continue;
            }
            double dot = to.scale(1 / distance).dot(look);
            if (dot < minDot || !caster.hasLineOfSight(candidate)) {
                continue;
            }
            double score = dot - distance / (range * 4);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
