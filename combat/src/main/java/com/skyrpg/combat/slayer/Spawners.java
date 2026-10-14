package com.skyrpg.combat.slayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.PlacedBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/**
 * Spawners from the Slayer Master, on Philip's ladder: each one unlocks once you've killed enough of the mob before it
 * (lifetime kills, from the game's statistics), in order. The ladder is data:
 * {@code data/<ns>/skyrpg/spawners/*.json}:
 * <pre>
 * {"kills": 5000, "steps": [{"mob": "minecraft:rabbit", "price": 5000}, {"mob": "minecraft:chicken", "price": 10000, "kills": 5000}, ...]}
 * </pre>
 * The first step is open to everyone; a step's {@code kills} (or the file's) are kills of the step before it. A spawner
 * a player placed gives itself back when broken; spawners found in the world stay vanilla.
 */
public final class Spawners {
    public static final String DIRECTORY = "skyrpg/spawners";
    private static final int MAX_BUY = 64;
    public static final Item SPAWNER = Registry.register(BuiltInRegistries.ITEM, key(),
            new SpawnerItem(Blocks.SPAWNER, new Item.Properties().setId(key()).rarity(Rarity.EPIC)));

    /** One step of the ladder: its mob, price, and the kills of the step before it that unlock it. */
    record Step(EntityType<?> mob, long price, int kills) {
    }

    record Result(boolean ok, Component message) {
    }

    private static List<Step> steps = List.of();

    private Spawners() {
    }

    private static ResourceKey<Item> key() {
        return ResourceKey.create(Registries.ITEM, SkyRpgCombat.id("mob_spawner"));
    }

    static void init() {
        JsonDataLoader.register(SkyRpgCombat.id("spawners"), DIRECTORY, Spawners::load);
        // A spawner a player placed comes back as an item (and gives no XP, so placing and breaking isn't an XP farm).
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(Blocks.SPAWNER) || !(level instanceof ServerLevel serverLevel) || !PlacedBlocks.isPlaced(level, pos)
                    || !(blockEntity instanceof SpawnerBlockEntity spawner)) {
                return true;
            }
            CompoundTag data = spawner.saveCustomOnly(level.registryAccess());
            String id = data.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "");
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
            serverLevel.destroyBlock(pos, false, player);
            PlacedBlocks.clear(serverLevel, pos);
            if (type != null && !player.isCreative()) {
                Block.popResource(serverLevel, pos, SpawnerItem.create(type, 1));
            }
            return false;
        });
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Step> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                int kills = json.has("kills") ? json.get("kills").getAsInt() : 5000;
                for (JsonElement entry : json.getAsJsonArray("steps")) {
                    JsonObject step = entry.getAsJsonObject();
                    EntityType<?> mob = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(step.get("mob").getAsString())).orElse(null);
                    if (mob == null) {
                        SkyRpgCombat.LOGGER.warn("Spawner ladder {}: no mob {}", id, step.get("mob").getAsString());
                        continue;
                    }
                    loaded.add(new Step(mob, step.get("price").getAsLong(), step.has("kills") ? step.get("kills").getAsInt() : kills));
                }
            } catch (RuntimeException e) {
                SkyRpgCombat.LOGGER.warn("Skipping spawner ladder {}: {}", id, e.getMessage());
            }
        });
        steps = List.copyOf(loaded);
    }

    static int kills(ServerPlayer player, EntityType<?> type) {
        return player.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED.get(type));
    }

    /** Unlocked: the first step, or the step before is unlocked and enough of its mob are dead. */
    static boolean unlocked(ServerPlayer player, int index) {
        for (int i = 1; i <= index; i++) {
            if (kills(player, steps.get(i - 1).mob()) < steps.get(i).kills()) {
                return false;
            }
        }
        return index >= 0 && index < steps.size();
    }

    static List<SlayerPayloads.SpawnerView> views(ServerPlayer player) {
        List<SlayerPayloads.SpawnerView> views = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            Step before = i == 0 ? null : steps.get(i - 1);
            int kills = before == null ? 0 : kills(player, before.mob());
            views.add(new SlayerPayloads.SpawnerView(i, SpawnerItem.create(step.mob(), 1), step.mob().getDescription().getString(), step.price(),
                    unlocked(player, i), before == null ? "" : before.mob().getDescription().getString(), Math.min(kills, step.kills()),
                    before == null ? 0 : step.kills()));
        }
        return views;
    }

    static Result buy(ServerPlayer player, int index, int requested) {
        if (index < 0 || index >= steps.size()) {
            return new Result(false, Component.literal("That spawner isn't sold any more."));
        }
        Step step = steps.get(index);
        String name = step.mob().getDescription().getString() + " Spawner";
        if (!unlocked(player, index)) {
            return new Result(false, Component.literal(name + " is still locked."));
        }
        int count = Mth.clamp(requested, 1, MAX_BUY);
        long cost = step.price() * count;
        if (!Wallet.take(player, cost)) {
            return new Result(false, Component.literal("Not enough coins: " + count + "x costs " + Msg.number(cost) + "."));
        }
        ItemStack stack = SpawnerItem.create(step.mob(), count);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
        Notice.send(player, Notice.Kind.SHOP, Msg.text("Bought " + count + "x " + name + " for ", ChatFormatting.GREEN).append(Msg.coins(cost)));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        return new Result(true, Component.literal("Bought " + count + "x " + name));
    }
}
