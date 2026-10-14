package com.skyrpg.combat.mob;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Ender Wyrm: the final boss (docs/design/end_game.md), a long serpentine dragon (drawn by EnderWyrmModel). The
 * entity is only the head: it flies without gravity or block collision (no physics), steering itself with a server-side
 * state machine, and the client draws the body segments along a trail of the head's recent positions.
 * <p>
 * States ({@link Mode}), each attack telegraphed with particles and sound before it lands:
 * <ul>
 *   <li>CIRCLE: arcs round the arena centre (where it was summoned) and picks the next attack;</li>
 *   <li>DIVE_WIND / DIVE: marks a player with a line of soul fire, rears, then dives along that line (phase 2+ chains dives);</li>
 *   <li>BREATH_WIND / BREATH: hovers, opens its jaws and gathers void light while the cone's edges are drawn on the
 *   air, then breathes a cone of void breath (damage, wither, harmful clouds where it hits the ground);</li>
 *   <li>SUMMON_WIND: rises, rings appear on the ground, then 3 (4 from phase 2) Void Echoes (named endermen) step out;</li>
 *   <li>STORM_WIND / STORM (phase 3): the void storm, falling void bolts at players' positions, each marked on the ground
 *   about a second before it strikes.</li>
 * </ul>
 * Phases change at 66% and 33% health: it roars, speeds up and summons at once.
 */
public class EnderWyrmEntity extends Monster {
    public static final int SEGMENTS = 12;
    public static final double HEAD_TO_SEG0 = 2.1;
    public static final double SPACING = 1.25;
    public static final double HEAD_HEIGHT = 1.5;
    /** The body's total length behind the head, blocks. */
    public static final double BODY_LENGTH = HEAD_TO_SEG0 + SPACING * (SEGMENTS - 1) + 2;

    public enum Mode {
        CIRCLE, DIVE_WIND, DIVE, BREATH_WIND, BREATH, SUMMON_WIND, STORM_WIND, STORM
    }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(EnderWyrmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(EnderWyrmEntity.class, EntityDataSerializers.INT);
    public static final String ECHO_TAG = "skyrpg_void_echo";
    private static final double[] CIRCLE_SPEED = {0.45, 0.58, 0.72};
    private static final double[] DIVE_SPEED = {1.1, 1.3, 1.5};

    private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Ender Wyrm").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final List<Mob> echoes = new ArrayList<>();
    private final List<Vec3> summonSpots = new ArrayList<>();
    private final List<double[]> bolts = new ArrayList<>(); // x, y, z, ticks left
    private final List<Vec3> trail = new ArrayList<>(); // client only: the head's recent positions, newest first
    private int trailNewestTick = -1; // client only: the tick trail[0] was recorded on (it is where the head is now)
    private final Map<UUID, Integer> presence = new HashMap<>(); // seconds each player spent in the arena, for credit
    private UUID summoner;
    private int idleTicks;

    private Vec3 center;
    private int modeTicks;
    private int summonCooldown = 200;
    private int stormCooldown = 500;
    private int diveChain;
    private Vec3 aim = Vec3.ZERO;
    private int introTicks = 100;
    private final java.util.Set<UUID> diveHit = new java.util.HashSet<>();

    public EnderWyrmEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
        bar.setDarkenScreen(false).setCreateWorldFog(false).setPlayBossMusic(false);
    }

