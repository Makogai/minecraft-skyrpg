package com.skyrpg.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A build plan: a box of {@code sizeX × sizeY × sizeZ} and the blocks in it (air is left out). Blocks are kept as two
 * parallel arrays sorted by their local index {@code x + z·sizeX + y·sizeX·sizeZ}, so they come layer by layer from
 * the bottom up ({@link #layerStart}). Only the block entity data that is safe to copy is kept (sign text, banner
 * patterns, skull owners, see {@link Schematics#keptBlockEntity}): never inventories.
 *
 * <p>Immutable once made; the client gets the same object (without block entities) to draw the hologram.
 */
public final class Blueprint {
    public static final int MAX_SIZE = 128;
    public static final int MAX_BLOCKS = 120_000;

    public static final int KIND_VANILLA = 0;
    public static final int KIND_UPLOAD = 1;

    public final String id;
    public final String name;
    public final String author;
    public final int kind;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final List<BlockState> palette;
    /** Local index of every block, ascending. */
    public final int[] positions;
    /** Index into {@link #palette} for every block. */
    public final int[] states;
    /** Kept block entity data, by entry (not local index). */
    public final Map<Integer, CompoundTag> blockEntities;
    /** {@code layerStart[y]} is the first entry at height y; {@code layerStart[sizeY]} is the entry count. */
    public final int[] layerStart;

    public Blueprint(String id, String name, String author, int kind, int sizeX, int sizeY, int sizeZ, List<BlockState> palette,
                     int[] positions, int[] states, Map<Integer, CompoundTag> blockEntities) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.kind = kind;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.positions = positions;
        this.states = states;
        this.blockEntities = Map.copyOf(blockEntities);
        this.layerStart = new int[sizeY + 1];
        int layer = sizeX * sizeZ;
        int entry = 0;
        for (int y = 0; y <= sizeY; y++) {
            while (entry < positions.length && positions[entry] / layer < y) {
                entry++;
            }
            layerStart[y] = entry;
        }
        layerStart[sizeY] = positions.length;
    }

    public int count() {
        return positions.length;
    }

    public int localX(int index) {
        return index % sizeX;
    }

    public int localZ(int index) {
        return (index / sizeX) % sizeZ;
    }

    public int localY(int index) {
        return index / (sizeX * sizeZ);
    }

    public BlockState state(int entry) {
        return palette.get(states[entry]);
    }

    /** The entry at this local index, or -1. */
    public int entryAt(int localIndex) {
        int found = Arrays.binarySearch(positions, localIndex);
        return found >= 0 ? found : -1;
    }

    /** "12 × 8 × 15". */
    public String sizeText() {
        return sizeX + " × " + sizeY + " × " + sizeZ;
    }

    public Blueprint withoutBlockEntities() {
        return blockEntities.isEmpty() ? this : new Blueprint(id, name, author, kind, sizeX, sizeY, sizeZ, palette, positions, states, Map.of());
    }

    // ------------------------------------------------------------------ files

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", 1);
        tag.putString("Name", name);
        tag.putString("Author", author);
        tag.putInt("Kind", kind);
        tag.putIntArray("Size", new int[]{sizeX, sizeY, sizeZ});
        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", paletteTag);
        tag.putIntArray("Positions", positions);
        tag.putIntArray("States", states);
        ListTag entities = new ListTag();
        blockEntities.forEach((entry, data) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("Entry", entry);
            one.put("Data", data.copy());
            entities.add(one);
        });
        tag.put("BlockEntities", entities);
        return tag;
    }

    static Blueprint load(String id, CompoundTag tag) {
        int[] size = tag.getIntArray("Size").orElse(new int[]{1, 1, 1});
        List<BlockState> palette = new ArrayList<>();
        for (net.minecraft.nbt.Tag one : tag.getListOrEmpty("Palette")) {
            palette.add(one instanceof CompoundTag compound ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound)
                    : Blocks.AIR.defaultBlockState());
        }
        int[] positions = tag.getIntArray("Positions").orElse(new int[0]);
        int[] states = tag.getIntArray("States").orElse(new int[0]);
        Map<Integer, CompoundTag> entities = new HashMap<>();
        ListTag list = tag.getListOrEmpty("BlockEntities");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompoundOrEmpty(i);
            entities.put(one.getIntOr("Entry", -1), one.getCompoundOrEmpty("Data"));
        }
        return new Blueprint(id, tag.getStringOr("Name", id), tag.getStringOr("Author", ""), tag.getIntOr("Kind", KIND_UPLOAD),
                size[0], size[1], size[2], palette, positions, states, entities);
    }

    /** Collects blocks in any order (local coordinates), then makes the sorted blueprint. */
    static final class Builder {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
        private final Map<Integer, BlockState> blocks = new HashMap<>();
        private final Map<Integer, CompoundTag> entities = new HashMap<>();

        Builder(int sizeX, int sizeY, int sizeZ) throws Schematics.BadFile {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
                throw new Schematics.BadFile("The schematic is empty.");
            }
            if (sizeX > MAX_SIZE || sizeY > MAX_SIZE || sizeZ > MAX_SIZE) {
                throw new Schematics.BadFile("Too big: " + sizeX + " × " + sizeY + " × " + sizeZ + " (the most is " + MAX_SIZE + " on each side).");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        void put(int x, int y, int z, BlockState state, CompoundTag blockEntity) throws Schematics.BadFile {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return;
            }
            int index = x + z * sizeX + y * sizeX * sizeZ;
            if (state.isAir()) {
                blocks.remove(index);
                entities.remove(index);
                return;
            }
            blocks.put(index, state);
            if (blocks.size() > MAX_BLOCKS) {
                throw new Schematics.BadFile("Too many blocks (the most is " + String.format("%,d", MAX_BLOCKS) + ").");
            }
            if (blockEntity != null) {
                entities.put(index, blockEntity);
            } else {
                entities.remove(index);
            }
        }

        Blueprint build(String id, String name, String author, int kind) throws Schematics.BadFile {
            if (blocks.isEmpty()) {
                throw new Schematics.BadFile("The schematic has no blocks.");
            }
            int[] positions = blocks.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            int[] states = new int[positions.length];
            Map<Integer, CompoundTag> byEntry = new HashMap<>();
            for (int i = 0; i < positions.length; i++) {
                BlockState state = blocks.get(positions[i]);
                states[i] = paletteIndex.computeIfAbsent(state, s -> {
                    palette.add(s);
                    return palette.size() - 1;
                });
                CompoundTag entity = entities.get(positions[i]);
                if (entity != null) {
                    byEntry.put(i, entity);
                }
            }
            return new Blueprint(id, name, author, kind, sizeX, sizeY, sizeZ, palette, positions, states, byEntry);
        }
    }
}
