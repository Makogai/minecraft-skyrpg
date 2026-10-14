package com.skyrpg.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Fishing hotspots: now and then a circle of open water (lava in the Nether, for a lava rod) a few blocks wide appears
 * near someone who is fishing. A ring of glowing particles and a floating label mark it from afar; it lasts a few
 * minutes. A cast whose <b>bobber</b> floats inside it gets the hotspot's one or two buffs (not the player: step out and
 * you keep nothing).
 * <p>
 * Numbers are data: {@code data/<ns>/skyrpg/hotspots/*.json} (see {@code hotspots.json}): radius, duration, spawn chance,
 * cooldown, the least water it needs, and the buffs a hotspot can roll ({@code stat}: fishing_speed, fishing_fortune,
 * sea_creature_chance, double_catch, rare_finds; a value between {@code min} and {@code max}; {@code weight}).
 * Never in the hub.
 */
public final class Hotspots {
    public static final String DIRECTORY = "skyrpg/hotspots";
    private static final String TAG = "skyrpg_hotspot";
    private static final double MAX_SPEED_TICKS_BONUS = 200;

    record Buff(String stat, String label, double min, double max, int weight, boolean percent) {
        String show(double value) {
            return "+" + (long) value + (percent ? "% " : " ") + label;
        }
    }

    record Roll(Buff buff, double value) {
    }

    /** The numbers, from data. */
    record Settings(double radius, int durationTicks, double chancePerSecond, int cooldownTicks, int maxNear, double nearRange,
                    int minDepth, double minFill, double secondBuffChance, List<Buff> buffs) {
    }

    static final class Spot {
        final ServerLevel level;
        final Vec3 center;
        final boolean lava;
        final List<Roll> rolls;
        final long born;
        final long expires;
        UUID label;
        int phase;

        Spot(ServerLevel level, Vec3 center, boolean lava, List<Roll> rolls, long born, long expires) {
            this.level = level;
            this.center = center;
            this.lava = lava;
            this.rolls = rolls;
            this.born = born;
            this.expires = expires;
        }
    }

    private static Settings settings = defaults();
    private static final List<Spot> SPOTS = new ArrayList<>();
    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();
    private static final Map<UUID, String> LAST_STATUS = new HashMap<>();

    private Hotspots() {
    }

    private static Settings defaults() {
        return new Settings(4, 240 * 20, 0.02, 90 * 20, 2, 48, 2, 0.85, 0.4, List.of(
                new Buff("fishing_speed", "Fishing Speed", 20, 50, 10, false),
                new Buff("fishing_fortune", "Fishing Fortune", 15, 40, 8, false),
                new Buff("sea_creature_chance", "Sea Creature Chance", 3, 8, 6, false),
                new Buff("double_catch", "Double Catch", 8, 20, 6, true),
                new Buff("rare_finds", "Rare Finds", 25, 75, 4, true)));
    }

    static void load(Map<Identifier, JsonElement> files) {
        Settings loaded = defaults();
        for (Map.Entry<Identifier, JsonElement> file : files.entrySet()) {
            try {
                JsonObject json = file.getValue().getAsJsonObject();
                List<Buff> buffs = loaded.buffs();
                if (json.has("buffs")) {
                    buffs = new ArrayList<>();
                    for (JsonElement element : json.getAsJsonArray("buffs")) {
                        JsonObject buff = element.getAsJsonObject();
                        String stat = buff.get("stat").getAsString();
                        if (!List.of("fishing_speed", "fishing_fortune", "sea_creature_chance", "double_catch", "rare_finds").contains(stat)) {
                            throw new IllegalArgumentException("unknown stat " + stat);
                        }
                        buffs.add(new Buff(stat, buff.get("label").getAsString(), buff.get("min").getAsDouble(), buff.get("max").getAsDouble(),
                                buff.has("weight") ? buff.get("weight").getAsInt() : 1,
                                buff.has("percent") && buff.get("percent").getAsBoolean()));
                    }
                    if (buffs.isEmpty()) {
                        throw new IllegalArgumentException("no buffs");
                    }
                }
                loaded = new Settings(num(json, "radius", loaded.radius()), (int) (num(json, "duration_seconds", loaded.durationTicks() / 20.0) * 20),
                        num(json, "spawn_chance_per_second", loaded.chancePerSecond()),
                        (int) (num(json, "cooldown_seconds", loaded.cooldownTicks() / 20.0) * 20), (int) num(json, "max_near", loaded.maxNear()),
                        num(json, "near_range", loaded.nearRange()), (int) num(json, "min_depth", loaded.minDepth()),
                        num(json, "min_fill", loaded.minFill()), num(json, "second_buff_chance", loaded.secondBuffChance()), List.copyOf(buffs));
            } catch (RuntimeException e) {
                SkyRpgFishing.LOGGER.warn("Skipping hotspot file {}: {}", file.getKey(), e.getMessage());
            }
        }
        settings = loaded;
    }

    private static double num(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Hotspots::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("hotspot")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1))
                                        .executes(ctx -> debugSpawn(ctx.getSource().getPlayerOrException(), Vec3Argument.getVec3(ctx, "pos"),
                                                StringArgumentType.getString(ctx, "stat"), DoubleArgumentType.getDouble(ctx, "value"))))))));
    }

    private static void clearAll() {
        for (Spot spot : SPOTS) {
            removeLabel(spot);
        }
        SPOTS.clear();
        NEXT_SPAWN.clear();
        LAST_STATUS.clear();
    }

    // ---- what a bobber gets ----

    /** The hotspot this bobber floats in, or null. */
    static Spot at(FishingHook hook) {
        if (SPOTS.isEmpty() || !(hook.level() instanceof ServerLevel level)) {
            return null;
        }
        double r2 = settings.radius() * settings.radius();
        for (Spot spot : SPOTS) {
            if (spot.level != level) {
                continue;
            }
            double dx = hook.getX() - spot.center.x;
            double dz = hook.getZ() - spot.center.z;
            if (dx * dx + dz * dz <= r2 && Math.abs(hook.getY() - spot.center.y) <= 3) {
                return spot;
            }
        }
        return null;
    }

    public static CastBonus bonusAt(FishingHook hook) {
        Spot spot = at(hook);
        if (spot == null) {
            return CastBonus.NONE;
        }
        double speed = 0;
        double fortune = 0;
        double sea = 0;
        double dbl = 0;
        double rare = 0;
        for (Roll roll : spot.rolls) {
            switch (roll.buff().stat()) {
                case "fishing_speed" -> speed += roll.value();
                case "fishing_fortune" -> fortune += roll.value();
                case "sea_creature_chance" -> sea += roll.value();
                case "double_catch" -> dbl += roll.value() / 100;
                case "rare_finds" -> rare += roll.value() / 100;
                default -> {
                }
            }
        }
        return new CastBonus(speed, fortune, sea, dbl, rare);
    }

    /** Extra Fishing Speed points from the hotspot under the bobber (the bait's are in the hook's base wait). */
    public static double speedAt(FishingHook hook) {
        return Math.min(MAX_SPEED_TICKS_BONUS, bonusAt(hook).speed());
    }

    /** "+30 Fishing Speed, +12% Double Catch" for the hotspot under this hook, or "". */
    static String statusAt(FishingHook hook) {
        Spot spot = at(hook);
        return spot == null ? "" : describe(spot);
    }

    private static String describe(Spot spot) {
        StringBuilder text = new StringBuilder();
        for (Roll roll : spot.rolls) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(roll.buff().show(roll.value()));
        }
        return text.toString();
    }

    // ---- life cycle ----

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                trySpawn(player, now);
            }
        }
        for (Iterator<Spot> it = SPOTS.iterator(); it.hasNext(); ) {
            Spot spot = it.next();
            if (now >= spot.expires || spot.level.getServer() != server) {
                removeLabel(spot);
                spot.level.sendParticles(spot.lava ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SPLASH, spot.center.x, spot.center.y + 0.2, spot.center.z,
                        40, settings.radius() * 0.5, 0.1, settings.radius() * 0.5, 0.05);
                it.remove();
                continue;
            }
            particles(server, spot, now);
            if (now % 20 == 0) {
                updateLabel(spot, now);
            }
        }
        if (now % 10 == 0) {
            sendStatus(server);
        }
        if (now % 200 == 0) {
            // Labels loaded back from a chunk saved with the world (or left by a crash).
            for (ServerLevel level : server.getAllLevels()) {
                for (Display.TextDisplay stray : level.getEntities(EntityTypes.TEXT_DISPLAY,
                        display -> display.entityTags().contains(TAG) && SPOTS.stream().noneMatch(spot -> display.getUUID().equals(spot.label)))) {
                    stray.discard();
                }
            }
        }
    }

    private static boolean fishing(ServerPlayer player) {
        return player.fishing != null || player.getMainHandItem().getItem() instanceof FishingRodItem
                || player.getOffhandItem().getItem() instanceof FishingRodItem;
    }

    private static void trySpawn(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        if (player.isSpectator() || !fishing(player) || level.dimension().identifier().getNamespace().equals("skyrpg_hub")
                || now < NEXT_SPAWN.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        boolean lava = level.dimension() == Level.NETHER;
        if (lava && !(player.getMainHandItem().is(LavaFishing.LAVA_RODS) || player.getOffhandItem().is(LavaFishing.LAVA_RODS))) {
            return;
        }
        int near = 0;
        for (Spot spot : SPOTS) {
            if (spot.level == level && spot.center.distanceToSqr(player.position()) < settings.nearRange() * settings.nearRange()) {
                near++;
            }
        }
        if (near >= settings.maxNear() || player.getRandom().nextDouble() >= settings.chancePerSecond()) {
            return;
        }
        Vec3 site = findSite(level, player, lava);
        if (site == null) {
            return;
        }
        NEXT_SPAWN.put(player.getUUID(), now + settings.cooldownTicks());
        Spot spot = new Spot(level, site, lava, roll(player), now, now + settings.durationTicks());
        SPOTS.add(spot);
        spawnLabel(spot);
        updateLabel(spot, now);
        player.sendOverlayMessage(Component.literal("A fishing hotspot appeared nearby!").withStyle(ChatFormatting.AQUA));
        level.playSound(null, site.x, site.y, site.z, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH, net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 0.7f);
    }

    private static List<Roll> roll(ServerPlayer player) {
        List<Buff> pool = new ArrayList<>(settings.buffs());
        List<Roll> rolls = new ArrayList<>();
        int count = player.getRandom().nextDouble() < settings.secondBuffChance() && pool.size() > 1 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            int total = pool.stream().mapToInt(Buff::weight).sum();
            int pick = player.getRandom().nextInt(Math.max(1, total));
            Buff chosen = pool.get(pool.size() - 1);
            for (Buff buff : pool) {
                pick -= buff.weight();
                if (pick < 0) {
                    chosen = buff;
                    break;
                }
            }
            pool.remove(chosen);
            double value = chosen.min() + player.getRandom().nextDouble() * (chosen.max() - chosen.min());
            rolls.add(new Roll(chosen, Math.round(value)));
        }
        return rolls;
    }

    /** A spot of open fluid 10-30 blocks from the player where a circle of the hotspot's size is all deep enough fluid. */
    private static Vec3 findSite(ServerLevel level, ServerPlayer player, boolean lava) {
        TagKey<Fluid> tag = lava ? FluidTags.LAVA : FluidTags.WATER;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double distance = 10 + player.getRandom().nextDouble() * 20;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            int y = surface(level, x, z, player.getBlockY() + 12, player.getBlockY() - 16, tag);
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            double radius = settings.radius();
            // Not on top of another one (a bobber gets one hotspot's buffs).
            boolean overlaps = false;
            for (Spot other : SPOTS) {
                overlaps |= other.level == level && Math.hypot(other.center.x - x, other.center.z - z) < radius * 2 + 1;
            }
            if (overlaps) {
                continue;
            }
            int samples = 0;
            int good = 0;
            for (double dx = -radius; dx <= radius; dx += 1.5) {
                for (double dz = -radius; dz <= radius; dz += 1.5) {
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    samples++;
                    if (deepFluid(level, (int) Math.floor(x + 0.5 + dx), y, (int) Math.floor(z + 0.5 + dz), tag)) {
                        good++;
                    }
                }
            }
            if (samples > 0 && good >= samples * settings.minFill()) {
                BlockPos pos = new BlockPos(x, y, z);
                FluidState fluid = level.getFluidState(pos);
                return new Vec3(x + 0.5, y + fluid.getHeight(level, pos), z + 0.5);
            }
        }
        return null;
    }

    /** The y of the topmost fluid block at x, z (air above it) searching downward, or MIN_VALUE. */
    private static int surface(ServerLevel level, int x, int z, int from, int to, TagKey<Fluid> tag) {
        for (int y = from; y >= Math.max(to, level.getMinY()); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(tag)) {
                return level.getFluidState(pos.above()).isEmpty() ? y : Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean deepFluid(ServerLevel level, int x, int y, int z, TagKey<Fluid> tag) {
        // Surface at this column must be the same height, air above, fluid for minDepth below.
        if (!level.getFluidState(new BlockPos(x, y + 1, z)).isEmpty()) {
            return false;
        }
        BlockState above = level.getBlockState(new BlockPos(x, y + 1, z));
        if (!above.isAir() && !above.getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty()) {
            return false;
        }
        for (int d = 0; d < settings.minDepth(); d++) {
            if (!level.getFluidState(new BlockPos(x, y - d, z)).is(tag)) {
                return false;
            }
        }
        return true;
    }

    // ---- looks ----

    private static void particles(MinecraftServer server, Spot spot, long now) {
        double radius = settings.radius();
        boolean fading = spot.expires - now < 200;
        ParticleOptions ring = spot.lava ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
        ParticleOptions inside = spot.lava ? ParticleTypes.LAVA : ParticleTypes.BUBBLE_POP;
        for (ServerPlayer player : spot.level.players()) {
            if (player.distanceToSqr(spot.center) > 128 * 128) {
                continue;
            }
            if (now % 2 == 0 && !(fading && now % 4 == 0)) {
                // A turning ring: a few points a tick, drifting round.
                for (int i = 0; i < 4; i++) {
                    double angle = (now * 0.12) + i * (Math.PI / 2) + (now % 8) * 0.05;
                    spot.level.sendParticles(player, ring, true, true, spot.center.x + Math.cos(angle) * radius, spot.center.y + 0.15,
                            spot.center.z + Math.sin(angle) * radius, 1, 0, 0.02, 0, 0.0);
                }
            }
            if (now % 10 == 0) {
                // A light pillar in the middle so it shows from far away, and pops inside.
                spot.level.sendParticles(player, ring, true, true, spot.center.x, spot.center.y + 0.4, spot.center.z, 3, 0.15, 0.5, 0.15, 0.01);
                spot.level.sendParticles(player, inside, false, false, spot.center.x, spot.center.y + 0.1, spot.center.z, 4, radius * 0.5, 0.05, radius * 0.5, 0.02);
            }
        }
    }

    private static void spawnLabel(Spot spot) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", true);
        tag.putBoolean("shadow", true);
        tag.putInt("background", 0x55000000);
        tag.putFloat("view_range", 2.0f);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        double y = spot.center.y + 2.6;
        Entity entity = EntityType.loadEntityRecursive(tag, spot.level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
            e.snapTo(spot.center.x, y, spot.center.z, 0, 0);
            return e;
        });
        if (entity instanceof Display.TextDisplay display) {
            spot.label = display.getUUID();
            spot.level.tryAddFreshEntityWithPassengers(display);
        }
    }

    private static void updateLabel(Spot spot, long now) {
        if (spot.label == null) {
            return;
        }
        int left = (int) Math.max(0, (spot.expires - now) / 20);
        MutableComponent text = Component.literal("✦ Hotspot").withStyle(spot.lava ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD);
        for (Roll roll : spot.rolls) {
            text.append(Component.literal("\n" + roll.buff().show(roll.value())).withStyle(ChatFormatting.YELLOW));
        }
        text.append(Component.literal(String.format("\n%d:%02d", left / 60, left % 60)).withStyle(left < 10 ? ChatFormatting.RED : ChatFormatting.GRAY));
        CompoundTag data = new CompoundTag();
        ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).result().ifPresent(encoded -> data.put("text", encoded));
        MinecraftServer server = spot.level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(spot.level).withSuppressedOutput(),
                "data merge entity " + spot.label + " " + data);
    }

    private static void removeLabel(Spot spot) {
        if (spot.label != null && spot.level.getEntity(spot.label) != null) {
            spot.level.getEntity(spot.label).discard();
        }
        spot.label = null;
    }

    // ---- the HUD line ----

    /** Tells every fisher what hotspot their bobber is in (or that it isn't in one), when that changes. */
    private static void sendStatus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String status = player.fishing == null ? "" : statusAt(player.fishing);
            String last = LAST_STATUS.getOrDefault(player.getUUID(), "");
            if (!status.equals(last)) {
                if (status.isEmpty()) {
                    LAST_STATUS.remove(player.getUUID());
                } else {
                    LAST_STATUS.put(player.getUUID(), status);
                }
                FishingPayloads.send(player, status);
            }
        }
    }

    /** Op command for tests: a hotspot with one chosen buff at a position. */
    private static int debugSpawn(ServerPlayer player, Vec3 at, String stat, double value) {
        ServerLevel level = player.level();
        Buff buff = settings.buffs().stream().filter(b -> b.stat().equals(stat)).findFirst().orElse(settings.buffs().get(0));
        long now = level.getServer().getTickCount();
        Spot spot = new Spot(level, at, level.dimension() == Level.NETHER, List.of(new Roll(buff, value)), now, now + settings.durationTicks());
        SPOTS.add(spot);
        spawnLabel(spot);
        updateLabel(spot, now);
        return 1;
    }
}
