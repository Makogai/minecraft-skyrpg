package com.skyrpg.combat.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;

/** Glimmer Crawler: a quick cave spider with glowing crystals on its back, deep in the caves (below y 0). */
public class GlimmerCrawlerEntity extends Spider {
    public GlimmerCrawlerEntity(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.4).add(Attributes.SCALE, 1.25);
    }
}
