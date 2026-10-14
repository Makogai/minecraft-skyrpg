package com.skyrpg.core.client.tab;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.player.LocalPlayer;

/**
 * An info panel in the SkyRPG tab list (Profile, Skills, ...). Mods add their own with
 * {@link SkyRpgTab#registerPanel}; {@code lines} is read every frame while Tab is held.
 */
public record TabPanel(String title, int color, int order, Function<LocalPlayer, List<Line>> lines) {

    /** A label on the left and a value on the right, e.g. "Purse ... 1,234". */
    public record Line(String label, String value, int valueColor) {
    }
}
