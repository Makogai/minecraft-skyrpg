package com.skyrpg.combat.ability;

import com.google.gson.JsonObject;
import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.vfx.Vfx;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The sword abilities, {@code skyrpg_combat:starfall} and {@code skyrpg_combat:thunderclap} (the {@code skyrpg_items} mod
 * does mana, cooldown and messages; see docs/design/abilities.md for the whole choreography).
 *
 * <ul>
 *   <li><b>Starfall</b> {@code {"radius": 6, "damage": 80, "stagger": 2, "max_targets": 14}}: a star circle opens under you, every
 *       hostile mob in range gets a target ring, and a star falls out of the sky onto each, nearest first, one every
 *       {@code stagger} ticks. A star deals its damage when it lands (damage numbers, kill credit and loot are yours), knocks
 *       the mob up and away, and rings a bell note that climbs with every star of the volley. A volley of three or more ends
 *       with a bloom and a screen flash.</li>
 *   <li><b>Thunderclap</b> {@code {"radius": 5, "damage": 40, "stagger": 2, "max_targets": 12, "stun": 1.5}}: a shockwave ring, then
 *       lightning on every hostile mob in range, nearest first, a spark arcing from each to the next. Struck mobs are
 *       shocked (slowed hard) for {@code stun} seconds.</li>
 *   <li><b>Void Rend</b> (the Voidreaver) {@code {"radius": 8, "damage": 150, "max_targets": 16, "pull_ticks": 14, "wither": 3}}: a rift
 *       opens a few blocks in front of you and drags every hostile mob in range towards it (bosses and heavy mobs resist in
 *       proportion to their knockback resistance), then collapses: damage, Wither II and a shockwave on each.</li>
 * </ul>
 */
public final class WeaponAbilities {
    private static final int FALL_TICKS = 8;
    private static final Identifier STARFALL_CAST = id("starfall_cast");
    private static final Identifier STARFALL_MARKER = id("starfall_marker");
    private static final Identifier STARFALL_METEOR = id("starfall_meteor");
    private static final Identifier STARFALL_IMPACT = id("starfall_impact");
    private static final Identifier STARFALL_FINALE = id("starfall_finale");
    private static final Identifier THUNDER_CAST = id("thunder_cast");
    private static final Identifier THUNDER_STRIKE = id("thunder_strike");

