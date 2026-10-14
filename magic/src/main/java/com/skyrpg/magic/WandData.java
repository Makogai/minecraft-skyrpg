package com.skyrpg.magic;

import java.util.ArrayList;
import java.util.List;

/**
 * What a wand stack holds, read by {@link WandItem#data}: the spell id in each slot ("" for an empty one) and which slot is selected.
 * Both live in synced components of the stack, so the HUD and the spell wheel read them straight off it on the client too.
 */
public record WandData(List<String> slots, int selected) {
    public WandData {
        slots = List.copyOf(slots);
    }

    public static WandData empty(int slotCount) {
        return new WandData(java.util.Collections.nCopies(slotCount, ""), 0);
    }

    /** The spell id in this slot, "" if empty or out of range. */
    public String spellAt(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : "";
    }

    public String selectedSpell() {
        return spellAt(selected);
    }

    /** The same slots, resized to a wand's slot count (a data change may add or remove slots). */
    public WandData fit(int slotCount) {
        if (slots.size() == slotCount && selected >= 0 && selected < slotCount) {
            return this;
        }
        List<String> next = new ArrayList<>(slots);
        while (next.size() < slotCount) {
            next.add("");
        }
        return new WandData(next.subList(0, slotCount), Math.max(0, Math.min(selected, slotCount - 1)));
    }

    public WandData with(int slot, String spell) {
        List<String> next = new ArrayList<>(slots);
        next.set(slot, spell);
        return new WandData(next, selected);
    }

    public WandData select(int slot) {
        return new WandData(slots, slot);
    }

    public int filled() {
        return (int) slots.stream().filter(s -> !s.isEmpty()).count();
    }
}
