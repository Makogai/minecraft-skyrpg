package com.skyrpg.magic.spell;

import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.ManaShield;
import com.skyrpg.magic.SpellDamage;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Arcane Missiles, Blink and Mana Shield. */
final class ArcaneSpells {
    private ArcaneSpells() {
    }

    static boolean missiles(CastContext c) {
        int count = (int) c.param("missiles", 3);
        double damage = c.damage("damage", 30);
        double speed = c.param("speed", 1.15);
        double homing = c.param("homing", 0.2);
        LivingEntity lock = Targeting.assist(c.caster, 30, 0.6);
        Vec3 base = SpellRunner.aimDirection(c, 40);
        c.sound(c.muzzle(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.9f, 1.5f);
        for (int i = 0; i < count; i++) {
            int index = i;
            Tasks.later(1 + i * 3, () -> {
                if (!c.caster.isAlive()) {
                    return;
                }
                // Fanned out to the sides and up, then bending in on their target.
                double side = (index - (count - 1) / 2.0) * 0.55;
                Vec3 right = base.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 direction = base.add(right.scale(side)).add(0, 0.25 + 0.1 * (index % 2), 0).normalize();
                Bolt.launch(c, c.muzzle(), direction.scale(speed), 70, 0.45, "arcane_trail", (bolt, entity, point) -> {
                    Fx.play(c.level, "arcane_hit", point);
                    c.sound(point, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7f, 1.5f);
                    if (entity != null) {
                        c.hurt(entity, damage);
                        SpellDamage.push(entity, bolt.pos.subtract(bolt.velocity), 0.15);
                    }
                    return true;
                }).homing(homing, lock);
                c.sound(c.muzzle(), SoundEvents.EVOKER_CAST_SPELL, 0.5f, 1.8f + 0.15f * index);
            });
        }
        return true;
    }

    static boolean blink(CastContext c) {
        ServerLevel level = c.level;
        double range = c.param("range", 8);
        Vec3 look = c.caster.getLookAngle();
        Vec3 eye = c.caster.getEyePosition();
        BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(range + 0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
        double reach = wall.getType() == HitResult.Type.MISS ? range : Math.max(0, eye.distanceTo(wall.getLocation()) - 0.6);
        Vec3 from = c.caster.position();
        Vec3 landing = null;
        // The furthest spot along the line where you fit and have something to stand on.
        for (double d = Math.min(range, reach); d >= 1.5; d -= 0.5) {
            Vec3 spot = from.add(look.scale(d));
            Vec3 safe = settle(c, spot);
            if (safe != null) {
                landing = safe;
                break;
            }
        }
        if (landing == null) {
            SpellRunner.tell(c, "No room to blink");
            return false;
        }
        Fx.play(level, "blink_flash", from.add(0, 1, 0));
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.5f);
        c.caster.teleportTo(landing.x, landing.y, landing.z);
        c.caster.resetFallDistance();
        c.caster.setDeltaMovement(look.scale(0.25));
        c.caster.syncVelocity = true;
        Fx.play(level, "blink_flash", landing.add(0, 1, 0));
        Fx.sound(level, landing, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8f, 1.6f);
        Fx.line(level, ParticleTypes.PORTAL, from.add(0, 1, 0), landing.add(0, 1, 0), 0.3, 0.15);
        return true;
    }

    /** A standing spot at or just below this point where the player's box is free, there is a floor and it is not lava or water; null if none. */
    private static Vec3 settle(CastContext c, Vec3 spot) {
        ServerLevel level = c.level;
        for (int drop = 0; drop <= 4; drop++) {
            Vec3 feet = spot.add(0, -drop, 0);
            var box = c.caster.getBoundingBox().move(feet.subtract(c.caster.position()));
            if (!level.noCollision(c.caster, box)) {
                continue;
            }
            BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
            if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                continue;
            }
            if (level.getFluidState(BlockPos.containing(feet)).is(FluidTags.LAVA) || level.getFluidState(below).is(FluidTags.LAVA)) {
                return null;
            }
            return new Vec3(feet.x, Math.floor(feet.y - 0.2) + 1.0, feet.z);
        }
        return null;
    }

    static boolean manaShield(CastContext c) {
        double seconds = c.param("duration", 8);
        ManaShield.activate(c.caster, seconds, c.param("mana_per_damage", 0.5));
        Vec3 at = c.caster.position();
        Fx.play(c.level, "mana_shield_cast", at.add(0, 1, 0));
        Fx.play(c.caster, "mana_shield_aura");
        Fx.sound(c.level, at, SoundEvents.BEACON_ACTIVATE, 0.9f, 1.5f);
        Fx.sound(c.level, at, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
        return true;
    }
}
