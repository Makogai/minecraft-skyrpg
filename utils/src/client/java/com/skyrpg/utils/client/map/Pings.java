package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.net.Ping;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pinging a spot for everyone around: press the ping key (middle mouse by default) at a block or mob up to 256 blocks
 * away and a marker with your name shows for everyone in the dimension for a few seconds, on screen (or as an arrow at
 * the screen edge) and on the minimap, with a sound. The server relays it ({@link Ping}).
 */
public final class Pings {
    public static final KeyMapping PING_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_utils.ping", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, SkyRpgCoreClient.CATEGORY));
    private static final double REACH = 256;
    private static final long SHOWN_MILLIS = 8000;
    private static final int[] COLORS = {0xFFFFD84A, 0xFF55FFFF, 0xFFFF6BD6, 0xFF7CFF6B, 0xFFFF9F43, 0xFFB18CFF};

    /** A ping on screen until {@code until}. */
    record Shown(String player, double x, double y, double z, int color, long until) {
    }

    private static final List<Shown> SHOWN = new ArrayList<>();

    private Pings() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Ping.Shown.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(Pings::tick);
        MapMarkers.register((dimension, out) -> {
            for (int i = 0; i < SHOWN.size(); i++) {
                Shown ping = SHOWN.get(i);
                out.accept(new MapMarkers.Marker(SkyRpgUtilsClient.id("ping_" + i), ping.x(), ping.y(), ping.z(),
                        new MapMarkers.Dot(ping.color()), Component.literal(ping.player() + "'s ping").withColor(ping.color()),
                        List.of(Component.literal("Pinged a moment ago").withColor(0xFF8D96A8)), ping.color()));
            }
        });
    }

    /** The pings showing now. */
    static List<Shown> active() {
        return List.copyOf(SHOWN);
    }

    /** Forgets every ping (leaving a world or changing dimension). */
    public static void clear() {
        SHOWN.clear();
    }

    private static void tick(Minecraft minecraft) {
        long now = Util.getMillis();
        SHOWN.removeIf(ping -> ping.until() < now);
        while (PING_KEY.consumeClick()) {
            if (minecraft.player == null || minecraft.gui.screen() != null) {
                continue;
            }
            Vec3 target = target(minecraft);
            if (target == null) {
                minecraft.player.sendOverlayMessage(Component.literal("Nothing there to ping").withColor(0xFF8D96A8));
            } else if (ClientPlayNetworking.canSend(Ping.TYPE)) {
                ClientPlayNetworking.send(new Ping(target.x, target.y, target.z));
            }
        }
    }

    /** What the crosshair is on: a mob or player close by, else the block it points at (far away too). */
    private static Vec3 target(Minecraft minecraft) {
        if (minecraft.hitResult instanceof EntityHitResult entity) {
            return entity.getEntity().position().add(0, entity.getEntity().getBbHeight(), 0);
        }
        HitResult hit = minecraft.player.pick(REACH, 1f, false);
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    private static void receive(Ping.Shown payload) {
        Minecraft minecraft = Minecraft.getInstance();
        SHOWN.removeIf(ping -> ping.player().equals(payload.player()));
        int color = COLORS[Math.floorMod(payload.player().hashCode(), COLORS.length)];
        SHOWN.add(new Shown(payload.player(), payload.x(), payload.y(), payload.z(), color, Util.getMillis() + SHOWN_MILLIS));
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6f, 1.6f, false);
        }
    }
}
