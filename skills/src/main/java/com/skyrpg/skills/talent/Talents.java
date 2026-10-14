package com.skyrpg.skills.talent;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skills.SkyRpgSkills;
import io.netty.buffer.ByteBuf;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Talent trees: each skill level gives a talent point for that skill, spent on that skill's talents (from data). Ranks
 * give stats (the "Talents" source) and gear effects (a {@link GearEffects} source), so talents work through the same
 * machinery as gear. A reset is free once a day, then costs {@link #RESET_COST} coins. Also does Second Wind.
 */
public final class Talents {
    public static final String DIRECTORY = "skyrpg/talents";
    public static final long RESET_COST = 2000;
    /** Second Wind: survive a killing blow, once every 5 minutes. */
    public static final Identifier SECOND_WIND = SkyRpgSkills.id("second_wind");
    private static final long SECOND_WIND_COOLDOWN_TICKS = 5 * 60 * 20;

    /** Ranks bought: skill -> talent id -> rank. Synced to the owner for the Talents page. */
    public static final AttachmentType<Map<Identifier, Map<String, Integer>>> RANKS = AttachmentRegistry.create(SkyRpgSkills.id("talents"), builder -> builder
            .persistent(Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING, Codec.INT)))
            .initializer(Map::of)
            .copyOnDeath()
            .syncWith(ByteBufCodecs.<ByteBuf, Identifier, Map<String, Integer>, Map<Identifier, Map<String, Integer>>>map(HashMap::new, Identifier.STREAM_CODEC,
                    ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)),
                    AttachmentSyncPredicate.targetOnly()));
    /** The day (epoch day) of the last free reset. */
    private static final AttachmentType<Long> FREE_RESET_DAY = AttachmentRegistry.create(SkyRpgSkills.id("talent_reset_day"), builder -> builder
            .persistent(Codec.LONG)
            .initializer(() -> -1L)
            .copyOnDeath());

    private static Map<Identifier, List<Talent>> trees = Map.of();
    private static Map<Identifier, String> rawFiles = Map.of();
    private static final Map<UUID, Long> SECOND_WIND_READY = new HashMap<>();

    private Talents() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TalentPayloads.Definitions.TYPE, TalentPayloads.Definitions.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TalentPayloads.Action.TYPE, TalentPayloads.Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TalentPayloads.Action.TYPE, (payload, context) -> {
            if (payload.reset()) {
                reset(context.player(), payload.skill());
            } else {
                spend(context.player(), payload.skill(), payload.talent());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> server.getPlayerList().getPlayers().forEach(Talents::send));
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Talents";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                forEachRank(player, (talent, rank) -> talent.stats().forEach((id, amount) -> {
                    StatType stat = StatTypes.get(id);
                    if (stat != null && StatTypes.isPlayerStat(stat)) {
                        out.add(stat, amount * rank);
                    }
                }));
            }
        });
        GearEffects.addSource(player -> {
            Map<Identifier, Double> effects = new LinkedHashMap<>();
            forEachRank(player, (talent, rank) -> talent.effects().forEach((id, value) -> effects.merge(id, value * rank, Double::sum)));
            return effects;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && GearEffects.has(player, SECOND_WIND)) {
                long now = player.level().getServer().getTickCount();
                if (now >= SECOND_WIND_READY.getOrDefault(player.getUUID(), 0L)) {
                    SECOND_WIND_READY.put(player.getUUID(), now + SECOND_WIND_COOLDOWN_TICKS);
                    player.setHealth(Math.max(1, player.getMaxHealth() * 0.2f));
                    player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
                    Msg.playSound(player, SoundEvents.TOTEM_USE, 0.6f, 1.4f);
                    Notice.of(Notice.Kind.COMBAT).icon(Notice.HEART).tag("SECOND WIND").color(0xFFC940)
                            .send(player, Msg.text("You shrug off a killing blow. (Ready again in 5 minutes.)", ChatFormatting.YELLOW));
                    return false;
                }
            }
            return true;
        });
    }

    public static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, List<Talent>> loaded = new LinkedHashMap<>();
        Map<Identifier, String> raw = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                Talent.Tree tree = Talent.parseTree(json.getAsJsonObject());
                loaded.computeIfAbsent(tree.skill(), skill -> new ArrayList<>()).addAll(tree.talents());
                raw.put(id, json.toString());
            } catch (RuntimeException e) {
                SkyRpgSkills.LOGGER.warn("Skipping talent file {}: {}", id, e.getMessage());
            }
        });
        trees = loaded;
        rawFiles = raw;
    }

    /** Replaces the trees with the server's (client side). */
    public static void replace(Map<Identifier, String> files) {
        Map<Identifier, List<Talent>> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            Talent.Tree tree = Talent.parseTree(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
            loaded.computeIfAbsent(tree.skill(), skill -> new ArrayList<>()).addAll(tree.talents());
        });
        trees = loaded;
    }

    public static Map<Identifier, List<Talent>> trees() {
        return trees;
    }

    public static Talent find(Identifier skill, String id) {
        for (Talent talent : trees.getOrDefault(skill, List.of())) {
            if (talent.id().equals(id)) {
                return talent;
            }
        }
        return null;
    }

    public static int rank(Player player, Identifier skill, String talent) {
        return player.getAttachedOrElse(RANKS, Map.of()).getOrDefault(skill, Map.of()).getOrDefault(talent, 0);
    }

    public static int spent(Player player, Identifier skill) {
        return player.getAttachedOrElse(RANKS, Map.of()).getOrDefault(skill, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Unspent points: one per level in the skill, minus what's spent. */
    public static int points(Player player, Identifier skill) {
        return Math.max(0, SkillLevels.level(player, skill) - spent(player, skill));
    }

    private interface RankConsumer {
        void accept(Talent talent, int rank);
    }

    private static void forEachRank(ServerPlayer player, RankConsumer consumer) {
        player.getAttachedOrElse(RANKS, Map.of()).forEach((skill, ranks) -> ranks.forEach((id, rank) -> {
            Talent talent = find(skill, id);
            if (talent != null && rank > 0) {
                consumer.accept(talent, Math.min(rank, talent.ranks()));
            }
        }));
    }

    private static void spend(ServerPlayer player, Identifier skill, String id) {
        Talent talent = find(skill, id);
        if (talent == null) {
            return;
        }
        int rank = rank(player, skill, id);
        String skillName = SkillLevels.displayName(skill);
        if (rank >= talent.ranks()) {
            player.sendOverlayMessage(Msg.text(talent.name() + " is maxed.", ChatFormatting.RED));
        } else if (SkillLevels.level(player, skill) < talent.level()) {
            player.sendOverlayMessage(Msg.text(talent.name() + " needs " + skillName + " " + talent.level() + ".", ChatFormatting.RED));
        } else if (points(player, skill) <= 0) {
            player.sendOverlayMessage(Msg.text("No " + skillName + " talent points left. Level up " + skillName + " for more.", ChatFormatting.RED));
        } else {
            setRank(player, skill, id, rank + 1);
            player.sendOverlayMessage(Msg.text(talent.name() + " " + Msg.roman(rank + 1), ChatFormatting.GREEN));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.8f);
        }
    }

    private static void setRank(ServerPlayer player, Identifier skill, String id, int rank) {
        Map<Identifier, Map<String, Integer>> all = new HashMap<>(player.getAttachedOrElse(RANKS, Map.of()));
        Map<String, Integer> ranks = new HashMap<>(all.getOrDefault(skill, Map.of()));
        ranks.put(id, rank);
        all.put(skill, Map.copyOf(ranks));
        player.setAttached(RANKS, Map.copyOf(all));
        Stats.refresh(player);
    }

    private static void reset(ServerPlayer player, Identifier skill) {
        if (spent(player, skill) == 0) {
            return;
        }
        long today = LocalDate.now().toEpochDay();
        boolean free = player.getAttachedOrElse(FREE_RESET_DAY, -1L) != today;
        if (!free && !Wallet.take(player, RESET_COST)) {
            player.sendOverlayMessage(Msg.text("A second reset today costs " + Msg.number(RESET_COST) + " coins.", ChatFormatting.RED));
            return;
        }
        if (free) {
            player.setAttached(FREE_RESET_DAY, today);
        }
        Map<Identifier, Map<String, Integer>> all = new HashMap<>(player.getAttachedOrElse(RANKS, Map.of()));
        all.remove(skill);
        player.setAttached(RANKS, Map.copyOf(all));
        Stats.refresh(player);
        player.sendOverlayMessage(Msg.text(SkillLevels.displayName(skill) + " talents reset" + (free ? " (free today)" : " for "
                + Msg.number(RESET_COST) + " coins"), ChatFormatting.YELLOW));
    }

    private static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, new TalentPayloads.Definitions(rawFiles));
    }
}
