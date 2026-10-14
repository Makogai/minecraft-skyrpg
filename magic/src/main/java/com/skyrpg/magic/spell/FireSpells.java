package com.skyrpg.magic.spell;

import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.SpellDamage;
import com.skyrpg.magic.Statuses;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Firebolt, Fireball and Meteor. Explosions here hurt and burn creatures only: they never break blocks or start fires. */
final class FireSpells {
    private FireSpells() {
    }

    static boolean firebolt(CastContext c) {
        double speed = c.param("speed", 1.9);
        double range = c.param("range", 36);
        Vec3 direction = SpellRunner.aimDirection(c, range);
        double damage = c.damage("damage", 35);
        double burnDps = c.param("burn_dps", 8) * c.power();
        double burnSeconds = c.param("burn_seconds", 3);
        Bolt.launch(c, c.muzzle(), direction.scale(speed), (int) Math.ceil(range / speed), 0.35, "firebolt_trail", (bolt, entity, point) -> {
            Fx.play(c.level, "firebolt_hit", point);
            c.sound(point, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.6f);
            if (entity != null) {
                c.hurt(entity, damage);
                Statuses.burn(c.caster, entity, burnDps, burnSeconds, c.level.getServer());
                SpellDamage.push(entity, c.caster.position(), 0.25);
            }
            return true;
        });
        c.sound(c.muzzle(), SoundEvents.BLAZE_SHOOT, 0.7f, 1.7f);
        return true;
    }

    static boolean fireball(CastContext c) {
        double speed = c.param("speed", 1.2);
        double range = c.param("range", 40);
        Vec3 direction = SpellRunner.aimDirection(c, range);
        double radius = c.scaled(c.param("radius", 3), c.param("radius_charged", 5.5));
        double damage = c.damage("damage", 70) * c.scaled(1, c.param("charge_damage", 2.0));
        double burnDps = c.param("burn_dps", 14) * c.power();
        boolean big = c.charge >= 0.6;
        Bolt.launch(c, c.muzzle(), direction.scale(speed), (int) Math.ceil(range / speed), 0.55, "fireball_trail", (bolt, entity, point) -> {
            explode(c, point, radius, damage, burnDps, c.param("burn_seconds", 4), entity, big ? "fireball_explosion_big" : "fireball_explosion");
            if (big) {
                Fx.shake(c.level, point, 28, 0.8f, 10);
            } else {
                Fx.shake(c.level, point, 16, 0.3f, 6);
            }
            return true;
        }).explodeAtEnd();
        c.sound(c.muzzle(), SoundEvents.FIRECHARGE_USE, 1.0f, (float) (1.0 - c.charge * 0.4));
        c.sound(c.muzzle(), SoundEvents.BLAZE_SHOOT, 0.6f, 0.7f);
        return true;
    }

    static boolean meteor(CastContext c) {
        double range = c.param("range", 42);
        Targeting.Aim aim = Targeting.aim(c.caster, range);
        Vec3 target = aim.entity() != null ? aim.entity().position() : Targeting.ground(c.level, c.caster, aim.point());
        int delay = (int) c.param("delay_ticks", 26);
        double radius = c.scaled(c.param("radius", 5), c.param("radius_charged", 8));
        double damage = c.damage("damage", 170) * c.scaled(1, c.param("charge_damage", 2.0));
        double burnDps = c.param("burn_dps", 18) * c.power();
        // The meteor comes in along a slanted line from high up, so the streak across the sky points at where it lands.
        Vec3 start = target.add(-14, 46, -9);
        int[] age = {0};
        c.sound(c.muzzle(), SoundEvents.EVOKER_CAST_SPELL, 1.0f, 0.6f);
        c.sound(target, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f);
        Fx.play(c.level, "meteor_marker", target);
        Tasks.add(() -> {
            age[0]++;
            double t = Math.min(1.0, age[0] / (double) delay);
            double fall = t * t;
            Vec3 pos = start.lerp(target, fall);
            ServerLevel level = c.level;
            Fx.play(level, "meteor_trail", pos);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 4, 0.5, 0.5, 0.5, 0.02);
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 6, 0.6, 0.6, 0.6, 0.05);
            if (age[0] % 4 == 0 && age[0] < delay) {
                Fx.play(level, "meteor_marker", target);
                Fx.sound(level, pos, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
            }
            if (age[0] < delay) {
                return false;
            }
            explode(c, target, radius, damage, burnDps, c.param("burn_seconds", 5), null, "meteor_impact");
            Fx.sound(level, target, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.55f);
            Fx.sound(level, target, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
            Fx.shake(level, target, 48, 1.4f, 16);
            return true;
        });
        return true;
    }

    /** A fiery blast at a point: hurts and burns every creature the caster may hit within {@code radius}, less towards the edge. */
    static void explode(CastContext c, Vec3 point, double radius, double damage, double burnDps, double burnSeconds, LivingEntity direct, String effect) {
        ServerLevel level = c.level;
        Fx.play(level, effect, point);
        Fx.sound(level, point, SoundEvents.GENERIC_EXPLODE, 1.4f, 1.1f);
        Set<LivingEntity> victims = new HashSet<>(Targeting.around(level, c.caster, point, radius));
        if (direct != null && Targeting.canHit(c.caster, direct)) {
            victims.add(direct);
        }
        for (LivingEntity victim : victims) {
            double distance = Targeting.center(victim).distanceTo(point);
            double falloff = victim == direct ? 1.0 : Math.max(0.55, 1.0 - 0.45 * distance / Math.max(0.5, radius));
            c.hurt(victim, damage * falloff);
            Statuses.burn(c.caster, victim, burnDps, burnSeconds, level.getServer());
            SpellDamage.push(victim, point, 0.7 * falloff + 0.2);
        }
    }
}
