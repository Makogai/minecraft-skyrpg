package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.MobLevelling;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Dungeon bosses: they wake when a player walks into their room (a {@code boss} marker), fight with the moves their
 * data gives them (see docs/design/dungeons.md), and on death everyone who fought gets their own roll of the loot
 * table, plus a first clear reward the first time. A boss nobody fights for 30 seconds goes back to sleep, and comes
 * back at full health next time.
 */
public final class DungeonBosses {
    static final String TAG = "skyrpg_dungeon_boss";
    static final String BOSS_PREFIX = "skyrpg_boss=";
    static final String MARKER_PREFIX = "skyrpg_marker=";
    /** Where a boss without a room marker (a region boss) is at home, so a restart doesn't move its leash. */
    static final String HOME_PREFIX = "skyrpg_home=";
    static final String MINION_TAG = "skyrpg_dungeon_minion";
    private static final double BAR_RANGE = 48;
    private static final double LEASH = 28;
    private static final int GIVE_UP_TICKS = 600;
    private static final Identifier ENRAGE_SPEED = SkyRpgWorld.id("enrage_speed");
    private static final Identifier ENRAGE_DAMAGE = SkyRpgWorld.id("enrage_damage");

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<TempBlock> TEMP_BLOCKS = new ArrayList<>();
    private static final List<WebShot> WEB_SHOTS = new ArrayList<>();

    /** A fight in progress. */
    static final class Fight {
        final Dungeons.Boss def;
        final Mob mob;
        final ServerLevel level;
        final Vec3 home;
        final UUID marker;
        final ServerBossEvent bar;
        final Set<UUID> hitters = new HashSet<>();
        final List<UUID> minions = new ArrayList<>();
        final long[] next;
        final boolean[] used;
        boolean enraged;
        /** The boss's health in SkyRPG units, after scaling for the party. */
        double totalHealth;
        int phase;
        long wardUntil;
        double wardReduction;
        long lonelySince = -1;

        Fight(Dungeons.Boss def, Mob mob, ServerLevel level, Vec3 home, UUID marker) {
            this.def = def;
            this.totalHealth = def.health();
            this.mob = mob;
            this.level = level;
            this.home = home;
            this.marker = marker;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(def.name()), barColor(def.barColor()), BossEvent.BossBarOverlay.NOTCHED_10);
            this.next = new long[def.moves().size()];
            this.used = new boolean[def.moves().size()];
            long now = level.getGameTime();
            for (int i = 0; i < next.length; i++) {
                next[i] = now + def.moves().get(i).delay();
            }
        }

