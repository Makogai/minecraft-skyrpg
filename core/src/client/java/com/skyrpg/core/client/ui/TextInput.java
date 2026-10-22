package com.skyrpg.core.client.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Since 26.x the game only receives typed characters while "text input" is on (it's how IMEs are supported), and
 * vanilla only switches it on while one of its own EditBoxes has focus. SkyRPG's screens draw their own text fields
 * (wiki search, settings search, pay amounts, bench search...), so without this, keys like Backspace worked but typed
 * letters never arrived. Text input stays on while any SkyRPG screen is open.
 */
public final class TextInput {
    private static final String OURS = "com.skyrpg.";

    private TextInput() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen.getClass().getName().startsWith(OURS)) {
                minecraft.textInputManager().startTextInput(screen);
                ScreenEvents.remove(screen).register(TextInput::stop);
            }
        });
    }

    private static void stop(Screen screen) {
        Minecraft.getInstance().textInputManager().stopTextInput(screen);
    }
}
