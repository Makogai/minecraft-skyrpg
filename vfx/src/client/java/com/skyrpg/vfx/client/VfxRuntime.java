package com.skyrpg.vfx.client;

import com.skyrpg.vfx.SkyRpgVfx;
import com.skyrpg.vfx.client.particle.VfxParticle;
import com.skyrpg.vfx.data.Anchor;
import com.skyrpg.vfx.data.ColorSpec;
import com.skyrpg.vfx.data.Condition;
import com.skyrpg.vfx.data.EffectDef;
import com.skyrpg.vfx.data.EmitterDef;
import com.skyrpg.vfx.data.VfxLibrary;
import com.skyrpg.vfx.particle.VfxParticleOptions;
import com.skyrpg.vfx.particle.VfxParticles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Runs effects on the client: plays them (at once for effects that only act on their first tick, else as a running
 * instance stepped every tick), turns emitters into particles, and keeps everything inside the quality setting's
 * budgets. Client thread only.
 *
 * <p>Budgets: a per-tick cap on new particles (separate for ambient effects from bindings and for one-shots, so busy
 * gear never starves a level-up), a cap on live VFX particles (counted by the particles themselves as they tick),
 * a draw range per effect, and a cap on running instances. With the setting on Off nothing here runs at all.
 */
public final class VfxRuntime {
    private static final int MAX_INSTANCES = 256;
    private static final float TAU = (float) (Math.PI * 2);
    private static final RandomGenerator RANDOM = new SplittableRandom();

    private static final ArrayList<Instance> INSTANCES = new ArrayList<>();
    private static final ArrayList<Instance> PENDING = new ArrayList<>();
    /** Reused instances for effects that finish on their first tick, one per nesting depth. */
    private static final Instance[] SCRATCH = new Instance[VfxLibrary.MAX_DEPTH + 2];
    private static final float[] SHAPE = new float[6];
    private static final float[] LOCAL = new float[3];
    private static final Set<Identifier> WARNED = new HashSet<>();

    private static VfxLibrary library = VfxLibrary.EMPTY;
    private static boolean synced;

    private static VfxQuality quality = VfxQuality.HIGH;
    private static float scale = 1;
    private static int ambientBudget;
    private static int eventBudget;
    private static int alive;

    private VfxRuntime() {
    }

    // ------------------------------------------------------------------------------------------------ library

    public static VfxLibrary library() {
        return library;
    }

    static void setLibrary(VfxLibrary fresh) {
        library = fresh;
        synced = true;
        WARNED.clear();
        // Running instances point at the old definitions; let them finish rather than mixing old and new.
    }

    static EffectDef find(Identifier id) {
        EffectDef def = library.effect(id);
        if (def == null && synced && WARNED.add(id)) {
            SkyRpgVfx.LOGGER.warn("VfxClient.play: unknown effect {}", id);
        }
        return def;
    }

    static void clear() {
        INSTANCES.clear();
        PENDING.clear();
        Arrays.fill(SCRATCH, null);
        alive = 0;
    }

    static void reset() {
        clear();
        library = VfxLibrary.EMPTY;
        synced = false;
    }

    static VfxQuality quality() {
        return quality;
    }

    // ------------------------------------------------------------------------------------------------ ticking

