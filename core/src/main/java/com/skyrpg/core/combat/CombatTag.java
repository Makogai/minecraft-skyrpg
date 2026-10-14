package com.skyrpg.core.combat;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.data.JsonDataLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Combat tag: for {@link CombatRules#tagTicks} after dealing damage to, or taking damage from, a hostile mob or another
 * player (projectiles and abilities count for whoever fired them) a player is "in combat". Fall, fire, drowning and the
 * like don't count, nor do hits on passive animals. Creative and spectator players are never tagged.
 *
 * <pre>
 * if (CombatTag.isTagged(player)) { ... CombatTag.secondsLeft(player) ... }
 * CombatTag.onTagged((player, fresh) -> ...);
 * CombatTag.onUntagged((player, reason) -> ...);
 * </pre>
 * Dying and logging out clear the tag. The remaining time is synced to the client ({@link CombatTagPayload}) for the
 * HUD indicator.
 */
public final class CombatTag {
    /** Why a tag ended. */
    public enum Reason { EXPIRED, DEATH, DISCONNECT, CLEARED }

    /** Called each time a player is tagged; {@code fresh} is true when they weren't in combat before. */
    @FunctionalInterface
    public interface TaggedListener {
        void onTagged(ServerPlayer player, boolean fresh);
    }

    /** Called when a player leaves combat. */
    @FunctionalInterface
    public interface UntaggedListener {
        void onUntagged(ServerPlayer player, Reason reason);
    }

    private static final Map<UUID, Long> EXPIRES = new HashMap<>();
    private static final List<TaggedListener> TAGGED = new CopyOnWriteArrayList<>();
    private static final List<UntaggedListener> UNTAGGED = new CopyOnWriteArrayList<>();

    private CombatTag() {
    }

    public static void onTagged(TaggedListener listener) {
        TAGGED.add(listener);
    }

    public static void onUntagged(UntaggedListener listener) {
        UNTAGGED.add(listener);
    }

    /** Puts the player in combat for the configured time (restarting it if they already are). */
    public static void tag(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        int ticks = CombatRules.get().tagTicks();
        if (ticks <= 0) {
            return;
        }
        boolean fresh = !isTagged(player);
        EXPIRES.put(player.getUUID(), now(player) + ticks);
        sync(player, ticks);
        for (TaggedListener listener : TAGGED) {
            listener.onTagged(player, fresh);
        }
    }

    public static boolean isTagged(ServerPlayer player) {
        Long expires = EXPIRES.get(player.getUUID());
        return expires != null && expires > now(player);
    }

    /** Whole seconds of combat left, rounded up (0 when not in combat). */
    public static int secondsLeft(ServerPlayer player) {
        Long expires = EXPIRES.get(player.getUUID());
        if (expires == null) {
            return 0;
        }
        long left = expires - now(player);
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    /** Takes the player out of combat now. */
    public static void clear(ServerPlayer player) {
        end(player, Reason.CLEARED, true);
    }

    private static long now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    private static void end(ServerPlayer player, Reason reason, boolean sync) {
        if (EXPIRES.remove(player.getUUID()) == null) {
            return;
        }
        if (sync) {
            sync(player, 0);
        }
        for (UntaggedListener listener : UNTAGGED) {
            listener.onUntagged(player, reason);
        }
    }

    private static void sync(ServerPlayer player, int ticks) {
        if (ServerPlayNetworking.canSend(player, CombatTagPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CombatTagPayload(ticks));
        }
    }

    /** Whether trading blows with this makes a fight: a hostile mob or a player. */
    private static boolean fights(Entity other) {
        return other instanceof Enemy || other instanceof ServerPlayer;
    }

    private static void onDamage(LivingEntity victim, DamageSource source) {
        // The culprit is a projectile's owner or an ability's caster; fall, fire, drowning, void ... have none.
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == victim) {
            return;
        }
        if (victim instanceof ServerPlayer hurt && fights(attacker)) {
            tag(hurt);
        }
        if (attacker instanceof ServerPlayer hitter && fights(victim)) {
            tag(hitter);
        }
    }

    private static void tick(MinecraftServer server) {
        if (EXPIRES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (UUID id : new ArrayList<>(EXPIRES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                EXPIRES.remove(id);
            } else if (EXPIRES.get(id) <= now) {
                end(player, Reason.EXPIRED, true);
            }
        }
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CombatTagPayload.TYPE, CombatTagPayload.CODEC);
        JsonDataLoader.register(SkyRpgCore.id("combat_rules"), CombatRules.DIRECTORY, CombatRules::load);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> onDamage(entity, source));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                end(player, Reason.DEATH, true);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.getPlayer(), Reason.DISCONNECT, false));
        ServerTickEvents.END_SERVER_TICK.register(CombatTag::tick);
        Warmups.init();
    }
}
