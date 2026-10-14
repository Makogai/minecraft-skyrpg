package com.skyrpg.items.backpack;

import com.mojang.serialization.Codec;
import com.skyrpg.items.ModItems;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Backpacks: carried in their own slots in the inventory (three, right of the window, see
 * {@link BackpackMenuSlot}) and opened from there. A backpack's contents live on the backpack item itself
 * (the container component, like a shulker box), so a full backpack can be moved, stored or traded, and
 * while it's open its slot isn't part of the open menu, so it can't be moved out from under you.
 * Backpacks never go inside backpacks. The slots are kept on death, like accessories.
 */
public final class Backpacks {
    public static final int SLOTS = 3;

    /** The three carried backpacks (EMPTY where there's none). Only the owner needs them, through their inventory menu. */
    public static final AttachmentType<List<ItemStack>> CARRIED = AttachmentRegistry.create(SkyRpgItems.id("backpacks"), builder -> builder
            .persistent(ItemStack.OPTIONAL_CODEC.listOf().xmap(Backpacks::fixed, list -> list))
            .copyOnDeath()
            .initializer(() -> fixed(List.of())));

    private Backpacks() {
    }

    public static void init() {
        BackpackPayloads.init();
    }

    /** How many slots this backpack holds, or 0 if it isn't one. */
    public static int capacity(ItemStack stack) {
        return capacity(stack.getItem());
    }

    public static int capacity(Item item) {
        return Map.of(ModItems.SATCHEL, 9, ModItems.TRAIL_PACK, 18, ModItems.RANGER_PACK, 27, ModItems.VOYAGER_PACK, 36,
                ModItems.COLOSSAL_PACK, 54).getOrDefault(item, 0);
    }

    public static boolean isBackpack(ItemStack stack) {
        return capacity(stack) > 0;
    }

    public static List<ItemStack> carried(Player player) {
        return player.getAttachedOrElse(CARRIED, fixed(List.of()));
    }

    public static ItemStack get(Player player, int slot) {
        return carried(player).get(slot);
    }

    /** Stores the stack as given, so don't change it afterwards. */
    public static void set(Player player, int slot, ItemStack stack) {
        List<ItemStack> changed = new ArrayList<>(carried(player));
        changed.set(slot, stack);
        player.setAttached(CARRIED, List.copyOf(changed));
    }

    /** Opens the backpack in that slot, or the first one carried if {@code slot} is -1. */
    public static void open(ServerPlayer player, int slot) {
        if (slot < 0) {
            for (int i = 0; i < SLOTS && slot < 0; i++) {
                if (isBackpack(get(player, i))) {
                    slot = i;
                }
            }
            if (slot < 0) {
                player.sendOverlayMessage(Component.literal("You aren't carrying a backpack. Put one in a backpack slot (E).").withColor(0xFFFF6B6B));
                return;
            }
        }
        if (slot >= SLOTS || !isBackpack(get(player, slot))) {
            return;
        }
        ItemStack backpack = get(player, slot);
        int index = slot;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> BackpackMenu.create(id, inventory, (ServerPlayer) owner, index),
                backpack.getHoverName()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static List<ItemStack> fixed(List<ItemStack> stacks) {
        List<ItemStack> fixed = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            fixed.add(i < stacks.size() && stacks.get(i) != null ? stacks.get(i) : ItemStack.EMPTY);
        }
        return List.copyOf(fixed);
    }
}
