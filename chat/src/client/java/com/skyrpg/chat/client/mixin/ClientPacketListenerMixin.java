package com.skyrpg.chat.client.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No "Chat messages can't be verified" toast on joining: SkyRPG Chat sends all player chat as server messages, so
 * there's nothing to verify and the warning only confuses.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Redirect(method = "handleLogin", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;addToast(Lnet/minecraft/client/gui/components/toasts/Toast;)V"))
    private void skyrpg_chat$skipInsecureChatToast(ToastManager toasts, Toast toast) {
        if (toast instanceof SystemToast system && system.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
            return;
        }
        toasts.addToast(toast);
    }
}
