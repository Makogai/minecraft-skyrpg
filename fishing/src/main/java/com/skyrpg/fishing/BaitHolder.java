package com.skyrpg.fishing;

import com.skyrpg.fishing.FishingItems.BaitItem;

/** A fishing hook (through our mixin) that remembers the bait it was cast with. */
public interface BaitHolder {
    BaitItem skyrpg$bait();
}
