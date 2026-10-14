package com.skyrpg.items.shield;

import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.stats.SkyArrows;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ModItems;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.bow.Bows;
import com.skyrpg.vfx.Vfx;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shields. Blocking is vanilla's {@code blocks_attacks} component (so any item could block); each shield blocks its own share
 * of frontal damage. What a shield does on top is a gear effect in its definition, active while it is held or in the off hand
 * (see {@code GearEffectSources}):
 * <ul>
 *   <li>{@code skyrpg_items:shield_bash}: a blocked melee attacker is knocked back this hard (1 = a good shove).</li>
 *   <li>{@code skyrpg_items:shield_ignite}: blocked attackers burn for this many seconds.</li>
 *   <li>{@code skyrpg_items:shield_frost}: blocked attackers are slowed and frozen for this many seconds.</li>
 *   <li>{@code skyrpg_items:shield_charge}: every block stores a charge, up to this many.</li>
 *   <li>{@code skyrpg_items:shield_shockwave}: sneak + use releases the charges as a lightning shockwave, this much damage each.</li>
 *   <li>{@code skyrpg_items:shield_reflect}: projectiles that hit the shield fly back at whoever shot them, as your own.</li>
 * </ul>
 */
public final class Shields {
    public static final Identifier BASH = SkyRpgItems.id("shield_bash");
    public static final Identifier IGNITE = SkyRpgItems.id("shield_ignite");
    public static final Identifier FROST = SkyRpgItems.id("shield_frost");
    public static final Identifier CHARGE = SkyRpgItems.id("shield_charge");
    public static final Identifier SHOCKWAVE = SkyRpgItems.id("shield_shockwave");
    public static final Identifier REFLECT = SkyRpgItems.id("shield_reflect");

