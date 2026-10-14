package com.skyrpg.items.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Timed boosts (core's {@link Boosts}): extra stats and gear effects until they run out. Saved with the player
 * (they keep counting while you're offline); a new boost with the same key replaces the old one. Counted in stats
 * as the "Boosts" source and in {@link GearEffectSources}.
 */
public final class BoostStore {
    /** One running boost. {@code expires} is the overworld's game time when it ends. */
    record Active(String key, String name, Map<String, Double> stats, Map<String, Double> effects, long expires) {
        static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Active::key),
                Codec.STRING.fieldOf("name").forGetter(Active::name),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("stats").forGetter(Active::stats),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("effects").forGetter(Active::effects),
                Codec.LONG.fieldOf("expires").forGetter(Active::expires)
        ).apply(i, Active::new));
    }

    static final AttachmentType<List<Active>> ACTIVE = AttachmentRegistry.create(SkyRpgItems.id("boosts"), builder -> builder
            .persistent(Active.CODEC.listOf())
            .initializer(List::of));

    private BoostStore() {
    }

    public static void init() {
        Boosts.install(BoostStore::give);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Boosts";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                long now = now(player);
                for (Active boost : active(player)) {
                    if (boost.expires() > now) {
                        boost.stats().forEach((id, amount) -> {
                            StatType stat = StatTypes.get(id);
                            if (stat != null && StatTypes.isPlayerStat(stat)) {
                                out.add(stat, amount);
                            }
                        });
                    }
                }
            }
        });
    }

    static List<Active> active(ServerPlayer player) {
        return player.getAttachedOrElse(ACTIVE, List.of());
    }

    private static long now(ServerPlayer player) {
        return player.level().getServer().overworld().getGameTime();
    }

    private static void give(ServerPlayer player, String key, Component name, Map<String, Double> stats, Map<Identifier, Double> effects, int ticks) {
        List<Active> list = new ArrayList<>(active(player));
        list.removeIf(boost -> boost.key().equals(key));
        Map<String, Double> effectIds = new HashMap<>();
        effects.forEach((id, value) -> effectIds.put(id.toString(), value));
        list.add(new Active(key, name.getString(), Map.copyOf(stats), Map.copyOf(effectIds), now(player) + ticks));
        player.setAttached(ACTIVE, List.copyOf(list));
        Stats.refresh(player);
    }

    /** Every second: drops boosts that ran out (with a note) and refreshes stats if anything changed. */
    static void tick(ServerPlayer player) {
        List<Active> list = active(player);
        if (list.isEmpty()) {
            return;
        }
        long now = now(player);
        List<Active> left = new ArrayList<>();
        for (Active boost : list) {
            if (boost.expires() > now) {
                left.add(boost);
            } else {
                Notice.send(player, Notice.Kind.INFO, Msg.text(boost.name() + " has worn off.", ChatFormatting.GRAY));
            }
        }
        if (left.size() != list.size()) {
            player.setAttached(ACTIVE, List.copyOf(left));
            Stats.refresh(player);
        }
    }

    /** Seconds left on the boost with this key, or 0. */
    public static long secondsLeft(ServerPlayer player, String key) {
        long now = now(player);
        for (Active boost : active(player)) {
            if (boost.key().equals(key)) {
                return Math.max(0, (boost.expires() - now) / 20);
            }
        }
        return 0;
    }
}
