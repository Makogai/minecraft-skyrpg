package com.skyrpg.mounts;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/** The Griffin Whistle: one item whose stack holds a tamed griffin (see {@link Mounts}); and the mod's creative tab. */
public final class MountItems {
    public static final Item GRIFFIN_WHISTLE;

    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgMounts.id("griffin_whistle"));
        GRIFFIN_WHISTLE = Registry.register(BuiltInRegistries.ITEM, key, new WhistleItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
    }

    private MountItems() {
    }

    /** Right-click: the griffin goes into your stable and is called if no mount is out. */
    public static final class WhistleItem extends Item {
        WhistleItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);
                if (Mounts.claim(serverPlayer, stack)) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgMounts.id("mounts")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_mounts"))
                        .icon(() -> Mounts.create(GriffinColor.GOLDEN, "Aerie", 5, 10, null, ""))
                        .displayItems((parameters, output) -> {
                            RandomSource random = RandomSource.create(7);
                            for (GriffinColor color : GriffinColor.values()) {
                                output.accept(Mounts.create(color, random, null, ""));
                            }
                            output.accept(MountBlocks.GRIFFIN_NEST_ITEM);
                        })
                        .build());
    }
}
