package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link DwarvenConstructModel} needs from a Dwarven Construct: the progress of its punch. */
public class DwarvenConstructRenderState extends LivingEntityRenderState {
    /** Progress of the melee swing (0 to 1), 0 when not punching. */
    public float attackTime;
}
