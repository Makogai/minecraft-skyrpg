package com.skyrpg.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Every blueprint a station can use, shared by the whole server: the schematics players have uploaded (saved in the
 * world folder, {@code skyrpg_builder/blueprints/}) and Minecraft's own village houses, igloo, outpost tower and ruined
 * portals, read straight from the game.
 */
public final class Library {
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    public static final int CHUNK_BYTES = 30_000;
    private static final int MAX_PER_PLAYER = 40;
    private static final int MAX_TOTAL = 500;
    /** Vanilla structures offered as blueprints: {path pattern, category}. */
    private static final Pattern VILLAGE = Pattern.compile("village/(plains|desert|savanna|taiga|snowy)/(houses|town_centers)/([a-z0-9_]+)");
    private static final Map<String, String> SINGLES = Map.of(
            "igloo/top", "Igloo",
            "pillager_outpost/watchtower", "Pillager outpost");
    private static final Pattern PORTAL = Pattern.compile("ruined_portal/(portal_\\d+|giant_portal_\\d+)");

    /** What the blueprint picker shows. {@code kind}: {@link Blueprint#KIND_VANILLA} or {@link Blueprint#KIND_UPLOAD}. */
    public record Info(String id, String name, String author, int kind, String category, int sizeX, int sizeY, int sizeZ, int blocks) {
        static Info of(Blueprint blueprint, String category) {
            return new Info(blueprint.id, blueprint.name, blueprint.author, blueprint.kind, category, blueprint.sizeX, blueprint.sizeY,
                    blueprint.sizeZ, blueprint.count());
        }
    }

