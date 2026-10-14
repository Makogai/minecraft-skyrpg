package com.skyrpg.items.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.accessory.Accessories;
import com.skyrpg.items.accessory.AccessorySlot;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Draws worn accessories on players, for everyone: the accessory layer on every player (and mannequin)
 * renderer, bracers on your own hands in first person, and the cape rule.
 * <p>
 * Each item's worn texture is {@code <namespace>:textures/entity/accessory/<item path>.png}, laid out for its
 * slot's {@link AccessoryModel}. An accessory without one isn't drawn (data packs can add accessories that are
 * only stats). A worn cloak replaces the player's own cape: both hang from the same place.
 */
public final class AccessoryRendering {
    /** Item -> its worn texture, or empty if it has none. Cleared on resource reload. */
    private static final Map<Item, Optional<Identifier>> TEXTURES = new HashMap<>();
    /** Bracer models only used for your own hands in first person (see {@link AccessoryModel#firstPersonArm}). */
    private static AccessoryModel firstPersonWide;
    private static AccessoryModel firstPersonSlim;

    private AccessoryRendering() {
    }

    public static void init() {
        for (AccessorySlot slot : AccessorySlot.values()) {
            ModelLayerRegistry.registerModelLayer(layer(slot, false), () -> AccessoryModel.createLayer(slot, false));
            ModelLayerRegistry.registerModelLayer(layer(slot, true), () -> AccessoryModel.createLayer(slot, true));
        }
        // Fabric hands every avatar renderer (players' and mannequins') over as the player type.
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new AccessoryLayer(avatar, context));
                firstPersonWide = bake(context, AccessorySlot.BRACERS, false, stack -> false);
                firstPersonSlim = bake(context, AccessorySlot.BRACERS, true, stack -> false);
            }
        });
        LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state ->
                texture(AccessorySlot.CLOAK, AccessoryRenderState.of(state).get(AccessorySlot.CLOAK)) == null);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SkyRpgItems.id("accessory_textures"),
                (ResourceManagerReloadListener) manager -> TEXTURES.clear());
    }

    static AccessoryModel bake(EntityRendererProvider.Context context, AccessorySlot slot, boolean slim, Predicate<ItemStack> wearsArmour) {
        return new AccessoryModel(context.bakeLayer(layer(slot, slim)), slim, slot, wearsArmour);
    }

    private static ModelLayerLocation layer(AccessorySlot slot, boolean slim) {
        return new ModelLayerLocation(SkyRpgItems.id("accessory_" + slot.id()), slim ? "slim" : "wide");
    }

    /** The worn texture of the accessory in this slot, or null if the stack doesn't belong there or has none. */
    static Identifier texture(AccessorySlot slot, ItemStack stack) {
        if (!slot.fits(stack)) {
            return null;
        }
        return TEXTURES.computeIfAbsent(stack.getItem(), item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item).withPath(path -> "textures/entity/accessory/" + path + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(id).map(resource -> id);
        }).orElse(null);
    }

    /** Cutout, both sides drawn (capes and cuffs are seen from inside), with the glint when enchanted, like armour. */
    static RenderType renderType(Identifier texture, ItemStack stack) {
        return stack.hasFoil() ? RenderTypes.armorCutoutNoCullGlint(texture) : RenderTypes.armorCutoutNoCull(texture);
    }

    /** Called after vanilla draws your own arm in first person: your bracers on it. */
    public static void submitFirstPersonBracer(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right) {
        LocalPlayer player = Minecraft.getInstance().player;
        AccessoryModel model = player != null && player.getSkin().model() == PlayerModelType.SLIM ? firstPersonSlim : firstPersonWide;
        if (player == null || model == null) {
            return;
        }
        ItemStack stack = Accessories.worn(player, AccessorySlot.BRACERS);
        Identifier texture = texture(AccessorySlot.BRACERS, stack);
        if (texture != null) {
            ModelPart arm = model.firstPersonArm(right);
            collector.submitModelPart(arm, poseStack, renderType(texture, stack), light, OverlayTexture.NO_OVERLAY, null);
        }
    }
}
