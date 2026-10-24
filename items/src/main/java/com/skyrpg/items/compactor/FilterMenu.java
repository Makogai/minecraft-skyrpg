package com.skyrpg.items.compactor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * A Personal Compactor's or Deletor's settings, shown as a plain chest (vanilla's 9xN menu, no client code): its material
 * slots first, the rest locked. Nothing moves: clicking an item in your inventory adds that material, clicking a material
 * slot clears it. Every click is answered with the real contents, so the client never keeps a moved item.
 */
final class FilterMenu extends AbstractContainerMenu {
    private final ServerPlayer player;
    private final InteractionHand hand;
    private final ItemStack tool;
    private final PersonalTools.ToolItem item;
    private final SimpleContainer display;
    private final int size;

    private FilterMenu(int id, Inventory inventory, ServerPlayer player, InteractionHand hand, int rows) {
        super(type(rows), id);
        this.player = player;
        this.hand = hand;
        this.tool = player.getItemInHand(hand);
        this.item = (PersonalTools.ToolItem) tool.getItem();
        this.size = rows * 9;
        this.display = new SimpleContainer(size);
        for (int i = 0; i < size; i++) {
            addSlot(new Slot(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        int top = 18 + rows * 18 + 13;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, top + 58));
        }
        // Rewrites the tooltip too, in case the tool was made some other way (a command, an old version).
        PersonalTools.setFilter(tool, PersonalTools.filter(tool));
        refresh();
    }

    static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PersonalTools.ToolItem tool) || player.isSpectator()) {
            return;
        }
        int rows = (tool.tier().slots() + 1 + 8) / 9;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new FilterMenu(id, inventory, player, hand, rows), stack.getHoverName()));
    }

    private static MenuType<?> type(int rows) {
        return rows <= 1 ? MenuType.GENERIC_9x1 : MenuType.GENERIC_9x2;
    }

    private void refresh() {
        List<Item> filter = PersonalTools.filter(tool);
        int slots = item.tier().slots();
        for (int i = 0; i < size; i++) {
            ItemStack shown;
            if (i < filter.size()) {
                Item material = filter.get(i);
                Item enchanted = item.compactor() ? Enchanted.of(material) : null;
                shown = new ItemStack(enchanted != null ? enchanted : material);
                shown.set(DataComponents.LORE, new ItemLore(List.of(
                        text(item.compactor() ? "Packs " + Enchanted.COUNT + " " + material.getName(new ItemStack(material)).getString() + " into one"
                                : "Deletes " + material.getName(new ItemStack(material)).getString(), ChatFormatting.GRAY),
                        text("Click to clear this slot", ChatFormatting.YELLOW))));
            } else if (i < slots) {
                shown = named(Items.STAINED_GLASS_PANE.lightGray(), "Empty slot", ChatFormatting.GRAY,
                        "Click " + (item.compactor() ? "a material" : "an item") + " in your inventory to add it");
            } else if (i == size - 1) {
                shown = item.compactor()
                        ? named(Items.PISTON, "How it works", ChatFormatting.GOLD, "Every " + Enchanted.COUNT + " of a material here",
                        "becomes one enchanted item as you", "gather. Keep it anywhere in your inventory.")
                        : named(Items.LAVA_BUCKET, "How it works", ChatFormatting.GOLD, "Items set here are thrown away",
                        "as you gather them (collections still", "count). Keep it anywhere in your inventory.");
            } else {
                shown = named(Items.STAINED_GLASS_PANE.black(), "Locked", ChatFormatting.DARK_GRAY, "A higher tier holds more");
            }
            display.setItem(i, shown);
        }
    }

    private static ItemStack named(Item base, String name, ChatFormatting color, String... lines) {
        ItemStack stack = new ItemStack(base);
        stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(text(line, ChatFormatting.GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static Component text(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(color);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
            if (slotId >= 0 && slotId < size) {
                clear(slotId);
            } else if (slotId >= size && slotId < slots.size()) {
                add(slots.get(slotId).getItem());
            }
        }
        sendAllDataToRemote();
    }

    private void clear(int index) {
        List<Item> filter = new ArrayList<>(PersonalTools.filter(tool));
        if (index < filter.size()) {
            filter.remove(index);
            PersonalTools.setFilter(tool, filter);
            refresh();
            sound(0.8f);
        }
    }

    private void add(ItemStack stack) {
        if (stack.isEmpty() || PersonalTools.isTool(stack.getItem())) {
            return;
        }
        Item material = stack.getItem();
        if (item.compactor()) {
            Item source = Enchanted.material(material);
            if (source != null) {
                material = source;
            }
            if (Enchanted.of(material) == null) {
                say(stack.getHoverName().getString() + " has no enchanted form.");
                return;
            }
        } else if (stack.getMaxStackSize() <= 1) {
            say("A deletor only takes stackable items, so gear is never deleted.");
            return;
        }
        List<Item> filter = new ArrayList<>(PersonalTools.filter(tool));
        if (filter.contains(material)) {
            say("It's already set to that.");
            return;
        }
        if (filter.size() >= item.tier().slots()) {
            say("All " + item.tier().slots() + " slots are used. Click one to clear it.");
            return;
        }
        filter.add(material);
        PersonalTools.setFilter(tool, filter);
        refresh();
        sound(1.4f);
        if (item.compactor()) {
            PersonalTools.compact(player);
        }
    }

    private void say(String message) {
        player.sendOverlayMessage(Component.literal(message).withColor(0xFFFF6B6B));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    private void sound(float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3f, pitch);
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player who) {
        return who == player && player.getItemInHand(hand) == tool;
    }
}
