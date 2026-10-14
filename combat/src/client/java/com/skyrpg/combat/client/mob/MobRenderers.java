package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.GlimmerCrawlerEntity;
import com.skyrpg.combat.mob.WorldMobs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.layers.DrownedOuterLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.SkeletonClothingLayer;
import net.minecraft.client.renderer.entity.layers.SpiderEyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Our world mobs on vanilla models, with our own textures (vanilla's overlay layers taken off, everything is in ours). */
public final class MobRenderers {
    private static final Identifier GLIMMER_CRAWLER = texture("glimmer_crawler");
    private static final RenderType GLIMMER_GLOW = RenderTypes.eyes(texture("glimmer_crawler_glow"));
    private static final Identifier BOG_HAG = texture("bog_hag");
    private static final Identifier FROST_REVENANT = texture("frost_revenant");
    private static final Identifier EMBER_IMP = texture("ember_imp");
    private static final Identifier GROVE_SENTINEL = texture("grove_sentinel");
    private static final Identifier TIDE_LURKER = texture("tide_lurker");
    private static final Identifier HOLLOW_WISP = texture("hollow_wisp");
    private static final Identifier GRAVECALLER = texture("gravecaller");

    private MobRenderers() {
    }

    public static void init() {
        EntityRendererRegistry.register(WorldMobs.GLIMMER_CRAWLER, GlimmerCrawlerRenderer::new);
        EntityRendererRegistry.register(WorldMobs.BOG_HAG, context -> new WitchRenderer(context) {
            @Override
            public Identifier getTextureLocation(WitchRenderState state) {
                return BOG_HAG;
            }
        });
        EntityRendererRegistry.register(WorldMobs.FROST_REVENANT, FrostRevenantRenderer::new);
        EntityRendererRegistry.register(WorldMobs.EMBER_IMP, context -> new BlazeRenderer(context) {
            @Override
            public Identifier getTextureLocation(LivingEntityRenderState state) {
                return EMBER_IMP;
            }
        });
        EntityRendererRegistry.register(WorldMobs.GROVE_SENTINEL, context -> new ZombieRenderer(context) {
            @Override
            public Identifier getTextureLocation(ZombieRenderState state) {
                return GROVE_SENTINEL;
            }
        });
        EntityRendererRegistry.register(WorldMobs.TIDE_LURKER, TideLurkerRenderer::new);
        EntityRendererRegistry.register(WorldMobs.HOLLOW_WISP, context -> new VexRenderer(context) {
            @Override
            public Identifier getTextureLocation(VexRenderState state) {
                return HOLLOW_WISP;
            }
        });
        EntityRendererRegistry.register(WorldMobs.GRAVECALLER, context -> new ZombieRenderer(context) {
            @Override
            public Identifier getTextureLocation(ZombieRenderState state) {
                return GRAVECALLER;
            }
        });
    }

    private static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/mob/" + name + ".png");
    }

    /** A cave-spider-sized model (made a little bigger by the crawler's scale) with glowing crystals. */
    private static final class GlimmerCrawlerRenderer extends SpiderRenderer<GlimmerCrawlerEntity> {
        GlimmerCrawlerRenderer(EntityRendererProvider.Context context) {
            super(context, ModelLayers.CAVE_SPIDER);
            layers.removeIf(layer -> layer instanceof SpiderEyesLayer);
            addLayer(new EyesLayer<LivingEntityRenderState, SpiderModel>(this) {
                @Override
                public RenderType renderType() {
                    return GLIMMER_GLOW;
                }
            });
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return GLIMMER_CRAWLER;
        }
    }

    private static final class FrostRevenantRenderer extends StrayRenderer {
        FrostRevenantRenderer(EntityRendererProvider.Context context) {
            super(context);
            layers.removeIf(layer -> layer instanceof SkeletonClothingLayer);
        }

        @Override
        public Identifier getTextureLocation(SkeletonRenderState state) {
            return FROST_REVENANT;
        }
    }

    private static final class TideLurkerRenderer extends DrownedRenderer {
        TideLurkerRenderer(EntityRendererProvider.Context context) {
            super(context);
            layers.removeIf(layer -> layer instanceof DrownedOuterLayer);
        }

        @Override
        public Identifier getTextureLocation(ZombieRenderState state) {
            return TIDE_LURKER;
        }
    }
}