    static void tick(Minecraft minecraft) {
        quality = VfxQuality.current();
        if (quality.off() || minecraft.level == null) {
            if (!INSTANCES.isEmpty() || !PENDING.isEmpty()) {
                clear();
            }
            VfxParticle.takeTicked();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        alive = VfxParticle.takeTicked();
        float vanilla = VfxQuality.vanillaScale(minecraft);
        scale = quality.countScale() * vanilla;
        ambientBudget = (int) (quality.particlesPerTick() * Math.max(0.35f, vanilla));
        eventBudget = ambientBudget;

        INSTANCES.addAll(PENDING);
        PENDING.clear();
        int kept = 0;
        for (int i = 0, size = INSTANCES.size(); i < size; i++) {
            Instance instance = INSTANCES.get(i);
            if (instance.step(minecraft)) {
                INSTANCES.set(kept++, instance);
            }
        }
        INSTANCES.subList(kept, INSTANCES.size()).clear();

        BindingRunner.tick(minecraft, quality, vanilla);
    }

    // ------------------------------------------------------------------------------------------------ playing

    /**
     * Plays {@code def} on {@code entity} (null for a plain position at x, y, z). {@code hand}: 0 main, 1 off (for the
     * "hand" anchor). Ambient plays (from bindings) give way first when budgets run low.
     */
    static void play(EffectDef def, Entity entity, double x, double y, double z, int hand, int depth, boolean ambient) {
        if (def == null || quality.off() || depth > VfxLibrary.MAX_DEPTH) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (entity != null) {
            if (entity.isRemoved()) {
                entity = null;
            } else {
                x = entity.getX();
                y = entity.getY();
                z = entity.getZ();
            }
        }
        if (depth == 0) {
            Vec3 camera = minecraft.gameRenderer.mainCamera().position();
            double range = def.range() * quality.rangeScale();
            double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                return;
            }
            int cap = ambient ? quality.maxAlive() : quality.maxAlive() * 13 / 10;
            if (alive >= cap || (ambient ? ambientBudget : eventBudget) <= 0) {
                return;
            }
        }
        if (def.instant()) {
            Instance scratch = SCRATCH[depth];
            if (scratch == null) {
                scratch = SCRATCH[depth] = new Instance();
            }
            scratch.start(def, entity, x, y, z, hand, depth, ambient);
            scratch.step(minecraft);
            scratch.entity = null; // don't keep entities alive from a scratch slot
            return;
        }
        if (INSTANCES.size() + PENDING.size() >= MAX_INSTANCES) {
            return;
        }
        Instance instance = new Instance();
        instance.start(def, entity, x, y, z, hand, depth, ambient);
        if (instance.step(minecraft)) {
            PENDING.add(instance);
        }
    }

    /** A running effect. */
    private static final class Instance {
        EffectDef def;
        Entity entity;
        boolean anchored;
        double x, y, z;
        float width = 1, height = 1, eyes = 0.5f, yaw;
        boolean mainRight = true;
        int hand;
        int depth;
        boolean ambient;
        int age;
        float[] carry = new float[8];
        long skip;

        void start(EffectDef def, Entity entity, double x, double y, double z, int hand, int depth, boolean ambient) {
            this.def = def;
            this.entity = entity;
            this.anchored = entity != null;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hand = hand;
            this.depth = depth;
            this.ambient = ambient;
            this.age = 0;
            this.yaw = 0;
            this.width = this.height = 1;
            this.eyes = 0.5f;
            this.mainRight = true;
            EmitterDef[] emitters = def.emitters();
            if (carry.length < emitters.length) {
                carry = new float[emitters.length];
            } else {
                Arrays.fill(carry, 0, emitters.length, 0);
            }
            skip = 0;
            for (int i = 0; i < Math.min(64, emitters.length); i++) {
                if (emitters[i].chance < 1 && RANDOM.nextFloat() >= emitters[i].chance) {
                    skip |= 1L << i;
                }
            }
        }

        /** Runs this tick's emitters; false once the effect is over. */
        boolean step(Minecraft minecraft) {
            if (entity != null) {
                if (entity.isRemoved()) {
                    entity = null; // finish where it was
                } else {
                    x = entity.getX();
                    y = entity.getY();
                    z = entity.getZ();
                    width = entity.getBbWidth();
                    height = entity.getBbHeight();
                    eyes = entity.getEyeHeight();
                    yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
                    mainRight = !(entity instanceof LivingEntity living) || living.getMainArm() == HumanoidArm.RIGHT;
                }
            }
            int state = conditions(entity, minecraft.level);
            boolean firstPerson = entity != null && entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
            EmitterDef[] emitters = def.emitters();
            for (int i = 0; i < emitters.length; i++) {
                EmitterDef e = emitters[i];
                if (age < e.delay || age >= e.end() || (i < 64 && (skip & (1L << i)) != 0)) {
                    continue;
                }
                if (!Condition.test(state, e.required, e.forbidden) || (firstPerson && e.anchor.nearFace() && !e.firstPerson)) {
                    continue;
                }
                int t = age - e.delay;
                float factor = e.mode == EmitterDef.OptionMode.EFFECT ? 1 : scale;
                float want = 0;
                if (t == 0 || (e.every > 0 && t % e.every == 0)) {
                    want += e.count.sample(RANDOM) * factor;
                }
                if (e.rate > 0) {
                    want += e.rate * factor;
                }
                float total = carry[i] + want;
                int n = (int) total;
                total -= n;
                if (age + 1 >= e.end()) {
                    // Last tick: settle the remainder by chance, so small rates still show up sometimes.
                    if (total > 0 && RANDOM.nextFloat() < total) {
                        n++;
                    }
                    total = 0;
                }
                carry[i] = total;
                if (n > 0) {
                    emit(this, e, n, minecraft);
                }
            }
            age++;
            return age < def.length();
        }
    }

