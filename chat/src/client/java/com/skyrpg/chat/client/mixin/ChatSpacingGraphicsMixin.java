package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatSpacing;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Message spacing for the tag stripe and tag icon beside each line, in all three chat passes (closed chat, open chat
 * and the clickable-text pass), so they move with their line. See {@link ChatSpacing}.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$ClickableTextOnlyGraphicsAccess"}, remap = false)
public abstract class ChatSpacingGraphicsMixin {
    @Shadow(remap = false)
    public abstract void handleTag(int x0, int y0, int x1, int y1, float alpha, GuiMessageTag tag);

    @Shadow(remap = false)
    public abstract void handleTagIcon(int x, int y, boolean forceVisible, GuiMessageTag tag, GuiMessageTag.Icon icon);

    @Inject(method = "handleTag(IIIIFLnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrpg_chat$spacedTag(int x0, int y0, int x1, int y1, float alpha, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatSpacing.shouldShift()) {
            int shift = ChatSpacing.current;
            ChatSpacing.reissuing = true;
            try {
                handleTag(x0, y0 + shift, x1, y1 + shift, alpha, tag);
            } finally {
                ChatSpacing.reissuing = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "handleTagIcon(IIZLnet/minecraft/client/multiplayer/chat/GuiMessageTag;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag$Icon;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrpg_chat$spacedIcon(int x, int y, boolean forceVisible, GuiMessageTag tag, GuiMessageTag.Icon icon, CallbackInfo ci) {
        if (ChatSpacing.shouldShift()) {
            int shift = ChatSpacing.current;
            ChatSpacing.reissuing = true;
            try {
                handleTagIcon(x, y + shift, forceVisible, tag, icon);
            } finally {
                ChatSpacing.reissuing = false;
            }
            ci.cancel();
        }
    }
}
