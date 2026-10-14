package com.skyrpg.npcs.client;

import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.npcs.NpcMapPayload;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Puts every NPC on the maps: their face, name, role and what clicking them does. */
public final class SkyRpgNpcsClient implements ClientModInitializer {
    private static List<MapMarkers.Marker> markers = List.of();
    private static List<Identifier> dimensions = List.of();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(NpcMapPayload.TYPE, (payload, context) -> update(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            markers = List.of();
            dimensions = List.of();
            com.skyrpg.core.client.NpcMarkers.clear();
        });
        MapMarkers.register((dimension, out) -> {
            for (int i = 0; i < markers.size(); i++) {
                if (dimensions.get(i).equals(dimension)) {
                    out.accept(markers.get(i));
                }
            }
        });
    }

    private static void update(NpcMapPayload payload) {
        markers = payload.npcs().stream().map(npc -> new MapMarkers.Marker(npc.id(), npc.x(), npc.y(), npc.z(),
                new MapMarkers.Face(skinTexture(npc.skin())),
                Component.literal(npc.name()).withColor(npc.color()),
                details(npc), 0xFF000000 | npc.color())).toList();
        dimensions = payload.npcs().stream().map(NpcMapPayload.Entry::dimension).toList();
        com.skyrpg.core.client.NpcMarkers.setSpots(payload.npcs().stream()
                .map(npc -> new com.skyrpg.core.client.NpcMarkers.Spot(npc.id(), npc.x(), npc.y(), npc.z())).toList());
    }

    private static List<Component> details(NpcMapPayload.Entry npc) {
        Component role = Component.literal(npc.role()).withColor(0xAAAAAA)
                .append(Component.literal("  ·  ").withColor(0x555555))
                .append(Component.literal(npc.hint()).withColor(0xF2B33D));
        return npc.role().isEmpty() ? List.of() : List.of(role);
    }

    /** The NPC skin's texture file: {@code skyrpg_npcs:entity/npc/farmer} is {@code textures/entity/npc/farmer.png}. */
    private static Identifier skinTexture(Identifier skin) {
        return skin.withPath(path -> "textures/" + path + ".png");
    }
}
