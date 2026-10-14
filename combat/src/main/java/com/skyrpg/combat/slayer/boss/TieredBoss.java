package com.skyrpg.combat.slayer.boss;

/**
 * A slayer boss whose look and abilities depend on its tier (1-4). The tier is synced to clients (its model shows
 * more armour, glow and regalia tier by tier) and saved with the entity.
 */
public interface TieredBoss {
    int getTier();

    void setTier(int tier);
}
