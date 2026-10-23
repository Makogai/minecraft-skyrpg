package com.skyrpg.core.records;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import com.skyrpg.core.SkyRpgCore;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Everyone's statistics for the Players page of the menu: playtime, distance, blocks mined, kills and so on from
 * vanilla's statistics, plus whatever other mods add with {@link #addSource} (skill levels, coins, ...).
 *
 * <p>Online players are read live; everyone else from the snapshot saved when they last left (and every few minutes
 * while they play) in {@code <world>/skyrpg_records/<uuid>.json}. Players from before this existed, with only a vanilla
 * stats file, still show with their vanilla numbers. The client asks with {@link Request} and gets the whole board as
 * JSON in {@link Board}.
 */
public final class PlayerRecords {
    private static final Gson GSON = new Gson();
    private static final long SAVE_EVERY_MS = 5 * 60_000;
    private static final long REQUEST_EVERY_MS = 1_000;
    private static final int TOP = 12;
    private static final List<Consumer<Entries>> SOURCES = new ArrayList<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static MinecraftServer server;
    private static long lastSave = Util.getMillis();

    private PlayerRecords() {
    }

    /** How a value is shown: ticks as "3h 12m", centimetres as "1.2 km", damage (tenths) as a number, and so on. */
    public enum Format {
        COUNT, TIME, DISTANCE, DAMAGE, COINS, LEVEL, AVERAGE
    }

    /** One player's numbers being collected; sources add theirs with {@link #add}. */
    public static final class Entries {
        private final ServerPlayer player;
        private final JsonArray array = new JsonArray();

        Entries(ServerPlayer player) {
            this.player = player;
        }

        public ServerPlayer player() {
            return player;
        }

        /**
         * A number under {@code section} ("SkyRPG", "Combat"...). {@code key} must be unique; {@code icon} is an item
         * id. For {@link Format#AVERAGE} pass the value times 100.
         */
        public Entries add(String section, String key, String label, Format format, Item icon, long value) {
            JsonObject entry = new JsonObject();
            entry.addProperty("s", section);
            entry.addProperty("k", key);
            entry.addProperty("l", label);
            entry.addProperty("f", format.name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("i", BuiltInRegistries.ITEM.getKey(icon).toString());
            entry.addProperty("v", value);
            array.add(entry);
            return this;
        }

        /** A line explaining the number just added, shown when it's hovered. */
        public Entries note(String text) {
            if (!array.isEmpty()) {
                array.get(array.size() - 1).getAsJsonObject().addProperty("d", text);
            }
            return this;
        }
    }

