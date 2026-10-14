package com.skyrpg.events;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * How players score in an event. JSON:
 * <pre>{@code
 * {type: "break_blocks", blocks: ["#minecraft:logs", "minecraft:pumpkin"], points: 1}
 * {type: "break_blocks", blocks: ["#minecraft:crops"], grown_only: true, points: 1}   // only fully grown crops
 * {type: "kill_mobs", entities: ["minecraft:zombie", "#minecraft:skeletons"], points: 2}
 * {type: "custom"}    // points only come from /event points and SkyRpgEvents.addPoints
 * }</pre>
 * {@code #} marks a tag. An empty or missing list matches every block or mob.
 */
public record ScoringRule(Type type, List<Identifier> ids, List<TagKey<Block>> blockTags, List<TagKey<EntityType<?>>> entityTags, int points,
                          boolean grownOnly) {
    public enum Type {
        BREAK_BLOCKS, KILL_MOBS, CATCH, CUSTOM
    }

    public static ScoringRule breakBlocks(int points, String... blocks) {
        return parse(rule("break_blocks", "blocks", points, blocks));
    }

    public static ScoringRule killMobs(int points, String... entities) {
        return parse(rule("kill_mobs", "entities", points, entities));
    }

    public static ScoringRule custom() {
        return new ScoringRule(Type.CUSTOM, List.of(), List.of(), List.of(), 0, false);
    }

    private static JsonObject rule(String type, String key, int points, String... targets) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("points", points);
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (String target : targets) {
            list.add(target);
        }
        json.add(key, list);
        return json;
    }

    public static ScoringRule parse(JsonObject json) {
        String typeName = Json.string(json, "custom", "type").toLowerCase(Locale.ROOT).replace('-', '_');
        Type type = switch (typeName) {
            case "break_blocks", "break", "mine", "blocks" -> Type.BREAK_BLOCKS;
            case "kill_mobs", "kill", "kills", "mobs" -> Type.KILL_MOBS;
            case "catch", "catch_fish", "fishing" -> Type.CATCH;
            case "custom", "manual" -> Type.CUSTOM;
            default -> throw new IllegalArgumentException("Unknown scoring type '" + typeName + "' (use break_blocks, kill_mobs, catch or custom)");
        };
        int points = Json.integer(json, 1, "points");
        List<Identifier> ids = new ArrayList<>();
        List<TagKey<Block>> blockTags = new ArrayList<>();
        List<TagKey<EntityType<?>>> entityTags = new ArrayList<>();
        if (type == Type.BREAK_BLOCKS) {
            for (String block : Json.strings(json, "blocks", "block")) {
                Identifier id = Json.id(block, "block");
                if (block.startsWith("#")) {
                    blockTags.add(TagKey.create(Registries.BLOCK, id));
                } else if (BuiltInRegistries.BLOCK.containsKey(id)) {
                    ids.add(id);
                } else {
                    throw new IllegalArgumentException("Unknown block '" + block + "'");
                }
            }
        } else if (type == Type.KILL_MOBS) {
            for (String entity : Json.strings(json, "entities", "entity", "mobs", "mob")) {
                Identifier id = Json.id(entity, "entity");
                if (entity.startsWith("#")) {
                    entityTags.add(TagKey.create(Registries.ENTITY_TYPE, id));
                } else if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    ids.add(id);
                } else {
                    throw new IllegalArgumentException("Unknown entity '" + entity + "'");
                }
            }
        }
        // For "catch": "sea_creatures_only" counts only sea creatures (it rides on the grown_only flag).
        boolean grownOnly = json.has("grown_only") && json.get("grown_only").getAsBoolean()
                || json.has("sea_creatures_only") && json.get("sea_creatures_only").getAsBoolean();
        return new ScoringRule(type, List.copyOf(ids), List.copyOf(blockTags), List.copyOf(entityTags), points, grownOnly);
    }

    private boolean matchesAnything() {
        return ids.isEmpty() && blockTags.isEmpty() && entityTags.isEmpty();
    }

    public boolean matches(BlockState state) {
        if (type != Type.BREAK_BLOCKS || grownOnly && !grown(state)) {
            return false;
        }
        if (matchesAnything() || ids.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return true;
        }
        for (TagKey<Block> tag : blockTags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** A crop at its last stage (or any block with an {@code age} at its highest); other blocks count as grown. */
    private static boolean grown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                return state.getValue(age) >= age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            }
        }
        return true;
    }

    public boolean matches(Entity entity) {
        if (type != Type.KILL_MOBS) {
            return false;
        }
        if (matchesAnything() || ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : entityTags) {
            if (entity.typeHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** A fishing catch (from core's Catches): any catch, or with "sea_creatures_only" just sea creatures. */
    public boolean matchesCatch(boolean seaCreature) {
        return type == Type.CATCH && (!grownOnly || seaCreature);
    }

    /** "break logs", "kill zombies, skeletons" (for chat). */
    public String describe() {
        List<String> targets = new ArrayList<>();
        ids.forEach(id -> targets.add(id.getPath().replace('_', ' ')));
        blockTags.forEach(tag -> targets.add(tag.location().getPath().replace('_', ' ')));
        entityTags.forEach(tag -> targets.add(tag.location().getPath().replace('_', ' ')));
        String what = targets.isEmpty() ? (type == Type.BREAK_BLOCKS ? "any block" : "any mob") : String.join(", ", targets);
        String each = points == 1 ? "1 point" : points + " points";
        return switch (type) {
            case BREAK_BLOCKS -> (grownOnly ? "Harvest grown " : "Break ") + what + " (" + each + " each)";
            case KILL_MOBS -> "Kill " + what + " (" + each + " each)";
            case CATCH -> (grownOnly ? "Fish up sea creatures" : "Catch anything with a rod") + " (" + each + " each)";
            case CUSTOM -> "";
        };
    }
}
