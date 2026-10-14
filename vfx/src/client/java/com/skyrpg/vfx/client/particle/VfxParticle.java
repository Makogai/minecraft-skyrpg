package com.skyrpg.vfx.client.particle;

import com.skyrpg.vfx.particle.Orient;
import com.skyrpg.vfx.particle.VfxParticleKind;
import com.skyrpg.vfx.particle.VfxParticleOptions;
import java.util.Optional;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One SkyRPG VFX particle: a camera-facing (or flat, or streak-aligned) quad that blends its colour and size over
 * its life, with its own gravity, drag, spin and sway, optionally fullbright. The VFX runtime can also make it follow
 * an entity or orbit around one.
 */
public class VfxParticle extends SingleQuadParticle {
    /** Hard ceiling on live VFX particles, enforced by the particle engine itself (a backstop for the budget). */
    private static final Optional<ParticleLimit> LIMIT = Optional.of(new ParticleLimit(6000));
    private static final float TAU = (float) (Math.PI * 2);

    // Render scratch (render thread only).
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector3f AXIS_X = new Vector3f();
    private static final Vector3f AXIS_Y = new Vector3f();

    /** How many VFX particles ticked since the runtime last asked: the number alive, without keeping a list. */
    private static int ticked;

    private final VfxParticleKind kind;
    private final FabricSpriteSet sprites;
    private final int colorStart;
    private final int colorEnd;
    private final float sizeStart;
    private final float sizeEnd;
    private final float gravityScale;
    private final float keep;
    private final float spin;
    private final float sway;
    private final float fadeIn;
    private final boolean fullbright;
    private final Orient orient;
    private final float seed;
    private float renderSize;
    private float streakAngle;

    private Entity follow;
    private double followX, followY, followZ;

    private boolean orbit;
    private double centerX, centerZ;
    private float orbitRadius, orbitAngle, orbitSpeed, radialSpeed;

