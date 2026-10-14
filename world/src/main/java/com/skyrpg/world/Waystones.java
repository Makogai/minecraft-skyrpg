package com.skyrpg.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Waystones: attune to one by using it, then use any waystone to travel to the others you've attuned (or to the hub)
 * for a few XP levels. The names live here (one list for the whole server, saved with the overworld); each player's
 * attunements live in their {@link Explorer} data. Villages get a waystone next to their bell the first time it loads.
 */
public final class Waystones {
    private static final double USE_RANGE = 6;
    private static final int MAX_COST = 5;
    private static final int HUB_COST_FROM = 2;
    private static final String[] WORDS = {"Elder", "Moss", "Ash", "Raven", "Thorn", "Amber", "Frost", "Dusk", "Glimmer", "Stone", "Willow",
            "Ember", "Hollow", "Silver", "Briar", "Wolf", "Star", "Mist", "Iron", "Bramble"};
    private static final String[] PLACES = {"watch", "ford", "rest", "gate", "stead", "mere", "crest", "vale", "hollow", "reach", "shrine", "cairn"};

    /** One waystone the server knows about. */
    record Entry(String dimension, long pos, String name) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension), Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)).apply(i, Entry::new));

        String key() {
            return dimension + "|" + pos;
        }
    }

    /** Every waystone's name, and which villages already got theirs. */
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().optionalFieldOf("waystones", List.of()).forGetter(d -> List.copyOf(d.entries.values())),
                Codec.LONG.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> List.copyOf(d.villages))).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgWorld.id("waystones"), Data::new, CODEC, null);

        final Map<String, Entry> entries = new LinkedHashMap<>();
        final Set<Long> villages = new HashSet<>();

        Data() {
        }

        Data(List<Entry> entries, List<Long> villages) {
            entries.forEach(entry -> this.entries.put(entry.key(), entry));
            this.villages.addAll(villages);
        }

        Entry get(String dimension, BlockPos pos) {
            return entries.get(dimension + "|" + pos.asLong());
        }

        void put(Entry entry) {
            entries.put(entry.key(), entry);
            setDirty();
        }

        void remove(String dimension, BlockPos pos) {
            if (entries.remove(dimension + "|" + pos.asLong()) != null) {
                setDirty();
            }
        }
    }

    /** Where a player's open menu can take them, in the order the menu shows. */
    private record Trip(String dimension, BlockPos from, List<Target> targets) {
    }

    private record Target(boolean hub, Entry entry, int cost) {
    }

    private static final Map<UUID, Trip> OPEN = new HashMap<>();
    private static final List<BlockPos> BELLS = new ArrayList<>();

    private Waystones() {
    }

    static void init() {
        // Villages: remember bells as their chunks load, place the waystone once the area is loaded.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (level.dimension() != Level.OVERWORLD) {
                return;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BellBlockEntity) {
                    BELLS.add(blockEntity.getBlockPos());
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!BELLS.isEmpty() && server.getTickCount() % 20 == 0) {
                villageWaystones(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            OPEN.clear();
            BELLS.clear();
        });
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    private static boolean isHub(String dimension) {
        return dimension.startsWith("skyrpg_hub:");
    }

    // ---------------------------------------------------------------- using one

    /** A player used the waystone at {@code pos} (holding a name tag to rename it, or not). */
    static void use(ServerPlayer player, BlockPos pos, ItemStack nameTag) {
        ServerLevel level = player.level();
        Data data = data(level.getServer());
        String dimension = dimensionOf(level);
        Entry entry = data.get(dimension, pos);
        if (entry == null) {
            entry = new Entry(dimension, pos.asLong(), isHub(dimension) ? "Skyhaven" : autoName(level, pos));
            data.put(entry);
        }
        if (nameTag != null && nameTag.has(DataComponents.CUSTOM_NAME)) {
            String name = nameTag.getHoverName().getString().strip();
            if (!name.isEmpty() && name.length() <= 32) {
                entry = new Entry(dimension, pos.asLong(), name);
                data.put(entry);
                if (!player.isCreative()) {
                    nameTag.shrink(1);
                }
                player.sendOverlayMessage(Component.literal("Waystone renamed: " + name).withStyle(ChatFormatting.AQUA));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
                return;
            }
        }
        if (!isHub(dimension) && !Explorer.state(player).attuned(dimension, pos)) {
            Explorer.attune(player, dimension, pos, entry.name());
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(WaystoneBlock.LIT) && !state.getValue(WaystoneBlock.LIT)) {
                level.setBlock(pos, state.setValue(WaystoneBlock.LIT, true), Block.UPDATE_ALL);
            }
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.8);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.05);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
            WorldPayloads.banner(player, SkyRpgWorld.id("waystone"), "WAYSTONE ATTUNED", Component.literal(entry.name()),
                    Component.literal("Use any waystone to travel back here"), 0xFF5CE1E6);
        }
        openMenu(player, entry);
    }

    private static void openMenu(ServerPlayer player, Entry here) {
        ServerLevel level = player.level();
        Data data = data(level.getServer());
        List<Target> targets = new ArrayList<>();
        List<WorldPayloads.Destination> shown = new ArrayList<>();
        boolean inHub = isHub(here.dimension());
        if (!inHub) {
            targets.add(new Target(true, null, 0));
            shown.add(new WorldPayloads.Destination(WorldPayloads.Destination.HUB, "Skyhaven", "Return to the hub", 0));
        }
        // Refresh the player's list: names may have changed, and broken waystones drop out.
        List<Explorer.Attuned> kept = new ArrayList<>();
        BlockPos herePos = BlockPos.of(here.pos());
        for (Explorer.Attuned attuned : Explorer.state(player).waystones()) {
            Entry entry = data.entries.get(attuned.dimension() + "|" + attuned.pos());
            if (entry == null) {
                continue;
            }
            kept.add(new Explorer.Attuned(entry.dimension(), entry.pos(), entry.name()));
            if (entry.key().equals(here.key())) {
                continue;
            }
            int cost = inHub ? HUB_COST_FROM : cost(here.dimension(), herePos, entry);
            targets.add(new Target(false, entry, cost));
            shown.add(new WorldPayloads.Destination(WorldPayloads.Destination.WAYSTONE, entry.name(), describe(here, entry), cost));
        }
        Explorer.setWaystones(player, kept);
        OPEN.put(player.getUUID(), new Trip(here.dimension(), herePos, targets));
        if (ServerPlayNetworking.canSend(player, WorldPayloads.WaystoneMenu.TYPE)) {
            ServerPlayNetworking.send(player, new WorldPayloads.WaystoneMenu(here.name(), shown));
        } else {
            Notice.send(player, Notice.Kind.INFO, Component.literal(here.name() + ": install SkyRPG to travel between waystones.").withStyle(ChatFormatting.AQUA));
        }
        level.playSound(null, herePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    private static int cost(String dimension, BlockPos from, Entry to) {
        double distance = dimension.equals(to.dimension()) ? Math.sqrt(from.distSqr(BlockPos.of(to.pos()))) : 3000;
        return Math.min(MAX_COST, 1 + (int) (distance / 1000));
    }

    private static String describe(Entry here, Entry to) {
        BlockPos pos = BlockPos.of(to.pos());
        String world = switch (to.dimension()) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> to.dimension();
        };
        if (!here.dimension().equals(to.dimension())) {
            return world + " · " + pos.getX() + ", " + pos.getZ();
        }
        long blocks = Math.round(Math.sqrt(BlockPos.of(here.pos()).distSqr(pos)));
        return world + " · " + Msg.number(blocks) + " blocks · " + pos.getX() + ", " + pos.getZ();
    }

    // ---------------------------------------------------------------- travelling

    /** From the menu: go to the destination at {@code index}, if the player is still at their waystone and can pay. */
    static void travel(ServerPlayer player, int index) {
        Trip trip = OPEN.remove(player.getUUID());
        if (trip == null || index < 0 || index >= trip.targets().size() || !dimensionOf(player.level()).equals(trip.dimension())
                || player.position().distanceTo(Vec3.atCenterOf(trip.from())) > USE_RANGE) {
            return;
        }
        Target target = trip.targets().get(index);
        if (!player.isCreative() && player.experienceLevel < target.cost()) {
            player.sendOverlayMessage(Component.literal("You need " + target.cost() + " XP levels to travel there.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }
        ServerLevel fromLevel = player.level();
        Vec3 from = player.position();
        if (target.hub()) {
            effects(fromLevel, from, false);
            fromLevel.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), "hub");
            return;
        }
        Entry entry = target.entry();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension()));
        ServerLevel toLevel = fromLevel.getServer().getLevel(key);
        if (toLevel == null) {
            return;
        }
        BlockPos stone = BlockPos.of(entry.pos());
        toLevel.getChunk(stone);
        if (!(toLevel.getBlockState(stone).getBlock() instanceof WaystoneBlock)) {
            data(fromLevel.getServer()).remove(entry.dimension(), stone);
            player.sendOverlayMessage(Component.literal("That waystone is gone.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!player.isCreative()) {
            player.giveExperienceLevels(-target.cost());
        }
        Vec3 to = standingSpot(toLevel, stone);
        effects(fromLevel, from, false);
        float yaw = (float) Math.toDegrees(Math.atan2(-(stone.getX() + 0.5 - to.x), stone.getZ() + 0.5 - to.z));
        player.teleport(new TeleportTransition(toLevel, to, Vec3.ZERO, yaw, 10, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        effects(toLevel, to, true);
        player.sendOverlayMessage(Component.literal("✦ " + entry.name()).withStyle(ChatFormatting.AQUA));
    }

    /** Somewhere to stand next to a waystone: two free blocks over a solid one. */
    private static Vec3 standingSpot(ServerLevel level, BlockPos stone) {
        for (int distance = 1; distance <= 2; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos feet = stone.relative(direction, distance).above(dy);
                    // Two blocks out, the block in between must be open too, or we'd land behind a wall.
                    BlockPos between = stone.relative(direction, 1).above(dy);
                    if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && open(level, feet)
                            && (distance == 1 || open(level, between))) {
                        return Vec3.atBottomCenterOf(feet);
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(stone.above());
    }

    /** Room for a player: no collision at the feet or the head. */
    private static boolean open(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void effects(ServerLevel level, Vec3 at, boolean arrive) {
        level.sendParticles(arrive ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 50, 0.4, 0.9, 0.4, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 12, 0.3, 0.8, 0.3, 0.03);
        level.playSound(null, at.x, at.y, at.z, arrive ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, arrive ? 1.3f : 1.1f);
    }

    // ---------------------------------------------------------------- names and villages

    /** A name for a new waystone: the dungeon it guards, the village it stands in, or a made-up place and its biome. */
    static String autoName(ServerLevel level, BlockPos pos) {
        for (Marker marker : DungeonMarkers.near(level, Vec3.atCenterOf(pos), 48)) {
            DungeonMarkers.Info info = DungeonMarkers.info(level, marker);
            if (info.kind().equals("dungeon") && info.dungeon() != null) {
                return info.dungeon().name();
            }
        }
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
        String word = WORDS[(int) Math.floorMod(hash >>> 7, WORDS.length)] + PLACES[(int) Math.floorMod(hash >>> 23, PLACES.length)];
        Holder<Biome> biome = level.getBiome(pos);
        String biomeName = biome.unwrapKey().map(key -> title(key.identifier().getPath())).orElse("Wilds");
        if (level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid()) {
            return word + " Village";
        }
        return word + " · " + biomeName;
    }

    private static String title(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }

    private static void villageWaystones(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Data data = data(server);
        for (BlockPos bell : new ArrayList<>(BELLS)) {
            if (!level.isLoaded(bell.offset(-4, 0, -4)) || !level.isLoaded(bell.offset(4, 0, 4))) {
                continue;
            }
            BELLS.remove(bell);
            StructureStart village = level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE);
            if (!village.isValid() || data.villages.contains(village.getChunkPos().pack())) {
                continue;
            }
            BlockPos spot = besideBell(level, bell);
            if (spot == null) {
                continue;
            }
            data.villages.add(village.getChunkPos().pack());
            data.setDirty();
            level.setBlock(spot, WorldContent.WAYSTONE.defaultBlockState(), Block.UPDATE_ALL);
            data.put(new Entry(dimensionOf(level), spot.asLong(), autoName(level, spot)));
        }
    }

    /** A free spot on solid ground a couple of blocks from the bell. */
    private static BlockPos besideBell(ServerLevel level, BlockPos bell) {
        for (int distance = 2; distance <= 4; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos pos = bell.relative(direction, distance).above(dy);
                    BlockState here = level.getBlockState(pos);
                    if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
                            && level.getBlockState(pos.above()).isAir()
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** /waystone list: every waystone the server knows. */
    static List<String> list(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (Entry entry : data(server).entries.values()) {
            lines.add(entry.name() + " · " + entry.dimension() + " " + BlockPos.of(entry.pos()).toShortString());
        }
        return lines;
    }
}
