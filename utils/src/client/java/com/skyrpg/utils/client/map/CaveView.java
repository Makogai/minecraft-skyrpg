package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The minimap underground (and in the Nether): instead of the surface far above, the floor around you,
 * darker the further below your feet it is, with walls dark. It covers {@link #RADIUS} blocks around where
 * you were when it was last drawn, and is redrawn a few rows a tick into a back buffer, then swapped in, so
 * it never shows half of an old picture.
 */
public final class CaveView {
    public static final int RADIUS = 48;
    static final int SIZE = RADIUS * 2;
    private static final int ROWS_PER_TICK = 12;
    private static final int UP = 2;
    private static final int DOWN = 24;
    /** Ticks the player has to stay above or below ground before the view switches, so it doesn't flicker. */
    private static final int SWITCH_TICKS = 15;
    private static final int WALL = 0xFF1A1D24;

    static final Identifier ID = SkyRpgUtilsClient.id("map/cave");
    private static NativeImage image;
    private static DynamicTexture texture;
    private static final int[] BACK = new int[SIZE * SIZE];
    private static boolean active;
    private static int votes;
    private static int row;
    private static int buildX;
    private static int buildZ;
    private static int buildY;
    /** Block at the image's centre, for drawing. */
    static int centerX;
    static int centerZ;
    private static boolean ready;

    private CaveView() {
    }

    /** Whether the minimap should show this instead of the surface. */
    public static boolean active() {
        return active && ready;
    }

    public static void reset() {
        active = false;
        ready = false;
        votes = 0;
        row = 0;
    }

    public static void tick(Minecraft minecraft, boolean enabled) {
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        boolean underground = enabled && underground(level, player);
        if (underground != active) {
            if (++votes >= SWITCH_TICKS) {
                active = underground;
                votes = 0;
                row = 0;
                ready = false;
            }
        } else {
            votes = 0;
        }
        if (!active) {
            return;
        }
        if (row == 0) {
            buildX = player.getBlockX();
            buildZ = player.getBlockZ();
            buildY = player.getBlockY();
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int end = Math.min(SIZE, row + ROWS_PER_TICK);
        for (; row < end; row++) {
            int z = buildZ - RADIUS + row;
            for (int col = 0; col < SIZE; col++) {
                BACK[row * SIZE + col] = column(level, buildX - RADIUS + col, z, buildY, cursor);
            }
        }
        if (row >= SIZE) {
            swap();
            row = 0;
        }
    }

    private static void swap() {
        if (image == null) {
            image = new NativeImage(SIZE, SIZE, true);
            texture = new DynamicTexture(ID::toString, image);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixel(x, z, BACK[z * SIZE + x]);
            }
        }
        texture.upload();
        centerX = buildX;
        centerZ = buildZ;
        ready = true;
    }

    /** Below the surface with no sky overhead, or in a dimension with a roof. */
    private static boolean underground(ClientLevel level, Player player) {
        if (level.dimensionType().hasCeiling()) {
            return true;
        }
        BlockPos head = BlockPos.containing(player.getEyePosition());
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, head.getX(), head.getZ());
        return head.getY() < surface - 4 && !level.canSeeSky(head);
    }

    /** The floor at (x, z) seen from height y: the first block with air above it, going down. */
    private static int column(ClientLevel level, int x, int z, int y, BlockPos.MutableBlockPos cursor) {
        boolean open = false;
        int solidRun = 0;
        for (int dy = UP; dy >= -DOWN; dy--) {
            BlockState state = level.getBlockState(cursor.set(x, y + dy, z));
            if (state.isAir() || !state.getFluidState().isEmpty() && dy > -DOWN) {
                if (!state.getFluidState().isEmpty()) {
                    // Water and lava: show the surface of the pool.
                    return 0xFF000000 | MapScanner.scale(state.getMapColor(level, cursor).col, depthShade(-dy));
                }
                open = true;
                solidRun = 0;
                continue;
            }
            if (open) {
                MapColor color = state.getMapColor(level, cursor);
                if (color == MapColor.NONE) {
                    continue;
                }
                return 0xFF000000 | MapScanner.scale(color.col, depthShade(-dy));
            }
            // Solid from your head height down: a wall, unless there's open space a little lower.
            if (++solidRun >= UP + 3) {
                return WALL;
            }
        }
        return 0xFF050608;
    }

    private static float depthShade(int below) {
        return Math.max(0.35f, 1.0f - Math.max(0, below) * 0.03f);
    }
}
