package com.skyrpg.chat.mixin;

import com.skyrpg.chat.Whispers;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** /msg, /tell and /w all end in MsgCommand.sendMessage: send a styled whisper instead (vanilla's if styling fails). */
@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private static void skyrpg_chat$styledWhisper(CommandSourceStack source, Collection<ServerPlayer> players, PlayerChatMessage message,
                                                  CallbackInfo ci) {
        if (Whispers.send(source, players, message.signedContent())) {
            ci.cancel();
        }
    }
}
