package com.skyrpg.economy;

import com.skyrpg.core.economy.Wallet;
import net.minecraft.server.level.ServerPlayer;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.economy.bank.Bank;
import com.skyrpg.economy.net.EconomyPayloads;
import com.skyrpg.economy.shop.Shops;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coins: every player's purse and bank account, NPC shops and sell prices, and the commands to use them. */
public class SkyRpgEconomy implements ModInitializer {
    public static final String MOD_ID = "skyrpg_economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Other mods pay and charge coins through core's Wallet without depending on this mod.
        Wallet.install(new Wallet.Backend() {
            @Override
            public long balance(ServerPlayer player) {
                return Purse.get(player);
            }

            @Override
            public void give(ServerPlayer player, long amount) {
                Purse.add(player, amount);
            }

            @Override
            public boolean take(ServerPlayer player, long amount) {
                return Purse.trySpend(player, amount);
            }
        });
        Purse.init();
        Bank.init();
        com.skyrpg.core.records.PlayerRecords.addSource(entries -> entries
                .add("SkyRPG", "purse", "Purse", com.skyrpg.core.records.PlayerRecords.Format.COINS,
                        net.minecraft.world.item.Items.GOLD_NUGGET, Purse.get(entries.player()))
                .add("SkyRPG", "bank", "Bank", com.skyrpg.core.records.PlayerRecords.Format.COINS,
                        net.minecraft.world.item.Items.GOLD_BLOCK, Bank.balance(entries.player())));
        JsonDataLoader.register(id("sell_prices"), SellPrices.DIRECTORY, SellPrices::load);
        JsonDataLoader.register(id("shops"), Shops.DIRECTORY, Shops::load);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EconomyCommands.register(dispatcher));

        // NPCs (or anything else) open shops and the bank through these actions:
        // {"type": "open_shop", "params": {"shop": "skyrpg_economy:farmer"}}, {"type": "open_bank", "params": {"keeper": "..."}}
        Actions.register("open_shop", (player, params) -> {
            Identifier shop = params.has("shop") ? Identifier.tryParse(params.get("shop").getAsString()) : null;
            if (shop == null || !Shops.open(player, shop)) {
                LOGGER.warn("open_shop needs a valid \"shop\", got {}", params);
            }
        });
        Actions.register("open_bank", (player, params) -> Bank.open(player, params.has("keeper") ? params.get("keeper").getAsString() : ""));

        EconomyPayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.Buy.TYPE, (payload, context) -> Shops.buy(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.Sell.TYPE, (payload, context) -> Shops.sell(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.Buyback.TYPE, (payload, context) -> Shops.buyback(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.BankAction.TYPE, (payload, context) -> Bank.handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Sessions.forget(handler.player.getUUID());
            Shops.forget(handler.player.getUUID());
        });
        Payments.init();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
