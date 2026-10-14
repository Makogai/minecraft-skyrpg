package com.skyrpg.devtools.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.rcon.RconConsoleSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Commands Claude runs over RCON normally echo to every operator's chat ("[Rcon: Successfully filled
 * 312 blocks]"). Dev only: keep those echoes out of players' chat. The replies still reach RCON itself.
 */
@Mixin(CommandSourceStack.class)
abstract class QuietRconMixin {
    @Shadow
    @Final
    private CommandSource source;

    @Inject(method = "broadcastToAdmins", at = @At("HEAD"), cancellable = true)
    private void skipRconEcho(Component message, CallbackInfo ci) {
        if (source instanceof RconConsoleSource) {
            ci.cancel();
        }
    }
}
