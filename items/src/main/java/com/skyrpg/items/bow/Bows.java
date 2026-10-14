package com.skyrpg.items.bow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.SkyArrows;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.mixin.AbstractArrowAccess;
import com.skyrpg.items.shield.Shields;
import com.skyrpg.vfx.Vfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What SkyRPG bows do beyond shooting. A bow's definition has a {@code "bow"} object:
 * <pre>
 * "bow": {"draw_speed": 1.3,                  // how much faster than vanilla's 1 second it draws
 *         "pierce": 1,                        // extra mobs the arrow passes through
 *         "arrows": 3, "arrow_share": 0.55,   // arrows per shot, and the part of the damage each one deals
 *         "spread": 4, "homing": 0.12,        // degrees between arrows; how hard they steer towards a target
 *         "fire": true,                       // the arrow burns
 *         "burst": {"radius": 2.5, "share": 0.6},       // a small area hit around what it hits (never breaks blocks)
 *         "freeze": 3,                                  // seconds of slow and frost on what it hits
 *         "lightning": {"every": 3, "share": 0.8},      // every Nth full-draw shot calls lightning on what it hits
 *         "trail": [{"particle": "minecraft:flame", "count": 1, "chance": 1.0}]}
 * </pre>
 * The arrow's damage itself is the bow's "damage" stat through {@link SkyArrows}.
 */
public final class Bows {
    private static final String BOLT_TAG = "skyrpg_bolt";
    private static final int MAX_FLIGHT = 160;

    private record Tracked(AbstractArrow arrow, JsonObject bow, boolean vanish) {
    }

    private static final List<Tracked> FLYING = new ArrayList<>();
    private static final Map<UUID, Integer> SHOTS = new HashMap<>();
    /** Players whose current draw already rang the "full draw" glint. */
    private static final java.util.Set<UUID> DRAWN = new java.util.HashSet<>();
    private static final Map<String, SimpleParticleType> PARTICLES = new HashMap<>();
    /** Stops a burst or a lightning strike from triggering effects of its own. */
    private static boolean busy;

