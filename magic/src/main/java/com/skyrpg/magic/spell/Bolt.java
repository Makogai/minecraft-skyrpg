package com.skyrpg.magic.spell;

import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A spell projectile: not an entity, just a point the server moves each tick (in small steps, so nothing is skipped), with the
 * trail drawn as VFX. It stops at blocks, and hits entities the caster may hit; what happens on a hit is the spell's business.
 */
final class Bolt implements Tasks.Task {
    /** Called on every hit. {@code entity} is null for a block hit (or the end of the range, with {@link #explodeAtEnd}). Return true to end the bolt. */
    @FunctionalInterface
    interface Impact {
        boolean hit(Bolt bolt, LivingEntity entity, Vec3 point);
    }

    final CastContext ctx;
    final ServerLevel level;
    Vec3 pos;
    Vec3 velocity;
    private final double speed;
    private final int maxTicks;
    private final double radius;
    private final String trail;
    private final Impact impact;
    private double homing;
    private double gravity;
    private boolean explodeAtEnd;
    private Consumer<Vec3> stepFx;
    private LivingEntity locked;
    private final Set<UUID> hit = new HashSet<>();
    private int age;

    private Bolt(CastContext ctx, Vec3 from, Vec3 velocity, int maxTicks, double radius, String trail, Impact impact) {
        this.ctx = ctx;
        this.level = ctx.level;
        this.pos = from;
        this.velocity = velocity;
        this.speed = velocity.length();
        this.maxTicks = maxTicks;
        this.radius = radius;
        this.trail = trail;
        this.impact = impact;
    }

    static Bolt launch(CastContext ctx, Vec3 from, Vec3 velocity, int maxTicks, double radius, String trail, Impact impact) {
        Bolt bolt = new Bolt(ctx, from, velocity, maxTicks, radius, trail, impact);
        Tasks.add(bolt);
        return bolt;
    }

    Bolt homing(double strength, LivingEntity lock) {
        this.homing = strength;
        this.locked = lock;
        return this;
    }

    Bolt gravity(double perTick) {
        this.gravity = perTick;
        return this;
    }

    Bolt explodeAtEnd() {
        this.explodeAtEnd = true;
        return this;
    }

    Bolt stepFx(Consumer<Vec3> fx) {
        this.stepFx = fx;
        return this;
    }

    /** Whether this bolt already hit the entity (a piercing bolt hits each one once). */
    boolean alreadyHit(LivingEntity entity) {
        return hit.contains(entity.getUUID());
    }

    @Override
    public boolean tick() {
        if (!ctx.caster.isAlive() || ctx.caster.isRemoved() || !pos.isFinite()) {
            return true;
        }
        if (homing > 0) {
            steer();
        }
        if (gravity != 0) {
            velocity = velocity.add(0, -gravity, 0);
        }
        int steps = Math.max(1, (int) Math.ceil(velocity.length() / 0.5));
        Vec3 step = velocity.scale(1.0 / steps);
        for (int i = 0; i < steps; i++) {
            Vec3 from = pos;
            Vec3 to = from.add(step);
            BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster));
            double blockDistance = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceToSqr(block.getLocation());
            LivingEntity nearest = null;
            Vec3 nearestPoint = null;
            double nearestDistance = Double.MAX_VALUE;
            AABB sweep = new AABB(from, to).inflate(radius + 0.5);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> !hit.contains(e.getUUID()) && Targeting.canHit(ctx.caster, e))) {
                AABB box = candidate.getBoundingBox().inflate(radius);
                Optional<Vec3> point = box.contains(from) ? Optional.of(from) : box.clip(from, to);
                if (point.isPresent()) {
                    double distance = from.distanceToSqr(point.get());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate;
                        nearestPoint = point.get();
                    }
                }
            }
            if (nearest != null && nearestDistance <= blockDistance) {
                hit.add(nearest.getUUID());
                pos = nearestPoint;
                if (impact.hit(this, nearest, nearestPoint)) {
                    return true;
                }
            } else if (block.getType() != HitResult.Type.MISS) {
                pos = block.getLocation();
                impact.hit(this, null, pos);
                return true;
            }
            pos = to;
            if (stepFx != null) {
                stepFx.accept(pos);
            }
        }
        if (trail != null) {
            Fx.play(level, trail, pos);
        }
        if (++age >= maxTicks) {
            if (explodeAtEnd) {
                impact.hit(this, null, pos);
            }
            return true;
        }
        return false;
    }

    /** Bends the flight towards a target: the locked one while it lives, otherwise the best hostile thing ahead. */
    private void steer() {
        if (locked == null || !locked.isAlive() || locked.isRemoved()) {
            locked = null;
            if (age % 3 == 0) {
                Vec3 heading = velocity.normalize();
                double best = 0.25;
                for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(14), e -> !hit.contains(e.getUUID()) && Targeting.hostile(ctx.caster, e))) {
                    Vec3 to = Targeting.center(candidate).subtract(pos);
                    double distance = to.length();
                    if (distance < 0.5) {
                        continue;
                    }
                    double score = to.scale(1 / distance).dot(heading) - distance / 60;
                    if (score > best) {
                        best = score;
                        locked = candidate;
                    }
                }
            }
        }
        if (locked != null) {
            Vec3 want = Targeting.center(locked).subtract(pos).normalize();
            velocity = velocity.normalize().scale(1 - homing).add(want.scale(homing)).normalize().scale(speed);
        }
    }
}
