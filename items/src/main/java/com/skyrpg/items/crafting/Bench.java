package com.skyrpg.items.crafting;

import com.mojang.brigadier.CommandDispatcher;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.backpack.Backpacks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The Artisan Bench: where SkyRPG gear is crafted from materials, by recipes that unlock with skill levels
 * ({@link BenchRecipes}). Place one anywhere; NPCs can open it too ({@code "open_bench"} action). Upgrades keep
 * the item's name, enchantments and contents. Ingredients come from the inventory and carried backpacks
 * ({@link Materials}).
 */
public final class Bench {
    private static final int MAX_TIMES = 64;
    /** What an upgraded item keeps from the one it's made from. */
    private static final List<DataComponentType<?>> KEPT = List.of(DataComponents.CUSTOM_NAME, DataComponents.ENCHANTMENTS,
            DataComponents.CONTAINER, DataComponents.LORE);
    /** The enchants mod's component (SkyRPG enchants live there, not in vanilla's), kept too when that mod is loaded. */
    private static final Identifier ENCHANTS_COMPONENT = Identifier.fromNamespaceAndPath("skyrpg_enchants", "enchants");

    /** The recipe a player tracks on the HUD (see {@link BenchPayloads.Track}). */
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Identifier> TRACKED =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(SkyRpgItems.id("tracked_recipe"),
                    builder -> builder.persistent(Identifier.CODEC).copyOnDeath());
    private static final int TRACK_SYNC_TICKS = 20;

    public static final Block BLOCK = registerBlock();
    public static final Item ITEM = registerItem();

    private Bench() {
    }

