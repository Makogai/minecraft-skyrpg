package com.skyrpg.stats;

import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * How Strength, Crit Chance, Crit Damage and Defense change damage. Called (through a mixin) at the start
 * of every {@code LivingEntity.hurtServer}, before vanilla's armor and invulnerability handling.
 */
public final class CombatStats {
    private CombatStats() {
    }

    public static float modifyDamage(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
        if (amount <= 0) {
            return amount;
        }
        double damage = amount;
        Crits.set(false);

        // A player's own melee hit: Strength, then a crit roll.
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK) && target != attacker) {
            damage *= 1 + Math.max(0, Stats.get(attacker, StatTypes.STRENGTH)) / 100;
            if (attacker.getRandom().nextDouble() * 100 < Stats.get(attacker, StatTypes.CRIT_CHANCE)) {
                damage *= 1 + Math.max(0, Stats.get(attacker, StatTypes.CRIT_DAMAGE)) / 100;
                Crits.set(true);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0.25);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                        attacker.getSoundSource(), 1.0f, 1.0f);
            }
        }

        // Other mods' multipliers (slayer bosses tougher than vanilla's health cap, slayer weapons).
        damage *= IncomingDamage.multiplier(target, source);

        // Defense: 100 defense halves damage, 300 quarters it, and so on.
        if (target instanceof ServerPlayer defender && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            double defense = Math.max(0, Stats.get(defender, StatTypes.DEFENSE));
            damage *= 100 / (100 + defense);
        }
        return (float) damage;
    }
}
