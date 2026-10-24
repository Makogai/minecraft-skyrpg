package com.skyrpg.items.compactor;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * Personal Compactors and Deletors, like Hypixel's: carried anywhere in the main inventory, each set (right-click) to a few
 * materials. A compactor packs every {@value Enchanted#COUNT} of its materials into one enchanted item as they come in; a
 * deletor throws its materials away before they reach the inventory (they still count for collections). Four tiers, 4000 to
 * 7000, differ in how many materials they hold. Several carried tools add up. Only plain stacks are touched, never named or
 * enchanted ones, and a deletor only takes stackable items, so gear is never deleted.
 */
public final class PersonalTools {
    /** A tier: its number in the name, how many materials it holds, and its rarity. */
    public record Tier(int number, int slots, com.skyrpg.items.Rarity rarity) {
    }

    public static final List<Tier> TIERS = List.of(
            new Tier(4000, 3, com.skyrpg.items.Rarity.UNCOMMON),
            new Tier(5000, 5, com.skyrpg.items.Rarity.RARE),
            new Tier(6000, 7, com.skyrpg.items.Rarity.EPIC),
            new Tier(7000, 12, com.skyrpg.items.Rarity.LEGENDARY));

    /** The materials a tool is set to, as item ids separated by commas, in its custom data. */
    private static final String FILTER = "skyrpg_filter";

    private static final List<ToolItem> TOOLS = new ArrayList<>();

    private PersonalTools() {
    }

    /** A Personal Compactor or Deletor. Right-click opens its settings ({@link FilterMenu}). */
    public static final class ToolItem extends Item {
        private final boolean compactor;
        private final Tier tier;

        ToolItem(Properties properties, boolean compactor, Tier tier) {
            super(properties);
            this.compactor = compactor;
            this.tier = tier;
        }

        public boolean compactor() {
            return compactor;
        }

        public Tier tier() {
            return tier;
        }

        /** "Personal Compactor 4000" in its rarity's colour (the item name component is always the translation). */
        @Override
        public Component getName(ItemStack stack) {
            return Component.literal("Personal " + (compactor ? "Compactor " : "Deletor ") + tier.number()).withColor(tier.rarity().color());
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                FilterMenu.open(serverPlayer, hand);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Registers the tools and hooks them into item gains and pickups (mod init). */
    public static void init() {
        for (boolean compactor : new boolean[] {true, false}) {
            for (Tier tier : TIERS) {
                String kind = compactor ? "compactor" : "deletor";
                Identifier id = SkyRpgItems.id("personal_" + kind + "_" + tier.number());
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
                Item.Properties properties = new Item.Properties().setId(key).stacksTo(1)
                        .component(DataComponents.LORE, lore(compactor, tier, List.of()));
                TOOLS.add(Registry.register(BuiltInRegistries.ITEM, key, new ToolItem(properties, compactor, tier)));
            }
        }
        ItemGains.DELETE.register(PersonalTools::deletes);
        ItemGains.MAKE_ROOM.register(PersonalTools::compact);
        ItemGains.GAINED.register((player, stack, source) -> {
            if (Enchanted.of(stack.getItem()) != null) {
                compact(player);
            }
        });
    }

    public static Collection<ToolItem> all() {
        return Collections.unmodifiableList(TOOLS);
    }

    public static boolean isTool(Item item) {
        return item instanceof ToolItem;
    }

    /** The materials this tool is set to (at most its tier's slots). */
    public static List<Item> filter(ItemStack tool) {
        if (!(tool.getItem() instanceof ToolItem item)) {
            return List.of();
        }
        String saved = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(FILTER, "");
        List<Item> items = new ArrayList<>();
        for (String part : saved.split(",")) {
            Identifier id = Identifier.tryParse(part);
            if (!part.isEmpty() && id != null && items.size() < item.tier().slots()) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
            }
        }
        return items;
    }

    /** Saves the tool's materials and rewrites its tooltip. */
    static void setFilter(ItemStack tool, List<Item> items) {
        if (!(tool.getItem() instanceof ToolItem item)) {
            return;
        }
        List<String> ids = items.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).toList();
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(FILTER, String.join(",", ids));
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        tool.set(DataComponents.LORE, lore(item.compactor(), item.tier(), items));
    }

    /** After an upgrade at the Artisan Bench, the new tier keeps the old one's materials. */
    public static void carryOver(ItemStack from, ItemStack to) {
        setFilter(to, filter(from));
    }

    private static ItemLore lore(boolean compactor, Tier tier, List<Item> items) {
        List<Component> lines = new ArrayList<>();
        if (compactor) {
            lines.add(line("Packs every " + Enchanted.COUNT + " of its materials", ChatFormatting.GRAY));
            lines.add(line("into one enchanted item as you gather.", ChatFormatting.GRAY));
        } else {
            lines.add(line("Throws its materials away as you", ChatFormatting.GRAY));
            lines.add(line("gather them. Collections still count.", ChatFormatting.GRAY));
        }
        lines.add(line("Works anywhere in your inventory.", ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());
        lines.add(line("Materials (" + items.size() + "/" + tier.slots() + "):", ChatFormatting.WHITE));
        if (items.isEmpty()) {
            lines.add(line(" none yet", ChatFormatting.DARK_GRAY));
        }
        for (Item item : items) {
            lines.add(line(" " + (compactor ? "▸ " : "✕ ") + item.getName(new ItemStack(item)).getString(),
                    compactor ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        lines.add(Component.empty());
        lines.add(line("Right-click to set it up", ChatFormatting.YELLOW));
        lines.add(line(tier.rarity().name(), null).withColor(tier.rarity().color()).withStyle(ChatFormatting.BOLD));
        return new ItemLore(lines);
    }

    private static MutableComponent line(String text, ChatFormatting color) {
        MutableComponent line = Component.literal(text).withStyle(style -> style.withItalic(false));
        return color == null ? line : line.withStyle(color);
    }

    /** Every material the player's carried tools of one kind are set to. */
    private static Set<Item> materials(ServerPlayer player, boolean compactors) {
        Set<Item> materials = new LinkedHashSet<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ToolItem tool && tool.compactor() == compactors) {
                materials.addAll(filter(stack));
            }
        }
        return materials;
    }

    /** Plain stacks only: a named, enchanted or otherwise changed stack is left alone. */
    private static boolean plain(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    /** True if a carried deletor is set to this item (then it's thrown away instead of picked up). */
    public static boolean deletes(ServerPlayer player, ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1 && plain(stack, stack.getItem())
                && materials(player, false).contains(stack.getItem());
    }

    /** Packs every full {@value Enchanted#COUNT} of the carried compactors' materials into enchanted items. */
    public static void compact(ServerPlayer player) {
        Set<Item> materials = materials(player, true);
        if (materials.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        boolean packed = false;
        for (Item material : materials) {
            Item enchanted = Enchanted.of(material);
            if (enchanted == null) {
                continue;
            }
            int have = 0;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (plain(stack, material)) {
                    have += stack.getCount();
                }
            }
            int made = have / Enchanted.COUNT;
            if (made == 0) {
                continue;
            }
            int left = made * Enchanted.COUNT;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (plain(stack, material)) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
            ItemStack result = new ItemStack(enchanted, made);
            inventory.add(result);
            if (!result.isEmpty()) {
                player.drop(result, false, Prediction.SERVER_ONLY);
            }
            packed = true;
        }
        if (packed) {
            inventory.setChanged();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS,
                    0.15f, 1.6f);
        }
    }
}
