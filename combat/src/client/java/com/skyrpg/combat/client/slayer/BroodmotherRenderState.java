package com.skyrpg.combat.client.slayer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link BroodmotherModel} needs from the Broodmother: tier, pounce pose, cocoon, shield and the bite swing. */
public class BroodmotherRenderState extends LivingEntityRenderState {
    /** Slayer tier 1 to 4; decides the texture and which armour, ridge, eggs, silk and crown parts show. */
    public int tier = 1;
    /** Ticks left of the pounce pose (fractional, with the partial tick), 0 when not pouncing. */
    public float pounceTicks;
    /** 0 to 1: how far the silk cocoon has closed (legs fold in as it does). */
    public float cocoon;
    /** 0 to 1: how visible the ring of shield spiderlings is. */
    public float shield;
    /** Progress of the melee swing (the bite), 0 when not swinging. */
    public float attackTime;
}
