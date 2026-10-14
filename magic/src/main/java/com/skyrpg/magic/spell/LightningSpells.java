package com.skyrpg.magic.spell;

import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.SpellDamage;
import com.skyrpg.magic.Statuses;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Chain Lightning and Thunderclap. The bolts are particle arcs plus a visual-only lightning entity: nothing catches fire. */
final class LightningSpells {
    private LightningSpells() {
    }

    static boolean chainLightning(CastContext c) {
        double range = c.param("range", 26);
        Targeting.Aim aim = Targeting.aim(c.caster, range);
        LivingEntity first = aim.entity() != null && Targeting.canHit(c.caster, aim.entity()) ? aim.entity() : Targeting.assist(c.caster, range, 0.85);
        if (first == null) {
            // Nothing to hit: the bolt still leaps to where you aim, for show, but costs nothing.
            Vec3 end = aim.point();
            Fx.arc(c.level, c.caster.getRandom(), ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, c.muzzle(), end);
            c.sound(c.muzzle(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.2f, 2.0f);
            SpellRunner.tell(c, "No target in sight");
            return false;
        }
        int targets = (int) c.param("targets", 4) + c.wand.chainBonus();
        double jump = c.param("jump_range", 7);
        double damage = c.damage("damage", 52);
        List<LivingEntity> chain = new ArrayList<>();
        Set<LivingEntity> used = new HashSet<>();
        chain.add(first);
        used.add(first);
        while (chain.size() < targets) {
            LivingEntity from = chain.getLast();
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : Targeting.around(c.level, c.caster, Targeting.center(from), jump)) {
                double distance = candidate.distanceToSqr(from);
                if (!used.contains(candidate) && distance < best) {
                    best = distance;
                    next = candidate;
                }
            }
            if (next == null) {
                break;
            }
            chain.add(next);
            used.add(next);
        }
        Vec3 previous = c.muzzle();
        int delay = 0;
        for (int i = 0; i < chain.size(); i++) {
            LivingEntity target = chain.get(i);
            Vec3 from = previous;
            Vec3 to = Targeting.center(target);
            int hop = i;
            Tasks.later(Math.max(1, delay), () -> strike(c, from, target, damage, hop));
            previous = to;
            delay += 2;
        }
        c.sound(c.muzzle(), SoundEvents.TRIDENT_THUNDER, 0.5f, 1.8f);
        return true;
    }

    private static void strike(CastContext c, Vec3 from, LivingEntity target, double damage, int hop) {
        if (!target.isAlive()) {
            return;
        }
        ServerLevel level = c.level;
        Vec3 to = Targeting.center(target);
        Fx.arc(level, c.caster.getRandom(), ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, from, to);
        Fx.arc(level, c.caster.getRandom(), ParticleTypes.ELECTRIC_SPARK, ParticleTypes.ELECTRIC_SPARK, from, to);
        Fx.play(level, "lightning_hit", to);
        c.sound(to, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.7f + 0.1f * hop);
        c.hurt(target, damage);
        Statuses.slow(target, 1, 0.8);
        if (hop == 0) {
            c.sound(to, SoundEvents.TRIDENT_THUNDER, 0.7f, 1.4f);
        }
    }

    static boolean thunderclap(CastContext c) {
        double radius = c.param("radius", 6);
        double damage = c.damage("damage", 62);
        int stun = (int) (c.param("stun_seconds", 1.4) * 20);
        Vec3 center = c.caster.position().add(0, 0.2, 0);
        ServerLevel level = c.level;
        Fx.play(level, "thunderclap", center);
        Fx.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.1f, 1.3f);
        Fx.sound(level, center, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
        Fx.shake(level, center, 32, 0.9f, 12);
        List<LivingEntity> victims = Targeting.around(level, c.caster, center.add(0, 1, 0), radius);
        int bolts = 0;
        for (LivingEntity victim : victims) {
            c.hurt(victim, damage);
            SpellDamage.push(victim, center, c.param("knockback", 1.3));
            Statuses.root(victim, stun, level.getServer());
            Fx.play(level, "stun_stars", victim.position().add(0, victim.getBbHeight() + 0.2, 0));
            if (bolts++ < 4) {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
                    level.addFreshEntity(bolt);
                }
            }
        }
        return true;
    }
}
