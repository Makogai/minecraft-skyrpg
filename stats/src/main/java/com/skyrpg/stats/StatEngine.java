package com.skyrpg.stats;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Recomputes every player's stats from all {@link Stats.Source}s twice a second, and turns the totals
 * into vanilla attributes: max health, walking speed and block break speed. Also regenerates mana.
 */
final class StatEngine {
    private static final int RECOMPUTE_TICKS = 10;
    private static final int MANA_TICKS = 20;
    /** Mana regenerated per second, as a share of max mana. */
    private static final double MANA_REGEN = 0.02;

    private static final Identifier HEALTH_MODIFIER = SkyRpgStats.id("health");
    private static final Identifier SPEED_MODIFIER = SkyRpgStats.id("speed");
    private static final Identifier MINING_SPEED_MODIFIER = SkyRpgStats.id("mining_speed");

    private StatEngine() {
    }

    static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % RECOMPUTE_TICKS == 0) {
            server.getPlayerList().getPlayers().forEach(StatEngine::recompute);
        }
        if (tick % MANA_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                double max = PlayerStats.maxMana(player);
                PlayerStats.setMana(player, PlayerStats.mana(player) + max * MANA_REGEN);
            }
        }
    }

    /** Asks every source for its contributions, stores the breakdown (syncing only on change) and applies attributes. */
    static void recompute(ServerPlayer player) {
        Map<String, Map<String, Double>> breakdown = new HashMap<>();
        for (Stats.Source source : Stats.sources()) {
            try {
                source.contribute(player, (stat, value) -> {
                    if (value != 0 && StatTypes.isPlayerStat(stat)) {
                        breakdown.computeIfAbsent(stat.id(), id -> new HashMap<>()).merge(source.name(), value, Double::sum);
                    }
                });
            } catch (RuntimeException e) {
                SkyRpgStats.LOGGER.error("Stat source {} failed for {}", source.name(), player.getScoreboardName(), e);
            }
        }
        if (!breakdown.equals(player.getAttachedOrElse(PlayerStats.BREAKDOWN, Map.of()))) {
            Map<String, Map<String, Double>> frozen = new HashMap<>();
            breakdown.forEach((stat, sources) -> frozen.put(stat, Map.copyOf(sources)));
            player.setAttached(PlayerStats.BREAKDOWN, Map.copyOf(frozen));
        }
        applyAttributes(player);
        // Mana can't exceed a (possibly lower) new maximum.
        PlayerStats.setMana(player, PlayerStats.mana(player));
    }

    private static void applyAttributes(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double vanillaHealth = PlayerStats.total(player, StatTypes.HEALTH) / StatTypes.HEALTH_SCALE;
            setModifier(maxHealth, HEALTH_MODIFIER, Math.max(1, vanillaHealth) - maxHealth.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, ratio(player, StatTypes.SPEED) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_MODIFIER, ratio(player, StatTypes.MINING_SPEED),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    /** A stat as a multiplier where 100 = 1.0. */
    private static double ratio(ServerPlayer player, StatType stat) {
        return Math.max(0, PlayerStats.total(player, stat)) / 100;
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            setModifier(instance, id, amount, operation);
        }
    }

    /** Adds, updates or removes our modifier, touching the attribute only when the value changes. */
    private static void setModifier(AttributeInstance instance, Identifier id, double amount, AttributeModifier.Operation operation) {
        AttributeModifier existing = instance.getModifier(id);
        if (Math.abs(amount) < 1e-9) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.operation() == operation && Math.abs(existing.amount() - amount) < 1e-9) {
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
