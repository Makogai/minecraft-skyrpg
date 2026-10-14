package com.skyrpg.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hub layout, loaded from {@code data/<namespace>/skyrpg/hub/*.json} (the last file by id wins).
 *
 * @param spawn       where players appear in the hub
 * @param voidY       players falling below this are put back at spawn
 * @param structure   the saved hub build, a structure file in {@code data/<ns>/structure/}
 * @param buildVersion bump this whenever the shipped build changes, so existing worlds rebuild their hub
 * @param buildOrigin  min corner of the build box
 * @param buildSize    size of the build box, used by /hubadmin save and when clearing for a rebuild
 * @param survivalPortals walking into one of these boxes sends you to the survival world
 * @param decor       floating, animated display entities (see {@link HubDecor})
 * @param emitters    spots that keep puffing particles
 */
public record HubConfig(Vec3 spawn, float spawnYaw, float spawnPitch, int voidY, Identifier structure, int buildVersion,
                        BlockPos buildOrigin, Vec3i buildSize, List<AABB> survivalPortals, List<HubDecor.Piece> decor,
                        List<HubDecor.Emitter> emitters) {
    public static final String DIRECTORY = "skyrpg/hub";

    public static final HubConfig DEFAULT = new HubConfig(new Vec3(0.5, 65, 0.5), 0, 0, 20, SkyRpgHub.id("hub"), 0,
            new BlockPos(-64, 24, -64), new Vec3i(128, 96, 128), List.of(), List.of(), List.of());

    private static HubConfig current = DEFAULT;

    public static HubConfig get() {
        return current;
    }

    public BlockPos spawnBlock() {
        return BlockPos.containing(spawn);
    }

    static void load(Map<Identifier, JsonElement> files) {
        files.forEach((id, json) -> {
            try {
                current = parse(json.getAsJsonObject());
            } catch (RuntimeException e) {
                SkyRpgHub.LOGGER.error("Skipping invalid hub config {}: {}", id, e.getMessage());
            }
        });
    }

    private static HubConfig parse(JsonObject json) {
        JsonObject spawn = json.getAsJsonObject("spawn");
        JsonObject build = json.getAsJsonObject("build");
        List<AABB> portals = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("survival_portals")) {
            JsonObject portal = element.getAsJsonObject();
            BlockPos from = blockPos(portal.getAsJsonArray("from"));
            BlockPos to = blockPos(portal.getAsJsonArray("to"));
            // Inclusive block corners -> a box covering both whole blocks.
            portals.add(AABB.encapsulatingFullBlocks(from, to));
        }
        Vec3i size = blockPos(build.getAsJsonArray("size"));
        return new HubConfig(
                new Vec3(spawn.get("x").getAsDouble(), spawn.get("y").getAsDouble(), spawn.get("z").getAsDouble()),
                spawn.get("yaw").getAsFloat(), spawn.get("pitch").getAsFloat(),
                json.get("void_y").getAsInt(),
                Identifier.parse(build.get("structure").getAsString()),
                build.get("version").getAsInt(),
                blockPos(build.getAsJsonArray("origin")),
                new Vec3i(size.getX(), size.getY(), size.getZ()),
                List.copyOf(portals),
                HubDecor.parsePieces(json.getAsJsonArray("decor")),
                HubDecor.parseEmitters(json.getAsJsonArray("emitters")));
    }

    private static BlockPos blockPos(JsonArray array) {
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
