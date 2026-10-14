package com.skyrpg.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skyrpg.core.combat.CombatTag;
import com.skyrpg.core.data.PlayerFacts;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.core.world.Regions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * What SkyHelper is told about the player asking: what they hold and carry, where they are, their stats, skills, coins
 * and quest. The service shows it to the model as "key: value" lines, so the keys are written for a reader.
 */
final class HelperContext {
    private static final List<Identifier> SKILLS = List.of(SkillLevels.COMBAT, SkillLevels.MINING, SkillLevels.FARMING,
            SkillLevels.FORAGING, SkillLevels.FISHING);
    private static final int INVENTORY_LINES = 30;

    private HelperContext() {
    }

    static JsonObject of(ServerPlayer player) {
        JsonObject c = new JsonObject();
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            JsonObject item = new JsonObject();
            item.addProperty("name", held.getHoverName().getString());
            item.addProperty("id", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            item.addProperty("count", held.getCount());
            c.add("held", item);
        }
        if (!player.getOffhandItem().isEmpty()) {
            c.addProperty("offhand", player.getOffhandItem().getHoverName().getString());
        }
        JsonArray wearing = new JsonArray();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                wearing.add(worn.getHoverName().getString());
            }
        }
        if (!wearing.isEmpty()) {
            c.add("wearing", wearing);
        }
        Map<String, Integer> carried = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                carried.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
            }
        }
        JsonArray inventory = new JsonArray();
        carried.entrySet().stream().limit(INVENTORY_LINES).forEach(e -> inventory.add(e.getKey() + (e.getValue() > 1 ? " x" + e.getValue() : "")));
        if (!inventory.isEmpty()) {
            c.add("inventory", inventory);
        }

        JsonObject where = new JsonObject();
        ServerLevel level = player.level();
        String dimension = level.dimension().identifier().toString();
        where.addProperty("world", switch (dimension) {
            case "skyrpg_hub:hub" -> "Skyhaven (the hub, safe)";
            case "minecraft:overworld" -> "the survival world (Overworld)";
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> dimension;
        });
        BlockPos pos = player.blockPosition();
        where.addProperty("position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        level.getBiome(pos).unwrapKey().ifPresent(key -> where.addProperty("biome", key.identifier().getPath().replace('_', ' ')));
        String region = Regions.typeAt(level, pos.getX(), pos.getZ());
        if (region != null && !region.isEmpty()) {
            where.addProperty("danger region", region.replace('_', ' '));
        }
        c.add("where", where);

        JsonObject state = new JsonObject();
        state.addProperty("health", Math.round(player.getHealth() * StatTypes.HEALTH_SCALE) + "/" + Math.round(player.getMaxHealth() * StatTypes.HEALTH_SCALE));
        if (Stats.available()) {
            state.addProperty("mana", Math.round(Stats.mana(player)));
        }
        state.addProperty("coins in purse", Wallet.balance(player));
        if (CombatTag.isTagged(player)) {
            state.addProperty("in combat", true);
        }
        c.add("status", state);

        JsonObject skills = new JsonObject();
        for (Identifier skill : SKILLS) {
            skills.addProperty(SkillLevels.displayName(skill), SkillLevels.level(player, skill));
        }
        c.add("skill levels", skills);

        if (Stats.available()) {
            JsonObject stats = new JsonObject();
            for (StatType type : StatTypes.all()) {
                if (!StatTypes.isPlayerStat(type)) {
                    continue;
                }
                double value = Stats.get(player, type);
                if (value != 0) {
                    String shown = value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
                    stats.addProperty(type.name(), shown + (type.percent() ? "%" : ""));
                }
            }
            c.add("stats", stats);
        }

        PlayerFacts.collect(player).forEach((key, value) -> c.addProperty(key, String.valueOf(value)));

        JsonObject world = new JsonObject();
        world.addProperty("time", level.isDarkOutside() ? "night" : "day");
        if (BloodMoon.active()) {
            world.addProperty("blood moon", "active right now");
        }
        JsonArray online = new JsonArray();
        level.getServer().getPlayerList().getPlayers().forEach(p -> online.add(p.getGameProfile().name()));
        world.add("players online", online);
        c.add("server", world);
        return c;
    }
}
