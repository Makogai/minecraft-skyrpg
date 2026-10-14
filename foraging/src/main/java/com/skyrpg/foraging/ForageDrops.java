package com.skyrpg.foraging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chance drops from chopped logs, by data: where the new woods' saplings come from. From
 * {@code data/<ns>/skyrpg/forage_drops/*.json}:
 * <pre>
 * {"block": "minecraft:spruce_log", "item": "skyrpg_foraging:mistpine_sapling", "chance": 0.006,
 *  "biome": "#minecraft:is_mountain", "night_only": false}
 * </pre>
 * {@code block} and {@code biome} take a tag with {@code #}. Rare-find luck makes these likelier too. A block or item
 * that doesn't exist just never matches.
 */
public final class ForageDrops {
    public static final String DIRECTORY = "skyrpg/forage_drops";

    private record Drop(String block, Identifier item, double chance, String biome, boolean nightOnly) {
    }

    private static List<Drop> drops = List.of();

    private ForageDrops() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Drop> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                loaded.add(new Drop(json.get("block").getAsString(), Identifier.parse(json.get("item").getAsString()),
                        json.get("chance").getAsDouble(), json.has("biome") ? json.get("biome").getAsString() : null,
                        json.has("night_only") && json.get("night_only").getAsBoolean()));
            } catch (RuntimeException e) {
                SkyRpgForaging.LOGGER.warn("Skipping forage drop {}: {}", id, e.getMessage());
            }
        });
        drops = List.copyOf(loaded);
    }

    static void roll(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        double luck = 1 + ForagingEffects.value(player, ForagingEffects.RARE_FINDS);
        for (Drop drop : drops) {
            if (!matchesBlock(drop.block(), state) || drop.nightOnly() && !level.isDarkOutside()
                    || drop.biome() != null && !matchesBiome(drop.biome(), level, pos)) {
                continue;
            }
            if (player.getRandom().nextDouble() < drop.chance() * luck) {
                BuiltInRegistries.ITEM.getOptional(drop.item()).ifPresent(item -> {
                    Chopping.give(player, new ItemStack(item));
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("A ")
                            .append(new ItemStack(item).getHoverName()).append(" fell from the branches!").withColor(0xFF7BD88F));
                });
            }
        }
    }

    private static boolean matchesBlock(String block, BlockState state) {
        if (block.startsWith("#")) {
            return state.is(TagKey.create(Registries.BLOCK, Identifier.parse(block.substring(1))));
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(Identifier.parse(block));
    }

    private static boolean matchesBiome(String biome, ServerLevel level, BlockPos pos) {
        if (biome.startsWith("#")) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.parse(biome.substring(1)));
            return level.getBiome(pos).is(tag);
        }
        return level.getBiome(pos).is(Identifier.parse(biome));
    }
}
