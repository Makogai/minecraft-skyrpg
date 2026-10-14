package com.skyrpg.combat.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link FireBeetleModel} needs from a Fire Beetle besides the walk: the bite swing and the charge pose. */
public class FireBeetleRenderState extends LivingEntityRenderState {
    /** Progress of the melee swing (the mandible snap), 0 when not swinging. */
    public float attackTime;
    /** 0 to 1: how far into the charge wind-up pose it is (crouched, wing cases lifted, mandibles open). */
    public float windup;
    /** 0 to 1: how much of the dash pose shows (low and lunging, mandibles wide). */
    public float dash;
}
