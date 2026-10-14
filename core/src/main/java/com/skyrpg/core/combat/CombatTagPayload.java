package com.skyrpg.core.combat;

import com.skyrpg.core.SkyRpgCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: ticks of combat tag left (0 = not in combat). The client counts down by itself from there. */
public record CombatTagPayload(int ticksLeft) implements CustomPacketPayload {
    public static final Type<CombatTagPayload> TYPE = new Type<>(SkyRpgCore.id("combat_tag"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatTagPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CombatTagPayload::ticksLeft, CombatTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
