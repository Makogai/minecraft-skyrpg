package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.EnderWyrmEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What {@link EnderWyrmModel} needs: the head and every segment placed along the trail, and what the Wyrm is doing. */
public class EnderWyrmRenderState extends LivingEntityRenderState {
    /** The head (index 0) and the body segments: offsets from the entity's position and facings (see EnderWyrmEntity.bodyPoints). */
    public EnderWyrmEntity.BodyPoint[] body = new EnderWyrmEntity.BodyPoint[0];
    public EnderWyrmEntity.Mode mode = EnderWyrmEntity.Mode.CIRCLE;
    public int phase = 1;
}
