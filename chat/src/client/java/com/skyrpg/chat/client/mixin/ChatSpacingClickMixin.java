package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatSpacing;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Message spacing for clicks and hovers: the clickable-text pass sees each line where it is drawn. See {@link ChatSpacing}. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$ClickableTextOnlyGraphicsAccess", remap = false)
public abstract class ChatSpacingClickMixin {
    @Shadow(remap = false)
    public abstract boolean handleMessage(int y, float opacity, FormattedCharSequence line);

    @Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrpg_chat$spacedClick(int y, float opacity, FormattedCharSequence line, CallbackInfoReturnable<Boolean> cir) {
        if (ChatSpacing.shouldShift()) {
            int shift = ChatSpacing.current;
            ChatSpacing.reissuing = true;
            try {
                cir.setReturnValue(handleMessage(y + shift, opacity, line));
            } finally {
                ChatSpacing.reissuing = false;
            }
        }
    }
}
