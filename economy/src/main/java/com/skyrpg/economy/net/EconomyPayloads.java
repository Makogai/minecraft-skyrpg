package com.skyrpg.economy.net;

import com.skyrpg.economy.SkyRpgEconomy;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Everything shops, the bank and the menu's Pay page send over the network. The server always decides:
 * the client only shows what it is sent and asks for trades, and every request is checked again on the server.
 * <ul>
 *   <li>server → client: {@link Shop} opens a shop screen (or, with {@code refresh}, updates the open one),
 *       {@link OpenBank} opens the bank, {@link Notice} shows a result line in whichever screen is open
 *       (a shop, the bank, or the SkyRPG menu's Pay page).</li>
 *   <li>client → server: {@link Buy}, {@link Sell}, {@link Buyback}, {@link BankAction} and {@link Pay}.</li>
 * </ul>
 * Coins and the bank balance aren't in here: they sync as player attachments.
 */
public final class EconomyPayloads {
    private EconomyPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Shop.TYPE, Shop.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenBank.TYPE, OpenBank.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Notice.TYPE, Notice.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Buy.TYPE, Buy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Sell.TYPE, Sell.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Buyback.TYPE, Buyback.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BankAction.TYPE, BankAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Pay.TYPE, Pay.CODEC);
    }

    /** One offer: the stack you get (its count is how many) and what it costs. */
    /** {@code costs}: items paid per unit (with their counts); {@code skill}/{@code level}: what unlocks it, if anything. */
    public record Offer(ItemStack stack, long price, List<ItemStack> costs, Optional<Identifier> skill, int level) {
        static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Offer::stack,
                ByteBufCodecs.VAR_LONG, Offer::price,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Offer::costs,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Offer::skill,
                ByteBufCodecs.VAR_INT, Offer::level,
                Offer::new);
    }

    /**
     * The selling side of a shop: what the keeper pays per item (only for items they buy), and the
     * player's last sale here, which they can buy back for what they got.
     */
    public record Selling(boolean buys, String hint, Map<Identifier, Long> prices, List<ItemStack> buyback, long buybackPrice) {
        static final StreamCodec<RegistryFriendlyByteBuf, Selling> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Selling::buys,
                ByteBufCodecs.STRING_UTF8, Selling::hint,
                ByteBufCodecs.<ByteBuf, Identifier, Long, Map<Identifier, Long>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG), Selling::prices,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Selling::buyback,
                ByteBufCodecs.VAR_LONG, Selling::buybackPrice,
                Selling::new);
    }

    public record Shop(Identifier shop, String title, String keeper, int accent, ItemStack icon, List<Offer> offers, Selling selling,
                       boolean refresh) implements CustomPacketPayload {
        public static final Type<Shop> TYPE = new Type<>(SkyRpgEconomy.id("shop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Shop> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Shop::shop,
                ByteBufCodecs.STRING_UTF8, Shop::title,
                ByteBufCodecs.STRING_UTF8, Shop::keeper,
                ByteBufCodecs.INT, Shop::accent,
                ItemStack.STREAM_CODEC, Shop::icon,
                Offer.CODEC.apply(ByteBufCodecs.list()), Shop::offers,
                Selling.CODEC, Shop::selling,
                ByteBufCodecs.BOOL, Shop::refresh,
                Shop::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenBank(String keeper) implements CustomPacketPayload {
        public static final Type<OpenBank> TYPE = new Type<>(SkyRpgEconomy.id("open_bank"));
        public static final StreamCodec<ByteBuf, OpenBank> CODEC = ByteBufCodecs.STRING_UTF8.map(OpenBank::new, OpenBank::keeper);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A short result ("Bought 16x Wheat Seeds", "You need 120 more coins") for the open screen. */
    public record Notice(Component text, boolean good) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = new Type<>(SkyRpgEconomy.id("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Notice::text,
                ByteBufCodecs.BOOL, Notice::good,
                Notice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Buy {@code units} times the offer at {@code offer} (each unit is the offer's count of items). */
    public record Buy(Identifier shop, int offer, int units) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(SkyRpgEconomy.id("buy"));
        public static final StreamCodec<ByteBuf, Buy> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Buy::shop,
                ByteBufCodecs.VAR_INT, Buy::offer,
                ByteBufCodecs.VAR_INT, Buy::units,
                Buy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sell every one of this item from the inventory, or with no item, everything this keeper buys. */
    public record Sell(Identifier shop, Optional<Identifier> item) implements CustomPacketPayload {
        public static final Type<Sell> TYPE = new Type<>(SkyRpgEconomy.id("sell"));
        public static final StreamCodec<ByteBuf, Sell> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Sell::shop,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Sell::item,
                Sell::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Buyback(Identifier shop) implements CustomPacketPayload {
        public static final Type<Buyback> TYPE = new Type<>(SkyRpgEconomy.id("buyback"));
        public static final StreamCodec<ByteBuf, Buyback> CODEC = Identifier.STREAM_CODEC.map(Buyback::new, Buyback::shop);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Pay {@code amount} coins from your purse to the online player {@code target}: the Pay page in the
     * SkyRPG menu. The server checks it like /pay (see Payments) and answers with a {@link Notice}.
     */
    public record Pay(UUID target, long amount) implements CustomPacketPayload {
        public static final Type<Pay> TYPE = new Type<>(SkyRpgEconomy.id("pay"));
        public static final StreamCodec<ByteBuf, Pay> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Pay::target,
                ByteBufCodecs.VAR_LONG, Pay::amount,
                Pay::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Move coins between purse and bank. {@code mode}: {@link #ALL}, {@link #HALF} or {@link #EXACT} ({@code amount}). */
    public record BankAction(boolean deposit, int mode, long amount) implements CustomPacketPayload {
        public static final int ALL = 0;
        public static final int HALF = 1;
        public static final int EXACT = 2;
        public static final Type<BankAction> TYPE = new Type<>(SkyRpgEconomy.id("bank_action"));
        public static final StreamCodec<ByteBuf, BankAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BankAction::deposit,
                ByteBufCodecs.VAR_INT, BankAction::mode,
                ByteBufCodecs.VAR_LONG, BankAction::amount,
                BankAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
