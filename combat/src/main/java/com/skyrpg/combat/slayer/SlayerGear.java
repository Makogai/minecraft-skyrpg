package com.skyrpg.combat.slayer;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.stats.IncomingDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * The slayer gear effects (see core's {@link GearEffects}):
 * <ul>
 *   <li>{@code skyrpg_combat:bane/<line>}: this much more damage (0.5 = +50%) to the mobs that line counts and to its
 *   boss.</li>
 *   <li>{@code skyrpg_combat:ward/<line>}: this much less damage (0.3 = -30%) taken from that line's mobs and its boss
 *   (the slayer armour sets).</li>
 *   <li>{@code skyrpg_combat:slayer_xp/<line>}: this much more slayer XP (0.25 = +25%) from that line's bosses.</li>
 *   <li>{@code skyrpg_combat:venom}: melee hits poison for this many seconds.</li>
 * </ul>
 */
public final class SlayerGear {
    public static final Identifier VENOM = SkyRpgCombat.id("venom");

    private SlayerGear() {
    }

    public static Identifier bane(String line) {
        return SkyRpgCombat.id("bane/" + line);
    }

    public static Identifier ward(String line) {
        return SkyRpgCombat.id("ward/" + line);
    }

    public static Identifier slayerXp(String line) {
        return SkyRpgCombat.id("slayer_xp/" + line);
    }

    /** The extra slayer XP a player's gear gives for this line (0.25 = +25%). */
    public static double xpBonus(ServerPlayer player, String line) {
        return Math.max(0, GearEffects.value(player, slayerXp(line)));
    }

    static void init() {
        // Slayer armour: take less from the line's mobs and boss.
        IncomingDamage.registerHit((target, source) -> {
            if (!(target instanceof ServerPlayer player) || source.getEntity() == null) {
                return 1;
            }
            double multiplier = 1;
            for (SlayerLine line : Slayers.lines().values()) {
                double ward = GearEffects.value(player, ward(line.id()));
                if (ward > 0 && (line.counts(source.getEntity()) || BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).equals(line.boss()))) {
                    multiplier *= Math.max(0, 1 - ward);
                }
            }
            return multiplier;
        });
        IncomingDamage.registerHit((target, source) -> {
            ServerPlayer player = source.getEntity() instanceof ServerPlayer direct ? direct
                    : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (player == null) {
                return 1;
            }
            double multiplier = 1;
            for (SlayerLine line : Slayers.lines().values()) {
                double bane = GearEffects.value(player, bane(line.id()));
                if (bane > 0 && (line.counts(target) || BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).equals(line.boss()))) {
                    multiplier *= 1 + bane;
                }
            }
            return multiplier;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0 || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || entity == player) {
                return;
            }
            double venom = GearEffects.value(player, VENOM);
            if (venom > 0 && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.round(venom * 20), 1), player);
            }
        });
    }
}
