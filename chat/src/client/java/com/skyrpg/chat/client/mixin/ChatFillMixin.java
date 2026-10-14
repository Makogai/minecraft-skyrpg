package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The chat's background fills go through these two private drawing helpers (closed chat and open chat): recolour
 * them to the SkyRPG theme, keeping vanilla's opacity.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"})
public abstract class ChatFillMixin {
    @ModifyArg(method = "fill(IIIII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
    private int skyrpg_chat$themedFill(int color) {
        return ChatLook.lineFill(color);
    }
}
