package com.skyrpg.combat.slayer;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.boss.BoneMarshalEntity;
import com.skyrpg.combat.slayer.boss.TieredBoss;
import com.skyrpg.combat.slayer.boss.GraveTyrantEntity;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Slayer boss fights. A boss rises next to the player whose quest bar filled, hunts them, and has a time limit. It leaves
 * (and comes back with the time that was left) if they log off, change dimension or go to the hub. Every tier is a new
 * form of the boss (its own model, bigger each tier) and adds one attack and one defence on top of the tiers below:
 * the older signature moves are here and in {@link BossMoves}, the rest and the full catalogue in {@link SlayerAbilities}.
 * Minions are "farmed" mobs: little XP and no rewards.
 */
public final class SlayerBosses {
    static final String BOSS_TAG = "skyrpg_slayer_boss";
    static final String MINION_TAG = "skyrpg_slayer_minion";
    private static final double LEASH = 32;
    private static final double BAR_RANGE = 40;
    private static final int MAX_MINIONS = 8;
    /** A boss that hasn't hit its owner for this long comes to them (no cheesing from a pillar). */
    private static final int UNSTUCK = 300;

    static final class Run {
        final UUID owner;
        final SlayerLine line;
        final int tierNumber;
        final SlayerLine.Tier tier;
        final ServerBossEvent bar;
        final List<Mob> minions = new ArrayList<>();
        Mob boss;
        ServerLevel level;
        long started;
        long lastHit;
        long shieldUntil;
        int nullHits;
        int phase;
        boolean enraged;
        boolean bannerPlanted;
        // Tier abilities (SlayerAbilities).
        long boneArmourUntil;
        boolean undyingUsed;
        int cocoonHits;
        long cocoonUntil;
        boolean cocoonUsed;
        long retreatReadyAt;
        boolean collapseUsed;

