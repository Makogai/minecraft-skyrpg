package com.skyrpg.hub;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Everyone walks faster in Skyhaven, so getting from one master to the next is quick: a movement speed bonus while you
 * are in the hub, taken off the moment you leave (a dimension change), and checked twice a second in case anything
 * missed it. It stacks with the Speed stat.
 */
final class HubSpeed {
    /** +60% of base walking speed. */
    static final double BONUS = 0.6;
    private static final Identifier MODIFIER = SkyRpgHub.id("hub_speed");
    private static final int CHECK_TICKS = 10;

    private HubSpeed() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    apply(player);
                }
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> apply(player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> apply(handler.getPlayer())));
    }

    static void apply(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean inHub = Hub.isHub(player.level());
        boolean has = speed.getModifier(MODIFIER) != null;
        if (inHub && !has) {
            speed.addTransientModifier(new AttributeModifier(MODIFIER, BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!inHub && has) {
            speed.removeModifier(MODIFIER);
        }
    }
}
