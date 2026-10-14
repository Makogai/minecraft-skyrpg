package com.skyrpg.combat.client.slayer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link GraveTyrantModel} needs from the Grave Tyrant: its tier, slam wind-up, enrage and swing. */
public class GraveTyrantRenderState extends LivingEntityRenderState {
    /** Slayer tier 1 to 4; each tier has its own texture and reveals more parts (see GraveTyrantModel). */
    public int tier = 1;
    /** Ticks left of the Grave Slam pose (fractional, with the partial tick), 0 when not slamming. */
    public float slamTicks;
    public boolean enraged;
    /** Progress of the melee swing, 0 when not swinging. */
    public float attackTime;
}