    public VfxParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, VfxParticleOptions options,
                       FabricSpriteSet sprites, VfxParticleKind kind) {
        super(level, x, y, z, sprites.first());
        this.kind = kind;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.colorStart = options.color();
        this.colorEnd = options.colorEnd();
        this.sizeStart = options.size();
        this.sizeEnd = options.sizeEnd();
        this.lifetime = Math.max(1, options.lifetime());
        this.gravityScale = options.gravity();
        this.gravity = 0;
        this.keep = Mth.clamp(1 - options.drag(), 0, 1);
        this.friction = keep;
        this.spin = options.spin() * Mth.DEG_TO_RAD;
        this.sway = options.sway();
        this.fadeIn = options.fadeIn();
        this.fullbright = options.fullbright();
        this.hasPhysics = options.collide();
        Orient wanted = options.orient();
        this.orient = wanted == Orient.AUTO ? kind.defaultOrient() : wanted;
        this.seed = random.nextFloat() * TAU;
        float largest = Math.max(sizeStart, sizeEnd);
        setSize(Math.max(0.05f, Math.min(largest, 2f)), Math.max(0.05f, Math.min(largest, 2f)));

        switch (kind.spriteMode()) {
            case RANDOM -> setSprite(sprites.get(random));
            case AGE -> setSpriteFromAge(sprites);
            default -> {
            }
        }
        // Loose bits start turned any way; glyphs, wisps and halos stay upright.
        if (kind == VfxParticleKind.LEAF || kind == VfxParticleKind.SHARD || kind == VfxParticleKind.SNOWFLAKE
                || kind == VfxParticleKind.SMOKE_PUFF || kind == VfxParticleKind.EMBER || kind == VfxParticleKind.STAR) {
            roll = oRoll = random.nextFloat() * TAU;
        }
        renderSize = sizeStart * 0.5f;
    }

    /** Count of VFX particles ticked since the last call (so: alive right now), and starts counting again. */
    public static int takeTicked() {
        int count = ticked;
        ticked = 0;
        return count;
    }

    /** Moves along with this entity for the rest of its life. */
    public void follow(Entity entity) {
        follow = entity;
        followX = entity.getX();
        followY = entity.getY();
        followZ = entity.getZ();
    }

    /**
     * Circles (centerX, centerZ) at its current distance, {@code speed} radians a tick, drifting outwards by
     * {@code radial} blocks a tick; with an entity, the centre moves with it.
     */
    public void orbit(double centerX, double centerZ, float speed, float radial, Entity entity) {
        this.orbit = true;
        this.centerX = centerX;
        this.centerZ = centerZ;
        double dx = x - centerX, dz = z - centerZ;
        this.orbitRadius = (float) Math.sqrt(dx * dx + dz * dz);
        this.orbitAngle = (float) Math.atan2(dz, dx);
        this.orbitSpeed = speed;
        this.radialSpeed = radial;
        if (entity != null) {
            follow(entity);
        }
    }

    @Override
    public void tick() {
        ticked++;
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (kind.spriteMode() == VfxParticleKind.SpriteMode.AGE) {
            setSpriteFromAge(sprites);
        }

        double fdx = 0, fdy = 0, fdz = 0;
        if (follow != null) {
            if (follow.isRemoved()) {
                follow = null;
            } else {
                fdx = follow.getX() - followX;
                fdy = follow.getY() - followY;
                fdz = follow.getZ() - followZ;
                followX = follow.getX();
                followY = follow.getY();
                followZ = follow.getZ();
            }
        }

        yd -= 0.04 * gravityScale;
        if (sway != 0) {
            float t = age * 0.22f + seed;
            xd += Mth.sin(t) * sway * 0.08f;
            zd += Mth.cos(t * 0.83f + 1.3f) * sway * 0.08f;
        }
        if (orbit) {
            orbitAngle += orbitSpeed;
            orbitRadius = Math.max(0, orbitRadius + radialSpeed);
            radialSpeed *= keep;
            centerX += fdx;
            centerZ += fdz;
            double nx = centerX + Mth.cos(orbitAngle) * orbitRadius + xd;
            double nz = centerZ + Mth.sin(orbitAngle) * orbitRadius + zd;
            move(nx - x, yd + fdy, nz - z);
            if (sway != 0) {
                xd *= 0.8;
                zd *= 0.8;
            } else {
                xd = 0;
                zd = 0;
            }
            yd *= keep;
        } else {
            move(xd + fdx, yd + fdy, zd + fdz);
            xd *= keep;
            yd *= keep;
            zd *= keep;
            if (onGround && hasPhysics) {
                xd *= 0.7;
                zd *= 0.7;
            }
        }
        roll += spin;
        if (kind == VfxParticleKind.BUBBLE && hasPhysics && y == yo && yd > 0) {
            remove(); // popped against a ceiling
        }
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
        float life = Mth.clamp((age + partialTicks) / lifetime, 0, 1);
        int color = lerpColor(colorStart, colorEnd, life);
        float a = (color >>> 24) / 255f;
        if (fadeIn > 0 && life < fadeIn) {
            a *= life / fadeIn;
        }
        float size = Mth.lerp(life, sizeStart, sizeEnd);
        float bright = 1;
        switch (kind) {
            case EMBER -> {
                float flicker = 0.75f + 0.25f * Mth.sin((age + partialTicks) * 1.9f + seed * 3) * Mth.sin((age + partialTicks) * 0.7f + seed);
                bright = flicker;
                a *= 0.8f + 0.2f * flicker;
            }
            case STAR -> size *= 0.7f + 0.3f * Math.abs(Mth.sin((age + partialTicks) * 0.45f + seed));
            case BUBBLE -> size *= 1 + 0.06f * Mth.sin((age + partialTicks) * 0.9f + seed);
            default -> {
            }
        }
        if (a <= 0.004f || size <= 0.001f) {
            return;
        }
        alpha = a;
        rCol = ((color >> 16) & 0xFF) / 255f * bright;
        gCol = ((color >> 8) & 0xFF) / 255f * bright;
        bCol = (color & 0xFF) / 255f * bright;
        renderSize = size * 1.2f; // effect sizes are in blocks; 0.5 made them near-invisible in game

        switch (orient) {
            case FLAT -> {
                float r = Mth.lerp(partialTicks, oRoll, roll);
                ROTATION.rotationX(-Mth.HALF_PI).rotateZ(r);
                extractRotatedQuad(state, camera, ROTATION, partialTicks);
                ROTATION.rotationX(Mth.HALF_PI).rotateZ(-r);
                extractRotatedQuad(state, camera, ROTATION, partialTicks);
            }
            case VERTICAL -> {
                FacingCameraMode.LOOKAT_Y.setRotation(ROTATION, camera, partialTicks);
                if (roll != 0) {
                    ROTATION.rotateZ(Mth.lerp(partialTicks, oRoll, roll));
                }
                extractRotatedQuad(state, camera, ROTATION, partialTicks);
            }
            case VELOCITY -> {
                ROTATION.set(camera.rotation());
                float dx = (float) (x - xo), dy = (float) (y - yo), dz = (float) (z - zo);
                if (dx * dx + dy * dy + dz * dz > 1.0e-8f) {
                    AXIS_X.set(1, 0, 0).rotate(ROTATION);
                    AXIS_Y.set(0, 1, 0).rotate(ROTATION);
                    float along = dx * AXIS_X.x + dy * AXIS_X.y + dz * AXIS_X.z;
                    float up = dx * AXIS_Y.x + dy * AXIS_Y.y + dz * AXIS_Y.z;
                    if (along * along + up * up > 1.0e-10f) {
                        streakAngle = (float) Math.atan2(up, along);
                    }
                }
                ROTATION.rotateZ(streakAngle);
                extractRotatedQuad(state, camera, ROTATION, partialTicks);
            }
            default -> super.extract(state, camera, partialTicks);
        }
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return renderSize;
    }

    @Override
    protected int getLightCoords(float partialTicks) {
        return fullbright ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTicks);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return LIMIT;
    }

    private static int lerpColor(int a, int b, float t) {
        if (a == b || t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        return ((int) (aa + ((b >>> 24) - aa) * t) << 24) | ((int) (ar + (((b >> 16) & 0xFF) - ar) * t) << 16)
                | ((int) (ag + (((b >> 8) & 0xFF) - ag) * t) << 8) | (int) (ab + ((b & 0xFF) - ab) * t);
    }
}