    public static void init() {
        BenchPayloads.register();
        JsonDataLoader.register(SkyRpgItems.id("bench_recipes"), BenchRecipes.DIRECTORY, BenchRecipes::load);
        ServerPlayNetworking.registerGlobalReceiver(BenchPayloads.Craft.TYPE, (payload, context) ->
                craft(context.player(), payload.recipe(), payload.times()));
        ServerPlayNetworking.registerGlobalReceiver(BenchPayloads.Track.TYPE, (payload, context) ->
                track(context.player(), payload.recipe().isEmpty() ? null : Identifier.tryParse(payload.recipe())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // The recipe list for recipe viewers (JEI) and the tracked recipe for the HUD.
            ServerPlayNetworking.send(handler.getPlayer(), view(handler.getPlayer(), Component.empty(), true, true));
            syncTracked(handler.getPlayer());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % TRACK_SYNC_TICKS == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.hasAttached(TRACKED)) {
                        syncTracked(player);
                    }
                }
            }
        });
        Actions.register("open_bench", (player, params) -> open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> registerCommand(dispatcher));
        SkillLevels.LEVEL_UP.register(Bench::announceUnlocks);
        // Other mods' costs (enchanting) take from the same places the bench does.
        com.skyrpg.core.items.MaterialStore.install(new com.skyrpg.core.items.MaterialStore.Backend() {
            @Override
            public int count(ServerPlayer player, Item item) {
                return Materials.count(player, item);
            }

            @Override
            public void take(ServerPlayer player, Item item, int amount) {
                Materials.take(player, item, amount);
            }
        });
    }

    private static Block registerBlock() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgItems.id("artisan_bench"));
        return Registry.register(BuiltInRegistries.BLOCK, key, new ArtisanBenchBlock(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).ignitedByLava()));
    }

    private static Item registerItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgItems.id("artisan_bench"));
        return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bench")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
    }

    public static void open(ServerPlayer player) {
        ServerPlayNetworking.send(player, view(player, Component.empty(), true, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static BenchPayloads.View view(ServerPlayer player, Component notice, boolean good, boolean refresh) {
        List<BenchPayloads.Entry> entries = new ArrayList<>();
        for (BenchRecipe recipe : BenchRecipes.all().values()) {
            entries.add(entry(player, recipe));
        }
        return new BenchPayloads.View(entries, notice, good, refresh);
    }

    private static BenchPayloads.Entry entry(ServerPlayer player, BenchRecipe recipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        List<Integer> have = new ArrayList<>();
        for (BenchRecipe.Ingredient ingredient : recipe.ingredients()) {
            ingredients.add(stack(ingredient.item(), ingredient.count()));
            have.add(Materials.count(player, ingredient.item()));
        }
        return new BenchPayloads.Entry(recipe.id(), recipe.category(), stack(recipe.result(), recipe.count()), ingredients, have,
                recipe.requirement(), recipe.unlocked(player));
    }

    /** Starts tracking a recipe on the HUD ({@code null} stops). */
    public static void track(ServerPlayer player, Identifier recipe) {
        if (recipe == null || BenchRecipes.get(recipe) == null) {
            player.removeAttached(TRACKED);
        } else {
            player.setAttached(TRACKED, recipe);
        }
        syncTracked(player);
    }

    private static void syncTracked(ServerPlayer player) {
        Identifier id = player.getAttached(TRACKED);
        BenchRecipe recipe = id == null ? null : BenchRecipes.get(id);
        ServerPlayNetworking.send(player, new BenchPayloads.Tracked(recipe == null ? List.of() : List.of(entry(player, recipe))));
    }

    /** A display stack (counts may go past a stack's size; it's only shown). */
    private static ItemStack stack(Item item, int count) {
        ItemStack stack = SkyItems.create(item, 1);
        stack.setCount(count);
        return stack;
    }

    private static void craft(ServerPlayer player, Identifier id, int requested) {
        BenchRecipe recipe = BenchRecipes.get(id);
        if (recipe == null) {
            reply(player, Component.literal("That recipe doesn't exist any more."), false);
            return;
        }
        if (!recipe.unlocked(player)) {
            reply(player, Component.literal("Needs " + recipe.requirement() + "."), false);
            return;
        }
        int affordable = Integer.MAX_VALUE;
        for (BenchRecipe.Ingredient ingredient : recipe.ingredients()) {
            affordable = Math.min(affordable, Materials.count(player, ingredient.item()) / ingredient.count());
        }
        if (affordable <= 0) {
            reply(player, Component.literal("Missing: " + missing(player, recipe)), false);
            return;
        }
        int times = Math.min(Mth.clamp(requested, 1, MAX_TIMES), affordable);
        for (int i = 0; i < times; i++) {
            ItemStack upgraded = null;
            int backpackSlot = -1;
            for (BenchRecipe.Ingredient ingredient : recipe.ingredients()) {
                Materials.Taken taken = Materials.take(player, ingredient.item(), ingredient.count());
                if (ingredient.item() == recipe.upgrade() && upgraded == null && !taken.stacks().isEmpty()) {
                    upgraded = taken.stacks().getFirst();
                    backpackSlot = taken.backpackSlot();
                }
            }
            ItemStack result = SkyItems.create(recipe.result(), recipe.count());
            if (upgraded != null) {
                for (DataComponentType<?> type : KEPT) {
                    copy(type, upgraded, result);
                }
                DataComponentType<?> enchants = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ENCHANTS_COMPONENT);
                if (enchants != null) {
                    copy(enchants, upgraded, result);
                }
            }
            if (backpackSlot >= 0 && Backpacks.isBackpack(result) && Backpacks.get(player, backpackSlot).isEmpty()) {
                Backpacks.set(player, backpackSlot, result);
            } else {
                give(player, result);
            }
        }
        com.skyrpg.core.event.ProgressEvents.BENCH_CRAFTED.invoker().onCrafted(player, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(recipe.result()), times * recipe.count());
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
        ItemStack shown = SkyItems.create(recipe.result(), 1);
        reply(player, Component.literal("Crafted " + (times * recipe.count() > 1 ? Msg.number(times * recipe.count()) + "× " : ""))
                .append(shown.getHoverName()), true);
    }

    private static <T> void copy(DataComponentType<T> type, ItemStack from, ItemStack to) {
        T value = from.get(type);
        if (value != null) {
            to.set(type, value);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
            player.sendOverlayMessage(Component.literal("Inventory full! It dropped at your feet.").withColor(0xFFFF6B6B));
        }
    }

    /** "12 Raw Skyiron, 1 Prism Fragment": what's short for one craft. */
    private static String missing(ServerPlayer player, BenchRecipe recipe) {
        List<String> parts = new ArrayList<>();
        for (BenchRecipe.Ingredient ingredient : recipe.ingredients()) {
            int lacking = ingredient.count() - Materials.count(player, ingredient.item());
            if (lacking > 0) {
                parts.add(Msg.number(lacking) + " " + new ItemStack(ingredient.item()).getHoverName().getString());
            }
        }
        return String.join(", ", parts);
    }

    private static void reply(ServerPlayer player, Component notice, boolean good) {
        ServerPlayNetworking.send(player, view(player, notice, good, true));
    }

    /** Inside the level-up message: the recipes this level just unlocked. */
    private static void announceUnlocks(ServerPlayer player, Identifier skill, int oldLevel, int newLevel) {
        List<Component> names = new ArrayList<>();
        for (BenchRecipe recipe : BenchRecipes.all().values()) {
            if (skill.equals(recipe.skill()) && recipe.level() > oldLevel && recipe.level() <= newLevel) {
                names.add(SkyItems.create(recipe.result(), 1).getHoverName());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        Component list = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            list = ((net.minecraft.network.chat.MutableComponent) list).append(i == 0 ? Component.empty() : Msg.text(", ", ChatFormatting.DARK_GRAY)).append(names.get(i));
        }
        Notice.of(Notice.Kind.ITEM).tag("NEW RECIPES").send(player, ((net.minecraft.network.chat.MutableComponent) list)
                .append(Msg.text("  at the Artisan Bench", ChatFormatting.DARK_GRAY)));
    }
}
