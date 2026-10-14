package com.skyrpg.core.net;

import com.skyrpg.core.SkyRpgCore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: "point the player at this place". The utils mod (if the player has it) shows it as the tracked
 * waypoint: compass ribbon, in-world marker and minimap arrow. Treasure maps, contracts and events send it:
 * <pre>
 * WaypointTarget.send(player, "Buried treasure", pos, 0xFFF2B33D);
 * WaypointTarget.clear(player);
 * </pre>
 * {@code dimension} is e.g. {@code minecraft:overworld}; empty means "wherever the player is". {@code clear} removes
 * whatever is tracked. A player without the utils mod ignores it.
 */
public record WaypointTarget(boolean clear, String name, int x, int y, int z, int color, String dimension) implements CustomPacketPayload {
    public static final Type<WaypointTarget> TYPE = new Type<>(SkyRpgCore.id("waypoint_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointTarget> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WaypointTarget::clear,
            ByteBufCodecs.STRING_UTF8, WaypointTarget::name,
            ByteBufCodecs.VAR_INT, WaypointTarget::x,
            ByteBufCodecs.VAR_INT, WaypointTarget::y,
            ByteBufCodecs.VAR_INT, WaypointTarget::z,
            ByteBufCodecs.INT, WaypointTarget::color,
            ByteBufCodecs.STRING_UTF8, WaypointTarget::dimension,
            WaypointTarget::new);

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    /** Tracks {@code pos} for the player, in whatever dimension they're in. {@code color} is ARGB. */
    public static void send(ServerPlayer player, String name, Vec3 pos, int color) {
        send(player, name, pos, color, null);
    }

    /** Same, in a given dimension (null = the player's current one). */
    public static void send(ServerPlayer player, String name, Vec3 pos, int color, Identifier dimension) {
        ServerPlayNetworking.send(player, new WaypointTarget(false, name, (int) Math.floor(pos.x), (int) Math.floor(pos.y),
                (int) Math.floor(pos.z), color, dimension == null ? "" : dimension.toString()));
    }

    /** Stops tracking whatever the player is tracking. */
    public static void clear(ServerPlayer player) {
        ServerPlayNetworking.send(player, new WaypointTarget(true, "", 0, 0, 0, 0, ""));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
