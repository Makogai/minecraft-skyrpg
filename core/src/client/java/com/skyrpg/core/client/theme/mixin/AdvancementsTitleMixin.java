package com.skyrpg.core.client.theme.mixin;

import com.skyrpg.core.client.theme.ThemePack;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The "Advancements" window title is drawn in the same dark grey as container titles. */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsTitleMixin {
    @ModifyArg(method = "extractWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
            index = 4)
    private int skyrpg$themedTitleColor(int color) {
        return ThemePack.labelColor(color);
    }
}
