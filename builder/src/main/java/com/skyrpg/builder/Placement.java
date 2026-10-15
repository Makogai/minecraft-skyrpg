package com.skyrpg.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a blueprint goes: {@code origin} is the world position of the placed box's lowest north-west corner, and the
 * blueprint is mirrored (front to back) and then turned clockwise {@code turns} times, like vanilla structures. The
 * box's footprint swaps width and depth on odd turns; heights never change, so layer {@code y} is always world
 * {@code origin.y + y}.
 */
public record Placement(BlockPos origin, int turns, boolean mirrored, int sizeX, int sizeY, int sizeZ) {
    public static Placement of(Blueprint blueprint, BlockPos origin, int turns, boolean mirrored) {
        return new Placement(origin, Math.floorMod(turns, 4), mirrored, blueprint.sizeX, blueprint.sizeY, blueprint.sizeZ);
    }

    public Rotation rotation() {
        return switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public Mirror mirror() {
        return mirrored ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    /** The placed box's size along world X. */
    public int width() {
        return turns % 2 == 0 ? sizeX : sizeZ;
    }

    /** The placed box's size along world Z. */
    public int depth() {
        return turns % 2 == 0 ? sizeZ : sizeX;
    }

    public BlockPos max() {
        return origin.offset(width() - 1, sizeY - 1, depth() - 1);
    }

    /** Placed-box offset (0..width-1, 0..depth-1) of a blueprint column. */
    public int placedX(int x, int z) {
        int mx = mirrored ? sizeX - 1 - x : x;
        return switch (turns) {
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - mx;
            case 3 -> z;
            default -> mx;
        };
    }

    public int placedZ(int x, int z) {
        int mx = mirrored ? sizeX - 1 - x : x;
        return switch (turns) {
            case 1 -> mx;
            case 2 -> sizeZ - 1 - z;
            case 3 -> sizeX - 1 - mx;
            default -> z;
        };
    }

    /** World position of a blueprint position. */
    public BlockPos world(int x, int y, int z) {
        return origin.offset(placedX(x, z), y, placedZ(x, z));
    }

    /** World position of a blueprint entry's local index. */
    public BlockPos world(Blueprint blueprint, int localIndex) {
        return world(blueprint.localX(localIndex), blueprint.localY(localIndex), blueprint.localZ(localIndex));
    }

    /** The block as it looks once placed this way. */
    public BlockState transform(BlockState state) {
        return state.mirror(mirror()).rotate(rotation());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getY() >= origin.getY() && pos.getZ() >= origin.getZ()
                && pos.getX() < origin.getX() + width() && pos.getY() < origin.getY() + sizeY && pos.getZ() < origin.getZ() + depth();
    }
}
