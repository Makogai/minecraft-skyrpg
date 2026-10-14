package com.skyrpg.items.ability;

import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.vfx.Vfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click abilities on items: looks up the held item's ability, checks its cooldown and mana (with a clear message and a
 * dull click when it can't be used), runs it, spends the mana, starts the cooldown (shown as vanilla's dark sweep over the item,
 * and a chime and "ready" message when a long one ends) and plays the ability's data-driven cast (see {@link AbilityFx}).
 * The abilities with their own choreography (Starfall, Thunderclap, Echo Sense, Treefeller) live in their mods; the ones
 * here are Riftstep and Mend. The whole list: docs/design/abilities.md.
 */
public final class Abilities {
    private static final Map<String, AbilityType> TYPES = new HashMap<>();
    /** Player -> item id -> server tick when the ability is ready again. */
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    /** Player -> server tick they were last told "no" (so holding the button does not machine-gun the message and click). */
    private static final Map<UUID, Long> NOTICED = new HashMap<>();
    /** Cooldowns that ended while the player might still be holding the item: they get a "ready" ping. */
    private static final List<Ready> READY = new ArrayList<>();
    /** Only cooldowns at least this long (seconds) announce that they are over. */
    private static final double READY_PING_MIN = 8;

    private record Ready(UUID player, Identifier item, String name, long at) {
    }

    private static final Identifier RIFT_DEPART = Identifier.fromNamespaceAndPath("skyrpg_abilities", "rift_depart");
    private static final Identifier RIFT_ARRIVE = Identifier.fromNamespaceAndPath("skyrpg_abilities", "rift_arrive");
    private static final Identifier RIFT_TRAIL = Identifier.fromNamespaceAndPath("skyrpg_abilities", "rift_trail");
    private static final Identifier RIFT_CUT = Identifier.fromNamespaceAndPath("skyrpg_abilities", "rift_cut");

    private Abilities() {
    }

    public static void register(String type, AbilityType ability) {
        TYPES.put(type, ability);
    }

