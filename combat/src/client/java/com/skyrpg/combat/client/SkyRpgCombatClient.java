package com.skyrpg.combat.client;

import com.skyrpg.combat.client.mob.MobRenderers;
import com.skyrpg.combat.client.slayer.SlayerClient;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import net.fabricmc.api.ClientModInitializer;

/** Combat on the client: the nameplates over levelled mobs (see {@link Nameplates}) and slayers (see {@link SlayerClient}). */
public class SkyRpgCombatClient implements ClientModInitializer {
    static Setting nameplates;

    @Override
    public void onInitializeClient() {
        nameplates = SkyRpgSettings.toggle("combat.nameplates", SkyRpgSettings.HUD, "Mob nameplates",
                "Show level and health over hostile mobs near you", true, null);
        SlayerClient.init();
        MobRenderers.init();
        com.skyrpg.combat.client.mob.DeepMobRenderers.init(); // Sporeling and Fire Beetle models
        com.skyrpg.combat.client.mob.DwarvenMobRenderers.init(); // Dwarven Construct and Iron Thane models
        com.skyrpg.combat.client.mob.EndMobRenderers.init(); // the Ender Wyrm model
        com.skyrpg.combat.client.mob.NetherMobRenderers.init(); // Bone Giant and Magma Tyrant models
    }

    public static boolean nameplatesOn() {
        return nameplates == null || nameplates.get();
    }
}
