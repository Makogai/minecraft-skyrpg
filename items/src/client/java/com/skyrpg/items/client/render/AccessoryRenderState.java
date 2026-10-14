package com.skyrpg.items.client.render;

import com.skyrpg.items.accessory.AccessoryLoadout;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * What a player wears in their accessory slots, carried on their render state. Set at the end of render
 * state extraction (the stacks come from the synced attachment), read by the accessory layer.
 */
public final class AccessoryRenderState {
    public static final RenderStateDataKey<AccessoryLoadout> LOADOUT = RenderStateDataKey.create(() -> "skyrpg_items:accessories");

    private AccessoryRenderState() {
    }

    /** The loadout on this state, or EMPTY when none was set (mannequins). */
    public static AccessoryLoadout of(AvatarRenderState state) {
        return state.getDataOrDefault(LOADOUT, AccessoryLoadout.EMPTY);
    }
}
