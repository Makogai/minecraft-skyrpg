package com.skyrpg.chat.client.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chat history, to swap stripes on existing messages when the setting changes. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccess {
    @Accessor("allMessages")
    List<GuiMessage> skyrpg_chat$allMessages();

    @Accessor("chatScrollbarPos")
    int skyrpg_chat$scroll();
}
