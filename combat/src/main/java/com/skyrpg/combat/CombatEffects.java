package com.skyrpg.combat;

import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.text.Msg;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * The combat gear effects (see core's {@link GearEffects}): weapons, sets and talents grant them by data; what they do is
 * here. Also the weapon abilities Thunderclap and Starfall.
 */
public final class CombatEffects {
    /** Heals this part of the melee damage you deal (0.04 = 4%). */
    public static final Identifier LIFE_STEAL = SkyRpgCombat.id("life_steal");
    /** Critical hits heal this part of your max health (0.05 = 5%). */
    public static final Identifier CRIT_HEAL = SkyRpgCombat.id("crit_heal");
    /** Sets what you hit on fire for this many seconds. */
    public static final Identifier IGNITE = SkyRpgCombat.id("ignite");
    /** Hands this part of melee damage you take back to the attacker (0.2 = 20%). */
    public static final Identifier THORNS = SkyRpgCombat.id("thorns");
    /** A hit for a fifth of your health or more raises a shield worth this part of your max health (once every 20 s). */
    public static final Identifier GUARD = SkyRpgCombat.id("guard");
    /** Chance (0..1) to slip past a melee hit entirely. */
    public static final Identifier DODGE = SkyRpgCombat.id("dodge");

    private static final int GUARD_COOLDOWN = 400;
    private static final Map<UUID, Long> GUARD_READY = new HashMap<>();

    private CombatEffects() {
    }

    static double value(ServerPlayer player, Identifier effect) {
        return GearEffects.value(player, effect);
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && player.getRandom().nextDouble() < value(player, DODGE)) {
                player.level().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.2);
                Msg.playSound(player, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
                player.sendOverlayMessage(Msg.text("Dodged!", ChatFormatting.LIGHT_PURPLE));
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0) {
                return;
            }
            // Dealing a melee hit.
            if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player) {
                double steal = value(player, LIFE_STEAL);
                double heal = damageTaken * steal;
                if (Crits.wasCrit()) {
                    heal += player.getMaxHealth() * value(player, CRIT_HEAL);
                }
                if (heal > 0) {
                    player.heal((float) heal);
                }
                double ignite = value(player, IGNITE);
                if (ignite > 0) {
                    entity.igniteForSeconds((float) ignite);
                }
            }
            // Taking a melee hit.
            if (entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                    && attacker != player) {
                double thorns = value(player, THORNS);
                if (thorns > 0) {
                    attacker.hurtServer(player.level(), player.damageSources().thorns(player), (float) (damageTaken * thorns));
                }
                double guard = value(player, GUARD);
                long now = player.level().getServer().getTickCount();
                if (guard > 0 && damageTaken >= player.getMaxHealth() * 0.2 && now >= GUARD_READY.getOrDefault(player.getUUID(), 0L)) {
                    GUARD_READY.put(player.getUUID(), now + GUARD_COOLDOWN);
                    player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), (float) (player.getMaxHealth() * guard)));
                    Msg.playSound(player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.2f);
                    player.sendOverlayMessage(Msg.text("⛨ Stormguard!", ChatFormatting.AQUA));
                }
            }
        });

        // "skyrpg_combat:thunderclap": {"radius": 5, "damage": 40} lightning (no fire) on every hostile mob around you.
        AbilityHandlers.register("skyrpg_combat:thunderclap", (player, params) -> {
            double radius = params.has("radius") ? params.get("radius").getAsDouble() : 5;
            float damage = (float) ((params.has("damage") ? params.get("damage").getAsDouble() : 40) / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE);
            List<LivingEntity> hit = nearbyEnemies(player, radius);
            for (LivingEntity target : hit) {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(player.level(), EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.setPos(target.getX(), target.getY(), target.getZ());
                    player.level().addFreshEntity(bolt);
                }
                target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
            }
            if (hit.isEmpty()) {
                player.sendOverlayMessage(Msg.text("Nothing to strike nearby", ChatFormatting.GRAY));
                return false;
            }
            return true;
        });
        // "skyrpg_combat:starfall": {"radius": 6, "damage": 80} stars crash down on every hostile mob around you.
        AbilityHandlers.register("skyrpg_combat:starfall", (player, params) -> {
            double radius = params.has("radius") ? params.get("radius").getAsDouble() : 6;
            float damage = (float) ((params.has("damage") ? params.get("damage").getAsDouble() : 80) / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE);
            List<LivingEntity> hit = nearbyEnemies(player, radius);
            ServerLevel level = player.level();
            for (LivingEntity target : hit) {
                for (int i = 0; i < 6; i++) {
                    level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 3 - i * 0.5, target.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
                }
                level.sendParticles(ParticleTypes.FIREWORK, target.getX(), target.getY() + 0.5, target.getZ(), 12, 0.4, 0.3, 0.4, 0.15);
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
            }
            if (hit.isEmpty()) {
                player.sendOverlayMessage(Msg.text("Nothing to strike nearby", ChatFormatting.GRAY));
                return false;
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0f, 0.9f);
            return true;
        });
    }

    private static List<LivingEntity> nearbyEnemies(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(radius),
                e -> e instanceof Enemy && e.isAlive() && e.distanceTo(player) <= radius);
    }
}
