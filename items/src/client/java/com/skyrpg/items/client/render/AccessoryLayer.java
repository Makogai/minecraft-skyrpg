package com.skyrpg.items.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.items.accessory.AccessoryLoadout;
import com.skyrpg.items.accessory.AccessorySlot;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Draws the accessories a player wears (from {@link AccessoryRenderState}, so other players' too) on their
 * model: one {@link AccessoryModel} per slot and skin shape, textured per item. Hidden while the player is
 * invisible, like the cape; the cloak also hides under an elytra.
 */
final class AccessoryLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final EquipmentAssetManager equipmentAssets;
    private final Map<AccessorySlot, AccessoryModel> wide = new EnumMap<>(AccessorySlot.class);
    private final Map<AccessorySlot, AccessoryModel> slim = new EnumMap<>(AccessorySlot.class);

    AccessoryLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.equipmentAssets = context.getEquipmentAssets();
        for (AccessorySlot slot : AccessorySlot.values()) {
            wide.put(slot, AccessoryRendering.bake(context, slot, false, this::isArmour));
            slim.put(slot, AccessoryRendering.bake(context, slot, true, this::isArmour));
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        AccessoryLoadout loadout = AccessoryRenderState.of(state);
        if (state.isInvisible || loadout.isEmpty()) {
            return;
        }
        Map<AccessorySlot, AccessoryModel> models = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
        for (AccessorySlot slot : AccessorySlot.values()) {
            ItemStack stack = loadout.get(slot);
            Identifier texture = AccessoryRendering.texture(slot, stack);
            if (texture == null || slot == AccessorySlot.CLOAK && hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
                continue;
            }
            collector.submitModel(models.get(slot), state, poseStack, AccessoryRendering.renderType(texture, stack),
                    light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }

    /** A chest stack drawn as armour on the body (not an elytra, not a block on a mannequin). */
    private boolean isArmour(ItemStack chest) {
        return hasLayer(chest, EquipmentClientInfo.LayerType.HUMANOID);
    }

    /** Whether the stack's equipment asset has layers of this type, like {@code CapeLayer}'s check. */
    private boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType type) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.assetId().isPresent()
                && !equipmentAssets.get(equippable.assetId().get()).getLayers(type).isEmpty();
    }
}