    private Bows() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                drawTick(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!busy && damageTaken > 0 && source.getDirectEntity() instanceof AbstractArrow arrow && SkyArrows.of(arrow) != null
                    && source.getEntity() instanceof ServerPlayer owner && entity != owner && entity.level() instanceof ServerLevel level) {
                onHit(level, owner, arrow, entity, damageTaken);
            }
        });
        // A killing blow doesn't reach AFTER_DAMAGE (the target is already dying), but its burst, frost and lightning still count.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!busy && source.getDirectEntity() instanceof AbstractArrow arrow && SkyArrows.of(arrow) != null
                    && source.getEntity() instanceof ServerPlayer owner && entity != owner && entity.level() instanceof ServerLevel level) {
                float basis = (float) (SkyArrows.of(arrow).damage() / StatTypes.HEALTH_SCALE
                        * (1 + Math.max(0, Stats.get(owner, StatTypes.STRENGTH)) / 100));
                onHit(level, owner, arrow, entity, basis);
            }
        });
    }

    private static Identifier fx(String path) {
        return Identifier.fromNamespaceAndPath("skyrpg_abilities", path);
    }

    /** A glint and a click the moment a SkyRPG bow reaches full draw (once per draw), so you know when to let go. */
    private static void drawTick(ServerPlayer player) {
        ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (using.isEmpty() || !(using.getItem() instanceof SkyBowItem)) {
            DRAWN.remove(player.getUUID());
            return;
        }
        double speed = num(config(using), "draw_speed", 1.0);
        if (player.getTicksUsingItem() * speed >= 20 && DRAWN.add(player.getUUID())) {
            Vfx.play(player, fx("bow_ready"));
            com.skyrpg.core.text.Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.9f);
            com.skyrpg.core.text.Msg.playSound(player, SoundEvents.CROSSBOW_LOADING_END.value(), 0.5f, 1.4f);
        }
    }

    /** The {@code "bow"} object of a bow stack's definition (empty if it has none). */
    public static JsonObject config(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def != null && def.json().has("bow") ? def.json().getAsJsonObject("bow") : new JsonObject();
    }

    public static double num(JsonObject bow, String key, double fallback) {
        return bow.has(key) && bow.get(key).isJsonPrimitive() ? bow.get(key).getAsDouble() : fallback;
    }

    /** Called as each arrow leaves the bow: tags its damage and sets up what the bow does with it. */
    static void prepare(AbstractArrow arrow, ItemStack bowStack, ServerPlayer shooter, float power, int index) {
        ItemDefinition def = SkyItems.definition(bowStack);
        if (def == null) {
            return;
        }
        JsonObject bow = config(bowStack);
        boolean volley = num(bow, "arrows", 1) > 1;
        SkyArrows.tag(arrow, def.stat(StatTypes.DAMAGE) * Math.max(0.1, power) * (volley ? num(bow, "arrow_share", 1) : 1));
        int pierce = (int) num(bow, "pierce", 0);
        if (pierce > 0 && arrow instanceof AbstractArrowAccess access) {
            access.skyrpg$setPierceLevel((byte) pierce);
        }
        if (bow.has("fire") && bow.get("fire").getAsBoolean()) {
            arrow.igniteForSeconds(8);
        }
        if (volley) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        if (bow.has("lightning") && index == 0 && power >= 1.0f) {
            int every = (int) Math.max(1, num(bow.getAsJsonObject("lightning"), "every", 3));
            int shots = SHOTS.merge(shooter.getUUID(), 1, Integer::sum);
            if (shots % every == 0) {
                arrow.addTag(BOLT_TAG);
                Vfx.play(shooter, fx("arrow_lightning_shot"));
                Vfx.sound(shooter.level(), shooter.position(), "minecraft:entity.lightning_bolt.impact", 0.6f, 1.8f);
                Vfx.sound(shooter.level(), shooter.position(), "minecraft:item.trident.thunder", 0.5f, 1.6f);
                Vfx.shake(shooter, 0.5f, 6);
            }
        }
        if (volley && index == 0) {
            Vfx.play(shooter, fx("arrow_volley_shot"));
            for (int i = 0; i < (int) num(bow, "arrows", 1); i++) {
                Vfx.sound(shooter.level(), shooter.position(), "minecraft:block.amethyst_block.chime", 0.7f, 1.3f + 0.25f * i);
            }
            Vfx.sound(shooter.level(), shooter.position(), "minecraft:entity.firework_rocket.shoot", 0.5f, 1.5f);
        }
        if (power >= 1.0f && index == 0) {
            Vfx.shake(shooter, 0.18f, 4); // a full draw lets go with a kick
        }
        if (bow.has("trail") || bow.has("homing") || volley) {
            FLYING.add(new Tracked(arrow, bow, volley));
        }
    }

    private static void tick() {
        for (Iterator<Tracked> it = FLYING.iterator(); it.hasNext(); ) {
            Tracked tracked = it.next();
            AbstractArrow arrow = tracked.arrow();
            if (arrow.isRemoved() || !(arrow.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            Vec3 velocity = arrow.getDeltaMovement();
            boolean resting = velocity.lengthSqr() < 0.04 || arrow.tickCount > MAX_FLIGHT;
            if (resting) {
                if (tracked.vanish()) {
                    level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                    arrow.discard();
                }
                it.remove();
                continue;
            }
            trail(level, arrow, tracked.bow());
            double homing = num(tracked.bow(), "homing", 0);
            if (homing > 0 && arrow.tickCount > 2) {
                steer(level, arrow, velocity, homing);
            }
        }
    }

    private static void trail(ServerLevel level, AbstractArrow arrow, JsonObject bow) {
        if (!bow.has("trail")) {
            return;
        }
        for (JsonElement element : bow.getAsJsonArray("trail")) {
            JsonObject spec = element.getAsJsonObject();
            if (level.getRandom().nextDouble() >= num(spec, "chance", 1.0)) {
                continue;
            }
            SimpleParticleType type = particle(spec.get("particle").getAsString());
            if (type != null) {
                double spread = num(spec, "spread", 0.03);
                level.sendParticles(type, arrow.getX(), arrow.getY(), arrow.getZ(), (int) num(spec, "count", 1), spread, spread, spread, num(spec, "speed", 0.0));
            }
        }
    }

    /** Bends the arrow a little towards the nearest enemy in front of it. */
    private static void steer(ServerLevel level, AbstractArrow arrow, Vec3 velocity, double strength) {
        Vec3 heading = velocity.normalize();
        LivingEntity best = null;
        double bestScore = 0;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, arrow.getBoundingBox().inflate(14), e -> isTarget(e) && e != arrow.getOwner())) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(arrow.position());
            double distance = to.length();
            double facing = distance < 1e-3 ? 1 : to.normalize().dot(heading);
            if (facing < 0.5) {
                continue;
            }
            double score = facing / (1 + distance * 0.05);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            Vec3 wanted = best.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            double speed = velocity.length();
            arrow.setDeltaMovement(heading.scale(1 - strength).add(wanted.scale(strength)).normalize().scale(speed));
            arrow.syncVelocity = true;
        }
    }

    private static void onHit(ServerLevel level, ServerPlayer owner, AbstractArrow arrow, LivingEntity target, float taken) {
        JsonObject bow = config(arrow.getWeaponItem());
        busy = true;
        try {
            // What the hit looks like: the bow's own flavour, else a plain spark pop.
            boolean explodes = bow.has("burst");
            String hitFx = explodes ? "arrow_burst" : bow.has("freeze") ? "arrow_hit_frost" : num(bow, "arrows", 1) > 1 ? "arrow_hit_star"
                    : num(bow, "pierce", 0) > 0 ? "arrow_hit_pierce" : "arrow_hit";
            Vfx.play(level, fx(hitFx), target.position());
            if (hitFx.equals("arrow_hit_star")) {
                Vfx.sound(level, target.position(), "minecraft:block.amethyst_block.break", 0.6f, 1.5f);
            } else if (hitFx.equals("arrow_hit_pierce")) {
                Vfx.sound(level, target.position(), "minecraft:entity.player.attack.sweep", 0.5f, 1.8f);
            }
            if (explodes) {
                Vfx.shake(owner, 0.45f, 6);
            }
            if (bow.has("burst")) {
                JsonObject burst = bow.getAsJsonObject("burst");
                double radius = num(burst, "radius", 2.5);
                float share = (float) num(burst, "share", 0.5);
                Vec3 at = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                        e -> e != target && e != owner && isTarget(e) && e.isAlive() && e.distanceTo(target) <= radius)) {
                    other.igniteForSeconds(3);
                    other.hurtServer(level, level.damageSources().explosion(arrow, owner), taken * share);
                }
            }
            if (bow.has("freeze")) {
                freeze(target, bow.get("freeze").getAsDouble());
            }
            if (arrow.entityTags().contains(BOLT_TAG)) {
                arrow.removeTag(BOLT_TAG);
                Shields.bolt(level, target.position());
                Vfx.play(level, fx("thunder_strike"), target.position());
                Vfx.shake(owner, 0.55f, 7);
                target.hurtServer(level, level.damageSources().indirectMagic(arrow, owner),
                        taken * (float) num(bow.getAsJsonObject("lightning"), "share", 0.8));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 24, 0.4, 0.6, 0.4, 0.3);
            }
        } finally {
            busy = false;
        }
    }

    /** Slows and frosts something for a few seconds (the Frostbite Bow's arrows, the Frostward Barrier's blocks). */
    public static void freeze(LivingEntity entity, double seconds) {
        int ticks = (int) (seconds * 20);
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 4, false, true));
        entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + ticks));
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY(0.6), entity.getZ(), 18, 0.35, 0.5, 0.35, 0.03);
            level.playSound(null, entity.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.4f, 1.8f);
        }
    }

    private static boolean isTarget(LivingEntity entity) {
        return entity instanceof Enemy || entity instanceof Mannequin;
    }

    private static SimpleParticleType particle(String id) {
        return PARTICLES.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            return parsed != null && BuiltInRegistries.PARTICLE_TYPE.getValue(parsed) instanceof SimpleParticleType simple ? simple : null;
        });
    }

    /** Shot counters go with the player. */
    public static void forget(UUID player) {
        SHOTS.remove(player);
        DRAWN.remove(player);
    }
}