    private WeaponAbilities() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("skyrpg_abilities", path);
    }

    public static void init() {
        AbilityHandlers.register("skyrpg_combat:starfall", WeaponAbilities::starfall);
        AbilityHandlers.register("skyrpg_combat:thunderclap", WeaponAbilities::thunderclap);
        AbilityHandlers.register("skyrpg_combat:void_rend", WeaponAbilities::voidRend);
    }

    private static double num(JsonObject params, String key, double fallback) {
        return params.has(key) ? params.get(key).getAsDouble() : fallback;
    }

    /** Hostile mobs (and training dummies) near the player, nearest first, at most {@code max}. */
    static List<LivingEntity> targets(ServerPlayer player, double radius, int max) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, radius, radius),
                        e -> (e instanceof Enemy || e instanceof Mannequin) && e.isAlive() && e != player && e.distanceTo(player) <= radius)
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player))).limit(max).toList();
    }

    private static boolean starfall(ServerPlayer player, JsonObject params) {
        double radius = num(params, "radius", 6);
        float damage = (float) (num(params, "damage", 80) / StatTypes.HEALTH_SCALE);
        int stagger = (int) Math.max(1, num(params, "stagger", 2));
        List<LivingEntity> hit = targets(player, radius, (int) num(params, "max_targets", 14));
        if (hit.isEmpty()) {
            player.sendOverlayMessage(Msg.text("Nothing to strike nearby", ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = player.level();
        var server = level.getServer();
        Vec3 at = player.position();
        // Wind-up: the star circle, the column of light, a rising chime.
        Vfx.play(player, STARFALL_CAST);
        Vfx.sound(level, at, "minecraft:block.beacon.activate", 0.8f, 1.9f);
        Vfx.sound(level, at, "minecraft:block.amethyst_block.resonate", 1.0f, 1.5f);
        Vfx.sound(level, at, "minecraft:entity.evoker.cast_spell", 0.6f, 1.6f);
        Vfx.shake(player, 0.3f, 8);
        int count = hit.size();
        int last = (count - 1) * stagger;
        for (int i = 0; i < count; i++) {
            LivingEntity target = hit.get(i);
            Vfx.play(level, STARFALL_MARKER, target.position());
            int index = i;
            // Each star leaves the sky as it is its turn and lands FALL_TICKS later.
            Vfx.later(server, i * stagger, () -> {
                if (target.isAlive()) {
                    Vfx.play(level, STARFALL_METEOR, target.position());
                    Vfx.sound(level, target.position(), "minecraft:entity.firework_rocket.launch", 0.5f, 1.7f);
                }
            });
            Vfx.later(server, i * stagger + FALL_TICKS, () -> land(player, target, damage, index, count));
        }
        if (count >= 3) {
            Vfx.later(server, last + FALL_TICKS + 2, () -> {
                Vfx.play(player, STARFALL_FINALE);
                Vfx.sound(level, player.position(), "minecraft:entity.firework_rocket.large_blast", 0.9f, 1.0f);
                Vfx.sound(level, player.position(), "minecraft:block.amethyst_cluster.break", 1.0f, 0.8f);
                Vfx.flash(player, 0x55FFF4D0, 8);
                Vfx.shake(player, 1.1f, 12);
            });
        }
        return true;
    }

    /** One star lands: damage, knock-up, impact effect, a bell note that climbs the scale with each star. */
    private static void land(ServerPlayer player, LivingEntity target, float damage, int index, int total) {
        ServerLevel level = player.level();
        if (player.isRemoved() || !target.isAlive() || target.level() != level) {
            return;
        }
        Vec3 pos = target.position();
        Vfx.play(level, STARFALL_IMPACT, pos);
        float pitch = (float) Math.min(2.0, 0.9 + index * 0.07);
        float volume = index == 0 ? 1.0f : 0.75f;
        Vfx.sound(level, pos, "minecraft:entity.firework_rocket.blast", volume, 1.2f);
        Vfx.sound(level, pos, "minecraft:block.amethyst_block.break", volume, pitch);
        Vfx.sound(level, pos, "minecraft:block.note_block.chime", volume * 0.9f, pitch);
        Vfx.sound(level, pos, "minecraft:entity.generic.explode", 0.3f, 1.8f);
        target.hurtServer(level, player.damageSources().playerAttack(player), damage);
        // A star hits from above: up and a little away from the sword.
        if (target.isAlive()) {
            Vec3 away = pos.subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.25);
            push(target, away.x, 0.42, away.z);
        }
        Vfx.shakeAround(level, pos, 16, total > 1 ? 0.45f : 0.7f, 6);
    }

    /** The Voidreaver's ability: a rift drags hostile mobs in for {@code pull_ticks}, then collapses on them. */
    private static boolean voidRend(ServerPlayer player, JsonObject params) {
        double radius = num(params, "radius", 8);
        float damage = (float) (num(params, "damage", 150) / StatTypes.HEALTH_SCALE);
        int pullTicks = (int) Math.max(4, num(params, "pull_ticks", 14));
        int wither = (int) (num(params, "wither", 3) * 20);
        List<LivingEntity> hit = targets(player, radius, (int) num(params, "max_targets", 16));
        if (hit.isEmpty()) {
            player.sendOverlayMessage(Msg.text("Nothing to rend nearby", ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = player.level();
        var server = level.getServer();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 rift = player.position().add(look.scale(3.5)).add(0, 1.2, 0);
        Vfx.sound(level, rift, "minecraft:block.end_portal.spawn", 0.7f, 1.6f);
        Vfx.sound(level, rift, "minecraft:entity.enderman.scream", 0.6f, 0.5f);
        Vfx.shake(player, 0.4f, pullTicks);
        for (int t = 0; t < pullTicks; t++) {
            int tick = t;
            Vfx.later(server, t, () -> {
                if (player.isRemoved() || player.level() != level) {
                    return;
                }
                riftSwirl(level, rift, tick, pullTicks);
                for (LivingEntity target : hit) {
                    if (!target.isAlive() || target.level() != level) {
                        continue;
                    }
                    Vec3 to = rift.subtract(target.position().add(0, target.getBbHeight() * 0.5, 0));
                    double dist = to.length();
                    if (dist > 1.2) {
                        Vec3 pull = to.scale(Math.min(0.55, 0.12 + dist * 0.05) / dist);
                        push(target, pull.x, pull.y * 0.6 + 0.04, pull.z);
                    }
                    if (tick % 3 == 0) {
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3, 0.2, 0.3, 0.2, 0.15);
                    }
                }
            });
        }
        Vfx.later(server, pullTicks, () -> {
            if (player.isRemoved() || player.level() != level) {
                return;
            }
            Vfx.sound(level, rift, "minecraft:entity.generic.explode", 0.9f, 0.6f);
            Vfx.sound(level, rift, "minecraft:entity.warden.sonic_boom", 0.5f, 1.4f);
            Vfx.sound(level, rift, "minecraft:block.end_portal_frame.fill", 1.0f, 0.6f);
            for (int ring = 1; ring <= 3; ring++) {
                double r = ring * 1.6;
                for (int i = 0; i < 28; i++) {
                    double a = i * Math.PI * 2 / 28;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x + Math.cos(a) * r, rift.y, rift.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.15);
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, rift.x, rift.y, rift.z, 1, 0, 0, 0, 0);
            level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), rift.x, rift.y, rift.z, 40, 1.4, 0.8, 1.4, 0.04);
            Vfx.shakeAround(level, rift, 20, 1.0f, 10);
            Vfx.flash(player, 0x44B06AE8, 8);
            for (LivingEntity target : hit) {
                if (!target.isAlive() || target.level() != level) {
                    continue;
                }
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                if (target.isAlive()) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, wither, 1));
                    Vec3 out = target.position().subtract(rift).multiply(1, 0, 1);
                    out = out.lengthSqr() < 1e-4 ? Vec3.ZERO : out.normalize().scale(0.35);
                    push(target, out.x, 0.35, out.z);
                }
                level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), target.getX(), target.getY() + 1, target.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            }
        });
        return true;
    }

    /** The rift while it pulls: a flat violet disc turning, motes spiralling in, a dark core. */
    private static void riftSwirl(ServerLevel level, Vec3 rift, int tick, int total) {
        double spin = tick * 0.55;
        double reach = 2.6 - 1.6 * tick / total;
        for (int arm = 0; arm < 3; arm++) {
            double a = spin + arm * Math.PI * 2 / 3;
            for (int k = 0; k < 3; k++) {
                double r = reach * (1 - k * 0.28);
                double aa = a + k * 0.5;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x + Math.cos(aa) * r, rift.y + Math.sin(aa * 2) * 0.15, rift.z + Math.sin(aa) * r, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, rift.x, rift.y, rift.z, 4, 0.25, 0.25, 0.25, 0.4);
        if (tick % 4 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK, rift.x, rift.y, rift.z, 2, 0.15, 0.15, 0.15, 0.01);
            Vfx.sound(level, rift, "minecraft:block.portal.ambient", 0.5f, 1.2f + 0.8f * tick / total);
        }
    }

    private static boolean thunderclap(ServerPlayer player, JsonObject params) {
        double radius = num(params, "radius", 5);
        float damage = (float) (num(params, "damage", 40) / StatTypes.HEALTH_SCALE);
        int stagger = (int) Math.max(1, num(params, "stagger", 2));
        double stun = num(params, "stun", 1.5);
        List<LivingEntity> hit = targets(player, radius, (int) num(params, "max_targets", 12));
        if (hit.isEmpty()) {
            player.sendOverlayMessage(Msg.text("Nothing to strike nearby", ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = player.level();
        var server = level.getServer();
        Vec3 at = player.position();
        Vfx.play(player, THUNDER_CAST);
        Vfx.sound(level, at, "minecraft:entity.lightning_bolt.impact", 0.7f, 1.6f);
        Vfx.sound(level, at, "minecraft:item.trident.thunder", 0.8f, 1.5f);
        Vfx.sound(level, at, "minecraft:entity.breeze.wind_burst", 0.8f, 1.2f);
        Vfx.shake(player, 0.4f, 8);
        int count = hit.size();
        for (int i = 0; i < count; i++) {
            LivingEntity target = hit.get(i);
            LivingEntity previous = i == 0 ? null : hit.get(i - 1);
            Vfx.later(server, 3 + i * stagger, () -> strike(player, target, previous, damage, stun));
        }
        if (count >= 3) {
            Vfx.later(server, 3 + (count - 1) * stagger + 2, () -> {
                Vfx.flash(player, 0x44DCEBFF, 6);
                Vfx.shake(player, 0.9f, 9);
                Vfx.sound(level, player.position(), "minecraft:entity.lightning_bolt.thunder", 0.8f, 1.3f);
            });
        }
        return true;
    }

    /** Lightning on one mob: bolt, impact, shock, and a spark arcing in from the one struck before it. */
    private static void strike(ServerPlayer player, LivingEntity target, LivingEntity previous, float damage, double stun) {
        ServerLevel level = player.level();
        if (player.isRemoved() || !target.isAlive() || target.level() != level) {
            return;
        }
        Vec3 pos = target.position();
        bolt(level, pos);
        Vfx.play(level, THUNDER_STRIKE, pos);
        Vfx.sound(level, pos, "minecraft:entity.lightning_bolt.impact", 0.7f, 1.4f);
        Vfx.sound(level, pos, "minecraft:entity.firework_rocket.blast", 0.6f, 1.8f);
        if (previous != null && previous.isAlive()) {
            arc(level, previous.getBoundingBox().getCenter(), target.getBoundingBox().getCenter());
        }
        target.hurtServer(level, player.damageSources().playerAttack(player), damage);
        if (target.isAlive() && stun > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (stun * 20), 3, false, true));
            Vec3 away = pos.subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.3);
            push(target, away.x, 0.25, away.z);
        }
        Vfx.shakeAround(level, pos, 14, 0.4f, 5);
    }

    private static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.setPos(at.x, at.y, at.z);
            level.addFreshEntity(bolt);
        }
    }

    /** A jittery line of electric sparks between two points. */
    private static void arc(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int steps = Math.max(2, (int) (line.length() * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(line.scale((double) i / steps));
            double j = i == 0 || i == steps ? 0 : 0.18;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + (level.getRandom().nextDouble() - 0.5) * j * 2,
                    p.y + (level.getRandom().nextDouble() - 0.5) * j * 2, p.z + (level.getRandom().nextDouble() - 0.5) * j * 2, 1, 0, 0, 0, 0);
        }
    }

    /** A shove that bosses and heavy mobs shrug off in proportion to their knockback resistance. */
    private static void push(LivingEntity target, double x, double y, double z) {
        double resist = 1 - Math.min(1, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (resist <= 0) {
            return;
        }
        target.push(x * resist, y * resist, z * resist);
        target.syncVelocity = true;
    }
}