        Run(UUID owner, SlayerLine line, int tierNumber, SlayerLine.Tier tier) {
            this.owner = owner;
            this.line = line;
            this.tierNumber = tierNumber;
            this.tier = tier;
            this.bar = new ServerBossEvent(UUID.randomUUID(), title(line, tierNumber), color(line.kind()), BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    /** A void rift: bursts where it opened, a moment later. */
    private record Rift(ServerLevel level, Vec3 at, long bursts, Mob boss, double damage) {
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** When each boss's time runs out (server ticks); kept while the boss is away. */
    private static final Map<UUID, Long> DEADLINES = new HashMap<>();
    private static final List<Rift> RIFTS = new ArrayList<>();
    private static long now;

    private SlayerBosses() {
    }

    static void init() {
        BossMoves.init();
        SlayerAbilities.init();
        ServerTickEvents.END_SERVER_TICK.register(SlayerBosses::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RUNS.clear();
            DEADLINES.clear();
            RIFTS.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Run run = RUNS.remove(handler.getPlayer().getUUID());
            if (run != null) {
                takeDown(run);
            }
        });
        IncomingDamage.register(SlayerBosses::damageTaken);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0) {
                return;
            }
            if (entity instanceof ServerPlayer player && source.getEntity() instanceof Mob attacker && attacker.entityTags().contains(BOSS_TAG)) {
                Run run = byBoss(attacker);
                if (run != null) {
                    bossHit(run, player, source.getDirectEntity() == attacker);
                }
            } else if (entity instanceof Mob mob && mob.entityTags().contains(BOSS_TAG)) {
                Run run = byBoss(mob);
                boolean byPlayer = source.getEntity() instanceof ServerPlayer
                        || source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer;
                if (run != null && byPlayer && run.nullHits > 0) {
                    run.nullHits--;
                    if (run.nullHits == 0) {
                        sound(run, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
                        particles(run.level, ParticleTypes.REVERSE_PORTAL, mob.position().add(0, 1.5, 0), 60, 0.8, 0.12);
                        tell(run, Msg.text("The null shield shatters!", ChatFormatting.LIGHT_PURPLE));
                    }
                    updateBar(run);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob && mob.entityTags().contains(BOSS_TAG)) {
                Run run = byBoss(mob);
                if (run == null) {
                    return;
                }
                RUNS.remove(run.owner);
                DEADLINES.remove(run.owner);
                takeDown(run);
                ServerPlayer owner = run.level.getServer().getPlayerList().getPlayer(run.owner);
                if (owner != null) {
                    Slayers.complete(owner, run.line, run.tierNumber, run.tier, (int) Math.min(Integer.MAX_VALUE, Math.max(1, now - run.started)));
                }
            }
        });
    }

    // ---------------------------------------------------------------- the quest's side

    /** The quest bar is full: the boss rises now (or as soon as the player is somewhere it can). */
    static void summon(ServerPlayer owner, SlayerLine line, int tierNumber, SlayerLine.Tier tier) {
        DEADLINES.put(owner.getUUID(), now + tier.minutes() * 1200L);
        owner.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        owner.connection.send(new ClientboundSetTitleTextPacket(Component.literal(line.name().toUpperCase()).withColor(line.color() & 0xFFFFFF)
                .withStyle(ChatFormatting.BOLD)));
        owner.connection.send(new ClientboundSetSubtitleTextPacket(Msg.text("has risen! Beat it within " + tier.minutes() + " minutes.",
                ChatFormatting.GRAY)));
        if (canFight(owner)) {
            spawn(owner, line, tierNumber, tier);
        }
    }

    /** Ends the fight for good (quest over): no boss, no timer. */
    static void despawn(ServerPlayer owner) {
        DEADLINES.remove(owner.getUUID());
        Run run = RUNS.remove(owner.getUUID());
        if (run != null) {
            takeDown(run);
        }
    }

    static void ownerDied(ServerPlayer owner) {
        SlayerState.Quest quest = Slayers.quest(owner);
        if (quest != null && quest.bossUp()) {
            fail(owner, "You died.");
        }
    }

    static int secondsLeft(ServerPlayer owner) {
        Long deadline = DEADLINES.get(owner.getUUID());
        return deadline == null ? 0 : (int) Math.max(0, (deadline - now) / 20);
    }

    /** Slayer bosses and their minions (their kills don't fill quest bars). */
    static boolean isSlayerMob(Entity entity) {
        return entity.entityTags().contains(BOSS_TAG) || entity.entityTags().contains(MINION_TAG);
    }

    private static void fail(ServerPlayer owner, String why) {
        Slayers.endQuest(owner);
        Notice.of(Notice.Kind.SLAYER).icon(Notice.CROSS).tag("QUEST FAILED").color(0xFF5555).send(owner, Msg.text(why, ChatFormatting.GRAY));
        Msg.playSound(owner, SoundEvents.WITHER_SHOOT, 0.6f, 0.5f);
    }

    private static boolean canFight(ServerPlayer owner) {
        return owner.isAlive() && !owner.isSpectator() && !owner.level().dimension().identifier().getNamespace().equals("skyrpg_hub");
    }

    // ---------------------------------------------------------------- spawning

    private static void spawn(ServerPlayer owner, SlayerLine line, int tierNumber, SlayerLine.Tier tier) {
        ServerLevel level = owner.level();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(line.boss()).orElse(null);
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob boss)) {
            SkyRpgCombat.LOGGER.warn("Slayer line {} has no usable boss {}", line.id(), line.boss());
            return;
        }
        // Max health stops at 1024 (5,120 in SkyRPG units); tougher bosses take less damage instead.
        double health = tier.health() / StatTypes.HEALTH_SCALE;
        float toughness = (float) Math.max(1, health / 1024);
        set(boss, Attributes.MAX_HEALTH, Math.min(1024, health));
        multiply(boss, Attributes.ATTACK_DAMAGE, tier.damage());
        // Each tier is visibly bigger than the last.
        set(boss, Attributes.SCALE, scale(line.kind()) * (0.88 + 0.12 * tierNumber));
        multiply(boss, Attributes.MOVEMENT_SPEED, 1.1);
        // Bone Plating: the Bone Marshal can't be knocked back at all.
        set(boss, Attributes.KNOCKBACK_RESISTANCE, line.kind().equals("bone_marshal") ? 1 : Math.min(1, 0.5 + 0.12 * tierNumber));
        set(boss, Attributes.FOLLOW_RANGE, 40);
        set(boss, Attributes.STEP_HEIGHT, 1.1);
        boss.setHealth(boss.getMaxHealth());
        boss.setAttached(MobLevels.INFO, new MobLevels.MobInfo(tier.level(), false, toughness));
        boss.addTag(BOSS_TAG);
        // Other mods' drops can depend on the tier (enchanting's tomes).
        boss.addTag(BOSS_TAG + "_tier_" + tierNumber);
        boss.setCustomName(title(line, tierNumber));
        boss.setPersistenceRequired();
        if (boss instanceof TieredBoss tiered) {
            tiered.setTier(tierNumber);
        }
        if (boss instanceof BoneMarshalEntity marshal) {
            marshal.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            marshal.setDropChance(EquipmentSlot.MAINHAND, 0);
            marshal.setArrowDamage(2 * tier.damage());
            marshal.reassessWeaponGoal();
        }
        placeNear(level, owner, boss, 4, 7);
        level.addFreshEntity(boss);
        boss.setTarget(owner);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(boss.getX(), boss.getY(), boss.getZ(), 0, 0);
            level.addFreshEntity(bolt);
        }
        particles(level, ParticleTypes.SOUL, boss.position().add(0, 1, 0), 40, 0.8, 0.05);

