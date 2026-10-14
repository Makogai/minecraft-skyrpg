package com.skyrpg.magic;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.magic.mixin.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Spell damage. It is a normal {@code indirect_magic} hit with the caster as its cause, so everything that listens to damage
 * already counts it for the caster: damage numbers, combat XP, loot, slayer progress, kill credit, and the party mod's
 * no-friendly-fire rule (it resolves the cause). It is not a player melee hit, so Strength and melee crits do not apply: spells
 * scale with Intelligence instead (see {@link CastContext#power}).
 */
public final class SpellDamage {
    private SpellDamage() {
    }

    /**
     * Hurts {@code target} for {@code amount} SkyRPG damage (the unit of weapon damage: 5 per vanilla point), ignoring its
     * invulnerability frames. Returns whether it hurt.
     */
    public static boolean hurt(ServerPlayer caster, LivingEntity target, double amount) {
        if (!(target.level() instanceof ServerLevel level) || amount <= 0) {
            return false;
        }
        DamageSource source = level.damageSources().indirectMagic(caster, caster);
        ((EntityAccessor) target).skyrpgMagic$setInvulnerableTime(0);
        return target.hurtServer(level, source, (float) (amount / StatTypes.HEALTH_SCALE));
    }

    /** Pushes a target away from a point (horizontally, with a little lift). */
    public static void push(LivingEntity target, Vec3 from, double strength) {
        Vec3 away = target.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(0.0, 0.0, 1.0);
        }
        away = away.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(away.x, 0.25 + strength * 0.15, away.z));
        target.syncVelocity = true;
    }
}
