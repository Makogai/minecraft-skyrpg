package com.skyrpg.items.wardrobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The wardrobe, like Hypixel's: {@value #SETS} stored armour sets (two pages of nine), opened with {@code /wardrobe}
 * ({@code /wd}) or the O key. A set is put on with one click; the set you had on goes back to its own slot (or, if it
 * didn't come from the wardrobe, into your inventory). While a set is worn its slot shows it as equipped and it's on
 * you, not in the slot, so stats and set bonuses work as normal. The stored sets are kept on death, like backpacks; what
 * you're wearing drops as usual.
 */
public final class Wardrobe {
    public static final int SETS = 18;
    public static final int PER_PAGE = 9;
    /** Helmet, chestplate, leggings, boots: the rows of the menu and the order inside a set. */
    public static final EquipmentSlot[] PIECES = SkyItems.ARMOR_SLOTS;

    /** Every set's four pieces in a row (set {@code i} is {@code pieces[i*4 .. i*4+3]}), and which set is worn (-1: none). */
    public record Data(List<ItemStack> pieces, int equipped) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("pieces").forGetter(Data::pieces),
                Codec.INT.optionalFieldOf("equipped", -1).forGetter(Data::equipped)).apply(i, Data::of));

        static final Data EMPTY = of(List.of(), -1);

        /** Pads (or trims) to exactly {@value #SETS} sets. */
        static Data of(List<ItemStack> pieces, int equipped) {
            List<ItemStack> fixed = new ArrayList<>(SETS * 4);
            for (int i = 0; i < SETS * 4; i++) {
                fixed.add(i < pieces.size() && pieces.get(i) != null ? pieces.get(i) : ItemStack.EMPTY);
            }
            return new Data(List.copyOf(fixed), equipped >= 0 && equipped < SETS ? equipped : -1);
        }

        public ItemStack piece(int set, int piece) {
            return pieces.get(set * 4 + piece);
        }

        public boolean empty(int set) {
            for (int piece = 0; piece < 4; piece++) {
                if (!piece(set, piece).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final AttachmentType<Data> DATA = AttachmentRegistry.create(SkyRpgItems.id("wardrobe"), builder -> builder
            .persistent(Data.CODEC)
            .copyOnDeath()
            .initializer(() -> Data.EMPTY));

    private Wardrobe() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            for (String name : new String[] {"wardrobe", "wd"}) {
                dispatcher.register(Commands.literal(name).executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException(), 0);
                    return 1;
                }));
            }
        });
    }

    public static Data get(Player player) {
        return player.getAttachedOrElse(DATA, Data.EMPTY);
    }

    /** Stores the stack as given, so don't change it afterwards. */
    static void setPiece(Player player, int set, int piece, ItemStack stack) {
        Data data = get(player);
        List<ItemStack> pieces = new ArrayList<>(data.pieces());
        pieces.set(set * 4 + piece, stack);
        player.setAttached(DATA, Data.of(pieces, data.equipped()));
    }

    private static void setEquipped(Player player, int equipped) {
        player.setAttached(DATA, Data.of(get(player).pieces(), equipped));
    }

    public static void open(ServerPlayer player, int page) {
        if (player.isSpectator()) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new WardrobeMenu(id, inventory, (ServerPlayer) owner, page),
                Component.literal("Wardrobe (" + (page + 1) + "/" + SETS / PER_PAGE + ")")));
    }

    /** True if this stack can go in that piece's row (its equippable slot is that armour slot). */
    public static boolean fits(ItemStack stack, int piece) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == PIECES[piece];
    }

    /**
     * Puts on set {@code set}, or takes it off if it's the one worn. Returns a message for the player when it can't
     * (no room for loose armour), else null.
     */
    static String toggle(ServerPlayer player, int set) {
        Data data = get(player);
        if (data.equipped() == set) {
            storeWorn(player, set);
            setEquipped(player, -1);
            sound(player, 0.8f);
            return null;
        }
        if (data.empty(set)) {
            return "That slot is empty. Put armour in it first.";
        }
        if (data.equipped() >= 0) {
            storeWorn(player, data.equipped());
        } else if (!looseToInventory(player)) {
            return "No room in your inventory for the armour you're wearing. Put it in an empty wardrobe slot instead.";
        }
        Data now = get(player);
        for (int piece = 0; piece < 4; piece++) {
            player.setItemSlot(PIECES[piece], now.piece(set, piece));
            setPiece(player, set, piece, ItemStack.EMPTY);
        }
        setEquipped(player, set);
        sound(player, 1.2f);
        return null;
    }

    /** Moves what's worn into set {@code set}'s slot (which is empty while it's equipped). */
    private static void storeWorn(ServerPlayer player, int set) {
        for (int piece = 0; piece < 4; piece++) {
            ItemStack worn = player.getItemBySlot(PIECES[piece]);
            ItemStack stored = get(player).piece(set, piece);
            setPiece(player, set, piece, worn.copy());
            player.setItemSlot(PIECES[piece], ItemStack.EMPTY);
            if (!stored.isEmpty() && !player.getInventory().add(stored.copy())) {
                player.drop(stored.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
    }

    /** Armour worn that didn't come from the wardrobe goes into the inventory; false (and nothing moves) if it won't fit. */
    private static boolean looseToInventory(ServerPlayer player) {
        int needed = 0;
        for (EquipmentSlot slot : PIECES) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                needed++;
            }
        }
        int free = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).isEmpty()) {
                free++;
            }
        }
        if (free < needed) {
            return false;
        }
        for (EquipmentSlot slot : PIECES) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                inventory.add(worn.copy());
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        return true;
    }

    private static void sound(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS,
                0.8f, pitch);
    }
}
