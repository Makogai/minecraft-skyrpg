package com.skyrpg.utils.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Your own named, coloured points, per server and dimension (kept next to the map tiles in {@code waypoints.json}),
 * and the one place you're being pointed at (the <i>tracked</i> target). Made by {@code /waypoint} (alias
 * {@code /wp}), a right-click on the world map, a death ("Last death"), a chat link from a friend, or by code:
 * <pre>
 * Waypoints.add("Crypt", 120, 64, -30, Waypoints.GOLD);   // here, this dimension
 * Waypoints.track("Crypt", 120, 64, -30, Waypoints.GOLD); // points you there (one target at a time)
 * Waypoints.untrack();
 * </pre>
 * Servers can set the target with {@link WaypointTarget}. Shown through {@link MapMarkers} on both maps, as
 * arrows on the minimap edge ({@link WaypointDraw}), in the world ({@link WaypointHud}) and in the compass ribbon
 * ({@link WaypointRibbon}).
 */
public final class Waypoints {
    public static final int GOLD = 0xFFF2B33D;
    public static final int AQUA = 0xFF5CE1E6;
    public static final int PURPLE = 0xFFB98CFF;
    public static final int GREEN = 0xFF5CD68A;
    public static final int RED = 0xFFFF6464;
    public static final int BLUE = 0xFF5CA8FF;
    public static final int ORANGE = 0xFFFF9A4D;
    public static final int WHITE = 0xFFE8ECF4;
    /** The colours on offer when making one. */
    public static final int[] COLORS = {GOLD, AQUA, PURPLE, GREEN, RED, BLUE, ORANGE, WHITE};
    public static final String DEATH = "Last death";

    static final String PREFIX = "waypoint/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** What a shared waypoint looks like in chat; friends' clients turn it into a clickable line. */
    private static final Pattern SHARED = Pattern.compile("\\[waypoint: ([^\\[\\]]{1,32}?) (-?\\d+) (-?\\d+) (-?\\d+)]");

    /** A saved point. */
    public record Waypoint(String name, int x, int y, int z, int color) {
    }

    /** Where you're being pointed. {@code dimension} null = wherever you are. */
    public record Target(Identifier dimension, String name, double x, double y, double z, int color) {
    }

    private static List<Waypoint> list = new ArrayList<>();
    private static Path file;
    private static Target tracked;
    private static boolean wasDead;

    private Waypoints() {
    }

