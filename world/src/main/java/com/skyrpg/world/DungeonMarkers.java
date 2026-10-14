package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.skyrpg.world.mixin.EntityAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * Brings dungeon templates to life. Every template carries invisible marker entities (tag {@value #TAG}) whose data
 * says what they are: mob packs, bosses, puzzle doors and their levers and plates, vault doors, refilling chests,
 * secrets and the dungeon's entrance. This class tracks the loaded ones and acts on them every half second.
 * Kinds and fields: docs/design/dungeons.md.
 *
 * <p>A marker keeps its own state in its data under "state", so it survives restarts and chunk unloads. Everything
 * that gets used up (a mob pack, an open door, a looted chest, a dead boss) comes back after the dungeon's reset time,
 * but only when nobody is around to see it happen.
 */
public final class DungeonMarkers {
    public static final String TAG = "skyrpg_dungeon";
    private static final int PERIOD = 10;
    private static final double DISCOVER_RADIUS = 20;
    private static final double SECRET_RADIUS = 2.5;
    /** Used-up parts only come back with no player this close. */
    private static final double RESET_CLEARANCE = 32;
    /** Without a structure piece to scope a group (a /place'd dungeon), parts within this range belong together. */
    private static final double GROUP_RANGE = 24;
    private static final int DOOR_LIMIT = 64;
    private static final double KEY_DROP_CHANCE = 0.35;

    private static final Map<UUID, Marker> LOADED = new LinkedHashMap<>();
    private static final Map<UUID, Info> INFO = new HashMap<>();

    /** What a marker is (worked out once): its kind and fields, the structure piece it's in, and its dungeon. */
    record Info(String kind, JsonObject def, BoundingBox piece, Dungeons.Dungeon dungeon, BlockPos pos, String instance) {
        String group() {
            return Dungeons.str(def, "group", "");
        }

        int resetTicks() {
            return dungeon == null ? Dungeons.DEFAULT_RESET_TICKS : dungeon.resetTicks();
        }
    }

    private DungeonMarkers() {
    }

    static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Marker marker && marker.entityTags().contains(TAG)) {
                LOADED.put(marker.getUUID(), marker);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Marker) {
                LOADED.remove(entity.getUUID());
                INFO.remove(entity.getUUID());
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> INFO.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOADED.clear();
            INFO.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(DungeonMarkers::tick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)
                    || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            for (Marker marker : near(serverLevel, Vec3.atCenterOf(hit.getBlockPos()), 1.5)) {
                if (marker.blockPosition().equals(hit.getBlockPos()) && info(serverLevel, marker).kind().equals("chest")) {
                    return DungeonLoot.open(serverLevel, serverPlayer, marker, info(serverLevel, marker));
                }
            }
            return useKeyDoor(serverLevel, serverPlayer, hit.getBlockPos());
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (player.isCreative() || !(level instanceof ServerLevel serverLevel) || !sealed(serverLevel, pos)) {
                return true;
            }
            player.sendOverlayMessage(Component.literal("An ancient seal holds this in place.").withStyle(ChatFormatting.DARK_PURPLE));
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel level) {
                dropKey(level, entity);
            }
        });
    }

    // ---------------------------------------------------------------- data

    static CompoundTag data(Entity marker) {
        return ((EntityAccessor) marker).skyrpg$getCustomData().copyTag();
    }

    static CompoundTag state(Entity marker) {
        return data(marker).getCompoundOrEmpty("state");
    }

    /** Replaces a marker's whole data (for markers made at runtime, like a boss's Reward Chest). */
    static void setData(Entity marker, CompoundTag data) {
        ((EntityAccessor) marker).skyrpg$setCustomData(CustomData.of(data));
    }

    static void setState(Entity marker, CompoundTag state) {
        CompoundTag tag = data(marker);
        tag.put("state", state);
        ((EntityAccessor) marker).skyrpg$setCustomData(CustomData.of(tag));
    }

    static Info info(ServerLevel level, Marker marker) {
        Info cached = INFO.get(marker.getUUID());
        if (cached != null) {
            return cached;
        }
        CompoundTag tag = data(marker);
        tag.remove("state");
        JsonElement converted = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
        JsonObject def = converted.isJsonObject() ? converted.getAsJsonObject() : new JsonObject();
        String kind = Dungeons.str(def, "skyrpg", "");
        BlockPos pos = marker.blockPosition();
        BoundingBox piece = null;
        Dungeons.Dungeon dungeon = null;
        String instance = null;
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos,
                holder -> holder.unwrapKey().map(key -> Dungeons.byStructure(key.identifier()) != null).orElse(false));
        if (start.isValid()) {
            Identifier structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
            dungeon = Dungeons.byStructure(structure);
            instance = level.dimension().identifier() + "|" + start.getChunkPos().pack();
            for (StructurePiece structurePiece : start.getPieces()) {
                if (structurePiece.getBoundingBox().isInside(pos)) {
                    piece = structurePiece.getBoundingBox();
                    break;
                }
            }
        }
        if (dungeon == null && def.has("dungeon")) {
            dungeon = Dungeons.dungeon(Identifier.parse(def.get("dungeon").getAsString()));
        }
        if (dungeon == null && def.has("boss")) {
            Dungeons.Boss boss = Dungeons.boss(Identifier.parse(def.get("boss").getAsString()));
            dungeon = boss == null ? null : Dungeons.dungeon(boss.dungeon());
        }
        if (dungeon == null) {
            dungeon = nearestEntrance(level, marker.position());
        }
        if (instance == null) {
            // A /place'd dungeon: one run per 128-block area.
            instance = level.dimension().identifier() + "|area" + (pos.getX() >> 7) + "," + (pos.getZ() >> 7);
        }
        Info info = new Info(kind, def, piece, dungeon, pos, instance);
        INFO.put(marker.getUUID(), info);
        return info;
    }

    /** The dungeon of the nearest loaded entrance marker, for dungeons that weren't generated (/place). */
    private static Dungeons.Dungeon nearestEntrance(ServerLevel level, Vec3 pos) {
        Dungeons.Dungeon best = null;
        double bestDistance = 160 * 160;
        for (Marker other : LOADED.values()) {
            if (other.level() != level) {
                continue;
            }
            CompoundTag tag = data(other);
            if (!tag.getStringOr("skyrpg", "").equals("dungeon")) {
                continue;
            }
            double distance = other.position().distanceToSqr(pos);
            Dungeons.Dungeon dungeon = Dungeons.dungeon(Identifier.tryParse(tag.getStringOr("dungeon", "")));
            if (dungeon != null && distance < bestDistance) {
                best = dungeon;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Loaded markers in this level within {@code radius} of {@code pos}. */
    static List<Marker> near(ServerLevel level, Vec3 pos, double radius) {
        List<Marker> found = new ArrayList<>();
        for (Marker marker : LOADED.values()) {
            if (!marker.isRemoved() && marker.level() == level && marker.position().distanceToSqr(pos) <= radius * radius) {
                found.add(marker);
            }
        }
        return found;
    }

    static Marker loaded(UUID id) {
        return id == null ? null : LOADED.get(id);
    }

    // ---------------------------------------------------------------- players

    /** Non-spectator players within {@code radius} (horizontally) and {@code vertical} blocks up or down. */
    static List<ServerPlayer> playersNear(ServerLevel level, Vec3 pos, double radius, double vertical) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double dx = player.getX() - pos.x;
            double dz = player.getZ() - pos.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - pos.y) <= vertical) {
                found.add(player);
            }
        }
        return found;
    }

    static boolean anyPlayerNear(ServerLevel level, Vec3 pos, double radius, double vertical) {
        return !playersNear(level, pos, radius, vertical).isEmpty();
    }

    private static boolean resettable(ServerLevel level, Marker marker, long since, Info info) {
        return level.getGameTime() - since >= info.resetTicks() && !anyPlayerNear(level, marker.position(), RESET_CLEARANCE, RESET_CLEARANCE);
    }

    // ---------------------------------------------------------------- the tick

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 0 || LOADED.isEmpty()) {
            return;
        }
        for (Marker marker : new ArrayList<>(LOADED.values())) {
            if (marker.isRemoved() || !(marker.level() instanceof ServerLevel level)) {
                LOADED.remove(marker.getUUID());
                INFO.remove(marker.getUUID());
                continue;
            }
            try {
                Info info = info(level, marker);
                switch (info.kind()) {
                    case "dungeon" -> entrance(level, marker, info);
                    case "secret" -> secret(level, marker, info);
                    case "spawner" -> spawner(level, marker, info);
                    case "boss" -> DungeonBosses.tickMarker(level, marker, info);
                    case "chest" -> chest(level, marker, info);
                    case "plate" -> plate(level, marker, info);
                    case "door" -> door(level, marker, info);
                    case "key_door" -> keyDoor(level, marker, info);
                    case "trap" -> DungeonTraps.tick(level, marker, info);
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Dungeon marker at {} failed: {}", marker.blockPosition(), e.toString());
            }
        }
    }

    private static void entrance(ServerLevel level, Marker marker, Info info) {
        Dungeons.Dungeon dungeon = info.dungeon();
        if (dungeon == null) {
            return;
        }
        for (ServerPlayer player : playersNear(level, marker.position(), DISCOVER_RADIUS, DISCOVER_RADIUS)) {
            Explorer.discover(player, dungeon, marker.blockPosition());
        }
    }

    private static void secret(ServerLevel level, Marker marker, Info info) {
        String key = level.dimension().identifier() + "|" + marker.blockPosition().asLong();
        for (ServerPlayer player : playersNear(level, marker.position(), SECRET_RADIUS, 2.5)) {
            Explorer.secret(player, key, Dungeons.str(info.def(), "name", "A hidden alcove"));
            DungeonRuns.of(info.instance()).secrets.add(marker.getUUID());
        }
    }

    // ---------------------------------------------------------------- mob packs

    /** The pack's waves: "waves" (a list of mob lists) or "mobs" (one wave). */
    private static List<JsonArray> waves(Info info) {
        List<JsonArray> waves = new ArrayList<>();
        if (info.def().has("waves") && info.def().get("waves").isJsonArray()) {
            for (JsonElement wave : info.def().getAsJsonArray("waves")) {
                if (wave.isJsonArray()) {
                    waves.add(wave.getAsJsonArray());
                }
            }
        }
        if (waves.isEmpty()) {
            waves.add(info.def().has("mobs") && info.def().get("mobs").isJsonArray() ? info.def().getAsJsonArray("mobs") : new JsonArray());
        }
        return waves;
    }

    private static void spawner(ServerLevel level, Marker marker, Info info) {
        CompoundTag state = state(marker);
        double radius = Dungeons.num(info.def(), "radius", 10);
        List<JsonArray> waves = waves(info);
        if (!state.getBooleanOr("spawned", false)) {
            if (anyPlayerNear(level, marker.position(), radius, 8)) {
                spawnWave(level, marker, info, state, waves, 0);
            }
        } else if (!state.getBooleanOr("cleared", false)) {
            if (!packDead(level, state)) {
                return;
            }
            int next = state.getIntOr("wave", 0) + 1;
            if (next < waves.size()) {
                if (anyPlayerNear(level, marker.position(), radius * 2, 12)) {
                    spawnWave(level, marker, info, state, waves, next);
                }
            } else {
                state.putBoolean("cleared", true);
                setState(marker, state);
                DungeonRuns.of(info.instance()).packs++;
            }
        } else if (resettable(level, marker, state.getLongOr("at", 0), info) && packDead(level, state)) {
            setState(marker, new CompoundTag());
        }
    }

    private static void spawnWave(ServerLevel level, Marker marker, Info info, CompoundTag state, List<JsonArray> waves, int wave) {
        Identifier key = info.dungeon() == null ? null : info.dungeon().key();
        List<Mob> spawned = MobSpawning.spawn(level, waves.get(wave), marker.position(), Dungeons.num(info.def(), "spread", 3), -1, key);
        ListTag ids = new ListTag();
        for (Mob mob : spawned) {
            ids.add(StringTag.valueOf(mob.getUUID().toString()));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
        }
        state.putBoolean("spawned", true);
        state.putInt("wave", wave);
        state.putLong("at", level.getGameTime());
        state.put("mobs", ids);
        setState(marker, state);
        level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.8f, 0.7f);
        if (waves.size() > 1) {
            for (ServerPlayer player : playersNear(level, marker.position(), 24, 12)) {
                player.sendOverlayMessage(Component.literal("⚔ Wave " + (wave + 1) + "/" + waves.size()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }
    }

    static boolean packDead(ServerLevel level, CompoundTag state) {
        ListTag ids = state.getListOrEmpty("mobs");
        for (int i = 0; i < ids.size(); i++) {
            UUID id = parseUuid(ids.getStringOr(i, ""));
            Entity entity = id == null ? null : level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                return false;
            }
        }
        return true;
    }

    static UUID parseUuid(String text) {
        try {
            return text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Elites drop their dungeon's vault key now and then. */
    private static void dropKey(ServerLevel level, Entity entity) {
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(MobSpawning.KEY_PREFIX)) {
                continue;
            }
            Item key = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(tag.substring(MobSpawning.KEY_PREFIX.length()))).orElse(null);
            if (key != null && level.getRandom().nextDouble() < KEY_DROP_CHANCE) {
                ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(key));
                drop.setGlowingTag(true);
                level.addFreshEntity(drop);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0f, 1.4f);
            }
            return;
        }
    }

    // ---------------------------------------------------------------- chests

    /** Per-player chests (see DungeonLoot): the block stays empty; everyone's copies come back after a reset. */
    private static void chest(ServerLevel level, Marker marker, Info info) {
        DungeonLoot.emptyBlock(level, marker.blockPosition());
        CompoundTag state = state(marker);
        if (!state.contains("at") || !resettable(level, marker, state.getLongOr("at", 0), info)) {
            return;
        }
        if (Dungeons.bool(info.def(), "temporary", false)) {
            // A boss's Reward Chest goes away once its run is over.
            if (level.getBlockState(marker.blockPosition()).is(Blocks.CHEST)) {
                level.setBlock(marker.blockPosition(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            marker.discard();
            return;
        }
        setState(marker, new CompoundTag());
    }

    // ---------------------------------------------------------------- puzzles and doors

    private static void plate(ServerLevel level, Marker marker, Info info) {
        CompoundTag state = state(marker);
        boolean lit = state.getBooleanOr("lit", false);
        BlockState block = level.getBlockState(marker.blockPosition());
        boolean pressed = block.hasProperty(BlockStateProperties.POWERED) && block.getValue(BlockStateProperties.POWERED)
                || block.hasProperty(BlockStateProperties.POWER) && block.getValue(BlockStateProperties.POWER) > 0;
        if (pressed && !lit) {
            state.putBoolean("lit", true);
            setState(marker, state);
            level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 1.2f);
            List<Marker> plates = siblings(level, marker, info, "plate");
            long litCount = plates.stream().filter(p -> state(p).getBooleanOr("lit", false)).count();
            for (ServerPlayer player : playersNear(level, marker.position(), 20, 10)) {
                player.sendOverlayMessage(Component.literal("◆ A plate glows (" + litCount + "/" + plates.size() + ")").withStyle(ChatFormatting.AQUA));
            }
            lit = true;
        }
        if (lit) {
            level.sendParticles(ParticleTypes.END_ROD, marker.getX(), marker.getY() + 0.3, marker.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
        }
    }

    private static void door(ServerLevel level, Marker marker, Info info) {
        CompoundTag state = state(marker);
        if (!state.getBooleanOr("open", false)) {
            if (!anyPlayerNear(level, marker.position(), 32, 24)) {
                return;
            }
            List<Marker> triggers = triggers(level, marker, info);
            if (!triggers.isEmpty() && solved(level, triggers)) {
                open(level, marker, state, Dungeons.str(info.def(), "message", "Somewhere nearby, stone grinds open..."));
            }
        } else if (resettable(level, marker, state.getLongOr("at", 0), info)) {
            close(level, marker, state);
            for (Marker trigger : triggers(level, marker, info)) {
                Info triggerInfo = info(level, trigger);
                if (triggerInfo.kind().equals("plate")) {
                    setState(trigger, new CompoundTag());
                } else if (triggerInfo.kind().equals("lever")) {
                    scramble(level, trigger, triggerInfo);
                }
            }
        }
    }

    private static void keyDoor(ServerLevel level, Marker marker, Info info) {
        CompoundTag state = state(marker);
        if (state.getBooleanOr("open", false) && resettable(level, marker, state.getLongOr("at", 0), info)) {
            close(level, marker, state);
        }
    }

    /** The other markers of this kind in the same group (and the same piece) as {@code marker}, itself included. */
    private static List<Marker> siblings(ServerLevel level, Marker marker, Info info, String kind) {
        List<Marker> found = new ArrayList<>();
        for (Marker other : near(level, marker.position(), 64)) {
            Info otherInfo = info(level, other);
            if (otherInfo.kind().equals(kind) && sameGroup(marker, info, other, otherInfo)) {
                found.add(other);
            }
        }
        return found;
    }

    /** What opens this door: the levers, plates, mob packs and bosses of its group. */
    private static List<Marker> triggers(ServerLevel level, Marker door, Info info) {
        List<Marker> found = new ArrayList<>();
        if (info.group().isEmpty()) {
            return found;
        }
        for (Marker other : near(level, door.position(), 64)) {
            Info otherInfo = info(level, other);
            switch (otherInfo.kind()) {
                case "lever", "plate", "spawner", "boss" -> {
                    if (sameGroup(door, info, other, otherInfo)) {
                        found.add(other);
                    }
                }
                default -> {
                }
            }
        }
        return found;
    }

    private static boolean sameGroup(Marker a, Info aInfo, Marker b, Info bInfo) {
        if (aInfo.group().isEmpty() || !aInfo.group().equals(bInfo.group())) {
            return false;
        }
        if (aInfo.piece() != null && bInfo.piece() != null) {
            return aInfo.piece().equals(bInfo.piece());
        }
        return a.position().distanceTo(b.position()) <= GROUP_RANGE;
    }

    private static boolean solved(ServerLevel level, List<Marker> triggers) {
        for (Marker trigger : triggers) {
            Info info = info(level, trigger);
            CompoundTag state = state(trigger);
            boolean done = switch (info.kind()) {
                case "lever" -> {
                    BlockState block = level.getBlockState(trigger.blockPosition());
                    yield !block.hasProperty(BlockStateProperties.POWERED)
                            || block.getValue(BlockStateProperties.POWERED) == Dungeons.bool(info.def(), "want", true);
                }
                case "plate" -> state.getBooleanOr("lit", false);
                case "spawner" -> state.getBooleanOr("cleared", false);
                case "boss" -> state.getBooleanOr("killed", false);
                default -> true;
            };
            if (!done) {
                return false;
            }
        }
        return true;
    }

    /** Flips a lever away from its wanted position, so the puzzle has to be solved again. */
    private static void scramble(ServerLevel level, Marker lever, Info info) {
        BlockPos pos = lever.blockPosition();
        BlockState block = level.getBlockState(pos);
        if (block.hasProperty(BlockStateProperties.POWERED) && level.getRandom().nextBoolean() == true) {
            level.setBlock(pos, block.setValue(BlockStateProperties.POWERED, !Dungeons.bool(info.def(), "want", true)), Block.UPDATE_ALL);
        }
    }

    /** The door's blocks: every block of the same type connected to the marker's block (up to 64). */
    static List<BlockPos> doorBlocks(Level level, BlockPos start) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockState first = level.getBlockState(start);
        if (first.isAir() || !first.getFluidState().isEmpty() && first.is(Blocks.WATER)) {
            return blocks;
        }
        Block type = first.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && blocks.size() < DOOR_LIMIT) {
            BlockPos pos = queue.poll();
            blocks.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && level.getBlockState(next).is(type)) {
                    queue.add(next);
                }
            }
        }
        return blocks;
    }

    /** Opens a door: its blocks vanish (to water if the room is flooded) and are remembered for the reset. */
    static void open(ServerLevel level, Marker marker, CompoundTag state, String message) {
        List<BlockPos> blocks = doorBlocks(level, marker.blockPosition());
        ListTag saved = new ListTag();
        for (BlockPos pos : blocks) {
            BlockState old = level.getBlockState(pos);
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.put("block", NbtUtils.writeBlockState(old));
            saved.add(entry);
        }
        for (BlockPos pos : blocks) {
            level.setBlock(pos, flooded(level, pos) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.6);
        }
        state.putBoolean("open", true);
        state.putLong("at", level.getGameTime());
        state.put("blocks", saved);
        setState(marker, state);
        level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.6f);
        level.playSound(null, marker.getX(), marker.getY(), marker.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.2f, 0.5f);
        for (ServerPlayer player : playersNear(level, marker.position(), 40, 20)) {
            player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static boolean flooded(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && level.getFluidState(pos.relative(direction)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    static void close(ServerLevel level, Marker marker, CompoundTag state) {
        ListTag saved = state.getListOrEmpty("blocks");
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompoundOrEmpty(i);
            BlockPos pos = BlockPos.of(entry.getLongOr("pos", 0));
            BlockState current = level.getBlockState(pos);
            if (current.isAir() || current.is(Blocks.WATER)) {
                level.setBlock(pos, NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.getCompoundOrEmpty("block")), Block.UPDATE_ALL);
            }
        }
        setState(marker, new CompoundTag());
    }

    /** Right-clicking a vault door: with its key it opens, without one it says what it needs. */
    private static InteractionResult useKeyDoor(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        for (Marker marker : near(level, Vec3.atCenterOf(clicked), 12)) {
            Info info = info(level, marker);
            if (!info.kind().equals("key_door") || state(marker).getBooleanOr("open", false)
                    || !doorBlocks(level, marker.blockPosition()).contains(clicked)) {
                continue;
            }
            Item key = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(Dungeons.str(info.def(), "key", ""))).orElse(null);
            ItemStack held = player.getMainHandItem();
            if (key != null && held.is(key)) {
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                open(level, marker, state(marker), Dungeons.str(info.def(), "message", "The vault's seal breaks. Its treasure is yours."));
                level.playSound(null, clicked, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5f, 0.8f);
                return InteractionResult.SUCCESS;
            }
            String keyName = key == null ? "a key" : new ItemStack(key).getHoverName().getString();
            player.sendOverlayMessage(Component.literal("Locked. It needs a " + keyName + ".").withStyle(ChatFormatting.GOLD));
            level.playSound(null, clicked, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    /** Closed doors (and the blocks right around them), levers and plates can't be broken. */
    private static boolean sealed(ServerLevel level, BlockPos pos) {
        for (Marker marker : near(level, Vec3.atCenterOf(pos), 12)) {
            Info info = info(level, marker);
            switch (info.kind()) {
                case "lever", "plate", "chest" -> {
                    if (marker.blockPosition().equals(pos)) {
                        return true;
                    }
                }
                case "door", "key_door" -> {
                    if (!state(marker).getBooleanOr("open", false)) {
                        for (BlockPos block : doorBlocks(level, marker.blockPosition())) {
                            if (block.distManhattan(pos) <= 1) {
                                return true;
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- commands

    /** Resets every dungeon part within {@code radius}: packs and bosses come back, doors close, chests refill. */
    static int reset(ServerLevel level, Vec3 pos, double radius) {
        int count = 0;
        for (Marker marker : near(level, pos, radius)) {
            Info info = info(level, marker);
            CompoundTag state = state(marker);
            switch (info.kind()) {
                case "spawner" -> {
                    ListTag ids = state.getListOrEmpty("mobs");
                    for (int i = 0; i < ids.size(); i++) {
                        UUID id = parseUuid(ids.getStringOr(i, ""));
                        Entity mob = id == null ? null : level.getEntity(id);
                        if (mob != null) {
                            mob.discard();
                        }
                    }
                    setState(marker, new CompoundTag());
                }
                case "boss" -> DungeonBosses.resetMarker(level, marker);
                case "door", "key_door" -> {
                    if (state.getBooleanOr("open", false)) {
                        close(level, marker, state);
                    }
                }
                case "plate" -> setState(marker, new CompoundTag());
                case "lever" -> scramble(level, marker, info);
                case "chest" -> setState(marker, new CompoundTag());
                default -> {
                }
            }
            count++;
        }
        return count;
    }

    /** One line per marker within {@code radius}, for /dungeon info. */
    static List<String> describe(ServerLevel level, Vec3 pos, double radius) {
        List<String> lines = new ArrayList<>();
        for (Marker marker : near(level, pos, radius)) {
            Info info = info(level, marker);
            BlockPos at = marker.blockPosition();
            String group = info.group().isEmpty() ? "" : " [" + info.group() + "]";
            lines.add(info.kind() + group + " @ " + at.getX() + " " + at.getY() + " " + at.getZ()
                    + (info.dungeon() == null ? "" : " · " + info.dungeon().id().getPath()) + (info.piece() == null ? " · no piece" : "")
                    + " · " + state(marker));
        }
        return lines;
    }
}
