package com.skyrpg.magic;

import com.skyrpg.vfx.Vfx;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Sounds, VFX and particles for spells. VFX effects are data in this mod ({@code skyrpg/vfx/}), played through the vfx mod. */
public final class Fx {
    private Fx() {
    }

    /** Plays one of this mod's VFX effects at a point. */
    public static void play(ServerLevel level, String effect, Vec3 pos) {
        Identifier id = SkyRpgMagic.id(effect);
        if (Vfx.exists(id)) {
            Vfx.play(level, id, pos);
        }
    }

    /** Plays one of this mod's VFX effects on an entity (it follows the entity). */
    public static void play(Entity anchor, String effect) {
        Identifier id = SkyRpgMagic.id(effect);
        if (Vfx.exists(id)) {
            Vfx.play(anchor, id);
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Particles seen by everyone in range except one player (the caster, whose own camera they would fill). */
    public static void particlesExcept(ServerLevel level, ServerPlayer except, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        for (ServerPlayer viewer : PlayerLookup.around(level, pos, 48)) {
            if (viewer != except) {
                level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
            }
        }
    }

    /** A line of particles from a to b, {@code spacing} blocks apart. */
    public static void line(ServerLevel level, ParticleOptions particle, Vec3 a, Vec3 b, double spacing, double spread) {
        Vec3 delta = b.subtract(a);
        double length = delta.length();
        int steps = Math.max(1, (int) (length / spacing));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.add(delta.scale(i / (double) steps));
            level.sendParticles(particle, p.x, p.y, p.z, 1, spread, spread, spread, 0.0);
        }
    }

    /** A jagged bolt of particles from a to b: the straight line broken into kinked segments. */
    public static void arc(ServerLevel level, RandomSource random, ParticleOptions core, ParticleOptions glow, Vec3 a, Vec3 b) {
        Vec3 delta = b.subtract(a);
        int segments = Math.max(2, (int) (delta.length() / 1.2));
        Vec3 previous = a;
        for (int i = 1; i <= segments; i++) {
            Vec3 p = i == segments ? b : a.add(delta.scale(i / (double) segments))
                    .add((random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9);
            line(level, core, previous, p, 0.25, 0.0);
            level.sendParticles(glow, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            previous = p;
        }
    }

    /** Shakes the camera of every player within {@code radius} (less the further they are). */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float amount, int ticks) {
        for (ServerPlayer player : PlayerLookup.around(level, pos, radius)) {
            double distance = player.position().distanceTo(pos);
            float scaled = (float) (amount * Math.max(0.15, 1 - distance / radius));
            ServerPlayNetworking.send(player, new MagicPayloads.Shake(scaled, ticks));
        }
    }
}
