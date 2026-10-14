package com.skyrpg.core.combat;

import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Teleport warm-ups: a player-facing warp (/hub, /survival, waystone travel) goes through {@link #request} instead of
 * teleporting at once. In combat it is refused; otherwise the player has to stand still for
 * {@link CombatRules#warmupTicks} (a countdown in the action bar, a ring of particles and a tick sound each second) and
 * then the action runs. Moving more than {@link CombatRules#moveTolerance} blocks, taking damage, getting tagged, dying,
 * logging out or changing dimension cancels it. Creative and spectator players skip the wait.
 */
public final class Warmups {
    private static final class Warmup {
        final Vec3 start;
        final net.minecraft.resources.Identifier dimension;
        final String destination;
        final Runnable action;
        final long endsAt;
        int lastShown = -1;

        Warmup(ServerPlayer player, String destination, Runnable action, long endsAt) {
            this.start = player.position();
            this.dimension = player.level().dimension().identifier();
            this.destination = destination;
            this.action = action;
            this.endsAt = endsAt;
        }
    }

    private static final Map<UUID, Warmup> ACTIVE = new HashMap<>();
    private static boolean bypass;

    private Warmups() {
    }

    /**
     * Asks to teleport. Returns true if the warm-up started (or was skipped and the action already ran), false if it was
     * refused (in combat) or cancelled. {@code toggle}: asking again during a warm-up cancels it, as the commands do;
     * otherwise the new request replaces the running one.
     *
     * @param destination how the countdown names the place, e.g. "the Hub"
     * @param action      the teleport itself; runs on the server thread once the player has stood still long enough
     */
    public static boolean request(ServerPlayer player, String destination, boolean toggle, Runnable action) {
        if (bypass) {
            action.run();
            return true;
        }
        if (toggle && ACTIVE.containsKey(player.getUUID())) {
            cancel(player, "Teleport cancelled.");
            return false;
        }
        ACTIVE.remove(player.getUUID());
        CombatRules rules = CombatRules.get();
        if (player.isCreative() || player.isSpectator() || rules.warmupTicks() <= 0) {
            action.run();
            return true;
        }
        if (CombatTag.isTagged(player)) {
            fail(player, "You can't teleport while in combat: " + CombatTag.secondsLeft(player) + "s left.");
            return false;
        }
        long endsAt = player.level().getServer().getTickCount() + rules.warmupTicks();
        ACTIVE.put(player.getUUID(), new Warmup(player, destination, action, endsAt));
        // The first tick shows the countdown right away.
        return true;
    }

    /** Runs {@code action}, during which {@link #request} lets teleports through (for a warp that has already waited, like the waystone's trip to the hub). */
    public static void runBypassing(Runnable action) {
        boolean before = bypass;
        bypass = true;
        try {
            action.run();
        } finally {
            bypass = before;
        }
    }

    public static boolean isWarming(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Cancels a running warm-up with a message (nothing if there is none). */
    public static void cancel(ServerPlayer player, String reason) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            fail(player, reason);
        }
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendOverlayMessage(Msg.text(message, ChatFormatting.RED));
        player.sendSystemMessage(Msg.text(message, ChatFormatting.RED));
        Msg.playSound(player, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        double tolerance = CombatRules.get().moveTolerance();
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            Warmup warmup = ACTIVE.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive()) {
                ACTIVE.remove(id);
                continue;
            }
            if (!player.level().dimension().identifier().equals(warmup.dimension)) {
                cancel(player, "Teleport cancelled: you changed dimension.");
                continue;
            }
            if (player.position().distanceTo(warmup.start) > tolerance) {
                cancel(player, "Teleport cancelled: you moved.");
                continue;
            }
            if (now >= warmup.endsAt) {
                ACTIVE.remove(id);
                Msg.playSound(player, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.2f);
                warmup.action.run();
                continue;
            }
            int secondsLeft = (int) ((warmup.endsAt - now + 19) / 20);
            if (secondsLeft != warmup.lastShown) {
                warmup.lastShown = secondsLeft;
                player.sendOverlayMessage(Msg.text("Teleporting to " + warmup.destination + " in " + secondsLeft + "s - stand still",
                        ChatFormatting.GOLD));
                ring(player, secondsLeft);
                Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f + 0.1f * (CombatRules.get().warmupTicks() / 20 - secondsLeft));
            }
        }
    }

    /** A ring of particles around the player's feet that closes in as the warm-up runs out. */
    private static void ring(ServerPlayer player, int secondsLeft) {
        double radius = 0.7 + 0.25 * secondsLeft;
        int points = 28;
        for (int i = 0; i < points; i++) {
            double angle = Math.TAU * i / points;
            player.level().sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(angle) * radius, player.getY() + 0.15,
                    player.getZ() + Math.sin(angle) * radius, 1, 0, 0.05, 0, 0.01);
        }
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Warmups::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer player) {
                cancel(player, "Teleport cancelled: you took damage.");
            }
        });
        CombatTag.onTagged((player, fresh) -> cancel(player, "Teleport cancelled: you're in combat."));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                ACTIVE.remove(player.getUUID());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
    }
}
