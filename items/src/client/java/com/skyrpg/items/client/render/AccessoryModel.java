package com.skyrpg.items.client.render;

import com.skyrpg.items.accessory.AccessorySlot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * The 3D shape of one accessory slot, worn on the player model. Like vanilla's cape model it is a whole
 * player model with every vanilla cube removed, so its parts follow the wearer's pose; the slot's cubes hang
 * under body, arms and legs. All items of a slot share these cubes and their UV layout (the textures are
 * drawn by {@code tools/art/generate_accessories.py}, which has the same table); an item leaves the cubes it
 * doesn't use transparent.
 * <p>
 * Every slot is built twice: a {@code bare} group just outside the skin's overlay and an {@code armored} group
 * just outside chest armour (and its raised detail), so the two never z-fight. {@link #setupAnim} shows one of
 * them. It only reads the render state, because models are drawn later than they are submitted.
 * <p>
 * Distances from the skin, in model pixels (see docs/design/accessories.md, "Depth lanes"): amulet 0.35 / 1.45,
 * cloak 0.5 / 1.4, belt 0.55 / 1.2, bracers 0.35 / 1.25. Vanilla's are jacket 0.25, leggings 0.5, armour 1.0.
 */
public final class AccessoryModel extends PlayerModel {
    private static final String BARE = "bare";
    private static final String ARMORED = "armored";

    private final AccessorySlot slot;
    private final Predicate<ItemStack> wearsArmour;
    private final List<ModelPart> bare = new ArrayList<>();
    private final List<ModelPart> armored = new ArrayList<>();

    /** @param wearsArmour whether a chest stack is drawn as armour on the body (the armored fit is used then) */
    public AccessoryModel(ModelPart root, boolean slim, AccessorySlot slot, Predicate<ItemStack> wearsArmour) {
        super(root, slim);
        this.slot = slot;
        this.wearsArmour = wearsArmour;
        for (ModelPart part : List.of(body, rightArm, leftArm, rightLeg, leftLeg)) {
            if (part.hasChild(BARE)) {
                bare.add(part.getChild(BARE));
            }
            if (part.hasChild(ARMORED)) {
                armored.add(part.getChild(ARMORED));
            }
        }
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        boolean armour = wearsArmour.test(state.chestEquipment);
        bare.forEach(part -> part.visible = !armour);
        armored.forEach(part -> part.visible = armour);
        if (slot == AccessorySlot.CLOAK) {
            sway(body.getChild(armour ? ARMORED : BARE).getChild("cape"), state);
        }
    }

    /** Vanilla's cape swing (see {@code PlayerCapeModel}), damped: a cloak is heavier than a cape. */
    private static void sway(ModelPart cape, AvatarRenderState state) {
        float swing = 6.0f + 0.6f * (state.capeLean / 2.0f + state.capeFlap);
        float side = 0.6f * state.capeLean2 / 2.0f;
        cape.rotateBy(new Quaternionf()
                .rotateY((float) -Math.PI)
                .rotateX(swing * (float) (Math.PI / 180.0))
                .rotateZ(side * (float) (Math.PI / 180.0))
                .rotateY((180.0f - side) * (float) (Math.PI / 180.0)));
    }

    /**
     * The right or left arm in vanilla's first-person hand pose, showing the bare fit (no armour is drawn on
     * hands). Only for a model kept for first person: it isn't posed from a render state.
     */
    ModelPart firstPersonArm(boolean right) {
        bare.forEach(part -> part.visible = true);
        armored.forEach(part -> part.visible = false);
        ModelPart arm = right ? rightArm : leftArm;
        arm.resetPose();
        arm.zRot = right ? 0.1f : -0.1f;
        return arm;
    }

    // ------------------------------------------------------------------ meshes

    public static LayerDefinition createLayer(AccessorySlot slot, boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        PartDefinition root = mesh.getRoot().clearRecursively();
        for (boolean armour : new boolean[]{false, true}) {
            Groups groups = new Groups(root, armour ? ARMORED : BARE);
            switch (slot) {
                case AMULET -> amulet(groups, armour ? 1.45f : 0.35f);
                case CLOAK -> cloak(groups, armour ? 1.4f : 0.5f, slim, armour);
                case BELT -> belt(groups, armour ? 1.2f : 0.55f);
                case BRACERS -> bracers(groups, armour ? 1.25f : 0.35f, slim && !armour);
            }
        }
        return switch (slot) {
            case AMULET -> LayerDefinition.create(mesh, 32, 16);
            case CLOAK -> LayerDefinition.create(mesh, 64, 32);
            case BELT, BRACERS -> LayerDefinition.create(mesh, 32, 32);
        };
    }

    /** Cord round the neck (a shell over the upper body, mostly transparent) and a pendant resting on it. */
    private static void amulet(Groups g, float depth) {
        float front = -2 - depth;
        g.body.addOrReplaceChild("cord", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, 0, -2, 8, 5, 4, new CubeDeformation(depth, 0.1f, depth)), PartPose.ZERO);
        g.body.addOrReplaceChild("pendant", CubeListBuilder.create().texOffs(0, 9)
                .addBox(-2, 1, front - 0.925f, 4, 4, 1, new CubeDeformation(0, 0, -0.125f)), PartPose.ZERO);
    }

    /**
     * A cape from the top of the back (turned round like vanilla's, so its front texture faces out) under a
     * collar roll, a mantle with lapels and a toggle clasp on each, and shoulder drapes. With armour the drapes
     * are left out: the shoulders belong to the armour's pauldrons.
     */
    private static void cloak(Groups g, float depth, boolean slim, boolean armour) {
        float front = -2 - depth;
        g.body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5, 0, -1, 10, 16, 1), PartPose.offsetAndRotation(0, 0, 2 + depth + 0.05f, 0, (float) Math.PI, 0));
        g.body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(22, 0)
                .addBox(-4, 0, -2, 8, 5, 4, new CubeDeformation(depth, 0.15f, depth)), PartPose.ZERO);
        g.body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(22, 9)
                .addBox(-5, -0.5f, 1.8f + depth, 10, 2, 2, new CubeDeformation(0.1f, 0, 0)), PartPose.ZERO);
        g.body.addOrReplaceChild("clasps", CubeListBuilder.create().texOffs(46, 0)
                .addBox(-3, 0.5f, front - 0.5f, 1, 2, 1)
                .addBox(2, 0.5f, front - 0.5f, 1, 2, 1), PartPose.ZERO);
        if (!armour) {
            int width = slim ? 3 : 4;
            int u = slim ? 38 : 22;
            CubeDeformation drape = new CubeDeformation(depth, 0.15f, depth);
            g.rightArm.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(u, 13)
                    .addBox(slim ? -2 : -3, -2, -2, width, 3, 4, drape), PartPose.ZERO);
            g.leftArm.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(u, 13).mirror()
                    .addBox(-1, -2, -2, width, 3, 4, drape), PartPose.ZERO);
        }
    }

    /**
     * A band round the waist with a buckle in front, a knot on the right hip, a pouch (with a tool in it) on
     * the left, and a tail hanging from the knot that swings with the right leg.
     */
    private static void belt(Groups g, float depth) {
        float front = -2 - depth;
        g.body.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, 9.5f, -2, 8, 3, 4, new CubeDeformation(depth, 0, depth)), PartPose.ZERO);
        g.body.addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(0, 7)
                .addBox(-2, 9.5f, front - 0.925f, 4, 3, 1, new CubeDeformation(0, 0.2f, -0.125f)), PartPose.ZERO);
        g.body.addOrReplaceChild("knot", CubeListBuilder.create().texOffs(10, 7)
                .addBox(-4, 10, front - 0.925f, 2, 2, 1, new CubeDeformation(0.1f, 0.1f, -0.125f)), PartPose.ZERO);
        g.body.addOrReplaceChild("pouch", CubeListBuilder.create().texOffs(16, 7)
                .addBox(1, 10, front - 1.8f, 3, 3, 2, new CubeDeformation(0, 0, -0.25f)), PartPose.ZERO);
        g.body.addOrReplaceChild("tool", CubeListBuilder.create()
                .texOffs(26, 7).addBox(2, 7.5f, front - 1.3f, 1, 4, 1, new CubeDeformation(-0.25f, 0, -0.25f))
                .texOffs(0, 12).addBox(1, 7, front - 1.3f, 3, 1, 1, new CubeDeformation(0, 0, -0.1f)), PartPose.ZERO);
        g.rightLeg.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 12)
                .addBox(-2, 0, front - 0.8f, 2, 4, 1, new CubeDeformation(0, 0, -0.25f)), PartPose.ZERO);
    }

    /**
     * A cuff round each forearm and a raised guard plate on its outer side. Armour sleeves are wide even on
     * slim players, so only the bare fit has a slim (3 wide) cuff, with its own UVs.
     */
    private static void bracers(Groups g, float depth, boolean thin) {
        int width = thin ? 3 : 4;
        int v = thin ? 8 : 0;
        CubeDeformation cuff = new CubeDeformation(depth, 0.25f, depth);
        CubeDeformation plate = new CubeDeformation(-0.125f, 0, 0);
        float outer = width - 1 + depth;
        g.rightArm.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, v)
                .addBox(1 - width, 5, -2, width, 4, 4, cuff), PartPose.ZERO);
        g.rightArm.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(16, 0)
                .addBox(-outer - 0.925f, 4.5f, -1.5f, 1, 5, 3, plate), PartPose.ZERO);
        g.leftArm.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, v).mirror()
                .addBox(-1, 5, -2, width, 4, 4, cuff), PartPose.ZERO);
        g.leftArm.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(16, 0).mirror()
                .addBox(outer - 0.075f, 4.5f, -1.5f, 1, 5, 3, plate), PartPose.ZERO);
    }

    /** One fit's group under each part the slots use. */
    private record Groups(PartDefinition body, PartDefinition rightArm, PartDefinition leftArm, PartDefinition rightLeg) {
        Groups(PartDefinition root, String name) {
            this(group(root, "body", name), group(root, "right_arm", name), group(root, "left_arm", name), group(root, "right_leg", name));
        }

        private static PartDefinition group(PartDefinition root, String part, String name) {
            return root.getChild(part).addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
        }
    }
}
