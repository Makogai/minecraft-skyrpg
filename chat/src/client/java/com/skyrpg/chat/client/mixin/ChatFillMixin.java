package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatHelperLook;
import com.skyrpg.chat.client.ChatLook;
import com.skyrpg.chat.client.ChatSpacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The chat's background fills go through these two private drawing helpers (closed chat and open chat): recolour
 * them to the SkyRPG theme, keeping vanilla's opacity.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"})
public abstract class ChatFillMixin {
    @Shadow(remap = false)
    public abstract void fill(int x0, int y0, int x1, int y1, int color);

    @ModifyArg(method = "fill(IIIII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
    private int skyrpg_chat$themedFill(int color) {
        return ChatLook.lineFill(color);
    }

    /**
     * A line's background: moved up by the message spacing (filled again at the new place, see ChatSpacing), then
     * remembered, so SkyHelper's glow can be painted on exactly the same pixels.
     */
    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true)
    private void skyrpg_chat$lineBox(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (ChatSpacing.shouldShift()) {
            int shift = ChatSpacing.current;
            ChatSpacing.reissuing = true;
            try {
                fill(x0, y0 + shift, x1, y1 + shift, color);
            } finally {
                ChatSpacing.reissuing = false;
            }
            ci.cancel();
            return;
        }
        ChatHelperLook.recordFill(x0, y0, x1, y1);
    }
}
