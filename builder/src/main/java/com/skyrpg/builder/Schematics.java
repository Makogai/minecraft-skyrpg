package com.skyrpg.builder;

import com.mojang.serialization.Dynamic;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads schematic files into {@link Blueprint}s: Litematica ({@code .litematic}, every region merged), Sponge / WorldEdit
 * ({@code .schem}, versions 1 to 3) and vanilla structure files ({@code .nbt}, also how the village houses are read).
 * Files from older Minecraft versions are run through the game's data fixers first. Blocks the game doesn't know are
 * left out and counted in {@link Result#unknown}. MCEdit's old numeric {@code .schematic} format isn't supported.
 */
public final class Schematics {
    /** What a gzip bomb may grow to before reading stops. */
    private static final long NBT_QUOTA = 128L << 20;
    private static final int CURRENT = SharedConstants.getCurrentVersion().dataVersion().version();

    private Schematics() {
    }

    /** A file that can't be turned into a blueprint; the message is shown to the player as is. */
    public static final class BadFile extends Exception {
        public BadFile(String message) {
            super(message);
        }
    }

    public record Result(Blueprint blueprint, int unknown) {
    }

    public static Result read(byte[] bytes, String fileName, String id, String author, int kind) throws BadFile {
        CompoundTag root = readNbt(bytes);
        String lower = fileName.toLowerCase(Locale.ROOT);
        Reader reader = new Reader();
        Blueprint.Builder builder;
        String name = baseName(fileName);
        if (root.contains("Regions") || lower.endsWith(".litematic")) {
            builder = reader.litematic(root);
            name = root.getCompoundOrEmpty("Metadata").getStringOr("Name", name);
        } else if (root.contains("Schematic") || root.contains("BlockData") || lower.endsWith(".schem")) {
            CompoundTag schematic = root.getCompound("Schematic").orElse(root);
            builder = reader.sponge(schematic);
            name = schematic.getCompoundOrEmpty("Metadata").getStringOr("Name", name);
        } else if (root.contains("Blocks") && root.contains("Materials")) {
            throw new BadFile("Old MCEdit .schematic files aren't supported. Save it as .litematic or .schem first.");
        } else if (root.contains("blocks") && (root.contains("palette") || root.contains("palettes"))) {
            builder = reader.structure(root, true);
        } else {
            throw new BadFile("That doesn't look like a schematic (.litematic, .schem or .nbt).");
        }
        try {
            return new Result(builder.build(id, cleanName(name), author, kind), reader.unknown);
        } catch (BadFile e) {
            if (!reader.unknownNames.isEmpty()) {
                throw new BadFile(e.getMessage() + " Unknown blocks: " + String.join(", ", reader.unknownNames.stream().limit(3).toList()) + ".");
            }
            SkyRpgBuilder.LOGGER.info("Schematic {} gave no blocks; root keys {}", fileName, root.keySet());
            throw e;
        }
    }

    /** A vanilla structure (already current, as saved by the game's structure manager). */
    static Blueprint structure(CompoundTag saved, String id, String name) throws BadFile {
        return new Reader().structure(saved, false).build(id, name, "Minecraft", Blueprint.KIND_VANILLA);
    }

    private static CompoundTag readNbt(byte[] bytes) throws BadFile {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(NBT_QUOTA));
        } catch (Exception compressed) {
            try {
                return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.create(NBT_QUOTA));
            } catch (Exception plain) {
                throw new BadFile("Couldn't read the file (is it a schematic?).");
            }
        }
    }

    public static String baseName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String cleanName(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}§]", "").replace('_', ' ').trim();
        if (clean.isEmpty()) {
            clean = "Blueprint";
        }
        return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
    }

    /**
     * The block entity data worth copying for this block: sign text, banner patterns, skull owners. Inventories, loot
     * tables, spawner data and everything else are dropped (a blueprint must never conjure items). Null when nothing is kept.
     */
    static CompoundTag keptBlockEntity(BlockState state, CompoundTag raw) {
        if (raw == null) {
            return null;
        }
        String[] keys;
        if (state.getBlock() instanceof SignBlock) {
            keys = new String[]{"front_text", "back_text", "is_waxed"};
        } else if (state.getBlock() instanceof AbstractBannerBlock) {
            keys = new String[]{"patterns", "CustomName"};
        } else if (state.getBlock() instanceof AbstractSkullBlock) {
            keys = new String[]{"profile", "note_block_sound", "custom_name"};
        } else {
            return null;
        }
        CompoundTag kept = new CompoundTag();
        for (String key : keys) {
            Tag value = raw.get(key);
            if (value != null) {
                kept.put(key, value.copy());
            }
        }
        return kept.isEmpty() ? null : kept;
    }

    /** One file being read: counts the blocks it didn't know. */
    private static final class Reader {
        int unknown;
        final java.util.Set<String> unknownNames = new java.util.LinkedHashSet<>();

        /** Block entity data from an older version, updated (it needs its "id"). */
        static CompoundTag entity(CompoundTag raw, String id, int dataVersion) {
            if (raw == null || dataVersion <= 0 || dataVersion >= CURRENT) {
                return raw;
            }
            CompoundTag tag = raw.copy();
            if (!tag.contains("id") && id != null) {
                tag.putString("id", id);
            }
            try {
                Tag updated = DataFixers.getDataFixer().update(References.BLOCK_ENTITY, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag), dataVersion, CURRENT)
                        .getValue();
                return updated instanceof CompoundTag compound ? compound : raw;
            } catch (RuntimeException e) {
                return raw;
            }
        }

        /** A block state from {@code {Name, Properties}}, updated from {@code dataVersion} if older. */
        BlockState state(CompoundTag tag, int dataVersion) {
            CompoundTag fixed = tag;
            if (dataVersion > 0 && dataVersion < CURRENT) {
                try {
                    Tag updated = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag),
                            dataVersion, CURRENT).getValue();
                    if (updated instanceof CompoundTag compound) {
                        fixed = compound;
                    }
                } catch (RuntimeException ignored) {
                    // keep the original
                }
            }
            // 26.x writes {id, properties}; older versions (and most schematic tools) {Name, Properties}.
            CompoundTag source = fixed;
            String text = source.getString("id").orElseGet(() -> source.getStringOr("Name", "minecraft:air"));
            Identifier name = Identifier.tryParse(text);
            if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) {
                unknown++;
                unknownNames.add(text);
                return Blocks.AIR.defaultBlockState();
            }
            CompoundTag current = new CompoundTag();
            current.putString("id", name.toString());
            CompoundTag properties = source.getCompound("properties").orElseGet(() -> source.getCompoundOrEmpty("Properties"));
            if (!properties.isEmpty()) {
                current.put("properties", properties);
            }
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, current);
        }

        /** A block state from Sponge's string form, {@code minecraft:oak_stairs[facing=east,half=bottom]}. */
        BlockState state(String text, int dataVersion) {
            CompoundTag tag = new CompoundTag();
            int open = text.indexOf('[');
            tag.putString("Name", open < 0 ? text : text.substring(0, open));
            if (open >= 0 && text.endsWith("]")) {
                CompoundTag properties = new CompoundTag();
                for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        properties.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                }
                tag.put("Properties", properties);
            }
            return state(tag, dataVersion);
        }

        Blueprint.Builder litematic(CompoundTag root) throws BadFile {
            int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
            CompoundTag regions = root.getCompoundOrEmpty("Regions");
            if (regions.isEmpty()) {
                throw new BadFile("The litematic has no regions.");
            }
            // The box around every region.
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (String key : regions.keySet()) {
                int[] box = regionBox(regions.getCompoundOrEmpty(key));
                minX = Math.min(minX, box[0]);
                minY = Math.min(minY, box[1]);
                minZ = Math.min(minZ, box[2]);
                maxX = Math.max(maxX, box[0] + box[3] - 1);
                maxY = Math.max(maxY, box[1] + box[4] - 1);
                maxZ = Math.max(maxZ, box[2] + box[5] - 1);
            }
            Blueprint.Builder builder = new Blueprint.Builder(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
            for (String key : regions.keySet()) {
                CompoundTag region = regions.getCompoundOrEmpty(key);
                int[] box = regionBox(region);
                int sx = box[3], sy = box[4], sz = box[5];
                ListTag paletteTag = region.getListOrEmpty("BlockStatePalette");
                BlockState[] palette = new BlockState[Math.max(1, paletteTag.size())];
                palette[0] = Blocks.AIR.defaultBlockState();
                for (int i = 0; i < paletteTag.size(); i++) {
                    palette[i] = state(paletteTag.getCompoundOrEmpty(i), dataVersion);
                }
                Map<Long, CompoundTag> entities = new HashMap<>();
                ListTag entityList = region.getListOrEmpty("TileEntities");
                for (int i = 0; i < entityList.size(); i++) {
                    CompoundTag entity = entityList.getCompoundOrEmpty(i);
                    entities.put(key(entity.getIntOr("x", 0), entity.getIntOr("y", 0), entity.getIntOr("z", 0)), entity(entity, null, dataVersion));
                }
                long[] data = region.getLongArray("BlockStates").orElse(new long[0]);
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
                long volume = (long) sx * sy * sz;
                if ((volume * bits + 63) / 64 > data.length) {
                    throw new BadFile("The litematic's block data is cut short.");
                }
                long mask = (1L << bits) - 1;
                for (long index = 0; index < volume; index++) {
                    long start = index * bits;
                    int startLong = (int) (start >> 6);
                    int endLong = (int) ((start + bits - 1) >> 6);
                    int startBit = (int) (start & 63);
                    long value = startLong == endLong ? (data[startLong] >>> startBit) & mask
                            : ((data[startLong] >>> startBit) | (data[endLong] << (64 - startBit))) & mask;
                    if (value >= palette.length || palette[(int) value].isAir()) {
                        continue;
                    }
                    BlockState state = palette[(int) value];
                    int x = (int) (index % sx);
                    int z = (int) ((index / sx) % sz);
                    int y = (int) (index / ((long) sx * sz));
                    builder.put(box[0] + x - minX, box[1] + y - minY, box[2] + z - minZ, state,
                            keptBlockEntity(state, entities.get(key(x, y, z))));
                }
            }
            return builder;
        }

        /** A litematic region's min corner and size: {x, y, z, sizeX, sizeY, sizeZ}. Negative sizes grow back from the position. */
        private int[] regionBox(CompoundTag region) {
            CompoundTag pos = region.getCompoundOrEmpty("Position");
            CompoundTag size = region.getCompoundOrEmpty("Size");
            int[] box = new int[6];
            String[] axes = {"x", "y", "z"};
            for (int a = 0; a < 3; a++) {
                int p = pos.getIntOr(axes[a], 0);
                int s = size.getIntOr(axes[a], 1);
                box[a] = s < 0 ? p + s + 1 : p;
                box[a + 3] = Math.max(1, Math.abs(s));
            }
            return box;
        }

        Blueprint.Builder sponge(CompoundTag schematic) throws BadFile {
            int version = schematic.getIntOr("Version", 1);
            int dataVersion = schematic.getIntOr("DataVersion", 0);
            int width = schematic.getShortOr("Width", (short) 0) & 0xFFFF;
            int height = schematic.getShortOr("Height", (short) 0) & 0xFFFF;
            int length = schematic.getShortOr("Length", (short) 0) & 0xFFFF;
            CompoundTag paletteTag;
            byte[] data;
            ListTag entityList;
            if (version >= 3) {
                CompoundTag blocks = schematic.getCompoundOrEmpty("Blocks");
                paletteTag = blocks.getCompoundOrEmpty("Palette");
                data = blocks.getByteArray("Data").orElse(new byte[0]);
                entityList = blocks.getListOrEmpty("BlockEntities");
            } else {
                paletteTag = schematic.getCompoundOrEmpty("Palette");
                data = schematic.getByteArray("BlockData").orElse(new byte[0]);
                entityList = schematic.contains("BlockEntities") ? schematic.getListOrEmpty("BlockEntities") : schematic.getListOrEmpty("TileEntities");
            }
            Map<Integer, BlockState> palette = new HashMap<>();
            for (String key : paletteTag.keySet()) {
                palette.put(paletteTag.getIntOr(key, -1), state(key, dataVersion));
            }
            Map<Long, CompoundTag> entities = new HashMap<>();
            for (int i = 0; i < entityList.size(); i++) {
                CompoundTag entity = entityList.getCompoundOrEmpty(i);
                int[] pos = entity.getIntArray("Pos").orElse(new int[]{-1, -1, -1});
                if (pos.length == 3) {
                    CompoundTag fields = version >= 3 ? entity.getCompoundOrEmpty("Data") : entity;
                    entities.put(key(pos[0], pos[1], pos[2]), entity(fields, entity.getStringOr("Id", null), dataVersion));
                }
            }
            Blueprint.Builder builder = new Blueprint.Builder(width, height, length);
            int index = 0;
            int at = 0;
            long volume = (long) width * height * length;
            while (at < data.length && index < volume) {
                int value = 0;
                int shift = 0;
                byte b;
                do {
                    if (at >= data.length || shift > 28) {
                        throw new BadFile("The schematic's block data is damaged.");
                    }
                    b = data[at++];
                    value |= (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0);
                BlockState state = palette.get(value);
                if (state != null && !state.isAir()) {
                    int x = index % width;
                    int z = (index / width) % length;
                    int y = index / (width * length);
                    builder.put(x, y, z, state, keptBlockEntity(state, entities.get(key(x, y, z))));
                }
                index++;
            }
            return builder;
        }

        Blueprint.Builder structure(CompoundTag root, boolean fix) throws BadFile {
            CompoundTag tag = root;
            int dataVersion = root.getIntOr("DataVersion", 0);
            if (fix && dataVersion > 0 && dataVersion < CURRENT) {
                try {
                    tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), root, dataVersion);
                } catch (RuntimeException ignored) {
                    tag = root;
                }
            }
            ListTag size = tag.getListOrEmpty("size");
            Blueprint.Builder builder = new Blueprint.Builder(size.getIntOr(0, 0), size.getIntOr(1, 0), size.getIntOr(2, 0));
            ListTag paletteTag = tag.contains("palettes") ? tag.getListOrEmpty("palettes").getListOrEmpty(0) : tag.getListOrEmpty("palette");
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < paletteTag.size(); i++) {
                palette[i] = state(paletteTag.getCompoundOrEmpty(i), 0);
            }
            ListTag blocks = tag.getListOrEmpty("blocks");
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                int stateIndex = block.getIntOr("state", -1);
                if (stateIndex < 0 || stateIndex >= palette.length) {
                    continue;
                }
                BlockState state = palette[stateIndex];
                CompoundTag nbt = block.getCompound("nbt").orElse(null);
                if (state.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                if (state.is(Blocks.JIGSAW)) {
                    // Jigsaws become what they turn into when the structure generates (usually air).
                    state = nbt == null ? Blocks.AIR.defaultBlockState() : state(nbt.getStringOr("final_state", "minecraft:air"), 0);
                    nbt = null;
                }
                ListTag pos = block.getListOrEmpty("pos");
                builder.put(pos.getIntOr(0, -1), pos.getIntOr(1, -1), pos.getIntOr(2, -1), state, keptBlockEntity(state, nbt));
            }
            return builder;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
        }
    }

    /** Reads a whole file, for {@link IOException}-free callers. */
    static byte[] bytes(java.nio.file.Path path) throws IOException {
        return java.nio.file.Files.readAllBytes(path);
    }
}
