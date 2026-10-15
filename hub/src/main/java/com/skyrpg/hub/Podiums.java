package com.skyrpg.hub;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.math.Transformation;
import com.skyrpg.core.leaderboard.Leaderboards;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.PodiumMannequinAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Leaderboard podiums in Skyhaven: the top three of a {@link Leaderboards.Board} as statues of the players (their
 * skin, on a gold, silver and bronze pedestal), a plate on each pedestal with their name and scores, and the board's
 * title floating above. The pedestals themselves are blocks in the hub build (tools/builds/hub_skyhaven.py, podium());
 * this places everything on them, from hub.json {@code podiums}: the board, the ground block under the middle
 * pedestal and the way the podium faces. Statues watch whoever walks past; #1 sparkles. Like {@link HubDecor} it only
 * runs while someone is in the hub, and finds its entities again by tag after a restart.
 */
public final class Podiums {
    static final String TAG = "skyrpg_hub_podium";

    /** A podium: which board it shows, the ground block under its middle (#1) pedestal and the way it faces visitors. */
    public record Podium(Identifier board, BlockPos center, Direction facing) {
        String key() {
            return board.getNamespace() + "." + board.getPath();
        }

        float yaw() {
            return facing.toYRot();
        }

        /** The middle of a block {@code side} blocks to the visitor's left of #1's pedestal and {@code back} behind it. */
        Vec3 at(double side, double back, double up) {
            Direction left = facing.getClockWise();
            return new Vec3(center.getX() + 0.5 + left.getStepX() * side - facing.getStepX() * back,
                    center.getY() + up,
                    center.getZ() + 0.5 + left.getStepZ() * side - facing.getStepZ() * back);
        }
    }

    /** #1 in the middle, #2 on the visitor's left, #3 on their right; how tall each pedestal is (in blocks). */
    static final int[] SIDE = {0, 4, -4};
    static final int[] HEIGHT = {3, 2, 1};
    private static final int[] COLOR = {0xFFD24A, 0xDCE4EE, 0xE39A62};
    private static final String[] PLACE = {"1ST", "2ND", "3RD"};
    private static final float PLATE_SCALE = 0.72f;

    private static final Map<String, Entity> LIVE = new HashMap<>();
    /** What each entity shows now, so unchanged text and skins aren't sent again. */
    private static final Map<String, Object> SHOWN = new HashMap<>();
    private static final Map<String, UUID> FIRST = new HashMap<>();
    private static HubConfig matched;

