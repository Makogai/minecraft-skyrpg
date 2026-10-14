package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The chat input line's background (vanilla's only fill in ChatScreen's drawing) in the SkyRPG look. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void skyrpg_chat$inputBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        ChatLook.inputBox(graphics, x0, y0, x1, y1, color);
    }
}
