package com.skyrpg.crates;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Notice;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * The crate items: the Hallow's Crate and its key (right-click either for the crate screen), the candy it can give
 * (eaten for a short boost, see {@link Candy}) and the Pumpkin Pouch (right-click for the coins inside).
 */
public final class CrateItems {
    static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static final Item HALLOWS_CRATE = item("hallows_crate", p -> p.stacksTo(64).rarity(Rarity.EPIC), CrateItem::new);
    public static final Item HALLOWS_KEY = item("hallows_key", p -> p.stacksTo(64).rarity(Rarity.RARE), CrateItem::new);
    public static final Item CANDY_CORN = candy("candy_corn", 2, 0.3f);
    public static final Item TOFFEE_APPLE = candy("toffee_apple", 5, 0.6f);
    public static final Item PUMPKIN_POUCH = item("pumpkin_pouch", p -> p.stacksTo(64).rarity(Rarity.UNCOMMON), PouchItem::new);

    private CrateItems() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgCrates.id("crates")),
                FabricCreativeModeTab.builder()
                        .title(Component.literal("SkyRPG Crates"))
                        .icon(() -> new ItemStack(HALLOWS_CRATE))
                        .displayItems((parameters, output) -> {
                            ITEMS.values().forEach(output::accept);
                            output.accept(pouch(1000));
                        })
                        .build());
    }

    /** A Pumpkin Pouch holding this many coins. */
    public static ItemStack pouch(long coins) {
        ItemStack stack = new ItemStack(PUMPKIN_POUCH);
        CompoundTag tag = new CompoundTag();
        tag.putLong("Coins", coins);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(String.format("%,d coins inside", coins)).withColor(0xFFC940)
                .withStyle(style -> style.withItalic(false)))));
        return stack;
    }

    private static Item candy(String name, int nutrition, float saturation) {
        return item(name, p -> p.stacksTo(64).rarity(Rarity.UNCOMMON)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().build()), Candy.CandyItem::new);
    }

    private static Item item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCrates.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
        ITEMS.put(name, item);
        return item;
    }

    /** The crate, or its key: right-click shows the crate's contents (and opens it from there). */
    static final class CrateItem extends Item {
        CrateItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                Crate crate = Crates.byItem(player.getItemInHand(hand).getItem());
                if (crate != null) {
                    Crates.showMenu(serverPlayer, crate);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Right-click: the coins inside go to your purse. */
    static final class PouchItem extends Item {
        PouchItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);
                CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                long coins = data == null ? 0 : data.copyTag().getLongOr("Coins", 0);
                if (coins > 0) {
                    stack.consume(1, player);
                    Wallet.give(serverPlayer, coins);
                    Notice.send(serverPlayer, Notice.Kind.COINS, Component.literal("The pouch held ")
                            .append(Component.literal(String.format("%,d coins", coins)).withColor(0xFFC940)).append("!"));
                    level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
                    level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }
}