    // ------------------------------------------------------------------------------------------------ emitting

    private static void emit(Instance at, EmitterDef e, int n, Minecraft minecraft) {
        Entity entity = at.entity;
        float yaw = at.yaw * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        // The anchor's frame: x to the right, z forwards (Minecraft yaw 0 faces +z).
        float rx = -cos, rz = -sin, fx = -sin, fz = cos;
        anchorLocal(e, at);
        float lx = LOCAL[0], ly = LOCAL[1], lz = LOCAL[2];
        double ax = at.x + lx * rx + lz * fx;
        double ay = at.y + ly;
        double az = at.z + lx * rz + lz * fz;
        double evx = 0, evy = 0, evz = 0;
        if (entity != null && e.inherit != 0) {
            evx = (entity.getX() - entity.xo) * e.inherit;
            evy = (entity.getY() - entity.yo) * e.inherit;
            evz = (entity.getZ() - entity.zo) * e.inherit;
        }
        float w = at.anchored ? at.width : 1, h = at.anchored ? at.height : 1;
        float phase = Float.isNaN(e.shape.phase) ? RANDOM.nextFloat() * TAU : e.shape.phase;
        boolean attachSub = entity != null && !e.hasShape && e.offsetX == 0 && e.offsetY == 0 && e.offsetZ == 0;

        for (int i = 0; i < n; i++) {
            if ((at.ambient ? ambientBudget : eventBudget) <= 0) {
                return;
            }
            e.shape.sample(RANDOM, i, n, phase, w, h, SHAPE);
            float sx = SHAPE[0], sy = SHAPE[1], sz = SHAPE[2], ox = SHAPE[3], oy = SHAPE[4], oz = SHAPE[5];
            double px = ax + sx * rx + sz * fx;
            double py = ay + sy;
            double pz = az + sx * rz + sz * fz;
            if (e.mode == EmitterDef.OptionMode.EFFECT) {
                if (e.effect != null) {
                    play(e.effect, attachSub ? entity : null, px, py, pz, at.hand, at.depth + 1, at.ambient);
                }
                continue;
            }
            float speed = e.speed.sample(RANDOM);
            float spread = e.spread;
            float jx = spread == 0 ? 0 : (RANDOM.nextFloat() * 2 - 1) * spread;
            float jy = spread == 0 ? 0 : (RANDOM.nextFloat() * 2 - 1) * spread;
            float jz = spread == 0 ? 0 : (RANDOM.nextFloat() * 2 - 1) * spread;
            float lvx = e.velocityX + ox * speed + jx;
            float lvy = e.velocityY + oy * speed + jy + e.rise.sample(RANDOM);
            float lvz = e.velocityZ + oz * speed + jz;
            double vx = lvx * rx + lvz * fx + evx;
            double vy = lvy + evy;
            double vz = lvx * rz + lvz * fz + evz;
            spawn(e, minecraft, px, py, pz, vx, vy, vz, entity, ax, az, speed, at.ambient);
        }
    }

    /** The emitter's anchor point plus offset, in the anchor's frame relative to the feet, into LOCAL. */
    private static void anchorLocal(EmitterDef e, Instance at) {
        float lx = e.offsetX, ly = e.offsetY, lz = e.offsetZ;
        if (at.anchored) {
            float w = at.width, h = at.height;
            Anchor anchor = e.anchor;
            switch (anchor) {
                case FEET -> {
                }
                case CENTER -> ly += h * 0.5f;
                case HEAD -> ly += h;
                case EYES -> ly += at.eyes;
                case BACK -> {
                    ly += h * 0.7f;
                    lz -= w * 0.55f;
                }
                default -> {
                    boolean right = switch (anchor) {
                        case RIGHT_HAND -> true;
                        case LEFT_HAND -> false;
                        case OFF_HAND -> !at.mainRight;
                        case HAND -> at.hand == 1 ? !at.mainRight : at.mainRight;
                        default -> at.mainRight;
                    };
                    lx += (right ? 1 : -1) * w * 0.63f;
                    ly += h * 0.58f;
                    lz += w * 0.5f;
                }
            }
        }
        LOCAL[0] = lx;
        LOCAL[1] = ly;
        LOCAL[2] = lz;
    }

