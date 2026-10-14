package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.world.MobLevelling;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Deep: the overworld's caves in four stacked layers (Glimmer Caverns, Fungal Depths, Dwarven Halls, Magma Rifts),
 * each with its own cave surfaces and decorations (see {@link DeepLayerFeature}), a monster level bonus, its own
 * monsters roaming near players in dark cave air, drops from player kills, ambient particles and an arrival title.
 * Layers are data ({@code skyrpg/deep_layers/*.json}, written by tools/builds/deep_layers.py). See
 * docs/design/the_deep.md.
 *
 * <p>Out here, "in a layer" means the overworld, inside the layer's height band ({@code min_y} inclusive, {@code max_y}
 * exclusive) and out of sight of the sky, so a valley floor at y 30 is not the Glimmer Caverns. World generation only
 * looks at the height (and skips vanilla cave biomes).
 */
public final class DeepLayers {
    public static final String DIRECTORY = "skyrpg/deep_layers";
    static final String MOB_TAG = "skyrpg_layer_mob";
    /** How far past a layer's edge a player goes before their layer changes, so a border doesn't flicker titles. */
    private static final int HYSTERESIS = 2;
    private static final int SPAWN_INTERVAL = 60;
    /** The same layer's title shows again only after this many ticks. */
    private static final int TITLE_COOLDOWN = 20 * 60;
    private static final int SPAWN_RADIUS = 40;

    /** Which side of a cave a surface block faces: air above it, beside it, or below it. */
    enum Surface {
        FLOOR, WALL, CEILING;

        static Surface parse(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "ceiling" -> CEILING;
                case "wall" -> WALL;
                default -> FLOOR;
            };
        }
    }

    /** One decoration rule (see tools/builds/deep_layers.py for the fields). */
    record Decoration(String type, Surface on, BlockState block, BlockState alt, int min, int max, double density, double chance,
                      int heightMin, int heightMax, int radiusMin, int radiusMax) {
    }

    record Layer(String id, String name, int color, int minY, int maxY, int danger, int levelBonus, String description,
                 Set<Block> replaceBlocks, List<TagKey<Block>> replaceTags, BlockState[][] palette, int[][] weights, double threshold,
                 List<Decoration> decorations, List<JsonObject> mobs, int maxMobs, int maxLight, List<JsonObject> drops,
                 ParticleOptions particle, int particleCount) {
        boolean contains(int y) {
            return y >= minY && y < maxY;
        }

        String skulls() {
            return "☠".repeat(Math.max(1, danger));
        }

        /** Whether this layer may restyle (and decorate) a cave surface made of this block. */
        boolean replaceable(BlockState state) {
            if (replaceBlocks.contains(state.getBlock())) {
                return true;
            }
            for (TagKey<Block> tag : replaceTags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        }

        /** The palette block for a surface, picked by a hash (so neighbours sharing a hash share a block), or null. */
        BlockState material(Surface surface, long hash) {
            BlockState[] options = palette[surface.ordinal()];
            if (options.length == 0) {
                return null;
            }
            int[] cumulative = weights[surface.ordinal()];
            int pick = (int) Math.floorMod(hash, (long) cumulative[cumulative.length - 1]);
            for (int i = 0; i < options.length; i++) {
                if (pick < cumulative[i]) {
                    return options[i];
                }
            }
            return options[options.length - 1];
        }
    }

    private static volatile List<Layer> layers = List.of();
    private static volatile int lowest = Integer.MAX_VALUE;
    private static volatile int highest = Integer.MIN_VALUE;
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    /** Per player: the last layer title shown, and when. */
    private static final Map<UUID, String> TITLED = new HashMap<>();
    private static final Map<UUID, Integer> TITLED_AT = new HashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private DeepLayers() {
    }

    static void init() {
        MobLevelling.registerBonus((level, pos) -> {
            if (level.dimension() != Level.OVERWORLD) {
                return 0;
            }
            Layer layer = at(pos.getY());
            if (layer == null) {
                return 0;
            }
            // Mobs that spawn while chunks generate come off the server thread, where the sky can't be checked safely:
            // the height alone counts for them.
            if (level.getServer().isSameThread() && level.canSeeSky(pos)) {
                return 0;
            }
            return layer.levelBonus();
        });
        ServerTickEvents.END_SERVER_TICK.register(DeepLayers::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob && mob instanceof Enemy && mob.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                    && source.getEntity() instanceof ServerPlayer && !mob.entityTags().contains(MobTags.FARMED)) {
                Layer layer = underground(level, mob.blockPosition());
                if (layer != null) {
                    drops(level, mob, layer);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            INSIDE.remove(handler.player.getUUID());
            TITLED.remove(handler.player.getUUID());
            TITLED_AT.remove(handler.player.getUUID());
        });
    }

    // ---------------------------------------------------------------- data

    static void load(Map<Identifier, JsonElement> files) {
        List<Layer> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                loaded.add(parse(id.getPath(), element.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Skipping deep layer {}: {}", id, e.getMessage());
            }
        });
        loaded.sort(Comparator.comparingInt(Layer::maxY).reversed());
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Layer layer : loaded) {
            low = Math.min(low, layer.minY());
            high = Math.max(high, layer.maxY());
        }
        lowest = low;
        highest = high;
        layers = List.copyOf(loaded);
        WARNED.clear();
    }

    private static Layer parse(String id, JsonObject json) {
        Set<Block> replaceBlocks = new HashSet<>();
        List<TagKey<Block>> replaceTags = new ArrayList<>();
        JsonArray replace = json.has("replace") ? json.getAsJsonArray("replace") : new JsonArray();
        if (replace.isEmpty()) {
            replace.add("#minecraft:base_stone_overworld");
        }
        for (JsonElement entry : replace) {
            String name = entry.getAsString();
            if (name.startsWith("#")) {
                replaceTags.add(TagKey.create(Registries.BLOCK, Identifier.parse(name.substring(1))));
            } else {
                Block block = block(id, name);
                if (block != null) {
                    replaceBlocks.add(block);
                }
            }
        }
        BlockState[][] palette = new BlockState[Surface.values().length][];
        int[][] weights = new int[Surface.values().length][];
        JsonObject paletteJson = json.has("palette") ? json.getAsJsonObject("palette") : new JsonObject();
        for (Surface surface : Surface.values()) {
            String key = surface.name().toLowerCase(Locale.ROOT);
            JsonArray options = paletteJson.has(key) ? paletteJson.getAsJsonArray(key)
                    : paletteJson.has("any") ? paletteJson.getAsJsonArray("any") : new JsonArray();
            List<BlockState> states = new ArrayList<>();
            List<Integer> cumulative = new ArrayList<>();
            int total = 0;
            for (JsonElement option : options) {
                JsonObject object = option.getAsJsonObject();
                Block block = block(id, Dungeons.str(object, "block", "minecraft:air"));
                if (block == null) {
                    continue;
                }
                total += Math.max(1, (int) Dungeons.num(object, "weight", 1));
                states.add(block.defaultBlockState());
                cumulative.add(total);
            }
            palette[surface.ordinal()] = states.toArray(BlockState[]::new);
            weights[surface.ordinal()] = cumulative.stream().mapToInt(Integer::intValue).toArray();
        }
        List<Decoration> decorations = new ArrayList<>();
        for (JsonObject object : objects(json, "decorations")) {
            Block block = block(id, Dungeons.str(object, "block", "minecraft:air"));
            if (block == null) {
                continue;
            }
            String altName = Dungeons.str(object, "tip", Dungeons.str(object, "chain", Dungeons.str(object, "stem", Dungeons.str(object, "rim", ""))));
            Block alt = altName.isEmpty() ? null : block(id, altName);
            int[] count = range(object, "count", 0, 0);
            int[] height = range(object, "height", 2, 4);
            int[] radius = range(object, "radius", 1, 2);
            decorations.add(new Decoration(Dungeons.str(object, "type", "place"), Surface.parse(Dungeons.str(object, "on", "floor")),
                    block.defaultBlockState(), alt == null ? null : alt.defaultBlockState(), count[0], count[1],
                    Dungeons.num(object, "density", 0), Dungeons.num(object, "chance", 1), height[0], height[1], radius[0], radius[1]));
        }
        ParticleOptions particle = null;
        if (json.has("particle")) {
            particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.parse(json.get("particle").getAsString()))
                    .filter(type -> type instanceof SimpleParticleType).map(type -> (ParticleOptions) type).orElse(null);
        }
        String color = Dungeons.str(json, "color", "#B89CFF").replace("#", "");
        double coverage = Math.clamp(Dungeons.num(json, "coverage", 0.6), 0.0, 1.0);
        return new Layer(id, Dungeons.str(json, "name", id), Integer.parseInt(color, 16), (int) Dungeons.num(json, "min_y", 0),
                (int) Dungeons.num(json, "max_y", 40), (int) Dungeons.num(json, "danger", 1), (int) Dungeons.num(json, "level_bonus", 0),
                Dungeons.str(json, "description", ""), Set.copyOf(replaceBlocks), List.copyOf(replaceTags), palette, weights,
                DeepLayerFeature.threshold(coverage), List.copyOf(decorations), objects(json, "mobs"), (int) Dungeons.num(json, "max_mobs", 6),
                (int) Dungeons.num(json, "max_light", 7), objects(json, "drops"), particle, (int) Dungeons.num(json, "particle_count", 6));
    }

    private static Block block(String layer, String name) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name)).orElse(null);
        if (block == null) {
            SkyRpgWorld.LOGGER.warn("Deep layer {}: unknown block {}", layer, name);
        }
        return block;
    }

    private static int[] range(JsonObject json, String key, int min, int max) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray range = json.getAsJsonArray(key);
            min = range.get(0).getAsInt();
            max = Math.max(min, range.get(1).getAsInt());
        } else if (json.has(key)) {
            min = max = json.get(key).getAsInt();
        }
        return new int[] {min, max};
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(e -> {
                if (e.isJsonObject()) {
                    list.add(e.getAsJsonObject());
                }
            });
        }
        return List.copyOf(list);
    }

    // ---------------------------------------------------------------- where layers are

    /** Every layer, top first. Safe from world generation threads. */
    static List<Layer> layers() {
        return layers;
    }

    static int lowestY() {
        return lowest;
    }

    static int highestY() {
        return highest;
    }

    /** The layer whose band holds this height, or null (the height alone: no sky or dimension check). */
    static Layer at(int y) {
        for (Layer layer : layers) {
            if (layer.contains(y)) {
                return layer;
            }
        }
        return null;
    }

    static Layer byId(String id) {
        for (Layer layer : layers) {
            if (layer.id().equals(id)) {
                return layer;
            }
        }
        return null;
    }

    /** The layer at this overworld spot, if it's underground (out of the sky's sight); server thread only. */
    static Layer underground(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        Layer layer = at(pos.getY());
        return layer == null || level.canSeeSky(pos) ? null : layer;
    }

    // ---------------------------------------------------------------- the deep reacting to players

    private static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 4 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSpectator() && !player.isCreative()) {
                    SporePuffballBlock.near(player);
                }
            }
        }
        if (tick % 10 != 0 || layers.isEmpty()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Layer layer = player.level() == overworld ? current(overworld, player) : null;
            String before = INSIDE.get(player.getUUID());
            if (layer == null) {
                INSIDE.remove(player.getUUID());
                continue;
            }
            INSIDE.put(player.getUUID(), layer.id());
            boolean survival = !player.isSpectator() && !player.isCreative();
            if (!layer.id().equals(before) && survival) {
                enter(player, layer, tick);
            }
            ambience(overworld, player, layer);
            if (survival && tick % SPAWN_INTERVAL == 0) {
                spawn(overworld, player, layer);
            }
        }
    }

    /** The player's layer, keeping the one they're in until they're a couple of blocks past its edge. */
    private static Layer current(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        if (level.canSeeSky(pos)) {
            return null;
        }
        String before = INSIDE.get(player.getUUID());
        Layer previous = before == null ? null : byId(before);
        int y = pos.getY();
        if (previous != null && y >= previous.minY() - HYSTERESIS && y < previous.maxY() + HYSTERESIS) {
            return previous;
        }
        return at(y);
    }

    private static void enter(ServerPlayer player, Layer layer, int tick) {
        UUID uuid = player.getUUID();
        Integer shown = TITLED_AT.get(uuid);
        if (layer.id().equals(TITLED.get(uuid)) && shown != null && tick - shown < TITLE_COOLDOWN) {
            return;
        }
        TITLED.put(uuid, layer.id());
        TITLED_AT.put(uuid, tick);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(layer.name()).withColor(layer.color()).withStyle(ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(layer.skulls()).withStyle(ChatFormatting.RED)
                .append(Component.literal(" Monsters +" + layer.levelBonus() + " levels").withStyle(ChatFormatting.GRAY))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8f, 0.8f);
    }

    private static void ambience(ServerLevel level, ServerPlayer player, Layer layer) {
        if (layer.particle() != null && layer.particleCount() > 0) {
            level.sendParticles(player, layer.particle(), false, false, player.getX(), player.getY() + 1.5, player.getZ(), layer.particleCount(),
                    8, 3, 8, 0.01);
        }
    }

    /** A group from the layer's monsters, in dark cave air 10-24 blocks from the player, up to the layer's cap. */
    static boolean spawn(ServerLevel level, ServerPlayer player, Layer layer) {
        if (layer.mobs().isEmpty()) {
            return false;
        }
        AABB around = player.getBoundingBox().inflate(SPAWN_RADIUS, 16, SPAWN_RADIUS);
        if (level.getEntitiesOfClass(Mob.class, around, mob -> mob.entityTags().contains(MOB_TAG)).size() >= layer.maxMobs()) {
            return false;
        }
        RandomSource random = level.getRandom();
        JsonObject entry = pick(layer, random);
        if (entry == null) {
            return false;
        }
        EntityType<?> entityType = type(entry);
        BlockPos feet = spot(level, player, layer, random);
        if (feet == null) {
            return false;
        }
        int[] count = range(entry, "count", 1, 1);
        int total = count[0] + random.nextInt(count[1] - count[0] + 1);
        boolean any = false;
        for (int i = 0; i < total; i++) {
            Entity entity = entityType.create(level, EntitySpawnReason.EVENT);
            if (!(entity instanceof Mob mob)) {
                if (entity != null) {
                    entity.discard();
                }
                return any;
            }
            double spread = i == 0 ? 0 : 1.2;
            mob.snapTo(feet.getX() + 0.5 + (random.nextDouble() * 2 - 1) * spread, feet.getY(), feet.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * spread,
                    random.nextFloat() * 360, 0);
            if (!level.noCollision(mob)) {
                mob.discard();
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.EVENT, null);
            MobSpawning.dress(mob, entry);
            mob.addTag(MOB_TAG);
            level.addFreshEntity(mob);
            any = true;
        }
        return any;
    }

    /** A weighted mob entry whose type exists (other mods' monsters may be missing: those are skipped, warned once). */
    private static JsonObject pick(Layer layer, RandomSource random) {
        int total = 0;
        for (JsonObject entry : layer.mobs()) {
            if (type(entry) != null) {
                total += Math.max(1, (int) Dungeons.num(entry, "weight", 1));
            }
        }
        if (total == 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (JsonObject entry : layer.mobs()) {
            if (type(entry) == null) {
                continue;
            }
            pick -= Math.max(1, (int) Dungeons.num(entry, "weight", 1));
            if (pick < 0) {
                return entry;
            }
        }
        return null;
    }

    private static EntityType<?> type(JsonObject entry) {
        String id = Dungeons.str(entry, "type", "minecraft:zombie");
        Identifier parsed = Identifier.tryParse(id);
        EntityType<?> type = parsed == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
        if (type == null && WARNED.add(id)) {
            SkyRpgWorld.LOGGER.warn("Deep layers: no monster {} (skipped until it exists)", id);
        }
        return type;
    }

    /** Standing room on solid ground, inside the layer, dark enough and out of the sky's sight; null if none found. */
    private static BlockPos spot(ServerLevel level, ServerPlayer player, Layer layer, RandomSource random) {
        int py = player.getBlockY();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 10 + random.nextDouble() * 14;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, py, z))) {
                continue;
            }
            for (int step = 0; step <= 16; step++) {
                int y = py + ((step & 1) == 0 ? step / 2 : -(step + 1) / 2);
                if (!layer.contains(y)) {
                    continue;
                }
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos below = feet.below();
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                        || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(below).isEmpty()) {
                    continue;
                }
                if (level.getBrightness(LightLayer.BLOCK, feet) > layer.maxLight() || level.canSeeSky(feet)) {
                    break;
                }
                return feet;
            }
        }
        return null;
    }

    /** Monsters a player kills in a layer roll its drops. */
    private static void drops(ServerLevel level, Mob mob, Layer layer) {
        RandomSource random = level.getRandom();
        for (JsonObject drop : layer.drops()) {
            if (random.nextDouble() >= Dungeons.num(drop, "chance", 0.1)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(Dungeons.str(drop, "item", "minecraft:air"))).orElse(null);
            if (item == null) {
                continue;
            }
            int[] count = range(drop, "count", 1, 1);
            ItemEntity entity = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    new ItemStack(item, count[0] + random.nextInt(count[1] - count[0] + 1)));
            if (Dungeons.bool(drop, "rare", false)) {
                entity.setGlowingTag(true);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
            level.addFreshEntity(entity);
        }
    }

    /** /layer: where you are in the deep. */
    static List<String> describe(ServerLevel level, BlockPos pos) {
        List<String> lines = new ArrayList<>();
        Layer here = underground(level, pos);
        if (here != null) {
            lines.add("You're in " + here.name() + " " + here.skulls() + " (y " + here.minY() + " to " + here.maxY() + "): monsters +"
                    + here.levelBonus() + " levels. " + here.description());
        } else if (level.dimension() == Level.OVERWORLD && at(pos.getY()) != null) {
            lines.add("You're under the open sky, so not in " + at(pos.getY()).name() + ".");
        } else {
            lines.add("You're not in the deep.");
        }
        for (Layer layer : layers) {
            lines.add(" - " + layer.name() + " " + layer.skulls() + ": y " + layer.minY() + " to " + layer.maxY() + ", +" + layer.levelBonus()
                    + " levels");
        }
        return lines;
    }
}
