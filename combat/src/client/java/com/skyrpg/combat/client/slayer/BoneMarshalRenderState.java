package com.skyrpg.combat.client.slayer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link BoneMarshalModel} needs from the Bone Marshal: tier, bow state, shield, banner and swing. */
public class BoneMarshalRenderState extends LivingEntityRenderState {
    /** Slayer tier 1 to 4; decides which armour, banners and bow parts show and which texture is used. */
    public int tier = 1;
    /** Ticks left of the charged greatbow draw (fractional, with the partial tick), 0 when not drawing. */
    public float drawTicks;
    /** Ticks the current charged draw lasts in total (to ramp the pull-back in). */
    public float drawTotal;
    public boolean shielded;
    public boolean bannerPlanted;
    /** The vanilla aiming state of a bow-wielding skeleton (it is shooting at something). */
    public boolean aiming;
    public boolean usingItem;
    /** Ticks the bow has been drawn for a normal shot. */
    public float ticksUsingItem;
    /** Progress of the melee swing, 0 when not swinging. */
    public float attackTime;
}
