package com.skyrpg.magic;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;

/**
 * Mana Shield: for a few seconds, damage you would take is paid for from your mana instead (so much mana per point of damage).
 * When the mana runs out the shield breaks and the hit goes through.
 */
public final class ManaShield {
    private static final class Shield {
        int until;
        double manaPerDamage;
        double absorbed;
    }

    private static final Map<UUID, Shield> SHIELDS = new HashMap<>();

    private ManaShield() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || amount <= 0) {
                return true;
            }
            Shield shield = SHIELDS.get(player.getUUID());
            if (shield == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true;
            }
            double defense = Stats.available() ? Math.max(0, Stats.get(player, StatTypes.DEFENSE)) : 0;
            double damage = amount * StatTypes.HEALTH_SCALE * 100 / (100 + defense);
            double cost = damage * shield.manaPerDamage;
            ServerLevel level = player.level();
            if (Stats.available() && Stats.spendMana(player, cost)) {
                shield.absorbed += damage;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY(0.6), player.getZ(), 14, 0.4, 0.6, 0.4, 0.2);
                Fx.play(level, "shield_absorb", player.position().add(0, 1, 0));
                Fx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
                player.sendOverlayMessage(Msg.text("Mana Shield absorbed " + Msg.number(Math.round(damage)) + " damage", ChatFormatting.AQUA));
                return false;
            }
            // Not enough mana: the shield shatters and the hit lands.
            breakShield(player, shield);
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(ManaShield::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHIELDS.clear());
    }

    public static void activate(ServerPlayer player, double seconds, double manaPerDamage) {
        Shield shield = new Shield();
        shield.until = player.level().getServer().getTickCount() + (int) (seconds * 20);
        shield.manaPerDamage = manaPerDamage;
        SHIELDS.put(player.getUUID(), shield);
    }

    public static boolean active(ServerPlayer player) {
        return SHIELDS.containsKey(player.getUUID());
    }

    private static void breakShield(ServerPlayer player, Shield shield) {
        SHIELDS.remove(player.getUUID());
        Fx.play(player.level(), "shield_break", player.position().add(0, 1, 0));
        Fx.sound(player.level(), player.position(), SoundEvents.GLASS_BREAK, 1.0f, 0.8f);
        player.sendOverlayMessage(Msg.text("Your Mana Shield broke", ChatFormatting.RED));
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Shield>> it = SHIELDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shield> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Shield shield = entry.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= shield.until) {
                it.remove();
                Fx.sound(player.level(), player.position(), SoundEvents.BEACON_DEACTIVATE, 0.7f, 1.4f);
                player.sendOverlayMessage(Msg.text("Mana Shield faded (absorbed " + Msg.number(Math.round(shield.absorbed)) + ")", ChatFormatting.GRAY));
                continue;
            }
            if (now % 6 == 0) {
                Fx.play(player, "mana_shield_aura");
            }
        }
    }
}
