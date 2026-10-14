package com.skyrpg.magic;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One cast: who, with which spell and wand, and how charged (0 to 1, always 0 for a spell that cannot be charged). Spell code reads
 * its numbers from {@code def.params} through here, so the wand's bonuses and the caster's Intelligence apply in one place.
 */
public final class CastContext {
    public final ServerPlayer caster;
    public final ServerLevel level;
    public final SpellDef def;
    public final WandDef wand;
    public final double charge;

    public CastContext(ServerPlayer caster, SpellDef def, WandDef wand, double charge) {
        this.caster = caster;
        this.level = caster.level();
        this.def = def;
        this.wand = wand == null ? WandDef.NONE : wand;
        this.charge = Math.max(0, Math.min(1, charge));
    }

    /** Intelligence and the wand's element bonus: what every damage and healing number is multiplied by. */
    public double power() {
        double intelligence = Stats.available() ? Math.max(0, Stats.get(caster, StatTypes.INTELLIGENCE)) : 0;
        return (1 + intelligence / 100) * (1 + wand.power(def.element()));
    }

    public double param(String key, double fallback) {
        return def.param(key, fallback);
    }

    /** A damage number from the spell's data, with power applied. */
    public double damage(String key, double fallback) {
        return param(key, fallback) * power();
    }

    /** {@code min} at no charge up to {@code max} fully charged. */
    public double scaled(double min, double max) {
        return min + (max - min) * charge;
    }

    /** Where the wand's tip is: a little in front of the caster's hand. */
    public Vec3 muzzle() {
        Vec3 look = caster.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return caster.getEyePosition().add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.28, 0);
    }

    /** Hurts a target for this spell: {@code amount} is already scaled. */
    public boolean hurt(LivingEntity target, double amount) {
        return SpellDamage.hurt(caster, target, amount);
    }

    public void sound(Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Fx.sound(level, at, sound, volume, pitch);
    }

    public void sound(Vec3 at, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
        Fx.sound(level, at, sound, volume, pitch);
    }
}
