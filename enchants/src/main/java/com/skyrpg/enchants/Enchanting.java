package com.skyrpg.enchants;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.items.MaterialStore;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The enchanting screen's server side. It opens at Seraphine Vael (action {@code open_enchanting}; her library counts as
 * fifteen bookshelves) or at any enchanting table (the bookshelves around it decide the highest levels it offers, as
 * in vanilla). Every request is checked again here: the session (still near the table or where you opened it), the
 * item in the named slot, levels, Enchanting level, bookshelves and costs. Then it pays, enchants, gives Enchanting XP,
 * fills the Codex and sends a fresh view with a flash for the screen's animation.
 */
public final class Enchanting {
    /** Bookshelves Seraphine's library counts as. */
    public static final int FULL_POWER = 15;
    private static final double TABLE_REACH = 8;
    private static final double NPC_REACH = 12;

    private record Session(ResourceKey<Level> dimension, Vec3 at, BlockPos table) {
    }

    record Result(boolean ok, Component notice) {
        static Result fail(String text) {
            return new Result(false, Component.literal(text));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Enchanting() {
    }

    static void init() {
        EnchantPayloads.register();
        Actions.register("open_enchanting", (player, params) -> open(player, null));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)
                    || (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty())) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) {
                open(server, hit.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(EnchantPayloads.EnchantItem.TYPE, (payload, context) ->
                answer(context.player(), enchant(context.player(), payload.slot(), Identifier.tryParse(payload.enchant()), payload.level(),
                        payload.replace()), EnchantPayloads.FLASH_ENCHANT));
        ServerPlayNetworking.registerGlobalReceiver(EnchantPayloads.ApplyTome.TYPE, (payload, context) ->
                answer(context.player(), applyTome(context.player(), payload.tomeSlot(), payload.itemSlot(), payload.replace()),
                        EnchantPayloads.FLASH_TOME));
        ServerPlayNetworking.registerGlobalReceiver(EnchantPayloads.Combine.TYPE, (payload, context) ->
                answer(context.player(), combine(context.player(), payload.first(), payload.second()), EnchantPayloads.FLASH_COMBINE));
        ServerPlayNetworking.registerGlobalReceiver(EnchantPayloads.Salvage.TYPE, (payload, context) ->
                answer(context.player(), salvage(context.player(), payload.slot()), EnchantPayloads.FLASH_SALVAGE));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
    }

    // ---------------------------------------------------------------- sessions and views

    /** Opens the screen: at a table ({@code table} is its position) or at Seraphine ({@code null}). */
    public static void open(ServerPlayer player, BlockPos table) {
        SESSIONS.put(player.getUUID(), new Session(player.level().dimension(), table == null ? player.position() : Vec3.atCenterOf(table), table));
        Msg.playSound(player, SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.9f);
        ServerPlayNetworking.send(player, view(player, Component.empty(), true, false, EnchantPayloads.FLASH_NONE, 0));
    }

    private static Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !player.level().dimension().equals(session.dimension())
                || player.position().distanceTo(session.at()) > (session.table() == null ? NPC_REACH : TABLE_REACH)) {
            return null;
        }
        if (session.table() != null && !player.level().getBlockState(session.table()).is(Blocks.ENCHANTING_TABLE)) {
            return null;
        }
        return session;
    }

    /** Bookshelves around the session's table (15 at Seraphine's). */
    private static int power(ServerPlayer player, Session session) {
        if (session.table() == null) {
            return FULL_POWER;
        }
        int shelves = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(player.level(), session.table(), offset)) {
                shelves++;
            }
        }
        return Math.min(FULL_POWER, shelves);
    }

    private static EnchantPayloads.View view(ServerPlayer player, Component notice, boolean good, boolean refresh, int flash, int tab) {
        Session session = SESSIONS.get(player.getUUID());
        int power = session == null || session.table() == null ? -1 : power(player, session);
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (Item item : materials()) {
            materials.put(BuiltInRegistries.ITEM.getKey(item).toString(), MaterialStore.count(player, item));
        }
        return new EnchantPayloads.View(power, Wallet.balance(player), materials, notice, good, refresh, flash, tab);
    }

    /** Every material any cost can ask for: table costs (rules and enchants' own) and Runic Dust. */
    private static Set<Item> materials() {
        Set<Item> items = new LinkedHashSet<>();
        items.add(EnchantContent.RUNIC_DUST);
        List<Enchant.LevelCost> costs = new ArrayList<>();
        for (EnchantRarity rarity : EnchantRarity.values()) {
            for (int level = 1; level <= 5; level++) {
                costs.add(Enchants.rules().tableCost(rarity, level));
            }
        }
        Enchants.all().forEach(def -> costs.addAll(def.costs()));
        for (Enchant.LevelCost cost : costs) {
            cost.materials().keySet().forEach(id -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add));
        }
        return items;
    }

    /** Switches an open screen to a tab (0 enchant, 1 tomes, 2 codex). */
    static void showTab(ServerPlayer player, int tab) {
        ServerPlayNetworking.send(player, view(player, Component.empty(), true, true, EnchantPayloads.FLASH_NONE, tab));
    }

    static void answer(ServerPlayer player, Result result, int flash) {
        if (ServerPlayNetworking.canSend(player, EnchantPayloads.View.TYPE)) {
            ServerPlayNetworking.send(player, view(player, result.notice(), result.ok(), true, result.ok() ? flash : EnchantPayloads.FLASH_NONE, -1));
        }
    }

    // ---------------------------------------------------------------- costs

    private record Cost(long coins, int xpLevels, Map<Item, Integer> materials) {
        static Cost of(long coins, int xpLevels) {
            return new Cost(coins, xpLevels, Map.of());
        }

        Cost withMaterial(Item item, int count) {
            if (count <= 0) {
                return this;
            }
            Map<Item, Integer> next = new LinkedHashMap<>(materials);
            next.merge(item, count, Integer::sum);
            return new Cost(coins, xpLevels, next);
        }
    }

    private static Cost tableCost(Enchant def, int level) {
        Enchant.LevelCost cost = def.cost(level);
        Cost out = Cost.of(cost.coins(), cost.xp());
        for (Map.Entry<Identifier, Integer> material : cost.materials().entrySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(material.getKey()).orElse(null);
            if (item != null) {
                out = out.withMaterial(item, material.getValue());
            }
        }
        return out;
    }

    /** What's missing to pay this, or null if it's affordable. */
    private static String missing(ServerPlayer player, Cost cost) {
        if (Wallet.balance(player) < cost.coins()) {
            return "You need " + Msg.number(cost.coins()) + " coins";
        }
        if (!player.isCreative() && player.experienceLevel < cost.xpLevels()) {
            return "You need " + cost.xpLevels() + " experience levels";
        }
        for (Map.Entry<Item, Integer> material : cost.materials().entrySet()) {
            if (MaterialStore.count(player, material.getKey()) < material.getValue()) {
                return "You need " + material.getValue() + " " + material.getKey().getName(new ItemStack(material.getKey())).getString();
            }
        }
        return null;
    }

    private static boolean pay(ServerPlayer player, Cost cost) {
        if (missing(player, cost) != null || !Wallet.take(player, cost.coins())) {
            return false;
        }
        if (!player.isCreative() && cost.xpLevels() > 0) {
            player.giveExperienceLevels(-cost.xpLevels());
        }
        cost.materials().forEach((item, count) -> MaterialStore.take(player, item, count));
        return true;
    }

    private static void giveXp(ServerPlayer player, double xp) {
        if (xp > 0) {
            SkillLevels.giveXp(player, Codex.ENCHANTING, xp);
        }
    }

    // ---------------------------------------------------------------- the table

    private static ItemStack slot(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        return slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
    }

    /** Enchants on the item that this one would replace (conflicts, or another ultimate). */
    private static List<Identifier> replaced(Enchant def, EnchantList current) {
        List<Identifier> out = new ArrayList<>();
        for (Identifier other : current.levels().keySet()) {
            Enchant otherDef = Enchants.get(other);
            if (def.conflictsWith(other) || (def.ultimate() && otherDef != null && otherDef.ultimate() && !other.equals(def.id()))) {
                out.add(other);
            }
        }
        return out;
    }

    static Result enchant(ServerPlayer player, int slot, Identifier id, int level, boolean replace) {
        Session session = session(player);
        if (session == null) {
            return Result.fail("Move back to the table");
        }
        Enchant def = id == null ? null : Enchants.get(id);
        ItemStack stack = slot(player, slot);
        if (def == null || !def.tableable()) {
            return Result.fail("That enchant isn't offered at the table");
        }
        if (stack.isEmpty() || EnchantContent.isTome(stack) || !def.appliesTo(stack)) {
            return Result.fail(def.name() + " doesn't go on that");
        }
        if (level < 1 || level > def.tableMax()) {
            return Result.fail("The table offers " + def.name() + " up to " + Enchant.roman(def.tableMax()));
        }
        EnchantList current = EnchantContent.enchants(stack);
        if (current.level(id) >= level) {
            return Result.fail("It already has " + def.title(current.level(id)));
        }
        int shelves = Enchants.rules().shelves(level);
        if (power(player, session) < shelves) {
            return Result.fail(def.title(level) + " needs " + shelves + " bookshelves around the table");
        }
        int requires = def.cost(level).requires();
        if (SkillLevels.level(player, Codex.ENCHANTING) < requires) {
            return Result.fail(def.title(level) + " needs Enchanting " + requires);
        }
        List<Identifier> replaced = replaced(def, current);
        if (!replaced.isEmpty() && !replace) {
            return Result.fail("That would replace " + names(replaced, current));
        }
        Cost cost = tableCost(def, level);
        String missing = missing(player, cost);
        if (missing != null) {
            return Result.fail(missing);
        }
        if (!pay(player, cost)) {
            return Result.fail("You can't pay for that");
        }
        EnchantList next = current;
        for (Identifier other : replaced) {
            next = next.without(other);
        }
        EnchantContent.setEnchants(stack, next.with(id, level));
        player.getInventory().setChanged();
        giveXp(player, cost.xpLevels() * Enchants.rules().skillXp().perXpLevel() + cost.coins() / 1000.0 * Enchants.rules().skillXp().per1000Coins());
        Codex.discover(player, id);
        celebrate(player, session, def);
        Stats.refresh(player);
        return new Result(true, Component.literal("Bound ").append(title(def, level)).append(Component.literal(" to ").append(stack.getHoverName())));
    }

    private static MutableComponent title(Enchant def, int level) {
        return Component.literal(def.title(level)).withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(def.ultimate()));
    }

    private static String names(List<Identifier> ids, EnchantList current) {
        List<String> names = new ArrayList<>();
        for (Identifier id : ids) {
            Enchant def = Enchants.get(id);
            names.add(def == null ? id.getPath() : def.title(current.level(id)));
        }
        return String.join(", ", names);
    }

    /** Sounds and sparks: from the bookshelves into the table, or around the player at Seraphine's. */
    private static void celebrate(ServerPlayer player, Session session, Enchant def) {
        ServerLevel level = player.level();
        Vec3 at = session.table() == null ? player.position().add(0, 1.2, 0) : Vec3.atCenterOf(session.table()).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.6, at.z, 70, 0.9, 0.6, 0.9, 1.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (def.rarity().ordinal() >= EnchantRarity.EPIC.ordinal()) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.3);
        }
    }

    // ---------------------------------------------------------------- tomes

    static Result applyTome(ServerPlayer player, int tomeSlot, int itemSlot, boolean replace) {
        Session session = session(player);
        if (session == null) {
            return Result.fail("Move back to the table");
        }
        ItemStack tome = slot(player, tomeSlot);
        ItemStack stack = slot(player, itemSlot);
        if (!EnchantContent.isTome(tome) || tomeSlot == itemSlot) {
            return Result.fail("That isn't a tome");
        }
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(tome).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        if (def == null) {
            return Result.fail("This tome's magic has faded");
        }
        if (stack.isEmpty() || EnchantContent.isTome(stack) || !def.appliesTo(stack)) {
            return Result.fail(def.name() + " doesn't go on that");
        }
        EnchantList current = EnchantContent.enchants(stack);
        int result = resultLevel(def, current.level(def.id()), inside.getValue());
        if (result <= current.level(def.id())) {
            return Result.fail("It already has " + def.title(current.level(def.id())) + "; a stronger tome would raise it");
        }
        List<Identifier> replaced = replaced(def, current);
        if (!replaced.isEmpty() && !replace) {
            return Result.fail("That would replace " + names(replaced, current));
        }
        Cost cost = Cost.of(Enchants.rules().tomeCoins(def.rarity(), result), Enchants.rules().tomeXp(result));
        String missing = missing(player, cost);
        if (missing != null) {
            return Result.fail(missing);
        }
        if (!pay(player, cost)) {
            return Result.fail("You can't pay for that");
        }
        EnchantList next = current;
        for (Identifier other : replaced) {
            next = next.without(other);
        }
        EnchantContent.setEnchants(stack, next.with(def.id(), result));
        tome.shrink(1);
        player.getInventory().setChanged();
        giveXp(player, Enchants.rules().skillXp().tomeApply() * result);
        Codex.discover(player, def.id());
        celebrate(player, session, def);
        Stats.refresh(player);
        return new Result(true, Component.literal("Bound ").append(title(def, result)).append(Component.literal(" to ").append(stack.getHoverName())));
    }

    /**
     * The level an item ends up with when a tome is applied: the tome's level if that's higher, one more when they
     * match (up to the enchant's max), otherwise what it has.
     */
    public static int resultLevel(Enchant def, int current, int tome) {
        if (tome > current) {
            return Math.min(tome, def.maxLevel());
        }
        if (tome == current && current < def.maxLevel()) {
            return current + 1;
        }
        return current;
    }

    static Result combine(ServerPlayer player, int first, int second) {
        Session session = session(player);
        if (session == null) {
            return Result.fail("Move back to the table");
        }
        ItemStack a = slot(player, first);
        ItemStack b = slot(player, second);
        if (first == second || !EnchantContent.isTome(a) || !EnchantContent.isTome(b)
                || !EnchantContent.enchants(a).equals(EnchantContent.enchants(b))) {
            return Result.fail("Combining needs two identical tomes");
        }
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(a).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        if (def == null) {
            return Result.fail("This tome's magic has faded");
        }
        int result = inside.getValue() + 1;
        if (result > def.maxLevel()) {
            return Result.fail(def.name() + " can't go past " + Enchant.roman(def.maxLevel()));
        }
        Cost cost = Cost.of(Enchants.rules().combineCoins(def.rarity(), result), 0)
                .withMaterial(EnchantContent.RUNIC_DUST, Enchants.rules().combineDust(def.rarity(), result));
        String missing = missing(player, cost);
        if (missing != null) {
            return Result.fail(missing);
        }
        if (!pay(player, cost)) {
            return Result.fail("You can't pay for that");
        }
        b.shrink(1);
        player.getInventory().setItem(first, EnchantContent.tome(def, result));
        player.getInventory().setChanged();
        giveXp(player, Enchants.rules().skillXp().combine() * result);
        celebrate(player, session, def);
        return new Result(true, Component.literal("The tomes merge into ").append(Component.literal("Tome of ")
                .withStyle(Style.EMPTY.withColor(def.rarity().color()))).append(title(def, result)));
    }

    static Result salvage(ServerPlayer player, int slot) {
        Session session = session(player);
        if (session == null) {
            return Result.fail("Move back to the table");
        }
        ItemStack tome = slot(player, slot);
        if (!EnchantContent.isTome(tome)) {
            return Result.fail("That isn't a tome");
        }
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(tome).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        EnchantRarity rarity = def == null ? EnchantRarity.COMMON : def.rarity();
        int dust = Enchants.rules().salvageDust(rarity, inside.getValue());
        tome.shrink(1);
        give(player, new ItemStack(EnchantContent.RUNIC_DUST, dust));
        player.getInventory().setChanged();
        giveXp(player, Enchants.rules().skillXp().salvage() * inside.getValue());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.level().sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        return new Result(true, Component.literal("Salvaged into " + dust + " Runic Dust").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Into the bag, or at the player's feet if it's full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        int size = stack.getMaxStackSize();
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(size);
            if (!player.getInventory().add(part)) {
                player.drop(part, false, Prediction.SERVER_ONLY);
            }
        }
    }
}
