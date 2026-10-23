package com.skyrpg.events.client.billboard;

import com.skyrpg.events.billboard.Billboards;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

/** Client side of billboards: the renderer and the editor opening when the server says so. */
public final class BillboardClient {
    private BillboardClient() {
    }

    public static void init() {
        EntityRendererRegistry.register(Billboards.TYPE, BillboardEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Billboards.Edit.TYPE, (payload, context) -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().gui.setScreen(new BillboardScreen(payload.entity(), payload.json()))));
    }
}