        Run run = new Run(owner.getUUID(), line, tierNumber, tier);
        run.boss = boss;
        run.level = level;
        run.started = now;
        run.lastHit = now;
        RUNS.put(owner.getUUID(), run);
        updateBar(run);
    }

    /** Somewhere free {@code min} to {@code max} blocks from the player, on solid ground near their height. */
    private static void placeNear(ServerLevel level, ServerPlayer owner, Mob mob, double min, double max) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            double x = owner.getX() + Math.cos(angle) * distance;
            double z = owner.getZ() + Math.sin(angle) * distance;
            for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                BlockPos feet = BlockPos.containing(x, owner.getY() + dy, z);
                BlockPos below = feet.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    continue;
                }
                mob.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360, 0);
                if (level.noCollision(mob) && !level.containsAnyLiquid(mob.getBoundingBox())) {
                    return;
                }
            }
        }
        mob.snapTo(owner.getX(), owner.getY(), owner.getZ(), random.nextFloat() * 360, 0);
    }

    private static double scale(String kind) {
        return switch (kind) {
            case "grave_tyrant" -> 1.45;
            case "broodmother" -> 1.35;
            case "bone_marshal" -> 1.3;
            case "void_stalker" -> 1.15;
            default -> 1.3;
        };
    }

    private static BossEvent.BossBarColor color(String kind) {
        return switch (kind) {
            case "grave_tyrant" -> BossEvent.BossBarColor.GREEN;
            case "broodmother" -> BossEvent.BossBarColor.RED;
            case "bone_marshal" -> BossEvent.BossBarColor.WHITE;
            case "void_stalker" -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    private static MutableComponent title(SlayerLine line, int tierNumber) {
        return Component.literal(line.name() + " " + Msg.roman(tierNumber)).withColor(line.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
    }

    private static void set(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void multiply(Mob mob, Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    /** The boss leaves (and its minions with it); its quest and timer stay. */
    private static void takeDown(Run run) {
        BossMoves.clear(run);
        run.bar.removeAllPlayers();
        if (run.boss != null && run.boss.isAlive()) {
            run.boss.discard();
        }
        run.minions.forEach(minion -> {
            if (minion.isAlive()) {
                minion.discard();
            }
        });
        RIFTS.removeIf(rift -> rift.boss() == run.boss);
    }

    static Run byBoss(Entity boss) {
        for (Run run : RUNS.values()) {
            if (run.boss == boss) {
                return run;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- the fight

    private static void tick(MinecraftServer server) {
        now = server.getTickCount();
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            SlayerState.Quest quest = Slayers.quest(owner);
            if (quest == null || !quest.bossUp()) {
                continue;
            }
            SlayerLine line = Slayers.line(quest.line());
            SlayerLine.Tier tier = line == null ? null : line.tier(quest.tier());
            if (tier == null) {
                continue;
            }
            long deadline = DEADLINES.computeIfAbsent(owner.getUUID(), uuid -> now + tier.minutes() * 1200L);
            if (now >= deadline) {
                fail(owner, "Time's up: the " + line.name() + " got away.");
                continue;
            }
            Run run = RUNS.get(owner.getUUID());
            if (run != null && (run.boss.isRemoved() && !run.boss.isDeadOrDying() || run.level != owner.level() || !canFight(owner))) {
                RUNS.remove(owner.getUUID());
                takeDown(run);
                run = null;
            }
            if (run == null) {
                if (now % 20 == 0 && canFight(owner)) {
                    spawn(owner, line, quest.tier(), tier);
                }
                continue;
            }
            fight(run, owner);
        }
        RUNS.values().removeIf(run -> {
            if (server.getPlayerList().getPlayer(run.owner) == null) {
                takeDown(run);
                return true;
            }
            return false;
        });
        rifts();
        BossMoves.tick(now);
        SlayerAbilities.tickBlocks(now);
        if (now % 100 == 0) {
            sweep(server);
        }
    }

    private static void fight(Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        if (!boss.isAlive()) {
            return;
        }
        long t = now - run.started;
        int tier = run.tierNumber;
        if (boss.distanceTo(owner) > LEASH || now - run.lastHit > UNSTUCK && boss.distanceTo(owner) > 4) {
            particles(run.level, ParticleTypes.SOUL, boss.position().add(0, 1, 0), 20, 0.5, 0.05);
            placeNear(run.level, owner, boss, 2, 4);
            particles(run.level, ParticleTypes.SOUL, boss.position().add(0, 1, 0), 20, 0.5, 0.05);
            run.lastHit = now;
        }
        if (now % 20 == 0) {
            if (boss.getTarget() != owner && !owner.isCreative()) {
                boss.setTarget(owner);
            }
            updateBar(run);
        }
        float health = boss.getHealth() / boss.getMaxHealth();
        int phase = health < 0.33f ? 2 : health < 0.66f ? 1 : 0;
        boolean newPhase = phase > run.phase;
        run.phase = Math.max(run.phase, phase);
        if (t <= 0) {
            return;
        }
        switch (run.line.kind()) {
            case "grave_tyrant" -> {
                if (t % 240 == 60) {
                    BossMoves.graveSlam(run, owner);
                    BossMoves.later(run, 30, () -> SlayerAbilities.afterSlam(run));
                }
                if (tier >= 2 && !run.enraged && health < 0.5f) {
                    run.enraged = true;
                    BossMoves.enrage(run);
                    updateBar(run);
                }
                if (run.enraged && t % 20 == 0) {
                    particles(run.level, ParticleTypes.ANGRY_VILLAGER, boss.position().add(0, 2.2, 0), 1, 0.4, 0);
                }
                if (tier >= 2 && t % 300 == 0) {
                    raise(run, owner, EntityTypes.ZOMBIE, tier >= 4 ? 3 : 2, "raises the dead!");
                }
                if (tier >= 4 && t % 20 == 0) {
                    plagueAura(run);
                }
                if (tier >= 3 && t % 300 == 200) {
                    BossMoves.gravestones(run, owner);
                }
            }
            case "broodmother" -> {
                if (t % 160 == 0) {
                    webShot(run, owner);
                }
                if (tier >= 2 && t % 180 == 90) {
                    BossMoves.pounce(run, owner);
                }
                if (tier >= 3 && newPhase) {
                    BossMoves.eggSacs(run);
                }
                if (tier >= 3 && t % 300 == 150) {
                    raise(run, owner, EntityTypes.CAVE_SPIDER, tier >= 4 ? 4 : 3, "hatches her brood!");
                }
            }
            case "bone_marshal" -> {
                if (t % 200 == 0 && boss instanceof BoneMarshalEntity marshal && boss.distanceTo(owner) < 24) {
                    for (int i = 0; i < (tier >= 2 ? 5 : 3); i++) {
                        marshal.performRangedAttack(owner, 1.0f);
                    }
                    sound(run, SoundEvents.SKELETON_SHOOT, 1.2f, 0.7f);
                }
                if (tier >= 2 && t % 280 == 140) {
                    BossMoves.arrowRain(run, owner);
                }
                if (tier >= 3 && !run.bannerPlanted && health < 0.5f) {
                    run.bannerPlanted = true;
                    BossMoves.warBanner(run);
                }
                if (tier >= 4 && t % 400 == 300) {
                    run.shieldUntil = now + 80;
                    sound(run, SoundEvents.SHIELD_BLOCK.value(), 1.2f, 0.6f);
                    tell(run, Msg.text("The Bone Marshal raises a bone shield!", ChatFormatting.WHITE));
                    updateBar(run);
                }
                if (run.shieldUntil > now && t % 4 == 0) {
                    ring(run.level, ParticleTypes.WHITE_ASH, boss.position().add(0, 1.2, 0), 1.3, 12);
                }
                if (run.shieldUntil == now) {
                    updateBar(run);
                }
            }
            case "void_stalker" -> {
                if (t % 200 == 0 && boss instanceof Enderman stalker) {
                    Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                    stalker.teleport(behind.x, owner.getY(), behind.z);
                }
                if (t % 160 == 80) {
                    BossMoves.voidBolt(run, owner);
                }
                if (tier >= 3 && t % 400 == 300) {
                    BossMoves.voidBeacon(run, owner);
                }
                if (tier >= 2 && newPhase) {
                    run.nullHits = tier >= 4 ? 12 : tier >= 3 ? 8 : 5;
                    sound(run, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
                    tell(run, Msg.text("The Void Stalker is shrouded in a null shield! (" + run.nullHits + " hits)", ChatFormatting.LIGHT_PURPLE));
                    updateBar(run);
                }
                if (run.nullHits > 0 && t % 4 == 0) {
                    ring(run.level, ParticleTypes.REVERSE_PORTAL, boss.position().add(0, 1.5, 0), 1.0, 10);
                }
                if (tier >= 4 && t % 200 == 100) {
                    openRifts(run);
                }
            }
            default -> {
            }
        }
        SlayerAbilities.tick(run, owner, t, health, newPhase, now);
    }

    /** A hit on a player: life drain, venom or blindness. */
    private static void bossHit(Run run, ServerPlayer player, boolean melee) {
        run.lastHit = now;
        int tier = run.tierNumber;
        switch (run.line.kind()) {
            case "grave_tyrant" -> {
                if (tier >= 2) {
                    run.boss.heal(run.boss.getMaxHealth() * 0.02f);
                    particles(run.level, ParticleTypes.HEART, run.boss.position().add(0, 2.2, 0), 3, 0.3, 0);
                }
            }
            case "broodmother" -> {
                if (tier >= 4 && melee) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), run.boss);
                }
            }
            case "void_stalker" -> {
                if (tier >= 2 && melee) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), run.boss);
                }
            }
            default -> {
            }
        }
    }

    /** The bone and null shields. */
    private static double damageTaken(LivingEntity entity) {
        if (!entity.entityTags().contains(BOSS_TAG)) {
            return 1;
        }
        Run run = byBoss(entity);
        if (run == null) {
            return 1;
        }
        if (run.nullHits > 0) {
            return 0.1;
        }
        double banner = BossMoves.hasBanner(run) ? 0.5 : 1;
        return (run.shieldUntil > now ? 0.2 : 1) * banner * SlayerAbilities.defence(run, now);
    }

    private static void raise(Run run, ServerPlayer owner, EntityType<? extends Mob> type, int count, String what) {
        if (raiseAt(run, owner, type, count, run.boss.position()) > 0) {
            sound(run, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0f, 0.8f);
            tell(run, Component.literal("The " + run.line.name() + " " + what).withColor(run.line.color() & 0xFFFFFF));
        }
    }

    /** Minions around a spot (farmed, levelled like the boss's half); returns how many rose. */
    static int raiseAt(Run run, ServerPlayer owner, EntityType<? extends Mob> type, int count, Vec3 at) {
        run.minions.removeIf(minion -> !minion.isAlive());
        RandomSource random = run.level.getRandom();
        int raised = 0;
        for (int i = 0; i < count && run.minions.size() < MAX_MINIONS; i++) {
            Mob minion = type.create(run.level, EntitySpawnReason.MOB_SUMMONED);
            if (minion == null) {
                return raised;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            minion.snapTo(at.x + Math.cos(angle) * 2, at.y, at.z + Math.sin(angle) * 2, random.nextFloat() * 360, 0);
            if (!run.level.noCollision(minion)) {
                minion.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
            }
            minion.finalizeSpawn(run.level, run.level.getCurrentDifficultyAt(minion.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            minion.addTag(MINION_TAG);
            minion.addTag(MobTags.FARMED);
            MobLevels.set(minion, new MobLevels.MobInfo(Math.max(1, run.tier.level() / 2), false));
            minion.setTarget(owner);
            run.level.addFreshEntity(minion);
            particles(run.level, ParticleTypes.SOUL_FIRE_FLAME, minion.position().add(0, 0.6, 0), 10, 0.3, 0.02);
            run.minions.add(minion);
            raised++;
        }
        return raised;
    }

    /** The standing spot at or just under/over this point (for things a boss puts down). */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos feet = BlockPos.containing(at.x, at.y + dy, at.z);
            BlockPos below = feet.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(feet).isAir()) {
                return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            }
        }
        return at;
    }

    static long now() {
        return now;
    }

    /** Whether this fight is still on. */
    static boolean active(Run run) {
        return RUNS.get(run.owner) == run;
    }

    /** Every second: everyone within 5 blocks loses 4% of their health. */
    private static void plagueAura(Run run) {
        Mob boss = run.boss;
        ring(run.level, ParticleTypes.SNEEZE, boss.position().add(0, 0.3, 0), 5, 24);
        for (ServerPlayer player : players(run, 5)) {
            player.hurtServer(run.level, boss.damageSources().indirectMagic(boss, boss), player.getMaxHealth() * 0.04f);
        }
    }

    /** A string of web at the owner: slowness III for three seconds. */
    private static void webShot(Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        if (boss.distanceTo(owner) > 16) {
            return;
        }
        Vec3 from = boss.getEyePosition();
        Vec3 to = owner.position().add(0, 1, 0);
        for (int i = 0; i <= 12; i++) {
            Vec3 at = from.lerp(to, i / 12.0);
            run.level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
        }
        owner.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), boss);
        sound(run, SoundEvents.SPIDER_AMBIENT, 1.2f, 0.6f);
    }

    /** A rift under everyone near: it bursts for a quarter of their health after a second and a half. */
    private static void openRifts(Run run) {
        for (ServerPlayer player : players(run, 16)) {
            RIFTS.add(new Rift(run.level, player.position(), now + 30, run.boss, 0.25));
        }
        sound(run, SoundEvents.ENDERMAN_STARE, 1.0f, 0.6f);
    }

    private static void rifts() {
        for (Iterator<Rift> it = RIFTS.iterator(); it.hasNext(); ) {
            Rift rift = it.next();
            if (now < rift.bursts()) {
                if (now % 4 == 0) {
                    ring(rift.level(), ParticleTypes.PORTAL, rift.at().add(0, 0.1, 0), 1.6, 16);
                    ring(rift.level(), ParticleTypes.REVERSE_PORTAL, rift.at().add(0, 0.1, 0), 0.8, 8);
                }
                continue;
            }
            it.remove();
            rift.level().sendParticles(ParticleTypes.EXPLOSION, rift.at().x, rift.at().y + 0.5, rift.at().z, 3, 0.6, 0.3, 0.6, 0);
            rift.level().playSound(null, rift.at().x, rift.at().y, rift.at().z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.5f, 1.4f);
            int hit = 0;
            for (ServerPlayer player : rift.level().getEntitiesOfClass(ServerPlayer.class, new AABB(BlockPos.containing(rift.at())).inflate(2),
                    p -> !p.isSpectator() && !p.isCreative())) {
                player.hurtServer(rift.level(), rift.boss().damageSources().indirectMagic(rift.boss(), rift.boss()),
                        (float) (player.getMaxHealth() * rift.damage()));
                hit++;
            }
            Run owner = byBoss(rift.boss());
            if (owner != null) {
                SlayerAbilities.siphon(owner, hit);
            }
        }
    }

    static List<ServerPlayer> players(Run run, double range) {
        return run.level.getEntitiesOfClass(ServerPlayer.class, run.boss.getBoundingBox().inflate(range),
                p -> !p.isSpectator() && !p.isCreative() && p.distanceTo(run.boss) <= range);
    }

    static void updateBar(Run run) {
        if (run.boss == null) {
            return;
        }
        MutableComponent name = title(run.line, run.tierNumber);
        String tierTitle = SlayerAbilities.title(run.line.kind(), run.tierNumber);
        if (!tierTitle.isEmpty()) {
            name.append(Msg.text(" · " + tierTitle, ChatFormatting.GRAY));
        }
        SlayerAbilities.barExtras(run, name, now);
        if (run.nullHits > 0) {
            name.append(Msg.text("  ⬡ Null Shield: " + run.nullHits, ChatFormatting.LIGHT_PURPLE));
        }
        if (run.shieldUntil > now) {
            name.append(Msg.text("  ⛨ Bone Shield", ChatFormatting.WHITE));
        }
        if (BossMoves.hasBanner(run)) {
            name.append(Msg.text("  ⚑ War Banner", ChatFormatting.WHITE));
        }
        if (run.enraged) {
            name.append(Msg.text("  ENRAGED", ChatFormatting.DARK_RED));
        }
        Long deadline = DEADLINES.get(run.owner);
        if (deadline != null) {
            long seconds = Math.max(0, (deadline - now) / 20);
            name.append(Msg.text("  " + seconds / 60 + ":" + String.format("%02d", seconds % 60), ChatFormatting.YELLOW));
        }
        run.bar.setName(name);
        run.bar.setProgress(Math.max(0, run.boss.getHealth() / run.boss.getMaxHealth()));
        for (ServerPlayer player : run.level.getServer().getPlayerList().getPlayers()) {
            boolean near = player.level() == run.level && (player.getUUID().equals(run.owner) || player.distanceTo(run.boss) <= BAR_RANGE);
            if (near && !run.bar.getPlayers().contains(player)) {
                run.bar.addPlayer(player);
            } else if (!near && run.bar.getPlayers().contains(player)) {
                run.bar.removePlayer(player);
            }
        }
    }

    /** Bosses and minions left over from a restart or a fight that ended. */
    private static void sweep(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> isSlayerMob(mob) && !inRun(mob))) {
                mob.discard();
            }
            // Egg sacs, banners and beacons left over from a restart.
            for (Entity entity : level.getEntities(EntityTypeTest.forClass(Entity.class), e -> e.entityTags().contains(BossMoves.TOTEM_TAG)
                    && BossMoves.TOTEMS.stream().noneMatch(totem -> totem.display == e || totem.hitbox == e))) {
                entity.discard();
            }
        }
    }

    private static boolean inRun(Mob mob) {
        for (Run run : RUNS.values()) {
            if (run.boss == mob || run.minions.contains(mob)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- effects

    static void tell(Run run, Component message) {
        Component line = Notice.build(Notice.Kind.SLAYER, message);
        run.bar.getPlayers().forEach(player -> player.sendSystemMessage(line));
    }

    static void sound(Run run, SoundEvent sound, float volume, float pitch) {
        run.level.playSound(null, run.boss.getX(), run.boss.getY(), run.boss.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    static void particles(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }
}
