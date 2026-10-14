package com.skyrpg.items.accessory;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a player wears in their accessory slots: one stack per {@link AccessorySlot}, EMPTY where there is
 * none. Immutable, and so are the stacks in it: every change is a new loadout ({@link #with}), which is
 * what makes the attachment save and sync.
 *
 * @param stacks always {@link AccessorySlot#COUNT} entries, in slot order
 */
public record AccessoryLoadout(List<ItemStack> stacks) {
    public static final AccessoryLoadout EMPTY = new AccessoryLoadout(List.of());

    /** Saved by slot id ({@code {"amulet": {...}}}, empty slots left out), so slots can be added or reordered later. */
    public static final Codec<AccessoryLoadout> CODEC = Codec.unboundedMap(AccessorySlot.CODEC, ItemStack.CODEC)
            .xmap(AccessoryLoadout::fromMap, AccessoryLoadout::toMap);
    public static final StreamCodec<RegistryFriendlyByteBuf, AccessoryLoadout> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(AccessoryLoadout::new, AccessoryLoadout::stacks);

    /** Pads or trims to one entry per slot. */
    public AccessoryLoadout {
        List<ItemStack> fixed = new ArrayList<>(AccessorySlot.COUNT);
        for (int i = 0; i < AccessorySlot.COUNT; i++) {
            ItemStack stack = i < stacks.size() ? stacks.get(i) : null;
            fixed.add(stack == null ? ItemStack.EMPTY : stack);
        }
        stacks = List.copyOf(fixed);
    }

    public ItemStack get(AccessorySlot slot) {
        return stacks.get(slot.ordinal());
    }

    /** A copy with this slot changed. The stack is stored as given, so don't change it afterwards. */
    public AccessoryLoadout with(AccessorySlot slot, ItemStack stack) {
        List<ItemStack> changed = new ArrayList<>(stacks);
        changed.set(slot.ordinal(), stack);
        return new AccessoryLoadout(changed);
    }

    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    private static AccessoryLoadout fromMap(Map<AccessorySlot, ItemStack> saved) {
        List<ItemStack> stacks = new ArrayList<>(AccessorySlot.COUNT);
        for (AccessorySlot slot : AccessorySlot.values()) {
            stacks.add(saved.getOrDefault(slot, ItemStack.EMPTY));
        }
        return new AccessoryLoadout(stacks);
    }

    private Map<AccessorySlot, ItemStack> toMap() {
        Map<AccessorySlot, ItemStack> saved = new EnumMap<>(AccessorySlot.class);
        for (AccessorySlot slot : AccessorySlot.values()) {
            if (!get(slot).isEmpty()) {
                saved.put(slot, get(slot));
            }
        }
        return saved;
    }
}