    private static MinecraftServer server;
    private static Path folder;
    private static final Map<String, Info> UPLOADS = new LinkedHashMap<>();
    private static List<Info> vanilla;
    private static final Map<String, Identifier> VANILLA_IDS = new HashMap<>();
    private static final Map<String, String> VANILLA_NAMES = new HashMap<>();
    private static final Map<String, Blueprint> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Blueprint> eldest) {
            return size() > 24;
        }
    };
    private static final Map<UUID, Upload> UPLOADING = new HashMap<>();

    private Library() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(Library::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            UPLOADS.clear();
            CACHE.clear();
            vanilla = null;
            VANILLA_IDS.clear();
            VANILLA_NAMES.clear();
            UPLOADING.clear();
        });
    }

    private static void load(MinecraftServer started) {
        server = started;
        folder = started.getWorldPath(LevelResource.ROOT).resolve("skyrpg_builder").resolve("blueprints");
        UPLOADS.clear();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".nbt")).sorted().toList()) {
                    String id = file.getFileName().toString().replace(".nbt", "");
                    try {
                        Blueprint blueprint = Blueprint.load(id, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
                        UPLOADS.put(id, Info.of(blueprint, "Uploaded"));
                    } catch (IOException | RuntimeException e) {
                        SkyRpgBuilder.LOGGER.warn("Skipping unreadable blueprint {}: {}", file, e.toString());
                    }
                }
            }
        } catch (IOException e) {
            SkyRpgBuilder.LOGGER.error("Couldn't read the blueprint library at {}", folder, e);
        }
        SkyRpgBuilder.LOGGER.info("Blueprint library: {} uploaded blueprints", UPLOADS.size());
    }

    /** Uploaded ones first (newest first), then the vanilla ones by category. */
    static List<Info> all() {
        List<Info> list = new ArrayList<>(UPLOADS.values());
        java.util.Collections.reverse(list);
        list.addAll(vanilla());
        return list;
    }

    static Optional<Info> info(String id) {
        Info upload = UPLOADS.get(id);
        if (upload != null) {
            return Optional.of(upload);
        }
        return vanilla().stream().filter(i -> i.id().equals(id)).findFirst();
    }

    /** The blueprint with this id, from the cache, the folder or the game's structures; null if it's gone. */
    static Blueprint get(String id) {
        if (id == null || id.isEmpty() || server == null) {
            return null;
        }
        Blueprint cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Blueprint loaded = null;
        if (id.startsWith("v/")) {
            vanilla();
            Identifier structure = VANILLA_IDS.get(id);
            if (structure != null) {
                loaded = readVanilla(id, structure, VANILLA_NAMES.getOrDefault(id, id));
            }
        } else if (UPLOADS.containsKey(id) && id.matches("[a-z0-9]+")) {
            try {
                loaded = Blueprint.load(id, NbtIo.readCompressed(folder.resolve(id + ".nbt"), NbtAccounter.unlimitedHeap()));
            } catch (IOException | RuntimeException e) {
                SkyRpgBuilder.LOGGER.warn("Couldn't read blueprint {}: {}", id, e.toString());
            }
        }
        if (loaded != null) {
            CACHE.put(id, loaded);
        }
        return loaded;
    }

    private static Blueprint readVanilla(String id, Identifier structure, String name) {
        Optional<StructureTemplate> template = server.getStructureTemplateManager().get(structure);
        if (template.isEmpty()) {
            return null;
        }
        try {
            return Schematics.structure(template.get().save(new CompoundTag()), id, name);
        } catch (Schematics.BadFile | RuntimeException e) {
            return null;
        }
    }

    private static List<Info> vanilla() {
        if (vanilla != null || server == null) {
            return vanilla == null ? List.of() : vanilla;
        }
        List<Info> found = new ArrayList<>();
        List<Identifier> ids = server.getStructureTemplateManager().listTemplates()
                .filter(id -> id.getNamespace().equals("minecraft")).sorted(Comparator.comparing(Identifier::getPath)).toList();
        for (Identifier structure : ids) {
            String path = structure.getPath();
            String category = null;
            String name = null;
            Matcher village = VILLAGE.matcher(path);
            if (village.matches()) {
                String biome = village.group(1);
                category = capitalize(biome) + " village";
                name = pretty(village.group(3).replaceFirst("^" + biome + "_", ""));
            } else if (SINGLES.containsKey(path)) {
                category = SINGLES.get(path);
                name = path.startsWith("igloo") ? "Igloo" : "Watchtower";
            } else if (PORTAL.matcher(path).matches()) {
                category = "Ruined portal";
                name = pretty(path.substring(path.indexOf('/') + 1));
            }
            if (category == null) {
                continue;
            }
            String id = "v/" + path;
            VANILLA_IDS.put(id, structure);
            VANILLA_NAMES.put(id, name);
            Blueprint blueprint = readVanilla(id, structure, name);
            if (blueprint != null) {
                found.add(Info.of(blueprint, category));
            }
        }
        // Villages first (the houses are what most people want), then the rest.
        List<String> order = List.of("Plains village", "Taiga village", "Savanna village", "Desert village", "Snowy village", "Igloo",
                "Pillager outpost", "Ruined portal");
        found.sort(Comparator.comparingInt((Info info) -> order.indexOf(info.category())));
        vanilla = List.copyOf(found);
        SkyRpgBuilder.LOGGER.info("Blueprint library: {} vanilla structures", vanilla.size());
        return vanilla;
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /** "small_house_1" → "Small House 1". */
    private static String pretty(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(word.matches("0\\d") ? word.substring(1) : capitalize(word));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ uploads

    private static final class Upload {
        final int session;
        final String fileName;
        final byte[][] parts;
        int received;

        Upload(int session, String fileName, int count) {
            this.session = session;
            this.fileName = fileName;
            this.parts = new byte[count][];
        }
    }

    /** A piece of a file a player is uploading. Returns the result once the last piece is in (null until then). */
    static UploadOutcome chunk(ServerPlayer player, int session, int index, int count, String fileName, byte[] data) {
        int maxParts = (MAX_UPLOAD_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;
        if (count < 1 || count > maxParts || index < 0 || index >= count || data.length > CHUNK_BYTES) {
            UPLOADING.remove(player.getUUID());
            return UploadOutcome.fail("That file is too big (the most is 2 MB).");
        }
        Upload upload = UPLOADING.get(player.getUUID());
        if (upload == null || upload.session != session || upload.parts.length != count) {
            upload = new Upload(session, fileName, count);
            UPLOADING.put(player.getUUID(), upload);
        }
        if (upload.parts[index] == null) {
            upload.parts[index] = data;
            upload.received++;
        }
        if (upload.received < count) {
            return null;
        }
        UPLOADING.remove(player.getUUID());
        int total = 0;
        for (byte[] part : upload.parts) {
            total += part.length;
        }
        byte[] bytes = new byte[total];
        int at = 0;
        for (byte[] part : upload.parts) {
            System.arraycopy(part, 0, bytes, at, part.length);
            at += part.length;
        }
        return add(player, upload.fileName, bytes);
    }

    record UploadOutcome(boolean ok, String message, String id) {
        static UploadOutcome fail(String message) {
            return new UploadOutcome(false, message, "");
        }
    }

    private static UploadOutcome add(ServerPlayer player, String fileName, byte[] bytes) {
        if (server == null) {
            return UploadOutcome.fail("The server isn't ready.");
        }
        String author = player.getGameProfile().name();
        long mine = UPLOADS.values().stream().filter(i -> i.author().equals(author)).count();
        if (mine >= MAX_PER_PLAYER || UPLOADS.size() >= MAX_TOTAL) {
            return UploadOutcome.fail("The library is full for you (" + MAX_PER_PLAYER + " blueprints). Delete one first.");
        }
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase(Locale.ROOT);
        } while (UPLOADS.containsKey(id));
        Schematics.Result result;
        try {
            result = Schematics.read(bytes, fileName, id, author, Blueprint.KIND_UPLOAD);
        } catch (Schematics.BadFile e) {
            return UploadOutcome.fail(e.getMessage());
        } catch (RuntimeException e) {
            SkyRpgBuilder.LOGGER.warn("Couldn't read uploaded schematic {} from {}", fileName, author, e);
            return UploadOutcome.fail("Couldn't read that schematic.");
        }
        Blueprint blueprint = result.blueprint();
        try {
            NbtIo.writeCompressed(blueprint.save(), folder.resolve(id + ".nbt"));
        } catch (IOException e) {
            SkyRpgBuilder.LOGGER.error("Couldn't save blueprint {}", id, e);
            return UploadOutcome.fail("The server couldn't save it.");
        }
        UPLOADS.put(id, Info.of(blueprint, "Uploaded"));
        CACHE.put(id, blueprint);
        SkyRpgBuilder.LOGGER.info("{} uploaded blueprint {} ({}, {} blocks)", author, blueprint.name, id, blueprint.count());
        String message = "Added " + blueprint.name + " (" + blueprint.sizeText() + ", " + String.format("%,d", blueprint.count()) + " blocks)";
        if (result.unknown() > 0) {
            message += ". " + result.unknown() + " unknown block types were left out";
        }
        return new UploadOutcome(true, message + ".", id);
    }

    /** Deletes an uploaded blueprint; its uploader or an operator may. Stations using it keep nothing but its name. */
    static String delete(ServerPlayer player, String id) {
        Info info = UPLOADS.get(id);
        if (info == null) {
            return "You can only delete uploaded blueprints.";
        }
        boolean op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
        if (!info.author().equals(player.getGameProfile().name()) && !op) {
            return "Only " + info.author() + " can delete that one.";
        }
        try {
            Files.deleteIfExists(folder.resolve(id + ".nbt"));
        } catch (IOException e) {
            return "The server couldn't delete it.";
        }
        UPLOADS.remove(id);
        CACHE.remove(id);
        return "";
    }
}
