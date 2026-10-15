package com.skyrpg.builder.client;

import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.builder.Placement;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The holograms this client knows about (one per nearby station), the blueprints they show, and the quads to draw
 * for each: every block not built yet, textured and see-through, faces between two hologram blocks left out.
 * Blocks already in place disappear from the hologram; in planning, blocks that are in the way glow red.
 */
final class Ghosts {
    /** Quads beyond this aren't drawn (the outline still is). */
    private static final int MAX_QUADS = 250_000;
    private static final int TINT = 0xBFE6FF;
    private static final int CONFLICT = 0xFF6A6A;

    static final Map<String, Blueprint> BLUEPRINTS = new HashMap<>();
    static final Map<BlockPos, Ghost> ACTIVE = new HashMap<>();

    private Ghosts() {
    }

    static final class Ghost {
        final BuilderPayloads.Ghost data;
        final Placement placement;
        Mesh mesh;
        long builtAt;
        long buildCostNanos;

        Ghost(BuilderPayloads.Ghost data, Placement placement) {
            this.data = data;
            this.placement = placement;
        }
    }

    /** Quads to draw, each at a block position with its colour (alpha included). */
    record Mesh(long[] positions, BakedQuad[] quads, int[] colors, int count) {
    }

    static void geometry(Blueprint blueprint) {
        BLUEPRINTS.put(blueprint.id, blueprint);
        // Holograms waiting for this blueprint can draw now.
        ACTIVE.values().forEach(ghost -> ghost.mesh = null);
    }

    static void update(BuilderPayloads.Ghost data) {
        if (data.mode() == BuilderPayloads.Ghost.HIDE) {
            ACTIVE.remove(data.station());
            return;
        }
        Blueprint blueprint = BLUEPRINTS.get(data.blueprintId());
        Placement placement = blueprint == null ? null : Placement.of(blueprint, data.origin(), data.turns(), data.mirrored());
        ACTIVE.put(data.station(), new Ghost(data, placement));
    }

    static void clear() {
        ACTIVE.clear();
        BLUEPRINTS.clear();
    }

    /** The mesh for this hologram, rebuilt when it's stale (cheap holograms often, big ones less often). */
    static Mesh mesh(Ghost ghost, ClientLevel level) {
        Blueprint blueprint = BLUEPRINTS.get(ghost.data.blueprintId());
        if (blueprint == null) {
            return null;
        }
        Placement placement = ghost.placement != null ? ghost.placement
                : Placement.of(blueprint, ghost.data.origin(), ghost.data.turns(), ghost.data.mirrored());
        long now = level.getGameTime();
        long interval = Math.max(10, ghost.buildCostNanos / 400_000L);
        if (ghost.mesh == null || now - ghost.builtAt >= interval || now < ghost.builtAt) {
            long start = System.nanoTime();
            ghost.mesh = build(level, blueprint, placement, ghost.data.mode() == BuilderPayloads.Ghost.BUILD ? ghost.data.layer() : -1);
            ghost.builtAt = now;
            ghost.buildCostNanos = System.nanoTime() - start;
        }
        return ghost.mesh;
    }

    private record Block(BlockPos pos, BlockState state, boolean conflict) {
    }

    private static Mesh build(ClientLevel level, Blueprint blueprint, Placement placement, int fromLayer) {
        BlockState[] transformed = new BlockState[blueprint.palette.size()];
        for (int i = 0; i < transformed.length; i++) {
            transformed[i] = placement.transform(blueprint.palette.get(i));
        }
        List<Block> blocks = new ArrayList<>();
        Long2ObjectOpenHashMap<BlockState> byPos = new Long2ObjectOpenHashMap<>();
        int first = fromLayer < 0 ? 0 : blueprint.layerStart[Math.min(fromLayer, blueprint.sizeY)];
        for (int entry = first; entry < blueprint.count(); entry++) {
            BlockState target = transformed[blueprint.states[entry]];
            BlockPos pos = placement.world(blueprint, blueprint.positions[entry]);
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() == target.getBlock()) {
                continue;
            }
            boolean conflict = fromLayer < 0 && !current.isAir() && !current.canBeReplaced();
            blocks.add(new Block(pos, target, conflict));
            byPos.put(pos.asLong(), target);
        }
        Minecraft minecraft = Minecraft.getInstance();
        RandomSource random = RandomSource.create(42);
        List<BlockStateModelPart> parts = new ArrayList<>();
        int capacity = Math.min(MAX_QUADS, Math.max(64, blocks.size() * 4));
        long[] positions = new long[capacity];
        BakedQuad[] quads = new BakedQuad[capacity];
        int[] colors = new int[capacity];
        int count = 0;
        Direction[] sides = Direction.values();
        outer:
        for (Block block : blocks) {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(block.state());
            parts.clear();
            random.setSeed(42);
            model.collectParts(random, parts);
            int base = block.conflict() ? CONFLICT : TINT;
            int alpha = block.conflict() ? 0xA0 : 0x80;
            for (int side = 0; side <= sides.length; side++) {
                Direction direction = side < sides.length ? sides[side] : null;
                if (direction != null) {
                    BlockState neighbour = byPos.get(block.pos().relative(direction).asLong());
                    if (neighbour != null && neighbour.isSolidRender()) {
                        continue;
                    }
                }
                for (BlockStateModelPart part : parts) {
                    for (BakedQuad quad : part.getQuads(direction)) {
                        if (count == capacity) {
                            if (capacity >= MAX_QUADS) {
                                break outer;
                            }
                            capacity = Math.min(MAX_QUADS, capacity * 2);
                            positions = java.util.Arrays.copyOf(positions, capacity);
                            quads = java.util.Arrays.copyOf(quads, capacity);
                            colors = java.util.Arrays.copyOf(colors, capacity);
                        }
                        int rgb = base;
                        if (quad.materialInfo().isTinted()) {
                            rgb = multiply(rgb, tint(level, block.state(), block.pos(), quad.materialInfo().tintIndex()));
                        }
                        rgb = shade(rgb, quad.direction());
                        positions[count] = block.pos().asLong();
                        quads[count] = quad;
                        colors[count] = (alpha << 24) | (rgb & 0xFFFFFF);
                        count++;
                    }
                }
            }
        }
        return new Mesh(positions, quads, colors, count);
    }

    private static int tint(ClientLevel level, BlockState state, BlockPos pos, int index) {
        try {
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
            if (source == null) {
                return 0xFFFFFF;
            }
            return source.colorInWorld(state, level, pos);
        } catch (RuntimeException e) {
            return 0x7FB238;
        }
    }

    private static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    /** Vanilla's face shading, so the hologram has depth: tops bright, sides darker, bottoms darkest. */
    private static int shade(int rgb, Direction face) {
        float f = switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
