package com.skyrpg.stats.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.ProfileStat;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.tab.TabPanel;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.stats.PlayerStats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class SkyRpgStatsClient implements ClientModInitializer {
    private static final List<StatType> MAIN_STATS = List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH,
            StatTypes.CRIT_CHANCE, StatTypes.CRIT_DAMAGE, StatTypes.SPEED);

    private static final VitalsWidgets.Health HEALTH_BAR = new VitalsWidgets.Health();
    private static Setting replaceHearts;
    private static Setting hudEnabled;

    @Override
    public void onInitializeClient() {
        HudManager.register(HEALTH_BAR);
        HudManager.register(new VitalsWidgets.Mana());
        HudManager.register(new VitalsWidgets.Defense());
        replaceHearts = SkyRpgSettings.toggle("stats.replace_hearts", SkyRpgSettings.HUD, "Replace hearts with health bar",
                "Hide vanilla's hearts and armor row; the health bar and defense box show them instead", true, null);
        // Hide vanilla's hearts and armor icons only while our health bar is actually on screen.
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (g, deltaTracker) -> {
            if (!replacingHearts()) {
                vanilla.extractRenderState(g, deltaTracker);
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> (g, deltaTracker) -> {
            if (!replacingHearts()) {
                vanilla.extractRenderState(g, deltaTracker);
            }
        });

        SkyRpgMenu.registerPage(new StatsPage());
        int order = 10;
        for (StatType stat : MAIN_STATS) {
            SkyRpgMenu.registerProfileStat(new ProfileStat(stat.name(), stat.symbol(), stat.color(), order++,
                    player -> stat.format(PlayerStats.total(player, stat))));
        }
        SkyRpgTab.registerPanel(new TabPanel("Stats", StatTypes.HEALTH.color(), 20, player -> {
            List<TabPanel.Line> lines = new ArrayList<>();
            for (StatType stat : MAIN_STATS) {
                lines.add(new TabPanel.Line(stat.symbol() + " " + stat.name(), stat.format(PlayerStats.total(player, stat)), stat.color()));
            }
            return lines;
        }));
    }

    private static boolean replacingHearts() {
        return replaceHearts.get() && masterHud() && HudManager.layout(HEALTH_BAR).enabled;
    }

    /** Core's "SkyRPG HUD" switch: with it off, no widgets draw, so vanilla's hearts must come back. */
    private static boolean masterHud() {
        if (hudEnabled == null) {
            hudEnabled = SkyRpgSettings.byCategory().values().stream().flatMap(List::stream)
                    .filter(setting -> setting.id().equals("hud.enabled")).findFirst().orElse(null);
        }
        return hudEnabled == null || hudEnabled.get();
    }
}
