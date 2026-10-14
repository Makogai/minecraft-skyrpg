package com.skyrpg.world.bloodmoon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.core.world.MobLevelling;
import com.skyrpg.core.world.MobTags;
import com.skyrpg.world.DangerRegions;
import com.skyrpg.world.RegionBosses;
import com.skyrpg.world.RegionShape;
import com.skyrpg.world.SkyRpgWorld;
import com.skyrpg.world.WorldContent;
import com.skyrpg.world.WorldPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Blood Moon (docs/design/the_wild.md): about one night in eight the overworld's moon rises red. Everything hostile is
 * ten levels stronger, Bloodbound hordes spawn around every player (a region's own monsters inside a danger region),
 * Elites are three times as likely, drops are doubled, Blood Shards drop, and each danger region's heart awakens when a
 * player comes close. It lasts from dusk to dawn.
 *
 * <p>This class owns the schedule, the state (saved with the overworld, so a restart doesn't end the moon), the waves, the
 * drops and the tallies; {@link BloodMoon} in core carries "is it on" to the other mods, and clients get a
 * {@link BloodMoonSync} to paint the sky and the HUD.
 */
public final class BloodMoons {
    /** On every monster the moon spawned (waves, region hordes): they vanish at dawn. */
    public static final String TAG = "skyrpg_bloodbound";
    private static final int DAY = 24000;
    /** Time of day when a night is decided, and when dawn comes. */
    private static final int DUSK = 13000;
    private static final int DAWN = 23000;
    private static final double CHANCE = 0.12;
    /** A moon is guaranteed on the twelfth night without one. */
    private static final int GUARANTEE = 11;
    private static final int LEVELS = 10;
    private static final double SHARD_CHANCE = 0.04;
    private static final double ELITE_SHARD_CHANCE = 0.25;
    private static final int WAVE_CAP = 10;
    private static final int WAVE_EVERY = 100;
    private static final double AWAKEN_RANGE = 64;
    private static final String ELITE_TAG = "skyrpg_elite";
    private static final List<String> BOSS_TAGS = List.of("skyrpg_dungeon_boss", "skyrpg_world_boss");

    /** What the moon needs to remember: where it is in the schedule, and what has already stirred. */
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(d -> d.active),
                Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(d -> d.endsAt),
                Codec.LONG.optionalFieldOf("decided_day", -1L).forGetter(d -> d.decidedDay),
                Codec.INT.optionalFieldOf("since", 0).forGetter(d -> d.since),
                Codec.BOOL.optionalFieldOf("last_was_blood", false).forGetter(d -> d.lastWasBlood),
                Codec.STRING.listOf().optionalFieldOf("awakened", List.of()).forGetter(d -> List.copyOf(d.awakened)))
                .apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgWorld.id("blood_moon"), Data::new, CODEC, null);

        boolean active;
        long endsAt;
        long decidedDay = -1;
        int since;
        boolean lastWasBlood;
        final Set<String> awakened = new HashSet<>();

        Data() {
        }

        Data(boolean active, long endsAt, long decidedDay, int since, boolean lastWasBlood, List<String> awakened) {
            this.active = active;
            this.endsAt = endsAt;
            this.decidedDay = decidedDay;
            this.since = since;
            this.lastWasBlood = lastWasBlood;
            this.awakened.addAll(awakened);
        }
    }

    /** One player's night: monsters killed and Blood Shards found. */
    private static final class Tally {
        int kills;
        int shards;
    }

    private static final Map<UUID, Tally> TALLIES = new HashMap<>();
    /** Regions whose boss a player has been told about this moon. */
    private static final Map<UUID, Set<String>> TOLD = new HashMap<>();

    private BloodMoons() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(BloodMoonSync.TYPE, BloodMoonSync.CODEC);
        MobLevelling.registerBonus((level, pos) -> BloodMoon.active() && level.dimension() == Level.OVERWORLD ? LEVELS : 0);
        ServerTickEvents.END_SERVER_TICK.register(BloodMoons::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(BloodMoons::onDeath);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Data data = data(server);
            BloodMoon.set(data.active);
            TALLIES.clear();
            TOLD.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BloodMoon.set(false));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player, data(server)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            TALLIES.remove(handler.player.getUUID());
            TOLD.remove(handler.player.getUUID());
        });
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    // ---------------------------------------------------------------- schedule

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        Data data = data(server);
        long clock = overworld.getOverworldClockTime();
        if (data.active) {
            if (clock >= data.endsAt) {
                end(server, data, true);
                return;
            }
            tickActive(server, overworld, data, clock);
            return;
        }
        long day = Math.floorDiv(clock, DAY);
        long timeOfDay = Math.floorMod(clock, DAY);
        if (timeOfDay >= DUSK && timeOfDay < DAWN - 1000 && data.decidedDay != day && hasPlayer(overworld)) {
            data.decidedDay = day;
            boolean start;
            if (data.lastWasBlood) {
                // Never two nights running.
                data.lastWasBlood = false;
                data.since++;
                start = false;
            } else {
                start = data.since >= GUARANTEE || overworld.getRandom().nextDouble() < CHANCE;
                if (!start) {
                    data.since++;
                }
            }
            data.setDirty();
            if (start) {
                start(server, data, clock);
            }
        }
    }

    private static boolean hasPlayer(ServerLevel overworld) {
        for (ServerPlayer player : overworld.players()) {
            if (!player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /** Starts the moon now; it lasts until the next dawn. */
    public static boolean start(MinecraftServer server) {
        Data data = data(server);
        if (data.active) {
            return false;
        }
        start(server, data, server.overworld().getOverworldClockTime());
        return true;
    }

    private static void start(MinecraftServer server, Data data, long clock) {
        long day = Math.floorDiv(clock, DAY);
        long timeOfDay = Math.floorMod(clock, DAY);
        data.active = true;
        data.endsAt = timeOfDay < DAWN ? day * DAY + DAWN : (day + 1) * DAY + DAWN;
        data.since = 0;
        data.lastWasBlood = true;
        data.awakened.clear();
        data.setDirty();
        TALLIES.clear();
        TOLD.clear();
        BloodMoon.set(true);
        Component body = Msg.text("The moon rises red... ", ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Msg.text("Monsters are stronger, the old hearts stir, and the dead drop twice as much.", ChatFormatting.GRAY));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Notice.send(player, Notice.Kind.BLOOD_MOON, body);
            WorldPayloads.banner(player, Identifier.withDefaultNamespace("redstone"), "BLOOD MOON", Component.literal("The moon rises red"),
                    Component.literal("Monsters +" + LEVELS + " levels · double drops · Blood Shards"), 0xC8102E);
            player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            sync(player, data);
        }
        refreshStats(server);
    }

    /** Ends the moon now (/bloodmoon stop). */
    public static boolean stop(MinecraftServer server) {
        Data data = data(server);
        if (!data.active) {
            return false;
        }
        end(server, data, false);
        return true;
    }

    private static void end(MinecraftServer server, Data data, boolean dawn) {
        data.active = false;
        data.awakened.clear();
        data.setDirty();
        BloodMoon.set(false);
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.entityTags().contains(TAG))) {
                level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                mob.discard();
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Tally tally = TALLIES.get(player.getUUID());
            Component line = Msg.text(dawn ? "The Blood Moon sets. " : "The Blood Moon is gone. ", ChatFormatting.RED, ChatFormatting.BOLD);
            if (tally != null && (tally.kills > 0 || tally.shards > 0)) {
                line = line.copy().append(Msg.text("You slew " + tally.kills + " monsters and found " + tally.shards + " Blood Shards.", ChatFormatting.GRAY));
            } else {
                line = line.copy().append(Msg.text("You saw it out.", ChatFormatting.GRAY));
            }
            Notice.send(player, Notice.Kind.BLOOD_MOON, line);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0f, 0.6f);
            sync(player, data);
            // the heart waypoint only makes sense while the moon is up
            Set<String> told = TOLD.get(player.getUUID());
            if (told != null && !told.isEmpty()) {
                WaypointTarget.clear(player);
            }
        }
        TALLIES.clear();
        TOLD.clear();
        refreshStats(server);
    }

    private static void refreshStats(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(Stats::refresh);
    }

    // ---------------------------------------------------------------- while it lasts

    private static void tickActive(MinecraftServer server, ServerLevel overworld, Data data, long clock) {
        int tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (tick % 100 == 0) {
                sync(player, data);
            }
            if (player.level() != overworld || player.isSpectator()) {
                continue;
            }
            RegionShape region = DangerRegions.at(overworld, player.getBlockX(), player.getBlockZ());
            if (region != null) {
                heart(overworld, player, region, data);
            }
            if (region == null && tick % WAVE_EVERY == 0 && !player.isCreative()) {
                wave(overworld, player);
            }
        }
    }

    /** Players in a region hear about its boss; the first to come within range wakes it. */
    private static void heart(ServerLevel level, ServerPlayer player, RegionShape region, Data data) {
        BlockPos heart = DangerRegions.heartOf(level, region);
        String boss = RegionBosses.bossName(region);
        if (boss == null) {
            return;
        }
        Set<String> told = TOLD.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        if (told.add(region.key())) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(boss + " stirs").withColor(region.color()).withStyle(ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("The Blood Moon calls it from the heart of " + region.name())
                    .withStyle(ChatFormatting.RED)));
            WaypointTarget.send(player, region.name() + " heart", Vec3.atCenterOf(heart), 0xFF000000 | region.color());
            told.add(region.key() + "/guided");
        }
        double distance = Math.hypot(player.getX() - heart.getX() - 0.5, player.getZ() - heart.getZ() - 0.5);
        if (distance <= AWAKEN_RANGE && told.remove(region.key() + "/guided")) {
            WaypointTarget.clear(player);
        }
        if (distance <= AWAKEN_RANGE && data.awakened.add(region.key())) {
            data.setDirty();
            RegionBosses.wake(level, region, "the Blood Moon");
        }
    }

    /** A handful of Bloodbound monsters 20-40 blocks from a player on the surface, up to a cap around them. */
    private static void wave(ServerLevel level, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        if (!level.canSeeSky(at) && at.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 8) {
            return;
        }
        AABB around = player.getBoundingBox().inflate(56, 32, 56);
        int nearby = level.getEntitiesOfClass(Mob.class, around, mob -> mob.entityTags().contains(TAG)).size();
        if (nearby >= WAVE_CAP) {
            return;
        }
        RandomSource random = level.getRandom();
        int count = Math.min(WAVE_CAP - nearby, 2 + random.nextInt(3));
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 20 + random.nextDouble() * 20;
        for (int i = 0; i < count; i++) {
            double a = angle + (random.nextDouble() - 0.5) * 0.6;
            int x = (int) Math.floor(player.getX() + Math.cos(a) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(a) * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getFluidState(feet.below()).isEmpty() || !level.getFluidState(feet).isEmpty()) {
                continue;
            }
            EntityType<?> type = pick(random);
            Entity entity = type.create(level, EntitySpawnReason.EVENT);
            if (!(entity instanceof Mob mob)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }
            mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!level.noCollision(mob)) {
                mob.discard();
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.EVENT, null);
            mob.setCustomName(Component.literal("Bloodbound " + type.getDescription().getString()).withStyle(ChatFormatting.RED));
            mob.addTag(TAG);
            mob.setPersistenceRequired();
            mob.setTarget(player);
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.getX(), mob.getY() + 1, mob.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private static EntityType<?> pick(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 40) {
            return EntityTypes.ZOMBIE;
        }
        if (roll < 65) {
            return EntityTypes.SKELETON;
        }
        if (roll < 85) {
            return EntityTypes.SPIDER;
        }
        return EntityTypes.CREEPER;
    }

    // ---------------------------------------------------------------- kills

    private static void onDeath(net.minecraft.world.entity.LivingEntity entity, DamageSource source) {
        if (!BloodMoon.active() || !(entity instanceof Mob mob) || !(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || !(source.getEntity() instanceof ServerPlayer player)
                || mob.entityTags().contains(MobTags.FARMED)) {
            return;
        }
        Tally tally = TALLIES.computeIfAbsent(player.getUUID(), id -> new Tally());
        tally.kills++;
        boolean boss = BOSS_TAGS.stream().anyMatch(mob.entityTags()::contains);
        boolean region = mob.entityTags().stream().anyMatch(tag -> tag.startsWith(RegionBosses.BOSS_TAG));
        if (!boss && !region) {
            doubleLoot(level, mob, source, player);
        }
        RandomSource random = level.getRandom();
        int shards = 0;
        if (boss) {
            shards = 2 + random.nextInt(3);
        } else if (!region && random.nextDouble() < (mob.entityTags().contains(ELITE_TAG) ? ELITE_SHARD_CHANCE : SHARD_CHANCE)) {
            shards = 1 + (mob.entityTags().contains(ELITE_TAG) ? random.nextInt(2) : 0);
        }
        if (shards > 0) {
            tally.shards += shards;
            ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(WorldContent.BLOOD_SHARD, shards));
            drop.setGlowingTag(true);
            level.addFreshEntity(drop);
            player.sendOverlayMessage(Component.literal("+" + shards + " Blood Shard" + (shards == 1 ? "" : "s")).withStyle(ChatFormatting.RED));
        }
    }

    /** The mob's loot table rolled a second time. (Region materials are rolled twice in {@link DangerRegions}.) */
    private static void doubleLoot(ServerLevel level, Mob mob, DamageSource source, ServerPlayer player) {
        var key = mob.getLootTable();
        if (key.isEmpty()) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        for (ItemStack stack : table.getRandomItems(params)) {
            mob.spawnAtLocation(level, stack);
        }
    }

    // ---------------------------------------------------------------- sync and commands

    private static void sync(ServerPlayer player, Data data) {
        if (!ServerPlayNetworking.canSend(player, BloodMoonSync.TYPE)) {
            return;
        }
        long left = data.active ? Math.max(0, data.endsAt - player.level().getServer().overworld().getOverworldClockTime()) : 0;
        ServerPlayNetworking.send(player, new BloodMoonSync(data.active, (int) Math.min(left, Integer.MAX_VALUE)));
    }

    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bloodmoon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").executes(ctx -> {
                    boolean started = start(ctx.getSource().getServer());
                    if (started) {
                        ctx.getSource().sendSuccess(() -> Component.literal("The Blood Moon rises"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("A Blood Moon is already up"));
                    }
                    return started ? 1 : 0;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    boolean stopped = stop(ctx.getSource().getServer());
                    if (stopped) {
                        ctx.getSource().sendSuccess(() -> Component.literal("The Blood Moon is over"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No Blood Moon right now"));
                    }
                    return stopped ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    Data data = data(server);
                    long clock = server.overworld().getOverworldClockTime();
                    List<String> lines = new ArrayList<>();
                    if (data.active) {
                        long left = Math.max(0, data.endsAt - clock);
                        lines.add("Blood Moon: ON, " + left / 20 / 60 + "m " + left / 20 % 60 + "s until dawn; " + data.awakened.size() + " hearts awake");
                    } else {
                        lines.add("Blood Moon: off. Nights since the last one: " + data.since + (data.lastWasBlood ? " (last night had one)" : "")
                                + ", next roll " + (data.since >= GUARANTEE ? "guaranteed" : (int) (CHANCE * 100) + "%") + " at dusk");
                    }
                    lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
                    return 1;
                })));
    }
}
