package com.skyrpg.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The hearts of the danger regions: each region type has a boss (see {@code the_wild.md}) that wakes at the region's
 * centre when someone uses a Heart Sigil on it, or on its own during a Blood Moon. The fights themselves are
 * {@link DungeonBosses} fights (boss bar, moves, minions, loot table, rewards, kill events), so any number of regions can
 * have a boss up at once; this class decides which boss, where, and whether one is already up.
 */
public final class RegionBosses {
    /** On a region boss: "skyrpg_region_boss=<region key>". */
    public static final String BOSS_TAG = "skyrpg_region_boss=";
    /** On mobs that only a Hallowed Edge weapon hurts fully (the Moors' ghosts and the Lantern Wraith). */
    public static final String HALLOWED_TAG = "skyrpg_hallowed_only";
    private static final double HALLOWED_TAKEN = 0.25;
    private static final double SIGIL_RANGE = 24;
    private static final Identifier ENCHANTS = Identifier.parse("skyrpg_enchants:enchants");
    private static final Identifier HALLOWED_EDGE = Identifier.parse("skyrpg_enchants:hallowed_edge");
    private static final Identifier PHANTOM = Identifier.withDefaultNamespace("phantom");
    private static final Map<UUID, Long> HINTED = new HashMap<>();
    private static Method levelsMethod;

    private RegionBosses() {
    }

