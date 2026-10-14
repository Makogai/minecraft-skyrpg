package com.skyrpg.vfx;

import com.skyrpg.vfx.net.VfxPayloads;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side entry point for other mods: plays a data-defined effect ({@code data/<ns>/skyrpg/vfx/<id>.json}) for
 * every player who can see it. The particles themselves are simulated on the clients, so a call costs one small packet
 * per nearby player. Unknown ids are logged once and ignored. Call from the server thread.
 *
 * <pre>Vfx.play(level, Identifier.fromNamespaceAndPath("skyrpg_vfx", "slam_shockwave"), mob.position());
 * Vfx.shake(player, 0.8f, 8);                    // a solid thump
 * Vfx.later(server, 6, () -&gt; Vfx.flash(player, 0x66FFFFFF, 5));</pre>
 *
 * <h2>Screen shake and flash</h2>
 * {@link #shake(ServerPlayer, float, int)} swings that player's camera by up to {@code strength} degrees (0.3 a tick of
 * rumble, 0.6 a light hit, 1.0 a solid hit, 1.6 a big slam, 2.5 the end of the world) and fades out over {@code ticks}.
 * Shakes do not stack, the stronger one wins; the player's Distortion Effects accessibility slider and the Effects
 * quality setting (Off) turn it down. {@link #shakeAround} does the same to everyone near a point, weaker with distance.
 * {@link #flash(ServerPlayer, int, int)} washes the screen with an ARGB colour (its alpha is the peak) for a few ticks.
 */
public final class Vfx {
    private record Task(long due, Runnable run) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static boolean tickerRegistered;

    private Vfx() {
    }

    /** Plays a one-shot effect at a point. */
    public static void play(ServerLevel level, Identifier effect, Vec3 pos) {
        VfxServer.play(level, effect, pos);
    }

    /** Plays an effect anchored to an entity, following it for the effect's lifetime. */
    public static void play(Entity anchor, Identifier effect) {
        VfxServer.play(anchor, effect);
    }

    /** True if an effect with this id is loaded on the server. */
    public static boolean exists(Identifier effect) {
        return VfxServer.library().effect(effect) != null;
    }

    /** Shakes one player's camera: up to {@code strength} degrees, fading out over {@code ticks}. */
    public static void shake(ServerPlayer player, float strength, int ticks) {
        if (strength > 0 && ticks > 0 && ServerPlayNetworking.canSend(player, VfxPayloads.Shake.TYPE)) {
            ServerPlayNetworking.send(player, new VfxPayloads.Shake(strength, ticks));
        }
    }

    /** Shakes everyone within {@code radius} blocks of a point, fading out linearly towards the edge (a third at the rim). */
    public static void shakeAround(ServerLevel level, Vec3 pos, double radius, float strength, int ticks) {
        for (ServerPlayer player : PlayerLookup.around(level, pos, radius)) {
            double falloff = 1 - 0.67 * Math.min(1, player.position().distanceTo(pos) / radius);
            shake(player, (float) (strength * falloff), ticks);
        }
    }

    /** Washes one player's screen with a colour ({@code 0xAARRGGBB}, alpha = the strongest it gets) for {@code ticks}. */
    public static void flash(ServerPlayer player, int argb, int ticks) {
        if (ticks > 0 && ServerPlayNetworking.canSend(player, VfxPayloads.Flash.TYPE)) {
            ServerPlayNetworking.send(player, new VfxPayloads.Flash(argb, ticks));
        }
    }

    /** Runs {@code task} on the server thread after {@code ticks} ticks (0 = at the end of this one). */
    public static void later(MinecraftServer server, int ticks, Runnable task) {
        synchronized (TASKS) {
            if (!tickerRegistered) {
                tickerRegistered = true;
                ServerTickEvents.END_SERVER_TICK.register(Vfx::runDue);
            }
            TASKS.add(new Task(server.getTickCount() + Math.max(0, ticks), task));
        }
    }

    private static void runDue(MinecraftServer server) {
        List<Runnable> due = new ArrayList<>();
        synchronized (TASKS) {
            long now = server.getTickCount();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.due() <= now) {
                    due.add(task.run());
                    it.remove();
                }
            }
        }
        for (Runnable run : due) {
            try {
                run.run();
            } catch (RuntimeException e) {
                SkyRpgVfx.LOGGER.error("A delayed effect failed", e);
            }
        }
    }

    private static final java.util.Map<String, SoundEvent> SOUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Plays a sound by its registry id ({@code "minecraft:entity.firework_rocket.blast"}) for everyone near, with a little
     * random pitch (+-6%) so repeats do not sound identical. Layer two or three for a fat sound; an unknown id is ignored.
     */
    public static void sound(ServerLevel level, Vec3 pos, String id, float volume, float pitch) {
        SoundEvent sound = SOUNDS.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            SoundEvent found = parsed == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(parsed);
            if (found == null) {
                SkyRpgVfx.LOGGER.warn("Vfx.sound: unknown sound {}", key);
            }
            return found == null ? SILENCE : found;
        });
        if (sound != SILENCE) {
            float varied = pitch * (0.94f + level.getRandom().nextFloat() * 0.12f);
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, varied);
        }
    }

    private static final SoundEvent SILENCE = SoundEvent.createVariableRangeEvent(SkyRpgVfx.id("silence"));
}
