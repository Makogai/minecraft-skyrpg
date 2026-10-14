package com.skyrpg.items.client.accessory;

import com.skyrpg.items.accessory.AccessoryMenuSlot;
import com.skyrpg.items.accessory.AccessorySlot;
import com.skyrpg.items.client.backpack.BackpackScreens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The accessory panel in the survival and creative inventory screens. The slots are part of the player's
 * inventory menu ({@link AccessoryMenuSlot}); the mixins in {@code accessory.mixin} draw the panel
 * ({@link AccessoryPanel}), place the slots in the creative inventory tab, hide them while the recipe book
 * covers them and keep clicks on the panel from dropping the carried item.
 */
public final class AccessoryScreens {
    private AccessoryScreens() {
    }

    public static void init() {
        // The client keeps one inventory menu per player, so slots the survival inventory hid while its
        // recipe book was open are shown again when it closes.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof InventoryScreen inventory) {
                ScreenEvents.remove(screen).register(closed -> {
                    setHidden(inventory.getMenu(), false);
                    BackpackScreens.setHidden(inventory.getMenu(), false);
                });
            }
        });
    }

    /** Hides or shows the accessory slots in a player's inventory menu (no drawing, hovering or clicking). */
    public static void setHidden(InventoryMenu menu, boolean hidden) {
        for (AccessorySlot slot : AccessorySlot.values()) {
            if (menu.getSlot(slot.menuIndex()) instanceof AccessoryMenuSlot accessory) {
                accessory.setHidden(hidden);
            }
        }
    }
}
