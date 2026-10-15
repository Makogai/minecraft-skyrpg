package com.skyrpg.pets.client.model;

import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * One pet kind's baked 3D model and its animations. The model is shared by every player's pet of that kind: all the
 * per-pet numbers come in through {@link Anim} (the model's "render state"), applied by {@link #setupAnim} when it is
 * drawn, so two players with the same pet never disturb each other.
 */
public final class PetModel extends Model<PetModel.Anim> {
    /**
     * What a pet looks like this frame.
     *
     * @param rarity    the rarity form (which accessories show)
     * @param age       time in ticks (with partial ticks); drives every idle animation
     * @param seed      0..1, so pets of one kind don't blink and flap in lockstep
     * @param headYaw   radians the head is turned (lagged behind where the player looks)
     * @param headPitch radians the head is tipped
     * @param lagX      how far (in blocks, model space) the pet trails behind its resting spot
     * @param lagY      same, vertically
     * @param lagZ      same, front to back
     */
    public record Anim(PetRarity rarity, float age, float seed, float headYaw, float headPitch, float lagX, float lagY, float lagZ) {
        float speed() {
            return Mth.clamp(Mth.sqrt(lagX * lagX + lagY * lagY + lagZ * lagZ) * 3f, 0f, 1f);
        }
    }

    private static final Map<PetType, PetModel> MODELS = new EnumMap<>(PetType.class);

    private final String name;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final String[] rarityParts;
    private final int[] rarityMasks;
    private final Identifier[] textures = new Identifier[PetRarity.values().length];
    private final Identifier[] glowTextures = new Identifier[PetRarity.values().length];
    private final float scale;
    private final boolean translucent;

    private PetModel(ModelPart root, String name, float scale, boolean translucent, int glowMask, String[] rarityParts, int[] rarityMasks) {
        super(root, RenderTypes::entityCutout);
        this.name = name;
        this.scale = scale;
        this.translucent = translucent;
        this.rarityParts = rarityParts;
        this.rarityMasks = rarityMasks;
        collect(root);
        for (PetRarity r : PetRarity.values()) {
            String base = "textures/entity/pet/" + name + "_" + r.name().toLowerCase(java.util.Locale.ROOT);
            textures[r.ordinal()] = Identifier.fromNamespaceAndPath("skyrpg_pets", base + ".png");
            glowTextures[r.ordinal()] = (glowMask >> r.ordinal() & 1) != 0 ? Identifier.fromNamespaceAndPath("skyrpg_pets", base + "_glow.png") : null;
        }
    }

    private void collect(ModelPart part) {
        for (String child : childNames(part)) {
            ModelPart c = part.getChild(child);
            parts.putIfAbsent(child, c);
            collect(c);
        }
    }

    private static java.util.List<String> childNames(ModelPart part) {
        // ModelPart exposes children only by name; the layouts' part names are a fixed set, so probe them.
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String n : ALL_NAMES) {
            if (part.hasChild(n)) {
                out.add(n);
            }
        }
        return out;
    }

    private static final String[] ALL_NAMES = {"pet", "body", "head", "lids", "helmet", "goggles", "crown", "paw_r", "paw_l", "pick", "antenna_r",
            "antenna_l", "flower", "halo", "wing_r", "wing_l", "scarf", "scarf_tail", "tuft_r", "tuft_l", "sprig", "monocle", "amulet", "ear_r",
            "ear_l", "starfish", "held", "held_x1", "held_x234", "held_x5", "foot_r", "foot_l", "tail", "leg_f_r", "leg_f_l", "leg_b_r", "leg_b_l",
            "collar", "collar_x2", "collar_x34", "collar_x5", "stem", "curl", "leaf", "arm_r", "arm_l", "wisp"};

    /** The model for a pet kind (baked on first use). */
    public static PetModel of(PetType type) {
        return MODELS.computeIfAbsent(type, PetModel::bake);
    }

    private static PetModel bake(PetType type) {
        return switch (type) {
            case MOLE -> new PetModel(MolePetLayout.create().bakeRoot(), MolePetLayout.NAME, MolePetLayout.SCALE, MolePetLayout.TRANSLUCENT,
                    MolePetLayout.GLOW_RARITIES, MolePetLayout.RARITY_PARTS, MolePetLayout.RARITY_MASKS);
            case BEE -> new PetModel(BeePetLayout.create().bakeRoot(), BeePetLayout.NAME, BeePetLayout.SCALE, BeePetLayout.TRANSLUCENT,
                    BeePetLayout.GLOW_RARITIES, BeePetLayout.RARITY_PARTS, BeePetLayout.RARITY_MASKS);
            case OWL -> new PetModel(OwlPetLayout.create().bakeRoot(), OwlPetLayout.NAME, OwlPetLayout.SCALE, OwlPetLayout.TRANSLUCENT,
                    OwlPetLayout.GLOW_RARITIES, OwlPetLayout.RARITY_PARTS, OwlPetLayout.RARITY_MASKS);
            case PUP -> new PetModel(PupPetLayout.create().bakeRoot(), PupPetLayout.NAME, PupPetLayout.SCALE, PupPetLayout.TRANSLUCENT,
                    PupPetLayout.GLOW_RARITIES, PupPetLayout.RARITY_PARTS, PupPetLayout.RARITY_MASKS);
            case OTTER -> new PetModel(OtterPetLayout.create().bakeRoot(), OtterPetLayout.NAME, OtterPetLayout.SCALE, OtterPetLayout.TRANSLUCENT,
                    OtterPetLayout.GLOW_RARITIES, OtterPetLayout.RARITY_PARTS, OtterPetLayout.RARITY_MASKS);
            case JACK -> new PetModel(JackPetLayout.create().bakeRoot(), JackPetLayout.NAME, JackPetLayout.SCALE, JackPetLayout.TRANSLUCENT,
                    JackPetLayout.GLOW_RARITIES, JackPetLayout.RARITY_PARTS, JackPetLayout.RARITY_MASKS);
        };
    }

    /** Model pixels to player model pixels. */
    public float scale() {
        return scale;
    }

    public RenderType renderType(PetRarity rarity) {
        Identifier tex = textures[rarity.ordinal()];
        return translucent ? RenderTypes.entityTranslucent(tex) : RenderTypes.entityCutout(tex);
    }

    public Identifier texture(PetRarity rarity) {
        return textures[rarity.ordinal()];
    }

    /** The glow layer's texture, or null if this rarity has no glow. */
    public Identifier glowTexture(PetRarity rarity) {
        return glowTextures[rarity.ordinal()];
    }

    public boolean translucent() {
        return translucent;
    }

    private float[] fit;

    /**
     * The model's size and middle at rest, in blocks and the model's own space (before {@link #scale()}): {largest extent, x, y, z}.
     * For menu previews, which draw the model at whatever size fits their box.
     */
    public float[] fit() {
        if (fit == null) {
            root.setPos(0f, 0f, 0f);
            root.setRotation(0f, 0f, 0f);
            root.xScale = root.yScale = root.zScale = 1f;
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            root.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
                for (int i = 0; i < 8; i++) {
                    Vector3f p = pose.pose().transformPosition((i & 1) == 0 ? cube.minX / 16f : cube.maxX / 16f,
                            (i & 2) == 0 ? cube.minY / 16f : cube.maxY / 16f, (i & 4) == 0 ? cube.minZ / 16f : cube.maxZ / 16f, new Vector3f());
                    lo[0] = Math.min(lo[0], p.x);
                    lo[1] = Math.min(lo[1], p.y);
                    lo[2] = Math.min(lo[2], p.z);
                    hi[0] = Math.max(hi[0], p.x);
                    hi[1] = Math.max(hi[1], p.y);
                    hi[2] = Math.max(hi[2], p.z);
                }
            });
            resetPose();
            float extent = Math.max(0.05f, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])));
            fit = new float[]{extent, (lo[0] + hi[0]) / 2f, (lo[1] + hi[1]) / 2f, (lo[2] + hi[2]) / 2f};
        }
        return fit;
    }

    /** The fullbright glow layer's render type, or null if this rarity has no glow. */
    public RenderType glowType(PetRarity rarity) {
        Identifier tex = glowTextures[rarity.ordinal()];
        return tex == null ? null : RenderTypes.eyes(tex);
    }

    // ---------------------------------------------------------------- animation

    private void rot(String part, float x, float y, float z) {
        ModelPart p = parts.get(part);
        if (p != null) {
            p.offsetRotation(new Vector3f(x, y, z));
        }
    }

    private void move(String part, float x, float y, float z) {
        ModelPart p = parts.get(part);
        if (p != null) {
            p.offsetPos(new Vector3f(x, y, z));
        }
    }

    private void show(String part, boolean visible) {
        ModelPart p = parts.get(part);
        if (p != null) {
            p.visible = visible;
        }
    }

    /** A short pulse (0..1) that happens every so often, different for each pet and side. */
    private static float pulse(float age, float seed, float period, float length, float offset) {
        float t = (age + (seed + offset) * period) % period;
        if (t >= length) {
            return 0f;
        }
        return Mth.sin(t / length * Mth.PI);
    }

    @Override
    public void setupAnim(Anim a) {
        resetPose();
        float age = a.age();
        float seed = a.seed();
        float speed = a.speed();
        int rarity = a.rarity().ordinal();
        for (int i = 0; i < rarityParts.length; i++) {
            show(rarityParts[i], (rarityMasks[i] >> rarity & 1) != 0);
        }
        // the eyelids are only there while blinking
        show("lids", pulse(age, seed, 88f, 4f, 0f) > 0.2f);

        // tilt into the direction of travel, bob a little
        rot("pet", Mth.clamp(a.lagZ() * 0.9f, -0.5f, 0.5f), 0f, Mth.clamp(a.lagX() * 0.9f, -0.5f, 0.5f));
        // the head looks where the owner looks
        float yaw = a.headYaw();
        float pitch = a.headPitch();
        if (name.equals("owl")) {
            yaw *= 1.4f;
        }
        rot("head", pitch + Mth.sin(age * 0.07f + seed * 6f) * 0.03f, yaw, 0f);
        // ear twitches: now and then one ear flicks
        float twitchR = pulse(age, seed, 70f, 6f, 0f);
        float twitchL = pulse(age, seed, 97f, 6f, 0.5f);

        switch (name) {
            case "mole" -> {
                rot("paw_r", Mth.sin(age * 0.12f + seed * 6f) * 0.18f, 0f, 0f);
                rot("paw_l", Mth.sin(age * 0.12f + 2f + seed * 6f) * 0.18f, 0f, 0f);
                rot("head", Mth.sin(age * 0.35f) * 0.04f * (1f - speed), 0f, 0f);
                rot("helmet", 0f, 0f, Mth.sin(age * 0.09f) * 0.015f);
            }
            case "bee" -> {
                float flap = Mth.sin(age * (2.4f + speed * 0.8f) + seed * 6f) * (0.5f + speed * 0.15f);
                rot("wing_r", 0f, 0f, flap);
                rot("wing_l", 0f, 0f, -flap);
                rot("antenna_r", Mth.sin(age * 0.2f) * 0.1f, 0f, Mth.sin(age * 0.17f) * 0.08f);
                rot("antenna_l", Mth.sin(age * 0.2f + 1f) * 0.1f, 0f, -Mth.sin(age * 0.17f + 1f) * 0.08f);
                rot("scarf_tail", 0f, 0f, Mth.sin(age * 0.25f) * 0.2f);
                rot("halo", 0f, Mth.sin(age * 0.05f) * 0.1f, 0f);
            }
            case "owl" -> {
                float flutter = speed * Mth.sin(age * 1.4f) * 0.4f + Mth.sin(age * 0.11f + seed * 6f) * 0.04f;
                rot("wing_r", 0f, 0f, flutter);
                rot("wing_l", 0f, 0f, -flutter);
                rot("tuft_r", 0f, 0f, -twitchR * 0.35f);
                rot("tuft_l", 0f, 0f, twitchL * 0.35f);
                rot("sprig", 0f, 0f, Mth.sin(age * 0.13f) * 0.1f);
            }
            case "pup" -> {
                // a wag that gets frantic when the pet is dragged along
                float wag = Mth.sin(age * (0.55f + speed * 0.3f)) * (0.45f + speed * 0.25f);
                rot("tail", Mth.sin(age * 0.11f) * 0.05f, wag, 0f);
                // ears: floppy sway with the lean, plus the odd twitch
                float flop = Mth.sin(age * 0.09f + seed * 6f) * 0.06f + speed * 0.25f;
                rot("ear_r", twitchR * 0.4f, 0f, -flop - twitchR * 0.3f);
                rot("ear_l", twitchL * 0.4f, 0f, flop + twitchL * 0.3f);
                float legs = Mth.sin(age * 0.2f + seed * 6f) * (0.08f + speed * 0.4f);
                rot("leg_f_r", legs, 0f, 0f);
                rot("leg_b_l", legs, 0f, 0f);
                rot("leg_f_l", -legs, 0f, 0f);
                rot("leg_b_r", -legs, 0f, 0f);
            }
            case "otter" -> {
                rot("tail", Mth.sin(age * 0.15f) * 0.08f, Mth.sin(age * 0.2f + seed * 6f) * (0.25f + speed * 0.2f), 0f);
                rot("ear_r", twitchR * 0.3f, 0f, -twitchR * 0.4f);
                rot("ear_l", twitchL * 0.3f, 0f, twitchL * 0.4f);
                rot("paw_r", Mth.sin(age * 0.1f) * 0.05f, 0f, 0f);
                rot("paw_l", Mth.sin(age * 0.1f + 1f) * 0.05f, 0f, 0f);
                move("held", 0f, Mth.sin(age * 0.1f) * 0.1f, 0f);
                float paddle = Mth.sin(age * 0.25f) * (0.12f + speed * 0.3f);
                rot("foot_r", paddle, 0f, 0f);
                rot("foot_l", -paddle, 0f, 0f);
            }
            case "jack" -> {
                // the pumpkin bobs and sways; the vine arms wave, the curl bounces, the wisp flickers beside it
                move("head", 0f, Mth.sin(age * 0.12f + seed * 6f) * 0.5f, 0f);
                rot("head", 0f, 0f, Mth.sin(age * 0.06f + seed * 6f) * 0.08f);
                rot("stem", 0f, 0f, Mth.sin(age * 0.09f) * 0.06f);
                rot("curl", 0f, 0f, Mth.sin(age * 0.2f) * 0.15f);
                rot("leaf", 0f, 0f, Mth.sin(age * 0.13f + 1f) * 0.08f);
                rot("arm_r", 0f, 0f, Mth.sin(age * 0.15f + seed * 6f) * 0.3f);
                rot("arm_l", 0f, 0f, -Mth.sin(age * 0.15f + 1f + seed * 6f) * 0.3f);
                move("wisp", 0f, Mth.sin(age * 0.2f) * 0.7f, 0f);
                rot("wisp", 0f, age * 0.05f, Mth.sin(age * 0.45f) * 0.1f);
                rot("halo", 0f, Mth.sin(age * 0.05f) * 0.1f, 0f);
            }
            default -> {
            }
        }
    }
}
