package com.skyrpg.builder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One Builder Station: its blueprint and where it goes, what has been delivered, and how far the build is. Saved
 * with the overworld ({@link Stations}); the blueprint itself lives in the {@link Library}.
 */
final class Station {
    static final int EMPTY = 0;
    static final int PLANNING = 1;
    static final int BUILDING = 2;
    static final int DONE = 3;

    /** How far a blueprint may sit from its station, per axis. */
    static final int MAX_OFFSET = 64;

    static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(s -> s.dimension),
            Codec.LONG.fieldOf("pos").forGetter(s -> s.pos.asLong()),
            Codec.INT.optionalFieldOf("facing", 2).forGetter(s -> s.facing.get3DDataValue()),
            Codec.STRING.optionalFieldOf("owner", "").forGetter(s -> s.owner),
            Codec.STRING.optionalFieldOf("blueprint", "").forGetter(s -> s.blueprintId),
            Codec.INT.listOf().optionalFieldOf("offset", java.util.List.of(0, 0, 2)).forGetter(s -> java.util.List.of(s.offX, s.offY, s.offZ)),
            Codec.INT.optionalFieldOf("turns", 0).forGetter(s -> s.turns),
            Codec.BOOL.optionalFieldOf("mirrored", false).forGetter(s -> s.mirrored),
            Codec.BOOL.optionalFieldOf("preview", true).forGetter(s -> s.preview),
            Codec.BOOL.optionalFieldOf("clear", true).forGetter(s -> s.clear),
            Codec.INT.optionalFieldOf("state", EMPTY).forGetter(s -> s.state),
            Codec.INT.listOf().optionalFieldOf("progress", java.util.List.of(0, 0, 0, 0)).forGetter(s -> java.util.List.of(s.layer, s.cursor, s.processed, s.blocked)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stored", Map.of()).forGetter(s -> s.stored),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("leftovers", Map.of()).forGetter(s -> s.leftovers),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("helpers", Map.of()).forGetter(s -> s.helpers),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("replacements", Map.of()).forGetter(s -> s.replacements)
    ).apply(i, Station::new));

    final String dimension;
    final BlockPos pos;
    Direction facing;
    String owner;
    String blueprintId;
    int offX;
    int offY;
    int offZ;
    int turns;
    boolean mirrored;
    boolean preview;
    boolean clear;
    int state;
    /** Building: the layer being built, the next step in it, place steps done so far, and blocks left out because something was in the way. */
    int layer;
    int cursor;
    int processed;
    int blocked;
    /** Delivered materials (item id to count). */
    final Map<String, Integer> stored = new LinkedHashMap<>();
    /** What clearing the site and placing water gave back, waiting to be collected. */
    final Map<String, Integer> leftovers = new LinkedHashMap<>();
    /** Who delivered how many items for the current blueprint (player name to count). */
    final Map<String, Integer> helpers = new LinkedHashMap<>();
    /**
     * Stand-ins for blocks that are hard to get (blueprint block id to the block built instead; {@code minecraft:air}
     * skips it). Reset with a new blueprint.
     */
    final Map<String, String> replacements = new LinkedHashMap<>();

    // Not saved.
    /** Bumped whenever what the hologram shows changes, so clients get the new placement. */
    int version;
    /** The current layer's steps while building (rebuilt after a restart). */
    BuildSteps steps;
    /** Players with the menu open. */
    final Set<UUID> viewers = new java.util.HashSet<>();
    boolean viewDirty = true;
    /** Materials still needed, by item id, from the last scan of the site (null when stale). */
    Scan scan;
    long scannedAt;
    String pauseReason = "";
    /** Blocks the running build places in all (its buildable blocks); 0 until known. */
    int buildTotal;
    /** Build pacing: blocks per tick, the work it may do now, and ticks left resting after a layer. */
    double rate;
    double credit;
    int layerPause;
    /** What the last action did ("Delivered 64 items."), shown until {@link #resultUntil} (wall-clock millis). */
    String lastResult = "";
    long resultUntil;

    Station(String dimension, BlockPos pos, Direction facing, String owner) {
        this.dimension = dimension;
        this.pos = pos;
        this.facing = facing;
        this.owner = owner;
        this.blueprintId = "";
        this.offZ = 2;
        this.preview = true;
        this.clear = true;
        this.state = EMPTY;
    }

    private Station(String dimension, long pos, int facing, String owner, String blueprintId, java.util.List<Integer> offset, int turns,
                    boolean mirrored, boolean preview, boolean clear, int state, java.util.List<Integer> progress, Map<String, Integer> stored,
                    Map<String, Integer> leftovers, Map<String, Integer> helpers, Map<String, String> replacements) {
        this(dimension, BlockPos.of(pos), Direction.from3DDataValue(facing), owner);
        this.blueprintId = blueprintId;
        this.offX = offset.size() > 0 ? offset.get(0) : 0;
        this.offY = offset.size() > 1 ? offset.get(1) : 0;
        this.offZ = offset.size() > 2 ? offset.get(2) : 2;
        this.turns = turns;
        this.mirrored = mirrored;
        this.preview = preview;
        this.clear = clear;
        this.state = state;
        this.layer = progress.size() > 0 ? progress.get(0) : 0;
        this.cursor = progress.size() > 1 ? progress.get(1) : 0;
        this.processed = progress.size() > 2 ? progress.get(2) : 0;
        this.blocked = progress.size() > 3 ? progress.get(3) : 0;
        this.stored.putAll(stored);
        this.leftovers.putAll(leftovers);
        this.helpers.putAll(helpers);
        this.replacements.putAll(replacements);
    }

    /**
     * The block to build for a blueprint block: itself, or its stand-in, keeping every property the two share (facing,
     * half, shape, waterlogged...). Done before the placement turns it, so the stand-in turns the same way.
     */
    net.minecraft.world.level.block.state.BlockState replace(net.minecraft.world.level.block.state.BlockState state) {
        if (replacements.isEmpty()) {
            return state;
        }
        String to = replacements.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (to == null) {
            return state;
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(to);
        net.minecraft.world.level.block.Block block = id == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return state;
        }
        net.minecraft.world.level.block.state.BlockState out = block.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (out.hasProperty(property)) {
                out = copy(out, state, property);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState copy(
            net.minecraft.world.level.block.state.BlockState to, net.minecraft.world.level.block.state.BlockState from,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    String key() {
        return key(dimension, pos);
    }

    static String key(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    Placement placement(Blueprint blueprint) {
        return Placement.of(blueprint, pos.offset(offX, offY, offZ), turns, mirrored);
    }

    int storedCount(String item) {
        return stored.getOrDefault(item, 0);
    }

    void store(String item, int count) {
        if (count > 0) {
            stored.merge(item, count, Integer::sum);
        }
    }

    /** Takes up to {@code count}; returns how many were there. */
    int take(String item, int count) {
        int have = storedCount(item);
        int taken = Math.min(have, count);
        if (have - taken <= 0) {
            stored.remove(item);
        } else {
            stored.put(item, have - taken);
        }
        return taken;
    }

    void giveBack(String item, int count) {
        if (count > 0) {
            leftovers.merge(item, count, Integer::sum);
        }
    }

    /** The result of comparing the blueprint with what's in the world. */
    record Scan(Map<String, Integer> needs, int done, int total, int skipped, int inTheWay) {
        static Scan empty() {
            return new Scan(new HashMap<>(), 0, 0, 0, 0);
        }
    }

    /** The steps of one layer: clears first, then the blocks (solid ones before the ones that hang on them). */
    record BuildSteps(int layer, long[] positions, int[] entries) {
        /** {@code entries[i] == CLEAR} means "make this air". */
        static final int CLEAR = -1;

        int size() {
            return positions.length;
        }
    }
}
