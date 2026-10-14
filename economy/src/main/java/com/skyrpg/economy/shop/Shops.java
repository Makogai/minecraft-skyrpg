package com.skyrpg.economy.shop;

import com.google.gson.JsonElement;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.economy.Coins;
import com.skyrpg.economy.Purse;
import com.skyrpg.economy.SellPrices;
import com.skyrpg.economy.Sessions;
import com.skyrpg.economy.SkyRpgEconomy;
import com.skyrpg.economy.net.EconomyPayloads;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Every shop from the data files, and the server side of buying, selling and buying back. */
public final class Shops {
    public static final String DIRECTORY = "skyrpg/shops";
    private static final int RED = 0xFF6464;

    /** What a player last sold, per shop, so a misclick can be undone (for the same coins). */
    private record LastSale(Identifier shop, List<ItemStack> stacks, long price) {
    }

    private static Map<Identifier, ShopDefinition> shops = Map.of();
    private static final Map<UUID, LastSale> LAST_SALE = new ConcurrentHashMap<>();
    /** SkyItems.create(Item, int) when the items mod is installed: gives SkyRPG items their name, rarity and stats. */
    private static MethodHandle skyItemsCreate;
    private static boolean lookedUpSkyItems;

    private Shops() {
    }

    public static Map<Identifier, ShopDefinition> all() {
        return shops;
    }