    /** Adds a mod's own numbers to every player's page (called on the server thread, for online players). */
    public static void addSource(Consumer<Entries> source) {
        SOURCES.add(source);
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Board.TYPE, Board.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> request(context.player()));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            s.getPlayerList().getPlayers().forEach(PlayerRecords::save);
            server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> save(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            save(handler.player);
            LAST_REQUEST.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (Util.getMillis() - lastSave > SAVE_EVERY_MS) {
                lastSave = Util.getMillis();
                s.getPlayerList().getPlayers().forEach(PlayerRecords::save);
            }
        });
    }

    private static void request(ServerPlayer player) {
        long now = Util.getMillis();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (server == null || last != null && now - last < REQUEST_EVERY_MS) {
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        ServerPlayNetworking.send(player, new Board(GSON.toJson(board())));
    }

    /** Every known player: live for those online, else their last snapshot, else just their vanilla stats file. */
    private static JsonObject board() {
        Map<UUID, JsonObject> players = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            JsonObject live = snapshot(online);
            live.addProperty("online", true);
            players.put(online.getUUID(), live);
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path file : files) {
                UUID id = uuid(file);
                if (id != null && !players.containsKey(id)) {
                    players.put(id, GSON.fromJson(Files.readString(file), JsonObject.class));
                }
            }
        } catch (IOException | RuntimeException e) {
            if (Files.isDirectory(dir())) {
                SkyRpgCore.LOGGER.warn("Couldn't read player records", e);
            }
        }
        Path statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(statsDir, "*.json")) {
            for (Path file : files) {
                UUID id = uuid(file);
                if (id != null && !players.containsKey(id)) {
                    JsonObject legacy = legacy(id, file);
                    if (legacy != null) {
                        players.put(id, legacy);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            if (Files.isDirectory(statsDir)) {
                SkyRpgCore.LOGGER.warn("Couldn't read vanilla stats files", e);
            }
        }
        JsonArray array = new JsonArray();
        players.values().forEach(array::add);
        JsonObject board = new JsonObject();
        board.add("players", array);
        board.addProperty("now", System.currentTimeMillis());
        return board;
    }

    private static UUID uuid(Path file) {
        String name = file.getFileName().toString();
        try {
            return UUID.fromString(name.substring(0, name.length() - ".json".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonObject legacy(UUID id, Path file) {
        String name = server.services().nameToIdCache().get(id).map(n -> n.name()).orElse(null);
        if (name == null) {
            return null;
        }
        JsonObject out = new JsonObject();
        out.addProperty("id", id.toString());
        out.addProperty("name", name);
        try {
            out.addProperty("last", Files.getLastModifiedTime(file).toMillis());
        } catch (IOException ignored) {
        }
        vanilla(new ServerStatsCounter(server, file), out, new Entries(null));
        return out;
    }

    /** Writes the player's snapshot now (on joining, leaving, every few minutes and when the server stops). */
    private static void save(ServerPlayer player) {
        if (server == null) {
            return;
        }
        Path file = dir().resolve(player.getUUID() + ".json");
        try {
            JsonObject snapshot = snapshot(player);
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(snapshot));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Couldn't save player record {}", file, e);
        }
    }

    /** When the player was first recorded: from their saved snapshot, or now if there's none yet. */
    private static long firstSeen(UUID id) {
        Path file = dir().resolve(id + ".json");
        try {
            if (Files.isRegularFile(file)) {
                JsonElement first = GSON.fromJson(Files.readString(file), JsonObject.class).get("first");
                if (first != null) {
                    return first.getAsLong();
                }
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Couldn't read player record {}", file, e);
        }
        return System.currentTimeMillis();
    }

    private static Path dir() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg_records");
    }

    private static JsonObject snapshot(ServerPlayer player) {
        JsonObject out = new JsonObject();
        out.addProperty("id", player.getUUID().toString());
        out.addProperty("name", player.getGameProfile().name());
        out.addProperty("first", firstSeen(player.getUUID()));
        out.addProperty("last", System.currentTimeMillis());
        for (Property textures : player.getGameProfile().properties().get("textures")) {
            JsonArray skin = new JsonArray();
            skin.add(textures.value());
            skin.add(textures.signature());
            out.add("skin", skin);
        }
        Entries entries = new Entries(player);
        vanilla(player.getStats(), out, entries);
        for (Consumer<Entries> source : SOURCES) {
            try {
                source.accept(entries);
            } catch (RuntimeException e) {
                SkyRpgCore.LOGGER.warn("A player records source failed", e);
            }
        }
        out.add("entries", entries.array);
        return out;
    }

    // ------------------------------------------------------------------ vanilla statistics

    private static void vanilla(StatsCounter stats, JsonObject out, Entries e) {
        long distance = 0;
        Identifier[] travel = {Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SWIM_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM,
                Stats.WALK_ON_WATER_ONE_CM, Stats.CLIMB_ONE_CM, Stats.FALL_ONE_CM, Stats.FLY_ONE_CM, Stats.AVIATE_ONE_CM, Stats.BOAT_ONE_CM,
                Stats.HORSE_ONE_CM, Stats.MINECART_ONE_CM, Stats.PIG_ONE_CM, Stats.STRIDER_ONE_CM, Stats.HAPPY_GHAST_ONE_CM,
                Stats.NAUTILUS_ONE_CM};
        for (Identifier stat : travel) {
            distance += custom(stats, stat);
        }

        String life = "Time & Life";
        e.add(life, "play_time", "Playtime", Format.TIME, Items.CLOCK, custom(stats, Stats.PLAY_TIME));
        e.add(life, "sessions", "Sessions", Format.COUNT, Items.OAK_DOOR, custom(stats, Stats.LEAVE_GAME));
        e.add(life, "time_since_death", "Since last death", Format.TIME, Items.TOTEM_OF_UNDYING, custom(stats, Stats.TIME_SINCE_DEATH));
        e.add(life, "sleep", "Nights slept", Format.COUNT, Items.STRAW_BED, custom(stats, Stats.SLEEP_IN_BED) + custom(stats, Stats.SLEEP_IN_STRAW_BED));
        e.add(life, "fish", "Fish caught", Format.COUNT, Items.FISHING_ROD, custom(stats, Stats.FISH_CAUGHT));
        e.add(life, "bred", "Animals bred", Format.COUNT, Items.WHEAT, custom(stats, Stats.ANIMALS_BRED));
        e.add(life, "trades", "Villager trades", Format.COUNT, Items.EMERALD, custom(stats, Stats.TRADED_WITH_VILLAGER));
        e.add(life, "enchanted", "Items enchanted", Format.COUNT, Items.ENCHANTING_TABLE, custom(stats, Stats.ENCHANT_ITEM));
        e.add(life, "chests", "Chests opened", Format.COUNT, Items.CHEST, custom(stats, Stats.OPEN_CHEST) + custom(stats, Stats.OPEN_BARREL));
        e.add(life, "cake", "Cake slices eaten", Format.COUNT, Items.CAKE, custom(stats, Stats.EAT_CAKE_SLICE));

        String move = "Travel";
        e.add(move, "distance", "Distance travelled", Format.DISTANCE, Items.COMPASS, distance)
                .note("Every way of moving added up (griffin flights are counted separately)");
        e.add(move, "walk", "Walked", Format.DISTANCE, Items.LEATHER_BOOTS, custom(stats, Stats.WALK_ONE_CM));
        e.add(move, "sprint", "Sprinted", Format.DISTANCE, Items.GOLDEN_BOOTS, custom(stats, Stats.SPRINT_ONE_CM));
        e.add(move, "crouch", "Crouched", Format.DISTANCE, Items.LEATHER_LEGGINGS, custom(stats, Stats.CROUCH_ONE_CM));
        e.add(move, "swim", "Swum", Format.DISTANCE, Items.TROPICAL_FISH,
                custom(stats, Stats.SWIM_ONE_CM) + custom(stats, Stats.WALK_UNDER_WATER_ONE_CM));
        e.add(move, "climb", "Climbed", Format.DISTANCE, Items.LADDER, custom(stats, Stats.CLIMB_ONE_CM));
        e.add(move, "fall", "Fallen", Format.DISTANCE, Items.FEATHER, custom(stats, Stats.FALL_ONE_CM));
        e.add(move, "elytra", "Glided", Format.DISTANCE, Items.ELYTRA, custom(stats, Stats.AVIATE_ONE_CM));
        e.add(move, "fly", "In the air", Format.DISTANCE, Items.PHANTOM_MEMBRANE, custom(stats, Stats.FLY_ONE_CM))
                .note("Moving while off the ground without an elytra: mostly sprint-jumping, falling and knockback");
        e.add(move, "boat", "By boat", Format.DISTANCE, Items.OAK_BOAT, custom(stats, Stats.BOAT_ONE_CM));
        e.add(move, "ride", "Riding", Format.DISTANCE, Items.SADDLE,
                custom(stats, Stats.HORSE_ONE_CM) + custom(stats, Stats.PIG_ONE_CM) + custom(stats, Stats.STRIDER_ONE_CM)
                        + custom(stats, Stats.HAPPY_GHAST_ONE_CM) + custom(stats, Stats.NAUTILUS_ONE_CM));
        e.add(move, "minecart", "By minecart", Format.DISTANCE, Items.MINECART, custom(stats, Stats.MINECART_ONE_CM));
        e.add(move, "jumps", "Jumps", Format.COUNT, Items.RABBIT_FOOT, custom(stats, Stats.JUMP));

        Map<Identifier, Long> mined = new HashMap<>();
        long minedTotal = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            int count = stats.getValue(Stats.BLOCK_MINED, block);
            if (count > 0) {
                minedTotal += count;
                Item item = block.asItem();
                if (item != Items.AIR) {
                    mined.merge(BuiltInRegistries.ITEM.getKey(item), (long) count, Long::sum);
                }
            }
        }
        long placed = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) {
                placed += stats.getValue(Stats.ITEM_USED, item);
            }
        }
        String build = "Mining & Building";
        e.add(build, "mined", "Blocks mined", Format.COUNT, Items.IRON_PICKAXE, minedTotal);
        e.add(build, "placed", "Blocks placed", Format.COUNT, Items.BRICKS, placed);
        e.add(build, "crafted", "Items crafted", Format.COUNT, Items.CRAFTING_TABLE, total(stats, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM));
        e.add(build, "broken", "Tools worn out", Format.COUNT, Items.STONE_PICKAXE, total(stats, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM));
        e.add(build, "picked_up", "Items picked up", Format.COUNT, Items.HOPPER, total(stats, Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM));
        e.add(build, "dropped", "Items dropped", Format.COUNT, Items.DROPPER, custom(stats, Stats.DROP));

        String fight = "Combat";
        e.add(fight, "mob_kills", "Mobs killed", Format.COUNT, Items.IRON_SWORD, custom(stats, Stats.MOB_KILLS));
        e.add(fight, "player_kills", "Players killed", Format.COUNT, Items.SKELETON_SKULL, custom(stats, Stats.PLAYER_KILLS));
        e.add(fight, "deaths", "Deaths", Format.COUNT, Items.BONE, custom(stats, Stats.DEATHS));
        e.add(fight, "damage_dealt", "Damage dealt", Format.DAMAGE, Items.DIAMOND_SWORD, custom(stats, Stats.DAMAGE_DEALT));
        e.add(fight, "damage_taken", "Damage taken", Format.DAMAGE, Items.IRON_CHESTPLATE, custom(stats, Stats.DAMAGE_TAKEN));
        e.add(fight, "blocked", "Blocked with a shield", Format.DAMAGE, Items.SHIELD, custom(stats, Stats.DAMAGE_BLOCKED_BY_SHIELD));
        e.add(fight, "raids", "Raids won", Format.COUNT, Items.CROSSBOW, custom(stats, Stats.RAID_WIN));

        out.add("mined", top(mined));
        out.add("killed", top(counts(stats, Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE)));
        out.add("killed_by", top(counts(stats, Stats.ENTITY_KILLED_BY, BuiltInRegistries.ENTITY_TYPE)));
        out.add("crafts", top(counts(stats, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM)));
        if (e.player == null) {
            out.add("entries", e.array);
        }
    }

    private static long custom(StatsCounter stats, Identifier stat) {
        return stats.getValue(Stats.CUSTOM.get(stat));
    }

    private static <T> long total(StatsCounter stats, StatType<T> type, Registry<T> registry) {
        long sum = 0;
        for (T value : registry) {
            sum += stats.getValue(type, value);
        }
        return sum;
    }

    private static <T> Map<Identifier, Long> counts(StatsCounter stats, StatType<T> type, Registry<T> registry) {
        Map<Identifier, Long> counts = new HashMap<>();
        for (T value : registry) {
            int count = stats.getValue(type, value);
            if (count > 0) {
                counts.put(registry.getKey(value), (long) count);
            }
        }
        return counts;
    }

    /** The biggest few, as [[id, count], ...]. */
    private static JsonArray top(Map<Identifier, Long> counts) {
        JsonArray array = new JsonArray();
        counts.entrySet().stream()
                .sorted(Map.Entry.<Identifier, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP)
                .forEach(entry -> {
                    JsonArray pair = new JsonArray();
                    pair.add(entry.getKey().toString());
                    pair.add(entry.getValue());
                    array.add(pair);
                });
        return array;
    }

    // ------------------------------------------------------------------ payloads

    /** Client to server: send me the board. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(SkyRpgCore.id("records_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: every player's statistics, as JSON. */
    public record Board(String json) implements CustomPacketPayload {
        public static final Type<Board> TYPE = new Type<>(SkyRpgCore.id("records_board"));
        public static final StreamCodec<ByteBuf, Board> CODEC = ByteBufCodecs.stringUtf8(1 << 20).map(Board::new, Board::json);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
