package com.skyrpg.core.client.theme.mixin;

import com.skyrpg.core.client.theme.ThemePack;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes options.txt's pack list match the theme setting right before Minecraft applies it at startup. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    public List<String> resourcePacks;

    @Inject(method = "loadSelectedResourcePacks", at = @At("HEAD"))
    private void skyrpg$syncThemePack(PackRepository repository, CallbackInfo ci) {
        ThemePack.beforePacksLoaded(resourcePacks, repository);
    }
}
