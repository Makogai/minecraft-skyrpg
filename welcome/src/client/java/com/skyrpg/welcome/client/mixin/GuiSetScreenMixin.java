package com.skyrpg.welcome.client.mixin;

import com.skyrpg.welcome.client.SkyRpgWelcomeClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wherever the game opens the vanilla title screen (start-up, leaving a world, closing a sub-screen, or closing
 * the last screen with no world loaded), the SkyRPG one opens instead.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void skyrpg_welcome$title(Screen screen, CallbackInfo ci) {
        Screen replacement = SkyRpgWelcomeClient.replaceTitle(screen);
        if (replacement != null) {
            ((Gui) (Object) this).setScreen(replacement);
            ci.cancel();
        }
    }
}
