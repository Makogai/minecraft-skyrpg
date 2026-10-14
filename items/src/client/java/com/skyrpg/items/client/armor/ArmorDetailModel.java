package com.skyrpg.items.client.armor;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * One armour piece's raised detail for one set: the set's detail mesh with only that piece's boxes shown.
 * It is a player model, so the wearer's render state poses it exactly like the body and the armour under
 * it. Armour has the same shape on slim and wide players, so the detail does too.
 * <p>
 * Each instance shows one fixed piece, set here once: models are posed and drawn after every layer has
 * submitted, so anything toggled per submit would only show its last state.
 */
final class ArmorDetailModel extends PlayerModel {
    /** The armour pieces: the group their boxes hang in on each body part, and the slot they're worn in. */
    enum Piece {
        HELMET("helmet", EquipmentSlot.HEAD),
        CHEST("chest", EquipmentSlot.CHEST),
        LEGS("legs", EquipmentSlot.LEGS),
        BOOTS("boots", EquipmentSlot.FEET);

        final String group;
        final EquipmentSlot slot;

        Piece(String group, EquipmentSlot slot) {
            this.group = group;
            this.slot = slot;
        }
    }

    ArmorDetailModel(ModelPart root, Piece piece) {
        super(root, false);
        for (ModelPart part : List.of(head, body, rightArm, leftArm, rightLeg, leftLeg)) {
            for (Piece other : Piece.values()) {
                if (part.hasChild(other.group)) {
                    part.getChild(other.group).visible = other == piece;
                }
            }
        }
    }

    /** A player mesh without any cubes, for a set's detail to hang off (see {@link ArmorDetailMeshes}). */
    static MeshDefinition createEmptyMesh() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        mesh.getRoot().clearRecursively();
        return mesh;
    }
}
