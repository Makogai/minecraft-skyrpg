package com.skyrpg.items.client.armor;

import com.skyrpg.items.SkyRpgItems;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Raised detail on worn SkyRPG armour (brims, crests, pauldrons, cuffs...), so it looks like its icons
 * instead of a flat print: a detail model per set, drawn by {@link ArmorDetailLayer} over the vanilla armour.
 * <p>
 * A set's detail is its mesh in {@link ArmorDetailMeshes} plus {@code skyrpg_items:textures/entity/armor_detail/<set>.png},
 * and optionally {@code <set>_glow.png} for pixels that glow in the dark. tools/art/generate_armor_depth.py makes all
 * of them (and re-shades the flat armour layers to match).
 */
public final class ArmorDepthRendering {
    private ArmorDepthRendering() {
    }

    public static void init() {
        for (DetailSet set : DetailSet.values()) {
            ModelLayerRegistry.registerModelLayer(set.layer, set.mesh::get);
        }
        // Fabric hands every avatar renderer (players' and mannequins') over as the player type.
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new ArmorDetailLayer(avatar, context));
            }
        });
    }

    /** The armour sets with a detail model, named after their {@code skyrpg_items:<set>} equipment asset. */
    enum DetailSet {
        HARVESTER(ArmorDetailMeshes::harvester),
        PROSPECTOR(ArmorDetailMeshes::prospector),
        WOODSMAN(ArmorDetailMeshes::woodsman),
        VANGUARD(ArmorDetailMeshes::vanguard),
        DELVER(ArmorDetailMeshes::delver),
        SKYFORGED(ArmorDetailMeshes::skyforged),
        DEEPWARDEN(ArmorDetailMeshes::deepwarden),
        EMBERHEART(ArmorDetailMeshes::emberheart),
        LUMENWARD(ArmorDetailMeshes::lumenward),
        FIELDHAND(ArmorDetailMeshes::fieldhand),
        SUNWARD(ArmorDetailMeshes::sunward),
        FROSTGROVE(ArmorDetailMeshes::frostgrove),
        CINDERFIELD(ArmorDetailMeshes::cinderfield),
        MOONHARVEST(ArmorDetailMeshes::moonharvest),
        BARKGUARD(ArmorDetailMeshes::barkguard),
        MISTWARDEN(ArmorDetailMeshes::mistwarden),
        WILLOWSHADE(ArmorDetailMeshes::willowshade),
        EMBERBARK(ArmorDetailMeshes::emberbark),
        STARWOOD(ArmorDetailMeshes::starwood),
        FANGGUARD(ArmorDetailMeshes::fangguard),
        STORMPLATE(ArmorDetailMeshes::stormplate),
        RIFTWEAVE(ArmorDetailMeshes::riftweave),
        CINDERGUARD(ArmorDetailMeshes::cinderguard),
        STARFORGED(ArmorDetailMeshes::starforged),
        ANGLER(ArmorDetailMeshes::angler),
        REEFWALKER(ArmorDetailMeshes::reefwalker),
        ABYSSAL(ArmorDetailMeshes::abyssal),
        LEVIATHAN(ArmorDetailMeshes::leviathan),
        BLIGHTFORGED(ArmorDetailMeshes::blightforged),
        CINDERPLATE(ArmorDetailMeshes::cinderplate),
        RIMEGUARD(ArmorDetailMeshes::rimeguard),
        MIREWOVEN(ArmorDetailMeshes::mirewoven),
        ARACHNE_CARAPACE(ArmorDetailMeshes::arachne_carapace),
        TIDE_REGALIA(ArmorDetailMeshes::tide_regalia),
        WARDENS_SHROUD(ArmorDetailMeshes::wardens_shroud),
        RUNEFORGED(ArmorDetailMeshes::runeforged),
        HELLFORGED(ArmorDetailMeshes::hellforged),
        GRAVEWARDEN(ArmorDetailMeshes::gravewarden),
        SILKSTALKER(ArmorDetailMeshes::silkstalker),
        MARSHALS_BONEMAIL(ArmorDetailMeshes::marshals_bonemail),
        VOIDWALKER(ArmorDetailMeshes::voidwalker),
        HALLOWED(ArmorDetailMeshes::hallowed);

        final ResourceKey<EquipmentAsset> asset;
        final ModelLayerLocation layer;
        final Identifier texture;
        final Identifier glowTexture;
        final Supplier<LayerDefinition> mesh;

        DetailSet(Supplier<LayerDefinition> mesh) {
            String id = name().toLowerCase(Locale.ROOT);
            this.asset = ResourceKey.create(EquipmentAssets.ROOT_ID, SkyRpgItems.id(id));
            this.layer = new ModelLayerLocation(SkyRpgItems.id("armor_detail_" + id), "main");
            this.texture = SkyRpgItems.id("textures/entity/armor_detail/" + id + ".png");
            this.glowTexture = SkyRpgItems.id("textures/entity/armor_detail/" + id + "_glow.png");
            this.mesh = mesh;
        }

        /** The set drawn for this equipment asset, or null if it has no detail model. */
        static DetailSet of(ResourceKey<EquipmentAsset> asset) {
            for (DetailSet set : values()) {
                if (set.asset.equals(asset)) {
                    return set;
                }
            }
            return null;
        }
    }
}