    public static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, ShopDefinition> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, ShopDefinition.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgEconomy.LOGGER.error("Skipping invalid shop {}: {}", id, e.getMessage());
            }
        });
        shops = Map.copyOf(loaded);
        SkyRpgEconomy.LOGGER.info("Loaded {} shops", shops.size());
    }

    public static void forget(UUID player) {
        LAST_SALE.remove(player);
    }

    /** Opens the shop's screen for this player. Returns false if there is no such shop. */
    public static boolean open(ServerPlayer player, Identifier id) {
        ShopDefinition shop = shops.get(id);
        if (shop == null) {
            SkyRpgEconomy.LOGGER.warn("No shop called {}", id);
            return false;
        }
        Sessions.open(player, id);
        ServerPlayNetworking.send(player, view(player, shop, false));
        return true;
    }

    private static EconomyPayloads.Shop view(ServerPlayer player, ShopDefinition shop, boolean refresh) {
        List<EconomyPayloads.Offer> offers = new ArrayList<>();
        for (ShopDefinition.Offer offer : shop.offers()) {
            List<ItemStack> costs = offer.costs().stream().map(cost -> new ItemStack(cost.item(), cost.count())).toList();
            offers.add(new EconomyPayloads.Offer(stack(offer.item(), offer.count()), offer.price(), costs,
                    Optional.ofNullable(offer.skill()), offer.level()));
        }
        Map<Identifier, Long> prices = new LinkedHashMap<>();
        if (shop.buysSomething()) {
            SellPrices.all().forEach((item, price) -> {
                if (price > 0 && shop.buys(item)) {
                    prices.put(BuiltInRegistries.ITEM.getKey(item), price);
                }
            });
        }
        LastSale sale = LAST_SALE.get(player.getUUID());
        boolean hasSale = sale != null && sale.shop().equals(shop.id());
        EconomyPayloads.Selling selling = new EconomyPayloads.Selling(shop.buysSomething(), shop.buysHint(), prices,
                hasSale ? sale.stacks() : List.of(), hasSale ? sale.price() : 0);
        return new EconomyPayloads.Shop(shop.id(), shop.title(), shop.keeper(), shop.accent(), new ItemStack(shop.icon()), offers, selling, refresh);
    }

    public static void buy(ServerPlayer player, EconomyPayloads.Buy request) {
        ShopDefinition shop = checkedShop(player, request.shop());
        if (shop == null) {
            return;
        }
        if (request.offer() < 0 || request.offer() >= shop.offers().size()) {
            return;
        }
        ShopDefinition.Offer offer = shop.offers().get(request.offer());
        ItemStack like = stack(offer.item(), offer.count());
        int maxUnits = maxUnits(like.getMaxStackSize(), offer.count());
        int wanted = Math.clamp(request.units(), 1, maxUnits);
        int fits = Inventories.room(player.getInventory(), like) / offer.count();
        if (fits <= 0) {
            fail(player, Component.literal("Your inventory is full.").withColor(RED));
            return;
        }
        if (!offer.unlocked(player)) {
            fail(player, Component.literal("Requires " + SkillLevels.displayName(offer.skill()) + " " + offer.level() + ".").withColor(RED));
            return;
        }
        long purse = Purse.get(player);
        long affordable = offer.price() > 0 ? purse / offer.price() : Long.MAX_VALUE;
        if (affordable <= 0) {
            fail(player, Component.literal("You need ").withColor(RED).append(Coins.text(offer.price() - purse))
                    .append(Component.literal(" more for that.").withColor(RED)));
            return;
        }
        for (ShopDefinition.Cost cost : offer.costs()) {
            int have = Inventories.count(player.getInventory(), cost.item());
            affordable = Math.min(affordable, have / cost.count());
            if (affordable <= 0) {
                fail(player, Component.literal("You need ").withColor(RED).append(itemLabel(new ItemStack(cost.item()), cost.count() - have))
                        .append(Component.literal(" more for that.").withColor(RED)));
                return;
            }
        }
        // Buy as many as asked for, or as many as fit and you can pay for.
        int units = (int) Math.min(wanted, Math.min(fits, affordable));
        long total = offer.price() * units;
        if (total > 0 && !Purse.trySpend(player, total)) {
            return;
        }
        for (ShopDefinition.Cost cost : offer.costs()) {
            Inventories.remove(player.getInventory(), cost.item(), cost.count() * units);
        }
        int items = offer.count() * units;
        Inventories.give(player, like, items);

        MutableComponent line = Component.literal("Bought ").withStyle(ChatFormatting.GREEN).append(itemLabel(like, items))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GREEN));
        boolean first = true;
        if (total > 0) {
            line.append(Coins.text(total));
            first = false;
        }
        for (ShopDefinition.Cost cost : offer.costs()) {
            if (!first) {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GREEN));
            }
            line.append(itemLabel(new ItemStack(cost.item()), cost.count() * units));
            first = false;
        }
        if (units < wanted) {
            line.append(Component.literal(fits < wanted ? "  (inventory full)" : "  (all you could afford)").withStyle(ChatFormatting.GRAY));
        }
        Notice.of(Notice.Kind.SHOP).tag("BOUGHT").send(player, line);
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(line, true));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.6f);
        Msg.playSound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.5f, 1.4f);
    }

    public static void sell(ServerPlayer player, EconomyPayloads.Sell request) {
        ShopDefinition shop = checkedShop(player, request.shop());
        if (shop == null || !shop.buysSomething()) {
            return;
        }
        Optional<Item> only = request.item().map(BuiltInRegistries.ITEM::getValue);
        Inventory inventory = player.getInventory();
        List<ItemStack> sold = new ArrayList<>();
        long total = 0;
        int count = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || (only.isPresent() && stack.getItem() != only.get()) || !shop.buys(stack.getItem())) {
                continue;
            }
            long price = SellPrices.priceOf(stack);
            if (price <= 0) {
                continue;
            }
            sold.add(stack.copy());
            total += price;
            count += stack.getCount();
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (sold.isEmpty()) {
            fail(player, Component.literal(only.isPresent() ? "You don't have any of that." : "You have nothing " + firstName(shop) + " wants.")
                    .withColor(RED));
            return;
        }
        Purse.add(player, total);
        LAST_SALE.put(player.getUUID(), new LastSale(shop.id(), List.copyOf(sold), total));

        boolean single = sold.stream().allMatch(stack -> stack.getItem() == sold.getFirst().getItem());
        MutableComponent what = single ? itemLabel(sold.getFirst(), count) : Component.literal(Msg.number(count) + " items").withStyle(ChatFormatting.WHITE);
        MutableComponent line = Component.literal("Sold ").withStyle(ChatFormatting.GREEN).append(what)
                .append(Component.literal(" for ").withStyle(ChatFormatting.GREEN)).append(Coins.text(total));
        Notice.of(Notice.Kind.SHOP).tag("SOLD").send(player, line);
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(line, true));
        ServerPlayNetworking.send(player, view(player, shop, true));
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Msg.playSound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.6f, 1.2f);
    }

    public static void buyback(ServerPlayer player, EconomyPayloads.Buyback request) {
        ShopDefinition shop = checkedShop(player, request.shop());
        LastSale sale = LAST_SALE.get(player.getUUID());
        if (shop == null || sale == null || !sale.shop().equals(shop.id())) {
            return;
        }
        if (!Inventories.fits(player.getInventory(), sale.stacks())) {
            fail(player, Component.literal("Make room in your inventory first.").withColor(RED));
            return;
        }
        if (!Purse.trySpend(player, sale.price())) {
            fail(player, Component.literal("You need ").withColor(RED).append(Coins.text(sale.price() - Purse.get(player)))
                    .append(Component.literal(" more to buy that back.").withColor(RED)));
            return;
        }
        sale.stacks().forEach(stack -> Inventories.give(player, stack));
        LAST_SALE.remove(player.getUUID());
        MutableComponent line = Component.literal("Bought back your last sale for ").withStyle(ChatFormatting.GREEN).append(Coins.text(sale.price()));
        Notice.of(Notice.Kind.SHOP).tag("BUYBACK").send(player, line);
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(line, true));
        ServerPlayNetworking.send(player, view(player, shop, true));
        Msg.playSound(player, SoundEvents.ITEM_PICKUP, 0.6f, 1.0f);
    }

    /** At most a full inventory's worth per purchase. */
    public static int maxUnits(int maxStackSize, int count) {
        return Math.max(1, Inventory.INVENTORY_SIZE * maxStackSize / Math.max(1, count));
    }

    private static ShopDefinition checkedShop(ServerPlayer player, Identifier id) {
        ShopDefinition shop = shops.get(id);
        if (shop == null) {
            return null;
        }
        if (!Sessions.isAt(player, id)) {
            fail(player, Component.literal("Walk back to " + firstName(shop) + " to trade.").withColor(RED));
            return null;
        }
        return shop;
    }

    private static String firstName(ShopDefinition shop) {
        return shop.keeper().isEmpty() ? "the shopkeeper" : shop.keeper().split(" ")[0];
    }

    private static MutableComponent itemLabel(ItemStack stack, int count) {
        MutableComponent label = stack.getHoverName().copy();
        return count > 1 ? label.append(Component.literal(" x" + Msg.number(count)).withStyle(ChatFormatting.GRAY)) : label;
    }

    private static void fail(ServerPlayer player, Component text) {
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(text, false));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
    }

    /** A fresh stack, made by the items mod when it's there so SkyRPG items come with their name and stats. */
    static ItemStack stack(Item item, int count) {
        if (!lookedUpSkyItems) {
            lookedUpSkyItems = true;
            try {
                Class<?> skyItems = Class.forName("com.skyrpg.items.SkyItems");
                skyItemsCreate = MethodHandles.publicLookup().findStatic(skyItems, "create", MethodType.methodType(ItemStack.class, Item.class, int.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                skyItemsCreate = null;
            }
        }
        if (skyItemsCreate != null) {
            try {
                ItemStack stack = (ItemStack) skyItemsCreate.invokeExact(item, count);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Throwable e) {
                SkyRpgEconomy.LOGGER.warn("Couldn't make a {} through the items mod: {}", BuiltInRegistries.ITEM.getKey(item), e.toString());
            }
        }
        return new ItemStack(item, count);
    }
}
