package com.skyrpg.npcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.text.Msg;
import com.skyrpg.npcs.mixin.MannequinAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Keeps every NPC from the data files standing in the world. NPCs are player-shaped Mannequins that are
 * never saved (see {@code NpcSaveMixin}): they are spawned when the server starts, when their chunk loads
 * and after /reload, and updated in place when their data changes. They turn their heads towards nearby
 * players, and right-clicking one greets you and runs its action (open a shop, the bank...).
 */
public final class Npcs {
    /** Every NPC entity carries this tag... */
    public static final String TAG = "skyrpg_npc";
    /** ...plus "skyrpg_npc:<id>" naming which NPC it is. */
    private static final String ID_TAG_PREFIX = TAG + ":";
    private static final double LOOK_RANGE = 8;
    private static final long CLICK_COOLDOWN_MS = 400;

    private static Map<Identifier, NpcDefinition> definitions = Map.of();
    private static final Map<Identifier, Mannequin> LIVE = new HashMap<>();
    /** NPCs to (re)spawn or update on the next tick; chunk loads and reloads add to it. */
    private static final Set<Identifier> PENDING = new HashSet<>();
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();
    private static MinecraftServer server;

    private Npcs() {
    }

    public static Map<Identifier, NpcDefinition> definitions() {
        return definitions;
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, NpcDefinition> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, NpcDefinition.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgNpcs.LOGGER.error("Skipping invalid NPC {}: {}", id, e.getMessage());
            }
        });
        definitions = loaded;
        Map<Identifier, com.skyrpg.core.event.NpcInteractions.Info> infos = new LinkedHashMap<>();
        loaded.forEach((id, def) -> infos.put(id, new com.skyrpg.core.event.NpcInteractions.Info(id, def.name(), def.role(), def.color(),
                def.skin(), def.dimension(), def.pos(), def.hint(), def.actionType(), def.actionParams(), def.greetings())));
        com.skyrpg.core.event.NpcInteractions.publish(infos);
        SkyRpgNpcs.LOGGER.info("Loaded {} NPCs", loaded.size());
        if (server != null) {
            PENDING.addAll(loaded.keySet());
        }
    }

    static void start(MinecraftServer started) {
        server = started;
        PENDING.addAll(definitions.keySet());
    }

    static void stop() {
        server = null;
        LIVE.clear();
        PENDING.clear();
    }

    /** Re-spawns every NPC (admin command). */
    static int respawnAll() {
        LIVE.values().forEach(Entity::discard);
        LIVE.clear();
        PENDING.addAll(definitions.keySet());
        return definitions.size();
    }

    static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        for (NpcDefinition def : definitions.values()) {
            if (level.dimension().identifier().equals(def.dimension()) && ChunkPos.containing(BlockPos.containing(def.pos())).equals(pos)) {
                PENDING.add(def.id());
            }
        }
    }

    static void tick(MinecraftServer current) {
        if (!PENDING.isEmpty()) {
            for (Identifier id : Set.copyOf(PENDING)) {
                NpcDefinition def = definitions.get(id);
                ServerLevel level = def == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, def.dimension()));
                // Not loaded yet: the chunk-load hook adds it again when someone goes there.
                if (level != null && level.isLoaded(BlockPos.containing(def.pos()))) {
                    ensure(def, level);
                }
            }
            PENDING.clear();
        }
        // Every two seconds, bring back NPCs that were killed (/kill gets past invulnerability) where their chunk is loaded.
        if (current.getTickCount() % 40 == 0) {
            for (NpcDefinition def : definitions.values()) {
                Mannequin npc = LIVE.get(def.id());
                if (npc == null || npc.isRemoved()) {
                    PENDING.add(def.id());
                }
            }
        }
        // Retire NPCs whose data file is gone.
        LIVE.entrySet().removeIf(entry -> {
            if (definitions.containsKey(entry.getKey())) {
                return false;
            }
            entry.getValue().discard();
            return true;
        });
        if (current.getTickCount() % 2 == 0) {
            LIVE.forEach((id, npc) -> {
                NpcDefinition def = definitions.get(id);
                if (def != null && !npc.isRemoved()) {
                    lookAround(def, npc);
                }
            });
        }
    }

    private static void ensure(NpcDefinition def, ServerLevel level) {
        Mannequin npc = LIVE.get(def.id());
        if (npc != null && !npc.isRemoved() && npc.level() == level) {
            apply(def, npc);
            return;
        }
        npc = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (npc == null) {
            return;
        }
        npc.addTag(TAG);
        npc.addTag(ID_TAG_PREFIX + def.id());
        apply(def, npc);
        // Registered before it joins the world, so onEntityLoad recognises it as the real one.
        LIVE.put(def.id(), npc);
        level.addFreshEntity(npc);
    }

    /** Any NPC-tagged entity that isn't the live instance (left over from elsewhere) goes away: never two of one NPC. */
    static void onEntityLoad(Entity entity) {
        if (entity.entityTags().contains(TAG) && LIVE.get(idOf(entity)) != entity) {
            entity.discard();
        }
    }

    private static void apply(NpcDefinition def, Mannequin npc) {
        npc.snapTo(def.pos(), def.yaw(), 0);
        npc.setYHeadRot(def.yaw());
        npc.setYBodyRot(def.yaw());
        npc.setCustomName(Component.literal(def.name()).withColor(def.color()));
        npc.setCustomNameVisible(true);
        npc.setNoGravity(true);
        npc.setSilent(true);
        MannequinAccess access = (MannequinAccess) npc;
        access.skyrpg$setImmovable(true);
        access.skyrpg$setHideDescription(false);
        access.skyrpg$setDescription(roleLine(def));
        ResolvableProfile profile = profile(def);
        if (profile != null) {
            npc.setComponent(DataComponents.PROFILE, profile);
        }
    }

    /** "Farmer  ·  Shop" under the name: who they are, and what clicking does. */
    private static Component roleLine(NpcDefinition def) {
        return Component.literal(def.role()).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(def.hint()).withColor(0xF2B33D));
    }

    /** A profile with only a skin override pointing at our own texture (no Mojang account involved). */
    private static ResolvableProfile profile(NpcDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("texture", def.skin().toString());
        json.addProperty("model", def.model());
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> SkyRpgNpcs.LOGGER.error("Bad skin for NPC {}: {}", def.id(), error))
                .orElse(null);
    }

    /** Turns the head (and, past a point, the body) towards the nearest player; looks ahead when nobody is near. */
    private static void lookAround(NpcDefinition def, Mannequin npc) {
        Player nearest = npc.level().getNearestPlayer(npc, LOOK_RANGE);
        float targetYaw = def.yaw();
        float targetPitch = 0;
        if (nearest != null && !nearest.isSpectator()) {
            double dx = nearest.getX() - npc.getX();
            double dz = nearest.getZ() - npc.getZ();
            double dy = nearest.getEyeY() - npc.getEyeY();
            targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            targetPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }
        float head = Mth.approachDegrees(npc.getYHeadRot(), targetYaw, 14);
        npc.setYHeadRot(head);
        npc.setXRot(Mth.approach(npc.getXRot(), Mth.clamp(targetPitch, -40, 40), 8));
        // The body stays put unless the head would have to twist more than 55 degrees.
        float twist = Mth.wrapDegrees(head - def.yaw());
        float body = Math.abs(twist) > 55 ? head - Math.signum(twist) * 55 : def.yaw();
        npc.setYBodyRot(body);
        npc.setYRot(body);
    }

    /** Which NPC an entity is, or null. */
    public static Identifier idOf(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(ID_TAG_PREFIX)) {
                return Identifier.tryParse(tag.substring(ID_TAG_PREFIX.length()));
            }
        }
        return null;
    }

    static InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity) {
        // Entity tags only exist on the server; the client just sends the click along.
        if (level.isClientSide() || !(entity instanceof Mannequin) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        Identifier id = idOf(entity);
        if (id == null) {
            return InteractionResult.PASS;
        }
        NpcDefinition def = definitions.get(id);
        long now = Util.getMillis();
        // Clicks arrive more than once (both hands, at + on the entity); only act on one.
        if (def == null || hand != InteractionHand.MAIN_HAND || now - LAST_CLICK.getOrDefault(player.getUUID(), 0L) < CLICK_COOLDOWN_MS) {
            return InteractionResult.SUCCESS;
        }
        LAST_CLICK.put(player.getUUID(), now);
        // The questline (or any mod) may take the click: a dialogue instead of the shop.
        if (com.skyrpg.core.event.NpcInteractions.INTERACT.invoker().onInteract(serverPlayer, id)) {
            return InteractionResult.SUCCESS;
        }
        greet(def, serverPlayer);
        com.skyrpg.core.event.ProgressEvents.NPC_TALKED.invoker().onTalked(serverPlayer, id);
        if (!def.actionType().isEmpty() && !def.actionType().equals("dialog") && !Actions.run(def.actionType(), serverPlayer, def.actionParams())) {
            SkyRpgNpcs.LOGGER.warn("NPC {} has action '{}' but no mod handles it", id, def.actionType());
        }
        return InteractionResult.SUCCESS;
    }

    private static void greet(NpcDefinition def, ServerPlayer player) {
        if (!def.greetings().isEmpty()) {
            String line = def.greetings().get(player.getRandom().nextInt(def.greetings().size()));
            player.sendSystemMessage(Component.literal(def.name()).withColor(def.color())
                    .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(line).withColor(0xE9EDF4)));
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(def.sound());
        if (sound != null) {
            Msg.playSound(player, sound, 0.8f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        }
    }
}
