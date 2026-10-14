package com.skyrpg.combat;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What killing a levelled mob pays, on top of its vanilla drops and the Combat XP for its type:
 * <ul>
 *   <li>Combat XP: 1.5 per level.</li>
 *   <li>Valor Marks (the Combat Master's currency): 25% + 1% per level, one more per 15 levels; Elites always drop three
 *   more.</li>
 *   <li>Rare drops: a Dread Fang (1 in 400, level 10+), a Nightmare Core (1 in 4000, level 30+).</li>
 * </ul>
 * Magic Find makes every chance bigger (+1% per point). Mobs from spawners ("farmed") give a quarter of the XP and nothing
 * else.
 */
public final class CombatRewards {
    private static final double XP_PER_LEVEL = 1.5;
    private static final double FARMED_SHARE = 0.25;
    private static final int FANG_CHANCE = 400;
    private static final int FANG_LEVEL = 10;
    private static final int CORE_CHANCE = 4000;
    private static final int CORE_LEVEL = 30;

    private CombatRewards() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            MobLevels.MobInfo info = MobLevels.info(mob);
            ServerPlayer player = source.getEntity() instanceof ServerPlayer direct ? direct
                    : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (info == null || player == null) {
                return;
            }
            reward(level, player, mob, info);
        });
    }

    private static void reward(ServerLevel level, ServerPlayer player, Mob mob, MobLevels.MobInfo info) {
        RandomSource random = player.getRandom();
        int mobLevel = info.level();
        double find = 1 + Math.max(0, Stats.get(player, StatTypes.MAGIC_FIND)) / 100;
        if (MobLevels.farmed(mob)) {
            SkillLevels.giveXp(player, SkillLevels.COMBAT, mobLevel * XP_PER_LEVEL * FARMED_SHARE);
            return;
        }
        SkillLevels.giveXp(player, SkillLevels.COMBAT, mobLevel * XP_PER_LEVEL);

        int marks = random.nextDouble() < Math.min(1, (0.25 + 0.01 * mobLevel) * find) ? 1 + mobLevel / 15 : 0;
        if (info.elite()) {
            marks += 3;
        }
        if (marks > 0) {
            mob.spawnAtLocation(level, new ItemStack(CombatItems.VALOR_MARK, marks));
        }
        if (mobLevel >= CORE_LEVEL && random.nextDouble() * CORE_CHANCE < find) {
            rareDrop(level, player, mob, CombatItems.NIGHTMARE_CORE, ChatFormatting.DARK_PURPLE);
        } else if (mobLevel >= FANG_LEVEL && random.nextDouble() * FANG_CHANCE < find) {
            rareDrop(level, player, mob, CombatItems.DREAD_FANG, ChatFormatting.RED);
        }
    }

    private static void rareDrop(ServerLevel level, ServerPlayer player, Mob mob, Item item, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        mob.spawnAtLocation(level, stack.copy());
        Notice.rareDrop(player, stack.getHoverName().copy().withStyle(color)
                .append(Component.literal(" (Lv " + MobLevels.level(mob) + " ").withStyle(ChatFormatting.DARK_GRAY))
                .append(mob.getName().copy().withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)), Notice.rgb(color));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
    }
}
