package com.skyrpg.combat.client;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.stats.StatTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The nameplate over a levelled mob: "[Lv 12] Zombie 85/100❤", with health in SkyRPG units (like the health bar) and
 * coloured by how hurt it is; Elites get a gold "✦ Elite". Shown within 20 blocks, when you can see the mob.
 */
public final class Nameplates {
    private static final double RANGE = 20;

    private Nameplates() {
    }

    /** The plate for this entity, or null for none (not levelled, too far, out of sight, switched off). */
    public static Component plate(Entity entity, double distanceSq) {
        if (!(entity instanceof LivingEntity living) || !SkyRpgCombatClient.nameplatesOn() || distanceSq > RANGE * RANGE) {
            return null;
        }
        MobLevels.MobInfo info = MobLevels.info(entity);
        Minecraft minecraft = Minecraft.getInstance();
        if (info == null || living.isDeadOrDying() || minecraft.player == null || !minecraft.player.hasLineOfSight(entity)) {
            return null;
        }
        MutableComponent plate = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Lv " + info.level()).withStyle(levelColor(info.level())))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        if (info.elite()) {
            plate.append(Component.literal("✦ Elite ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(entity.getName().copy().withStyle(ChatFormatting.GOLD));
        } else {
            plate.append(entity.getName().copy().withStyle(ChatFormatting.WHITE));
        }
        // Tough bosses take less damage than their (capped) vanilla health suggests: show the real numbers.
        double toughness = Math.max(1, info.toughness());
        double health = Math.max(0, living.getHealth()) * StatTypes.HEALTH_SCALE * toughness;
        double max = living.getMaxHealth() * StatTypes.HEALTH_SCALE * toughness;
        double part = max <= 0 ? 0 : health / max;
        ChatFormatting color = part > 0.5 ? ChatFormatting.GREEN : part > 0.25 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return plate.append(Component.literal(" " + Math.round(Math.ceil(health))).withStyle(color))
                .append(Component.literal("/" + Math.round(max)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("❤").withStyle(ChatFormatting.RED));
    }

    private static ChatFormatting levelColor(int level) {
        if (level < 10) {
            return ChatFormatting.GREEN;
        }
        if (level < 20) {
            return ChatFormatting.YELLOW;
        }
        if (level < 30) {
            return ChatFormatting.GOLD;
        }
        return level < 40 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
    }
}
