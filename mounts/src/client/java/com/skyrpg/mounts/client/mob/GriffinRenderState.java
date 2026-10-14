package com.skyrpg.mounts.client.mob;

import com.skyrpg.mounts.GriffinColor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link GriffinModel} needs from a griffin besides the walk: its colour and how far into flight and glide it is. */
public class GriffinRenderState extends LivingEntityRenderState {
    public GriffinColor color = GriffinColor.GOLDEN;
    /** 0 folded and on the ground to 1 flying (wings out, legs tucked). */
    public float fly;
    /** 0 flapping to 1 gliding (wings spread and still). */
    public float glide;
    /** Time for the colour animations ({@link GriffinFx}), in ticks: age with a per-griffin offset, so neighbours differ. */
    public float fxTicks;
    /** Whether something rides it (the neck lifts, the tail raises). */
    public boolean ridden;
}
