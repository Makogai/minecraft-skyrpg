package com.skyrpg.core.world;

/** Entity tags any mod may check. */
public final class MobTags {
    /**
     * A mob from a spawner (see the combat mod): it drops its normal loot and counts for kill totals, but gives little
     * skill XP and no SkyRPG rewards, so AFK spawner farms don't replace playing.
     */
    public static final String FARMED = "skyrpg_farmed";

    private MobTags() {
    }
}
