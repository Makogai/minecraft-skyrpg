package com.skyrpg.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * What gives skill XP, loaded from {@code data/<namespace>/skyrpg/skill_xp/*.json}:
 * <pre>
 * {
 *   "skill": "skyrpg_skills:farming",
 *   "blocks": {"minecraft:wheat": 4, "#minecraft:logs": 6, "minecraft:cocoa": {"xp": 4, "mature_only": true}},
 *   "entities": {"minecraft:zombie": 6}
 * }
 * </pre>
 * Several files may add to the same skill. An exact block entry beats a tag entry. Crops
 * ({@link CropBlock}) only give XP when fully grown; set {@code mature_only} for other blocks with an
 * {@code age} property (cocoa, nether wart).
 */
public final class SkillXpSources {
    public static final String DIRECTORY = "skyrpg/skill_xp";

    public record Grant(Identifier skill, double xp, boolean matureOnly) {
    }

    private record TagGrant(TagKey<Block> tag, Grant grant) {
    }

    private static Map<Block, List<Grant>> blocks = Map.of();
    private static List<TagGrant> blockTags = List.of();
    private static Map<EntityType<?>, List<Grant>> entities = Map.of();

    private SkillXpSources() {
    }

    public static List<Grant> forBlock(BlockState state) {
        List<Grant> grants = blocks.get(state.getBlock());
        if (grants == null) {
            grants = blockTags.stream().filter(tagGrant -> state.is(tagGrant.tag())).map(TagGrant::grant).toList();
        }
        return grants.stream().filter(grant -> isMature(state) || !(grant.matureOnly() || state.getBlock() instanceof CropBlock)).toList();
    }

    public static List<Grant> forEntity(EntityType<?> type) {
        return entities.getOrDefault(type, List.of());
    }

    private static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                return state.getValue(age).equals(age.getPossibleValues().getLast());
            }
        }
        return true;
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<Block, List<Grant>> loadedBlocks = new HashMap<>();
        List<TagGrant> loadedTags = new ArrayList<>();
        Map<EntityType<?>, List<Grant>> loadedEntities = new HashMap<>();
        files.forEach((file, json) -> {
            try {
                JsonObject object = json.getAsJsonObject();
                Identifier skill = Identifier.parse(object.get("skill").getAsString());
                if (object.has("blocks")) {
                    object.getAsJsonObject("blocks").entrySet().forEach(entry -> {
                        Grant grant = parseGrant(skill, entry.getValue());
                        String key = entry.getKey();
                        if (key.startsWith("#")) {
                            loadedTags.add(new TagGrant(TagKey.create(Registries.BLOCK, Identifier.parse(key.substring(1))), grant));
                        } else if (BuiltInRegistries.BLOCK.containsKey(Identifier.parse(key))) {
                            loadedBlocks.computeIfAbsent(BuiltInRegistries.BLOCK.getValue(Identifier.parse(key)), b -> new ArrayList<>()).add(grant);
                        } else {
                            SkyRpgSkills.LOGGER.warn("Skill XP file {} lists unknown block {}", file, key);
                        }
                    });
                }
                if (object.has("entities")) {
                    object.getAsJsonObject("entities").entrySet().forEach(entry -> {
                        Identifier id = Identifier.parse(entry.getKey());
                        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                            loadedEntities.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getValue(id), t -> new ArrayList<>()).add(parseGrant(skill, entry.getValue()));
                        } else {
                            SkyRpgSkills.LOGGER.warn("Skill XP file {} lists unknown entity {}", file, id);
                        }
                    });
                }
            } catch (RuntimeException e) {
                SkyRpgSkills.LOGGER.error("Skipping invalid skill XP file {}: {}", file, e.getMessage());
            }
        });
        blocks = Map.copyOf(loadedBlocks);
        blockTags = List.copyOf(loadedTags);
        entities = Map.copyOf(loadedEntities);
        SkyRpgSkills.LOGGER.info("Loaded skill XP for {} blocks, {} block tags, {} entities", blocks.size(), blockTags.size(), entities.size());
    }

    private static Grant parseGrant(Identifier skill, JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            boolean matureOnly = object.has("mature_only") && object.get("mature_only").getAsBoolean();
            return new Grant(skill, object.get("xp").getAsDouble(), matureOnly);
        }
        return new Grant(skill, value.getAsDouble(), false);
    }
}
