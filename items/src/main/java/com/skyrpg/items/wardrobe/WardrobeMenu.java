package com.skyrpg.items.wardrobe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * One page of the wardrobe as a six-row chest (vanilla's screen, no client code). Rows 1-4 are helmets, chestplates,
 * leggings and boots: real slots, nine sets side by side, that take only the right kind of piece. Row 5 has a button
 * under each set (equip / take off), row 6 the page arrows. The worn set's column shows what you have on and can't be
 * changed from here. Every change to the slots is written straight back to the player's wardrobe.
 */
final class WardrobeMenu extends ChestMenu {
    private static final int PIECE_SLOTS = 36;
    private static final int BUTTONS = 36;
    private static final int SIZE = 54;
    private static final int PREVIOUS = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;
    private static final int PAGES = Wardrobe.SETS / Wardrobe.PER_PAGE;

    private final ServerPlayer player;
    private final int page;
    private final Storage storage;

    WardrobeMenu(int id, Inventory inventory, ServerPlayer player, int page) {
        this(id, inventory, player, page, new Storage(player, page));
    }

    private WardrobeMenu(int id, Inventory inventory, ServerPlayer player, int page, Storage storage) {
        super(MenuType.GENERIC_9x6, id, inventory, storage, 6);
        this.player = player;
        this.page = page;
        this.storage = storage;
        for (int i = 0; i < SIZE; i++) {
            Slot plain = slots.get(i);
            Slot slot = i < PIECE_SLOTS ? new PieceSlot(storage, i, plain.x, plain.y) : new LockedSlot(storage, i, plain.x, plain.y);
            slot.index = i;
            slots.set(i, slot);
        }
        storage.load();
        fillButtons();
    }

    private int set(int column) {
        return page * Wardrobe.PER_PAGE + column;
    }

    private boolean equippedColumn(int column) {
        return Wardrobe.get(player).equipped() == set(column);
    }

    /** Piece slots: only the matching kind of armour, one at a time, and not in the worn set's column. */
    private final class PieceSlot extends Slot {
        PieceSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !equippedColumn(getContainerSlot() % 9) && Wardrobe.fits(stack, getContainerSlot() / 9);
        }

        @Override
        public boolean mayPickup(Player who) {
            return !equippedColumn(getContainerSlot() % 9);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class LockedSlot extends Slot {
        LockedSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player who) {
            return false;
        }
    }

    /** The page's pieces; edits of the stored (not worn) columns go back to the wardrobe. */
    private static final class Storage extends SimpleContainer {
        private final ServerPlayer player;
        private final int page;
        private boolean loading = true;

        Storage(ServerPlayer player, int page) {
            super(SIZE);
            this.player = player;
            this.page = page;
        }

        void load() {
            loading = true;
            Wardrobe.Data data = Wardrobe.get(player);
            for (int column = 0; column < 9; column++) {
                int set = page * Wardrobe.PER_PAGE + column;
                for (int piece = 0; piece < 4; piece++) {
                    ItemStack shown = data.equipped() == set ? player.getItemBySlot(Wardrobe.PIECES[piece]).copy() : data.piece(set, piece).copy();
                    setItem(piece * 9 + column, shown);
                }
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) {
                return;
            }
            Wardrobe.Data data = Wardrobe.get(player);
            for (int column = 0; column < 9; column++) {
                int set = page * Wardrobe.PER_PAGE + column;
                if (data.equipped() == set) {
                    continue;
                }
                for (int piece = 0; piece < 4; piece++) {
                    ItemStack here = getItem(piece * 9 + column);
                    if (!ItemStack.matches(here, data.piece(set, piece))) {
                        Wardrobe.setPiece(player, set, piece, here.copy());
                    }
                }
            }
        }
    }

    private void fillButtons() {
        storage.loading = true;
        Wardrobe.Data data = Wardrobe.get(player);
        for (int column = 0; column < 9; column++) {
            int set = set(column);
            ItemStack button;
            if (data.equipped() == set) {
                button = named(Items.DYE.lime(), "Slot " + (set + 1) + ": Equipped", ChatFormatting.GREEN, "You're wearing this set.", "Click to take it off.");
            } else if (!data.empty(set)) {
                button = named(Items.DYE.pink(), "Slot " + (set + 1) + ": Ready", ChatFormatting.LIGHT_PURPLE, "Click to put this set on.");
            } else {
                button = named(Items.DYE.gray(), "Slot " + (set + 1) + ": Empty", ChatFormatting.GRAY, "Put armour in the slots above,",
                        "or click armour in your inventory.");
            }
            storage.setItem(BUTTONS + column, button);
        }
        for (int i = 45; i < SIZE; i++) {
            storage.setItem(i, named(Items.STAINED_GLASS_PANE.black(), " ", ChatFormatting.DARK_GRAY));
        }
        if (page > 0) {
            storage.setItem(PREVIOUS, named(Items.ARROW, "Previous page", ChatFormatting.YELLOW, "Page " + page + " of " + PAGES));
        }
        if (page < PAGES - 1) {
            storage.setItem(NEXT, named(Items.ARROW, "Next page", ChatFormatting.YELLOW, "Page " + (page + 2) + " of " + PAGES));
        }
        storage.setItem(INFO, named(Items.ARMOR_STAND, "Wardrobe", ChatFormatting.GOLD, "Keep up to " + Wardrobe.SETS + " armour sets here.",
                "Click a dye to wear a set: the one you", "had on goes back to its own slot.", "Stored sets are kept when you die."));
        storage.loading = false;
    }

    private static ItemStack named(Item base, String name, ChatFormatting color, String... lines) {
        ItemStack stack = new ItemStack(base);
        stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line).withStyle(style -> style.withItalic(false)).withStyle(ChatFormatting.GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId >= BUTTONS && slotId < SIZE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                press(slotId);
            }
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, input, who);
        fillButtons();
        broadcastChanges();
    }

    private void press(int slotId) {
        if (slotId < BUTTONS + 9) {
            String problem = Wardrobe.toggle(player, set(slotId - BUTTONS));
            if (problem != null) {
                player.sendOverlayMessage(Component.literal(problem).withColor(0xFFFF6B6B));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1.0f);
            }
            storage.load();
            fillButtons();
        } else if (slotId == PREVIOUS && page > 0) {
            Wardrobe.open(player, page - 1);
        } else if (slotId == NEXT && page < PAGES - 1) {
            Wardrobe.open(player, page + 1);
        }
    }

    /** Shift-click: a piece goes back to the inventory; armour from the inventory goes into the first free matching slot. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(who)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (index < PIECE_SLOTS) {
            moveItemStackTo(stack, SIZE, slots.size(), true);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (index >= SIZE) {
            for (int piece = 0; piece < 4 && !stack.isEmpty(); piece++) {
                for (int column = 0; column < 9; column++) {
                    Slot target = slots.get(piece * 9 + column);
                    if (!target.hasItem() && target.mayPlace(stack)) {
                        target.setByPlayer(stack.split(1));
                        slot.setChanged();
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player who) {
        return who == player;
    }
}