    static AttributeSupplier.Builder attributes() {
        // 1024 is vanilla's health cap; MobLevelling.boss gives it toughness 2, so it takes half damage: about 2000.
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1024).add(Attributes.ARMOR, 20).add(Attributes.ARMOR_TOUGHNESS, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FLYING_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 24)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 96);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, 0);
        builder.define(PHASE, 1);
    }

    @Override
    protected void registerGoals() {
        // no goals: the state machine drives it
    }

    public Mode getMode() {
        return MODES[Mth.clamp(getEntityData().get(MODE), 0, MODES.length - 1)];
    }

    public int getPhase() {
        return getEntityData().get(PHASE);
    }

    /** Who started the fight (gets their Void Sigil back if the Wyrm withdraws with nobody left to fight it). */
    void setSummoner(UUID summoner) {
        this.summoner = summoner;
    }

    /** Sets where the fight happens (the altar); the Wyrm circles it. */
    public void setCenter(Vec3 center) {
        this.center = center;
    }

    // ------------------------------------------------------------------------------------------------ body trail (client)

    /** A body segment (or the head): where it is, as an offset from the entity's interpolated position, and which way it faces. */
    public record BodyPoint(double dx, double dy, double dz, float yaw, float pitch) {
    }

    /** The head and the {@link #SEGMENTS} segments, placed along the head's trail, for rendering at this partial tick. */
    public BodyPoint[] bodyPoints(float pt) {
        double ex = Mth.lerp(pt, xo, getX());
        double ey = Mth.lerp(pt, yo, getY());
        double ez = Mth.lerp(pt, zo, getZ());
        Vec3 head = new Vec3(ex, ey + HEAD_HEIGHT, ez);
        List<Vec3> poly = new ArrayList<>(trail.size() + 1);
        poly.add(head);
        // trail[0] is where the head is at the end of this tick, i.e. AHEAD of the interpolated head for any partial tick below
        // 1: starting the path with it makes the body double back on itself (a kink that flickers at the front of the body).
        for (int i = trailNewestTick == tickCount ? 1 : 0; i < trail.size(); i++) {
            Vec3 p = trail.get(i);
            if (p.distanceToSqr(poly.get(poly.size() - 1)) > 0.01) {
                poly.add(p);
            }
        }
        double[] cum = new double[poly.size()];
        for (int i = 1; i < cum.length; i++) {
            cum[i] = cum[i - 1] + poly.get(i).distanceTo(poly.get(i - 1));
        }
        float yaw = Mth.rotLerp(pt, yRotO, getYRot());
        float pitch = Mth.lerp(pt, xRotO, getXRot());
        Vec3 back = new Vec3(Mth.sin(yaw * Mth.DEG_TO_RAD), 0, -Mth.cos(yaw * Mth.DEG_TO_RAD)); // behind the head
        BodyPoint[] out = new BodyPoint[SEGMENTS + 1];
        out[0] = new BodyPoint(0, HEAD_HEIGHT, 0, yaw, pitch);
        for (int i = 0; i < SEGMENTS; i++) {
            double d = HEAD_TO_SEG0 + SPACING * i;
            Vec3 p = pathAt(poly, cum, d, back);
            Vec3 ahead = pathAt(poly, cum, Math.max(0, d - 0.8), back);
            Vec3 behind = pathAt(poly, cum, d + 0.8, back);
            Vec3 h = ahead.subtract(behind);
            float segYaw = (float) (Mth.atan2(-h.x, h.z) * Mth.RAD_TO_DEG);
            float segPitch = (float) (-Mth.atan2(h.y, Math.sqrt(h.x * h.x + h.z * h.z)) * Mth.RAD_TO_DEG);
            out[1 + i] = new BodyPoint(p.x - ex, p.y - ey, p.z - ez, segYaw, segPitch);
        }
        return out;
    }

    private static Vec3 pathAt(List<Vec3> poly, double[] cum, double d, Vec3 fallbackBack) {
        int n = poly.size();
        if (d >= cum[n - 1]) {
            Vec3 dir = fallbackBack;
            if (n >= 2) {
                Vec3 v = poly.get(n - 1).subtract(poly.get(n - 2));
                if (v.lengthSqr() > 1.0e-4) {
                    dir = v.normalize();
                }
            }
            return poly.get(n - 1).add(dir.scale(d - cum[n - 1]));
        }
        for (int i = 1; i < n; i++) {
            if (cum[i] >= d) {
                double t = (d - cum[i - 1]) / Math.max(1.0e-6, cum[i] - cum[i - 1]);
                return poly.get(i - 1).lerp(poly.get(i), t);
            }
        }
        return poly.get(n - 1);
    }

    private void recordTrail() {
        Vec3 here = position().add(0, HEAD_HEIGHT, 0);
        if (!trail.isEmpty() && trail.get(0).distanceToSqr(here) > 144) {
            trail.clear();
        }
        if (trail.isEmpty()) {
            // first sight of the Wyrm (or after a jump): lay the body out along where it came from, not in a straight line
            // from its yaw (it may be diving or climbing)
            Vec3 v = getDeltaMovement();
            Vec3 back = v.lengthSqr() > 0.0025 ? v.normalize().scale(-1)
                    : new Vec3(Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            for (int i = 1; i <= 40; i++) {
                trail.add(here.add(back.scale(i * 0.5)));
            }
        }
        if (trail.get(0).distanceToSqr(here) >= 0.09) {
            trail.add(0, here);
            trailNewestTick = tickCount;
            while (trail.size() > 160) {
                trail.remove(trail.size() - 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        if (level().isClientSide()) {
            recordTrail();
        }
    }

    @Override
    public void travel(Vec3 input) {
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            return;
        }
        RandomSource random = getRandom();
        BodyPoint[] body = bodyPoints(1f);
        // void motes drift off the body, and the maw smokes when it's open
        if (random.nextInt(2) == 0) {
            BodyPoint p = body[1 + random.nextInt(SEGMENTS)];
            level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, getX() + p.dx() + (random.nextDouble() - 0.5),
                    getY() + p.dy() + (random.nextDouble() - 0.3), getZ() + p.dz() + (random.nextDouble() - 0.5), 0, 0.04, 0);
        }
        Mode mode = getMode();
        if (mode == Mode.BREATH_WIND || mode == Mode.BREATH || mode == Mode.DIVE_WIND) {
            Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 mouth = position().add(0, HEAD_HEIGHT - 0.2, 0).add(look.scale(2.6));
            level().addParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), mouth.x, mouth.y, mouth.z, look.x * 0.1, look.y * 0.1, look.z * 0.1);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (center == null) {
            center = position().add(0, -18, 0);
        }
        int phase = phaseForHealth();
        if (phase > getPhase()) {
            enterPhase(level, phase);
        }
        updateBar(level);
        tickBolts(level);
        echoes.removeIf(echo -> !echo.isAlive());
        if (summonCooldown > 0) {
            summonCooldown--;
        }
        if (stormCooldown > 0) {
            stormCooldown--;
        }
        if (introTicks > 0) {
            introTicks--;
        }
        modeTicks++;
        Player_ target = pickTarget(level);
        setTarget(target.player);
        trackArena(level);
        if (target.player == null) {
            // nobody (alive, in survival) left in the arena: wait half a minute for a respawn, then withdraw
            if (++idleTicks > IDLE_WITHDRAW_TICKS) {
                withdraw(level);
                return;
            }
        } else {
            idleTicks = 0;
        }
        Mode mode = getMode();
        int p = getPhase() - 1;
        switch (mode) {
            case CIRCLE -> circle(level, p, target.player);
            case DIVE_WIND -> diveWind(level, p, target.player);
            case DIVE -> dive(level, p, target.player);
            case BREATH_WIND -> breathWind(level, p, target.player);
            case BREATH -> breath(level, p, target.player);
            case SUMMON_WIND -> summonWind(level, p, target.player);
            case STORM_WIND -> stormWind(level, p);
            case STORM -> storm(level, p);
        }
        keepAboveGround(level);
    }

    private static final int IDLE_WITHDRAW_TICKS = 600;
    /** How far the head's centre stays above the highest block under it, so it skims the ground in a dive instead of ploughing through it. */
    private static final double GROUND_CLEARANCE = 2.2;

    /**
     * The Wyrm has no collisions, so without this its dives (aimed at players standing on the ground, with a lot of speed left
     * when they end) carried the head and then the body several blocks into the terrain. The head's centre is kept
     * {@link #GROUND_CLEARANCE} above the highest block under it (over the void there is none) and above the arena's airspace
     * floor (lower while diving).
     */
    private void keepAboveGround(ServerLevel level) {
        Vec3 v = getDeltaMovement();
        double headY = getY() + HEAD_HEIGHT;
        double nextX = getX() + v.x;
        double nextZ = getZ() + v.z;
        boolean diving = getMode() == Mode.DIVE;
        double floor = center.y + (diving ? 1 : getMode() == Mode.DIVE_WIND ? 3 : 5);
        if (level.hasChunkAt(BlockPos.containing(nextX, headY, nextZ))) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(nextX), Mth.floor(nextZ));
            if (top > level.getMinY()) {
                floor = diving ? top + GROUND_CLEARANCE : Math.max(floor, top + GROUND_CLEARANCE);
            }
        }
        if (headY + v.y < floor) {
            setDeltaMovement(v.x, Math.min(1.2, Math.max(v.y, (floor - headY) * 0.6)), v.z);
        }
    }

    /** Counts the seconds each player spends fighting it, so everyone who was there for the fight is paid (see {@link #die}). */
    private void trackArena(ServerLevel level) {
        if (tickCount % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(center.x, center.y, center.z) < 64 * 64) {
                presence.merge(player.getUUID(), 1, Integer::sum);
            }
        }
    }

    /** Nobody is left to fight it: it sinks back into the void, and whoever called it gets the Void Sigil back. */
    private void withdraw(ServerLevel level) {
        broadcastNear(level, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8)
                .build(Msg.text("The Ender Wyrm loses interest and sinks back into the void.", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)));
        Vec3 head = position().add(0, HEAD_HEIGHT, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 120, 3, 2, 3, 0.4);
        level.playSound(null, head.x, head.y, head.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 6f, 0.4f);
        bar.removeAllPlayers();
        for (Mob echo : echoes) {
            echo.discard();
        }
        bolts.clear();
        if (summoner != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(summoner);
            if (owner != null) {
                EnderWyrms.giveBack(owner);
                owner.sendSystemMessage(Notice.of(Notice.Kind.COMBAT).tag("ENDER WYRM").build(Msg.text("Your Void Sigil is returned.", ChatFormatting.GRAY)));
            }
        }
        discard();
    }

    /** A holder so a missing target is a normal case. */
    private record Player_(ServerPlayer player) {
    }

    private Player_ pickTarget(ServerLevel level) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double d = player.distanceToSqr(center.x, center.y, center.z);
            if (d < 100 * 100 && d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        // stick to the current target while it's still around (so the dives don't flip-flop)
        if (getTarget() instanceof ServerPlayer current && current.isAlive() && !current.isSpectator() && !current.isCreative()
                && current.distanceToSqr(center.x, center.y, center.z) < 100 * 100 && getRandom().nextInt(200) != 0) {
            best = current;
        }
        return new Player_(best);
    }

    private int phaseForHealth() {
        float ratio = getHealth() / getMaxHealth();
        return ratio > 0.66f ? 1 : ratio > 0.33f ? 2 : 3;
    }

    private void setMode(Mode mode) {
        getEntityData().set(MODE, mode.ordinal());
        modeTicks = 0;
    }

    private Vec3 mouth() {
        return position().add(0, HEAD_HEIGHT - 0.2, 0).add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(2.6));
    }

    // ------------------------------------------------------------------------------------------------ steering

    /** Eases the velocity toward flying at {@code speed} to {@code goal}. */
    private void flyTo(Vec3 goal, double speed, double turn) {
        Vec3 want = goal.subtract(position().add(0, HEAD_HEIGHT, 0));
        double len = want.length();
        want = len > 1.0e-4 ? want.scale(Math.min(speed, len * 0.25 + 0.05) / len) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().lerp(want, turn));
    }

    private void faceVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() < 0.01 && Math.abs(v.y) < 0.1) {
            return;
        }
        turnHead((float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * Mth.RAD_TO_DEG), 12f);
    }

    private void faceTowards(Vec3 point, float maxTurn) {
        Vec3 d = point.subtract(position().add(0, HEAD_HEIGHT, 0));
        turnHead((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG), maxTurn);
    }

    private void turnHead(float yaw, float pitch, float maxTurn) {
        float y = Mth.approachDegrees(getYRot(), yaw, maxTurn);
        float x = Mth.approachDegrees(getXRot(), Mth.clamp(pitch, -80f, 80f), maxTurn);
        setYRot(y);
        setXRot(x);
        setYHeadRot(y);
        setYBodyRot(y);
    }

    // ------------------------------------------------------------------------------------------------ states

    private void circle(ServerLevel level, int p, ServerPlayer target) {
        double radius = p == 2 ? 24 : 28;
        double angle = Math.atan2(getZ() - center.z, getX() - center.x);
        double ahead = angle + 0.38;
        Vec3 goal = new Vec3(center.x + Math.cos(ahead) * radius, center.y + 18 + 3 * Math.sin(tickCount / 40.0), center.z + Math.sin(ahead) * radius);
        // pull back toward the ring if a dive left it far outside
        flyTo(goal, CIRCLE_SPEED[p], 0.08);
        faceVelocity();
        int wait = introTicks > 0 ? 100 : (p == 0 ? 120 : p == 1 ? 90 : 70);
        if (modeTicks < wait || target == null) {
            return;
        }
        RandomSource random = getRandom();
        echoes.removeIf(echo -> !echo.isAlive());
        int cap = p == 0 ? 3 : 4;
        if (p == 2 && stormCooldown <= 0 && random.nextInt(2) == 0) {
            startStorm(level);
        } else if (summonCooldown <= 0 && echoes.size() <= cap - 3 && random.nextInt(2) == 0) {
            startSummon(level, target);
        } else if (random.nextInt(5) < 2) {
            startBreath(level, p);
        } else {
            diveChain = p == 0 ? 1 : p == 1 ? 2 : 3;
            startDive(level, p);
        }
    }

    private void startDive(ServerLevel level, int p) {
        setMode(Mode.DIVE_WIND);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 1.2f);
    }

    private void diveWind(ServerLevel level, int p, ServerPlayer target) {
        if (target == null) {
            setMode(Mode.CIRCLE);
            return;
        }
        int total = p == 2 ? 22 : 30;
        // rear up above the target, slowing down
        Vec3 goal = target.position().add(0, 16, 0).add(position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(10));
        flyTo(goal, 0.5, 0.12);
        faceTowards(target.getEyePosition(), 20f);
        Vec3 mouth = mouth();
        Vec3 line = target.position().add(0, 1, 0).subtract(mouth);
        if (modeTicks % 2 == 0) {
            // the dive line: soul fire marching from the maw to the mark, and a ring round the mark
            for (int i = 1; i <= 10; i++) {
                Vec3 pt = mouth.add(line.scale(i / 10.0));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            ring(level, target.position().add(0, 0.1, 0), 1.6, 12, ParticleTypes.REVERSE_PORTAL);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mouth.x, mouth.y, mouth.z, 6, 0.9, 0.9, 0.9, 0.3);
        if (modeTicks == total - 8) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2f, 1.4f);
        }
        if (modeTicks >= total) {
            aim = target.position().add(0, 1, 0).add(target.getDeltaMovement().multiply(1, 0, 1).scale(6));
            diveHit.clear();
            setMode(Mode.DIVE);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 5f, 0.6f);
        }
    }

    private void dive(ServerLevel level, int p, ServerPlayer target) {
        Vec3 head = position().add(0, HEAD_HEIGHT, 0);
        Vec3 toAim = aim.subtract(head);
        // a little homing on the live target in the first half
        if (target != null && modeTicks < 14) {
            aim = aim.lerp(target.position().add(0, 1, 0), 0.08);
            toAim = aim.subtract(head);
        }
        setDeltaMovement(getDeltaMovement().lerp(toAim.normalize().scale(DIVE_SPEED[p]), 0.35));
        faceVelocity();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 5, 1.2, 1.2, 1.2, 0.25);
        level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), head.x, head.y, head.z, 4, 1.2, 0.8, 1.2, 0.02);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || diveHit.contains(player.getUUID())) {
                continue;
            }
            if (player.getBoundingBox().inflate(2.2).contains(head) || player.distanceToSqr(head) < 20) {
                diveHit.add(player.getUUID());
                hit(level, player, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), getDeltaMovement().normalize().scale(1.4).add(0, 0.6, 0));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2f, 0.7f);
            }
        }
        boolean passed = toAim.dot(getDeltaMovement()) < 0 || toAim.lengthSqr() < 9;
        if (toAim.lengthSqr() < 64 && getDeltaMovement().y < 0) {
            // swoop: level off over the last few blocks so the dive curves back up instead of carrying on down
            Vec3 d = getDeltaMovement();
            setDeltaMovement(d.x, Mth.lerp(0.3, d.y, 0.25 * DIVE_SPEED[p]), d.z);
        }
        if (modeTicks > 55 || (passed && modeTicks > 8)) {
            diveChain--;
            if (diveChain > 0 && target != null) {
                setMode(Mode.DIVE_WIND);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 1.4f);
            } else {
                setMode(Mode.CIRCLE);
            }
        }
    }

    private void startBreath(ServerLevel level, int p) {
        setMode(Mode.BREATH_WIND);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.8f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3f, 0.7f);
    }

    private static final double BREATH_RANGE = 26;
    private static final double BREATH_COS = Math.cos(Math.toRadians(17));

    private void breathWind(ServerLevel level, int p, ServerPlayer target) {
        if (target == null) {
            setMode(Mode.CIRCLE);
            return;
        }
        int total = p == 2 ? 30 : 42;
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
        flyTo(target.position().add(away.scale(17)).add(0, 11, 0), 0.4, 0.1);
        faceTowards(target.position().add(0, 1, 0), 14f);
        Vec3 mouth = mouth();
        // gathering light at the maw
        level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), mouth.x, mouth.y, mouth.z, 8, 1.6, 1.6, 1.6, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, mouth.x, mouth.y, mouth.z, 2, 0.3, 0.3, 0.3, 0.02);
        if (modeTicks % 3 == 0) {
            // the cone's two edges drawn on the air, plus its axis
            Vec3 axis = target.position().add(0, 1, 0).subtract(mouth).normalize();
            Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
            double spread = Math.tan(Math.acos(BREATH_COS));
            for (int i = 3; i <= BREATH_RANGE; i += 3) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 pt = mouth.add(axis.scale(i)).add(side.scale(s * i * spread));
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (modeTicks >= total) {
            aim = target.position().add(0, 1, 0).subtract(mouth).normalize();
            setMode(Mode.BREATH);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 5f, 0.6f);
        }
    }

    private void breath(ServerLevel level, int p, ServerPlayer target) {
        setDeltaMovement(getDeltaMovement().scale(0.85));
        Vec3 mouth = mouth();
        if (target != null) {
            // the cone slowly tracks the target
            Vec3 want = target.position().add(0, 1, 0).subtract(mouth).normalize();
            aim = aim.lerp(want, 0.035 + 0.01 * p).normalize();
        }
        faceTowards(mouth.add(aim.scale(10)), 20f);
        RandomSource random = getRandom();
        for (int i = 0; i < 10; i++) {
            double spread = 0.22;
            Vec3 v = aim.add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread);
            double start = random.nextDouble() * 6;
            Vec3 at = mouth.add(aim.scale(start));
            level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.2f), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.1);
        }
        level.sendParticles(ParticleTypes.PORTAL, mouth.x, mouth.y, mouth.z, 4, 0.3, 0.3, 0.3, 1.2);
        if (modeTicks % 4 == 0) {
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 2.5f, 1.4f);
        }
        if (modeTicks % 5 == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                Vec3 to = player.position().add(0, 1, 0).subtract(mouth);
                double dist = to.length();
                if (dist < BREATH_RANGE && dist > 0.5 && to.dot(aim) / dist > BREATH_COS) {
                    hit(level, player, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3f, Vec3.ZERO);
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                }
            }
        }
        if (modeTicks % 12 == 3) {
            // harmful void cloud where the breath meets the ground
            HitResult ray = level.clip(new ClipContext(mouth, mouth.add(aim.scale(BREATH_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 at = ray.getLocation();
            AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
            cloud.setOwner(this);
            cloud.setRadius(3.2f);
            cloud.setDuration(140);
            cloud.setWaitTime(0);
            cloud.setRadiusPerTick(-0.01f);
            cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f));
            cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
            cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            level.addFreshEntity(cloud);
            level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), at.x, at.y + 0.3, at.z, 20, 1.5, 0.3, 1.5, 0.05);
        }
        if (modeTicks >= (p == 2 ? 70 : 55)) {
            setMode(Mode.CIRCLE);
        }
    }

    private void startSummon(ServerLevel level, ServerPlayer target) {
        setMode(Mode.SUMMON_WIND);
        summonSpots.clear();
        int count = getPhase() == 1 ? 3 : 4;
        RandomSource random = getRandom();
        Vec3 around = target != null ? target.position() : center;
        for (int i = 0; i < count; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = 6 + random.nextDouble() * 6;
            double x = around.x + Math.cos(ang) * r;
            double z = around.z + Math.sin(ang) * r;
            double y = groundY(level, x, z, around.y);
            summonSpots.add(new Vec3(x, y, z));
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 5f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 4f, 0.5f);
    }

    private static double groundY(LevelReader level, double x, double z, double fallback) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return y <= level.getMinY() ? fallback : y;
    }

    private void summonWind(ServerLevel level, int p, ServerPlayer target) {
        flyTo(center.add(0, 26, 0), 0.5, 0.1);
        turnHead(getYRot() + 6f, -35f, 10f);
        for (Vec3 spot : summonSpots) {
            ring(level, spot.add(0, 0.1, 0), 1.3 + modeTicks * 0.02, 10, ParticleTypes.REVERSE_PORTAL);
            level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1, spot.z, 6, 0.4, 1.0, 0.4, 0.5);
            if (modeTicks % 10 == 0) {
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.2f, 0.6f + modeTicks * 0.01f);
            }
        }
        if (modeTicks >= 50) {
            for (Vec3 spot : summonSpots) {
                spawnEcho(level, spot, target);
            }
            summonSpots.clear();
            summonCooldown = p == 0 ? 500 : p == 1 ? 400 : 320;
            setMode(Mode.CIRCLE);
        }
    }

    private void spawnEcho(ServerLevel level, Vec3 at, ServerPlayer target) {
        Mob echo = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.EVENT);
        if (echo == null) {
            return;
        }
        echo.snapTo(at.x, at.y, at.z, getRandom().nextFloat() * 360, 0);
        echo.finalizeSpawn(level, level.getCurrentDifficultyAt(echo.blockPosition()), EntitySpawnReason.EVENT, null);
        echo.setCustomName(Component.literal("Void Echo").withStyle(ChatFormatting.DARK_PURPLE));
        echo.setCustomNameVisible(true);
        echo.addTag(ECHO_TAG);
        echo.setPersistenceRequired();
        echo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        echo.setHealth(100);
        echo.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12);
        com.skyrpg.core.world.MobLevelling.level(echo, 70, false);
        if (target != null) {
            echo.setTarget(target);
        }
        level.addFreshEntity(echo);
        echoes.add(echo);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.5, 1, 0.5, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
    }

    private void startStorm(ServerLevel level) {
        setMode(Mode.STORM_WIND);
        broadcastNear(level, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8).build(Msg.text("The sky above the arena begins to split...", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 1.6f);
    }

    private void stormWind(ServerLevel level, int p) {
        flyTo(center.add(0, 30, 0), 0.5, 0.1);
        turnHead(getYRot() + 8f, -50f, 10f);
        Vec3 head = position().add(0, HEAD_HEIGHT, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 15, 3, 2, 3, 0.6);
        if (modeTicks % 6 == 0) {
            ring(level, new Vec3(head.x, head.y - 2 - (modeTicks % 12), head.z), 4 + modeTicks * 0.15, 24, ParticleTypes.PORTAL);
        }
        if (modeTicks >= 60) {
            setMode(Mode.STORM);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8f, 0.6f);
        }
    }

    private void storm(ServerLevel level, int p) {
        double angle = Math.atan2(getZ() - center.z, getX() - center.x) + 0.3;
        flyTo(new Vec3(center.x + Math.cos(angle) * 12, center.y + 28, center.z + Math.sin(angle) * 12), 0.5, 0.08);
        faceVelocity();
        RandomSource random = getRandom();
        if (modeTicks % 8 == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(center.x, center.y, center.z) > 80 * 80) {
                    continue;
                }
                bolts.add(new double[] {player.getX() + (random.nextDouble() - 0.5) * 2, groundBelow(level, player), player.getZ() + (random.nextDouble() - 0.5) * 2, 24});
            }
        }
        if (modeTicks % 12 == 5) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * 22;
            double x = center.x + Math.cos(ang) * r;
            double z = center.z + Math.sin(ang) * r;
            bolts.add(new double[] {x, groundY(level, x, z, center.y), z, 24});
        }
        if (modeTicks >= 170) {
            stormCooldown = 1000;
            setMode(Mode.CIRCLE);
        }
    }

    private static double groundBelow(ServerLevel level, ServerPlayer player) {
        return player.onGround() ? player.getY() : Math.max(level.getMinY(), groundY(level, player.getX(), player.getZ(), player.getY()));
    }

    /** Marks pending void bolts on the ground, and brings each one down when its time is up. */
    private void tickBolts(ServerLevel level) {
        for (int i = bolts.size() - 1; i >= 0; i--) {
            double[] bolt = bolts.get(i);
            bolt[3]--;
            if (bolt[3] > 0) {
                if ((int) bolt[3] % 3 == 0) {
                    ring(level, new Vec3(bolt[0], bolt[1] + 0.1, bolt[2]), 3.2 * (1 - bolt[3] / 26) + 0.8, 14, ParticleTypes.REVERSE_PORTAL);
                    ring(level, new Vec3(bolt[0], bolt[1] + 0.1, bolt[2]), 3.2, 14, ParticleTypes.PORTAL);
                }
                if (bolt[3] == 8) {
                    level.playSound(null, bolt[0], bolt[1], bolt[2], SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 1.8f);
                }
                continue;
            }
            bolts.remove(i);
            strike(level, bolt[0], bolt[1], bolt[2]);
        }
    }

    private void strike(ServerLevel level, double x, double y, double z) {
        for (int k = 0; k < 24; k++) {
            level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.5f), x, y + k, z, 3, 0.25, 0.2, 0.25, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, x, y + k, z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 2, 0.8, 0.2, 0.8, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 40, 1.2, 0.6, 1.2, 0.3);
        LightningBolt flash = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (flash != null) {
            flash.setVisualOnly(true);
            flash.snapTo(x, y, z);
            level.addFreshEntity(flash);
        }
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 3f, 0.8f);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.7f);
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (!player.isSpectator() && player.isAlive() && dx * dx + dz * dz < 3.4 * 3.4 && Math.abs(player.getY() - y) < 4) {
                hit(level, player, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f, new Vec3(dx, 0, dz).normalize().scale(0.6).add(0, 0.7, 0));
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 at, double radius, int points, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y, at.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0);
        }
    }

    private void hit(ServerLevel level, LivingEntity victim, float amount, Vec3 knock) {
        if (victim.hurtServer(level, damageSources().mobAttack(this), amount) && knock.lengthSqr() > 0) {
            victim.push(knock.x, knock.y, knock.z);
            victim.syncVelocity = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ phases, bar, messages

    private void enterPhase(ServerLevel level, int phase) {
        getEntityData().set(PHASE, phase);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4f, 0.8f);
        Vec3 head = position().add(0, HEAD_HEIGHT, 0);
        for (int r = 1; r <= 5; r++) {
            ring(level, head, r * 2.5, 20 + r * 6, ParticleTypes.REVERSE_PORTAL);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, head.x, head.y, head.z, 1, 0, 0, 0, 0);
        if (phase == 2) {
            broadcastNear(level, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8).build(Msg.text("The Ender Wyrm shrieks and the void answers! ", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Msg.text("Phase II: faster, with more echoes.", ChatFormatting.GRAY))));
            titleNear(level, Msg.text("Phase II", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        } else {
            broadcastNear(level, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8).build(Msg.text("The Ender Wyrm is enraged! ", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                    .append(Msg.text("Phase III: the void storm falls.", ChatFormatting.GRAY))));
            titleNear(level, Msg.text("The Void Storm", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }
        // it summons at once
        diveChain = 0;
        ServerPlayer target = pickTarget(level).player;
        summonCooldown = 0;
        stormCooldown = Math.min(stormCooldown, 400);
        startSummon(level, target);
    }

    private void updateBar(ServerLevel level) {
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        bar.setName(Component.literal("Ender Wyrm").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("  Phase " + toRoman(getPhase())).withStyle(ChatFormatting.GRAY)));
        if (tickCount % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                boolean near = player.distanceToSqr(this) < 160 * 160;
                if (near) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
        }
    }

    private static String toRoman(int n) {
        return n == 1 ? "I" : n == 2 ? "II" : "III";
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    /** Intro: called by {@link EnderWyrms#summon} once it's in the world. */
    void announce(ServerLevel level) {
        broadcastNear(level, Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8).build(Msg.text("The air tears open. ", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)
                .append(Msg.text("The Ender Wyrm", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)).append(Msg.text(" has come for you.", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))));
        titleNear(level, Msg.text("The Ender Wyrm", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + HEAD_HEIGHT, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + HEAD_HEIGHT, getZ(), 200, 4, 3, 4, 0.5);
    }

    private void broadcastNear(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 160 * 160) {
                player.sendSystemMessage(message);
            }
        }
    }

    private void titleNear(ServerLevel level, Component title) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 160 * 160) {
                player.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ damage, death

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || introTicks > 60) {
            return false;
        }
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof ServerPlayer player) {
            damageDealt.merge(player.getUUID(), (double) Math.max(0, before - getHealth()), Double::sum);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        bar.removeAllPlayers();
        setDeltaMovement(0, -0.02, 0);
        for (Mob echo : echoes) {
            if (echo.isAlive()) {
                echo.discard();
            }
        }
        bolts.clear();
        Vec3 head = position().add(0, HEAD_HEIGHT, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, head.x, head.y, head.z, 3, 3, 2, 3, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 300, 5, 3, 5, 0.8);
        level.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 10f, 0.6f);
        // lots of experience: vanilla XP and Combat XP for everyone who fought it (below)
        // everyone who fought it is paid, not just the killing blow: whoever hurt it, plus anyone who stood in the arena for
        // at least 15 seconds (healers and tanks of a party)
        Map<UUID, Double> credit = new HashMap<>(damageDealt);
        presence.forEach((id, seconds) -> {
            if (seconds >= PRESENCE_CREDIT_SECONDS) {
                credit.putIfAbsent(id, 0.0);
            }
        });
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(credit.entrySet());
        ranking.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        RandomSource random = getRandom();
        for (int i = 0; i < ranking.size(); i++) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ranking.get(i).getKey());
            if (player == null) {
                continue;
            }
            SkillLevels.giveXp(player, SkillLevels.COMBAT, i == 0 ? 30000 : 20000);
            player.giveExperiencePoints(8000);
            int scales = 12 + random.nextInt(7);
            int hearts = 1 + (random.nextInt(100) < 35 ? 1 : 0);
            give(player, new ItemStack(EndMobs.WYRM_SCALE, scales));
            give(player, new ItemStack(EndMobs.VOID_HEART, hearts));
            // whatever other mods add to the Wyrm's loot table (the magic mod's rare scrolls), rolled for each participant
            getLootTable().ifPresent(table -> dropFromLootTable(level, source, true, table, stack -> give(player, stack)));
            com.skyrpg.core.economy.Wallet.give(player, COIN_REWARD);
            Notice.of(Notice.Kind.COMBAT).tag("ENDER WYRM").send(player, Msg.text("#" + (i + 1) + " ", ChatFormatting.YELLOW)
                    .append(Msg.text(Msg.number(Math.round(ranking.get(i).getValue() * com.skyrpg.core.stats.StatTypes.HEALTH_SCALE * 2)) + " damage to the Ender Wyrm", ChatFormatting.GRAY)));
            Notice.of(Notice.Kind.COMBAT).tag("ENDER WYRM").send(player, Msg.text("+" + scales + " Wyrm Scales, +" + hearts + " Void Heart" + (hearts > 1 ? "s" : "")
                    + ", +" + Msg.number(COIN_REWARD) + " coins", ChatFormatting.LIGHT_PURPLE));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 0.7f);
            // first kill of the Wyrm for this player: SkyRPG XP (progress mod), and the Ender Wyrm quest
            com.skyrpg.core.event.BossKills.KILLED.invoker().onKilled(player, EnderWyrms.BOSS_ID, 100, com.skyrpg.core.event.BossKills.Kind.WORLD_BOSS);
        }
        firstKill(level, ranking);
    }

    private static final int PRESENCE_CREDIT_SECONDS = 15;
    private static final long COIN_REWARD = 10000;

    /** Into the inventory, or at the player's feet when it is full. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** The Wyrm's drops go straight to everyone who fought it (see {@link #die}); nothing falls out of the sky into the void. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
    }

    /** Nothing is dropped in the world for the killer only; {@link #die} rolls the loot table once per participant instead. */
    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean killedByPlayer) {
    }

    private void firstKill(ServerLevel level, List<Map.Entry<UUID, Double>> ranking) {
        java.nio.file.Path marker = level.getServer().getWorldPath(LevelResource.ROOT).resolve("skyrpg_ender_wyrm_slain.txt");
        boolean first = !java.nio.file.Files.exists(marker);
        String killer = "";
        if (!ranking.isEmpty()) {
            ServerPlayer top = level.getServer().getPlayerList().getPlayer(ranking.get(0).getKey());
            killer = top != null ? top.getGameProfile().name() : "";
        }
        if (first) {
            try {
                java.nio.file.Files.writeString(marker, "first slain by " + killer + "\n");
            } catch (java.io.IOException e) {
                // the broadcast still goes out; at worst it repeats after a restart
            }
        }
        Component message = first
                ? Notice.of(Notice.Kind.EVENT).icon(Notice.CROWN).tag("FIRST KILL").color(0xFFAA00).build(Msg.text(killer.isEmpty() ? "Heroes" : killer, ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append(Msg.text(" and friends have slain the Ender Wyrm for the first time on this server!", ChatFormatting.LIGHT_PURPLE)))
                : Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").build(Msg.text("The Ender Wyrm", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD).append(Msg.text(" has fallen!", ChatFormatting.GOLD)));
        level.getServer().getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(message));
    }

    // ------------------------------------------------------------------------------------------------ misc

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (center != null) {
            output.putDouble("WyrmCenterX", center.x);
            output.putDouble("WyrmCenterY", center.y);
            output.putDouble("WyrmCenterZ", center.z);
        }
        output.putInt("WyrmPhase", getPhase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (input.getDoubleOr("WyrmCenterY", Double.NaN) == input.getDoubleOr("WyrmCenterY", Double.NaN)) {
            center = new Vec3(input.getDoubleOr("WyrmCenterX", getX()), input.getDoubleOr("WyrmCenterY", getY() - 18), input.getDoubleOr("WyrmCenterZ", getZ()));
        }
        getEntityData().set(PHASE, Math.max(1, input.getIntOr("WyrmPhase", 1)));
        introTicks = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7f;
    }

    @Override
    protected float getSoundVolume() {
        return 4f;
    }

    /** Eye height is the head; used for targeting. */
    public BlockPos arenaCentre() {
        return center == null ? blockPosition() : BlockPos.containing(center);
    }
}
