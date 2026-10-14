package com.skyrpg.magic;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.BloodMoon;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Rare and legendary scrolls drop from bosses at a low chance. Added to the other mods' loot tables as they load (no edits to their
 * files): the loot table, the scroll's spell and the chance per roll.
 */
final class MagicLoot {
    private record Drop(String table, String spell, double chance) {
    }

    private static final List<Drop> DROPS = List.of(
            // World mobs and slayer-style bosses.
            new Drop("skyrpg_combat:entities/magma_tyrant", "fireball", 0.08),
            new Drop("skyrpg_combat:entities/magma_tyrant", "meteor", 0.015),
            new Drop("skyrpg_combat:entities/iron_thane", "chain_lightning", 0.08),
            new Drop("skyrpg_combat:entities/iron_thane", "thunderclap", 0.03),
            new Drop("skyrpg_combat:entities/frost_revenant", "blizzard", 0.03),
            new Drop("skyrpg_combat:entities/gravecaller", "mana_shield", 0.04),
            new Drop("skyrpg_combat:entities/ender_wyrm", "thunderclap", 0.05),
            new Drop("skyrpg_combat:entities/ender_wyrm", "meteor", 0.02),
            new Drop("skyrpg_combat:entities/grove_sentinel", "entangle", 0.05),
            // Dungeon bosses.
            new Drop("skyrpg_world:bosses/frost_revenant_king", "blizzard", 0.10),
            new Drop("skyrpg_world:bosses/storm_griffin", "chain_lightning", 0.10),
            new Drop("skyrpg_world:bosses/storm_griffin", "thunderclap", 0.04),
            new Drop("skyrpg_world:bosses/queen_arachne", "mana_shield", 0.08),
            new Drop("skyrpg_world:bosses/infernal_knight", "fireball", 0.10),
            new Drop("skyrpg_world:bosses/ash_colossus", "meteor", 0.04),
            new Drop("skyrpg_world:bosses/astral_sentinel", "arcane_missiles", 0.10),
            new Drop("skyrpg_world:bosses/astral_sentinel", "thunderclap", 0.05),
            new Drop("skyrpg_world:bosses/blight_lord", "entangle", 0.10),
            new Drop("skyrpg_world:bosses/tidemother", "healing_pulse", 0.12),
            new Drop("skyrpg_world:bosses/lantern_wraith", "mana_shield", 0.06),
            // Reward chests of the hardest dungeons.
            new Drop("skyrpg_world:rewards/magma_tyrant", "meteor", 0.04),
            new Drop("skyrpg_world:rewards/star_warden", "meteor", 0.05),
            new Drop("skyrpg_world:rewards/star_warden", "thunderclap", 0.08),
            new Drop("skyrpg_world:rewards/iron_thane", "chain_lightning", 0.10),
            new Drop("skyrpg_world:rewards/queen_arachne", "blizzard", 0.08));

    /** Scrolls a Blood Moon kill can drop, with weights (the milder ones are likelier). */
    private static final List<Map.Entry<String, Integer>> BLOOD_MOON = List.of(
            Map.entry("fireball", 5), Map.entry("blink", 5), Map.entry("arcane_missiles", 5), Map.entry("entangle", 5),
            Map.entry("chain_lightning", 3), Map.entry("blizzard", 3), Map.entry("mana_shield", 3), Map.entry("thunderclap", 1));
    private static final double BLOOD_MOON_CHANCE = 0.004;

    private MagicLoot() {
    }

    static void init() {
        // Under a Blood Moon, hostile mobs you kill may drop a scroll (0.4% per kill, better with Magic Find).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!BloodMoon.active() || !(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel level)
                    || !(source.getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            double find = 1 + (Stats.available() ? Math.max(0, Stats.get(killer, StatTypes.MAGIC_FIND)) : 0) / 100;
            if (level.getRandom().nextDouble() >= BLOOD_MOON_CHANCE * find) {
                return;
            }
            int total = BLOOD_MOON.stream().mapToInt(Map.Entry::getValue).sum();
            int roll = level.getRandom().nextInt(total);
            for (Map.Entry<String, Integer> entry : BLOOD_MOON) {
                roll -= entry.getValue();
                if (roll < 0) {
                    Item scroll = MagicItems.SCROLLS.get(entry.getKey());
                    if (scroll != null) {
                        entity.spawnAtLocation(level, new ItemStack(scroll));
                        killer.sendSystemMessage(Msg.text("The blood moon left a spell scroll behind", ChatFormatting.DARK_RED));
                    }
                    return;
                }
            }
        });
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            String table = key.identifier().toString();
            for (Drop drop : DROPS) {
                if (!drop.table().equals(table)) {
                    continue;
                }
                var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(SkyRpgMagic.MOD_ID, drop.spell() + "_scroll"));
                if (item != null) {
                    builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance((float) drop.chance())).add(LootItem.lootTableItem(item)));
                }
            }
        });
    }
}
