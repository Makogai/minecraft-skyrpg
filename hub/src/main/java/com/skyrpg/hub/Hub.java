package com.skyrpg.hub;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The hub world and moving players in and out of it. */
public final class Hub {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgHub.id("hub"));

    /** Where a player was in the survival world when they last went to the hub. Cleared on death. */
    public record Location(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
        static final Codec<Location> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Location::dimension),
                Vec3.CODEC.fieldOf("pos").forGetter(Location::pos),
                Codec.FLOAT.fieldOf("yaw").forGetter(Location::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Location::pitch)
        ).apply(instance, Location::new));
    }

    static final AttachmentType<Location> LAST_SURVIVAL_LOCATION = AttachmentRegistry.create(SkyRpgHub.id("last_survival_location"),
            builder -> builder.persistent(Location.CODEC));

    /** The overworld's original world spawn, saved before the world spawn moved to the hub. Stored on the overworld. */
    static final AttachmentType<BlockPos> SURVIVAL_SPAWN = AttachmentRegistry.create(SkyRpgHub.id("survival_spawn"),
            builder -> builder.persistent(BlockPos.CODEC));

    private Hub() {
    }

    static void init() {
        // Loads the class so the attachments are registered during mod init.
    }

    public static boolean isHub(Level level) {
        return level.dimension() == LEVEL;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static void sendToHub(ServerPlayer player) {
        ServerLevel hub = level(player.level().getServer());
        if (hub == null) {
            return;
        }
        if (!isHub(player.level())) {
            player.setAttached(LAST_SURVIVAL_LOCATION, new Location(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        }
        HubConfig config = HubConfig.get();
        teleport(player, hub, config.spawn(), config.spawnYaw(), config.spawnPitch());
    }

    /** Back to where the player left the survival world, or to the survival spawn if there's nowhere to go back to. */
    public static void sendToSurvival(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Location last = player.removeAttached(LAST_SURVIVAL_LOCATION);
        ServerLevel lastLevel = last == null ? null : server.getLevel(last.dimension());
        if (lastLevel != null) {
            teleport(player, lastLevel, last.pos(), last.yaw(), last.pitch());
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getAttachedOrElse(SURVIVAL_SPAWN, BlockPos.ZERO);
        // An unloaded chunk's heightmap reads as the bottom of the world, which sent players into the void.
        overworld.getChunk(spawn);
        BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        teleport(player, overworld, Vec3.atBottomCenterOf(surface), player.getYRot(), 0);
    }

    static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, pitch, Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }
}
