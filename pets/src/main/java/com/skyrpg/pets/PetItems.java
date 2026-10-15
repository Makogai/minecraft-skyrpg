package com.skyrpg.pets;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Pet items (one per kind; rarity and XP live in the stack) and the eggs they hatch from: an egg of each kind (a random
 * rarity, mostly Common), and the Golden Pet Egg (a random kind, Rare or better).
 */
public final class PetItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<PetType, Item> PETS = new EnumMap<>(PetType.class);
    private static final Map<PetType, Item> EGGS = new EnumMap<>(PetType.class);

    public static final Item GOLDEN_EGG;

    static {
        for (PetType type : PetType.values()) {
            PETS.put(type, item(type.id() + "_pet", p -> p.stacksTo(1).rarity(Rarity.UNCOMMON), p -> new PetItem(p, type)));
        }
        for (PetType type : PetType.values()) {
            if (type.hatches()) {
                EGGS.put(type, item(type.id() + "_egg", p -> p.stacksTo(16).rarity(Rarity.UNCOMMON), p -> new EggItem(p, type)));
            }
        }
        GOLDEN_EGG = item("golden_pet_egg", p -> p.stacksTo(16).rarity(Rarity.EPIC), p -> new EggItem(p, null));
    }

    private PetItems() {
    }

    public static Item pet(PetType type) {
        return PETS.get(type);
    }

    public static Item egg(PetType type) {
        return EGGS.get(type);
    }

    /** A pet: right-click to move it into your pet storage (and equip it when no pet is out). */
    public static final class PetItem extends Item {
        private final PetType type;

        PetItem(Properties properties, PetType type) {
            super(properties);
            this.type = type;
        }

        public PetType type() {
            return type;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);
                if (Pets.claim(serverPlayer, stack)) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** An egg: right-click to hatch it. */
    public static final class EggItem extends Item {
        private final PetType type;

        EggItem(Properties properties, PetType type) {
            super(properties);
            this.type = type;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (Pets.storageFull(serverPlayer)) {
                Notice.send(serverPlayer, Notice.Kind.PET, Pets.grey("Your pet storage is full (" + Pets.maxStorage()
                        + "). Take a pet out in the K menu before hatching this egg."));
                return InteractionResult.SUCCESS;
            }
            RandomSource random = serverPlayer.getRandom();
            PetType[] hatchable = java.util.Arrays.stream(PetType.values()).filter(PetType::hatches).toArray(PetType[]::new);
            PetType hatched = type != null ? type : hatchable[random.nextInt(hatchable.length)];
            PetRarity rarity = roll(random, type == null);
            ItemStack pet = Pets.create(hatched, rarity);
            ItemStack egg = player.getItemInHand(hand);
            egg.consume(1, player);
            MutableComponent hatchBody = Pets.grey("A ").append(Pets.coloredName(pet)).append(Pets.grey(" hatched and went into your pet storage!"));
            if (rarity.ordinal() >= PetRarity.EPIC.ordinal()) {
                hatchBody.append(Component.literal(rarity == PetRarity.LEGENDARY ? "  What a find!" : "  Lucky!")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
            }
            Pets.notice(rarity).tag("EGG HATCHED").send(serverPlayer, hatchBody);
            Msg.playSound(serverPlayer, SoundEvents.SNIFFER_EGG_HATCH, 1.0f, 1.0f);
            Pets.store(serverPlayer, pet, false);
            return InteractionResult.SUCCESS;
        }

        private static PetRarity roll(RandomSource random, boolean golden) {
            List<PetRarity> pool = new ArrayList<>();
            for (PetRarity rarity : PetRarity.values()) {
                if (!golden || rarity.ordinal() >= PetRarity.RARE.ordinal()) {
                    pool.add(rarity);
                }
            }
            int total = pool.stream().mapToInt(r -> golden ? r.weight() * 4 : r.weight()).sum();
            int pick = random.nextInt(total);
            for (PetRarity rarity : pool) {
                pick -= golden ? rarity.weight() * 4 : rarity.weight();
                if (pick < 0) {
                    return rarity;
                }
            }
            return PetRarity.COMMON;
        }
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgPets.id("pets")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_pets"))
                        .icon(() -> new ItemStack(GOLDEN_EGG))
                        .displayItems((parameters, output) -> {
                            EGGS.values().forEach(output::accept);
                            output.accept(GOLDEN_EGG);
                            for (PetType type : PetType.values()) {
                                output.accept(Pets.create(type, PetRarity.LEGENDARY));
                            }
                        })
                        .build());
    }

    private static Item item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> factory) {
        ResourceKey<net.minecraft.world.item.Item> key = ResourceKey.create(Registries.ITEM, SkyRpgPets.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
        ITEMS.put(name, item);
        return item;
    }
}
