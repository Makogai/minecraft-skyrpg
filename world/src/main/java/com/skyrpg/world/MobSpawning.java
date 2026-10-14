package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.world.MobLevelling;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns dungeon mobs from mob entries ({@code {"type", "count", "level", "elite", "name", "equipment"}}), for mob
 * packs and boss summons. Dungeon mobs never despawn; Elites carry their dungeon's key (see {@link DungeonMarkers}).
 */
final class MobSpawning {
    /** Every mob a dungeon spawned. */
    static final String MOB_TAG = "skyrpg_dungeon_mob";
    /** On Elites: the key they may drop, as "skyrpg_key=<item id>". */
    static final String KEY_PREFIX = "skyrpg_key=";

    private MobSpawning() {
    }

    /**
     * Spawns every entry around {@code center} (within {@code spread} blocks, on solid ground where possible).
     * @param limit at most this many mobs in total (for boss summons), or -1
     * @param key   the dungeon's key item for Elites to carry, or null
     */
    static List<Mob> spawn(ServerLevel level, JsonArray entries, Vec3 center, double spread, int limit, Identifier key) {
        List<Mob> spawned = new ArrayList<>();
        RandomSource random = level.getRandom();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(Dungeons.str(entry, "type", "minecraft:zombie"))).orElse(null);
            if (type == null) {
                SkyRpgWorld.LOGGER.warn("Unknown dungeon mob type {}", entry.get("type"));
                continue;
            }
            int count = (int) Dungeons.num(entry, "count", 1);
            for (int i = 0; i < count; i++) {
                if (limit >= 0 && spawned.size() >= limit) {
                    return spawned;
                }
                Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
                if (!(entity instanceof Mob mob)) {
                    if (entity != null) {
                        entity.discard();
                    }
                    continue;
                }
                place(level, mob, center, spread, random);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIGGERED, null);
                dress(mob, entry);
                mob.setPersistenceRequired();
                mob.addTag(MOB_TAG);
                boolean elite = Dungeons.bool(entry, "elite", false);
                MobLevelling.level(mob, (int) Dungeons.num(entry, "level", 10), elite);
                if (elite && key != null) {
                    mob.addTag(KEY_PREFIX + key);
                }
                level.addFreshEntity(mob);
                spawned.add(mob);
            }
        }
        return spawned;
    }

    static void dress(Mob mob, JsonObject entry) {
        if (entry.has("name")) {
            mob.setCustomName(Component.literal(entry.get("name").getAsString())
                    .withStyle(Dungeons.bool(entry, "elite", false) ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        }
        Dungeons.tags(entry).forEach(mob::addTag);
        if (entry.has("equipment") && entry.get("equipment").isJsonObject()) {
            for (var slot : entry.getAsJsonObject("equipment").entrySet()) {
                EquipmentSlot equipmentSlot;
                try {
                    equipmentSlot = EquipmentSlot.byName(slot.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(slot.getValue().getAsString())).orElse(null);
                if (item != null) {
                    mob.setItemSlot(equipmentSlot, new ItemStack(item));
                    mob.setDropChance(equipmentSlot, 0);
                }
            }
        }
    }

    /** A free spot near {@code center}: standing room, on a solid block, or in water for swimmers. */
    static void place(ServerLevel level, Mob mob, Vec3 center, double spread, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = center.x + (random.nextDouble() * 2 - 1) * spread;
            double z = center.z + (random.nextDouble() * 2 - 1) * spread;
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                BlockPos feet = BlockPos.containing(x, center.y + dy, z);
                BlockPos below = feet.below();
                boolean ground = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
                boolean water = !level.getFluidState(feet).isEmpty();
                if (!ground && !water) {
                    continue;
                }
                mob.snapTo(x, feet.getY(), z, random.nextFloat() * 360, 0);
                if (level.noCollision(mob)) {
                    return;
                }
            }
        }
        mob.snapTo(center.x, center.y, center.z, random.nextFloat() * 360, 0);
    }
}
