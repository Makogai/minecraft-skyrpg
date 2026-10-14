package com.skyrpg.chat.client.mixin;

import com.skyrpg.chat.client.ChatHooks;
import com.skyrpg.chat.client.ChatLook;
import com.skyrpg.chat.client.ChatSpacing;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every chat message passes ChatComponent.addMessage: here it gets its kind and stripe, repeats fold into the line
 * before, and the open tab decides whether it's drawn. Drawing the chat gets the SkyRPG panel behind it.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Unique
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
    @Unique
    private static final String NEW_GUI_MESSAGE = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;<init>(ILnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
    @Unique
    private static final String EXTRACT = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;III"
            + "Lnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    public abstract int getLinesPerPage();

    @Unique
    private static final String LINE_ACCEPT = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;"
            + "accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V";

    /** Message spacing: each pass over the lines first works out how far each one moves up (see ChatSpacing). */
    @Inject(method = "forEachLine", at = @At("HEAD"))
    private void skyrpg_chat$spacingPrepare(CallbackInfoReturnable<Integer> cir) {
        ChatSpacing.prepare(trimmedMessages, chatScrollbarPos, getLinesPerPage());
    }

    @ModifyArg(method = "forEachLine", at = @At(value = "INVOKE", target = LINE_ACCEPT), index = 1)
    private int skyrpg_chat$spacingEnter(int slot) {
        ChatSpacing.enter(slot);
        return slot;
    }

    @Inject(method = "forEachLine", at = @At(value = "INVOKE", target = LINE_ACCEPT, shift = At.Shift.AFTER))
    private void skyrpg_chat$spacingLeave(CallbackInfoReturnable<Integer> cir) {
        ChatSpacing.leave();
    }

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"))
    private void skyrpg_chat$beforeAdd(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatHooks.beforeAdd(allMessages, trimmedMessages, contents, signature, source, tag);
    }

    @ModifyArg(method = ADD_MESSAGE, at = @At(value = "INVOKE", target = NEW_GUI_MESSAGE), index = 4)
    private GuiMessageTag skyrpg_chat$tag(GuiMessageTag tag) {
        return ChatHooks.tagFor(tag);
    }

    @Inject(method = ADD_MESSAGE, at = @At("RETURN"))
    private void skyrpg_chat$afterAdd(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatHooks.afterAdd();
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void skyrpg_chat$filter(GuiMessage message, CallbackInfo ci) {
        if (!ChatHooks.display(message)) {
            ci.cancel();
        }
    }

    @Inject(method = EXTRACT, at = @At("HEAD"))
    private void skyrpg_chat$panel(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode,
                                   boolean changeCursorOnInsertions, CallbackInfo ci) {
        ChatLook.beforeChat(graphics, (ChatComponent) (Object) this, displayMode, mouseX, mouseY);
    }

    @Inject(method = EXTRACT, at = @At("RETURN"))
    private void skyrpg_chat$panelDone(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode,
                                       boolean changeCursorOnInsertions, CallbackInfo ci) {
        ChatLook.afterChat();
    }
}
