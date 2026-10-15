package com.skyrpg.crates;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** What the crate screen shows (the contents and odds), opening one, and the reel it rolled. */
public final class CratePayloads {
    private CratePayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Menu.TYPE, Menu.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Roll.TYPE, Roll.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
    }

    /** One card on the reel or in the contents: what it looks like, its tier colour, and if it's a hidden ★ card. */
    public record Card(ItemStack icon, String name, int color, boolean mystery) {
        static void write(RegistryFriendlyByteBuf buf, Card card) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, card.icon);
            buf.writeUtf(card.name, 128);
            buf.writeInt(card.color);
            buf.writeBoolean(card.mystery);
        }

        static Card read(RegistryFriendlyByteBuf buf) {
            return new Card(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readInt(), buf.readBoolean());
        }
    }

    /** A tier in the contents list: its name, colour, chance (0-1) and what it can give. */
    public record Tier(String name, int color, double chance, List<Card> cards) {
    }

    /** Opens the crate screen: the crate, how many of it and of its key you have, and everything inside with the odds. */
    public record Menu(String crate, String name, String subtitle, ItemStack icon, ItemStack keyIcon, String keyName, String keyHint,
                       int crates, int keys, List<Tier> tiers) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(SkyRpgCrates.id("menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Menu> CODEC = StreamCodec.ofMember(Menu::write, Menu::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(crate);
            buf.writeUtf(name);
            buf.writeUtf(subtitle);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, keyIcon);
            buf.writeUtf(keyName);
            buf.writeUtf(keyHint);
            buf.writeVarInt(crates);
            buf.writeVarInt(keys);
            buf.writeVarInt(tiers.size());
            for (Tier tier : tiers) {
                buf.writeUtf(tier.name());
                buf.writeInt(tier.color());
                buf.writeDouble(tier.chance());
                buf.writeVarInt(tier.cards().size());
                tier.cards().forEach(card -> Card.write(buf, card));
            }
        }

        private static Menu read(RegistryFriendlyByteBuf buf) {
            String crate = buf.readUtf();
            String name = buf.readUtf();
            String subtitle = buf.readUtf();
            ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            ItemStack keyIcon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            String keyName = buf.readUtf();
            String keyHint = buf.readUtf();
            int crates = buf.readVarInt();
            int keys = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), 16);
            List<Tier> tiers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String tierName = buf.readUtf();
                int color = buf.readInt();
                double chance = buf.readDouble();
                int cards = Math.min(buf.readVarInt(), 64);
                List<Card> list = new ArrayList<>(cards);
                for (int c = 0; c < cards; c++) {
                    list.add(Card.read(buf));
                }
                tiers.add(new Tier(tierName, color, chance, list));
            }
            return new Menu(crate, name, subtitle, icon, keyIcon, keyName, keyHint, crates, keys, tiers);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The reel the server rolled: the cards in order, which one it stops on, and what that is (revealed). */
    public record Roll(String crate, List<Card> reel, int winner, Card prize, String tierName, int crates, int keys)
            implements CustomPacketPayload {
        public static final Type<Roll> TYPE = new Type<>(SkyRpgCrates.id("roll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Roll> CODEC = StreamCodec.ofMember(Roll::write, Roll::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(crate);
            buf.writeVarInt(reel.size());
            reel.forEach(card -> Card.write(buf, card));
            buf.writeVarInt(winner);
            Card.write(buf, prize);
            buf.writeUtf(tierName);
            buf.writeVarInt(crates);
            buf.writeVarInt(keys);
        }

        private static Roll read(RegistryFriendlyByteBuf buf) {
            String crate = buf.readUtf();
            int count = Math.min(buf.readVarInt(), 128);
            List<Card> reel = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                reel.add(Card.read(buf));
            }
            return new Roll(crate, reel, buf.readVarInt(), Card.read(buf), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Asks to open one crate of this kind (the server checks for the crate and a key). */
    public record Open(String crate) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(SkyRpgCrates.id("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.ofMember(
                (open, buf) -> buf.writeUtf(open.crate), buf -> new Open(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
