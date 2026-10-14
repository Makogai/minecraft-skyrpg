package com.skyrpg.items.ability;

import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click abilities on items: looks up the held item's ability, checks its cooldown, spends mana
 * through {@link Stats#spendMana} and runs it. Feedback goes on the action bar.
 */
public final class Abilities {
    private static final Map<String, AbilityType> TYPES = new HashMap<>();
    /** Player -> item id -> server tick when the ability is ready again. */
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    private Abilities() {
    }

    public static void register(String type, AbilityType ability) {
        TYPES.put(type, ability);
    }

    public static void init() {
        register("riftstep", Abilities::riftstep);
        register("mend", Abilities::mend);
        UseItemCallback.EVENT.register((player, level, hand) -> tryUse(player, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> tryUse(player, hand));
    }

    private static InteractionResult tryUse(Player player, InteractionHand hand) {
        ItemDefinition def = SkyItems.definition(player.getItemInHand(hand));
        if (def == null || def.ability() == null || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // the server decides; don't also run the item's vanilla use
        }
        ItemDefinition.Ability ability = def.ability();
        AbilityType type = TYPES.get(ability.type());
        if (type == null) {
            // Other mods' ability types (core's AbilityHandlers), like skyrpg_mining:echo_sense.
            AbilityHandlers.Handler handler = AbilityHandlers.get(ability.type());
            if (handler == null) {
                return InteractionResult.PASS;
            }
            type = (user, used) -> handler.use(user, used.params());
        }
        long now = serverPlayer.level().getServer().getTickCount();
        Map<String, Long> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        long readyAt = cooldowns.getOrDefault(def.id().toString(), 0L);
        if (now < readyAt) {
            serverPlayer.sendOverlayMessage(Msg.text("This ability is on cooldown for " + Msg.number((readyAt - now) / 20.0) + "s", ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (ability.mana() > 0 && Stats.available() && Stats.mana(serverPlayer) < ability.mana()) {
            serverPlayer.sendOverlayMessage(Msg.text("Not enough mana! (" + Msg.number(ability.mana()) + " needed)", ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!type.use(serverPlayer, ability)) {
            return InteractionResult.FAIL;
        }
        if (ability.mana() > 0) {
            Stats.spendMana(serverPlayer, ability.mana());
            serverPlayer.sendOverlayMessage(Msg.text("-" + Msg.number(ability.mana()) + " Mana (" + ability.name() + ")", ChatFormatting.AQUA));
        } else {
            serverPlayer.sendOverlayMessage(Msg.text(ability.name() + "!", ChatFormatting.GOLD));
        }
        if (ability.cooldownSeconds() > 0) {
            cooldowns.put(def.id().toString(), now + Math.round(ability.cooldownSeconds() * 20));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Steps through a rift up to params.distance (default 8) blocks along the look direction, stopping
     * before anything solid.
     */
    private static boolean riftstep(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = player.level();
        double distance = ability.param("distance", 8);
        Vec3 look = player.getLookAngle();
        Vec3 start = player.position();
        Vec3 target = start;
        for (double d = 0.5; d <= distance; d += 0.5) {
            Vec3 candidate = start.add(look.scale(d));
            AABB box = player.getBoundingBox().move(candidate.subtract(start));
            if (!level.noCollision(player, box)) {
                break;
            }
            target = candidate;
        }
        if (target.distanceToSqr(start) < 0.25) {
            return false;
        }
        // A violet tear where you were and where you come out.
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y + 1, start.z, 24, 0.25, 0.6, 0.25, 0.05);
        player.teleportTo(target.x, target.y, target.z);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.WITCH, target.x, target.y + 1, target.z, 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, BlockPos.containing(target), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9f, 0.6f);
        level.playSound(null, BlockPos.containing(target), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.6f);
        return true;
    }

    /** Restores params.amount (default 30) health. */
    private static boolean mend(ServerPlayer player, ItemDefinition.Ability ability) {
        double amount = ability.param("amount", 30);
        player.heal((float) (amount / StatTypes.HEALTH_SCALE));
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    /** Forgets a player's cooldowns when they leave. */
    public static void forget(UUID player) {
        COOLDOWNS.remove(player);
    }
}
