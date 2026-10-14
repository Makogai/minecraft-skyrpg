package com.skyrpg.vfx;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.skyrpg.vfx.data.EffectDef;
import com.skyrpg.vfx.data.VfxLibrary;
import com.skyrpg.vfx.net.VfxPayloads;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The server's side of VFX: loads {@code data/<ns>/skyrpg/vfx/} and {@code data/<ns>/skyrpg/vfx_bindings/} on start and
 * every /reload (or just these with /vfx reload), sends them to every client, and sends play requests.
 *
 * <p>Clients get the definitions from the server ({@link VfxPayloads.Sync}) rather than shipping copies in assets: the
 * server's data packs are then the only source of truth, datapack overrides and /reload reach every player at once,
 * and a client can never draw an outdated version of an effect.
 */
public final class VfxServer {
    public static final String EFFECTS_DIR = "skyrpg/vfx";
    public static final String BINDINGS_DIR = "skyrpg/vfx_bindings";
    /** Point effects reach players this far beyond the effect's own range. */
    private static final double SEND_MARGIN = 16;

    private static volatile VfxLibrary library = VfxLibrary.EMPTY;
    private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

    private VfxServer() {
    }

    static void init() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SkyRpgVfx.id("effects"), new Loader());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> syncAll(server));
    }

    public static VfxLibrary library() {
        return library;
    }

    /** Re-reads only the VFX files (no full /reload) and sends them to everyone. */
    public static VfxLibrary reload(MinecraftServer server) {
        VfxLibrary fresh = read(server.getResourceManager());
        library = fresh;
        WARNED.clear();
        fresh.log("server");
        syncAll(server);
        return fresh;
    }

    private static VfxLibrary read(ResourceManager resources) {
        return VfxLibrary.build(files(resources, EFFECTS_DIR), files(resources, BINDINGS_DIR));
    }

    /** Every .json directly under {@code data/<ns>/<directory>/} (and its sub-folders), keyed by ns:relative/path. */
    private static Map<Identifier, JsonElement> files(ResourceManager resources, String directory) {
        Map<Identifier, JsonElement> files = new HashMap<>();
        String prefix = directory + "/";
        resources.listResources(directory, path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            String path = file.getPath();
            if (!path.startsWith(prefix)) {
                return; // e.g. skyrpg/vfx_bindings when listing skyrpg/vfx
            }
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(prefix.length(), path.length() - ".json".length()));
            try (Reader reader = resource.openAsReader()) {
                files.put(id, JsonParser.parseReader(reader));
            } catch (IOException | JsonParseException e) {
                SkyRpgVfx.LOGGER.error("Skipping unreadable VFX file {}", file, e);
            }
        });
        return files;
    }

    static void syncAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(VfxServer::sync);
    }

    static void sync(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, VfxPayloads.Sync.TYPE)) {
            ServerPlayNetworking.send(player, syncPayload());
        }
    }

    private static VfxPayloads.Sync syncPayload() {
        VfxLibrary current = library;
        Map<String, String> effects = new LinkedHashMap<>();
        current.effectJson().forEach((id, json) -> effects.put(id.toString(), json));
        Map<String, String> bindings = new LinkedHashMap<>();
        current.bindingJson().forEach((id, json) -> bindings.put(id.toString(), json));
        return new VfxPayloads.Sync(effects, bindings);
    }

    // ------------------------------------------------------------------------------------------------ playing

    static void play(ServerLevel level, Identifier effect, Vec3 pos) {
        EffectDef def = known(effect);
        if (def == null) {
            return;
        }
        VfxPayloads.Play payload = new VfxPayloads.Play(effect, -1, pos.x, pos.y, pos.z);
        send(PlayerLookup.around(level, pos, Math.min(160, def.range() + SEND_MARGIN)), payload);
    }

    static void play(Entity anchor, Identifier effect) {
        if (!(anchor.level() instanceof ServerLevel) || known(effect) == null) {
            return;
        }
        VfxPayloads.Play payload = new VfxPayloads.Play(effect, anchor.getId(), anchor.getX(), anchor.getY(), anchor.getZ());
        send(PlayerLookup.tracking(anchor), payload);
        if (anchor instanceof ServerPlayer self && ServerPlayNetworking.canSend(self, VfxPayloads.Play.TYPE)) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    private static void send(Collection<ServerPlayer> players, VfxPayloads.Play payload) {
        for (ServerPlayer player : players) {
            if (ServerPlayNetworking.canSend(player, VfxPayloads.Play.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static EffectDef known(Identifier effect) {
        EffectDef def = library.effect(effect);
        if (def == null && WARNED.add(effect)) {
            SkyRpgVfx.LOGGER.warn("Vfx.play: unknown effect {} (see /vfx list)", effect);
        }
        return def;
    }

    private static final class Loader extends SimpleReloadListener<VfxLibrary> {
        @Override
        protected VfxLibrary prepare(SharedState state) {
            return read(state.resourceManager());
        }

        @Override
        protected void apply(VfxLibrary loaded, SharedState state) {
            library = loaded;
            WARNED.clear();
            loaded.log("server");
        }
    }
}
