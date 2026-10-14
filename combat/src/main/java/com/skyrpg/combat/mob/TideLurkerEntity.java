package com.skyrpg.combat.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tide Lurker: a drowned that haunts rivers and beaches at night. Every few seconds it hauls a player 4 to 12 blocks
 * away towards itself (and the water) with its tide hook.
 */
public class TideLurkerEntity extends Drowned {
    private static final int EVERY = 120;
    private int nextHook = 60;

    public TideLurkerEntity(EntityType<? extends Drowned> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Drowned.createAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4);
    }

    @Override
    public void setBaby(boolean baby) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (!(target instanceof Player player) || player.isCreative() || player.isSpectator() || --nextHook > 0) {
            return;
        }
        double distance = distanceTo(player);
        if (distance < 4 || distance > 12 || !hasLineOfSight(player)) {
            nextHook = 20;
            return;
        }
        nextHook = EVERY;
        Vec3 pull = position().subtract(player.position()).normalize().scale(Math.min(2.0, distance * 0.18));
        player.push(pull.x, 0.35, pull.z);
        player.syncVelocity = true; // what vanilla's knockback sets: sends the push to the player's client
        Vec3 from = getEyePosition();
        Vec3 to = player.position().add(0, 1, 0);
        for (int i = 0; i <= 10; i++) {
            Vec3 at = from.lerp(to, i / 10.0);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.2f, 0.6f);
    }
}