    private Podiums() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Podiums::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel hub = Hub.level(server);
        if (hub == null || hub.players().isEmpty()) {
            return;
        }
        HubConfig config = HubConfig.get();
        if (config != matched) {
            LIVE.clear();
            SHOWN.clear();
            matched = config;
        }
        int tick = server.getTickCount();
        for (Podium podium : config.podiums()) {
            Leaderboards.Board board = Leaderboards.get(podium.board());
            if (board == null || !hub.areEntitiesLoaded(ChunkPos.pack(podium.center()))) {
                continue;
            }
            if (tick % 20 == 0) {
                refresh(server, hub, podium, board);
            }
            if (tick % 2 == 0) {
                watch(hub, podium);
            }
            if (tick % 10 == 0) {
                sparkle(hub, podium, tick);
            }
        }
        if (tick % 200 == 0) {
            sweep(hub, config);
        }
    }

    // ------------------------------------------------------------------ what's shown

    private static void refresh(MinecraftServer server, ServerLevel hub, Podium podium, Leaderboards.Board board) {
        List<Leaderboards.Entry> top = board.top(server, 3);
        text(hub, podium, "title", podium.at(0, 2.2, 7.15), 2.1f, false, board.title().copy().withStyle(style -> style.withBold(true)));
        text(hub, podium, "subtitle", podium.at(0, 2.2, 6.75), 0.8f, false, board.subtitle());
        for (int place = 0; place < 3; place++) {
            Leaderboards.Entry entry = place < top.size() ? top.get(place) : null;
            statue(hub, podium, place, entry);
            // The plates sit low on the pedestals' front faces, all at the same height.
            text(hub, podium, place + ".plate", podium.at(SIDE[place], -1.53, 1.08), PLATE_SCALE, true, plate(place, entry));
        }
        UUID first = top.isEmpty() ? null : top.getFirst().id();
        UUID before = FIRST.put(podium.key(), first);
        if (first != null && before != null && !first.equals(before)) {
            // A new #1: a burst of colour around the statue.
            Vec3 pos = podium.at(0, 0, 1 + HEIGHT[0] + 1);
            hub.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 60, 0.5, 0.8, 0.5, 0.4);
            hub.playSound(null, BlockPos.containing(pos), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
        }
    }

    private static Component plate(int place, Leaderboards.Entry entry) {
        MutableComponent text = Component.literal(PLACE[place] + "  ").withColor(COLOR[place]).withStyle(style -> style.withBold(true));
        if (entry == null) {
            return text.append(Component.literal("Nobody yet").withColor(0x8D96A8).withStyle(style -> style.withBold(false)))
                    .append(Component.literal("\nThis spot is waiting for you").withColor(0x5A6272).withStyle(style -> style.withBold(false)));
        }
        text.append(Component.literal(entry.name()).withColor(0xFFFFFF));
        for (Component line : entry.lines()) {
            text.append(Component.literal("\n")).append(line.copy().withStyle(style -> style.withBold(false)));
        }
        return text;
    }

    /** The statue of whoever is in this place (gone if nobody is). */
    private static void statue(ServerLevel hub, Podium podium, int place, Leaderboards.Entry entry) {
        String key = podium.key() + "." + place + ".statue";
        Vec3 pos = podium.at(SIDE[place], 0, 1 + HEIGHT[place]);
        Entity found = find(hub, key, pos);
        if (entry == null) {
            if (found != null) {
                found.discard();
            }
            LIVE.remove(key);
            SHOWN.remove(key);
            return;
        }
        Mannequin statue = found instanceof Mannequin mannequin ? mannequin : null;
        if (statue == null) {
            statue = EntityTypes.MANNEQUIN.create(hub, EntitySpawnReason.COMMAND);
            if (statue == null) {
                return;
            }
            statue.addTag(TAG);
            statue.addTag(TAG + "." + key);
            statue.snapTo(pos, podium.yaw(), 0);
            statue.setYHeadRot(podium.yaw());
            statue.setYBodyRot(podium.yaw());
            statue.setNoGravity(true);
            statue.setSilent(true);
            PodiumMannequinAccess access = (PodiumMannequinAccess) statue;
            access.skyrpg$podiumImmovable(true);
            access.skyrpg$podiumHideDescription(true);
            LIVE.put(key, statue);
            SHOWN.remove(key);
            hub.addFreshEntity(statue);
        }
        String look = entry.id() + "|" + entry.name() + "|" + entry.skin();
        if (!look.equals(SHOWN.get(key))) {
            statue.setComponent(DataComponents.PROFILE, profile(entry));
            statue.setCustomName(Component.literal("✦ " + PLACE[place] + " ✦").withColor(COLOR[place]));
            statue.setCustomNameVisible(true);
            SHOWN.put(key, look);
        }
    }

    /** Their skin as we last saw it (so it shows while they're offline), or looked up by name if we never did. */
    private static ResolvableProfile profile(Leaderboards.Entry entry) {
        if (entry.skin().isEmpty()) {
            return ResolvableProfile.createUnresolved(entry.name());
        }
        Property textures = new Property("textures", entry.skin(), entry.skinSignature().isEmpty() ? null : entry.skinSignature());
        return ResolvableProfile.createResolved(new GameProfile(entry.id(), entry.name(), new PropertyMap(ImmutableMultimap.of("textures", textures))));
    }

    /** A text display facing visitors: a name plate (with a dark background) or the floating title. */
    private static void text(ServerLevel hub, Podium podium, String part, Vec3 pos, float scale, boolean plate, Component content) {
        String key = podium.key() + "." + part;
        Entity found = find(hub, key, pos);
        Display.TextDisplay display = found instanceof Display.TextDisplay text ? text : null;
        if (display == null) {
            display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, hub);
            display.addTag(TAG);
            display.addTag(TAG + "." + key);
            display.snapTo(pos, podium.yaw(), 0);
            ((DisplayAccess) display).skyrpg$setViewRange(0.6f);
            TextDisplayAccess text = (TextDisplayAccess) display;
            text.skyrpg$setLineWidth(240);
            text.skyrpg$setBackgroundColor(plate ? 0xC80B1018 : 0);
            text.skyrpg$setFlags(Display.TextDisplay.FLAG_SHADOW);
            LIVE.put(key, display);
            SHOWN.remove(key);
            SHOWN.remove(key + "#scale");
            hub.addFreshEntity(display);
        }
        if (!content.equals(SHOWN.get(key))) {
            ((TextDisplayAccess) display).skyrpg$setText(content);
            SHOWN.put(key, content);
        }
        if (!Float.valueOf(scale).equals(SHOWN.get(key + "#scale"))) {
            ((DisplayAccess) display).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            SHOWN.put(key + "#scale", scale);
        }
    }

    // ------------------------------------------------------------------ alive

    /** Each statue turns its head towards the nearest player walking past (within 60° of straight ahead). */
    private static void watch(ServerLevel hub, Podium podium) {
        for (int place = 0; place < 3; place++) {
            if (!(LIVE.get(podium.key() + "." + place + ".statue") instanceof Mannequin statue) || statue.isRemoved()) {
                continue;
            }
            Player near = hub.getNearestPlayer(statue.getX(), statue.getY(), statue.getZ(), 14, false);
            float target = podium.yaw();
            if (near != null) {
                float toPlayer = (float) (Mth.atan2(near.getZ() - statue.getZ(), near.getX() - statue.getX()) * Mth.RAD_TO_DEG) - 90f;
                target = podium.yaw() + Mth.clamp(Mth.wrapDegrees(toPlayer - podium.yaw()), -60f, 60f);
            }
            float head = statue.getYHeadRot();
            statue.setYHeadRot(head + Mth.wrapDegrees(target - head) * 0.25f);
            statue.setYBodyRot(podium.yaw());
        }
    }

    /** Gold sparkles around #1, and now and then a music note over someone's head. */
    private static void sparkle(ServerLevel hub, Podium podium, int tick) {
        for (int place = 0; place < 3; place++) {
            if (!(LIVE.get(podium.key() + "." + place + ".statue") instanceof Mannequin statue) || statue.isRemoved()) {
                continue;
            }
            double y = statue.getY() + 1.9;
            if (place == 0) {
                hub.sendParticles(ParticleTypes.WAX_ON, statue.getX(), y - 0.6, statue.getZ(), 2, 0.45, 0.6, 0.45, 0.02);
            }
            if ((tick / 10 + place * 3) % 9 == 0) {
                hub.sendParticles(ParticleTypes.NOTE, statue.getX(), y + 0.4, statue.getZ(), 1, 0, 0, 0, hub.getRandom().nextDouble());
            }
        }
    }

    // ------------------------------------------------------------------ bookkeeping

    /** The live entity for a key: remembered, or found nearby by its tag (extra copies are removed). */
    private static Entity find(ServerLevel hub, String key, Vec3 pos) {
        Entity cached = LIVE.get(key);
        if (cached != null && !cached.isRemoved() && cached.level() == hub) {
            return cached;
        }
        LIVE.remove(key);
        String tag = TAG + "." + key;
        List<Entity> found = hub.getEntities((Entity) null, new AABB(BlockPos.containing(pos)).inflate(6), entity -> entity.entityTags().contains(tag));
        if (found.isEmpty()) {
            return null;
        }
        Entity entity = found.getFirst();
        found.subList(1, found.size()).forEach(Entity::discard);
        entity.snapTo(pos, entity.getYRot(), 0);
        LIVE.put(key, entity);
        return entity;
    }

    /** Removes podium entities of podiums no longer in hub.json, and duplicates. */
    private static void sweep(ServerLevel hub, HubConfig config) {
        Set<String> boards = new HashSet<>();
        config.podiums().forEach(podium -> boards.add(podium.key() + "."));
        for (Entity entity : hub.getAllEntities()) {
            if (!entity.entityTags().contains(TAG)) {
                continue;
            }
            String key = entity.entityTags().stream().filter(tag -> tag.startsWith(TAG + "."))
                    .map(tag -> tag.substring(TAG.length() + 1)).findFirst().orElse("");
            Entity live = LIVE.get(key);
            if (boards.stream().noneMatch(key::startsWith) || (live != null && live != entity)) {
                entity.discard();
            }
        }
    }

    static List<Podium> parse(JsonArray array) {
        List<Podium> podiums = new ArrayList<>();
        if (array == null) {
            return podiums;
        }
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            JsonArray center = json.getAsJsonArray("center");
            Direction facing = Direction.byName(json.get("facing").getAsString());
            podiums.add(new Podium(Identifier.parse(json.get("board").getAsString()),
                    new BlockPos(center.get(0).getAsInt(), center.get(1).getAsInt(), center.get(2).getAsInt()),
                    facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH));
        }
        return podiums;
    }
}
