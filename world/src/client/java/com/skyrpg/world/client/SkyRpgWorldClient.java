package com.skyrpg.world.client;

import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.map.MapMarkers;
import com.skyrpg.world.Explorer;
import com.skyrpg.world.SkyRpgWorld;
import com.skyrpg.world.WorldContent;
import com.skyrpg.world.WorldPayloads;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The world mod's client side: banners, the waystone menu, and found dungeons and waystones on the maps. */
public class SkyRpgWorldClient implements ClientModInitializer {
    private static final int WAYSTONE_COLOR = 0xFF5CE1E6;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WorldPayloads.Banner.TYPE, (payload, context) -> {
            Item icon = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(payload.icon())).orElse(Items.COMPASS);
            Banners.show(new Banners.Banner(new ItemStack(icon), payload.label(), payload.title(), payload.subtitle(), payload.color()));
        });
        ClientPlayNetworking.registerGlobalReceiver(WorldPayloads.WaystoneMenu.TYPE,
                (payload, context) -> Minecraft.getInstance().gui.setScreen(new WaystoneScreen(payload)));
        RegionMap.init();
        Atmosphere.init();
        HudManager.register(new BloodMoonWidget());
        MapMarkers.register((dimension, out) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            Explorer.State state = Explorer.state(minecraft.player);
            String dim = dimension.toString();
            for (Explorer.Found found : state.dungeons()) {
                if (!found.dimension().equals(dim)) {
                    continue;
                }
                BlockPos pos = found.blockPos();
                Item icon = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(found.icon())).orElse(Items.COMPASS);
                out.accept(new MapMarkers.Marker(SkyRpgWorld.id("dungeon/" + found.pos()), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        new MapMarkers.Item(new ItemStack(icon)), Component.literal(found.name()),
                        List.of(Component.literal("Dungeon").withStyle(ChatFormatting.GRAY)), found.color()));
            }
            for (Explorer.Attuned waystone : state.waystones()) {
                if (!waystone.dimension().equals(dim) || !waystone.placed()) {
                    continue;
                }
                BlockPos pos = waystone.blockPos();
                out.accept(new MapMarkers.Marker(SkyRpgWorld.id("waystone/" + waystone.pos()), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        new MapMarkers.Item(new ItemStack(WorldContent.WAYSTONE_ITEM)), Component.literal(waystone.name()),
                        List.of(Component.literal("Waystone · attuned").withStyle(ChatFormatting.GRAY)), WAYSTONE_COLOR));
            }
        });
    }
}
