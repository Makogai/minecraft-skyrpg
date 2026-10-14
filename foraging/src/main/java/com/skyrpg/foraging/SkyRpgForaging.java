package com.skyrpg.foraging;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Foraging skill's world: five new woods whose saplings only grow in the right place, stumps that regrow, Heartwood
 * Splinters (the Foraging Master's currency) and rare finds, Treefeller, and what foraging gear effects do.
 */
public class SkyRpgForaging implements ModInitializer {
    public static final String MOD_ID = "skyrpg_foraging";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier SOFT_LANDING_MODIFIER = id("soft_landing");

    @Override
    public void onInitialize() {
        ForagingBlocks.init();
        ForagingItems.init();
        Chopping.init();
        Treefeller.init();
        ForagingWorldgen.init();
        JsonDataLoader.register(id("forage_drops"), ForageDrops.DIRECTORY, ForageDrops::load);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(SkyRpgForaging::tick);
            }
        });
    }

    /** Every second: Speed in the woods, the soft-landing fall distance, star motes. */
    private static void tick(ServerPlayer player) {
        if (ForagingEffects.value(player, ForagingEffects.FOREST_SPEED) > 0) {
            var biome = player.level().getBiome(player.blockPosition());
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
            }
        }
        AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null) {
            double extra = ForagingEffects.value(player, ForagingEffects.SOFT_LANDING);
            AttributeModifier current = safeFall.getModifier(SOFT_LANDING_MODIFIER);
            if (extra <= 0) {
                if (current != null) {
                    safeFall.removeModifier(SOFT_LANDING_MODIFIER);
                }
            } else if (current == null || current.amount() != extra) {
                safeFall.addOrUpdateTransientModifier(new AttributeModifier(SOFT_LANDING_MODIFIER, extra, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (ForagingEffects.value(player, ForagingEffects.STAR_MOTES) > 0) {
            player.level().sendParticles(ParticleTypes.FIREFLY, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.8, 0.6, 0.8, 0.0);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
