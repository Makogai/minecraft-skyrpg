package com.skyrpg.skills;

import com.mojang.serialization.Codec;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Total XP per skill for each player. Saved with the player, kept on death, synced to that
 * player's client for future skill screens. Levels are always computed from XP, never stored, so
 * changing a skill's curve in data re-levels everyone.
 */
public final class PlayerSkills {
    public static final AttachmentType<Map<Identifier, Double>> XP = AttachmentRegistry.create(SkyRpgSkills.id("xp"), builder -> builder
            .persistent(Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE))
            .initializer(Map::of)
            .copyOnDeath()
            .syncWith(ByteBufCodecs.<ByteBuf, Identifier, Double, Map<Identifier, Double>>map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.DOUBLE),
                    AttachmentSyncPredicate.targetOnly()));

    /** Every this many levels pays a coin milestone. */
    private static final int MILESTONE_EVERY = 5;

    private PlayerSkills() {
    }

    static void init() {
        // Loads the class so the attachment is registered during mod init.
    }

    public static double getXp(Player player, Skill skill) {
        return player.getAttachedOrElse(XP, Map.of()).getOrDefault(skill.id(), 0.0);
    }

    public static int getLevel(Player player, Skill skill) {
        return skill.levelFor(getXp(player, skill));
    }

    public static void reset(Player player) {
        player.removeAttached(XP);
    }

    /** Sets a skill's total XP directly, without messages (admin use). */
    public static void setXp(Player player, Skill skill, double xp) {
        Map<Identifier, Double> all = new HashMap<>(player.getAttachedOrElse(XP, Map.of()));
        all.put(skill.id(), Math.max(0, xp));
        player.setAttached(XP, Map.copyOf(all));
    }

    /**
     * XP earned by playing: boosted by the skill's wisdom stat (e.g. Farming Wisdom 20 = +20%) and any running
     * {@link XpBoost}, then added with {@link #addXp}.
     */
    public static void gainXp(ServerPlayer player, Skill skill, double baseAmount) {
        StatType wisdom = StatTypes.get(skill.id().getPath() + "_wisdom");
        double multiplier = (wisdom == null ? 1 : 1 + Stats.get(player, wisdom) / 100) * XpBoost.multiplier();
        addXp(player, skill, baseAmount * multiplier);
        if (baseAmount > 0) {
            SkillLevels.XP_EARNED.invoker().onXpEarned(player, skill.id(), baseAmount * multiplier);
        }
    }

    /** Adds XP, shows "+4 Farming (54/125)" above the hotbar, and announces any level up. */
    public static void addXp(ServerPlayer player, Skill skill, double amount) {
        if (amount <= 0) {
            return;
        }
        double before = getXp(player, skill);
        double after = before + amount;
        Map<Identifier, Double> xp = new HashMap<>(player.getAttachedOrElse(XP, Map.of()));
        xp.put(skill.id(), after);
        player.setAttached(XP, Map.copyOf(xp));

        int oldLevel = skill.levelFor(before);
        int newLevel = skill.levelFor(after);
        String progress = newLevel >= skill.maxLevel()
                ? "MAX"
                : Msg.number(skill.xpIntoLevel(after)) + "/" + Msg.number(skill.xpForNextLevel(newLevel));
        player.sendOverlayMessage(Msg.text("+" + Msg.number(amount) + " " + skill.name() + " (" + progress + ")", ChatFormatting.DARK_AQUA));

        SkillEvents.XP_GAINED.invoker().onXpGained(player, skill, amount, after);
        if (newLevel > oldLevel) {
            announceLevelUp(player, skill, oldLevel, newLevel);
            Stats.refresh(player);
            SkillEvents.LEVEL_UP.invoker().onLevelUp(player, skill, oldLevel, newLevel);
        }
    }

    private static void announceLevelUp(ServerPlayer player, Skill skill, int oldLevel, int newLevel) {
        Notice.of(Notice.Kind.SKILL).icon(Notice.skillIcon(skill.id().getPath())).tag("LEVEL UP")
                .hover(Component.literal("Open your skills")).click("/skills")
                .send(player, skill.displayName()
                        .append(Msg.text(" " + (oldLevel == 0 ? "" : Msg.roman(oldLevel)) + " ➜ ", ChatFormatting.DARK_GRAY))
                        .append(Component.literal(Msg.roman(newLevel)).withColor(skill.color())));
        if (!skill.rewardsPerLevel().isEmpty()) {
            int levels = newLevel - oldLevel;
            MutableComponent rewards = Msg.text("    Rewards ", ChatFormatting.DARK_GRAY);
            boolean[] first = {true};
            skill.rewardsPerLevel().forEach((statId, perLevel) -> {
                StatType stat = StatTypes.get(statId);
                if (stat != null) {
                    if (!first[0]) {
                        rewards.append(Msg.text(" · ", ChatFormatting.DARK_GRAY));
                    }
                    first[0] = false;
                    rewards.append(Component.literal(stat.formatBonus(perLevel * levels) + " " + stat.symbol() + " " + stat.name()).withColor(stat.color()));
                }
            });
            if (!first[0]) {
                player.sendSystemMessage(rewards);
            }
        }
        int points = newLevel - oldLevel;
        player.sendSystemMessage(Msg.text("    +" + points + " " + skill.name() + " Talent Point" + (points == 1 ? "" : "s"), ChatFormatting.LIGHT_PURPLE)
                .append(Msg.text("  (K -> Talents)", ChatFormatting.DARK_GRAY)));
        // Every 5th level pays coins: level x 100.
        long coins = 0;
        for (int level = oldLevel + 1; level <= newLevel; level++) {
            if (level % MILESTONE_EVERY == 0) {
                coins += level * 100L;
            }
        }
        if (coins > 0) {
            Wallet.give(player, coins);
            Notice.of(Notice.Kind.COINS).tag("MILESTONE").send(player, Msg.text("+" + Msg.number(coins) + " coins", ChatFormatting.GOLD)
                    .append(Msg.text(" for reaching " + skill.name() + " " + Msg.roman(newLevel), ChatFormatting.GRAY)));
        }
        // Other mods' lines (new recipes...) go inside the box.
        SkillLevels.LEVEL_UP.invoker().onLevelUp(player, skill.id(), oldLevel, newLevel);
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }
}
