package com.skyrpg.hub.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display keeps its setters private; the hub's decor needs them to pose and animate its displays. */
@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation")
    void skyrpg$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void skyrpg$setTransformationInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void skyrpg$setTransformationInterpolationDelay(int ticks);

    @Invoker("setBrightnessOverride")
    void skyrpg$setBrightnessOverride(Brightness brightness);

    @Invoker("setViewRange")
    void skyrpg$setViewRange(float range);
}
