package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link MagmaTyrantModel} needs from the Magma Tyrant: how far into its slam it is. */
public class MagmaTyrantRenderState extends LivingEntityRenderState {
    /** Ticks left of the slam pose (fractional, with the partial tick), 0 when not slamming. */
    public float slamTicks;
}
