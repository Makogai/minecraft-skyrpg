package com.skyrpg.combat.mob;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Void Sigil: used anywhere in the End once the Ender Dragon is dead, it starts the summoning ritual
 * ({@link EnderWyrms#startRitual}) over the spot where the user stands. One is spent per fight.
 */
public final class VoidSigilItem extends Item {
    public VoidSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        String refusal = EnderWyrms.cannotStart(server, serverPlayer);
        if (refusal != null) {
            serverPlayer.sendOverlayMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (!EnderWyrms.startRitual(server, serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
