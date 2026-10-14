package com.skyrpg.items.client.backpack;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.backpack.BackpackPayloads;
import com.skyrpg.items.backpack.Backpacks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Opening backpacks from the client: B opens the first carried backpack, right-clicking a backpack slot in
 * the inventory opens that one (see the inventory screen mixins). The server opens the menu.
 */
public final class BackpackScreens {
    public static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_items.backpack", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, SkyRpgCoreClient.CATEGORY));

    private BackpackScreens() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.gui.screen() == null) {
                    open(-1);
                }
            }
        });
    }

    public static void open(int slot) {
        ClientPlayNetworking.send(new BackpackPayloads.Open(slot));
    }

    /** Right-click on a carried backpack's slot opens it; returns true if the click was used. */
    public static boolean clickSlot(int slot, int button, boolean carrying) {
        Minecraft minecraft = Minecraft.getInstance();
        if (slot < 0 || button != InputConstants.MOUSE_BUTTON_RIGHT || carrying || minecraft.player == null
                || !Backpacks.isBackpack(minecraft.player.inventoryMenu.getSlot(BackpackMenuSlot.FIRST_INDEX + slot).getItem())) {
            return false;
        }
        open(slot);
        return true;
    }

    /** Hides or shows the backpack slots in the player's inventory menu (no drawing, hovering or clicking). */
    public static void setHidden(InventoryMenu menu, boolean hidden) {
        for (int i = BackpackMenuSlot.FIRST_INDEX; i < BackpackMenuSlot.END_INDEX; i++) {
            if (menu.getSlot(i) instanceof BackpackMenuSlot slot) {
                slot.setHidden(hidden);
            }
        }
    }
}