        boolean warded() {
            return level.getGameTime() < wardUntil;
        }
    }

    private record Task(long at, Runnable run) {
    }

    /** A block a move put down for a while, and what was there before it (air for webs, the floor for molten spots). */
    private record TempBlock(ServerLevel level, BlockPos pos, Block block, BlockState original, long until) {
    }

    private static final class WebShot {
        final Snowball shot;
        final ServerLevel level;
        final long until;
        Vec3 last;

        WebShot(Snowball shot, ServerLevel level, long until) {
            this.shot = shot;
            this.level = level;
            this.until = until;
            this.last = shot.position();
        }
    }

    private DungeonBosses() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DungeonBosses::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && mob.entityTags().contains(TAG) && !FIGHTS.containsKey(mob.getUUID())) {
                resume(level, mob);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Fight fight = FIGHTS.remove(entity.getUUID());
            if (fight != null) {
                fight.bar.removeAllPlayers();
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            Fight fight = FIGHTS.get(entity.getUUID());
            if (fight != null && source.getEntity() instanceof ServerPlayer player) {
                fight.hitters.add(player.getUUID());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Fight fight = FIGHTS.remove(entity.getUUID());
            if (fight != null) {
                defeated(fight, source.getEntity());
            }
        });
        // A warded boss shrugs off most damage.
        IncomingDamage.register(entity -> {
            Fight fight = FIGHTS.get(entity.getUUID());
            return fight != null && fight.warded() ? 1 - fight.wardReduction : 1;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Temporary webs and molten floor mustn't outlive the fight.
            for (TempBlock temp : TEMP_BLOCKS) {
                if (temp.level().getBlockState(temp.pos()).is(temp.block())) {
                    temp.level().setBlock(temp.pos(), temp.original(), Block.UPDATE_ALL);
                }
            }
            TEMP_BLOCKS.clear();
            TASKS.clear();
            WEB_SHOTS.clear();
            FIGHTS.values().forEach(fight -> fight.bar.removeAllPlayers());
            FIGHTS.clear();
        });
    }

    // ---------------------------------------------------------------- the boss marker

    /** From {@link DungeonMarkers}: wakes the boss when a player comes close, and brings it back after a reset. */
    static void tickMarker(ServerLevel level, Marker marker, DungeonMarkers.Info info) {
        Dungeons.Boss def = Dungeons.boss(Identifier.tryParse(Dungeons.str(info.def(), "boss", "")));
        if (def == null) {
            return;
        }
        CompoundTag state = DungeonMarkers.state(marker);
        UUID bossId = DungeonMarkers.parseUuid(state.getStringOr("boss", ""));
        if (bossId != null) {
            Entity boss = level.getEntity(bossId);
            if (boss != null && boss.isAlive()) {
                return;
            }
            // Gone some other way (a command): after a few seconds with players around, the room wakes again.
            if (DungeonMarkers.anyPlayerNear(level, marker.position(), 24, 16)) {
                int missing = state.getIntOr("missing", 0) + 1;
                if (missing >= 10) {
                    state = new CompoundTag();
                } else {
                    state.putInt("missing", missing);
                }
                DungeonMarkers.setState(marker, state);
            }
            return;
        }
        if (state.getBooleanOr("killed", false)) {
            if (level.getGameTime() - state.getLongOr("at", 0) >= def.resetTicks()
                    && !DungeonMarkers.anyPlayerNear(level, marker.position(), 32, 32)) {
                DungeonMarkers.setState(marker, new CompoundTag());
            }
            return;
        }
        if (DungeonMarkers.anyPlayerNear(level, marker.position(), Dungeons.num(info.def(), "radius", 12), 8)) {
            Mob boss = spawn(level, def, marker.position(), marker.getUUID());
            if (boss != null) {
                state.putString("boss", boss.getUUID().toString());
                DungeonMarkers.setState(marker, state);
            }
        }
    }

    /** /dungeon reset: the boss (if up) leaves and the room is ready to wake again. */
    static void resetMarker(ServerLevel level, Marker marker) {
        UUID bossId = DungeonMarkers.parseUuid(DungeonMarkers.state(marker).getStringOr("boss", ""));
        Entity boss = bossId == null ? null : level.getEntity(bossId);
        if (boss != null) {
            Fight fight = FIGHTS.remove(boss.getUUID());
            if (fight != null) {
                sleep(fight);
            } else {
                boss.discard();
            }
        }
        DungeonMarkers.setState(marker, new CompoundTag());
    }

    // ---------------------------------------------------------------- spawning

    /** Spawns a boss at {@code at}; {@code marker} is its room's marker (null for /dungeon spawnboss). */
    static Mob spawn(ServerLevel level, Dungeons.Boss def, Vec3 at, UUID marker) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(def.entity()).orElse(null);
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
        if (!(entity instanceof Mob mob)) {
            SkyRpgWorld.LOGGER.warn("Dungeon boss {} has no usable entity {}", def.id(), def.entity());
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIGGERED, null);
        // More players in the room, more health. Max health stops at 1024 vanilla points; tougher bosses take less
        // damage instead (like slayer bosses).
        int party = Math.max(1, DungeonMarkers.playersNear(level, at, 32, 16).size());
        double totalHealth = def.health() * (1 + def.healthPerPlayer() * (party - 1));
        double health = totalHealth / StatTypes.HEALTH_SCALE;
        float toughness = (float) Math.max(1, health / 1024);
        set(mob, Attributes.MAX_HEALTH, Math.min(1024, health));
        set(mob, Attributes.ATTACK_DAMAGE, def.damage());
        set(mob, Attributes.MOVEMENT_SPEED, def.speed());
        set(mob, Attributes.SCALE, def.scale());
        set(mob, Attributes.KNOCKBACK_RESISTANCE, def.knockbackResistance());
        set(mob, Attributes.FOLLOW_RANGE, 40);
        set(mob, Attributes.STEP_HEIGHT, 1.1);
        mob.setHealth(mob.getMaxHealth());
        MobLevelling.boss(mob, def.level(), toughness);
        def.equipment().forEach((slot, itemId) -> {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(itemId)).orElse(null);
            try {
                EquipmentSlot equipmentSlot = EquipmentSlot.byName(slot);
                if (item != null) {
                    mob.setItemSlot(equipmentSlot, new ItemStack(item));
                    mob.setDropChance(equipmentSlot, 0);
                }
            } catch (IllegalArgumentException ignored) {
                // An unknown slot name: skip it.
            }
        });
        mob.setCustomName(Component.literal(def.name()).withColor(barRgb(def.barColor())).withStyle(ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.addTag(BOSS_PREFIX + def.id());
        def.tags().forEach(mob::addTag);
        mob.addTag(HOME_PREFIX + at.x + "," + at.y + "," + at.z);
        if (marker != null) {
            mob.addTag(MARKER_PREFIX + marker);
        }
        level.addFreshEntity(mob);

        Fight fight = new Fight(def, mob, level, at, marker);
        fight.totalHealth = totalHealth;
        FIGHTS.put(mob.getUUID(), fight);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(at.x, at.y, at.z, 0, 0);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 60, 1.0, 1.2, 1.0, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
        for (ServerPlayer player : DungeonMarkers.playersNear(level, at, BAR_RANGE, 24)) {
            if (!def.intro().isEmpty()) {
                player.sendSystemMessage(Component.literal(def.name() + ": ").withColor(barRgb(def.barColor())).withStyle(ChatFormatting.BOLD)
                        .append(Component.literal(def.intro()).withStyle(ChatFormatting.WHITE)));
            }
            if (party > 1) {
                Notice.send(player, Notice.Kind.DUNGEON, Component.literal("The boss is empowered for a party of " + party + " (" + Msg.compact(totalHealth) + " health).")
                        .withStyle(ChatFormatting.GRAY));
            }
            mob.setTarget(player);
        }
        return mob;
    }

    /** After a restart or chunk reload: picks the fight back up. */
    private static void resume(ServerLevel level, Mob mob) {
        Dungeons.Boss def = null;
        UUID marker = null;
        for (String tag : mob.entityTags()) {
            if (tag.startsWith(BOSS_PREFIX)) {
                def = Dungeons.boss(Identifier.tryParse(tag.substring(BOSS_PREFIX.length())));
            } else if (tag.startsWith(MARKER_PREFIX)) {
                marker = DungeonMarkers.parseUuid(tag.substring(MARKER_PREFIX.length()));
            }
        }
        if (def == null) {
            return;
        }
        Marker markerEntity = DungeonMarkers.loaded(marker);
        Vec3 home = markerEntity != null ? markerEntity.position() : mob.position();
        for (String tag : mob.entityTags()) {
            if (tag.startsWith(HOME_PREFIX)) {
                String[] parts = tag.substring(HOME_PREFIX.length()).split(",");
                try {
                    home = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                } catch (RuntimeException ignored) {
                    // keep the position
                }
            }
        }
        Fight fight = new Fight(def, mob, level, home, marker);
        fight.totalHealth = Math.max(def.health(), mob.getMaxHealth() * StatTypes.HEALTH_SCALE * Math.max(1, def.health() / StatTypes.HEALTH_SCALE / 1024));
        FIGHTS.put(mob.getUUID(), fight);
    }

    // ---------------------------------------------------------------- the fight

    private static void tick(MinecraftServer server) {
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            long now = server.overworld().getGameTime();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.at() <= now) {
                    due.add(task);
                    it.remove();
                }
            }
            for (Task task : due) {
                try {
                    task.run().run();
                } catch (RuntimeException e) {
                    SkyRpgWorld.LOGGER.warn("Boss move failed: {}", e.toString());
                }
            }
        }
        tickWebs(server);
        for (Fight fight : new ArrayList<>(FIGHTS.values())) {
            if (fight.mob.isRemoved() || !fight.mob.isAlive()) {
                continue;
            }
            try {
                tickFight(fight);
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Dungeon boss {} tick failed: {}", fight.def.id(), e.toString());
            }
        }
    }

    private static void tickFight(Fight fight) {
        Mob mob = fight.mob;
        ServerLevel level = fight.level;
        long now = level.getGameTime();
        List<ServerPlayer> players = DungeonMarkers.playersNear(level, fight.home, BAR_RANGE, 24);
        for (ServerPlayer player : List.copyOf(fight.bar.getPlayers())) {
            if (!players.contains(player)) {
                fight.bar.removePlayer(player);
            }
        }
        players.forEach(fight.bar::addPlayer);
        if (now % 5 == 0) {
            updateBar(fight);
        }
        if (players.isEmpty()) {
            if (fight.lonelySince < 0) {
                fight.lonelySince = now;
            } else if (now - fight.lonelySince > GIVE_UP_TICKS) {
                FIGHTS.remove(mob.getUUID());
                sleep(fight);
            }
            return;
        }
        fight.lonelySince = -1;
        if (mob.position().distanceTo(fight.home) > LEASH) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.5, 1, 0.5, 0.1);
            mob.teleportTo(fight.home.x, fight.home.y, fight.home.z);
            mob.getNavigation().stop();
        }
        if (!(mob.getTarget() instanceof ServerPlayer target) || !target.isAlive() || !players.contains(target)) {
            mob.setTarget(nearest(players, mob.position()));
        }
        if (fight.warded() && now % 4 == 0) {
            ring(level, ParticleTypes.ENCHANTED_HIT, mob.position().add(0, mob.getBbHeight() * 0.5, 0), mob.getBbWidth() + 0.6, 14);
        }
        double fraction = mob.getHealth() / mob.getMaxHealth();
        List<Dungeons.Phase> phases = fight.def.phases();
        while (fight.phase < phases.size() && fraction <= phases.get(fight.phase).below()) {
            announcePhase(fight, phases.get(fight.phase));
            fight.phase++;
        }
        List<Dungeons.Move> moves = fight.def.moves();
        for (int i = 0; i < moves.size(); i++) {
            Dungeons.Move move = moves.get(i);
            if (fraction > move.below()) {
                continue;
            }
            if (move.type().equals("enrage")) {
                if (!fight.used[i]) {
                    fight.used[i] = true;
                    warn(fight, move);
                    enrage(fight, move);
                }
                continue;
            }
            if (now >= fight.next[i]) {
                fight.next[i] = now + move.every();
                warn(fight, move);
                perform(fight, move, players);
            }
        }
    }

    private static void perform(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        switch (move.type()) {
            case "slam" -> slam(fight, move);
            case "summon" -> summon(fight, move);
            case "leap" -> leap(fight, move, players);
            case "volley" -> volley(fight, move);
            case "pull" -> pull(fight, move, players);
            case "shockwave" -> shockwave(fight, move);
            case "blink" -> blink(fight);
            case "webs" -> webs(fight, move, players);
            case "molten" -> molten(fight, move, players);
            case "cloud" -> cloud(fight, move);
            case "strike" -> strike(fight, move, players);
            case "spikes" -> spikes(fight, move);
            case "heal" -> heal(fight, move);
            case "ward" -> ward(fight, move);
            default -> SkyRpgWorld.LOGGER.warn("Unknown boss move type {} on {}", move.type(), fight.def.id());
        }
    }

    // ---------------------------------------------------------------- moves

    private static void slam(Fight fight, Dungeons.Move move) {
        double radius = move.num("radius", 5);
        int windup = (int) move.num("windup", 20);
        ParticleOptions particle = particle(move, ParticleTypes.CRIT);
        Mob mob = fight.mob;
        mob.getNavigation().stop();
        for (int t = 0; t < windup; t += 2) {
            later(fight, t, () -> ring(fight.level, particle, groundAt(mob), radius, 28));
        }
        later(fight, windup, () -> {
            Vec3 center = groundAt(mob);
            for (double r = 1; r <= radius; r += 1) {
                ring(fight.level, ParticleTypes.EXPLOSION, center, r, (int) (r * 4));
            }
            sound(fight, move, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.7f);
            for (ServerPlayer player : DungeonMarkers.playersNear(fight.level, center, radius, 3)) {
                hurt(fight, player, move.num("damage", 10));
                player.push(0, move.num("knockup", 0.8), 0);
                player.syncVelocity = true;
            }
        });
    }

    private static void summon(Fight fight, Dungeons.Move move) {
        fight.minions.removeIf(id -> {
            Entity minion = fight.level.getEntity(id);
            return minion == null || !minion.isAlive();
        });
        int room = fight.def.maxMinions() - fight.minions.size();
        if (move.json().has("max")) {
            room = Math.min(room, (int) move.num("max", room));
        }
        JsonArray mobs = move.json().has("mobs") && move.json().get("mobs").isJsonArray() ? move.json().getAsJsonArray("mobs") : new JsonArray();
        if (room <= 0 || mobs.isEmpty()) {
            return;
        }
        for (Mob minion : MobSpawning.spawn(fight.level, mobs, fight.mob.position(), 4, room, null)) {
            minion.addTag(MINION_TAG);
            minion.setTarget(fight.mob.getTarget());
            fight.minions.add(minion.getUUID());
            fight.level.sendParticles(particle(move, ParticleTypes.SOUL_FIRE_FLAME), minion.getX(), minion.getY() + 0.5, minion.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        sound(fight, move, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.8f);
    }

    private static void leap(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        Mob mob = fight.mob;
        double range = move.num("range", 16);
        List<ServerPlayer> inRange = players.stream().filter(p -> p.distanceTo(mob) <= range).toList();
        if (inRange.isEmpty()) {
            return;
        }
        ServerPlayer target = inRange.get(fight.level.getRandom().nextInt(inRange.size()));
        Vec3 to = target.position().subtract(mob.position());
        mob.setDeltaMovement(to.x / 18, 0.9, to.z / 18);
        mob.syncVelocity = true;
        sound(fight, move, SoundEvents.RAVAGER_ATTACK, 1.2f, 0.8f);
        double radius = move.num("radius", 3);
        for (int t = 8; t <= 40; t += 2) {
            final int tick = t;
            later(fight, t, () -> {
                if (fight.used.length >= 0 && (mob.onGround() || tick == 40) && !mob.entityTags().contains("skyrpg_landed_" + tick)) {
                    if (mob.entityTags().stream().anyMatch(tag -> tag.startsWith("skyrpg_leap_landed"))) {
                        return;
                    }
                    mob.addTag("skyrpg_leap_landed");
                    Vec3 center = groundAt(mob);
                    ring(fight.level, particle(move, ParticleTypes.EXPLOSION), center, radius, 16);
                    sound(fight, move, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.0f);
                    for (ServerPlayer player : DungeonMarkers.playersNear(fight.level, center, radius, 3)) {
                        hurt(fight, player, move.num("damage", 10));
                    }
                }
            });
        }
        later(fight, 42, () -> mob.removeTag("skyrpg_leap_landed"));
    }

    private static void volley(Fight fight, Dungeons.Move move) {
        Mob mob = fight.mob;
        if (!(mob.getTarget() instanceof LivingEntity target)) {
            return;
        }
        String kind = move.str("projectile", "arrow");
        int count = (int) move.num("count", 5);
        double damage = move.num("damage", 6);
        float spread = (float) (move.num("spread", 0.15) * 40);
        for (int i = 0; i < count; i++) {
            later(fight, i * 3, () -> shoot(fight, kind, target, damage, spread));
        }
    }

    private static void shoot(Fight fight, String kind, LivingEntity target, double damage, float spread) {
        Mob mob = fight.mob;
        ServerLevel level = fight.level;
        if (!mob.isAlive() || !target.isAlive()) {
            return;
        }
        Vec3 from = mob.getEyePosition();
        Vec3 aim = target.getEyePosition().subtract(from);
        double distance = aim.horizontalDistance();
        Projectile projectile;
        float velocity = 1.6f;
        switch (kind) {
            case "fireball" -> {
                projectile = new SmallFireball(level, mob, aim.normalize());
                projectile.setPos(from.x, from.y, from.z);
                level.addFreshEntity(projectile);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.8f);
                return;
            }
            case "trident" -> {
                ThrownTrident trident = new ThrownTrident(level, mob, new ItemStack(Items.TRIDENT));
                trident.pickup = AbstractArrow.Pickup.DISALLOWED;
                trident.setBaseDamage(damage / velocity);
                projectile = trident;
                later(fight, 100, trident::discard);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.DROWNED_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
            case "web", "snowball" -> {
                Snowball ball = new Snowball(level, mob, new ItemStack(kind.equals("web") ? Items.COBWEB : Items.SNOWBALL));
                projectile = ball;
                velocity = 1.3f;
                if (kind.equals("web")) {
                    WEB_SHOTS.add(new WebShot(ball, level, level.getGameTime() + 60));
                }
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.6f);
            }
            default -> {
                Arrow arrow = new Arrow(level, mob, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setBaseDamage(damage / velocity);
                projectile = arrow;
                later(fight, 100, arrow::discard);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0f, 0.9f);
            }
        }
        projectile.setPos(from.x, from.y, from.z);
        projectile.shoot(aim.x, aim.y + distance * 0.12, aim.z, velocity, spread);
        level.addFreshEntity(projectile);
    }

    private static void pull(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        double radius = move.num("radius", 12);
        double strength = move.num("strength", 1.0);
        Mob mob = fight.mob;
        for (int t = 0; t < 10; t += 2) {
            later(fight, t, () -> ring(fight.level, particle(move, ParticleTypes.PORTAL), groundAt(mob), radius * 0.6, 24));
        }
        later(fight, 10, () -> {
            sound(fight, move, SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.6f);
            for (ServerPlayer player : players) {
                Vec3 to = mob.position().subtract(player.position());
                if (!player.isAlive() || to.length() > radius || to.length() < 1.5) {
                    continue;
                }
                Vec3 pull = to.normalize().scale(0.7 * strength);
                player.push(pull.x, 0.25, pull.z);
                player.syncVelocity = true;
            }
        });
    }

    private static void shockwave(Fight fight, Dungeons.Move move) {
        double radius = move.num("radius", 8);
        Mob mob = fight.mob;
        ParticleOptions particle = particle(move, ParticleTypes.SWEEP_ATTACK);
        sound(fight, move, SoundEvents.WARDEN_ROAR, 1.2f, 1.2f);
        for (int t = 0; t <= 10; t += 2) {
            final double r = radius * (t + 2) / 12.0;
            later(fight, t, () -> ring(fight.level, particle, groundAt(mob).add(0, 0.4, 0), r, (int) (r * 6) + 6));
        }
        later(fight, 6, () -> {
            for (ServerPlayer player : DungeonMarkers.playersNear(fight.level, mob.position(), radius, 4)) {
                hurt(fight, player, move.num("damage", 8));
                Vec3 away = player.position().subtract(mob.position());
                Vec3 push = away.lengthSqr() > 0.01 ? away.normalize().scale(move.num("push", 1.5)) : new Vec3(0.6, 0, 0);
                player.push(push.x, 0.45, push.z);
                player.syncVelocity = true;
            }
        });
    }

    private static void blink(Fight fight) {
        Mob mob = fight.mob;
        if (!(mob.getTarget() instanceof LivingEntity target)) {
            return;
        }
        Vec3 look = target.getLookAngle();
        Vec3 behind = target.position().subtract(look.x * 2.5, 0, look.z * 2.5);
        Vec3 from = mob.position();
        fight.level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.5, 1, 0.5, 0.1);
        mob.teleportTo(behind.x, target.getY(), behind.z);
        if (!fight.level.noCollision(mob)) {
            mob.teleportTo(from.x, from.y, from.z);
            return;
        }
        fight.level.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, target.getY() + 1, behind.z, 40, 0.5, 1, 0.5, 0.1);
        fight.level.playSound(null, behind.x, target.getY(), behind.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 0.7f);
    }

    private static void webs(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        int count = (int) move.num("count", 8);
        double radius = move.num("radius", 4);
        int duration = (int) move.num("duration", 100);
        RandomSource random = fight.level.getRandom();
        for (ServerPlayer player : players) {
            if (player.distanceTo(fight.mob) > 20) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                BlockPos pos = BlockPos.containing(player.getX() + (random.nextDouble() * 2 - 1) * radius, player.getY() + random.nextInt(2),
                        player.getZ() + (random.nextDouble() * 2 - 1) * radius);
                tempBlock(fight.level, pos, Blocks.COBWEB, duration);
            }
            tempBlock(fight.level, player.blockPosition(), Blocks.COBWEB, duration / 2);
        }
        sound(fight, move, SoundEvents.SPIDER_AMBIENT, 1.4f, 0.5f);
    }

    /**
     * The floor around each player glows for a moment, then turns to magma for {@code duration} ticks: anyone standing on
     * it takes {@code damage} every half second and catches fire. Only plain solid blocks melt, and they come back after.
     */
    private static void molten(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        int count = (int) move.num("count", 10);
        double radius = move.num("radius", 4);
        int duration = (int) move.num("duration", 100);
        int delay = (int) move.num("delay", 25);
        double damage = move.num("damage", 10);
        RandomSource random = fight.level.getRandom();
        Set<BlockPos> spots = new HashSet<>();
        for (ServerPlayer player : players) {
            if (player.distanceTo(fight.mob) > 20) {
                continue;
            }
            addFloor(fight.level, player.blockPosition(), spots);
            for (int i = 0; i < count; i++) {
                addFloor(fight.level, BlockPos.containing(player.getX() + (random.nextDouble() * 2 - 1) * radius, player.getY(),
                        player.getZ() + (random.nextDouble() * 2 - 1) * radius), spots);
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        for (int t = 0; t < delay; t += 5) {
            later(fight, t, () -> {
                for (BlockPos pos : spots) {
                    fight.level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.3, 0.02, 0.3, 0.005);
                }
            });
        }
        sound(fight, move, SoundEvents.LAVA_AMBIENT, 1.6f, 0.6f);
        later(fight, delay, () -> {
            for (BlockPos pos : spots) {
                BlockState original = fight.level.getBlockState(pos);
                if (original.is(Blocks.MAGMA_BLOCK)) {
                    continue;
                }
                fight.level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                TEMP_BLOCKS.add(new TempBlock(fight.level, pos, Blocks.MAGMA_BLOCK, original, fight.level.getGameTime() + duration));
                fight.level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0);
            }
            fight.level.playSound(null, fight.mob.getX(), fight.mob.getY(), fight.mob.getZ(), SoundEvents.FIRECHARGE_USE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.4f, 0.6f);
        });
        for (int t = delay; t < delay + duration; t += 10) {
            later(fight, t, () -> {
                for (ServerPlayer player : fight.level.players()) {
                    if (player.isSpectator() || player.isCreative() || player.distanceTo(fight.mob) > 32) {
                        continue;
                    }
                    BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
                    if (spots.contains(under) && fight.level.getBlockState(under).is(Blocks.MAGMA_BLOCK)) {
                        hurt(fight, player, damage);
                        player.igniteForSeconds(3);
                    }
                }
            });
        }
    }

    /** The floor block under a spot, if it's a plain solid block that may melt (no chests, spawners or markers). */
    private static void addFloor(ServerLevel level, BlockPos column, Set<BlockPos> spots) {
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos pos = column.offset(0, dy - 1, 0);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos) && state.getDestroySpeed(level, pos) >= 0
                    && !state.is(Blocks.MAGMA_BLOCK) && level.getBlockState(pos.above()).isAir()) {
                spots.add(pos.immutable());
            }
            return;
        }
    }

    private static void cloud(Fight fight, Dungeons.Move move) {
        Mob mob = fight.mob;
        Entity where = move.str("on", "target").equals("self") || mob.getTarget() == null ? mob : mob.getTarget();
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.tryParse(move.str("effect", "minecraft:poison")))
                .map(reference -> (Holder<MobEffect>) reference).orElse(null);
        AreaEffectCloud cloud = new AreaEffectCloud(fight.level, where.getX(), where.getY(), where.getZ());
        cloud.setOwner(mob);
        cloud.setRadius((float) move.num("radius", 3.5));
        cloud.setRadiusOnUse(0);
        cloud.setRadiusPerTick(0);
        cloud.setDuration((int) move.num("duration", 100));
        cloud.setWaitTime(10);
        if (effect != null) {
            cloud.addEffect(new MobEffectInstance(effect, 60, (int) move.num("amplifier", 0)));
        }
        if (move.json().has("particle")) {
            cloud.setCustomParticle(particle(move, ParticleTypes.WITCH));
        }
        fight.level.addFreshEntity(cloud);
        sound(fight, move, SoundEvents.SPLASH_POTION_BREAK, 1.0f, 0.7f);
    }

    /**
     * Spots around each player light up (a ring of particles), then blast: {@code count} per player within {@code spread}
     * blocks of them, each {@code blast} blocks wide after {@code delay} ticks, dealing {@code damage}, setting fire for
     * {@code ignite} seconds and applying {@code effect}. With {@code lightning} a visual bolt strikes each spot.
     */
    private static void strike(Fight fight, Dungeons.Move move, List<ServerPlayer> players) {
        int count = (int) move.num("count", 3);
        double spread = move.num("spread", 5);
        double blast = move.num("blast", 2.5);
        int delay = (int) move.num("delay", 30);
        double damage = move.num("damage", 10);
        boolean lightning = move.json().has("lightning") && move.json().get("lightning").getAsBoolean();
        ParticleOptions particle = particle(move, ParticleTypes.FLAME);
        RandomSource random = fight.level.getRandom();
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player.distanceTo(fight.mob) > 24) {
                continue;
            }
            spots.add(player.position());
            for (int i = 1; i < count; i++) {
                spots.add(new Vec3(player.getX() + (random.nextDouble() * 2 - 1) * spread, player.getY(), player.getZ() + (random.nextDouble() * 2 - 1) * spread));
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        sound(fight, move, SoundEvents.BLAZE_SHOOT, 1.4f, 0.6f);
        for (int t = 0; t < delay; t += 4) {
            later(fight, t, () -> {
                for (Vec3 spot : spots) {
                    ring(fight.level, particle, groundNear(fight.level, spot), blast, 16);
                }
            });
        }
        later(fight, delay, () -> {
            for (Vec3 spot : spots) {
                Vec3 centre = groundNear(fight.level, spot);
                fight.level.sendParticles(particle, centre.x, centre.y + 0.6, centre.z, 40, blast * 0.4, 0.8, blast * 0.4, 0.05);
                if (lightning) {
                    LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(fight.level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setVisualOnly(true);
                        bolt.snapTo(centre.x, centre.y, centre.z, 0, 0);
                        fight.level.addFreshEntity(bolt);
                    }
                } else {
                    sound(fight, move, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
                }
                for (ServerPlayer player : DungeonMarkers.playersNear(fight.level, centre, blast, 3)) {
                    hurt(fight, player, damage);
                    if (move.num("ignite", 0) > 0) {
                        player.igniteForSeconds((float) move.num("ignite", 0));
                    }
                    applyEffect(player, move);
                }
            }
        });
    }

    /**
     * Rings of spikes expand from the boss: each shows as particles on the ground for {@code windup} ticks, then hits
     * everyone standing on it ({@code damage}, {@code freeze} ticks of frost, {@code effect}). {@code rings} rings,
     * reaching out to {@code radius}.
     */
    private static void spikes(Fight fight, Dungeons.Move move) {
        int rings = (int) move.num("rings", 3);
        double radius = move.num("radius", 12);
        int windup = (int) move.num("windup", 20);
        double damage = move.num("damage", 10);
        int freeze = (int) move.num("freeze", 140);
        ParticleOptions particle = particle(move, ParticleTypes.SNOWFLAKE);
        Mob mob = fight.mob;
        Vec3 centre = groundAt(mob);
        Set<UUID> hit = new HashSet<>();
        sound(fight, move, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        for (int i = 0; i < rings; i++) {
            double r = radius * (i + 1) / rings;
            int at = windup + i * 8;
            for (int t = Math.max(0, at - 14); t < at; t += 3) {
                later(fight, t, () -> ring(fight.level, ParticleTypes.CRIT, centre, r, (int) (r * 5) + 8));
            }
            later(fight, at, () -> {
                ring(fight.level, particle, centre.add(0, 0.3, 0), r, (int) (r * 8) + 8);
                ring(fight.level, ParticleTypes.ITEM_SNOWBALL, centre.add(0, 0.8, 0), r, (int) (r * 5) + 8);
                fight.level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 1.0f, 0.9f);
                for (ServerPlayer player : DungeonMarkers.playersNear(fight.level, centre, r + 1.8, 4)) {
                    double distance = Math.hypot(player.getX() - centre.x, player.getZ() - centre.z);
                    if (Math.abs(distance - r) <= 1.8 && hit.add(player.getUUID())) {
                        hurt(fight, player, damage);
                        player.setTicksFrozen(Math.max(player.getTicksFrozen(), freeze));
                        applyEffect(player, move);
                        player.push(0, 0.5, 0);
                        player.syncVelocity = true;
                    }
                }
            });
        }
    }

    private static void applyEffect(ServerPlayer player, Dungeons.Move move) {
        if (!move.json().has("effect")) {
            return;
        }
        BuiltInRegistries.MOB_EFFECT.get(Identifier.tryParse(move.str("effect", "minecraft:slowness"))).ifPresent(effect ->
                player.addEffect(new MobEffectInstance(effect, (int) move.num("effect_duration", 80), (int) move.num("amplifier", 0))));
    }

    /** The ground (top solid block) near a spot, so telegraphs sit on the floor and not in the air. */
    private static Vec3 groundNear(ServerLevel level, Vec3 spot) {
        BlockPos pos = BlockPos.containing(spot);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos at = pos.offset(0, dy, 0);
            if (!level.getBlockState(at.below()).isAir() && level.getBlockState(at).isAir()) {
                return new Vec3(spot.x, at.getY() + 0.1, spot.z);
            }
        }
        return new Vec3(spot.x, spot.y + 0.1, spot.z);
    }

    private static void enrage(Fight fight, Dungeons.Move move) {
        fight.enraged = true;
        modifier(fight.mob, Attributes.MOVEMENT_SPEED, ENRAGE_SPEED, move.num("speed", 0.3));
        modifier(fight.mob, Attributes.ATTACK_DAMAGE, ENRAGE_DAMAGE, move.num("damage", 0.3));
        fight.level.sendParticles(particle(move, ParticleTypes.ANGRY_VILLAGER), fight.mob.getX(), fight.mob.getY() + fight.mob.getBbHeight(),
                fight.mob.getZ(), 20, 0.8, 0.5, 0.8, 0.1);
        sound(fight, move, SoundEvents.RAVAGER_ROAR, 2.0f, 0.5f);
        updateBar(fight);
    }

    private static void heal(Fight fight, Dungeons.Move move) {
        Mob mob = fight.mob;
        mob.heal((float) (mob.getMaxHealth() * move.num("percent", 0.05)));
        fight.level.sendParticles(particle(move, ParticleTypes.HAPPY_VILLAGER), mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.8, 1, 0.8, 0.05);
        sound(fight, move, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
    }

    private static void ward(Fight fight, Dungeons.Move move) {
        fight.wardUntil = fight.level.getGameTime() + (long) move.num("duration", 160);
        fight.wardReduction = Math.max(0, Math.min(0.95, move.num("reduction", 0.9)));
        sound(fight, move, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.8f);
        updateBar(fight);
    }

    // ---------------------------------------------------------------- the end of a fight

    /** Nobody's fighting: the boss (and its minions) vanish, and its room can wake again. */
    private static void sleep(Fight fight) {
        fight.bar.removeAllPlayers();
        fight.level.sendParticles(ParticleTypes.SOUL, fight.mob.getX(), fight.mob.getY() + 1, fight.mob.getZ(), 30, 0.6, 1, 0.6, 0.03);
        discardMinions(fight);
        fight.mob.discard();
        Marker marker = DungeonMarkers.loaded(fight.marker);
        if (marker != null) {
            DungeonMarkers.setState(marker, new CompoundTag());
        }
    }

    private static void discardMinions(Fight fight) {
        for (UUID id : fight.minions) {
            Entity minion = fight.level.getEntity(id);
            if (minion != null && minion.isAlive()) {
                fight.level.sendParticles(ParticleTypes.SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
                minion.discard();
            }
        }
        fight.minions.clear();
    }

    private static void defeated(Fight fight, Entity killer) {
        ServerLevel level = fight.level;
        Mob mob = fight.mob;
        fight.bar.removeAllPlayers();
        discardMinions(fight);
        List<ServerPlayer> winners = new ArrayList<>();
        for (UUID id : fight.hitters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && player.distanceTo(mob) < 64) {
                winners.add(player);
            }
        }
        if (winners.isEmpty()) {
            winners.addAll(DungeonMarkers.playersNear(level, mob.position(), 32, 16));
        }
        for (ServerPlayer player : winners) {
            reward(fight, player);
        }
        Marker marker = DungeonMarkers.loaded(fight.marker);
        String instance = marker == null ? null : DungeonMarkers.info(level, marker).instance();
        if (fight.def.champion()) {
            DungeonRuns.of(instance).champions++;
        } else if (fight.def.rewardLoot() != null && !winners.isEmpty()) {
            DungeonRuns.Progress progress = DungeonRuns.of(instance);
            int bonus = progress.bonusRolls();
            rewardChest(level, fight, winners, bonus);
            for (ServerPlayer player : winners) {
                Notice.of(Notice.Kind.DUNGEON).tag("REWARD CHEST").send(player, Component.literal("Run: " + progress.summary() + " → ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Reward Chest x" + (1 + bonus)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            }
            DungeonRuns.reset(instance);
        }
        if (marker != null) {
            CompoundTag state = new CompoundTag();
            state.putBoolean("killed", true);
            state.putLong("at", level.getGameTime());
            DungeonMarkers.setState(marker, state);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1, mob.getZ(), 120, 1.0, 1.5, 1.0, 0.4);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0f, 1.2f);
        ExperienceOrb.award(level, mob.position(), 120);
    }

    private static void reward(Fight fight, ServerPlayer player) {
        Dungeons.Boss def = fight.def;
        ServerLevel level = fight.level;
        if (!def.death().isEmpty()) {
            player.sendSystemMessage(Component.literal(def.death()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        int dropped = 0;
        if (def.loot() != null) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, def.loot()));
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, fight.mob.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                give(player, stack);
                dropped++;
            }
        }
        long coins = def.coinsMax() <= 0 ? 0
                : def.coinsMin() + (long) (level.getRandom().nextDouble() * (def.coinsMax() - def.coinsMin() + 1));
        if (coins > 0) {
            Wallet.give(player, coins);
        }
        DungeonRuns.delving(player, def.delvingXp());
        if (coins > 0 || def.delvingXp() > 0) {
            Notice.of(Notice.Kind.DUNGEON).tag(def.region() ? "REGION BOSS SLAIN" : def.champion() ? "CHAMPION SLAIN" : "BOSS SLAIN").send(player, Component.literal("+").withStyle(ChatFormatting.GRAY).append(Msg.coins(coins))
                    .append(def.delvingXp() > 0 ? Component.literal(" · +" + Msg.number((long) def.delvingXp()) + " Delving XP").withStyle(ChatFormatting.DARK_AQUA)
                            : Component.empty()));
        }
        boolean first = Explorer.clearBoss(player, def.id());
        com.skyrpg.core.event.BossKills.KILLED.invoker().onKilled(player, def.id(), def.level(), def.region()
                ? com.skyrpg.core.event.BossKills.Kind.REGION_BOSS : def.champion()
                ? com.skyrpg.core.event.BossKills.Kind.DUNGEON_CHAMPION : com.skyrpg.core.event.BossKills.Kind.DUNGEON_BOSS);
        int color = barRgb(def.barColor());
        if (first) {
            if (def.firstClearCoins() > 0) {
                Wallet.give(player, def.firstClearCoins());
            }
            def.firstClearXp().forEach((skill, xp) -> SkillLevels.giveXp(player, skill, xp));
            MutableComponent subtitle = Component.literal("First clear! +").append(Msg.coins(def.firstClearCoins()));
            WorldPayloads.banner(player, Identifier.withDefaultNamespace("nether_star"), "FIRST CLEAR", Component.literal(def.name()), subtitle, color);
            Notice.of(Notice.Kind.DUNGEON).icon(Notice.TROPHY).tag("FIRST CLEAR").color(0xFFAA00).send(player, Component.literal(def.name()).withColor(color)
                    .append(Component.literal(" · +").withStyle(ChatFormatting.GRAY)).append(Msg.coins(def.firstClearCoins())));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            WorldPayloads.banner(player, Identifier.withDefaultNamespace("iron_sword"), def.champion() ? "CHAMPION SLAIN" : "BOSS DEFEATED",
                    Component.literal(def.name()), Component.literal("+").append(Msg.coins(coins))
                            .append(Component.literal(" · " + dropped + " drop" + (dropped == 1 ? "" : "s"))), color);
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            item.setNoPickUpDelay();
            player.level().addFreshEntity(item);
        }
    }

    /** A phase change: told to everyone in the fight, with a burst of effects. */
    private static void announcePhase(Fight fight, Dungeons.Phase phase) {
        Mob mob = fight.mob;
        if (!phase.message().isEmpty()) {
            Component text = Component.literal(fight.def.name() + ": ").withColor(barRgb(fight.def.barColor())).withStyle(ChatFormatting.BOLD)
                    .append(Component.literal(phase.message()).withStyle(ChatFormatting.WHITE));
            for (ServerPlayer player : fight.bar.getPlayers()) {
                player.sendSystemMessage(text);
            }
        }
        fight.level.sendParticles(ParticleTypes.SONIC_BOOM, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 1, 0, 0, 0, 0);
        fight.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 60, 1.2, 1.0, 1.2, 0.08);
        fight.level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.6f, 0.8f);
    }

    /**
     * The boss leaves a Reward Chest where it fell: one per-player chest (only the winners may open it) that rolls
     * {@code reward_loot} once plus {@code bonus} more times, and goes away when the dungeon resets.
     */
    private static void rewardChest(ServerLevel level, Fight fight, List<ServerPlayer> winners, int bonus) {
        BlockPos pos = BlockPos.containing(fight.home);
        for (int up = 0; up < 4 && !level.getBlockState(pos).canBeReplaced(); up++) {
            pos = pos.above();
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return;
        }
        boolean wet = level.getFluidState(pos).is(net.minecraft.world.level.material.Fluids.WATER);
        Marker marker = EntityTypes.MARKER.create(level, EntitySpawnReason.TRIGGERED);
        if (marker == null) {
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED, wet), Block.UPDATE_ALL);
        marker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        marker.addTag(DungeonMarkers.TAG);
        CompoundTag data = new CompoundTag();
        data.putString("skyrpg", "chest");
        data.putString("loot", fight.def.rewardLoot().toString());
        data.putString("tier", "reward");
        data.putInt("bonus", bonus);
        data.putBoolean("temporary", true);
        ListTag owners = new ListTag();
        winners.forEach(player -> owners.add(StringTag.valueOf(player.getUUID().toString())));
        data.put("for", owners);
        CompoundTag state = new CompoundTag();
        state.putLong("at", level.getGameTime());
        data.put("state", state);
        DungeonMarkers.setData(marker, data);
        level.addFreshEntity(marker);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.3);
        level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.4f, 0.9f);
    }

    // ---------------------------------------------------------------- helpers

    private static void updateBar(Fight fight) {
        Mob mob = fight.mob;
        fight.bar.setProgress(Math.max(0, Math.min(1, mob.getHealth() / mob.getMaxHealth())));
        double shown = mob.getHealth() / mob.getMaxHealth() * fight.totalHealth;
        MutableComponent name = Component.literal(fight.def.name()).withColor(barRgb(fight.def.barColor())).withStyle(ChatFormatting.BOLD)
                .append(Component.literal("  " + Msg.compact(shown) + " ❤").withStyle(ChatFormatting.RED));
        if (fight.enraged) {
            name.append(Component.literal("  ENRAGED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        if (fight.warded()) {
            name.append(Component.literal("  ◈ WARDED").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        fight.bar.setName(name);
    }

    private static void warn(Fight fight, Dungeons.Move move) {
        if (move.warn().isEmpty()) {
            return;
        }
        Component text = Component.literal("⚠ " + move.warn()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer player : fight.bar.getPlayers()) {
            player.sendOverlayMessage(text);
        }
    }

    private static void hurt(Fight fight, ServerPlayer player, double damage) {
        player.hurtServer(fight.level, fight.mob.damageSources().mobAttack(fight.mob), (float) damage);
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, Vec3 to) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double distance = player.position().distanceToSqr(to);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The point on the ground under the mob (for rings that should hug the floor). */
    private static Vec3 groundAt(Mob mob) {
        return new Vec3(mob.getX(), mob.getY() + 0.1, mob.getZ());
    }

    private static void later(Fight fight, int ticks, Runnable run) {
        TASKS.add(new Task(fight.level.getServer().overworld().getGameTime() + ticks, () -> {
            if (fight.mob.isAlive()) {
                run.run();
            }
        }));
    }

    private static void tempBlock(ServerLevel level, BlockPos pos, Block block, int ticks) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            TEMP_BLOCKS.add(new TempBlock(level, pos, block, Blocks.AIR.defaultBlockState(), level.getGameTime() + ticks));
        }
    }

    private static void tickWebs(MinecraftServer server) {
        for (Iterator<WebShot> it = WEB_SHOTS.iterator(); it.hasNext(); ) {
            WebShot shot = it.next();
            if (shot.shot.isRemoved()) {
                tempBlock(shot.level, BlockPos.containing(shot.last), Blocks.COBWEB, 100);
                it.remove();
            } else if (shot.level.getGameTime() > shot.until) {
                shot.shot.discard();
                it.remove();
            } else {
                shot.last = shot.shot.position();
            }
        }
        if (TEMP_BLOCKS.isEmpty()) {
            return;
        }
        for (Iterator<TempBlock> it = TEMP_BLOCKS.iterator(); it.hasNext(); ) {
            TempBlock temp = it.next();
            if (temp.level().getGameTime() >= temp.until()) {
                if (temp.level().getBlockState(temp.pos()).is(temp.block())) {
                    temp.level().setBlock(temp.pos(), temp.original(), Block.UPDATE_ALL);
                }
                it.remove();
            }
        }
    }

    private static void sound(Fight fight, Dungeons.Move move, SoundEvent fallback, float volume, float pitch) {
        SoundEvent sound = fallback;
        if (move.json().has("sound")) {
            sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.tryParse(move.str("sound", ""))).orElse(fallback);
        }
        fight.level.playSound(null, fight.mob.getX(), fight.mob.getY(), fight.mob.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static ParticleOptions particle(Dungeons.Move move, ParticleOptions fallback) {
        if (!move.json().has("particle")) {
            return fallback;
        }
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.tryParse(move.str("particle", "")))
                .filter(type -> type instanceof SimpleParticleType).map(type -> (ParticleOptions) type).orElse(fallback);
    }

    static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static void set(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void modifier(Mob mob, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    static BossEvent.BossBarColor barColor(String name) {
        return switch (name) {
            case "pink" -> BossEvent.BossBarColor.PINK;
            case "blue" -> BossEvent.BossBarColor.BLUE;
            case "green" -> BossEvent.BossBarColor.GREEN;
            case "yellow" -> BossEvent.BossBarColor.YELLOW;
            case "purple" -> BossEvent.BossBarColor.PURPLE;
            case "white" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    static int barRgb(String name) {
        return switch (name) {
            case "pink" -> 0xFF6EC7;
            case "blue" -> 0x5CA8FF;
            case "green" -> 0x5CD68A;
            case "yellow" -> 0xFFD24A;
            case "purple" -> 0xB98CFF;
            case "white" -> 0xFFFFFF;
            default -> 0xFF5555;
        };
    }

    /** /dungeon bosses: what's fighting right now. */
    static List<String> fights() {
        List<String> lines = new ArrayList<>();
        for (Fight fight : FIGHTS.values()) {
            lines.add(fight.def.id() + " @ " + fight.mob.blockPosition().toShortString() + " · " + Math.round(fight.mob.getHealth()) + "/"
                    + Math.round(fight.mob.getMaxHealth()) + (fight.enraged ? " enraged" : ""));
        }
        return lines;
    }
}
