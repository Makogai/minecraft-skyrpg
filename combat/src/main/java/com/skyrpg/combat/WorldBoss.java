package com.skyrpg.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World bosses: now and then (every two to three hours with enough players on, or {@code /worldboss start}) a boss
 * rises in the survival world near a random player, announced a minute ahead. It has a boss bar, slams the ground, calls
 * minions at two-thirds and one-third health and gets angrier at the end. Everyone who helped is paid by how much damage
 * they did; it leaves after fifteen minutes if nobody beats it. Bosses are data: {@code data/<ns>/skyrpg/world_bosses/*.json}.
 */
public final class WorldBoss {
    public static final String DIRECTORY = "skyrpg/world_bosses";
    static final String TAG = "skyrpg_world_boss";
    static final String MINION_TAG = "skyrpg_boss_minion";
    private static final int WARNING = 1200;
    private static final int TIME_LIMIT = 18000;
    private static final int SLAM_EVERY = 160;
    private static final double BAR_RANGE = 64;

    /** A boss, from data. Health is in SkyRPG units (like the health bar); coins and seals are [min, max]. */
    record Boss(String id, Identifier entity, String name, int level, double health, double damage, double scale, double speed,
                Identifier minion, int minions, int minionLevel, int[] coins, int[] seals, double xp, Identifier rare, double rareChance,
                Identifier bonus, double bonusChance, Identifier head, Identifier mainhand, Identifier minionHead, boolean auto) {
    }

    /** The fight in progress (one at a time). */
    private static final class Fight {
        final Boss boss;
        final ServerBossEvent bar;
        final Map<UUID, Double> damage = new HashMap<>();
        final List<Mob> minions = new ArrayList<>();
        Mob mob;
        BlockPos at;
        ServerLevel level;
        long spawnAt;
        long started;
        int phase;

        Fight(Boss boss) {
            this.boss = boss;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(boss.name()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static List<Boss> bosses = List.of();
    private static Fight fight;
    private static boolean auto = true;
    private static long nextAuto = -1;

    private WorldBoss() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Boss> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                loaded.add(new Boss(id.getPath(), Identifier.parse(json.get("entity").getAsString()), json.get("name").getAsString(),
                        num(json, "level", 40), num(json, "health", 5000), num(json, "damage", 3), num(json, "scale", 2), num(json, "speed", 1),
                        json.has("minion") ? Identifier.parse(json.get("minion").getAsString()) : null, (int) num(json, "minions", 4),
                        (int) num(json, "minion_level", 25), range(json, "coins", 800, 2000), range(json, "valor_seals", 2, 5),
                        num(json, "combat_xp", 600), json.has("rare") ? Identifier.parse(json.get("rare").getAsString()) : null,
                        num(json, "rare_chance", 0.05), json.has("bonus") ? Identifier.parse(json.get("bonus").getAsString()) : null,
                        num(json, "bonus_chance", 0.05), item(json, "head"), item(json, "mainhand"), item(json, "minion_head"),
                        !json.has("auto") || json.get("auto").getAsBoolean()));
            } catch (RuntimeException e) {
                SkyRpgCombat.LOGGER.warn("Skipping world boss {}: {}", id, e.getMessage());
            }
        });
        bosses = List.copyOf(loaded);
    }

    private static Identifier item(JsonObject json, String key) {
        return json.has(key) ? Identifier.parse(json.get(key).getAsString()) : null;
    }

    /** Puts an item in a slot that never drops (a boss's pumpkin head, its axe). */
    private static void wear(Mob mob, net.minecraft.world.entity.EquipmentSlot slot, Identifier item) {
        if (item != null) {
            BuiltInRegistries.ITEM.getOptional(item).ifPresent(found -> {
                mob.setItemSlot(slot, new ItemStack(found));
                mob.setDropChance(slot, 0);
            });
        }
    }

    private static double num(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static int num(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static int[] range(JsonObject json, String key, int min, int max) {
        if (!json.has(key)) {
            return new int[] {min, max};
        }
        var array = json.getAsJsonArray(key);
        return new int[] {array.get(0).getAsInt(), array.get(array.size() - 1).getAsInt()};
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WorldBoss::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (fight != null && entity == fight.mob && damageTaken > 0) {
                ServerPlayer player = source.getEntity() instanceof ServerPlayer direct ? direct
                        : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
                if (player != null) {
                    fight.damage.merge(player.getUUID(), (double) damageTaken, Double::sum);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (fight != null && entity == fight.mob) {
                victory(fight);
            }
        });
    }

    /** {@code /worldboss start}: warns now, rises in a minute. False if one is already on or none is defined. */
    static boolean start(MinecraftServer server, String id) {
        if (fight != null || bosses.isEmpty()) {
            return false;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension() == Level.OVERWORLD && !p.isSpectator()).toList();
        if (players.isEmpty()) {
            return false;
        }
        RandomSource random = server.overworld().getRandom();
        // Bosses with "auto": false (seasonal ones) only come when asked for by id.
        List<Boss> pool = bosses.stream().filter(Boss::auto).toList();
        if (id == null && pool.isEmpty()) {
            return false;
        }
        Boss boss = id == null ? pool.get(random.nextInt(pool.size()))
                : bosses.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
        if (boss == null) {
            return false;
        }
        ServerPlayer near = players.get(random.nextInt(players.size()));
        ServerLevel level = near.level();
        BlockPos at = dryGround(level, near, random);
        int x = at.getX();
        int z = at.getZ();
        Fight next = new Fight(boss);
        next.level = level;
        next.at = at;
        next.spawnAt = server.getTickCount() + WARNING;
        fight = next;
        broadcast(server, Notice.of(Notice.Kind.WARN).tag("WORLD BOSS").color(0xFF5555).build(Msg.text(boss.name(), ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Msg.text(" stirs near " + near.getName().getString() + " (" + x + ", " + z + ")! It rises in one minute.", ChatFormatting.GOLD))));
        server.getPlayerList().getPlayers().forEach(p -> Msg.playSound(p, SoundEvents.WITHER_AMBIENT, 0.6f, 0.6f));
        return true;
    }

    /** Somewhere 14 to 26 blocks from the player on dry land (not in a lake or river); the player's own spot if none. */
    private static BlockPos dryGround(ServerLevel level, ServerPlayer near, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 14 + random.nextDouble() * 12;
            int x = Mth.floor(near.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(near.getZ() + Math.sin(angle) * distance);
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(top.below()).isEmpty() && level.getFluidState(top).isEmpty() && level.getBlockState(top.below()).isSolid()) {
                return top;
            }
        }
        return near.blockPosition();
    }

    /** {@code /worldboss stop}: ends the fight with no rewards. */
    static boolean stop() {
        if (fight == null) {
            return false;
        }
        end(fight);
        return true;
    }

    static void setAuto(boolean on) {
        auto = on;
    }

    static boolean auto() {
        return auto;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 100 == 0) {
            sweep(server);
        }
        if (fight == null) {
            autoStart(server, now);
            return;
        }
        Fight current = fight;
        if (current.mob == null) {
            if (now >= current.spawnAt) {
                rise(current, now);
            } else if ((current.spawnAt - now) % 20 == 0) {
                current.level.sendParticles(ParticleTypes.SOUL, current.at.getX() + 0.5, current.at.getY() + 0.3, current.at.getZ() + 0.5,
                        12, 1.2, 0.2, 1.2, 0.02);
            }
            return;
        }
        if (current.mob.isRemoved() && !current.mob.isDeadOrDying()) {
            end(current);
            return;
        }
        if (now - current.started > TIME_LIMIT) {
            broadcast(server, Notice.of(Notice.Kind.INFO).tag("WORLD BOSS").build(Msg.text(current.boss.name() + " sinks back into the earth. Nobody beat it this time.", ChatFormatting.GRAY)));
            end(current);
            return;
        }
        if (now % 20 == 0) {
            updateBar(current);
        }
        float health = current.mob.getHealth() / current.mob.getMaxHealth();
        if (current.phase == 0 && health < 0.66f || current.phase == 1 && health < 0.33f) {
            current.phase++;
            summonMinions(current);
            if (current.phase == 2) {
                AttributeInstance speed = current.mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(speed.getBaseValue() * 1.3);
                }
                broadcastNear(current, Notice.of(Notice.Kind.COMBAT).tag("ENRAGED").build(Msg.text(current.boss.name() + " is enraged!", ChatFormatting.RED, ChatFormatting.BOLD)));
            }
        }
        if ((now - current.started) % SLAM_EVERY == 0 && now > current.started) {
            slam(current);
        }
    }

    private static void autoStart(MinecraftServer server, long now) {
        if (!auto || bosses.isEmpty()) {
            return;
        }
        RandomSource random = server.overworld().getRandom();
        if (nextAuto < 0) {
            nextAuto = now + (120 + random.nextInt(61)) * 1200L;
        }
        if (now >= nextAuto) {
            nextAuto = now + (120 + random.nextInt(61)) * 1200L;
            if (server.getPlayerList().getPlayerCount() >= 2) {
                start(server, null);
            }
        }
    }

    private static void rise(Fight current, long now) {
        Boss boss = current.boss;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(boss.entity()).orElse(null);
        Entity entity = type == null ? null : type.create(current.level, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob mob)) {
            fight = null;
            return;
        }
        mob.snapTo(current.at.getX() + 0.5, current.at.getY(), current.at.getZ() + 0.5, current.level.getRandom().nextFloat() * 360, 0);
        mob.finalizeSpawn(current.level, current.level.getCurrentDifficultyAt(current.at), EntitySpawnReason.EVENT, null);
        mob.addTag(TAG);
        wear(mob, net.minecraft.world.entity.EquipmentSlot.HEAD, boss.head());
        wear(mob, net.minecraft.world.entity.EquipmentSlot.MAINHAND, boss.mainhand());
        mob.setCustomName(Component.literal(boss.name()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        mob.setPersistenceRequired();
        // Max health can't go past 1024 (vanilla's cap), which is 5,120 in SkyRPG units.
        set(mob, Attributes.MAX_HEALTH, Math.min(1024, boss.health() / StatTypes.HEALTH_SCALE));
        multiply(mob, Attributes.ATTACK_DAMAGE, boss.damage());
        set(mob, Attributes.SCALE, boss.scale());
        multiply(mob, Attributes.MOVEMENT_SPEED, boss.speed());
        set(mob, Attributes.KNOCKBACK_RESISTANCE, 1);
        set(mob, Attributes.FOLLOW_RANGE, 48);
        mob.setHealth(mob.getMaxHealth());
        // The nameplate shows its level (without the level's health and damage on top).
        mob.setAttached(MobLevels.INFO, new MobLevels.MobInfo(boss.level(), false));
        current.level.addFreshEntity(mob);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(current.level, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(mob.getX(), mob.getY(), mob.getZ(), 0, 0);
            current.level.addFreshEntity(bolt);
        }
        current.mob = mob;
        current.started = now;
        broadcast(current.level.getServer(), Notice.of(Notice.Kind.COMBAT).tag("WORLD BOSS").build(Msg.text(boss.name(), ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Msg.text(" has risen at " + current.at.getX() + ", " + current.at.getZ() + "!", ChatFormatting.GOLD))));
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void multiply(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double factor) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    /** A ground slam: hurts and throws back everyone within 6 blocks. */
    private static void slam(Fight current) {
        Mob mob = current.mob;
        ServerLevel level = current.level;
        level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 0.2, mob.getZ(), 6, 2.5, 0.2, 2.5, 0);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mob.blockPosition()).inflate(6),
                p -> !p.isSpectator() && !p.isCreative())) {
            player.hurtServer(level, mob.damageSources().mobAttack(mob), (float) (4 * current.boss.damage()));
            Vec3 away = player.position().subtract(mob.position()).multiply(1, 0, 1).normalize().scale(1.4);
            player.push(away.x, 0.6, away.z);
            player.syncVelocity = true; // what vanilla's knockback sets: sends the push to the player's client
        }
    }

    private static void summonMinions(Fight current) {
        if (current.boss.minion() == null) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(current.boss.minion()).orElse(null);
        for (int i = 0; i < current.boss.minions() && type != null; i++) {
            Entity entity = type.create(current.level, EntitySpawnReason.EVENT);
            if (!(entity instanceof Mob minion)) {
                return;
            }
            double angle = Math.PI * 2 * i / current.boss.minions();
            minion.snapTo(current.mob.getX() + Math.cos(angle) * 3, current.mob.getY(), current.mob.getZ() + Math.sin(angle) * 3, 0, 0);
            minion.finalizeSpawn(current.level, current.level.getCurrentDifficultyAt(minion.blockPosition()), EntitySpawnReason.EVENT, null);
            minion.addTag(MINION_TAG);
            wear(minion, net.minecraft.world.entity.EquipmentSlot.HEAD, current.boss.minionHead());
            MobLevels.set(minion, new MobLevels.MobInfo(current.boss.minionLevel(), false));
            current.level.addFreshEntity(minion);
            current.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, minion.getX(), minion.getY() + 1, minion.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            current.minions.add(minion);
        }
        broadcastNear(current, Notice.of(Notice.Kind.COMBAT).tag("WORLD BOSS").build(Msg.text(current.boss.name() + " calls for help!", ChatFormatting.GOLD)));
    }

    private static void updateBar(Fight current) {
        current.bar.setProgress(Math.max(0, current.mob.getHealth() / current.mob.getMaxHealth()));
        for (ServerPlayer player : current.level.getServer().getPlayerList().getPlayers()) {
            boolean near = player.level() == current.level && player.distanceTo(current.mob) <= BAR_RANGE;
            if (near && !current.bar.getPlayers().contains(player)) {
                current.bar.addPlayer(player);
            } else if (!near && current.bar.getPlayers().contains(player)) {
                current.bar.removePlayer(player);
            }
        }
    }

    /** Pays everyone who hurt it, by how much: the top damager gets half again, everyone has a shot at the rare drop. */
    private static void victory(Fight current) {
        MinecraftServer server = current.level.getServer();
        Boss boss = current.boss;
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(current.damage.entrySet());
        ranking.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        broadcast(server, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("WORLD BOSS").build(Msg.text(boss.name(), ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Msg.text(" has fallen!", ChatFormatting.GOLD))));
        RandomSource random = current.level.getRandom();
        Item seal = CombatItems.VALOR_SEAL;
        Item rare = boss.rare() == null ? null : BuiltInRegistries.ITEM.getOptional(boss.rare()).orElse(null);
        for (int i = 0; i < ranking.size(); i++) {
            ServerPlayer player = server.getPlayerList().getPlayer(ranking.get(i).getKey());
            if (player == null) {
                continue;
            }
            double share = i == 0 ? 1.5 : 1.0;
            long coins = Math.round((boss.coins()[0] + random.nextInt(boss.coins()[1] - boss.coins()[0] + 1)) * share);
            int seals = (int) Math.round((boss.seals()[0] + random.nextInt(boss.seals()[1] - boss.seals()[0] + 1)) * share);
            Wallet.give(player, coins);
            player.getInventory().add(new ItemStack(seal, seals));
            SkillLevels.giveXp(player, SkillLevels.COMBAT, boss.xp() * share);
            com.skyrpg.core.event.BossKills.KILLED.invoker().onKilled(player,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("skyrpg_combat", boss.id()), boss.level(), com.skyrpg.core.event.BossKills.Kind.WORLD_BOSS);
            MutableComponent line = Msg.text("#" + (i + 1) + " ", ChatFormatting.YELLOW)
                    .append(Msg.text(Msg.number(Math.round(ranking.get(i).getValue() * StatTypes.HEALTH_SCALE)) + " damage", ChatFormatting.GRAY))
                    .append(Msg.text("  +" + Msg.number(coins) + " coins, " + seals + " Valor Seals", ChatFormatting.GOLD));
            Component rareLine = null;
            if (rare != null && random.nextDouble() < boss.rareChance() * share) {
                player.getInventory().add(new ItemStack(rare));
                rareLine = Notice.rareDrop(new ItemStack(rare).getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE), Notice.rgb(ChatFormatting.LIGHT_PURPLE));
            }
            Component bonusLine = null;
            Item bonus = boss.bonus() == null ? null : BuiltInRegistries.ITEM.getOptional(boss.bonus()).orElse(null);
            if (bonus != null && random.nextDouble() < boss.bonusChance()) {
                player.getInventory().add(new ItemStack(bonus));
                bonusLine = Notice.rareDrop(new ItemStack(bonus).getHoverName().copy().withStyle(ChatFormatting.GOLD), Notice.rgb(ChatFormatting.GOLD));
            }
            player.sendSystemMessage(Notice.of(Notice.Kind.COMBAT).tag("WORLD BOSS").build(line));
            if (rareLine != null) {
                player.sendSystemMessage(rareLine);
            }
            if (bonusLine != null) {
                player.sendSystemMessage(bonusLine);
            }
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
        end(current);
    }

    private static void end(Fight current) {
        current.bar.removeAllPlayers();
        if (current.mob != null && current.mob.isAlive()) {
            current.mob.discard();
        }
        current.minions.forEach(minion -> {
            if (minion.isAlive()) {
                minion.discard();
            }
        });
        fight = null;
    }

    /** Leftovers from before a restart, or from a fight that ended. */
    private static void sweep(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class),
                    mob -> (mob.entityTags().contains(TAG) || mob.entityTags().contains(MINION_TAG))
                            && (fight == null || mob != fight.mob && !fight.minions.contains(mob)))) {
                mob.discard();
            }
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(message));
    }

    private static void broadcastNear(Fight current, Component message) {
        current.bar.getPlayers().forEach(player -> player.sendSystemMessage(message));
    }
}
