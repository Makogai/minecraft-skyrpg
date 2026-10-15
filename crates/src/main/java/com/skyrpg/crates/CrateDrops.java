package com.skyrpg.crates;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Crates drop from monsters players kill while the crate is in season: rarely by day, more at night (and always in the
 * Nether and the End), more in a Blood Moon, often from elites and bosses; Magic Find helps. Mobs from spawners give
 * nothing. A player's first crate of each kind comes with a free key, so everyone gets to see the reel spin.
 */
final class CrateDrops {
    /** The crates each player has already had their free key for. */
    static final AttachmentType<List<String>> FREE_KEYS = AttachmentRegistry.create(SkyRpgCrates.id("free_keys"), builder -> builder
            .initializer(List::of)
            .persistent(Codec.STRING.listOf())
            .copyOnDeath());

    private static final Set<String> BOSS_TAGS = Set.of("skyrpg_dungeon_boss", "skyrpg_world_boss");

    private CrateDrops() {
    }

    static void init() {
        // Loads the class so the attachment is registered during mod init.
    }

    static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer killer)
                || entity.entityTags().contains(MobTags.FARMED)) {
            return;
        }
        for (Crate crate : Crates.all()) {
            if (!crate.inSeason()) {
                continue;
            }
            double chance = chance(crate.drops(), entity, level) * (1 + Stats.get(killer, StatTypes.MAGIC_FIND) / 100);
            if (level.getRandom().nextDouble() < chance) {
                drop(killer, entity, crate);
            }
        }
    }

    private static double chance(Crate.Drops drops, LivingEntity entity, ServerLevel level) {
        Set<String> tags = entity.entityTags();
        if (tags.stream().anyMatch(tag -> BOSS_TAGS.contains(tag) || tag.startsWith("skyrpg_region_boss="))) {
            return drops.boss();
        }
        if (tags.contains("skyrpg_elite")) {
            return drops.elite();
        }
        if (tags.contains("skyrpg_bloodbound") || (BloodMoon.active() && level.dimension() == Level.OVERWORLD)) {
            return drops.bloodMoon();
        }
        if (level.dimension() != Level.OVERWORLD || level.isDarkOutside()) {
            return drops.night();
        }
        return drops.day();
    }

    private static void drop(ServerPlayer killer, LivingEntity entity, Crate crate) {
        ServerLevel level = (ServerLevel) entity.level();
        entity.spawnAtLocation(level, new ItemStack(crate.item()));
        level.playSound(null, entity.blockPosition(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.8f, 0.7f);
        level.playSound(null, killer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
        Notice.rareDrop(killer, Component.literal("A ").append(Component.literal(crate.name()).withColor(0xFF9A3C))
                .append(" dropped! Right-click it to see what's inside."), 0xFFAA00);
        List<String> had = killer.getAttachedOrElse(FREE_KEYS, List.of());
        String id = crate.id().toString();
        if (!had.contains(id)) {
            List<String> now = new ArrayList<>(had);
            now.add(id);
            killer.setAttached(FREE_KEYS, List.copyOf(now));
            ItemStack key = new ItemStack(crate.key());
            killer.getInventory().add(key);
            if (!key.isEmpty()) {
                killer.drop(key, false, Prediction.SERVER_ONLY);
            }
            Notice.send(killer, Notice.Kind.CRATE, Component.literal("Your first " + crate.name() + " comes with a free ")
                    .append(crate.key().getDefaultInstance().getHoverName().copy().withColor(0xFFD24A))
                    .append("! Right-click the crate to open it."));
        }
    }
}
