package com.skyrpg.utils.client.info;

import com.skyrpg.utils.client.UtilsSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the player is looking at, worked out once a tick: the nearest block or entity along the line of sight,
 * up to {@link #RANGE} blocks (further than you can reach, so you can read about a mob before you get to it).
 */
public final class LookTarget {
    public static final double RANGE = 12;

    private static BlockPos block;
    private static BlockState state;
    private static Entity entity;

    private LookTarget() {
    }

    public static BlockPos block() {
        return block;
    }

    public static BlockState state() {
        return state;
    }

    public static Entity entity() {
        return entity;
    }

    public static boolean any() {
        return block != null || entity != null;
    }

    public static void tick(Minecraft minecraft) {
        block = null;
        state = null;
        entity = null;
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || !UtilsSettings.look().get()) {
            return;
        }
        HitResult blockHit = player.pick(RANGE, 1f, false);
        double blockDistance = RANGE * RANGE;
        if (blockHit instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            blockDistance = hit.getLocation().distanceToSqr(player.getEyePosition());
            block = hit.getBlockPos();
            state = minecraft.level.getBlockState(block);
            if (state.isAir()) {
                block = null;
                state = null;
            }
        }
        if (UtilsSettings.lookEntities().get()) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1f).scale(RANGE);
            AABB area = player.getBoundingBox().expandTowards(look).inflate(1);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look), area,
                    e -> e != player && !e.isSpectator() && !e.isInvisible() && !(e instanceof Interaction)
                            && (e.isPickable() || e instanceof ItemEntity),
                    blockDistance);
            if (hit != null) {
                entity = hit.getEntity();
                block = null;
                state = null;
            }
        }
    }
}
