package com.skyrpg.progress;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Journal: collections and the SkyRPG Level ({@code collections} package), and the questline ({@code quests}). See docs/design/journal.md. */
public class SkyRpgProgress implements ModInitializer {
    public static final String MOD_ID = "skyrpg_progress";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        com.skyrpg.progress.collections.Collections.init();
        com.skyrpg.progress.quests.Quests.init();
        com.skyrpg.progress.titles.TitleSystem.init();
        com.skyrpg.core.records.PlayerRecords.addSource(entries -> entries.add("SkyRPG", "skyrpg_level", "SkyRPG Level",
                com.skyrpg.core.records.PlayerRecords.Format.LEVEL, net.minecraft.world.item.Items.NETHER_STAR,
                com.skyrpg.progress.collections.SkyRpgLevel.level(entries.player())));
    }
}