    public static void init() {
        register("riftstep", Abilities::riftstep);
        register("mend", Abilities::mend);
        UseItemCallback.EVENT.register((player, level, hand) -> tryUse(player, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> tryUse(player, hand));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 4 == 0 && !READY.isEmpty()) {
                announceReady(server.getTickCount(), server);
            }
        });
    }

    private static InteractionResult tryUse(Player player, InteractionHand hand) {
        ItemDefinition def = SkyItems.definition(player.getItemInHand(hand));
        if (def == null || def.ability() == null || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // the server decides; don't also run the item's vanilla use
        }
        ItemDefinition.Ability ability = def.ability();
        AbilityType type = TYPES.get(ability.type());
        if (type == null) {
            // Other mods' ability types (core's AbilityHandlers), like skyrpg_mining:echo_sense.
            AbilityHandlers.Handler handler = AbilityHandlers.get(ability.type());
            if (handler == null) {
                return InteractionResult.PASS;
            }
            type = (user, used) -> handler.use(user, used.params());
        }
        long now = serverPlayer.level().getServer().getTickCount();
        Map<String, Long> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        long readyAt = cooldowns.getOrDefault(def.id().toString(), 0L);
        if (now < readyAt) {
            refuse(serverPlayer, now, ability.name() + " is recharging: " + Msg.number(Math.ceil((readyAt - now) / 2.0) / 10.0) + "s");
            return InteractionResult.FAIL;
        }
        if (ability.mana() > 0 && Stats.available() && Stats.mana(serverPlayer) < ability.mana()) {
            refuse(serverPlayer, now, "Not enough mana for " + ability.name() + ": " + Msg.number(Math.floor(Stats.mana(serverPlayer)))
                    + " / " + Msg.number(ability.mana()) + " " + StatTypes.MANA_ICON);
            return InteractionResult.FAIL;
        }
        if (!type.use(serverPlayer, ability)) {
            return InteractionResult.FAIL;
        }
        AbilityFx.cast(serverPlayer, ability);
        if (ability.mana() > 0) {
            Stats.spendMana(serverPlayer, ability.mana());
            serverPlayer.sendOverlayMessage(Msg.text("✦ " + ability.name() + "  -" + Msg.number(ability.mana()) + " " + StatTypes.MANA_ICON + " Mana", ChatFormatting.AQUA));
        } else {
            serverPlayer.sendOverlayMessage(Msg.text("✦ " + ability.name() + "!", ChatFormatting.GOLD));
        }
        if (ability.cooldownSeconds() > 0) {
            int ticks = (int) Math.round(ability.cooldownSeconds() * 20);
            cooldowns.put(def.id().toString(), now + ticks);
            serverPlayer.getCooldowns().addCooldown(serverPlayer.getItemInHand(hand), ticks);
            if (ability.cooldownSeconds() >= READY_PING_MIN) {
                READY.add(new Ready(player.getUUID(), def.id(), ability.name(), now + ticks));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Says why it didn't work, in red on the action bar with a dull click, at most five times a second. */
    private static void refuse(ServerPlayer player, long now, String message) {
        if (now - NOTICED.getOrDefault(player.getUUID(), -100L) < 6) {
            return;
        }
        NOTICED.put(player.getUUID(), now);
        player.sendOverlayMessage(Msg.text(message, ChatFormatting.RED));
        Msg.playSound(player, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, 0.6f, 1.3f);
    }

    /** "Starfall ready": a chime and a green action bar when a long cooldown ends while the item is in hand. */
    private static void announceReady(long now, net.minecraft.server.MinecraftServer server) {
        for (Iterator<Ready> it = READY.iterator(); it.hasNext(); ) {
            Ready ready = it.next();
            if (now < ready.at()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(ready.player());
            if (player == null) {
                continue;
            }
            ItemDefinition held = SkyItems.definition(player.getMainHandItem());
            if (held != null && held.id().equals(ready.item())) {
                player.sendOverlayMessage(Msg.text("✦ " + ready.name() + " ready", ChatFormatting.GREEN));
                Msg.playSound(player, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.9f);
                Msg.playSound(player, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.25f, 1.6f);
            }
        }
    }

    /**
     * Rift step: steps through a rift up to params.distance (default 8) blocks along the look direction, stopping before
     * anything solid. The rift's edge slices every hostile mob within params.width (1.4) blocks of the path for params.damage
     * (40), leaving a violet cut hanging in the air along the way.
     */
    private static boolean riftstep(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = player.level();
        double distance = ability.param("distance", 8);
        Vec3 look = player.getLookAngle();
        if (look.y < 0 && player.onGround()) {
            // Looking at the floor, you still dash along it (a downward ray would hit the ground at once).
            look = new Vec3(look.x, 0, look.z).normalize();
        }
        Vec3 start = player.position();
        Vec3 target = start;
        for (double d = 0.5; d <= distance; d += 0.5) {
            Vec3 candidate = start.add(look.scale(d));
            AABB box = player.getBoundingBox().move(candidate.subtract(start));
            // Walls stop the rift; mobs do not (it cuts through them), but you will not come out inside one.
            if (!level.noBlockCollision(player, box) || !level.noBorderCollision(player, box)) {
                break;
            }
            target = candidate;
        }
        while (target.distanceToSqr(start) >= 0.25 && !level.noEntityCollision(player, player.getBoundingBox().move(target.subtract(start)))) {
            target = target.subtract(look.scale(0.5));
        }
        if (target.distanceToSqr(start) < 0.25) {
            player.sendOverlayMessage(Msg.text("Something solid is in the way", ChatFormatting.RED));
            return false;
        }
        Vfx.play(level, RIFT_DEPART, start);
        Vfx.sound(level, start, "minecraft:entity.enderman.teleport", 0.7f, 0.8f);
        Vfx.sound(level, start, "minecraft:block.amethyst_cluster.break", 0.9f, 0.6f);
        // The cut hangs along the whole path, one puff of it a block.
        double length = start.distanceTo(target);
        for (double d = 1; d < length; d += 1) {
            Vfx.play(level, RIFT_TRAIL, start.add(target.subtract(start).scale(d / length)));
        }
        slice(player, start, target, ability.param("width", 1.4), (float) (ability.param("damage", 40) / StatTypes.HEALTH_SCALE));
        player.teleportTo(target.x, target.y, target.z);
        player.resetFallDistance();
        Vfx.play(level, RIFT_ARRIVE, target);
        Vfx.sound(level, target, "minecraft:entity.illusioner.mirror_move", 0.9f, 1.3f);
        Vfx.sound(level, target, "minecraft:entity.enderman.teleport", 0.5f, 1.7f);
        Vfx.sound(level, target, "minecraft:block.portal.trigger", 0.25f, 1.8f);
        Vfx.flash(player, 0x3AC490FF, 6);
        Vfx.shake(player, 0.35f, 6);
        return true;
    }

    /** Hurts every hostile mob close to the line from -> to. */
    private static void slice(ServerPlayer player, Vec3 from, Vec3 to, double width, float damage) {
        ServerLevel level = player.level();
        Vec3 line = to.subtract(from);
        double lengthSq = line.lengthSqr();
        AABB box = new AABB(from, to).inflate(width + 0.5, width + 1.0, width + 0.5);
        int hits = 0;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box, e -> (e instanceof Enemy || e instanceof Mannequin) && e.isAlive() && e != player)) {
            Vec3 center = mob.getBoundingBox().getCenter();
            double along = lengthSq < 1e-6 ? 0 : Math.max(0, Math.min(1, center.subtract(from).dot(line) / lengthSq));
            if (center.distanceTo(from.add(line.scale(along))) > width + mob.getBbWidth() * 0.5) {
                continue;
            }
            int index = hits++;
            Vfx.later(level.getServer(), 1 + Math.min(index, 8), () -> {
                if (!player.isRemoved() && mob.isAlive()) {
                    Vfx.play(level, RIFT_CUT, mob.position());
                    Vfx.sound(level, mob.position(), "minecraft:entity.player.attack.sweep", 0.8f, 1.4f);
                    Vfx.sound(level, mob.position(), "minecraft:block.amethyst_block.break", 0.7f, 0.9f + index * 0.08f);
                    mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
                    if (mob.isAlive()) {
                        double resist = 1 - Math.min(1, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                        mob.push(0, 0.3 * resist, 0);
                        mob.syncVelocity = true;
                    }
                }
            });
        }
    }

    /** Restores params.amount (default 30) health. */
    private static boolean mend(ServerPlayer player, ItemDefinition.Ability ability) {
        double amount = ability.param("amount", 30);
        if (player.getHealth() >= player.getMaxHealth()) {
            player.sendOverlayMessage(Msg.text("Already at full health", ChatFormatting.GRAY)); // no mana spent
            return false;
        }
        player.heal((float) (amount / StatTypes.HEALTH_SCALE));
        // The cast itself (rising helix, hearts, chimes) is the item's "cast" data; the sparkle at the wound is here.
        player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        return true;
    }

    /** Forgets a player's cooldowns when they leave. */
    public static void forget(UUID player) {
        COOLDOWNS.remove(player);
        NOTICED.remove(player);
        READY.removeIf(ready -> ready.player().equals(player));
    }
}
