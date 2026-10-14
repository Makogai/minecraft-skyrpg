package com.skyrpg.devtools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /devtools scan &lt;from&gt; &lt;to&gt; [summary|top|layers]: describes the blocks in a box as text.
 * <ul>
 *   <li>summary: how many of each block</li>
 *   <li>top: a top-down map of the highest non-air block per column, plus a height map</li>
 *   <li>layers: one character grid per y level</li>
 * </ul>
 * Grids have x increasing to the right and z increasing downwards (north at the top); '.' is air.
 */
final class ScanCommand {
    private static final int MAX_BLOCKS = 262_144;
    private static final int MAX_LAYER_BLOCKS = 32_768;
    private static final String SYMBOLS = "#@%&*+=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$^~!?<>/|";

    private ScanCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("devtools")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("scan")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> scan(ctx, "summary"))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("summary", "top", "layers"), builder))
                                                .executes(ctx -> scan(ctx, StringArgumentType.getString(ctx, "mode"))))))));
    }

    private static int scan(CommandContext<CommandSourceStack> ctx, String mode) {
        CommandSourceStack source = ctx.getSource();
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos b = BlockPosArgument.getBlockPos(ctx, "to");
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        long volume = (long) sx * sy * sz;
        if (volume > MAX_BLOCKS || (mode.equals("layers") && volume > MAX_LAYER_BLOCKS)) {
            source.sendFailure(Component.literal("Too big: " + volume + " blocks (max " + (mode.equals("layers") ? MAX_LAYER_BLOCKS : MAX_BLOCKS) + " for " + mode + ")"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        String[][][] names = new String[sx][sy][sz];
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockState state = level.getBlockState(min.offset(x, y, z));
                    String name = state.isAir() ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().replace("minecraft:", "");
                    names[x][y][z] = name;
                    if (name != null) {
                        counts.merge(name, 1, Integer::sum);
                    }
                }
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("Scan of ").append(level.dimension().identifier()).append(" from ").append(min.toShortString())
                .append(" to ").append(max.toShortString()).append(" (").append(sx).append("x").append(sy).append("x").append(sz)
                .append(", x right, z down, '.' air)\n");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        Map<String, Character> legend = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            legend.put(entry.getKey(), legend.size() < SYMBOLS.length() ? SYMBOLS.charAt(legend.size()) : '?');
        }
        out.append("Blocks: ");
        if (sorted.isEmpty()) {
            out.append("none (all air)");
        }
        for (Map.Entry<String, Integer> entry : sorted) {
            out.append(legend.get(entry.getKey())).append('=').append(entry.getKey()).append(' ').append(entry.getValue()).append("  ");
        }
        out.append('\n');

        if (mode.equals("top")) {
            StringBuilder heights = new StringBuilder("Height of that block (y - ").append(min.getY()).append(", base 36: 0-9 then a-z):\n");
            out.append("Top-down:\n");
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int top = -1;
                    for (int y = sy - 1; y >= 0; y--) {
                        if (names[x][y][z] != null) {
                            top = y;
                            break;
                        }
                    }
                    out.append(top < 0 ? '.' : legend.get(names[x][top][z]));
                    heights.append(top < 0 ? '.' : top < 36 ? Character.forDigit(top, 36) : '+');
                }
                out.append('\n');
                heights.append('\n');
            }
            out.append(heights);
        } else if (mode.equals("layers")) {
            for (int y = sy - 1; y >= 0; y--) {
                out.append("y=").append(min.getY() + y).append('\n');
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        out.append(names[x][y][z] == null ? '.' : legend.get(names[x][y][z]));
                    }
                    out.append('\n');
                }
            }
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return counts.size();
    }
}