    private static void spawn(EmitterDef e, Minecraft minecraft, double x, double y, double z, double vx, double vy, double vz, Entity entity,
                              double centerX, double centerZ, float speed, boolean ambient) {
        ParticleOptions options = options(e);
        if (options == null) {
            return;
        }
        if (ambient) {
            ambientBudget--;
        } else {
            eventBudget--;
        }
        alive++;
        boolean orbiting = e.orbitSpeed != 0;
        Particle particle = minecraft.particleEngine.createParticle(options, x, y, z, orbiting ? 0 : vx, vy, orbiting ? 0 : vz);
        if (particle instanceof VfxParticle vfx) {
            if (orbiting) {
                vfx.orbit(centerX, centerZ, e.orbitSpeed, speed, entity);
            } else if (e.follow && entity != null) {
                vfx.follow(entity);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions options(EmitterDef e) {
        switch (e.mode) {
            case VFX -> {
                int color = e.color.sample(RANDOM);
                int start = ColorSpec.withAlpha(color, e.alphaStart);
                int end = ColorSpec.withAlpha(e.colorEnd != null ? e.colorEnd.sample(RANDOM) : color, e.alphaEnd);
                float jitter = e.sizeJitter > 0 ? 1 + (RANDOM.nextFloat() * 2 - 1) * e.sizeJitter : 1;
                return new VfxParticleOptions(VfxParticles.type(e.kind), start, end, e.sizeStart * jitter, e.sizeEnd * jitter,
                        Math.max(1, Math.round(e.lifetime.sample(RANDOM))), e.gravity.sample(RANDOM), e.drag, e.spin.sample(RANDOM), e.sway,
                        e.fadeIn, VfxParticleOptions.flags(e.fullbright, e.collide, e.orient));
            }
            case FIXED -> {
                return e.fixed;
            }
            case DUST -> {
                return new DustParticleOptions(e.color.sample(RANDOM) & 0xFFFFFF, Mth.clamp(e.sizeStart, 0.01f, 4f));
            }
            case DUST_TRANSITION -> {
                int from = e.color.sample(RANDOM);
                int to = e.colorEnd != null ? e.colorEnd.sample(RANDOM) : from;
                return new DustColorTransitionOptions(from & 0xFFFFFF, to & 0xFFFFFF, Mth.clamp(e.sizeStart, 0.01f, 4f));
            }
            case COLOR -> {
                return ColorParticleOption.create((ParticleType<ColorParticleOption>) e.type, ColorSpec.withAlpha(e.color.sample(RANDOM), e.alphaStart));
            }
            case SPELL -> {
                return SpellParticleOption.create((ParticleType<SpellParticleOption>) e.type, e.color.sample(RANDOM), 1f);
            }
            default -> {
                return null;
            }
        }
    }

    /** The condition bits that hold for this entity (or a plain position when null) right now. */
    static int conditions(Entity entity, Level level) {
        int state = level.isDarkOutside() ? Condition.NIGHT.bit() : Condition.DAY.bit();
        if (level.isRaining()) {
            state |= Condition.RAINING.bit();
        }
        if (entity == null) {
            return state | Condition.STILL.bit() | Condition.ON_GROUND.bit();
        }
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        state |= dx * dx + dz * dz > 0.0004 ? Condition.MOVING.bit() : Condition.STILL.bit();
        if (entity.isCrouching()) {
            state |= Condition.SNEAKING.bit();
        }
        if (entity.isSprinting()) {
            state |= Condition.SPRINTING.bit();
        }
        boolean water = entity.isInWater();
        if (water) {
            state |= Condition.IN_WATER.bit();
        }
        if (entity.onGround()) {
            state |= Condition.ON_GROUND.bit();
        } else if (!water) {
            state |= Condition.AIRBORNE.bit();
        }
        return state;
    }

    /** For /vfxdebug-style readouts: running instances and the live particle estimate. */
    public static String stats() {
        return "instances " + INSTANCES.size() + ", particles ~" + alive + ", quality " + quality.name();
    }
}
