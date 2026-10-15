package com.skyrpg.hub.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** TextDisplay keeps its setters private; the leaderboard podiums need them for their name plates. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("setText")
    void skyrpg$setText(Component text);

    @Invoker("setLineWidth")
    void skyrpg$setLineWidth(int width);

    @Invoker("setBackgroundColor")
    void skyrpg$setBackgroundColor(int argb);

    @Invoker("setFlags")
    void skyrpg$setFlags(byte flags);
}
