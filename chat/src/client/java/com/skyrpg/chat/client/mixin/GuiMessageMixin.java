package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatLook;
import com.skyrpg.chat.client.ChatImageRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Lays out a message with its timestamp and "(x3)" repeat count around it, and room for its pictures after it. */
@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
    @ModifyArg(method = "splitLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
            index = 0)
    private FormattedText skyrpg_chat$decorate(FormattedText content) {
        return ChatLook.decorate((GuiMessage) (Object) this, content);
    }

    /** A message's pictures get blank lines after its text; the chat drawing mixins paint a slice of the picture on each. */
    @Inject(method = "splitLines", at = @At("RETURN"), cancellable = true)
    private void skyrpg_chat$imageLines(Font font, int width, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
        List<FormattedCharSequence> extra = ChatImageRender.linesFor((GuiMessage) (Object) this, width);
        if (!extra.isEmpty()) {
            List<FormattedCharSequence> all = new ArrayList<>(cir.getReturnValue());
            all.addAll(extra);
            cir.setReturnValue(all);
        }
    }
}
