package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatHelperLook;
import com.skyrpg.chat.client.ChatImageRender;
import com.skyrpg.chat.client.ChatSpacing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The chat draws each line's text through handleMessage (closed chat and open chat have one implementation each). A
 * picture's placeholder line is drawn as a slice of the picture instead.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"}, remap = false)
public abstract class ChatImageGraphicsMixin {
    @Shadow(remap = false)
    @Final
    private GuiGraphicsExtractor graphics;

    @Shadow(remap = false)
    public abstract boolean handleMessage(int y, float opacity, FormattedCharSequence line);

    @Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrpg_chat$picture(int y, float opacity, FormattedCharSequence line, CallbackInfoReturnable<Boolean> cir) {
        if (ChatSpacing.shouldShift()) {
            // message spacing: handle the line again, that much higher (see ChatSpacing)
            int shift = ChatSpacing.current;
            ChatSpacing.reissuing = true;
            try {
                cir.setReturnValue(handleMessage(y + shift, opacity, line));
            } finally {
                ChatSpacing.reissuing = false;
            }
            return;
        }
        if (line instanceof ChatImageRender.ImageLine image) {
            ChatImageRender.draw(graphics, image, y, opacity);
            cir.setReturnValue(false);
        } else if (line instanceof ChatHelperLook.HelperLine helper) {
            ChatHelperLook.draw(graphics, helper, y, opacity);   // the glow first; vanilla draws the text over it
        }
    }
}
