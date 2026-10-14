package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link SporelingModel} needs from a Sporeling besides the walk: its attack swing (the cap head-butt). */
public class SporelingRenderState extends LivingEntityRenderState {
    /** Progress of the melee swing, 0 when not swinging. */
    public float attackTime;
}
