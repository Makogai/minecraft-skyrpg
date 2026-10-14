package com.skyrpg.world.client;

import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.client.map.MapTints;
import com.skyrpg.world.DangerRegions;
import com.skyrpg.world.RegionShape;
import com.skyrpg.world.SkyRpgWorld;
import com.skyrpg.world.WorldPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Danger regions on the client's maps: a red wash over their ground (stronger along the border, so the edge reads
 * like a line) and a marker with the region's name at its heart. The server sends the regions around the player.
 */
final class RegionMap {
    private static final int FILL_ALPHA = 0x5A;
    private static final int BORDER_ALPHA = 0xB0;
    private static final double BORDER = 2.5;
    private static volatile String dimension = "";
    private static volatile List<RegionShape> regions = List.of();

    private RegionMap() {
    }

    /** The dimension the regions below are in (empty before the server has sent any). */
    static String dimension() {
        return dimension;
    }

    /** The regions around the player, as the server last sent them. */
    static List<RegionShape> regions() {
        return regions;
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(WorldPayloads.Regions.TYPE, (payload, context) -> {
            dimension = payload.dimension();
            regions = List.copyOf(payload.regions());
        });
        MapTints.register((dim, x, z) -> {
            if (!dim.toString().equals(dimension)) {
                return 0;
            }
            for (RegionShape region : regions) {
                double depth = region.depth(x, z);
                if (depth > 0) {
                    return (depth < BORDER ? BORDER_ALPHA : FILL_ALPHA) << 24 | DangerRegions.MAP_RED;
                }
            }
            return 0;
        });
        MapMarkers.register((dim, out) -> {
            if (!dim.toString().equals(dimension)) {
                return;
            }
            for (RegionShape region : regions) {
                out.accept(new MapMarkers.Marker(SkyRpgWorld.id("region/" + region.cx() + "_" + region.cz()), region.cx() + 0.5, 64,
                        region.cz() + 0.5, new MapMarkers.Dot(0xFF000000 | DangerRegions.MAP_RED), Component.literal("⚠ " + region.name()),
                        List.of(Component.literal("Danger " + region.skulls()).withStyle(ChatFormatting.RED),
                                Component.literal("Monsters +" + region.levelBonus() + " levels").withStyle(ChatFormatting.GRAY)),
                        0xFF000000 | region.color()));
            }
        });
    }
}
