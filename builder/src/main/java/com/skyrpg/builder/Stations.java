package com.skyrpg.builder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.items.MaterialStore;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * Every Builder Station on the server and what they do: the menu, planning (pick a blueprint, move/turn/mirror its
 * hologram), deliveries, and the build itself, which goes layer by layer from the bottom up, sweeping out from the
 * station: first clearing what's in the way (into the leftovers), then solid blocks, then the ones that hang on
 * them. Each placed block takes its materials from the station. Holograms are sent to players within
 * {@link #GHOST_RANGE} blocks.
 */
public final class Stations {
    private static final int GHOST_RANGE = 160;
    private static final int USE_RANGE = 8;
    private static final int PLACE_RANGE = 128;
    /** Ticks the build rests after each layer, so it visibly goes up a level at a time. */
    private static final int LAYER_PAUSE = 8;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final int BEAM_COLOR = 0x5CE1E6;
    private static final String HUB = "skyrpg_hub:hub";
    private static final AtomicInteger VERSIONS = new AtomicInteger();

    /** Every station, saved with the overworld. */
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Station.CODEC.listOf().optionalFieldOf("stations", List.of()).forGetter(d -> List.copyOf(d.byKey.values()))).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgBuilder.id("stations"), Data::new, CODEC, null);

        final Map<String, Station> byKey = new LinkedHashMap<>();

        Data() {
        }

        Data(List<Station> stations) {
            for (Station station : stations) {
                station.version = VERSIONS.incrementAndGet();
                byKey.put(station.key(), station);
            }
        }
    }

    private record Sent(BlockPos pos, int version) {
    }

    /** Which holograms (and blueprint geometry) each player has been sent. */
    private static final Map<UUID, Map<String, Sent>> GHOSTS = new HashMap<>();
    private static final Map<UUID, Set<String>> GEOMETRY = new HashMap<>();
    private static final Map<UUID, Long> IN_THE_WAY_WARNED = new HashMap<>();

    private Stations() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Stations::tick);
        // Sneak + right-click a station, whatever is in your hand (vanilla skips blocks when sneaking with an item): move
        // its hologram again.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
                    || !(world.getBlockState(hit.getBlockPos()).getBlock() instanceof BuilderBlock)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                open(serverPlayer, hit.getBlockPos(), true);
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.Action.TYPE, (payload, context) -> act(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.UploadChunk.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Library.UploadOutcome outcome = Library.chunk(player, payload.session(), payload.index(), payload.count(), payload.fileName(),
                    payload.data());
            if (outcome != null) {
                ServerPlayNetworking.send(player, new BuilderPayloads.UploadResult(outcome.ok(), outcome.message(), outcome.id()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            GHOSTS.remove(id);
            GEOMETRY.remove(id);
            IN_THE_WAY_WARNED.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            GHOSTS.clear();
            GEOMETRY.clear();
            IN_THE_WAY_WARNED.clear();
        });
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
    }

    /** Shows what an action did in the menu for a few seconds. */
    private static void say(Station station, String text) {
        station.lastResult = text;
        station.resultUntil = System.currentTimeMillis() + 6000;
    }

    private static void changed(Station station) {
        if (station.resultUntil == Long.MAX_VALUE) {
            station.lastResult = "";
            station.resultUntil = 0;
        }
        station.version = VERSIONS.incrementAndGet();
        station.viewDirty = true;
        station.scan = null;
    }

    // ------------------------------------------------------------------ the block

    static void placed(ServerLevel level, BlockPos pos, Direction facing, String owner) {
        Data data = data(level.getServer());
        Station station = new Station(dimension(level), pos.immutable(), facing, owner);
        changed(station);
        data.byKey.put(station.key(), station);
        data.setDirty();
    }

    /** The station block is gone: everything it held drops where it stood. */
    static void removed(ServerLevel level, BlockPos pos) {
        Data data = data(level.getServer());
        Station station = data.byKey.remove(Station.key(dimension(level), pos));
        if (station == null) {
            return;
        }
        data.setDirty();
        dropAll(level, pos, station.stored);
        dropAll(level, pos, station.leftovers);
    }

    private static void dropAll(ServerLevel level, BlockPos pos, Map<String, Integer> items) {
        items.forEach((id, count) -> {
            Item item = item(id);
            int left = Math.min(count, 64 * 64 * 4);
            while (left > 0 && item != Items.AIR) {
                int n = Math.min(left, item.getDefaultMaxStackSize());
                ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(item, n));
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
                left -= n;
            }
        });
        items.clear();
    }

    private static Station station(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BuilderBlock)) {
            return null;
        }
        Data data = data(level.getServer());
        return data.byKey.computeIfAbsent(Station.key(dimension(level), pos), key -> {
            // Placed some other way (a command, a structure): it starts out empty.
            Station station = new Station(dimension(level), pos.immutable(), state.getValue(BuilderBlock.FACING), "");
            changed(station);
            data.setDirty();
            return station;
        });
    }

    static void open(ServerPlayer player, BlockPos pos) {
        open(player, pos, false);
    }

    /** Right-click: the menu. Sneak + right-click while planning: straight back to placing the hologram in the world. */
    static void open(ServerPlayer player, BlockPos pos, boolean sneaking) {
        ServerLevel level = player.level();
        if (dimension(level).equals(HUB)) {
            Notice.send(player, Notice.Kind.WARN, "Builder Stations don't work in the hub. Use one in the survival world.");
            return;
        }
        Station station = station(level, pos);
        if (station == null) {
            return;
        }
        Blueprint blueprint = Library.get(station.blueprintId);
        if (blueprint != null) {
            sendGeometry(player, blueprint);
        }
        if (sneaking && blueprint != null && station.state == Station.PLANNING && ServerPlayNetworking.canSend(player, BuilderPayloads.Place.TYPE)) {
            ServerPlayNetworking.send(player, new BuilderPayloads.Place(station.pos, blueprint.id, blueprint.name, station.placement(blueprint).origin(),
                    station.turns, station.mirrored));
            return;
        }
        station.viewers.add(player.getUUID());
        ServerPlayNetworking.send(player, menu(level, station, blueprint, player, true));
    }

    // ------------------------------------------------------------------ menu

    private static BuilderPayloads.Menu menu(ServerLevel level, Station station, Blueprint blueprint, ServerPlayer player, boolean open) {
        Station.Scan scan = blueprint == null ? Station.Scan.empty() : scan(level, station, blueprint);
        Map<String, BuilderPayloads.Line> lines = new LinkedHashMap<>();
        scan.needs().forEach((id, need) -> lines.put(id, new BuilderPayloads.Line(id, station.storedCount(id), need)));
        station.stored.forEach((id, have) -> lines.putIfAbsent(id, new BuilderPayloads.Line(id, have, 0)));
        List<BuilderPayloads.Line> sorted = new ArrayList<>(lines.values());
        sorted.sort(Comparator.<BuilderPayloads.Line>comparingInt(l -> l.have() >= l.need() ? 1 : 0)
                .thenComparingInt(l -> -(l.need() - Math.min(l.have(), l.need())))
                .thenComparing(BuilderPayloads.Line::item));
        int leftovers = station.leftovers.values().stream().mapToInt(Integer::intValue).sum();
        int done = station.state == Station.BUILDING || station.state == Station.DONE ? station.processed : scan.done();
        return new BuilderPayloads.Menu(open, station.pos, station.state, station.blueprintId, blueprint == null ? "" : blueprint.name,
                blueprint == null ? "" : blueprint.author, blueprint == null ? 0 : blueprint.sizeX, blueprint == null ? 0 : blueprint.sizeY,
                blueprint == null ? 0 : blueprint.sizeZ, station.turns, station.mirrored, station.offX, station.offY, station.offZ,
                station.preview, station.clear, done, scan.total(), scan.skipped(), scan.inTheWay(), station.layer, sorted, leftovers,
                note(station, blueprint, scan), player.isCreative(), helpers(station));
    }

    /** "Drogashh 120 · PhilipSGamer 64": who delivered most, for the menu. */
    private static String helpers(Station station) {
        return station.helpers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
                .map(e -> e.getKey() + " " + String.format("%,d", e.getValue())).reduce((a, b) -> a + "  ·  " + b).orElse("");
    }

    private static String note(Station station, Blueprint blueprint, Station.Scan scan) {
        if (!station.pauseReason.isEmpty()) {
            return station.pauseReason;
        }
        if (!station.lastResult.isEmpty() && System.currentTimeMillis() < station.resultUntil) {
            return station.lastResult;
        }
        return switch (station.state) {
            case Station.EMPTY -> "Pick a blueprint to start.";
            case Station.BUILDING -> "Building layer " + (station.layer + 1) + " of " + (blueprint == null ? "?" : blueprint.sizeY) + "...";
            case Station.DONE -> "Finished.";
            default -> scan.inTheWay() > 0 && !station.clear ? scan.inTheWay() + " blocks are in the way and will be left out."
                    : "Deliver the materials, then press Build. \"Move in world\" places it somewhere else.";
        };
    }

    /** Compares the blueprint with the world: what's built, what's needed. Cached for two seconds. */
    static Station.Scan scan(ServerLevel level, Station station, Blueprint blueprint) {
        if (station.scan != null && level.getGameTime() - station.scannedAt < 40) {
            return station.scan;
        }
        Placement placement = station.placement(blueprint);
        BlockState[] transformed = transformed(blueprint, placement);
        Map<String, Integer> needs = new HashMap<>();
        int done = 0, total = 0, skipped = 0, inTheWay = 0;
        for (int entry = 0; entry < blueprint.count(); entry++) {
            BlockState target = transformed[blueprint.states[entry]];
            if (!Materials.buildable(target)) {
                skipped++;
                continue;
            }
            total++;
            BlockPos pos = placement.world(blueprint, blueprint.positions[entry]);
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() == target.getBlock()) {
                done++;
                continue;
            }
            if (!current.isAir() && !current.canBeReplaced()) {
                inTheWay++;
            }
            for (Materials.Need need : Materials.needs(level, target)) {
                needs.merge(itemId(need.item()), need.count(), Integer::sum);
            }
        }
        station.scan = new Station.Scan(needs, done, total, skipped, inTheWay);
        station.scannedAt = level.getGameTime();
        return station.scan;
    }

    private static BlockState[] transformed(Blueprint blueprint, Placement placement) {
        BlockState[] out = new BlockState[blueprint.palette.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = placement.transform(blueprint.palette.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ actions

    static void act(ServerPlayer player, BuilderPayloads.Action action) {
        ServerLevel level = player.level();
        Station station = station(level, action.station());
        if (station == null) {
            return;
        }
        if (action.action().equals("close")) {
            station.viewers.remove(player.getUUID());
            return;
        }
        // Placing happens out in the world, away from the station; everything else is done standing at it.
        double range = action.action().equals("place") ? PLACE_RANGE : USE_RANGE;
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(action.station())) > range * range || dimension(level).equals(HUB)) {
            return;
        }
        Blueprint blueprint = Library.get(station.blueprintId);
        boolean planning = station.state == Station.PLANNING;
        switch (action.action()) {
            case "library" -> {
                ServerPlayNetworking.send(player, new BuilderPayloads.LibraryList(Library.all(), player.getGameProfile().name()));
                return;
            }
            case "geometry" -> {
                // The picker's cards draw any blueprint, not just the chosen one.
                Blueprint shown = Library.get(action.text());
                if (shown != null) {
                    sendGeometry(player, shown);
                }
                return;
            }
            case "refresh" -> station.scan = null;
            case "choose" -> choose(player, station, action.text());
            case "move" -> {
                if (planning) {
                    station.offX = clampOffset(station.offX + Math.max(-16, Math.min(16, action.x())));
                    station.offY = clampOffset(station.offY + Math.max(-16, Math.min(16, action.y())));
                    station.offZ = clampOffset(station.offZ + Math.max(-16, Math.min(16, action.z())));
                    changed(station);
                }
            }
            case "turn" -> {
                if (planning && blueprint != null) {
                    Placement before = station.placement(blueprint);
                    station.turns = Math.floorMod(station.turns + (action.x() < 0 ? -1 : 1), 4);
                    Placement after = station.placement(blueprint);
                    // Turn about the middle, not the corner.
                    station.offX = clampOffset(station.offX + (before.width() - after.width()) / 2);
                    station.offZ = clampOffset(station.offZ + (before.depth() - after.depth()) / 2);
                    changed(station);
                }
            }
            case "place" -> {
                // Placed in the world (PlacementMode on the client): offsets from the station, then "turns,mirrored".
                if (planning && blueprint != null) {
                    station.offX = clampOffset(action.x());
                    station.offY = clampOffset(action.y());
                    station.offZ = clampOffset(action.z());
                    String[] parts = action.text().split(",");
                    try {
                        station.turns = Math.floorMod(Integer.parseInt(parts[0].trim()), 4);
                        station.mirrored = parts.length > 1 && parts[1].trim().equals("1");
                    } catch (NumberFormatException ignored) {
                        // keep the turn and mirror
                    }
                    if (station.placement(blueprint).contains(station.pos)) {
                        say(station, "It would cover the station, so it wasn't moved there.");
                        return;
                    }
                    changed(station);
                    say(station, "Placed. Deliver the materials here, then press Build.");
                    level.playSound(null, station.placement(blueprint).origin(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
                }
            }
            case "mirror" -> {
                if (planning) {
                    station.mirrored = !station.mirrored;
                    changed(station);
                }
            }
            case "preview" -> {
                station.preview = !station.preview;
                changed(station);
            }
            case "clear" -> {
                if (planning) {
                    station.clear = !station.clear;
                    changed(station);
                }
            }
            case "deposit" -> deposit(player, level, station, blueprint, action.text());
            case "withdraw" -> withdraw(player, station, action.text());
            case "collect" -> {
                int count = station.leftovers.values().stream().mapToInt(Integer::intValue).sum();
                station.leftovers.forEach((id, n) -> give(player, item(id), n));
                station.leftovers.clear();
                if (count > 0) {
                    say(station, "Collected " + String.format("%,d", count) + " items.");
                    pickupSound(player);
                }
            }
            case "build" -> startBuild(player, level, station, blueprint);
            case "cancel" -> {
                if (station.state == Station.BUILDING) {
                    stopBuilding(level, station, Station.PLANNING);
                    say(station, "Build stopped. Press Build to carry on where it left off.");
                }
            }
            case "again" -> {
                if (station.state == Station.DONE && blueprint != null) {
                    station.state = Station.PLANNING;
                    changed(station);
                }
            }
            case "reset" -> {
                if (station.state != Station.BUILDING) {
                    station.blueprintId = "";
                    station.state = Station.EMPTY;
                    changed(station);
                }
            }
            case "delete" -> {
                String problem = Library.delete(player, action.text());
                if (!problem.isEmpty()) {
                    Notice.send(player, Notice.Kind.WARN, problem);
                }
                ServerPlayNetworking.send(player, new BuilderPayloads.LibraryList(Library.all(), player.getGameProfile().name()));
                return;
            }
            default -> {
                return;
            }
        }
        station.viewDirty = true;
        data(level.getServer()).setDirty();
        // Answer right away so the menu feels instant.
        ServerPlayNetworking.send(player, menu(level, station, Library.get(station.blueprintId), player, false));
        station.viewDirty = false;
    }

    private static int clampOffset(int value) {
        return Math.max(-Station.MAX_OFFSET, Math.min(Station.MAX_OFFSET, value));
    }

    private static void choose(ServerPlayer player, Station station, String id) {
        if (station.state == Station.BUILDING) {
            return;
        }
        Blueprint blueprint = Library.get(id);
        if (blueprint == null) {
            Notice.send(player, Notice.Kind.WARN, "That blueprint isn't there any more.");
            return;
        }
        station.blueprintId = id;
        station.helpers.clear();
        station.turns = 0;
        station.mirrored = false;
        station.state = Station.PLANNING;
        station.layer = 0;
        station.cursor = 0;
        station.processed = 0;
        station.blocked = 0;
        // Behind the station as its placer saw it (the lens faces them), centred on it, standing on the same ground.
        int w = blueprint.sizeX;
        int d = blueprint.sizeZ;
        station.offY = 0;
        switch (station.facing.getOpposite()) {
            case NORTH -> {
                station.offX = -w / 2;
                station.offZ = -d - 1;
            }
            case EAST -> {
                station.offX = 2;
                station.offZ = -d / 2;
            }
            case WEST -> {
                station.offX = -w - 1;
                station.offZ = -d / 2;
            }
            default -> {
                station.offX = -w / 2;
                station.offZ = 2;
            }
        }
        station.offX = clampOffset(station.offX);
        station.offZ = clampOffset(station.offZ);
        changed(station);
        sendGeometry(player, blueprint);
        say(station, "Loaded " + blueprint.name + ". Aim where it should go and click to place it.");
    }

    private static void deposit(ServerPlayer player, ServerLevel level, Station station, Blueprint blueprint, String only) {
        if (blueprint == null || station.state == Station.EMPTY || station.state == Station.DONE) {
            return;
        }
        Station.Scan scan = scan(level, station, blueprint);
        int delivered = 0;
        for (Map.Entry<String, Integer> need : scan.needs().entrySet()) {
            if (!only.isEmpty() && !only.equals(need.getKey())) {
                continue;
            }
            int missing = need.getValue() - station.storedCount(need.getKey());
            if (missing <= 0) {
                continue;
            }
            Item item = item(need.getKey());
            int count = player.isCreative() ? missing : Math.min(missing, MaterialStore.count(player, item));
            if (count <= 0) {
                continue;
            }
            if (!player.isCreative()) {
                MaterialStore.take(player, item, count);
            }
            station.store(need.getKey(), count);
            station.helpers.merge(player.getGameProfile().name(), count, Integer::sum);
            delivered += count;
        }
        if (delivered > 0) {
            say(station, "Delivered " + String.format("%,d", delivered) + " items.");
            pickupSound(player);
            if (station.state == Station.BUILDING) {
                station.pauseReason = "";
            }
        } else {
            say(station, only.isEmpty() ? "You have nothing the build still needs." : "You don't have any of that.");
        }
    }

    private static void withdraw(ServerPlayer player, Station station, String only) {
        if (station.state == Station.BUILDING) {
            say(station, "Stop the build to take materials back.");
            return;
        }
        int taken = 0;
        for (String id : new ArrayList<>(station.stored.keySet())) {
            if (!only.isEmpty() && !only.equals(id)) {
                continue;
            }
            int count = station.take(id, Integer.MAX_VALUE);
            give(player, item(id), count);
            taken += count;
        }
        if (taken > 0) {
            say(station, "Took back " + String.format("%,d", taken) + " items.");
            pickupSound(player);
        }
    }

    private static void give(ServerPlayer player, Item item, int count) {
        while (count > 0 && item != Items.AIR) {
            int n = Math.min(count, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
                entity.setNoPickUpDelay();
                player.level().addFreshEntity(entity);
            }
            count -= n;
        }
    }

    private static void pickupSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.1f);
    }

    // ------------------------------------------------------------------ building

    private static void startBuild(ServerPlayer player, ServerLevel level, Station station, Blueprint blueprint) {
        if (station.state != Station.PLANNING || blueprint == null) {
            return;
        }
        Placement placement = station.placement(blueprint);
        if (placement.contains(station.pos)) {
            say(station, "The build would cover the station. Move it away first.");
            return;
        }
        if (placement.origin().getY() < level.getMinY() || placement.max().getY() > level.getMaxY()) {
            say(station, "Part of the build is outside the world's height.");
            return;
        }
        station.scan = null;
        Station.Scan scan = scan(level, station, blueprint);
        for (Map.Entry<String, Integer> need : scan.needs().entrySet()) {
            if (station.storedCount(need.getKey()) < need.getValue()) {
                say(station, "Still missing materials: deliver everything first.");
                return;
            }
        }
        station.state = Station.BUILDING;
        station.buildTotal = scan.total();
        station.rate = 0;
        station.credit = 0;
        station.layerPause = 0;
        station.layer = 0;
        station.cursor = 0;
        station.processed = 0;
        station.blocked = 0;
        station.steps = null;
        station.pauseReason = "";
        setActive(level, station, true);
        changed(station);
        level.playSound(null, station.pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.3f);
        Notice.send(player, Notice.Kind.BUILDER, Component.literal("Building ").append(Component.literal(blueprint.name).withColor(0x5CE1E6))
                .append(" from the ground up. Stand clear!"));
    }

    private static void stopBuilding(ServerLevel level, Station station, int state) {
        station.state = state;
        station.steps = null;
        station.pauseReason = "";
        setActive(level, station, false);
        changed(station);
    }

    private static void setActive(ServerLevel level, Station station, boolean active) {
        BlockState state = level.getBlockState(station.pos);
        if (state.getBlock() instanceof BuilderBlock && state.getValue(BuilderBlock.ACTIVE) != active) {
            level.setBlock(station.pos, state.setValue(BuilderBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    /**
     * Blocks per tick (usually a fraction: one block every few ticks). The whole build takes 12 s plus 0.12 s a block
     * plus 1.6 s a layer, between 16 s and about 3 minutes: a small village house about 45 s, a big build a few minutes.
     */
    private static double rate(ServerLevel level, Station station, Blueprint blueprint) {
        if (station.buildTotal <= 0) {
            station.buildTotal = scan(level, station, blueprint).total();
        }
        int total = Math.max(1, station.buildTotal);
        int layers = Math.max(1, blueprint.sizeY);
        double seconds = Math.max(16, Math.min(192, 0.8 * (15 + total * 0.15 + layers * 2.0)));
        double blockTicks = Math.max(20, seconds * 20 - layers * LAYER_PAUSE);
        return total / blockTicks;
    }

    /** About how many seconds the build still needs. */
    private static int secondsLeft(Station station, Blueprint blueprint) {
        if (station.rate <= 0) {
            return 0;
        }
        double ticks = Math.max(0, station.buildTotal - station.processed) / station.rate
                + Math.max(0, blueprint.sizeY - station.layer - 1) * (double) LAYER_PAUSE;
        return (int) Math.ceil(ticks / 20);
    }

    private static void build(ServerLevel level, Station station) {
        if (!level.isLoaded(station.pos)) {
            return;
        }
        Blueprint blueprint = Library.get(station.blueprintId);
        if (blueprint == null) {
            stopBuilding(level, station, Station.EMPTY);
            say(station, "The blueprint is gone.");
            return;
        }
        Placement placement = station.placement(blueprint);
        if (station.layerPause > 0) {
            station.layerPause--;
            return;
        }
        if (station.rate <= 0) {
            station.rate = rate(level, station, blueprint);
        }
        // Work is paid for from a slowly filling credit: a block costs 1, clearing one a third.
        station.credit = Math.min(station.credit + station.rate, station.rate * 3 + 1.5);
        int checks = 4096;
        int sounds = 0;
        BlockPos last = null;
        while (station.credit > 0 && checks-- > 0) {
            if (station.layer >= blueprint.sizeY) {
                finish(level, station, blueprint, placement);
                return;
            }
            if (station.steps == null || station.steps.layer() != station.layer) {
                station.steps = layerSteps(station, blueprint, placement, station.layer);
            }
            if (station.cursor >= station.steps.size()) {
                station.layer++;
                station.cursor = 0;
                station.steps = null;
                station.version = VERSIONS.incrementAndGet();
                station.layerPause = LAYER_PAUSE;
                break;
            }
            BlockPos pos = BlockPos.of(station.steps.positions()[station.cursor]);
            int entry = station.steps.entries()[station.cursor];
            BlockState current = level.getBlockState(pos);
            if (entry == Station.BuildSteps.CLEAR) {
                if (!current.isAir() && clear(level, station, pos, current)) {
                    station.credit -= 0.34;
                }
                station.cursor++;
                continue;
            }
            BlockState target = placement.transform(blueprint.state(entry));
            if (!Materials.buildable(target)) {
                station.cursor++;
                continue;
            }
            if (current == target) {
                station.processed++;
                station.cursor++;
                continue;
            }
            if (current.getBlock() == target.getBlock()) {
                level.setBlock(pos, target, PLACE_FLAGS);
                station.processed++;
                station.cursor++;
                station.credit -= 1;
                continue;
            }
            if (!current.isAir() && !current.canBeReplaced() && (!station.clear || !clear(level, station, pos, current))) {
                station.blocked++;
                station.processed++;
                station.cursor++;
                continue;
            }
            if (!target.getCollisionShape(level, pos).isEmpty() && someoneInside(level, pos)) {
                return;
            }
            List<Materials.Need> needs = Materials.needs(level, target);
            for (Materials.Need need : needs) {
                if (station.storedCount(itemId(need.item())) < need.count()) {
                    pause(level, station, "Waiting for " + need.count() + " × " + need.item().getDefaultInstance().getHoverName().getString() + ". Deliver more to carry on.");
                    return;
                }
            }
            for (Materials.Need need : needs) {
                station.take(itemId(need.item()), need.count());
            }
            Item back = Materials.returns(target);
            if (back != null) {
                station.giveBack(itemId(back), 1);
            }
            station.pauseReason = "";
            level.setBlock(pos, target, PLACE_FLAGS);
            copyBlockEntity(level, pos, target, blueprint.blockEntities.get(entry));
            if (!(target.getBlock() instanceof LiquidBlock)) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        4, 0.3, 0.3, 0.3, 0.05);
            }
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
            if (sounds < 2) {
                sounds++;
                level.playSound(null, pos, target.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.55f, 0.85f + level.getRandom().nextFloat() * 0.3f);
            }
            last = pos;
            station.processed++;
            station.cursor++;
            station.credit -= 1;
        }
        if (last != null) {
            beam(level, station.pos, last);
        }
    }

    /** The steps of one layer: clears, then solid blocks, then the rest, each sweeping out from the station. */
    private static Station.BuildSteps layerSteps(Station station, Blueprint blueprint, Placement placement, int y) {
        int width = placement.width();
        int depth = placement.depth();
        boolean[] covered = new boolean[width * depth];
        List<long[]> solid = new ArrayList<>();
        List<long[]> rest = new ArrayList<>();
        for (int entry = blueprint.layerStart[y]; entry < blueprint.layerStart[y + 1]; entry++) {
            int local = blueprint.positions[entry];
            int lx = blueprint.localX(local);
            int lz = blueprint.localZ(local);
            int px = placement.placedX(lx, lz);
            int pz = placement.placedZ(lx, lz);
            covered[px + pz * width] = true;
            BlockPos pos = placement.origin().offset(px, y, pz);
            BlockState target = placement.transform(blueprint.state(entry));
            long[] step = {pos.asLong(), entry, distance(station.pos, pos)};
            (target.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? solid : rest).add(step);
        }
        List<long[]> clears = new ArrayList<>();
        if (station.clear) {
            for (int pz = 0; pz < depth; pz++) {
                for (int px = 0; px < width; px++) {
                    if (!covered[px + pz * width]) {
                        BlockPos pos = placement.origin().offset(px, y, pz);
                        clears.add(new long[]{pos.asLong(), Station.BuildSteps.CLEAR, distance(station.pos, pos)});
                    }
                }
            }
        }
        Comparator<long[]> byDistance = Comparator.comparingLong(step -> step[2]);
        clears.sort(byDistance);
        solid.sort(byDistance);
        rest.sort(byDistance);
        int size = clears.size() + solid.size() + rest.size();
        long[] positions = new long[size];
        int[] entries = new int[size];
        int i = 0;
        for (List<long[]> group : List.of(clears, solid, rest)) {
            for (long[] step : group) {
                positions[i] = step[0];
                entries[i] = (int) step[1];
                i++;
            }
        }
        return new Station.BuildSteps(y, positions, entries);
    }

    private static long distance(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Clears a block out of the way. What it drops goes to the leftovers, but only blocks you could pick up by hand drop. */
    private static boolean clear(ServerLevel level, Station station, BlockPos pos, BlockState current) {
        if (current.getDestroySpeed(level, pos) < 0 || current.getBlock() instanceof BuilderBlock) {
            return false;
        }
        if (!(current.getBlock() instanceof LiquidBlock)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!current.requiresCorrectToolForDrops()) {
                for (ItemStack drop : Block.getDrops(current, level, pos, blockEntity)) {
                    station.giveBack(itemId(drop.getItem()), drop.getCount());
                }
            }
            if (blockEntity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        station.giveBack(itemId(stack.getItem()), stack.getCount());
                    }
                }
                container.clearContent();
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, current), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.3, 0.3, 0.3, 0.05);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
        return true;
    }

    private static void copyBlockEntity(ServerLevel level, BlockPos pos, BlockState state, net.minecraft.nbt.CompoundTag data) {
        if (data == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            blockEntity.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        } catch (RuntimeException e) {
            SkyRpgBuilder.LOGGER.debug("Couldn't copy block entity data at {}: {}", pos, e.toString());
        }
    }

    private static boolean someoneInside(ServerLevel level, BlockPos pos) {
        List<Player> inside = level.getEntitiesOfClass(Player.class, new AABB(pos), p -> !p.isSpectator());
        for (Player player : inside) {
            if (player instanceof ServerPlayer serverPlayer) {
                long now = level.getGameTime();
                Long warned = IN_THE_WAY_WARNED.get(player.getUUID());
                if (warned == null || now - warned > 40) {
                    IN_THE_WAY_WARNED.put(player.getUUID(), now);
                    serverPlayer.sendOverlayMessage(Component.literal("Step out of the build, it's waiting for you!").withColor(0x5CE1E6));
                }
            }
        }
        return !inside.isEmpty();
    }

    private static void pause(ServerLevel level, Station station, String reason) {
        if (!reason.equals(station.pauseReason)) {
            station.pauseReason = reason;
            station.viewDirty = true;
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().distSqr(station.pos) < 48 * 48) {
                    Notice.send(player, Notice.Kind.BUILDER, reason);
                }
            }
        }
    }

    private static void beam(ServerLevel level, BlockPos from, BlockPos to) {
        double x0 = from.getX() + 0.5, y0 = from.getY() + 1.1, z0 = from.getZ() + 0.5;
        double dx = to.getX() + 0.5 - x0, dy = to.getY() + 0.5 - y0, dz = to.getZ() + 0.5 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int points = (int) Math.min(48, Math.max(2, length * 1.6));
        DustParticleOptions dust = new DustParticleOptions(BEAM_COLOR, 0.7f);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            level.sendParticles(dust, x0 + dx * t, y0 + dy * t, z0 + dz * t, 1, 0, 0, 0, 0);
        }
    }

    private static void finish(ServerLevel level, Station station, Blueprint blueprint, Placement placement) {
        int placed = station.processed - station.blocked;
        stopBuilding(level, station, Station.DONE);
        say(station, "Built " + blueprint.name + ": " + String.format("%,d", placed) + " blocks"
                + (station.blocked > 0 ? ", " + station.blocked + " left out (something was in the way)" : "") + ".");
        // Stays until the station is used for something else.
        station.resultUntil = Long.MAX_VALUE;
        BlockPos min = placement.origin();
        BlockPos max = placement.max();
        double cx = (min.getX() + max.getX() + 1) / 2.0, cz = (min.getZ() + max.getZ() + 1) / 2.0;
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, max.getY() + 1.5, cz, 160, placement.width() / 3.0, 1.5, placement.depth() / 3.0, 0.5);
        level.sendParticles(ParticleTypes.FIREWORK, cx, max.getY() + 2, cz, 60, placement.width() / 4.0, 1, placement.depth() / 4.0, 0.15);
        level.playSound(null, station.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
        progress(level.getServer(), station, blueprint, true);
        level.playSound(null, BlockPos.containing(cx, max.getY(), cz), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.5f, 1f);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(station.pos) < 64 * 64) {
                Notice.send(player, Notice.Kind.BUILDER, Component.literal("Finished building ")
                        .append(Component.literal(blueprint.name).withColor(0x5CE1E6)).append("!"));
            }
        }
    }

    // ------------------------------------------------------------------ ticking and holograms

    private static void tick(MinecraftServer server) {
        Data data = data(server);
        boolean building = false;
        for (Station station : data.byKey.values()) {
            if (station.state == Station.BUILDING) {
                ServerLevel level = level(server, station.dimension);
                if (level != null) {
                    build(level, station);
                    building = true;
                }
            }
        }
        if (building) {
            data.setDirty();
        }
        long time = server.getTickCount();
        if (time % 10 == 0) {
            refreshMenus(server, data);
        }
        if (time % 5 == 0) {
            // Cheap: only changed holograms are sent, so others see a placement move almost at once.
            syncGhosts(server, data);
        }
        if (time % 10 == 0) {
            sendProgress(server, data);
        }
    }

    /** The progress overlay for everyone near a build. */
    private static void sendProgress(MinecraftServer server, Data data) {
        for (Station station : data.byKey.values()) {
            if (station.state == Station.BUILDING) {
                progress(server, station, Library.get(station.blueprintId), false);
            }
        }
    }

    private static void progress(MinecraftServer server, Station station, Blueprint blueprint, boolean finished) {
        ServerLevel level = level(server, station.dimension);
        if (level == null || blueprint == null) {
            return;
        }
        if (station.buildTotal <= 0) {
            station.buildTotal = scan(level, station, blueprint).total();
        }
        int total = Math.max(1, station.buildTotal);
        BuilderPayloads.Progress progress = new BuilderPayloads.Progress(station.pos, blueprint.name, blueprint.id,
                finished ? total : Math.min(station.processed, total), total, station.layer, blueprint.sizeY, station.pauseReason,
                finished ? 0 : secondsLeft(station, blueprint));
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - station.pos.getX();
            double dz = player.getZ() - station.pos.getZ();
            if (dx * dx + dz * dz < 64 * 64 && ServerPlayNetworking.canSend(player, BuilderPayloads.Progress.TYPE)) {
                sendGeometry(player, blueprint);
                ServerPlayNetworking.send(player, progress);
            }
        }
    }

    private static void refreshMenus(MinecraftServer server, Data data) {
        for (Station station : data.byKey.values()) {
            if (station.viewers.isEmpty() || !(station.viewDirty || station.state == Station.BUILDING)) {
                continue;
            }
            ServerLevel level = level(server, station.dimension);
            Blueprint blueprint = Library.get(station.blueprintId);
            for (UUID id : new ArrayList<>(station.viewers)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || level == null || player.level() != level || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(station.pos)) > 24 * 24) {
                    station.viewers.remove(id);
                    continue;
                }
                ServerPlayNetworking.send(player, menu(level, station, blueprint, player, false));
            }
            station.viewDirty = false;
        }
    }

    private static void sendGeometry(ServerPlayer player, Blueprint blueprint) {
        Set<String> sent = GEOMETRY.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        if (sent.add(blueprint.id) && ServerPlayNetworking.canSend(player, BuilderPayloads.Geometry.TYPE)) {
            ServerPlayNetworking.send(player, new BuilderPayloads.Geometry(blueprint.withoutBlockEntities()));
        }
    }

    private static void syncGhosts(MinecraftServer server, Data data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, BuilderPayloads.Ghost.TYPE)) {
                continue;
            }
            String dimension = dimension(player.level());
            Map<String, Sent> sent = GHOSTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
            Set<String> shown = new HashSet<>();
            for (Station station : data.byKey.values()) {
                boolean visible = (station.state == Station.PLANNING && station.preview) || station.state == Station.BUILDING;
                if (!visible || !station.dimension.equals(dimension)) {
                    continue;
                }
                double dx = player.getX() - station.pos.getX();
                double dz = player.getZ() - station.pos.getZ();
                if (dx * dx + dz * dz > GHOST_RANGE * GHOST_RANGE) {
                    continue;
                }
                Blueprint blueprint = Library.get(station.blueprintId);
                if (blueprint == null) {
                    continue;
                }
                shown.add(station.key());
                Sent before = sent.get(station.key());
                if (before != null && before.version() == station.version) {
                    continue;
                }
                sendGeometry(player, blueprint);
                Placement placement = station.placement(blueprint);
                ServerPlayNetworking.send(player, new BuilderPayloads.Ghost(station.pos, station.state == Station.BUILDING
                        ? BuilderPayloads.Ghost.BUILD : BuilderPayloads.Ghost.PLAN, blueprint.id, placement.origin(), station.turns,
                        station.mirrored, station.layer));
                sent.put(station.key(), new Sent(station.pos, station.version));
            }
            for (String key : new ArrayList<>(sent.keySet())) {
                if (!shown.contains(key)) {
                    Sent gone = sent.remove(key);
                    ServerPlayNetworking.send(player, new BuilderPayloads.Ghost(gone.pos(), BuilderPayloads.Ghost.HIDE, "", BlockPos.ZERO, 0, false, 0));
                }
            }
        }
    }
}
