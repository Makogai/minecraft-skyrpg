package com.skyrpg.utils.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.utils.client.map.WorldMapScreen;
import net.minecraft.client.Minecraft;

/** The utilities' rows on the Settings page, under "Map" and "Info". */
public final class UtilsSettings {
    public static final String MAP = "Map";
    public static final String INFO = "Info";

    private static Setting minimap;
    private static Setting rotate;
    private static Setting caves;
    private static Setting players;
    private static Setting markers;
    private static Setting wpWorld;
    private static Setting wpMinimap;
    private static Setting wpDistance;
    private static Setting wpSize;
    private static Setting look;
    private static Setting lookEntities;
    private static Setting coords;
    private static Setting biome;
    private static Setting time;
    private static Setting fps;

    private UtilsSettings() {
    }

    static void init() {
        minimap = SkyRpgSettings.toggle("utils.minimap", MAP, "Minimap", "A small map of the area around you, top right", true, null);
        rotate = SkyRpgSettings.toggle("utils.minimap_rotate", MAP, "Turn the minimap with you", "The way you face is always up, instead of north", false, null);
        caves = SkyRpgSettings.toggle("utils.minimap_caves", MAP, "Cave view", "Underground, show the floor around you", true, null);
        players = SkyRpgSettings.toggle("utils.map_players", MAP, "Players on the map", "Other players' faces on both maps", true, null);
        markers = SkyRpgSettings.toggle("utils.map_markers", MAP, "NPCs and waypoints", "Icons for NPCs, your waypoints and more on both maps", true, null);
        SkyRpgSettings.action("utils.open_map", MAP, "World map", "Everything you've explored (M)", "Open",
                () -> Minecraft.getInstance().gui.setScreen(new WorldMapScreen()));
        wpWorld = SkyRpgSettings.toggle("utils.wp_world", MAP, "Waypoints in the world", "A diamond with the name and distance where your waypoints are; the tracked one is always shown", true, null).section("Waypoints");
        wpMinimap = SkyRpgSettings.toggle("utils.wp_minimap", MAP, "Waypoints on the minimap", "Their markers, and an arrow on the edge when they're off the map", true, null).section("Waypoints");
        wpDistance = SkyRpgSettings.slider("utils.wp_distance", MAP, "Waypoint distance", "How far away a waypoint still shows in the world and as an arrow",
                100, 5000, 100, 2000, null).format(v -> v >= 5000 ? "Unlimited" : Math.round(v) + " blocks").section("Waypoints");
        wpSize = SkyRpgSettings.slider("utils.wp_size", MAP, "Waypoint marker size", "How big the diamond in the world is",
                6, 24, 1, 10, null).format(v -> Math.round(v) + " px").section("Waypoints");
        look = SkyRpgSettings.toggle("utils.look", INFO, "Look-at info", "What you're looking at, top centre", true, null);
        lookEntities = SkyRpgSettings.toggle("utils.look_entities", INFO, "Creatures and players too", "Mobs, NPCs and players, with their health", true, null);
        coords = SkyRpgSettings.toggle("utils.coords", INFO, "Coordinates", "Your position and the way you're facing", true, null);
        biome = SkyRpgSettings.toggle("utils.biome", INFO, "Biome", "The biome you're standing in", true, null);
        time = SkyRpgSettings.toggle("utils.time", INFO, "Time of day", "The day number and the time on a 24-hour clock", true, null);
        fps = SkyRpgSettings.toggle("utils.fps", INFO, "FPS", "Frames per second", false, null);
    }

    public static Setting minimap() {
        return minimap;
    }

    public static Setting rotate() {
        return rotate;
    }

    public static Setting caves() {
        return caves;
    }

    public static Setting players() {
        return players;
    }

    public static Setting markers() {
        return markers;
    }

    public static Setting wpWorld() {
        return wpWorld;
    }

    public static Setting wpMinimap() {
        return wpMinimap;
    }

    /** How far a waypoint still shows, in blocks (unlimited at the slider's top). */
    public static double wpMaxDistance() {
        return wpDistance.number() >= wpDistance.max() ? Double.MAX_VALUE : wpDistance.number();
    }

    public static int wpSize() {
        return wpSize.intValue();
    }

    public static Setting look() {
        return look;
    }

    public static Setting lookEntities() {
        return lookEntities;
    }

    public static Setting coords() {
        return coords;
    }

    public static Setting biome() {
        return biome;
    }

    public static Setting time() {
        return time;
    }

    public static Setting fps() {
        return fps;
    }
}
