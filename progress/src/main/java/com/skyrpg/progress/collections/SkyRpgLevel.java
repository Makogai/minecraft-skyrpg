package com.skyrpg.progress.collections;

import com.mojang.serialization.Codec;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.BossKills;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.vfx.Vfx;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The SkyRPG Level: every 100 SkyRPG XP is a level, without a cap. XP comes from quests, collection tiers (4 each), skill
 * levels (5 each), slayer levels (10 each), first dungeon clears (20 to 60) and first boss kills (15). The XP lives in an
 * attachment synced to every client (chat badge, Profile page); the chat mod reads it by id ({@code skyrpg_progress:level}).
 * Every 10th level pays coins and plays the level-up effect.
 */
public final class SkyRpgLevel {
    public static final int XP_PER_LEVEL = 100;
    public static final int SKILL_LEVEL_XP = 5;
    public static final int SLAYER_LEVEL_XP = 10;
    public static final int BOSS_FIRST_KILL_XP = 15;
    private static final Identifier LEVEL_UP_EFFECT = Identifier.fromNamespaceAndPath("skyrpg_vfx", "level_up");

    /** Total SkyRPG XP; synced to everyone. */
    public static final AttachmentType<Integer> XP = AttachmentRegistry.create(SkyRpgProgress.id("level"), builder -> builder
            .persistent(Codec.INT)
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
            .initializer(() -> 0)
            .copyOnDeath());

    /** Bosses this player has already been rewarded a first kill for (ids as strings). */
    private static final AttachmentType<Set<String>> FIRSTS = AttachmentRegistry.create(SkyRpgProgress.id("first_kills"), builder -> builder
            .persistent(Codec.STRING.listOf().xmap(list -> (Set<String>) new HashSet<>(list), set -> java.util.List.copyOf(set)))
            .initializer(HashSet::new)
            .copyOnDeath());

    private SkyRpgLevel() {
    }

    /** The bosses this player has been rewarded a first kill for (ids as strings); the titles seed their kill counts from it. */
    public static Set<String> firstKills(ServerPlayer player) {
        return player.getAttachedOrElse(FIRSTS, Set.of());
    }

    static void init() {
        SkillLevels.LEVEL_UP.register((player, skill, oldLevel, newLevel) -> {
            int gained = newLevel - oldLevel;
            if (gained <= 0) {
                return;
            }
            boolean slayer = skill.getNamespace().equals("skyrpg_combat") && skill.getPath().startsWith("slayer/");
            addXp(player, gained * (slayer ? SLAYER_LEVEL_XP : SKILL_LEVEL_XP), slayer ? "slayer level" : "skill level");
        });
        BossKills.KILLED.register((player, boss, level, kind) -> {
            Set<String> done = player.getAttachedOrCreate(FIRSTS);
            if (!done.add(boss.toString())) {
                return;
            }
            if (kind == BossKills.Kind.DUNGEON_CHAMPION) {
                addXp(player, dungeonClearXp(level), "dungeon first clear");
            } else {
                addXp(player, BOSS_FIRST_KILL_XP, "first boss kill");
            }
        });
    }

    /** 20 to 60 XP by how tough the dungeon's champion is. */
    static int dungeonClearXp(int bossLevel) {
        if (bossLevel < 25) {
            return 20;
        }
        if (bossLevel < 40) {
            return 30;
        }
        if (bossLevel < 52) {
            return 40;
        }
        if (bossLevel < 62) {
            return 50;
        }
        return 60;
    }

    /** The player's SkyRPG Level (0 for a new player). Works on both sides; it's synced to every client. */
    public static int level(Player player) {
        return xp(player) / XP_PER_LEVEL;
    }

    /** Total SkyRPG XP. */
    public static int xp(Player player) {
        return Math.max(0, player.getAttachedOrElse(XP, 0));
    }

    /** XP gathered towards the next level (0 to 99). */
    public static int xpIntoLevel(Player player) {
        return xp(player) % XP_PER_LEVEL;
    }

    /** Gives SkyRPG XP; {@code reason} is for logs and debugging. Every 10th level reached pays coins and plays the effect. */
    public static void addXp(ServerPlayer player, int xp, String reason) {
        if (xp <= 0) {
            return;
        }
        int before = xp(player);
        long total = Math.min(Integer.MAX_VALUE, (long) before + xp);
        player.setAttached(XP, (int) total);
        int oldLevel = before / XP_PER_LEVEL;
        int newLevel = (int) (total / XP_PER_LEVEL);
        for (int level = oldLevel + 1; level <= newLevel; level++) {
            levelReached(player, level, level == newLevel);
        }
    }

    private static void levelReached(ServerPlayer player, int level, boolean latest) {
        if (level % 10 != 0) {
            if (latest) {
                player.sendOverlayMessage(Component.literal("SkyRPG Level " + level).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            return;
        }
        long coins = 500L * level;
        Wallet.give(player, coins);
        Notice.of(Notice.Kind.SKILL).icon(Notice.STAR).tag("LEVEL UP").color(0xFFE14D).send(player,
                Msg.text("SkyRPG Level ", ChatFormatting.GRAY).append(Msg.text(String.valueOf(level), ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Msg.text("  +" + Msg.number(coins) + " coins", ChatFormatting.GOLD)));
        Msg.playSound(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        if (Vfx.exists(LEVEL_UP_EFFECT)) {
            Vfx.play(player, LEVEL_UP_EFFECT);
        }
    }

    /** For commands and tests: sets total XP without rewards. */
    static void setXp(ServerPlayer player, int xp) {
        player.setAttached(XP, Math.max(0, xp));
    }
}
