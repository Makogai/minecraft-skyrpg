package com.skyrpg.brainrots;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Catching brainrots, and spawning them. Every {@link #CHECK_TICKS} each player in the overworld has a
 * {@link #CHANCE} to get one nearby (20 to 44 blocks away, on the surface), so about one turns up every two minutes
 * while you're out, unless {@link #MAX_NEAR} are already around you. Which kind is weighted by rarity, by the biome
 * (beach brainrots on beaches...) and by the time of day (one only comes out at night).
 */
public final class Brainrots {
    static final int CHECK_TICKS = 200;
    static final double CHANCE = 0.09;
    static final int MAX_NEAR = 2;
    static final int MAX_WORLD = 18;

    private Brainrots() {
    }

    // ------------------------------------------------------------------ catching

    static void capture(ServerPlayer player, BrainrotEntity entity) {
        if (entity.caught || !entity.isAlive()) {
            return;
        }
        entity.caught = true;
        BrainrotKind kind = entity.kind();
        ServerLevel level = player.level();
        Album album = player.getAttachedOrElse(Album.ATTACHMENT, Album.EMPTY);
        boolean first = !album.has(kind);
        album = album.caught(kind, System.currentTimeMillis());
        player.setAttached(Album.ATTACHMENT, album);
        int coins = first ? kind.rarity.firstCoins : Math.max(1, kind.rarity.firstCoins / 10);
        Wallet.give(player, coins);

        double x = entity.getX(), y = entity.getY() + entity.getBbHeight() / 2, z = entity.getZ();
        entity.sayName(1.2f);
        level.sendParticles(ParticleTypes.NOTE, x, y + 0.5, z, 12, 0.6, 0.5, 0.6, 1);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 24, 0.4, 0.4, 0.4, 0.04);
        if (first) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 50, 0.5, 0.6, 0.5, 0.35);
        }
        level.playSound(null, player.blockPosition(), first ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                first ? 0.8f : 0.6f, first ? 1.3f : 1.1f);

        int found = album.discovered();
        int total = BrainrotKind.values().length;
        Component name = Component.literal(kind.displayName).withColor(kind.rarity.color & 0xFFFFFF);
        if (first) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 14));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("NEW BRAINROT!").withColor(0xFF7AD9)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(name.copy()));
            Notice.send(player, Notice.Kind.BRAINROT, Component.literal("Caught ").append(name).append(" for the first time! ")
                    .append(Component.literal("+" + coins + " coins").withColor(0xFFC940))
                    .append(Component.literal("  " + found + "/" + total + " in your album (J)").withColor(0x8D96A8)));
        } else {
            Notice.send(player, Notice.Kind.BRAINROT, Component.literal("Caught ").append(name).append(" again (×" + album.count(kind) + ") ")
                    .append(Component.literal("+" + coins + " coins").withColor(0xFFC940)));
        }
        if (first && found == total) {
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("BRAINROT MASTER").withColor(0xFFAA00)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("You caught all " + total + "!")));
            player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().name()
                    + " caught every brainrot! All " + total + " of them!").withColor(0xFF7AD9), false);
        }
        entity.discard();
    }

    // ------------------------------------------------------------------ spawning

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        List<BrainrotEntity> all = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof BrainrotEntity brainrot) {
                all.add(brainrot);
            }
        }
        if (all.size() >= MAX_WORLD) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || level.getRandom().nextDouble() >= CHANCE) {
                continue;
            }
            long near = all.stream().filter(b -> b.distanceToSqr(player) < 96 * 96).count();
            if (near >= MAX_NEAR) {
                continue;
            }
            BrainrotEntity spawned = spawnNear(level, player.blockPosition(), null);
            if (spawned != null) {
                all.add(spawned);
            }
        }
    }

    /** Spawns a brainrot (of {@code forced} kind, or a weighted pick) on the surface 20-44 blocks from {@code around}. */
    static BrainrotEntity spawnNear(ServerLevel level, BlockPos around, BrainrotKind forced) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = forced != null ? 3 + random.nextDouble() * 3 : 20 + random.nextDouble() * 24;
            int x = around.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = around.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!BrainrotEntity.isSolidGround(level, pos.below()) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            BrainrotKind kind = forced != null ? forced : pick(level, pos, random);
            if (kind == null) {
                return null;
            }
            BrainrotEntity entity = SkyRpgBrainrots.BRAINROT.create(level, EntitySpawnReason.NATURAL);
            if (entity == null) {
                return null;
            }
            entity.setKind(kind, true);
            double lift = kind.flies() ? 10 : kind.move == BrainrotKind.Move.FLOAT ? 1.4 : 0;
            entity.snapTo(x + 0.5, y + lift, z + 0.5, random.nextFloat() * 360f, 0f);
            level.addFreshEntity(entity);
            return entity;
        }
        return null;
    }

    private static BrainrotKind pick(ServerLevel level, BlockPos pos, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);
        boolean night = isNight(level);
        double total = 0;
        double[] weights = new double[BrainrotKind.values().length];
        for (BrainrotKind kind : BrainrotKind.values()) {
            weights[kind.ordinal()] = kind.rarity.weight * kind.habitat.weight(biome, night);
            total += weights[kind.ordinal()];
        }
        double roll = random.nextDouble() * total;
        for (BrainrotKind kind : BrainrotKind.values()) {
            roll -= weights[kind.ordinal()];
            if (roll <= 0) {
                return kind;
            }
        }
        return null;
    }

    private static boolean isNight(Level level) {
        long time = level.getOverworldClockTime() % 24000L;
        return time >= 13000 && time <= 23000;
    }

    // ------------------------------------------------------------------ commands

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brainrots")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(BrainrotKind.values()).map(k -> k.id), builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BrainrotKind kind = BrainrotKind.byId(StringArgumentType.getString(context, "kind"));
                                    if (kind == null) {
                                        context.getSource().sendFailure(Component.literal("No such brainrot."));
                                        return 0;
                                    }
                                    BrainrotEntity entity = spawnNear(player.level(), player.blockPosition(), kind);
                                    context.getSource().sendSuccess(() -> Component.literal(entity != null ? "Spawned " + kind.displayName
                                            : "No room nearby."), false);
                                    return entity != null ? 1 : 0;
                                })))
                .then(Commands.literal("near").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    List<BrainrotEntity> near = player.level().getEntitiesOfClass(BrainrotEntity.class, new AABB(player.blockPosition()).inflate(96));
                    context.getSource().sendSuccess(() -> Component.literal(near.size() + " brainrots within 96 blocks: "
                            + near.stream().map(b -> b.kind().displayName).toList()), false);
                    return near.size();
                })));
    }
}
