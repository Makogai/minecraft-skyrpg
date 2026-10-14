package com.skyrpg.world;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** The world mod over the network: big-moment banners, and the waystone menu and its travel requests. */
public final class WorldPayloads {
    private WorldPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Banner.TYPE, Banner.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WaystoneMenu.TYPE, WaystoneMenu.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Regions.TYPE, Regions.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Travel.TYPE, Travel.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Travel.TYPE, (payload, context) -> Waystones.travel(context.player(), payload.index()));
    }

    /** Shows a banner (dungeon discovered, first clear, secret found...) if the client has the mod. */
    public static void banner(ServerPlayer player, Identifier icon, String label, Component title, Component subtitle, int color) {
        if (ServerPlayNetworking.canSend(player, Banner.TYPE)) {
            ServerPlayNetworking.send(player, new Banner(icon.toString(), label, title, subtitle, color));
        }
    }

    public record Banner(String icon, String label, Component title, Component subtitle, int color) implements CustomPacketPayload {
        public static final Type<Banner> TYPE = new Type<>(SkyRpgWorld.id("banner"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Banner> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Banner::icon, ByteBufCodecs.STRING_UTF8, Banner::label, ComponentSerialization.STREAM_CODEC, Banner::title,
                ComponentSerialization.STREAM_CODEC, Banner::subtitle, ByteBufCodecs.INT, Banner::color, Banner::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One place a waystone can take you.
     * @param kind    {@link #HUB} or {@link #WAYSTONE}
     * @param where   a short line under the name ("Overworld · 1,240 blocks")
     * @param cost    XP levels it costs
     */
    public record Destination(int kind, String name, String where, int cost) {
        public static final int HUB = 0;
        public static final int WAYSTONE = 1;
        static final StreamCodec<ByteBuf, Destination> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Destination::kind, ByteBufCodecs.STRING_UTF8, Destination::name, ByteBufCodecs.STRING_UTF8, Destination::where,
                ByteBufCodecs.VAR_INT, Destination::cost, Destination::new);
    }

    /** Opens the waystone menu: the waystone you're at, and where you can go from it. */
    public record WaystoneMenu(String here, List<Destination> destinations) implements CustomPacketPayload {
        public static final Type<WaystoneMenu> TYPE = new Type<>(SkyRpgWorld.id("waystone_menu"));
        public static final StreamCodec<ByteBuf, WaystoneMenu> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, WaystoneMenu::here, Destination.CODEC.apply(ByteBufCodecs.list()), WaystoneMenu::destinations, WaystoneMenu::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The danger regions around the player (sent when they move into another region cell), for the map. */
    public record Regions(String dimension, List<RegionShape> regions) implements CustomPacketPayload {
        public static final Type<Regions> TYPE = new Type<>(SkyRpgWorld.id("regions"));
        public static final StreamCodec<ByteBuf, Regions> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Regions::dimension, RegionShape.STREAM_CODEC.apply(ByteBufCodecs.list()), Regions::regions, Regions::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Travel to the menu's destination at {@code index}. The server checks everything again. */
    public record Travel(int index) implements CustomPacketPayload {
        public static final Type<Travel> TYPE = new Type<>(SkyRpgWorld.id("waystone_travel"));
        public static final StreamCodec<ByteBuf, Travel> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Travel::index, Travel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