    public static void init() {
        MapMarkers.register((dimension, out) -> {
            if (!MapCache.active() || !MapCache.dimension().identifier().equals(dimension)) {
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                Waypoint waypoint = list.get(i);
                out.accept(new MapMarkers.Marker(SkyRpgUtilsClient.id(PREFIX + i), waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5,
                        new MapMarkers.Dot(waypoint.color()), Component.literal(waypoint.name()).withColor(waypoint.color()),
                        List.of(Component.literal("Your waypoint").withColor(0xFF8D96A8),
                                Component.literal("Click it for options").withColor(0xFF586174)),
                        waypoint.color()));
            }
            Target target = tracked();
            if (target != null && !isOwnPlace(target.x(), target.z())) {
                out.accept(new MapMarkers.Marker(SkyRpgUtilsClient.id("target"), target.x(), target.y(), target.z(),
                        new MapMarkers.Dot(target.color()), Component.literal(target.name()).withColor(target.color()),
                        List.of(Component.literal("Tracked target").withColor(0xFF8D96A8)), target.color()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(WaypointTarget.TYPE, (payload, context) -> context.client().execute(() -> {
            if (payload.clear()) {
                untrack();
            } else {
                Identifier dimension = payload.dimension().isEmpty() ? null : Identifier.tryParse(payload.dimension());
                track(dimension, payload.name(), payload.x() + 0.5, payload.y(), payload.z() + 0.5, payload.color());
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(Waypoints::tick);
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, bound, time) -> offerShared(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                offerShared(message);
            }
        });
        WaypointCommands.init();
        WaypointHud.init();
    }

    // ------------------------------------------------------------------ the list

    /** Loads the waypoints of the map folder now in use (called when the server or dimension changes). */
    static void use(Path folder) {
        file = folder == null ? null : folder.resolve("waypoints.json");
        list = new ArrayList<>();
        if (folder == null) {
            tracked = null;
        }
        if (file != null && Files.isRegularFile(file)) {
            try {
                Waypoint[] read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Waypoint[].class);
                if (read != null) {
                    list.addAll(Arrays.asList(read));
                }
            } catch (IOException | JsonParseException e) {
                SkyRpgUtilsClient.LOGGER.warn("Couldn't read waypoints {}", file, e);
            }
        }
        recoverUnknown(folder);
    }

    /**
     * Waypoints set while the server wasn't known yet were saved under "unknown" and looked lost on the next join.
     * The first time a real server loads this dimension, they're moved into its list (once; the old file is renamed).
     */
    private static void recoverUnknown(Path folder) {
        if (folder == null || folder.getParent() == null || folder.getParent().getParent() == null) {
            return;
        }
        Path world = folder.getParent().getParent();
        if (world.getFileName().toString().equals("unknown") || world.getParent() == null) {
            return;
        }
        Path stray = world.getParent().resolve("unknown").resolve(world.relativize(folder)).resolve("waypoints.json");
        if (!Files.isRegularFile(stray)) {
            return;
        }
        try {
            Waypoint[] read = GSON.fromJson(Files.readString(stray, StandardCharsets.UTF_8), Waypoint[].class);
            if (read != null) {
                for (Waypoint waypoint : read) {
                    if (list.stream().noneMatch(w -> w.x() == waypoint.x() && w.y() == waypoint.y() && w.z() == waypoint.z())) {
                        list.add(waypoint);
                    }
                }
                save();
            }
            Files.move(stray, stray.resolveSibling("waypoints.json.recovered"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JsonParseException e) {
            SkyRpgUtilsClient.LOGGER.warn("Couldn't recover waypoints {}", stray, e);
        }
    }

    /** This dimension's waypoints. */
    public static List<Waypoint> list() {
        return List.copyOf(list);
    }

    /** A waypoint by name (case-insensitive), or null. */
    public static Waypoint find(String name) {
        for (Waypoint waypoint : list) {
            if (waypoint.name().equalsIgnoreCase(name)) {
                return waypoint;
            }
        }
        return null;
    }

    /** Adds (or, with the same name, replaces) a waypoint in the dimension you're in. */
    public static void add(String name, int x, int y, int z, int color) {
        Waypoint made = new Waypoint(name.strip().isEmpty() ? nextName() : name.strip(), x, y, z, color);
        list.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(made.name()));
        list.add(made);
        save();
    }

    /** A waypoint with the next free "Waypoint N" name and the next colour. */
    public static void add(int x, int y, int z) {
        add(nextName(), x, y, z, COLORS[list.size() % COLORS.length]);
    }

    public static String nextName() {
        int number = 1;
        while (find("Waypoint " + number) != null) {
            number++;
        }
        return "Waypoint " + number;
    }

    /** Removes by name; false if there's none. */
    public static boolean remove(String name) {
        Waypoint waypoint = find(name);
        if (waypoint == null) {
            return false;
        }
        list.remove(waypoint);
        save();
        return true;
    }

    /** The waypoint behind this marker, or null if it isn't one of ours. */
    public static Waypoint of(MapMarkers.Marker marker) {
        Identifier id = marker.id();
        if (!id.getNamespace().equals(SkyRpgUtilsClient.MOD_ID) || !id.getPath().startsWith(PREFIX)) {
            return null;
        }
        try {
            int index = Integer.parseInt(id.getPath().substring(PREFIX.length()));
            return index >= 0 && index < list.size() ? list.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Removes the waypoint behind this marker; false if it isn't one of ours. */
    static boolean remove(MapMarkers.Marker marker) {
        Waypoint waypoint = of(marker);
        return waypoint != null && remove(waypoint.name());
    }

    private static boolean isOwnPlace(double x, double z) {
        for (Waypoint waypoint : list) {
            if (Math.abs(waypoint.x() + 0.5 - x) < 1 && Math.abs(waypoint.z() + 0.5 - z) < 1) {
                return true;
            }
        }
        return false;
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(list.toArray(new Waypoint[0])), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyRpgUtilsClient.LOGGER.warn("Couldn't save waypoints {}", file, e);
        }
    }

    // ------------------------------------------------------------------ tracking

    /** Tracks a place in the dimension you're in. {@code color} is ARGB. */
    public static void track(String name, double x, double y, double z, int color) {
        track(MapCache.active() ? MapCache.dimension().identifier() : null, name, x, y, z, color);
    }

    /** Tracks a place in a given dimension (null = wherever you are). */
    public static void track(Identifier dimension, String name, double x, double y, double z, int color) {
        tracked = new Target(dimension, name, x, y, z, color);
    }

    /** Tracks a saved waypoint; false if there's none by that name. */
    public static boolean track(String name) {
        Waypoint waypoint = find(name);
        if (waypoint == null) {
            return false;
        }
        track(waypoint.name(), waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5, waypoint.color());
        return true;
    }

    public static void untrack() {
        tracked = null;
    }

    /** The target being tracked, if it's in the dimension you're in. */
    public static Target tracked() {
        if (tracked == null || tracked.dimension() != null && (!MapCache.active() || !tracked.dimension().equals(MapCache.dimension().identifier()))) {
            return null;
        }
        return tracked;
    }

    static boolean isTracked(double x, double z) {
        Target target = tracked();
        return target != null && Math.abs(target.x() - x) < 1 && Math.abs(target.z() - z) < 1;
    }

    // ------------------------------------------------------------------ death, arriving

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || !MapCache.active()) {
            return;
        }
        boolean dead = minecraft.player.isDeadOrDying();
        if (dead && !wasDead) {
            add(DEATH, minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ(), RED);
        }
        wasDead = dead;
        Target target = tracked();
        if (target != null && !dead && minecraft.player.tickCount % 10 == 0) {
            double dx = minecraft.player.getX() - target.x();
            double dz = minecraft.player.getZ() - target.z();
            if (dx * dx + dz * dz < 9 && Math.abs(minecraft.player.getY() - target.y()) < 8) {
                untrack();
                minecraft.player.sendSystemMessage(prefix().append(Component.literal("You've arrived at " + target.name() + ".").withColor(0xFFC8CEDA)));
            }
        }
    }

    static MutableComponent prefix() {
        return Component.literal("Waypoint ").withColor(GOLD);
    }

    // ------------------------------------------------------------------ sharing

    /** Posts a waypoint in chat as a line other players' clients turn into a clickable "Add". */
    public static void share(String name, int x, int y, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            String clean = name.replaceAll("[\\[\\]]", "");
            minecraft.player.connection.sendChat("[waypoint: " + (clean.length() > 32 ? clean.substring(0, 32) : clean) + " " + x + " " + y + " " + z + "]");
        }
    }

    /** A chat line with a clickable Add and Track for a waypoint someone shared. */
    private static void offerShared(Component message) {
        Matcher matcher = SHARED.matcher(message.getString());
        Minecraft minecraft = Minecraft.getInstance();
        if (!matcher.find() || minecraft.player == null) {
            return;
        }
        String name = matcher.group(1);
        int x = Integer.parseInt(matcher.group(2));
        int y = Integer.parseInt(matcher.group(3));
        int z = Integer.parseInt(matcher.group(4));
        if (isOwnPlace(x + 0.5, z + 0.5)) {
            return;
        }
        String where = "\"" + name + "\" " + x + " " + y + " " + z;
        Component line = prefix().append(Component.literal(name + " (" + x + ", " + y + ", " + z + ")  ").withColor(0xFFC8CEDA))
                .append(link("[Add]", "/waypoint add " + where, "Save this waypoint", GREEN))
                .append(Component.literal(" "))
                .append(link("[Track]", "/waypoint track " + where, "Point me there", AQUA));
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(line);
            }
        });
    }

    static MutableComponent link(String text, String command, String hover, int color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** Where the waypoint settings are read from, for the other classes. */
    static boolean showOnMinimap() {
        return UtilsSettings.wpMinimap().get();
    }
}
