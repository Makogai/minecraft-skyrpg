package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link BoneGiantModel} needs from the Bone Giant: how far into its club swing or bone quake it is. */
public class BoneGiantRenderState extends LivingEntityRenderState {
    /** Ticks left of the club swing pose (fractional, with the partial tick), 0 when not swinging. */
    public float swingTicks;
    /** Ticks left of the bone quake pose, 0 when not quaking. */
    public float quakeTicks;
}
