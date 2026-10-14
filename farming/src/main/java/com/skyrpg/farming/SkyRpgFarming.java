package com.skyrpg.farming;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.FarmlandBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Farming skill's world: special crops that only grow in the right place, planters and sprinklers, Dew (the
 * Farming Master's currency) and rare finds, Rich Compost, meals, and what farming gear effects do.
 */
public class SkyRpgFarming implements ModInitializer {
    public static final String MOD_ID = "skyrpg_farming";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FarmingBlocks.init();
        FarmingItems.init();
        Harvest.init();
        JsonDataLoader.register(id("bonus_drops"), BonusDrops.DIRECTORY, BonusDrops::load);
        JsonDataLoader.register(id("meals"), Meals.DIRECTORY, Meals::load);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                boolean aura = server.getTickCount() % 40 == 0;
                server.getPlayerList().getPlayers().forEach(player -> tick(player, aura));
            }
        });
    }

    /** Every second: Speed on farmland; every other second: the growth aura. */
    private static void tick(ServerPlayer player, boolean aura) {
        if (FarmingEffects.value(player, FarmingEffects.FIELD_SPEED) > 0) {
            BlockPos feet = player.blockPosition();
            if (player.level().getBlockState(feet).getBlock() instanceof FarmlandBlock
                    || player.level().getBlockState(feet.below()).getBlock() instanceof FarmlandBlock) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
            }
        }
        int radius = (int) FarmingEffects.value(player, FarmingEffects.GROWTH_AURA);
        if (aura && radius > 0) {
            SprinklerBlock.water(player.level(), player.blockPosition(), radius, player.getRandom());
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
