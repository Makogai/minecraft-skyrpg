package com.skyrpg.utils.client.map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /waypoint} (alias {@code /wp}): {@code add <name> [x y z]}, {@code remove <name>}, {@code list},
 * {@code track <name> [x y z]}, {@code untrack}, {@code share <name>}. Names with spaces go in quotes. All client-side.
 */
final class WaypointCommands {
    private static final SuggestionProvider<FabricClientCommandSource> NAMES = (context, builder) -> {
        for (Waypoints.Waypoint waypoint : Waypoints.list()) {
            String name = waypoint.name();
            if (name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase().replace("\"", ""))) {
                builder.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
            }
        }
        return builder.buildFuture();
    };

    private WaypointCommands() {
    }

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            var root = dispatcher.register(build("waypoint"));
            dispatcher.register(ClientCommands.literal("wp").redirect(root));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                .executes(context -> add(context, false))
                                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                        .executes(context -> add(context, true)))))))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.string()).suggests(NAMES).executes(WaypointCommands::remove)))
                .then(ClientCommands.literal("list").executes(WaypointCommands::list))
                .then(ClientCommands.literal("track")
                        .then(ClientCommands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .executes(context -> track(context, false))
                                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                        .executes(context -> track(context, true)))))))
                .then(ClientCommands.literal("untrack").executes(context -> {
                    Waypoints.untrack();
                    context.getSource().sendFeedback(Waypoints.prefix().append(Component.literal("No longer tracking anything.").withColor(0xFFC8CEDA)));
                    return 1;
                }))
                .then(ClientCommands.literal("share")
                        .then(ClientCommands.argument("name", StringArgumentType.string()).suggests(NAMES).executes(WaypointCommands::share)));
    }

    private static int add(CommandContext<FabricClientCommandSource> context, boolean at) {
        String name = StringArgumentType.getString(context, "name");
        var player = context.getSource().getPlayer();
        int x = at ? IntegerArgumentType.getInteger(context, "x") : player.getBlockX();
        int y = at ? IntegerArgumentType.getInteger(context, "y") : player.getBlockY();
        int z = at ? IntegerArgumentType.getInteger(context, "z") : player.getBlockZ();
        Waypoints.Waypoint old = Waypoints.find(name);
        int color = old != null ? old.color() : Waypoints.COLORS[Waypoints.list().size() % Waypoints.COLORS.length];
        Waypoints.add(name, x, y, z, color);
        context.getSource().sendFeedback(Waypoints.prefix().append(Component.literal("Saved " + name + " at " + x + ", " + y + ", " + z + ".").withColor(0xFFC8CEDA)));
        return 1;
    }

    private static int remove(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (Waypoints.remove(name)) {
            context.getSource().sendFeedback(Waypoints.prefix().append(Component.literal("Removed " + name + ".").withColor(0xFFC8CEDA)));
            return 1;
        }
        context.getSource().sendError(Component.literal("No waypoint called " + name + " here."));
        return 0;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        var waypoints = Waypoints.list();
        if (waypoints.isEmpty()) {
            context.getSource().sendFeedback(Waypoints.prefix().append(Component.literal("None in this dimension. /waypoint add <name>").withColor(0xFFC8CEDA)));
            return 0;
        }
        var player = context.getSource().getPlayer();
        for (Waypoints.Waypoint waypoint : waypoints) {
            double distance = Math.sqrt(player.distanceToSqr(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5));
            String quoted = "\"" + waypoint.name() + "\"";
            context.getSource().sendFeedback(Component.literal(waypoint.name()).withColor(waypoint.color())
                    .append(Component.literal("  " + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() + "  ·  " + WaypointHud.distance(distance) + "  ").withColor(0xFF8D96A8))
                    .append(Waypoints.link("[Track]", "/waypoint track " + quoted, "Point me there", Waypoints.AQUA))
                    .append(Component.literal(" "))
                    .append(Waypoints.link("[Share]", "/waypoint share " + quoted, "Post it in chat", Waypoints.GREEN))
                    .append(Component.literal(" "))
                    .append(Waypoints.link("[Remove]", "/waypoint remove " + quoted, "Delete it", Waypoints.RED)));
        }
        return waypoints.size();
    }

    private static int track(CommandContext<FabricClientCommandSource> context, boolean at) {
        String name = StringArgumentType.getString(context, "name");
        if (at) {
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");
            Waypoints.track(name, x + 0.5, y, z + 0.5, Waypoints.AQUA);
        } else if (!Waypoints.track(name)) {
            context.getSource().sendError(Component.literal("No waypoint called " + name + " here."));
            return 0;
        }
        context.getSource().sendFeedback(Waypoints.prefix().append(Component.literal("Tracking " + name + ". /waypoint untrack to stop.").withColor(0xFFC8CEDA)));
        return 1;
    }

    private static int share(CommandContext<FabricClientCommandSource> context) {
        Waypoints.Waypoint waypoint = Waypoints.find(StringArgumentType.getString(context, "name"));
        if (waypoint == null) {
            context.getSource().sendError(Component.literal("No waypoint called " + StringArgumentType.getString(context, "name") + " here."));
            return 0;
        }
        Waypoints.share(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z());
        return 1;
    }
}
