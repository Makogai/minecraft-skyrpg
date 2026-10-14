package com.skyrpg.enchants;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arcane enchanting: SkyRPG's own enchants (data in {@code skyrpg/enchants}), stored on items as a component and
 * counted as stats and gear effects; the enchanting screen (Seraphine Vael in the hub, or any enchanting table with
 * bookshelves around it); tomes found in the world, combined and applied; the Enchanting skill and the Codex.
 * See {@code docs/design/enchanting.md}.
 */
public final class SkyRpgEnchants implements ModInitializer {
    public static final String MOD_ID = "skyrpg_enchants";
    public static final Logger LOGGER = LoggerFactory.getLogger("SkyRPG Enchanting");

    @Override
    public void onInitialize() {
        EnchantContent.init();
        Enchants.init();
        EnchantSources.init();
        EnchantEffects.init();
        Codex.init();
        Enchanting.init();
        TomeDrops.init();
        EnchantCommands.init();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
