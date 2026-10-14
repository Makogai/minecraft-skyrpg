package com.skyrpg.magic.spell;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Fx;
import com.skyrpg.magic.Statuses;
import com.skyrpg.magic.Targeting;
import com.skyrpg.magic.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Healing Pulse and Entangle. */
final class NatureSpells {
    private NatureSpells() {
    }

    static boolean healingPulse(CastContext c) {
        ServerLevel level = c.level;
        double radius = c.param("radius", 8);
        float heal = (float) (c.damage("heal", 60) / StatTypes.HEALTH_SCALE);
        Vec3 center = c.caster.position();
        Fx.play(level, "healing_pulse", center.add(0, 0.1, 0));
        Fx.sound(level, center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        Fx.sound(level, center, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5f, 1.8f);
        int healed = 0;
        healOne(c, c.caster, heal);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, new AABB(center, center).inflate(radius),
                p -> p != c.caster && p.isAlive() && p.position().distanceToSqr(center) <= radius * radius && Targeting.inSameParty(c.caster.getUUID(), p.getUUID()))) {
            healOne(c, other, heal);
            other.sendOverlayMessage(Msg.text(c.caster.getName().getString() + " healed you", ChatFormatting.GREEN));
            healed++;
        }
        c.caster.sendOverlayMessage(Msg.text("Healing Pulse" + (healed > 0 ? ": you and " + healed + " party member" + (healed > 1 ? "s" : "") : ""), ChatFormatting.GREEN));
        return true;
    }

    private static void healOne(CastContext c, ServerPlayer target, float amount) {
        target.heal(amount);
        Fx.play(target, "heal_target");
        c.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY(0.6), target.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
    }

    static boolean entangle(CastContext c) {
        Targeting.Aim aim = Targeting.aim(c.caster, c.param("range", 26));
        Vec3 center = aim.entity() != null ? aim.entity().position() : Targeting.ground(c.level, c.caster, aim.point());
        double radius = c.param("radius", 4);
        int rootTicks = (int) (c.param("root_seconds", 4) * 20);
        double damage = c.damage("damage", 14);
        ServerLevel level = c.level;
        Fx.play(level, "entangle_burst", center);
        Fx.sound(level, center, SoundEvents.GRASS_BREAK, 1.0f, 0.6f);
        Fx.sound(level, center, SoundEvents.VINE_BREAK, 1.0f, 0.7f);
        Fx.sound(level, center, SoundEvents.BONE_MEAL_USE, 1.0f, 0.8f);
        int[] age = {0};
        Tasks.add(() -> {
            if (age[0] == 0 || age[0] % 20 == 0) {
                for (LivingEntity victim : Targeting.around(level, c.caster, center.add(0, 1, 0), radius)) {
                    // Everything inside is held for another stretch each second, so the roots last as long as the field.
                    Statuses.root(victim, Math.min(40, rootTicks - age[0] + 5), level.getServer());
                    c.hurt(victim, damage);
                    Fx.play(level, "entangle_roots", victim.position());
                }
            }
            if (age[0] % 5 == 0) {
                Fx.play(level, "entangle_field", center);
            }
            return ++age[0] >= rootTicks;
        });
        return true;
    }
}
