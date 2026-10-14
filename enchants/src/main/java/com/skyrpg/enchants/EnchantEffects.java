package com.skyrpg.enchants;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the enchants that aren't plain stats or other mods' gear effects do. Each reads its numbers from its data file's
 * {@code values} at the level the item has, so balance stays in data:
 * <ul>
 *   <li>Melee damage bonuses (Keen, Hallowed Edge, Webcutter, Ambush, Reaper's Mark, Titanbane, Momentum, Soulrend) and
 *   arrow bonuses (Deadeye, Longshot) multiply the hit; Final Stand and Deflection cut what armour wearers take.</li>
 *   <li>On a melee hit: Cleave, Frostbite, Stormcaller (every third hit on the same foe), Rift Echo (every fifth hit
 *   strikes again a moment later). On a kill: Scavenger, Soul Siphon, Soulrend souls, Magnetism.</li>
 *   <li>Bows: Splitshot, Quiversong, Cinder Arrows and Starshot when an arrow leaves the bow.</li>
 *   <li>Armour: Rejuvenate (healing), Steadfast (knockback resistance), Phoenix Ember (cheats death).</li>
 *   <li>Tool ultimates ({@code "surge"} in their data): a chance while earning their skill's XP for a burst of a stat.</li>
 * </ul>
 * Extra hits these deal are magic damage from the player and don't trigger enchants again.
 */
public final class EnchantEffects {
    public static final Identifier KEEN = SkyRpgEnchants.id("keen");
    public static final Identifier HALLOWED_EDGE = SkyRpgEnchants.id("hallowed_edge");
    public static final Identifier WEBCUTTER = SkyRpgEnchants.id("webcutter");
    public static final Identifier AMBUSH = SkyRpgEnchants.id("ambush");
    public static final Identifier REAPERS_MARK = SkyRpgEnchants.id("reapers_mark");
    public static final Identifier TITANBANE = SkyRpgEnchants.id("titanbane");
    public static final Identifier MOMENTUM = SkyRpgEnchants.id("momentum");
    public static final Identifier CLEAVE = SkyRpgEnchants.id("cleave");
    public static final Identifier FROSTBITE = SkyRpgEnchants.id("frostbite");
    public static final Identifier STORMCALLER = SkyRpgEnchants.id("stormcaller");
    public static final Identifier SCAVENGER = SkyRpgEnchants.id("scavenger");
    public static final Identifier SOUL_SIPHON = SkyRpgEnchants.id("soul_siphon");
    public static final Identifier MAGNETISM = SkyRpgEnchants.id("magnetism");
    public static final Identifier SOULREND = SkyRpgEnchants.id("soulrend");
    public static final Identifier RIFT_ECHO = SkyRpgEnchants.id("rift_echo");
    public static final Identifier DEADEYE = SkyRpgEnchants.id("deadeye");
    public static final Identifier LONGSHOT = SkyRpgEnchants.id("longshot");
    public static final Identifier SPLITSHOT = SkyRpgEnchants.id("splitshot");
    public static final Identifier QUIVERSONG = SkyRpgEnchants.id("quiversong");
    public static final Identifier CINDER_ARROWS = SkyRpgEnchants.id("cinder_arrows");
    public static final Identifier STARSHOT = SkyRpgEnchants.id("starshot");
    public static final Identifier REJUVENATE = SkyRpgEnchants.id("rejuvenate");
    public static final Identifier STEADFAST = SkyRpgEnchants.id("steadfast");
    public static final Identifier DEFLECTION = SkyRpgEnchants.id("deflection");
    public static final Identifier FINAL_STAND = SkyRpgEnchants.id("final_stand");
    public static final Identifier PHOENIX_EMBER = SkyRpgEnchants.id("phoenix_ember");

    private static final String SEEN_TAG = "skyrpg_ench_shot";
    private static final String SPLIT_TAG = "skyrpg_ench_split";
    private static final String STAR_TAG = "skyrpg_ench_star";
    private static final Identifier STEADFAST_MODIFIER = SkyRpgEnchants.id("steadfast");
    private static final int MOMENTUM_WINDOW = 60;
    private static final int MOMENTUM_STACKS = 5;
    private static final int SOUL_TICKS = 400;
    private static final int SURGE_COOLDOWN = 600;
    private static final double MAX_CUT = 0.6;

    /** Set while one of our own extra hits is being dealt, so it doesn't set off enchants again. */
    private static final ThreadLocal<Boolean> GUARD = ThreadLocal.withInitial(() -> false);

    private record Storm(UUID target, int count) {
    }

    private record Combo(long last, int stacks) {
    }

    private record Task(long due, Runnable run) {
    }

    private static final Map<UUID, Storm> STORMS = new HashMap<>();
    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static final Map<UUID, Integer> ECHO_COUNTS = new HashMap<>();
    private static final Map<UUID, Deque<Long>> SOULS = new HashMap<>();
    private static final Map<UUID, Integer> STAR_COUNTS = new HashMap<>();
    private static final Map<UUID, Long> PHOENIX_READY = new HashMap<>();
    private static final Map<UUID, Long> SURGE_READY = new HashMap<>();
    private static final Map<Entity, Vec3> SHOT_FROM = new WeakHashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static MinecraftServer server;

    private EnchantEffects() {
    }

    static void init() {
        ServerTickEvents.START_SERVER_TICK.register(running -> server = running);
        IncomingDamage.registerHit(EnchantEffects::multiplier);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
            if (GUARD.get() || taken <= 0 || !(source.getEntity() instanceof ServerPlayer player) || entity == player) {
                return;
            }
            if (source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK)) {
                onMelee(player, entity, taken);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                onArrowHit(player, arrow, entity, taken);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player)) {
                onKill(player, entity);
            }
        });
        // Not from the void or /kill: those would only kill you again.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !phoenix(player));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer player && !arrow.entityTags().contains(SEEN_TAG)
                    && arrow.getDeltaMovement().lengthSqr() > 0.25) {
                arrow.addTag(SEEN_TAG);
                onShot(player, arrow, level);
            }
        });
        SkillLevels.XP_EARNED.register(EnchantEffects::surge);
        ServerTickEvents.END_SERVER_TICK.register(EnchantEffects::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, running) -> {
            UUID id = handler.player.getUUID();
            STORMS.remove(id);
            COMBOS.remove(id);
            ECHO_COUNTS.remove(id);
            SOULS.remove(id);
            STAR_COUNTS.remove(id);
        });
    }

    // ---------------------------------------------------------------- helpers

    private static double value(ItemStack stack, Identifier enchant, String key) {
        int level = EnchantSources.activeLevel(stack, enchant);
        Enchant def = level > 0 ? Enchants.get(enchant) : null;
        return def == null ? 0 : def.value(key, level);
    }

    private static long now() {
        return server == null ? 0 : server.getTickCount();
    }

    private static void schedule(int delay, Runnable run) {
        TASKS.add(new Task(now() + delay, run));
    }

    /** Deals {@code amount} (vanilla health) of our own damage to a mob, through its invulnerability frames. */
    private static void strike(ServerPlayer player, LivingEntity target, double amount) {
        if (amount <= 0 || !target.isAlive()) {
            return;
        }
        DamageSource source = player.damageSources().indirectMagic(player, player);
        int frames = target.getInvulnerableTime();
        GUARD.set(true);
        try {
            // The stats mod applies other mods' multipliers (slayer bosses' toughness) to every hit; this damage is
            // already a share of a hit that had them, so take them back out first.
            double multiplier = IncomingDamage.multiplier(target, source);
            target.setInvulnerableTime(0);
            target.hurtServer(player.level(), source, (float) (amount / Math.max(0.01, multiplier)));
        } finally {
            GUARD.set(false);
            target.setInvulnerableTime(frames);
        }
    }

    private static List<LivingEntity> enemiesAround(ServerPlayer player, LivingEntity center, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                e -> e != center && e != player && e.isAlive() && e instanceof Enemy && e.distanceTo(center) <= radius);
    }

    private static void particles(ServerLevel level, ParticleOptions type, Entity at, int count, double spread, double speed) {
        level.sendParticles(type, at.getX(), at.getY(0.5), at.getZ(), count, spread, spread, spread, speed);
    }

    private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---------------------------------------------------------------- damage multipliers

    private static double multiplier(LivingEntity target, DamageSource source) {
        if (GUARD.get()) {
            return 1;
        }
        double multiplier = 1;
        if (source.getEntity() instanceof ServerPlayer player && target != player) {
            if (source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK)) {
                multiplier *= 1 + meleeBonus(player, target);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                multiplier *= 1 + arrowBonus(arrow, target);
            }
        }
        if (target instanceof ServerPlayer defender) {
            double cut = 0;
            if (defender.getHealth() < defender.getMaxHealth() * 0.4) {
                cut += EnchantSources.wornTotal(defender, FINAL_STAND, "cut");
            }
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                cut += EnchantSources.wornTotal(defender, DEFLECTION, "cut");
            }
            multiplier *= 1 - Math.min(MAX_CUT, cut);
        }
        return multiplier;
    }

    private static double meleeBonus(ServerPlayer player, LivingEntity target) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !weapon.has(EnchantContent.ENCHANTS)) {
            return 0;
        }
        double bonus = value(weapon, KEEN, "dmg");
        if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)) {
            bonus += value(weapon, HALLOWED_EDGE, "dmg");
        }
        if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.ARTHROPOD)) {
            bonus += value(weapon, WEBCUTTER, "dmg");
        }
        if (target.getHealth() >= target.getMaxHealth()) {
            bonus += value(weapon, AMBUSH, "dmg");
        }
        double missing = (1 - target.getHealth() / Math.max(1, target.getMaxHealth())) * 100;
        bonus += value(weapon, REAPERS_MARK, "per") * Math.max(0, missing);
        if (target.getMaxHealth() > player.getMaxHealth()) {
            bonus += value(weapon, TITANBANE, "dmg");
        }
        Combo combo = COMBOS.get(player.getUUID());
        if (combo != null && now() - combo.last() <= MOMENTUM_WINDOW) {
            bonus += value(weapon, MOMENTUM, "per") * combo.stacks();
        }
        bonus += value(weapon, SOULREND, "per") * souls(player);
        return bonus;
    }

    private static double arrowBonus(AbstractArrow arrow, LivingEntity target) {
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null || bow.isEmpty()) {
            return 0;
        }
        double bonus = value(bow, DEADEYE, "dmg");
        Vec3 from = SHOT_FROM.get(arrow);
        if (from != null) {
            bonus += value(bow, LONGSHOT, "per") * Math.clamp(from.distanceTo(target.position()) - 5, 0, 25);
        }
        return bonus;
    }

    // ---------------------------------------------------------------- melee

    private static void onMelee(ServerPlayer player, LivingEntity target, float taken) {
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.has(EnchantContent.ENCHANTS)) {
            return;
        }
        ServerLevel level = player.level();
        double cleave = value(weapon, CLEAVE, "share");
        if (cleave > 0) {
            double radius = value(weapon, CLEAVE, "radius");
            List<LivingEntity> others = enemiesAround(player, target, radius);
            for (LivingEntity other : others) {
                strike(player, other, taken * cleave);
                particles(level, ParticleTypes.SWEEP_ATTACK, other, 1, 0, 0);
            }
            if (!others.isEmpty()) {
                sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
            }
        }
        double frost = value(weapon, FROSTBITE, "secs");
        if (frost > 0 && target.isAlive()) {
            int ticks = (int) (frost * 20);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), player);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks * 2));
            particles(level, ParticleTypes.SNOWFLAKE, target, 12, 0.35, 0.03);
        }
        double storm = value(weapon, STORMCALLER, "share");
        if (storm > 0) {
            Storm last = STORMS.get(player.getUUID());
            int count = last != null && last.target().equals(target.getUUID()) ? last.count() + 1 : 1;
            STORMS.put(player.getUUID(), new Storm(target.getUUID(), count % 3));
            if (count >= 3) {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.setPos(target.getX(), target.getY(), target.getZ());
                    level.addFreshEntity(bolt);
                }
                particles(level, ParticleTypes.ELECTRIC_SPARK, target, 24, 0.4, 0.3);
                strike(player, target, taken * storm);
            }
        }
        if (value(weapon, MOMENTUM, "per") > 0) {
            Combo combo = COMBOS.get(player.getUUID());
            int stacks = combo != null && now() - combo.last() <= MOMENTUM_WINDOW ? Math.min(MOMENTUM_STACKS, combo.stacks() + 1) : 1;
            COMBOS.put(player.getUUID(), new Combo(now(), stacks));
            if (stacks == MOMENTUM_STACKS) {
                particles(level, ParticleTypes.CRIT, player, 6, 0.4, 0.1);
            }
        }
        double echo = value(weapon, RIFT_ECHO, "share");
        if (echo > 0) {
            int count = ECHO_COUNTS.merge(player.getUUID(), 1, Integer::sum);
            if (count >= 5) {
                ECHO_COUNTS.put(player.getUUID(), 0);
                double amount = taken * echo;
                particles(level, ParticleTypes.REVERSE_PORTAL, target, 30, 0.4, 0.05);
                schedule(10, () -> {
                    if (target.isAlive() && player.isAlive() && target.level() == player.level()) {
                        particles(player.level(), ParticleTypes.PORTAL, target, 40, 0.5, 0.6);
                        sound(target, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.6f);
                        strike(player, target, amount);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- bows

    private static void onShot(ServerPlayer player, AbstractArrow arrow, ServerLevel level) {
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null || !bow.has(EnchantContent.ENCHANTS)) {
            return;
        }
        SHOT_FROM.put(arrow, arrow.position());
        if (value(bow, CINDER_ARROWS, "on") > 0) {
            arrow.igniteForSeconds(100);
        }
        if (arrow.entityTags().contains(SPLIT_TAG)) {
            return;
        }
        if (player.getRandom().nextDouble() < value(bow, SPLITSHOT, "chance")) {
            // Next tick: no adding entities while one is being loaded.
            schedule(0, () -> {
                for (int side = -1; side <= 1; side += 2) {
                    split(player, arrow, bow, side * 9.0);
                }
            });
        }
        if (!player.isCreative() && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                && player.getRandom().nextDouble() < value(bow, QUIVERSONG, "chance")) {
            ItemStack refund = arrow.getPickupItemStackOrigin().copyWithCount(1);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            if (!player.getInventory().add(refund)) {
                player.drop(refund, false, Prediction.SERVER_ONLY);
            }
            level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.1, player.getZ(), 1, 0, 0, 0, 0.5);
        }
        if (value(bow, STARSHOT, "share") > 0) {
            int count = STAR_COUNTS.merge(player.getUUID(), 1, Integer::sum);
            if (count >= 4) {
                STAR_COUNTS.put(player.getUUID(), 0);
                arrow.addTag(STAR_TAG);
                sound(player, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.7f, 1.4f);
            }
        }
    }

    private static void split(ServerPlayer player, AbstractArrow original, ItemStack bow, double degrees) {
        if (!original.isAlive() && original.getDeltaMovement().lengthSqr() < 0.01) {
            return;
        }
        ServerLevel level = player.level();
        Arrow extra = new Arrow(level, player, original.getPickupItemStackOrigin().copyWithCount(1), bow);
        extra.addTag(SEEN_TAG);
        extra.addTag(SPLIT_TAG);
        extra.setPos(original.getX(), original.getY(), original.getZ());
        Vec3 velocity = original.getDeltaMovement().yRot((float) Math.toRadians(degrees));
        extra.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0);
        extra.setCritArrow(original.isCritArrow());
        extra.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        if (original.isOnFire()) {
            extra.igniteForSeconds(100);
        }
        level.addFreshEntity(extra);
        SHOT_FROM.put(extra, player.position());
    }

    private static void onArrowHit(ServerPlayer player, AbstractArrow arrow, LivingEntity target, float taken) {
        if (!arrow.entityTags().contains(STAR_TAG)) {
            return;
        }
        arrow.removeTag(STAR_TAG);
        ItemStack bow = arrow.getWeaponItem();
        double share = bow == null ? 0 : value(bow, STARSHOT, "share");
        double radius = bow == null ? 3 : Math.max(2, value(bow, STARSHOT, "radius"));
        ServerLevel level = player.level();
        for (int i = 0; i < 8; i++) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 4 - i * 0.5, target.getZ(), 2, 0.1, 0.05, 0.1, 0.01);
        }
        level.sendParticles(ParticleTypes.FIREWORK, target.getX(), target.getY(0.5), target.getZ(), 40, radius / 3, 0.4, radius / 3, 0.2);
        sound(target, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.9f);
        for (LivingEntity other : enemiesAround(player, target, radius)) {
            strike(player, other, taken * share);
        }
        strike(player, target, taken * share * 0.5);
    }

    // ---------------------------------------------------------------- kills

    private static void onKill(ServerPlayer player, LivingEntity victim) {
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.has(EnchantContent.ENCHANTS)) {
            return;
        }
        ServerLevel level = player.level();
        boolean farmed = victim.entityTags().contains(MobTags.FARMED);
        double perHeart = value(weapon, SCAVENGER, "heart");
        if (perHeart > 0 && !farmed) {
            long coins = Math.round(perHeart * victim.getMaxHealth() / 2);
            if (coins > 0) {
                Wallet.give(player, coins);
                player.sendOverlayMessage(Msg.text("+" + Msg.number(coins) + " coins", ChatFormatting.GOLD)
                        .append(Msg.text("  Scavenger", ChatFormatting.DARK_GRAY)));
            }
        }
        double siphon = value(weapon, SOUL_SIPHON, "heal");
        if (siphon > 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal((float) (player.getMaxHealth() * siphon));
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2, player.getZ(), 2, 0.3, 0.1, 0.3, 0);
        }
        if (value(weapon, SOULREND, "per") > 0) {
            Deque<Long> souls = SOULS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            int max = (int) Math.max(1, value(weapon, SOULREND, "souls"));
            while (souls.size() >= max) {
                souls.pollFirst();
            }
            souls.addLast(now() + SOUL_TICKS);
            Vec3 from = victim.position().add(0, victim.getBbHeight() / 2, 0);
            Vec3 to = player.position().add(0, 1, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 at = from.lerp(to, i / 8.0);
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        if (value(weapon, MAGNETISM, "on") > 0) {
            AABB area = victim.getBoundingBox().inflate(1.5);
            schedule(1, () -> vacuum(player, level, area));
        }
    }

    private static int souls(ServerPlayer player) {
        Deque<Long> souls = SOULS.get(player.getUUID());
        if (souls == null) {
            return 0;
        }
        while (!souls.isEmpty() && souls.peekFirst() < now()) {
            souls.pollFirst();
        }
        return souls.size();
    }

    /** Magnetism: a fresh kill's drops and experience fly straight into the killer's bag. */
    private static void vacuum(ServerPlayer player, ServerLevel level, AABB area) {
        if (!player.isAlive() || player.level() != level) {
            return;
        }
        boolean took = false;
        // Only what dropped this moment, so nothing else lying around gets swept up.
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getAge() <= 2)) {
            ItemStack stack = drop.getItem().copy();
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(stack);
            }
            took = true;
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area, e -> e.isAlive() && e.tickCount <= 2)) {
            player.giveExperiencePoints(orb.getValue());
            orb.discard();
            took = true;
        }
        if (took) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.6f);
        }
    }

    // ---------------------------------------------------------------- armour

    /** Phoenix Ember: instead of dying, rise again (once per cooldown). */
    private static boolean phoenix(ServerPlayer player) {
        int level = EnchantSources.worn(player, EquipmentSlot.CHEST, PHOENIX_EMBER);
        if (level <= 0 || now() < PHOENIX_READY.getOrDefault(player.getUUID(), 0L)) {
            return false;
        }
        Enchant def = Enchants.get(PHOENIX_EMBER);
        PHOENIX_READY.put(player.getUUID(), now() + (long) (def.value("cooldown", level) * 20));
        player.setHealth((float) (player.getMaxHealth() * def.value("revive", level)));
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        ServerLevel world = player.level();
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
        world.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 80, 1.2, 0.1, 1.2, 0.08);
        sound(player, SoundEvents.TOTEM_USE, 0.8f, 1.3f);
        sound(player, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("✦ Phoenix Ember ✦")
                .withStyle(Style.EMPTY.withColor(0xFFFF8A3D).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("You rise from the ashes")
                .withStyle(ChatFormatting.GOLD)));
        return true;
    }

    // ---------------------------------------------------------------- tool ultimates

    /** Tool ultimates: {@code "surge": {"skill": "skyrpg_skills:mining", "stat": "mining_fortune"}} with chance, amount and seconds values. */
    private static void surge(ServerPlayer player, Identifier skill, double xp) {
        ItemStack held = player.getMainHandItem();
        if (!held.has(EnchantContent.ENCHANTS) || now() < SURGE_READY.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        for (Map.Entry<Identifier, Integer> entry : EnchantContent.enchants(held).levels().entrySet()) {
            Enchant def = Enchants.get(entry.getKey());
            if (def == null || !def.json().has("surge")) {
                continue;
            }
            var surge = def.json().getAsJsonObject("surge");
            int level = EnchantSources.activeLevel(held, def.id());
            if (level <= 0 || !skill.toString().equals(surge.get("skill").getAsString())
                    || player.getRandom().nextDouble() >= def.value("chance", level)) {
                continue;
            }
            StatType stat = StatTypes.get(surge.get("stat").getAsString());
            if (stat == null) {
                continue;
            }
            double amount = def.value("amount", level);
            int seconds = (int) Math.max(1, def.value("seconds", level));
            SURGE_READY.put(player.getUUID(), now() + SURGE_COOLDOWN + seconds * 20L);
            Boosts.give(player, "surge:" + def.id(), Component.literal(def.name()), Map.of(stat.id(), amount), Map.of(), seconds * 20);
            ServerLevel world = player.level();
            world.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 0.8, 0.6, 0.8);
            world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.7, 0.5, 0.05);
            sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
            sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
            player.sendOverlayMessage(Component.literal("✦ " + def.name().toUpperCase(java.util.Locale.ROOT) + "! ")
                    .withStyle(Style.EMPTY.withColor(EnchantRarity.ULTIMATE.color()).withBold(true))
                    .append(Component.literal(stat.formatBonus(amount) + " " + stat.name() + " for " + seconds + "s")
                            .withStyle(Style.EMPTY.withColor(stat.color()).withBold(false))));
            return;
        }
    }

    // ---------------------------------------------------------------- every tick

    private static void tick(MinecraftServer running) {
        server = running;
        long now = running.getTickCount();
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.due() <= now) {
                    due.add(task);
                    it.remove();
                }
            }
            due.forEach(task -> task.run().run());
        }
        if (now % 2 == 0 && !SHOT_FROM.isEmpty()) {
            for (Entity arrow : List.copyOf(SHOT_FROM.keySet())) {
                if (arrow.isRemoved() || arrow.tickCount > 6000) {
                    SHOT_FROM.remove(arrow);
                } else if (arrow.entityTags().contains(STAR_TAG) && arrow.getDeltaMovement().lengthSqr() > 0.01
                        && arrow.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.02, 0.02, 0.02, 0.005);
                }
            }
        }
        if (now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            if (now % 60 == 0) {
                double heal = EnchantSources.wornTotal(player, REJUVENATE, "heal");
                if (heal > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
                    player.heal((float) (player.getMaxHealth() * heal));
                }
            }
            AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knockback != null) {
                double steadfast = EnchantSources.wornTotal(player, STEADFAST, "kb");
                AttributeModifier current = knockback.getModifier(STEADFAST_MODIFIER);
                if (current == null ? steadfast > 0 : current.amount() != steadfast) {
                    knockback.removeModifier(STEADFAST_MODIFIER);
                    if (steadfast > 0) {
                        knockback.addTransientModifier(new AttributeModifier(STEADFAST_MODIFIER, steadfast, AttributeModifier.Operation.ADD_VALUE));
                    }
                }
            }
            int souls = souls(player);
            if (souls > 0) {
                player.level().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.2, player.getZ(), Math.min(souls, 4),
                        0.5, 0.4, 0.5, 0.01);
            }
        }
    }
}
