package com.skyrpg.progress.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.world.DangerRegions;
import com.skyrpg.world.Explorer;
import com.skyrpg.world.RegionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

/**
 * "Where do I go?": finds the place an objective points at, on demand and only when a player asks, and sends it as the
 * waypoint target. Structures are searched out to {@value #STRUCTURE_CHUNKS} chunks, biomes to {@value #BIOME_BLOCKS} blocks
 * and danger regions to {@value #REGION_BLOCKS} blocks; past that the player is told to explore.
 */
final class QuestTracking {
    static final int COLOR = 0xFFF2B33D;
    private static final int STRUCTURE_CHUNKS = 100;
    private static final int BIOME_BLOCKS = 2400;
    private static final int REGION_BLOCKS = 4800;
    private static final int REGION_STEP = 160;

    private QuestTracking() {
    }

    /** Points the player at the objective. Returns a one-line answer to show them (what is tracked, or why nothing is). */
    static String track(ServerPlayer player, QuestData.Quest quest, int index) {
        Objective o = quest.objectives().get(index);
        ServerLevel level = player.level();
        JsonObject fixed = o.json().has("track") ? o.json().getAsJsonObject("track") : null;
        if (fixed != null) {
            return fixed(player, fixed, o.label());
        }
        String dimension = o.str("dimension");
        boolean elsewhere = !dimension.isEmpty() && !level.dimension().identifier().toString().equals(dimension);
        String name = o.label();
        switch (o.type()) {
            case "dungeon_clear" -> {
                String structure = o.str("structure");
                Explorer.Found found = Explorer.state(player).dungeons().stream().filter(f -> java.util.List.of(o.str("dungeon").split(",")).contains(f.dungeon())).findFirst().orElse(null);
                if (found != null && found.dimension().equals(level.dimension().identifier().toString())) {
                    return send(player, o.str("name").isEmpty() ? name : o.str("name"), Vec3.atCenterOf(found.blockPos()), null);
                }
                if (elsewhere) {
                    return "Not here: this one lies in " + dimensionName(dimension) + ".";
                }
                return structure(player, level, structure, o.str("name").isEmpty() ? name : o.str("name"));
            }
            case "visit" -> {
                switch (o.str("kind")) {
                    case "structure" -> {
                        return elsewhere ? "Not here: this one lies in " + dimensionName(dimension) + "." : structure(player, level, o.str("id"), name);
                    }
                    case "dungeon_found" -> {
                        return elsewhere ? "Not here: this one lies in " + dimensionName(dimension) + "." : structure(player, level, o.str("structure"), o.str("name"));
                    }
                    case "biome" -> {
                        return elsewhere ? "Not here: go to " + dimensionName(dimension) + " first." : biome(player, level, o.str("id"), name);
                    }
                    case "region" -> {
                        return region(player, level, o.str("id"), name);
                    }
                    case "layer" -> {
                        int y = (o.num("min_y", -64) + o.num("max_y", 0)) / 2;
                        return send(player, name, new Vec3(player.getX(), y, player.getZ()), null);
                    }
                    case "dimension" -> {
                        return "Find your way to " + dimensionName(o.str("id")) + ".";
                    }
                    case "waystone" -> {
                        return "Walk up to a waystone and use it to attune it.";
                    }
                    default -> {
                        return "Nothing to point at for that one.";
                    }
                }
            }
            default -> {
                return "That objective has no marker: it happens wherever you are.";
            }
        }
    }

    private static String fixed(ServerPlayer player, JsonObject track, String label) {
        JsonArray pos = track.getAsJsonArray("pos");
        Vec3 target = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
        Identifier dimension = track.has("dimension") ? Identifier.parse(track.get("dimension").getAsString()) : null;
        String name = track.has("name") ? track.get("name").getAsString() : label;
        return send(player, name, target, dimension);
    }

    private static String structure(ServerPlayer player, ServerLevel level, String id, String name) {
        java.util.List<Holder<Structure>> holders = new java.util.ArrayList<>();
        for (String part : id.split(",")) {
            Holder<Structure> holder = Objectives.structure(level, part);
            if (holder != null) {
                holders.add(holder);
            }
        }
        if (holders.isEmpty()) {
            return "That place isn't part of this world.";
        }
        BlockPos found = level.findNearestMapStructure(HolderSet.direct(holders), player.blockPosition(), STRUCTURE_CHUNKS, false);
        if (found == null) {
            return "Nothing found within " + STRUCTURE_CHUNKS + " chunks. Explore further out and try again.";
        }
        return send(player, name, new Vec3(found.getX() + 0.5, found.getY() > 0 ? found.getY() : player.getY(), found.getZ() + 0.5), null);
    }

    private static String biome(ServerPlayer player, ServerLevel level, String id, String name) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                id.startsWith("#") ? holder -> holder.is(TagKey.<Biome>create(Registries.BIOME, Identifier.parse(id.substring(1))))
                        : holder -> holder.is(ResourceKey.<Biome>create(Registries.BIOME, Identifier.parse(id))),
                player.blockPosition(), BIOME_BLOCKS, 32, 64);
        if (found == null) {
            return "No such place within " + BIOME_BLOCKS + " blocks. Explore further out and try again.";
        }
        BlockPos pos = found.getFirst();
        return send(player, name, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), null);
    }

    private static String region(ServerPlayer player, ServerLevel level, String id, String name) {
        if (level.dimension() != Level.OVERWORLD) {
            return "Danger regions lie in the overworld.";
        }
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        RegionShape best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -REGION_BLOCKS; dx <= REGION_BLOCKS; dx += REGION_STEP) {
            for (int dz = -REGION_BLOCKS; dz <= REGION_BLOCKS; dz += REGION_STEP) {
                RegionShape region = DangerRegions.at(level, px + dx, pz + dz);
                if (region != null && (id.equals("any") || region.type().equals(id))) {
                    double distance = Math.hypot(region.cx() - px, region.cz() - pz);
                    if (distance < bestDistance) {
                        best = region;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best == null) {
            return "No such region within " + REGION_BLOCKS + " blocks. Explore further out and try again.";
        }
        return send(player, best.name(), new Vec3(best.cx() + 0.5, player.getY(), best.cz() + 0.5), null);
    }

    private static String send(ServerPlayer player, String name, Vec3 pos, Identifier dimension) {
        WaypointTarget.send(player, name, pos, COLOR, dimension);
        return "Tracking " + name + ".";
    }

    private static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            case "minecraft:overworld" -> "the overworld";
            case "skyrpg_hub:hub" -> "the hub";
            default -> id;
        };
    }
}
