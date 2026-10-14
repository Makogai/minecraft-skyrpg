package com.skyrpg.world;

import net.minecraft.ChatFormatting;
import com.skyrpg.core.text.Notice;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A Glimmer Geode (a rare drop in the Glimmer Caverns): right-click to crack it open for 3-8 Glimmer Shards and a 10%
 * chance of a Prism Fragment. The mining mod's items are looked up by id, so this works without it (amethyst instead).
 */
final class GlimmerGeodeItem extends Item {
    private static final Identifier GLIMMER = Identifier.fromNamespaceAndPath("skyrpg_mining", "glimmer_shard");
    private static final Identifier PRISM = Identifier.fromNamespaceAndPath("skyrpg_mining", "prism_fragment");
    private static final double PRISM_CHANCE = 0.10;

    GlimmerGeodeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack geode = player.getItemInHand(hand);
        geode.consume(1, player);
        RandomSource random = server.getRandom();
        Item glimmer = BuiltInRegistries.ITEM.getOptional(GLIMMER).orElse(Items.AMETHYST_SHARD);
        give(serverPlayer, new ItemStack(glimmer, 3 + random.nextInt(6)));
        double x = player.getX();
        double y = player.getEyeY() - 0.3;
        double z = player.getZ();
        server.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f + random.nextFloat() * 0.3f);
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 14, 0.4, 0.4, 0.4, 0.05);
        server.sendParticles(ParticleTypes.WAX_OFF, x, y, z, 10, 0.5, 0.5, 0.5, 0.5);
        if (random.nextDouble() < PRISM_CHANCE) {
            Item prism = BuiltInRegistries.ITEM.getOptional(PRISM).orElse(Items.AMETHYST_SHARD);
            ItemStack fragment = new ItemStack(prism);
            serverPlayer.sendSystemMessage(Notice.rareDrop("GEODE FIND!", fragment.getHoverName().copy().withStyle(ChatFormatting.AQUA), Notice.rgb(ChatFormatting.AQUA)));
            server.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
            give(serverPlayer, fragment);
        }
        return InteractionResult.SUCCESS;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }
}
