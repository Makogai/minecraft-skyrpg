package com.skyrpg.items;

import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.items.ToolTiers;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.items.accessory.Accessories;
import com.skyrpg.items.backpack.Backpacks;
import com.skyrpg.items.bow.Bows;
import com.skyrpg.items.crafting.Bench;
import com.skyrpg.items.effects.GearEffectSources;
import com.skyrpg.items.shield.Shields;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SkyRPG's own items, with rarities, stats, abilities and armour set bonuses defined in data packs. */
public class SkyRpgItems implements ModInitializer {
    public static final String MOD_ID = "skyrpg_items";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.init();
        JsonDataLoader.register(id("items"), ItemRegistry.ITEMS_DIRECTORY, ItemRegistry::loadItems);
        JsonDataLoader.register(id("item_sets"), ItemRegistry.SETS_DIRECTORY, ItemRegistry::loadSets);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ItemCommands.register(dispatcher));
        Stats.registerSource(new ItemStatSource());
        ToolCare.init();
        Abilities.init();
        Accessories.init();
        Backpacks.init();
        Bench.init();
        Shields.init();
        Bows.init();
        GearEffectSources.init();
        // Pickaxe tiers: a definition's "tier", else vanilla's.
        ToolTiers.install(stack -> {
            ItemDefinition def = SkyItems.definition(stack);
            return def != null && def.json().has("tier") ? def.json().get("tier").getAsInt() : ToolTiers.vanilla(stack);
        });

        // Clients need the definitions for tooltips: on join and after every /reload.
        PayloadTypeRegistry.clientboundPlay().register(ItemDefinitionsPayload.TYPE, ItemDefinitionsPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendDefinitions(handler.player);
            normalizeInventory(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Abilities.forget(handler.player.getUUID());
            ToolCare.forget(handler.player.getUUID());
            Bows.forget(handler.player.getUUID());
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(SkyRpgItems::sendDefinitions));

        // Keep owned items in line with their definitions (crafted stacks, balance changes), and swap
        // pre-registration items (vanilla items with a skyrpg_item tag) for the real ones.
        ServerTickEvents.END_SERVER_TICK.register(SkyRpgItems::tick);
        // Stats change the moment armor goes on or off, not on the next periodic update.
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
            if (entity instanceof ServerPlayer player && (SkyItems.idOf(from) != null || SkyItems.idOf(to) != null)) {
                Stats.refresh(player);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            server.getPlayerList().getPlayers().forEach(SkyRpgItems::normalizeInventory);
        }
    }

    private static void normalizeInventory(ServerPlayer player) {
        normalize(player.getInventory());
        normalize(player.getEnderChestInventory());
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack migrated = SkyItems.migrate(carried);
        if (migrated != null) {
            player.containerMenu.setCarried(migrated);
        } else {
            SkyItems.normalize(carried);
        }
        Accessories.normalize(player);
    }

    private static void normalize(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            ItemStack migrated = SkyItems.migrate(stack);
            if (migrated != null) {
                container.setItem(slot, migrated);
            } else {
                SkyItems.normalize(stack);
            }
        }
    }

    private static void sendDefinitions(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ItemDefinitionsPayload.TYPE)) {
            ServerPlayNetworking.send(player, ItemDefinitionsPayload.current());
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
