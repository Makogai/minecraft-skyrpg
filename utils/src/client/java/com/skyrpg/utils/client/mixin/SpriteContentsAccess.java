package com.skyrpg.utils.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sprite's pixels, so the map can colour each block like its texture. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccess {
    @Accessor("originalImage")
    NativeImage skyrpg$originalImage();
}
