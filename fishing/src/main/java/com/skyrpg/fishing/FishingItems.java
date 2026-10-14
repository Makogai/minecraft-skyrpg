package com.skyrpg.fishing;

import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Fishing items:
 * <ul>
 *   <li>Tidescales come up with catches: the Fishing Master's currency. Nine make a Tidescale Plate.</li>
 *   <li>Abyssal Pearls and Leviathan Scales are rare finds, for the best rods and armour.</li>
 *   <li>Baits: use one for ten minutes of faster bites or more sea creatures (one bait at a time).</li>
 *   <li>Lava fishing's catches: Magma Fish and Ember Scales, and the Molten Pearl, its rare find.</li>
 * </ul>
 */
public final class FishingItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final int BAIT_TICKS = 12000;

    public static final Item TIDESCALE = item("tidescale", p -> p.rarity(Rarity.UNCOMMON), Item::new);
    public static final Item TIDESCALE_PLATE = item("tidescale_plate", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item ABYSSAL_PEARL = item("abyssal_pearl", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item LEVIATHAN_SCALE = item("leviathan_scale", p -> p.rarity(Rarity.EPIC), Item::new);

    public static final Item WORM_BAIT = item("worm_bait", p -> p.stacksTo(16),
            p -> new BaitItem(p, "Worm Bait", Map.of("fishing_speed", 20.0), Map.of()));
    public static final Item GLOWWORM_BAIT = item("glowworm_bait", p -> p.stacksTo(16).rarity(Rarity.UNCOMMON),
            p -> new BaitItem(p, "Glowworm Bait", Map.of("fishing_speed", 40.0, "sea_creature_chance", 2.0), Map.of()));
    public static final Item ABYSSAL_BAIT = item("abyssal_bait", p -> p.stacksTo(16).rarity(Rarity.RARE),
            p -> new BaitItem(p, "Abyssal Bait", Map.of("sea_creature_chance", 6.0), Map.of(FishingEffects.DOUBLE_CATCH, 0.1)));

    // Lava fishing (the Magmaline Rod, in the Nether): its catches and its rare find.
    public static final Item MAGMA_FISH = item("magma_fish", p -> p, Item::new);
    public static final Item EMBER_SCALE = item("ember_scale", p -> p.rarity(Rarity.UNCOMMON), Item::new);
    public static final Item MOLTEN_PEARL = item("molten_pearl", p -> p.rarity(Rarity.EPIC), Item::new);

    private FishingItems() {
    }

    /** A bait: a ten-minute fishing boost (core Boosts; a new bait replaces the old one). */
    public static final class BaitItem extends Item {
        private final String name;
        private final Map<String, Double> stats;
        private final Map<Identifier, Double> effects;

        BaitItem(Properties properties, String name, Map<String, Double> stats, Map<Identifier, Double> effects) {
            super(properties);
            this.name = name;
            this.stats = stats;
            this.effects = effects;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                Boosts.give(serverPlayer, "bait", Component.literal(name), stats, effects, BAIT_TICKS);
                Notice.send(serverPlayer, Notice.Kind.FISHING, Msg.text("You bait your hook: ", ChatFormatting.AQUA)
                        .append(Msg.text(name + " for 10 minutes", ChatFormatting.WHITE)));
                Msg.playSound(serverPlayer, SoundEvents.FISHING_BOBBER_SPLASH, 0.6f, 1.4f);
                if (!player.isCreative()) {
                    player.getItemInHand(hand).shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgFishing.id("fishing")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_fishing"))
                        .icon(() -> new ItemStack(TIDESCALE))
                        .displayItems((parameters, output) -> ITEMS.values().forEach(output::accept))
                        .build());
    }

    private static Item item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgFishing.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
        ITEMS.put(name, item);
        return item;
    }
}
