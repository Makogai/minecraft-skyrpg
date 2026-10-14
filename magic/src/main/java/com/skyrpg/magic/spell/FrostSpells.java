package com.skyrpg.magic.spell;

import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.Statuses;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Frost Shard and Blizzard. Frost wands make the slows last longer. */
final class FrostSpells {
    private FrostSpells() {
    }

    static boolean frostShard(CastContext c) {
        double speed = c.param("speed", 2.3);
        double range = c.param("range", 34);
        Vec3 direction = SpellRunner.aimDirection(c, range);
        double damage = c.damage("damage", 32);
        double slowSeconds = c.param("slow_seconds", 2.5) * (1 + c.wand.slowBonus());
        int[] pierce = {(int) c.param("pierce", 3)};
        Bolt.launch(c, c.muzzle(), direction.scale(speed), (int) Math.ceil(range / speed), 0.4, "frost_trail", (bolt, entity, point) -> {
            Fx.play(c.level, "frost_hit", point);
            c.sound(point, SoundEvents.GLASS_BREAK, 0.45f, 1.9f);
            if (entity == null) {
                return true;
            }
            c.hurt(entity, damage);
            Statuses.slow(entity, (int) c.param("slow_level", 2), slowSeconds);
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 40, entity.getTicksFrozen() + 60));
            return --pierce[0] <= 0;
        });
        c.sound(c.muzzle(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
        c.sound(c.muzzle(), SoundEvents.PLAYER_HURT_FREEZE, 0.5f, 1.5f);
        return true;
    }

    static boolean blizzard(CastContext c) {
        Targeting.Aim aim = Targeting.aim(c.caster, c.param("range", 30));
        Vec3 center = aim.entity() != null ? aim.entity().position() : Targeting.ground(c.level, c.caster, aim.point());
        double radius = c.param("radius", 5);
        int duration = (int) (c.param("duration", 6) * 20);
        double damage = c.damage("damage", 24);
        double slowSeconds = 1.5 * (1 + c.wand.slowBonus());
        ServerLevel level = c.level;
        Fx.play(level, "blizzard_burst", center);
        Fx.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
        Fx.sound(level, center, SoundEvents.GLASS_BREAK, 0.7f, 0.6f);
        int[] age = {0};
        Tasks.add(() -> {
            if (!c.caster.isAlive() && age[0] > 0) {
                return true;
            }
            if (age[0] % 3 == 0) {
                Fx.play(level, "blizzard_field", center);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 4, center.z, 10, radius * 0.55, 1.5, radius * 0.55, 0.08);
            if (age[0] % 20 == 0) {
                Fx.sound(level, center, SoundEvents.SNOW_BREAK, 0.8f, 0.5f);
            }
            if (age[0] % 10 == 5) {
                for (LivingEntity victim : Targeting.around(level, c.caster, center.add(0, 1, 0), radius)) {
                    c.hurt(victim, damage);
                    Statuses.slow(victim, 2, slowSeconds);
                    victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze() + 20, victim.getTicksFrozen() + 40));
                    level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
                }
            }
            return ++age[0] >= duration;
        });
        return true;
    }
}
