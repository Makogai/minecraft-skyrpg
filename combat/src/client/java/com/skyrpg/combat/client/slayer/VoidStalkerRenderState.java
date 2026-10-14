package com.skyrpg.combat.client.slayer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link VoidStalkerModel} needs from the Void Stalker: its tier, channel, phase-out, shield and swing. */
public class VoidStalkerRenderState extends LivingEntityRenderState {
    /** Slayer tier 1 to 4: shards, shoulder crystals, second arms, spine, rift cloak, halo and textures grow with it. */
    public int tier = 1;
    /** Ticks left of the big spell (fractional, with the partial tick), 0 when not channelling. */
    public float channelTicks;
    /** Eased 0..1: arms up, head back, shards in a tight ring above the head. */
    public float channel;
    /** Eased 0..1: phased out (dodging); the body flickers and turns translucent. */
    public float phase;
    /** Eased 0..1: null shield up; the shards close into a sphere round the body. */
    public float shield;
    /** Progress of the melee swipe, 0 when not swinging. */
    public float attackTime;
    /** 0..1 opacity of the body, below 1 while phased. */
    public float bodyAlpha = 1f;
}
