package com.skyrpg.enchants;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The enchanting screen over the network. {@link View} opens or refreshes it; the client asks to {@link EnchantItem}
 * at the table, {@link ApplyTome}, {@link Combine} two tomes or {@link Salvage} one, naming inventory slots, and the
 * server checks everything again. The client knows its own inventory, XP and skill levels; the view adds what it
 * can't see: the table's power, the purse and material counts (backpacks included).
 */
public final class EnchantPayloads {
    /** What just happened, for the screen's animation. */
    public static final int FLASH_NONE = 0;
    public static final int FLASH_ENCHANT = 1;
    public static final int FLASH_TOME = 2;
    public static final int FLASH_COMBINE = 3;
    public static final int FLASH_SALVAGE = 4;

    private static final StreamCodec<ByteBuf, Integer> SIGNED = ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1);

    private EnchantPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EnchantItem.TYPE, EnchantItem.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ApplyTome.TYPE, ApplyTome.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Combine.TYPE, Combine.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Salvage.TYPE, Salvage.CODEC);
    }

    /**
     * @param power     bookshelves around the table (0-15), or -1 at Seraphine's (her library counts as full)
     * @param materials counts of every material enchanting can ask for, by item id
     * @param refresh   update an open screen instead of opening one
     * @param flash     one of the FLASH_ constants
     * @param tab       the tab to open on (0 enchant, 1 tomes, 2 codex), or -1 to keep the one showing
     */
    public record View(int power, long coins, Map<String, Integer> materials, Component notice, boolean good, boolean refresh, int flash,
                       int tab) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(SkyRpgEnchants.id("view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                SIGNED, View::power, ByteBufCodecs.VAR_LONG, View::coins,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), View::materials,
                ComponentSerialization.STREAM_CODEC, View::notice, ByteBufCodecs.BOOL, View::good, ByteBufCodecs.BOOL, View::refresh,
                ByteBufCodecs.VAR_INT, View::flash, SIGNED, View::tab, View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Enchant the item in {@code slot} with {@code enchant} at {@code level}; {@code replace} confirms replacing conflicts. */
    public record EnchantItem(int slot, String enchant, int level, boolean replace) implements CustomPacketPayload {
        public static final Type<EnchantItem> TYPE = new Type<>(SkyRpgEnchants.id("enchant"));
        public static final StreamCodec<ByteBuf, EnchantItem> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EnchantItem::slot, ByteBufCodecs.STRING_UTF8, EnchantItem::enchant, ByteBufCodecs.VAR_INT, EnchantItem::level,
                ByteBufCodecs.BOOL, EnchantItem::replace, EnchantItem::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Apply the tome in {@code tomeSlot} to the item in {@code itemSlot}. */
    public record ApplyTome(int tomeSlot, int itemSlot, boolean replace) implements CustomPacketPayload {
        public static final Type<ApplyTome> TYPE = new Type<>(SkyRpgEnchants.id("apply_tome"));
        public static final StreamCodec<ByteBuf, ApplyTome> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ApplyTome::tomeSlot, ByteBufCodecs.VAR_INT, ApplyTome::itemSlot, ByteBufCodecs.BOOL, ApplyTome::replace,
                ApplyTome::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Combine two identical tomes into one a level higher. */
    public record Combine(int first, int second) implements CustomPacketPayload {
        public static final Type<Combine> TYPE = new Type<>(SkyRpgEnchants.id("combine"));
        public static final StreamCodec<ByteBuf, Combine> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Combine::first, ByteBufCodecs.VAR_INT, Combine::second, Combine::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Break a tome down into Runic Dust. */
    public record Salvage(int slot) implements CustomPacketPayload {
        public static final Type<Salvage> TYPE = new Type<>(SkyRpgEnchants.id("salvage"));
        public static final StreamCodec<ByteBuf, Salvage> CODEC = ByteBufCodecs.VAR_INT.map(Salvage::new, Salvage::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
