package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link IronThaneModel} needs from the Iron Thane: how far into its hammer slam it is. */
public class IronThaneRenderState extends LivingEntityRenderState {
    /** Ticks left of the slam pose (fractional, with the partial tick), 0 when not slamming. */
    public float slamTicks;
}
