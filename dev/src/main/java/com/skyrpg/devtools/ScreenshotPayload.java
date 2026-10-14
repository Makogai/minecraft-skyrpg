package com.skyrpg.devtools;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Asks a dev client to take a screenshot of the game window, optionally after opening a SkyRPG menu
 * page ("skills") or closing any screen ("close"). Lets Claude see UI it builds.
 */
public record ScreenshotPayload(String page) implements CustomPacketPayload {
    public static final Type<ScreenshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SkyRpgDevtools.MOD_ID, "screenshot"));
    public static final StreamCodec<ByteBuf, ScreenshotPayload> CODEC = ByteBufCodecs.STRING_UTF8.map(ScreenshotPayload::new, ScreenshotPayload::page);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
