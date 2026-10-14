package com.skyrpg.hub;

import com.skyrpg.core.text.Notice;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the hub as built: outside creative mode nobody can break, place or fight there, and
 * blocks can only be used if they're tagged {@code #skyrpg_hub:usable_in_hub} (doors, buttons...).
 * Nothing takes damage or gets hungry in the hub, and falling off the island puts you back at spawn.
 */
final class HubProtection {
    static final TagKey<Block> USABLE = TagKey.create(Registries.BLOCK, SkyRpgHub.id("usable_in_hub"));
    private static final int CHECK_INTERVAL_TICKS = 5;

    private HubProtection() {
    }

    static boolean isProtected(Player player) {
        return Hub.isHub(player.level()) && !player.isCreative();
    }

    static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isProtected(player));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                isProtected(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                isProtected(player) && !level.getBlockState(hit.getBlockPos()).is(USABLE) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Item item = player.getItemInHand(hand).getItem();
            boolean changesWorld = item instanceof BlockItem || item instanceof BucketItem || item instanceof SpawnEggItem;
            return isProtected(player) && changesWorld ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                isProtected(player) && (entity instanceof HangingEntity || entity instanceof ArmorStand) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                isProtected(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !Hub.isHub(entity.level()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
        ServerTickEvents.END_SERVER_TICK.register(HubProtection::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel hub = Hub.level(server);
        if (hub == null) {
            return;
        }
        HubConfig config = HubConfig.get();
        // The gate is open air (real portal blocks would send players to the Nether or End), so draw one.
        for (AABB portal : config.survivalPortals()) {
            Vec3 center = portal.getCenter();
            hub.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 12,
                    portal.getXsize() / 3, portal.getYsize() / 3, portal.getZsize() / 3, 0.2);
        }
        // Copy: sending a player to the survival world removes them from hub.players() mid-loop.
        for (ServerPlayer player : List.copyOf(hub.players())) {
            keepFed(player);
            if (player.getY() < config.voidY()) {
                Hub.teleport(player, hub, config.spawn(), config.spawnYaw(), config.spawnPitch());
            } else if (config.survivalPortals().stream().anyMatch(portal -> portal.intersects(player.getBoundingBox()))) {
                Notice.send(player, Notice.Kind.INFO, Component.literal("Sending you to the survival world...").withStyle(ChatFormatting.GRAY));
                Hub.sendToSurvival(player);
            }
        }
    }

    /** Nobody gets hungry in the hub: food and saturation stay full while you're there. */
    private static void keepFed(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < 20 || food.getSaturationLevel() < 5) {
            food.setFoodLevel(20);
            food.setSaturation(5);
        }
    }
}
