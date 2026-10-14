package com.skyrpg.items.mixin;

import com.skyrpg.items.backpack.BackpackMenuSlot;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * In the creative inventory, the client sends every slot change as a "set creative slot" packet, and the
 * server only takes slots 1..45 (vanilla's inventory menu). Without this, accessories put on in creative
 * would be gone on the next sync; the backpack slots after them (50..52) too.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @ModifyConstant(method = "handleSetCreativeModeSlot", constant = @Constant(intValue = 45))
    private int skyrpg$acceptAccessorySlots(int lastSlot) {
        return BackpackMenuSlot.END_INDEX - 1;
    }
}
