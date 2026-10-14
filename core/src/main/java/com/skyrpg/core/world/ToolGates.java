package com.skyrpg.core.world;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.items.ToolTiers;
import com.skyrpg.core.text.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Tool tiers for blocks: SkyRPG's tougher ores and woods are in {@code skyrpg_core:tool_tier/<n>} block tags and don't
 * crack at all unless you hold the right kind of tool (a pickaxe for stone and ore, an axe for wood) of at least tier n
 * (see {@link ToolTiers}). Hitting one with a weaker tool says what's needed on the action bar.
 */
public final class ToolGates {
    public static final int MAX = 7;
    @SuppressWarnings("unchecked")
    private static final TagKey<Block>[] TAGS = new TagKey[MAX + 1];
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    static {
        for (int tier = 1; tier <= MAX; tier++) {
            TAGS[tier] = TagKey.create(Registries.BLOCK, SkyRpgCore.id("tool_tier/" + tier));
        }
    }

    private ToolGates() {
    }

    /** The tier this block needs (0 if it has no gate). */
    public static int required(BlockBehaviour.BlockStateBase state) {
        for (int tier = MAX; tier >= 1; tier--) {
            if (state.is(TAGS[tier])) {
                return tier;
            }
        }
        return 0;
    }

    /** "axe" for wood, else "pickaxe". */
    public static String toolName(BlockBehaviour.BlockStateBase state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE) ? "axe" : "pickaxe";
    }

    /** The held tool's tier for this block: 0 if it's the wrong kind of tool. */
    public static int tierFor(ItemStack held, BlockBehaviour.BlockStateBase state) {
        boolean axe = state.is(BlockTags.MINEABLE_WITH_AXE);
        return held.is(axe ? ItemTags.AXES : ItemTags.PICKAXES) ? ToolTiers.tier(held) : 0;
    }

    public static boolean canBreak(Player player, BlockBehaviour.BlockStateBase state) {
        int required = required(state);
        if (required == 0 || player.isCreative()) {
            return true;
        }
        int held = tierFor(player.getMainHandItem(), state);
        if (held >= required) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            long now = serverPlayer.level().getServer().getTickCount();
            if (now - LAST_HINT.getOrDefault(player.getUUID(), -100L) >= 40) {
                LAST_HINT.put(player.getUUID(), now);
                String tool = toolName(state);
                serverPlayer.sendOverlayMessage(Msg.text("This needs a Tier " + ToolTiers.roman(required) + " " + tool, ChatFormatting.RED)
                        .append(Msg.text("  (yours: " + (held == 0 ? "no " + tool : "Tier " + ToolTiers.roman(held)) + ")", ChatFormatting.GRAY)));
            }
        }
        return false;
    }
}
