package com.skyrpg.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.events.net.ShowcasePayloads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Billboards: pictures from web links shown as big flat panels in the world (the hub's event board), drawn by the client
 * mod. A billboard has a centre, the way it faces (yaw), a size in blocks, one or more pictures (a slideshow, one every
 * {@code seconds}) and an optional frame colour. Saved in {@code <world>/skyrpg/billboards.json}; every player gets the
 * whole list on joining and whenever it changes.
 *
 * <p>Ops: look at a wall and {@code /billboard add <id> <width> <height> <url>}; then {@code image}, {@code clear},
 * {@code size}, {@code nudge}, {@code move}, {@code seconds}, {@code frame}, {@code remove}, {@code list}. From the console,
 * {@code execute as <player> at @s run billboard ...}.
 */
public final class Billboards {
    private static final double REACH = 64;

    static final class Board {
        String dim;
        double x;
        double y;
        double z;
        float yaw;
        double width;
        double height;
        final List<String> images = new ArrayList<>();
        int seconds = 8;
        int frame = 0xFF2A1A40;
    }

    private static final Map<String, Board> BOARDS = new LinkedHashMap<>();
    private static MinecraftServer server;

    private Billboards() {
    }

    static void init() {
        ShowcasePayloads.register();
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            read();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> send(handler.player)));
    }

    // ---------------------------------------------------------------- commands

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("billboard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(ctx -> {
                    if (BOARDS.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("No billboards yet. Look at a wall: /billboard add <id> <width> <height> <url>"), false);
                    }
                    BOARDS.forEach((id, b) -> ctx.getSource().sendSuccess(() -> Component.literal(id + ": " + fmt(b.width) + "x" + fmt(b.height)
                            + " at " + (int) b.x + " " + (int) b.y + " " + (int) b.z + " (" + b.dim + "), " + b.images.size() + " picture"
                            + (b.images.size() == 1 ? "" : "s") + ", every " + b.seconds + "s"), false));
                    return BOARDS.size();
                }))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("width", DoubleArgumentType.doubleArg(0.5, 64))
                                        .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.5, 64))
                                                .then(Commands.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
                                                    Board board = new Board();
                                                    board.width = DoubleArgumentType.getDouble(ctx, "width");
                                                    board.height = DoubleArgumentType.getDouble(ctx, "height");
                                                    board.images.add(url(ctx));
                                                    place(board, ctx.getSource().getPlayerOrException());
                                                    BOARDS.put(id, board);
                                                    changed();
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Billboard " + id + " placed"), true);
                                                    return 1;
                                                }))))))
                .then(Commands.literal("image")
                        .then(board().then(Commands.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                            Board board = get(ctx);
                            board.images.add(url(ctx));
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Added picture " + board.images.size()), true);
                            return board.images.size();
                        }))))
                .then(Commands.literal("clear")
                        .then(board().then(Commands.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                            Board board = get(ctx);
                            board.images.clear();
                            board.images.add(url(ctx));
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Billboard now shows only that picture"), true);
                            return 1;
                        }))))
                .then(Commands.literal("size")
                        .then(board().then(Commands.argument("width", DoubleArgumentType.doubleArg(0.5, 64))
                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.5, 64)).executes(ctx -> {
                                    Board board = get(ctx);
                                    board.width = DoubleArgumentType.getDouble(ctx, "width");
                                    board.height = DoubleArgumentType.getDouble(ctx, "height");
                                    changed();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Resized"), true);
                                    return 1;
                                })))))
                .then(Commands.literal("nudge")
                        .then(board().then(Commands.argument("dx", DoubleArgumentType.doubleArg(-16, 16))
                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-16, 16))
                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-16, 16)).executes(ctx -> {
                                            Board board = get(ctx);
                                            board.x += DoubleArgumentType.getDouble(ctx, "dx");
                                            board.y += DoubleArgumentType.getDouble(ctx, "dy");
                                            board.z += DoubleArgumentType.getDouble(ctx, "dz");
                                            changed();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Moved"), true);
                                            return 1;
                                        }))))))
                .then(Commands.literal("move")
                        .then(board().executes(ctx -> {
                            place(get(ctx), ctx.getSource().getPlayerOrException());
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Moved to where you're looking"), true);
                            return 1;
                        })))
                .then(Commands.literal("seconds")
                        .then(board().then(Commands.argument("seconds", IntegerArgumentType.integer(2, 600)).executes(ctx -> {
                            get(ctx).seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Slideshow timing changed"), true);
                            return 1;
                        }))))
                .then(Commands.literal("frame")
                        .then(board().then(Commands.argument("color", StringArgumentType.word()).executes(ctx -> {
                            String color = StringArgumentType.getString(ctx, "color");
                            get(ctx).frame = color.equals("none") ? 0 : Json.color(new com.google.gson.JsonPrimitive(color), 0xFF2A1A40);
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Frame changed"), true);
                            return 1;
                        }))))
                .then(Commands.literal("remove")
                        .then(board().executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            BOARDS.remove(id);
                            changed();
                            ctx.getSource().sendSuccess(() -> Component.literal("Removed " + id), true);
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> board() {
        return Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(BOARDS.keySet(), b));
    }

    private static Board get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Board board = BOARDS.get(StringArgumentType.getString(ctx, "id"));
        if (board == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No such billboard")).create();
        }
        return board;
    }

    private static String url(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String url = StringArgumentType.getString(ctx, "url").trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("The picture must be a web link (https://...)")).create();
        }
        return url;
    }

    /** On the block face the player looks at (a wall), or four blocks in front of them facing them. */
    private static void place(Board board, ServerPlayer player) {
        HitResult hit = player.pick(REACH, 1, false);
        board.dim = player.level().dimension().identifier().toString();
        Vec3 at;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getDirection().getAxis().isHorizontal()) {
            board.yaw = block.getDirection().toYRot();
            at = hit.getLocation();
        } else {
            board.yaw = Direction.fromYRot(player.getYRot() + 180).toYRot();
            at = player.getEyePosition().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
        }
        double rad = Math.toRadians(board.yaw);
        // A hair in front of the wall so it doesn't flicker into it.
        board.x = at.x - Math.sin(rad) * 0.04;
        board.y = at.y;
        board.z = at.z + Math.cos(rad) * 0.04;
    }

    private static String fmt(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // ---------------------------------------------------------------- sync and saving

    private static void changed() {
        write();
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(Billboards::send);
        }
    }

    private static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ShowcasePayloads.Billboards.TYPE)) {
            ServerPlayNetworking.send(player, new ShowcasePayloads.Billboards(json().toString()));
        }
    }

    private static JsonArray json() {
        JsonArray list = new JsonArray();
        BOARDS.forEach((id, b) -> {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("dim", b.dim);
            json.addProperty("x", b.x);
            json.addProperty("y", b.y);
            json.addProperty("z", b.z);
            json.addProperty("yaw", b.yaw);
            json.addProperty("w", b.width);
            json.addProperty("h", b.height);
            JsonArray images = new JsonArray();
            b.images.forEach(images::add);
            json.add("images", images);
            json.addProperty("seconds", b.seconds);
            json.addProperty("frame", b.frame);
            list.add(json);
        });
        return list;
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg").resolve("billboards.json");
    }

    private static void read() {
        BOARDS.clear();
        try {
            Path file = file();
            if (!Files.isRegularFile(file)) {
                return;
            }
            for (JsonElement element : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                Board b = new Board();
                b.dim = json.get("dim").getAsString();
                b.x = json.get("x").getAsDouble();
                b.y = json.get("y").getAsDouble();
                b.z = json.get("z").getAsDouble();
                b.yaw = json.get("yaw").getAsFloat();
                b.width = json.get("w").getAsDouble();
                b.height = json.get("h").getAsDouble();
                json.getAsJsonArray("images").forEach(e -> b.images.add(e.getAsString()));
                b.seconds = Json.integer(json, 8, "seconds");
                b.frame = Json.integer(json, 0xFF2A1A40, "frame");
                BOARDS.put(json.get("id").getAsString(), b);
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't read the billboards", e);
        }
    }

    private static void write() {
        if (server == null) {
            return;
        }
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("billboards.json.tmp");
            Files.writeString(temp, json().toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't save the billboards", e);
        }
    }
}
