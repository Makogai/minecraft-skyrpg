package com.skyrpg.hub;

import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Ships the hub build as a structure file and keeps every world's hub up to date with it.
 *
 * <p>Workflow: build in the dev world, {@code /hubadmin save}, copy the saved .nbt into
 * {@code hub/src/main/resources/data/skyrpg_hub/structure/}, then bump {@code build.version} in
 * hub.json. Worlds with an older version get their hub cleared and rebuilt on next start.
 */
final class HubBuild {
    /** Which build version this world's hub has. Stored on the hub level. */
    static final AttachmentType<Integer> PLACED_VERSION = AttachmentRegistry.create(SkyRpgHub.id("placed_build_version"),
            builder -> builder.persistent(Codec.INT));

    private HubBuild() {
    }

    static void init() {
        // Loads the class so the attachment is registered during mod init.
    }

    static void onServerStarted(MinecraftServer server) {
        ServerLevel hub = Hub.level(server);
        if (hub == null) {
            SkyRpgHub.LOGGER.error("Hub dimension {} is missing; is the datapack disabled?", Hub.LEVEL.identifier());
            return;
        }
        makeHubTheWorldSpawn(server);
        int placed = hub.getAttachedOrElse(PLACED_VERSION, 0);
        int shipped = HubConfig.get().buildVersion();
        if (placed < shipped && place(server, hub, placed > 0)) {
            hub.setAttached(PLACED_VERSION, shipped);
            SkyRpgHub.LOGGER.info("Placed hub build version {}", shipped);
            // The floating texts (bank, Wildgate) are entities, not part of the saved structure: put them back with it.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(hub).withSuppressedOutput(),
                    "function skyrpg_hub:hub_texts");
        } else if (placed == 0) {
            placeSpawnPlatform(hub);
        }
    }

    /**
     * New players and anyone dying without a bed appear in the hub. The overworld's own spawn is
     * remembered as the survival spawn; running /setworldspawn in the overworld moves it (applied on restart).
     */
    static void makeHubTheWorldSpawn(MinecraftServer server) {
        LevelData.RespawnData current = server.getRespawnData();
        if (current.dimension() != Hub.LEVEL) {
            server.overworld().setAttached(Hub.SURVIVAL_SPAWN, current.pos());
            server.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
        }
        HubConfig config = HubConfig.get();
        server.setRespawnData(LevelData.RespawnData.of(Hub.LEVEL, config.spawnBlock(), config.spawnYaw(), config.spawnPitch()));
    }

    /** Places the shipped build. Returns false if there is no build to place yet. */
    static boolean place(MinecraftServer server, ServerLevel hub, boolean clearFirst) {
        HubConfig config = HubConfig.get();
        Optional<StructureTemplate> template = server.getStructureTemplateManager().get(config.structure());
        if (template.isEmpty()) {
            SkyRpgHub.LOGGER.warn("No hub build {} found yet", config.structure());
            return false;
        }
        if (clearFirst) {
            clear(hub, config);
        }
        template.get().placeInWorld(hub, config.buildOrigin(), config.buildOrigin(), new StructurePlaceSettings(), hub.getRandom(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Saves the build box from the world into the world's generated/ folder and returns the file. */
    static Path save(MinecraftServer server, ServerLevel hub) {
        HubConfig config = HubConfig.get();
        StructureTemplate template = server.getStructureTemplateManager().getOrCreate(config.structure());
        template.fillFromWorld(hub, config.buildOrigin(), config.buildSize(), false, List.of(Blocks.AIR));
        server.getStructureTemplateManager().save(config.structure());
        return server.getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(config.structure().getNamespace())
                .resolve("structure")
                .resolve(config.structure().getPath() + ".nbt");
    }

    private static void clear(ServerLevel hub, HubConfig config) {
        BlockPos min = config.buildOrigin();
        BlockPos max = min.offset(config.buildSize()).offset(-1, -1, -1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!hub.getBlockState(pos).isAir()) {
                hub.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Until a build ships, a small platform keeps new players from falling forever. */
    private static void placeSpawnPlatform(ServerLevel hub) {
        BlockPos center = HubConfig.get().spawnBlock().below();
        if (!hub.getBlockState(center).isAir()) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
            hub.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        }
    }
}
