package com.skyrpg.core.client.menu;

import java.util.function.Function;
import net.minecraft.client.player.LocalPlayer;

/**
 * A tile on the Profile page, e.g. Purse or Skill Average. Mods add their own with
 * {@link SkyRpgMenu#registerProfileStat}; {@code value} is read every frame.
 *
 * @param symbol a single character drawn in {@code color}, Hypixel style (❤ ❈ ❁ ✦ ...)
 */
public record ProfileStat(String label, String symbol, int color, int order, Function<LocalPlayer, String> value) {
}
