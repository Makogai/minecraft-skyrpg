package com.skyrpg.magic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * What spells leave on their targets: burning (damage over time that credits the caster, unlike vanilla fire), slowness and roots.
 * All short-lived and kept in memory only.
 */
public final class Statuses {
    private record Burn(LivingEntity target, UUID caster, double perSecond, int until) {
    }

    private record Root(LivingEntity target, int until) {
    }

    private static final Map<UUID, Burn> BURNS = new HashMap<>();
    private static final Map<UUID, Root> ROOTS = new HashMap<>();

    private Statuses() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Statuses::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BURNS.clear();
            ROOTS.clear();
        });
    }

    /** Sets the target burning: {@code perSecond} SkyRPG damage twice a second for {@code seconds}. A stronger burn replaces a weaker one. */
    public static void burn(ServerPlayer caster, LivingEntity target, double perSecond, double seconds, MinecraftServer server) {
        int until = server.getTickCount() + (int) (seconds * 20);
        Burn current = BURNS.get(target.getUUID());
        if (current == null || current.perSecond() <= perSecond || current.until() < until) {
            BURNS.put(target.getUUID(), new Burn(target, caster.getUUID(), Math.max(perSecond, current == null ? 0 : current.perSecond()), Math.max(until, current == null ? 0 : current.until())));
        }
    }

    public static boolean burning(LivingEntity target) {
        return BURNS.containsKey(target.getUUID());
    }

    /** Slowness of a given level for a while. */
    public static void slow(LivingEntity target, int level, double seconds) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (seconds * 20), level, false, true));
    }

    /** Holds a mob in place: it can't walk or be pushed along the ground, for a number of ticks. */
    public static void root(LivingEntity target, int ticks, MinecraftServer server) {
        ROOTS.put(target.getUUID(), new Root(target, server.getTickCount() + ticks));
        slow(target, 6, ticks / 20.0);
    }

    public static boolean rooted(LivingEntity target) {
        return ROOTS.containsKey(target.getUUID());
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (Iterator<Burn> it = BURNS.values().iterator(); it.hasNext(); ) {
            Burn burn = it.next();
            LivingEntity target = burn.target();
            if (!target.isAlive() || target.isRemoved() || now >= burn.until()) {
                it.remove();
                continue;
            }
            if (!(target.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (now % 4 == 0) {
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.5), target.getZ(), 2, target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3, 0.02);
            }
            if (now % 10 == 0 && server.getPlayerList().getPlayer(burn.caster()) instanceof ServerPlayer caster) {
                if (target.isInWater()) {
                    it.remove();
                    continue;
                }
                SpellDamage.hurt(caster, target, burn.perSecond() / 2);
                level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY(0.8), target.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
            }
        }
        for (Iterator<Root> it = ROOTS.values().iterator(); it.hasNext(); ) {
            Root root = it.next();
            LivingEntity target = root.target();
            if (!target.isAlive() || target.isRemoved() || now >= root.until()) {
                it.remove();
                continue;
            }
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(0, Math.min(0, motion.y), 0);
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            if (now % 6 == 0 && target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.COMPOSTER, target.getX(), target.getY() + 0.2, target.getZ(), 3, target.getBbWidth() * 0.4, 0.3, target.getBbWidth() * 0.4, 0.02);
            }
        }
    }
}
