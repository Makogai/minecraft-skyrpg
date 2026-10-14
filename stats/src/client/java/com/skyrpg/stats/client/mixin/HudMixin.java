package com.skyrpg.stats.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The action bar and the held item's name sit where vanilla's hearts and armour were, which is now our health and mana
 * bars; lift them above the bars while those show (survival and adventure).
 */
@Mixin(Hud.class)
abstract class HudMixin {
    private static final String TEXT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;"
            + "Lnet/minecraft/network/chat/Component;IIII)V";
    private static final int OVERLAY_LIFT = 16;
    private static final int ITEM_NAME_LIFT = 12;

    @ModifyArg(method = "extractOverlayMessage", at = @At(value = "INVOKE", target = TEXT), index = 3)
    private int skyrpg$liftOverlay(int y) {
        return barsShown() ? y - OVERLAY_LIFT : y;
    }

    @ModifyArg(method = "extractSelectedItemName", at = @At(value = "INVOKE", target = TEXT), index = 3)
    private int skyrpg$liftItemName(int y) {
        return barsShown() ? y - ITEM_NAME_LIFT : y;
    }

    private static boolean barsShown() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }
}