    static void init() {
        // Ghosts and the Lantern Wraith take a quarter of the damage unless the attacker's weapon has Hallowed Edge.
        IncomingDamage.registerHit((target, source) -> {
            if (!target.entityTags().contains(HALLOWED_TAG)) {
                return 1;
            }
            Entity attacker = source.getEntity();
            if (hallowed(attacker)) {
                return 1;
            }
            if (attacker instanceof ServerPlayer player) {
                long now = player.level().getGameTime();
                Long next = HINTED.get(player.getUUID());
                if (next == null || now >= next) {
                    HINTED.put(player.getUUID(), now + 100);
                    player.sendOverlayMessage(Component.literal("Your weapon passes through it. Only a Hallowed Edge hurts it fully.")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }
            return HALLOWED_TAKEN;
        });
    }

    /** Whether this attacker holds a weapon with the Hallowed Edge enchant (read through the enchants mod's component by id). */
    static boolean hallowed(Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) {
            return false;
        }
        ItemStack weapon = living.getMainHandItem();
        if (weapon.isEmpty()) {
            return false;
        }
        try {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ENCHANTS).orElse(null);
            if (type == null) {
                return false;
            }
            Object list = weapon.get(type);
            if (list == null) {
                return false;
            }
            if (levelsMethod == null) {
                levelsMethod = list.getClass().getMethod("levels");
            }
            return levelsMethod.invoke(list) instanceof Map<?, ?> levels && levels.containsKey(HALLOWED_EDGE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- waking a boss

    /** The name of this region's boss, or null if it has none. */
    public static String bossName(RegionShape region) {
        DangerRegions.Type type = DangerRegions.type(region.type());
        Dungeons.Boss def = type == null || type.boss() == null ? null : Dungeons.boss(type.boss());
        return def == null ? null : def.name();
    }

    /** Wakes the region's heart boss (a Blood Moon does this); false if it has none or is already up. */
    public static boolean wake(ServerLevel level, RegionShape region, String cause) {
        return awaken(level, region, cause) != null;
    }

    /** Whether this region's boss is up right now (in a loaded chunk). */
    static boolean active(ServerLevel level, RegionShape region) {
        BlockPos heart = DangerRegions.heart(level, region);
        String tag = BOSS_TAG + region.key();
        return !level.getEntitiesOfClass(Mob.class, new AABB(heart).inflate(96, 128, 96), mob -> mob.isAlive() && mob.entityTags().contains(tag)).isEmpty();
    }

    /**
     * Wakes the region's boss at its heart. Returns the boss, or null if it has none, one is already up, or it couldn't spawn.
     * @param cause  who or what woke it, for the announcement ("a Heart Sigil", "the Blood Moon")
     */
    static Mob awaken(ServerLevel level, RegionShape region, String cause) {
        DangerRegions.Type type = DangerRegions.type(region.type());
        if (type == null || type.boss() == null || active(level, region)) {
            return null;
        }
        BlockPos heart = DangerRegions.heart(level, region);
        Mob boss = spawn(level, type.boss(), new Vec3(heart.getX() + 3.5, heart.getY(), heart.getZ() + 0.5), region.key());
        if (boss == null) {
            return null;
        }
        Dungeons.Boss def = Dungeons.boss(type.boss());
        String name = def == null ? "The boss" : def.name();
        Component title = Component.literal(name).withColor(region.color()).withStyle(ChatFormatting.BOLD);
        Component subtitle = Component.literal("awakens at the heart of " + region.name()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) > 160 * 160) {
                continue;
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            Notice.of(Notice.Kind.COMBAT).tag("REGION BOSS").color(region.color())
                    .send(player, Msg.text(name, ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Msg.text(" has awoken at " + heart.getX() + ", " + heart.getZ() + " (" + cause + ").", ChatFormatting.GOLD)));
        }
        return boss;
    }

    /** Spawns a region boss anywhere (also for /regionboss spawn), tagged with the region it belongs to. */
    static Mob spawn(ServerLevel level, Identifier bossId, Vec3 at, String regionKey) {
        Dungeons.Boss def = Dungeons.boss(bossId);
        if (def == null) {
            return null;
        }
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(def.entity()).isEmpty()) {
            // The mod that adds this boss's body isn't here (the Storm Griffin without the mounts mod): a big phantom will do.
            def = def.withEntity(PHANTOM);
        }
        Mob mob = DungeonBosses.spawn(level, def, at, null);
        if (mob != null) {
            mob.addTag(BOSS_TAG + regionKey);
        }
        return mob;
    }

    // ---------------------------------------------------------------- Heart Sigils

    /** A Heart Sigil: use it at the heart of its region to wake the boss. */
    static final class SigilItem extends Item {
        private final String type;

        SigilItem(String type, Properties properties) {
            super(properties);
            this.type = type;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            ServerLevel overworld = server.getServer().overworld();
            RegionShape region = server == overworld ? DangerRegions.at(overworld, player.getBlockX(), player.getBlockZ()) : null;
            DangerRegions.Type regionType = region == null ? null : DangerRegions.type(region.type());
            if (region == null || !region.type().equals(type)) {
                DangerRegions.Type own = DangerRegions.type(type);
                serverPlayer.sendOverlayMessage(Component.literal("The sigil is cold. It belongs at the heart of " + (own == null ? type : own.name()) + ".")
                        .withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            BlockPos heart = DangerRegions.heart(overworld, region);
            double distance = Math.hypot(player.getX() - heart.getX() - 0.5, player.getZ() - heart.getZ() - 0.5);
            if (distance > SIGIL_RANGE || Math.abs(player.getY() - heart.getY()) > 20) {
                WaypointTarget.send(serverPlayer, region.name() + " heart", Vec3.atCenterOf(heart), 0xFF000000 | region.color());
                serverPlayer.sendOverlayMessage(Component.literal("The sigil pulls towards the heart, " + Math.round(distance) + " blocks away.")
                        .withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            if (regionType == null || regionType.boss() == null || active(overworld, region)) {
                serverPlayer.sendOverlayMessage(Component.literal(regionType == null || regionType.boss() == null ? "Nothing answers the sigil."
                        : "The heart is already awake.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            Mob boss = awaken(overworld, region, serverPlayer.getName().getString() + " used a Heart Sigil");
            if (boss == null) {
                serverPlayer.sendOverlayMessage(Component.literal("Nothing answers the sigil.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            player.getItemInHand(hand).consume(1, player);
            overworld.playSound(null, heart, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
            return InteractionResult.SUCCESS;
        }
    }

    // ---------------------------------------------------------------- commands

    /** /regionboss spawn|list (ops) and /region nearest (ops). Called from {@link WorldCommands}. */
    static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("regionboss").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DangerRegions.allTypes().stream().map(DangerRegions.Type::id), builder))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "type");
                            DangerRegions.Type type = DangerRegions.type(id);
                            if (type == null || type.boss() == null) {
                                ctx.getSource().sendFailure(Component.literal("No region type " + id + " with a boss"));
                                return 0;
                            }
                            Mob boss = spawn(ctx.getSource().getLevel(), type.boss(), ctx.getSource().getPosition(), "debug:" + id);
                            if (boss == null) {
                                ctx.getSource().sendFailure(Component.literal("Couldn't spawn " + type.boss()));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + boss.getName().getString() + " (" + BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType())
                                    + ", health " + Math.round(boss.getMaxHealth()) + ")"), true);
                            return 1;
                        })))
                .then(Commands.literal("awaken").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    RegionShape region = DangerRegions.at(level, (int) ctx.getSource().getPosition().x, (int) ctx.getSource().getPosition().z);
                    if (region == null) {
                        ctx.getSource().sendFailure(Component.literal("You're not in a danger region"));
                        return 0;
                    }
                    Mob boss = awaken(level, region, "an admin");
                    ctx.getSource().sendSuccess(() -> Component.literal(boss == null ? "Nothing woke (no boss, or it is already up)"
                            : "Woke " + boss.getName().getString() + " at the heart of " + region.name()), true);
                    return boss == null ? 0 : 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    var fights = DungeonBosses.fights();
                    if (fights.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("No boss fights right now."), false);
                    }
                    fights.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
                    return fights.size();
                })));
        dispatcher.register(Commands.literal("region").then(Commands.literal("nearest").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DangerRegions.allTypes().stream().map(DangerRegions.Type::id), builder))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "type");
                            ServerLevel overworld = ctx.getSource().getServer().overworld();
                            int x = (int) ctx.getSource().getPosition().x;
                            int z = (int) ctx.getSource().getPosition().z;
                            RegionShape found = DangerRegions.nearest(overworld, id, x, z, 40);
                            if (found == null) {
                                ctx.getSource().sendFailure(Component.literal("No " + id + " region within 30,000 blocks"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(found.name() + " at " + found.cx() + ", " + found.cz() + " (r " + found.radius() + ", "
                                    + Math.round(Math.hypot(found.cx() - x, found.cz() - z)) + " blocks away)"), false);
                            return 1;
                        }))));
    }
}
