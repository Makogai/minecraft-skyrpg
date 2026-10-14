package com.skyrpg.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chance drops from blocks that aren't ours, by data, so seeds can come from other mods' blocks without depending on
 * them. From {@code data/<ns>/skyrpg/bonus_drops/*.json}:
 * <pre>
 * {"block": "skyrpg_mining:emberite_ore", "item": "skyrpg_farming:emberpepper_seeds", "chance": 0.02,
 *  "grown_only": false, "night_only": false}
 * </pre>
 * A block or item that doesn't exist (its mod isn't installed) just never matches.
 */
public final class BonusDrops {
    public static final String DIRECTORY = "skyrpg/bonus_drops";

    private record Drop(Identifier block, Identifier item, double chance, boolean grownOnly, boolean nightOnly) {
    }

    private static List<Drop> drops = List.of();

    private BonusDrops() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Drop> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                loaded.add(new Drop(Identifier.parse(json.get("block").getAsString()), Identifier.parse(json.get("item").getAsString()),
                        json.get("chance").getAsDouble(),
                        json.has("grown_only") && json.get("grown_only").getAsBoolean(),
                        json.has("night_only") && json.get("night_only").getAsBoolean()));
            } catch (RuntimeException e) {
                SkyRpgFarming.LOGGER.warn("Skipping bonus drop {}: {}", id, e.getMessage());
            }
        });
        drops = List.copyOf(loaded);
    }

    static void roll(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Identifier block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (Drop drop : drops) {
            if (!drop.block().equals(block) || drop.grownOnly() && !Harvest.grown(state) || drop.nightOnly() && !level.isDarkOutside()) {
                continue;
            }
            double luck = 1 + FarmingEffects.value(player, FarmingEffects.RARE_FINDS) + FarmingEffects.value(player, FarmingEffects.SEED_LUCK);
            if (player.getRandom().nextDouble() < drop.chance() * luck) {
                Item item = BuiltInRegistries.ITEM.getValue(drop.item());
                Block.popResource(level, pos, new ItemStack(item));
            }
        }
    }
}
