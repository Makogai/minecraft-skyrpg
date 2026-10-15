package com.skyrpg.brainrots.client;

import com.skyrpg.brainrots.BrainrotKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link BrainrotModel} needs besides the walk: which brainrot it is. */
public class BrainrotRenderState extends LivingEntityRenderState {
    public BrainrotKind kind = BrainrotKind.TUNG_TUNG_SAHUR;
}