    /** Stored storm charges, synced to their owner so the client knows whether sneak + use is a shockwave. */
    public static final AttachmentType<Integer> CHARGES = AttachmentRegistry.create(SkyRpgItems.id("shield_charges"), builder -> builder
            .initializer(() -> 0)
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    private record Reflected(Projectile projectile, Vec3 velocity) {
    }

    private static final List<Reflected> REFLECTED = new java.util.ArrayList<>();

    private Shields() {
    }

    /** Vanilla's shield blocking, but taking {@code blocked} (0..1) of the damage from the front instead of all of it. */
    public static BlocksAttacks blocksAttacks(HolderLookup.Provider provider, float blocked) {
        return new BlocksAttacks(0.25f, 1.0f,
                List.of(new BlocksAttacks.DamageReduction(90.0f, Optional.empty(), 0.0f, blocked)),
                new BlocksAttacks.ItemDamageFunction(3.0f, 1.0f, 1.0f),
                Optional.of(provider.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK));
    }

    public static void init() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (Reflected reflected : REFLECTED) {
                if (!reflected.projectile().isRemoved()) {
                    reflected.projectile().setDeltaMovement(reflected.velocity());
                    reflected.projectile().syncVelocity = true;
                }
            }
            REFLECTED.clear();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.getDirectEntity() instanceof Projectile projectile && reflect(player, projectile, amount)));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (blocked && entity instanceof ServerPlayer player) {
                onBlocked(player, source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != player ? attacker : null,
                        baseDamage);
            }
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            ItemDefinition def = SkyItems.definition(stack);
            if (def == null || ModItems.kind(def.item()) != ModItems.Kind.SHIELD || !player.isShiftKeyDown()
                    || def.effects().getOrDefault(SHOCKWAVE, 0.0) <= 0 || player.getAttachedOrElse(CHARGES, 0) <= 0) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                shockwave(serverPlayer, def.effects().get(SHOCKWAVE));
            }
            return InteractionResult.SUCCESS;
        });
    }

    private static Identifier fx(String path) {
        return Identifier.fromNamespaceAndPath("skyrpg_abilities", path);
    }

    private static void onBlocked(ServerPlayer player, LivingEntity attacker, float damage) {
        ServerLevel level = player.level();
        // Every block, whatever the shield: a flash, a ring and sparks at its face (themed by what the shield does), a crunch, and a
        // jolt that grows with how hard the hit was.
        Vec3 face = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8)).add(0, 1.25, 0);
        String variant = GearEffects.value(player, REFLECT) > 0 ? "_astral" : GearEffects.value(player, CHARGE) > 0 ? "_storm"
                : GearEffects.value(player, FROST) > 0 ? "_frost" : GearEffects.value(player, IGNITE) > 0 ? "_ember" : "";
        Vfx.play(level, fx("shield_block" + variant), face);
        Vfx.sound(level, face, "minecraft:block.chain.hit", 0.6f, 0.8f);
        Vfx.sound(level, face, "minecraft:block.anvil.land", 0.2f, 1.9f);
        Vfx.shake(player, (float) Math.min(0.9, 0.2 + damage / player.getMaxHealth() * 2.0), 5);
        if (attacker != null) {
            double bash = GearEffects.value(player, BASH);
            if (bash > 0) {
                Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? player.getLookAngle().multiply(1, 0, 1) : away;
                Vec3 push = away.normalize().scale(0.9 * bash).add(0, 0.25 * Math.min(bash, 1.5), 0);
                attacker.push(push.x, push.y, push.z);
                attacker.syncVelocity = true;
                Vfx.play(level, fx("shield_bash"), attacker.position());
                Vfx.sound(level, attacker.position(), "minecraft:entity.player.attack.knockback", 0.9f, 0.8f);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.7f);
                Vfx.shake(player, 0.45f, 6);
            }
            double ignite = GearEffects.value(player, IGNITE);
            if (ignite > 0) {
                attacker.igniteForSeconds((float) ignite);
                level.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY(0.5), attacker.getZ(), 10, 0.3, 0.4, 0.3, 0.04);
            }
            double frost = GearEffects.value(player, FROST);
            if (frost > 0) {
                Bows.freeze(attacker, frost);
            }
        }
        double max = GearEffects.value(player, CHARGE);
        if (max > 0) {
            int charges = Math.min((int) max, player.getAttachedOrElse(CHARGES, 0) + 1);
            player.setAttached(CHARGES, charges);
            Vfx.play(player, fx("shield_charge"));
            Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.7f + 1.0f * charges / (float) max);
            Vfx.sound(level, player.position(), "minecraft:block.copper_bulb.turn_on", 0.6f, 0.8f + 0.2f * charges);
            if (charges >= max) {
                Msg.playSound(player, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.9f);
            }
            player.sendOverlayMessage(Msg.text("Storm charge " + charges + "/" + (int) max
                    + (charges >= max ? " (sneak + use to release)" : ""), ChatFormatting.AQUA));
        }
    }

    /** Sneak + use: every stored charge becomes lightning around you. */
    private static void shockwave(ServerPlayer player, double damagePerCharge) {
        ServerLevel level = player.level();
        int charges = player.getAttachedOrElse(CHARGES, 0);
        player.setAttached(CHARGES, 0);
        double radius = 3 + charges;
        float damage = (float) (damagePerCharge * charges / StatTypes.HEALTH_SCALE);
        List<LivingEntity> hit = new java.util.ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(radius),
                e -> e instanceof Enemy && e.isAlive() && e.distanceTo(player) <= radius));
        Vfx.play(player, fx(radius <= 4 ? "shield_shockwave_s" : radius <= 6 ? "shield_shockwave_m" : "shield_shockwave_l"));
        Vfx.sound(level, player.position(), "minecraft:entity.lightning_bolt.thunder", 0.7f, 1.4f);
        Vfx.sound(level, player.position(), "minecraft:item.trident.thunder", 0.9f, 1.2f);
        Vfx.sound(level, player.position(), "minecraft:entity.breeze.wind_burst", 0.8f, 1.0f);
        Vfx.shake(player, 0.6f + 0.2f * charges, 10);
        Vfx.flash(player, 0x33BFE6FF, 6);
        hit.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        for (int i = 0; i < hit.size(); i++) {
            LivingEntity target = hit.get(i);
            int index = i;
            Vfx.later(level.getServer(), 2 + i * 2, () -> {
                if (player.isRemoved() || !target.isAlive()) {
                    return;
                }
                if (index < 6) {
                    bolt(level, target.position());
                }
                Vfx.play(level, fx("thunder_strike"), target.position());
                Vfx.sound(level, target.position(), "minecraft:entity.lightning_bolt.impact", 0.6f, 1.4f);
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                Vfx.shakeAround(level, target.position(), 12, 0.35f, 5);
            });
        }
        player.sendOverlayMessage(Msg.text("Shockwave! " + charges + " charge" + (charges == 1 ? "" : "s") + " released", ChatFormatting.AQUA));
    }

    /** A bolt of lightning that burns nothing and hurts nobody by itself. */
    public static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.setPos(at.x, at.y, at.z);
            level.addFreshEntity(bolt);
        }
    }

    /** A projectile about to hit a blocking player with a reflecting shield flies back at its shooter instead. */
    private static boolean reflect(ServerPlayer player, Projectile projectile, float amount) {
        if (GearEffects.value(player, REFLECT) <= 0 || !player.isBlocking()) {
            return false;
        }
        // From the front means it is flying against where you look (its position at the hit can already be past your middle).
        Vec3 flight = projectile.getDeltaMovement().multiply(1, 0, 1);
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (flight.lengthSqr() > 1e-4 && look.lengthSqr() > 1e-4 && flight.normalize().dot(look.normalize()) >= 0) {
            return false; // from behind
        }
        net.minecraft.world.entity.Entity shooter = projectile.getOwner();
        double speed = Math.max(1.2, projectile.getDeltaMovement().length()) * 1.1;
        Vec3 direction = shooter != null && shooter != player
                ? shooter.getEyePosition().subtract(projectile.position()).normalize()
                : projectile.getDeltaMovement().scale(-1).normalize();
        projectile.setOwner(player);
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.syncVelocity = true;
        // The hit that fails to hurt makes vanilla bounce the projectile off afterwards: set our velocity again once that is done.
        REFLECTED.add(new Reflected(projectile, direction.scale(speed)));
        if (projectile instanceof AbstractArrow arrow) {
            // Their own arrow, as hard as it hit you but in SkyRPG terms, so it counts for your kills.
            SkyArrows.tag(arrow, Math.max(60, amount * StatTypes.HEALTH_SCALE * 1.5));
        }
        ServerLevel level = player.level();
        Vfx.play(level, fx("shield_reflect"), projectile.position());
        Vfx.shake(player, 0.5f, 6);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
        return true;
    }
}
