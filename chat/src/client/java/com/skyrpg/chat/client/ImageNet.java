package com.skyrpg.chat.client;

import com.skyrpg.chat.ChatImages;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Receives screenshots other players shared (chunks from the server) and hands them to the image cache. */
final class ImageNet {
    private static final int MAX_OPEN = 6;
    private static final Map<String, Assembly> OPEN = new HashMap<>();

    private ImageNet() {
    }

    private static final class Assembly {
        final int total;
        final List<byte[]> parts = new ArrayList<>();
        int size;

        Assembly(int total) {
            this.total = total;
        }
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ChatImages.ImageChunk.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OPEN.clear());
    }

    private static void receive(ChatImages.ImageChunk chunk) {
        if (chunk.total() < 1 || chunk.total() > ChatImages.MAX_CHUNKS || chunk.id().length() != 8) {
            return;
        }
        Assembly assembly = OPEN.get(chunk.id());
        if (chunk.index() == 0) {
            if (OPEN.size() >= MAX_OPEN) {
                OPEN.clear();
            }
            assembly = new Assembly(chunk.total());
            OPEN.put(chunk.id(), assembly);
        } else if (assembly == null || assembly.total != chunk.total() || assembly.parts.size() != chunk.index()) {
            OPEN.remove(chunk.id());
            return;
        }
        assembly.size += chunk.data().length;
        if (assembly.size > ChatImages.MAX_BYTES) {
            OPEN.remove(chunk.id());
            return;
        }
        assembly.parts.add(chunk.data());
        if (assembly.parts.size() == assembly.total) {
            OPEN.remove(chunk.id());
            ByteArrayOutputStream out = new ByteArrayOutputStream(assembly.size);
            for (byte[] part : assembly.parts) {
                out.write(part, 0, part.length);
            }
            ImageCache.supply(chunk.id(), out.toByteArray());
        }
    }
}
