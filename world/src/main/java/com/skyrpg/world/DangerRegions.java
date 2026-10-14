package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.core.world.MobLevelling;
import com.skyrpg.core.world.MobTags;
import com.skyrpg.core.world.Regions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Danger regions: big blobs of the overworld, placed from the world seed, where monsters are many levels stronger,
 * the land itself is corrupted (see {@link DangerRegionFeature}), regional monsters roam and drop regional materials,
 * and maps show red. Region types are data ({@code skyrpg/danger_regions/*.json}); which type a region is depends on
 * the biome at its centre. See docs/design/danger_regions.md.
 *
 * <p>The world is cut into {@value #CELL}-block cells; each cell may hold one region. Everything about a cell's
 * region comes from the seed, so the server can answer "which region is here?" for any position, loaded or not,
 * including from world generation threads.
 */
public final class DangerRegions {
    public static final String DIRECTORY = "skyrpg/danger_regions";
    static final int CELL = 768;
    private static final double CHANCE = 0.24;
    /** No region within this many blocks of world spawn. */
    private static final int SAFE_SPAWN = 450;
    static final String MOB_TAG = "skyrpg_region_mob";
    private static final int SYNC_CELLS = 2;
    /** The red wash every danger region gets on the map. */
    public static final int MAP_RED = 0xC0283C;

    /** One kind of region, from data. */
    record Type(String id, String name, int color, List<String> biomes, int weight, int levelBonus, int danger, String description,
                Map<Block, Block> palette, Map<TagKey<Block>, Block> tagPalette, List<JsonObject> decorations, List<JsonObject> mobs,
                int maxMobs, List<JsonObject> drops, ParticleOptions particle, ParticleOptions nightParticle, Identifier boss, Identifier sigil,
                JsonObject shrine, int fog, int fogDensity) {
        /** What this region turns a block into, or null to leave it (properties carried over where they match). */
        BlockState convert(BlockState state) {
            Block to = palette.get(state.getBlock());
            if (to == null) {
                for (Map.Entry<TagKey<Block>, Block> entry : tagPalette.entrySet()) {
                    if (state.is(entry.getKey())) {
                        to = entry.getValue();
                        break;
                    }
                }
            }
            return to == null || to == state.getBlock() ? null : carry(state, to.defaultBlockState());
        }
    }

    private static volatile Map<String, Type> types = Map.of();
    /** Per seed: each cell's region (or none). */
    private static final Map<Long, Optional<RegionShape>> CELLS = new ConcurrentHashMap<>();
    private static volatile long cachedSeed;
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final Map<UUID, Long> SYNCED_CELL = new HashMap<>();

    private DangerRegions() {
    }

    static void init() {
        Regions.install((level, x, z) -> {
            RegionShape region = level.dimension() == Level.OVERWORLD ? at(level, x, z) : null;
            return region == null ? null : region.type();
        });
        MobLevelling.registerBonus((level, pos) -> {
            RegionShape region = level.dimension() == Level.OVERWORLD ? at(level, pos.getX(), pos.getZ()) : null;
            return region == null ? 0 : region.levelBonus();
        });
        ServerTickEvents.END_SERVER_TICK.register(DangerRegions::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob && mob instanceof Enemy && mob.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                    && source.getEntity() instanceof ServerPlayer && !mob.entityTags().contains(MobTags.FARMED)) {
                drops(level, mob);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SYNCED_CELL.remove(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            INSIDE.remove(handler.player.getUUID());
            SYNCED_CELL.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CELLS.clear());
    }

    // ---------------------------------------------------------------- data

    static void load(Map<Identifier, JsonElement> files) {
        Map<String, Type> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                List<String> biomes = new ArrayList<>();
                json.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
                Map<Block, Block> palette = new HashMap<>();
                Map<TagKey<Block>, Block> tagPalette = new java.util.LinkedHashMap<>();
                if (json.has("palette")) {
                    for (var entry : json.getAsJsonObject("palette").entrySet()) {
                        Block to = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(entry.getValue().getAsString())).orElse(null);
                        if (to == null) {
                            SkyRpgWorld.LOGGER.warn("Danger region {}: unknown block {}", id, entry.getValue());
                            continue;
                        }
                        if (entry.getKey().startsWith("#")) {
                            tagPalette.put(TagKey.create(Registries.BLOCK, Identifier.parse(entry.getKey().substring(1))), to);
                        } else {
                            BuiltInRegistries.BLOCK.getOptional(Identifier.parse(entry.getKey())).ifPresent(from -> palette.put(from, to));
                        }
                    }
                }
                ParticleOptions particle = null;
                if (json.has("particle")) {
                    particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.parse(json.get("particle").getAsString()))
                            .filter(type -> type instanceof SimpleParticleType).map(type -> (ParticleOptions) type).orElse(null);
                }
                ParticleOptions nightParticle = null;
                if (json.has("night_particle")) {
                    nightParticle = BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.parse(json.get("night_particle").getAsString()))
                            .filter(type -> type instanceof SimpleParticleType).map(type -> (ParticleOptions) type).orElse(null);
                }
                int fog = Integer.parseInt(Dungeons.str(json, "fog", "#000000").replace("#", ""), 16);
                String color = Dungeons.str(json, "color", "#C0283C").replace("#", "");
                loaded.put(id.getPath(), new Type(id.getPath(), Dungeons.str(json, "name", id.getPath()), Integer.parseInt(color, 16), List.copyOf(biomes),
                        (int) Dungeons.num(json, "weight", 1), (int) Dungeons.num(json, "level_bonus", 10), (int) Dungeons.num(json, "danger", 2),
                        Dungeons.str(json, "description", ""), Map.copyOf(palette), tagPalette, objects(json, "decorations"), objects(json, "mobs"),
                        (int) Dungeons.num(json, "max_mobs", 8), objects(json, "drops"), particle, nightParticle,
                        json.has("boss") ? Identifier.parse(json.get("boss").getAsString()) : null,
                        json.has("sigil") ? Identifier.parse(json.get("sigil").getAsString()) : null,
                        json.has("shrine") && json.get("shrine").isJsonObject() ? json.getAsJsonObject("shrine") : new JsonObject(), fog,
                        (int) Math.round(Dungeons.num(json, "fog_density", 0) * 100)));
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Skipping danger region {}: {}", id, e.getMessage());
            }
        });
        types = loaded;
        CELLS.clear();
        SYNCED_CELL.clear();
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

    static Type type(String id) {
        return types.get(id);
    }

    // ---------------------------------------------------------------- where regions are

    /** The danger region at this overworld spot, or null. Safe to call from world generation threads. */
    public static RegionShape at(ServerLevel level, int x, int z) {
        long seed = level.getSeed();
        if (seed != cachedSeed) {
            CELLS.clear();
            cachedSeed = seed;
        }
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        RegionShape best = null;
        double bestDepth = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                RegionShape region = cell(level, cellX + dx, cellZ + dz);
                if (region != null) {
                    double depth = region.depth(x, z);
                    if (depth > bestDepth) {
                        best = region;
                        bestDepth = depth;
                    }
                }
            }
        }
        return best;
    }

    /** The region a cell holds (deterministic from the seed and the biome at its centre), or null. */
    static RegionShape cell(ServerLevel level, int cellX, int cellZ) {
        long key = (long) cellX << 32 | (cellZ & 0xFFFFFFFFL);
        return CELLS.computeIfAbsent(key, k -> Optional.ofNullable(roll(level, cellX, cellZ))).orElse(null);
    }

    private static RegionShape roll(ServerLevel level, int cellX, int cellZ) {
        if (types.isEmpty()) {
            return null;
        }
        RandomSource random = RandomSource.create(mix(level.getSeed() ^ 0x5D3A7F21C4E1L, cellX, cellZ));
        if (random.nextDouble() >= CHANCE) {
            return null;
        }
        int cx = cellX * CELL + CELL / 4 + random.nextInt(CELL / 2);
        int cz = cellZ * CELL + CELL / 4 + random.nextInt(CELL / 2);
        BlockPos spawn = level.getRespawnData().pos();
        if (Math.hypot(cx - spawn.getX(), cz - spawn.getZ()) < SAFE_SPAWN) {
            return null;
        }
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource()
                .createUncachedResolver(level.getChunkSource().randomState())
                .getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(80), QuartPos.fromBlock(cz));
        List<Type> candidates = new ArrayList<>();
        int total = 0;
        for (Type type : types.values()) {
            if (matches(biome, type.biomes())) {
                candidates.add(type);
                total += Math.max(1, type.weight());
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int pick = random.nextInt(total);
        Type chosen = candidates.getLast();
        for (Type type : candidates) {
            pick -= Math.max(1, type.weight());
            if (pick < 0) {
                chosen = type;
                break;
            }
        }
        int radius = 150 + random.nextInt(81);
        return new RegionShape(chosen.id(), chosen.name(), chosen.color(), chosen.danger(), chosen.levelBonus(), cx, cz, radius,
                random.nextFloat() * 6.283f, random.nextFloat() * 6.283f, random.nextFloat() * 6.283f, chosen.fog(), chosen.fogDensity());
    }

    private static boolean matches(Holder<Biome> biome, List<String> biomes) {
        for (String entry : biomes) {
            if (entry.startsWith("#")) {
                if (biome.is(TagKey.create(Registries.BIOME, Identifier.parse(entry.substring(1))))) {
                    return true;
                }
            } else if (biome.is(ResourceKey.create(Registries.BIOME, Identifier.parse(entry)))) {
                return true;
            }
        }
        return false;
    }

    private static long mix(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    /** Regions in the cells around a spot (for the client's map, and /region list). */
    static List<RegionShape> around(ServerLevel level, int x, int z, int cells) {
        List<RegionShape> found = new ArrayList<>();
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                RegionShape region = cell(level, cellX + dx, cellZ + dz);
                if (region != null) {
                    found.add(region);
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- the world reacting to players

    private static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 10 != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != overworld) {
                if (INSIDE.remove(player.getUUID()) != null) {
                    SYNCED_CELL.remove(player.getUUID());
                }
                continue;
            }
            RegionShape region = at(overworld, player.getBlockX(), player.getBlockZ());
            String id = region == null ? null : region.key();
            String before = INSIDE.get(player.getUUID());
            if (!java.util.Objects.equals(id, before)) {
                if (region != null) {
                    INSIDE.put(player.getUUID(), id);
                    enter(player, region);
                } else {
                    INSIDE.remove(player.getUUID());
                    player.sendOverlayMessage(Component.literal("You leave the danger zone").withStyle(ChatFormatting.GRAY));
                }
            }
            if (tick % 20 == 0) {
                sync(player, overworld);
            }
            if (region != null) {
                Type type = types.get(region.type());
                if (type != null) {
                    ambience(overworld, player, type);
                    // A Blood Moon doubles the region's horde: twice the cap, twice as often.
                    boolean moon = BloodMoon.active();
                    if (tick % (moon ? 30 : 60) == 0 && !player.isSpectator() && !player.isCreative()) {
                        spawn(overworld, player, region, type, moon ? 2 : 1);
                    }
                }
            }
        }
    }

    private static void enter(ServerPlayer player, RegionShape region) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("⚠ " + region.name()).withColor(region.color())
                .withStyle(ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Danger " + region.skulls()).withStyle(ChatFormatting.RED)
                .append(Component.literal("  ·  Monsters +" + region.levelBonus() + " levels").withStyle(ChatFormatting.GRAY))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(),
                net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 0.7f);
    }

    /** Sends the regions around the player whenever they move into another cell. */
    private static void sync(ServerPlayer player, ServerLevel level) {
        long cell = (long) Math.floorDiv(player.getBlockX(), CELL) << 32 | (Math.floorDiv(player.getBlockZ(), CELL) & 0xFFFFFFFFL);
        Long synced = SYNCED_CELL.get(player.getUUID());
        if (synced != null && synced == cell) {
            return;
        }
        SYNCED_CELL.put(player.getUUID(), cell);
        if (ServerPlayNetworking.canSend(player, WorldPayloads.Regions.TYPE)) {
            ServerPlayNetworking.send(player, new WorldPayloads.Regions(level.dimension().identifier().toString(),
                    around(level, player.getBlockX(), player.getBlockZ(), SYNC_CELLS)));
        }
    }

    private static void ambience(ServerLevel level, ServerPlayer player, Type type) {
        if (type.particle() != null) {
            level.sendParticles(player, type.particle(), false, false, player.getX(), player.getY() + 1.5, player.getZ(), 6, 8, 3, 8, 0.01);
        }
        // Will-o-the-wisps: pale lights that bob a little above the ground, at night.
        if (type.nightParticle() != null && level.isDarkOutside()) {
            level.sendParticles(player, type.nightParticle(), false, false, player.getX(), player.getY() + 0.8, player.getZ(), 2, 14, 1.2, 14, 0.002);
        }
    }

    /** Regional monsters roam around players inside, up to the type's cap. */
    private static void spawn(ServerLevel level, ServerPlayer player, RegionShape region, Type type, int capFactor) {
        if (type.mobs().isEmpty()) {
            return;
        }
        AABB around = player.getBoundingBox().inflate(48, 24, 48);
        int nearby = level.getEntitiesOfClass(Mob.class, around, mob -> mob.entityTags().contains(MOB_TAG)).size();
        if (nearby >= type.maxMobs() * capFactor) {
            return;
        }
        RandomSource random = level.getRandom();
        JsonObject entry = weighted(type.mobs(), random);
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(Dungeons.str(entry, "type", "minecraft:zombie"))).orElse(null);
        if (entityType == null) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 18 + random.nextDouble() * 18;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z)) || !region.contains(x, z)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.getFluidState(feet.below()).isEmpty() && !Dungeons.bool(entry, "water", false)) {
            return;
        }
        int min = 1;
        int max = 1;
        if (entry.has("count") && entry.get("count").isJsonArray()) {
            JsonArray count = entry.getAsJsonArray("count");
            min = count.get(0).getAsInt();
            max = Math.max(min, count.get(1).getAsInt());
        }
        int count = min + random.nextInt(max - min + 1);
        for (int i = 0; i < count; i++) {
            Entity entity = entityType.create(level, EntitySpawnReason.EVENT);
            if (!(entity instanceof Mob mob)) {
                if (entity != null) {
                    entity.discard();
                }
                return;
            }
            mob.snapTo(x + 0.5 + random.nextDouble() * 2 - 1, y, z + 0.5 + random.nextDouble() * 2 - 1, random.nextFloat() * 360, 0);
            if (!level.noCollision(mob)) {
                mob.discard();
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.EVENT, null);
            MobSpawning.dress(mob, entry);
            mob.addTag(MOB_TAG);
            if (BloodMoon.active()) {
                mob.addTag("skyrpg_bloodbound");
            }
            if (entry.has("tags") && entry.get("tags").isJsonArray()) {
                entry.getAsJsonArray("tags").forEach(tag -> mob.addTag(tag.getAsString()));
            }
            if (entry.has("speed")) {
                AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(speed.getBaseValue() * entry.get("speed").getAsDouble());
                }
            }
            if (Dungeons.bool(entry, "fireproof", false)) {
                mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
            level.addFreshEntity(mob);
            if (Dungeons.bool(entry, "rise", false)) {
                // Restless Dead dig themselves out of the ground.
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), mob.getX(), mob.getY() + 0.2,
                        mob.getZ(), 24, 0.4, 0.2, 0.4, 0.1);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ROOTED_DIRT_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 0.6f);
            }
        }
    }

    private static JsonObject weighted(List<JsonObject> entries, RandomSource random) {
        int total = 0;
        for (JsonObject entry : entries) {
            total += Math.max(1, (int) Dungeons.num(entry, "weight", 1));
        }
        int pick = random.nextInt(Math.max(1, total));
        for (JsonObject entry : entries) {
            pick -= Math.max(1, (int) Dungeons.num(entry, "weight", 1));
            if (pick < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    /** Monsters killed inside a region drop its materials. */
    private static void drops(ServerLevel level, Mob mob) {
        RegionShape region = at(level, mob.getBlockX(), mob.getBlockZ());
        Type type = region == null ? null : types.get(region.type());
        if (type == null) {
            return;
        }
        RandomSource random = level.getRandom();
        // A Blood Moon doubles the drops: every roll is made twice.
        for (JsonObject drop : BloodMoon.active() ? twice(type.drops()) : type.drops()) {
            if (random.nextDouble() >= Dungeons.num(drop, "chance", 0.1)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(Dungeons.str(drop, "item", "minecraft:air"))).orElse(null);
            if (item == null) {
                continue;
            }
            int min = 1;
            int max = 1;
            if (drop.has("count") && drop.get("count").isJsonArray()) {
                min = drop.getAsJsonArray("count").get(0).getAsInt();
                max = Math.max(min, drop.getAsJsonArray("count").get(1).getAsInt());
            }
            ItemEntity entity = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(item, min + random.nextInt(max - min + 1)));
            if (Dungeons.bool(drop, "rare", false)) {
                entity.setGlowingTag(true);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE,
                        1.5f, 0.6f);
            }
            level.addFreshEntity(entity);
        }
    }

    private static List<JsonObject> twice(List<JsonObject> drops) {
        List<JsonObject> doubled = new ArrayList<>(drops);
        doubled.addAll(drops);
        return doubled;
    }

    /** Every region type. */
    static java.util.Collection<Type> allTypes() {
        return types.values();
    }

    /** The heart of a region: where its shrine stands and its boss wakes (the ground there, from the loaded chunk or the generator). */
    static BlockPos heart(ServerLevel level, RegionShape region) {
        // Three blocks east of the altar, on the platform (the same height the shrine was built at).
        BlockPos column = new BlockPos(region.cx() + 3, 0, region.cz());
        int y;
        if (level.hasChunkAt(column)) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, region.cx() + 3, region.cz());
        } else {
            y = level.getChunkSource().getGenerator().getBaseHeight(region.cx(), region.cz(), Heightmap.Types.WORLD_SURFACE_WG, level,
                    level.getChunkSource().randomState());
        }
        return new BlockPos(region.cx(), y, region.cz());
    }

    /** The heart of a region (see {@link #heart}), for other packages. */
    public static BlockPos heartOf(ServerLevel level, RegionShape region) {
        return heart(level, region);
    }

    /** The nearest region of this type to (x, z) within {@code cells} cells, or null. */
    static RegionShape nearest(ServerLevel level, String typeId, int x, int z, int cells) {
        RegionShape best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RegionShape region : around(level, x, z, cells)) {
            double distance = Math.hypot(region.cx() - x, region.cz() - z);
            if (region.type().equals(typeId) && distance < bestDistance) {
                best = region;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A state of {@code to} with every property {@code from} shares (leaves keep their distance, logs their axis). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState carry(BlockState from, BlockState to) {
        for (Property property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = to.setValue(property, from.getValue(property));
            }
        }
        return to;
    }

    /** /region here and /region list. */
    static List<String> describe(ServerLevel level, int x, int z, int cells) {
        List<String> lines = new ArrayList<>();
        for (RegionShape region : around(level, x, z, cells)) {
            long distance = Math.round(Math.hypot(region.cx() - x, region.cz() - z));
            lines.add(region.name() + " " + region.skulls() + " at " + region.cx() + ", " + region.cz() + " (r " + region.radius() + ", "
                    + distance + " blocks away" + (region.contains(x, z) ? ", you're in it" : "") + ")");
        }
        return lines;
    }
}
