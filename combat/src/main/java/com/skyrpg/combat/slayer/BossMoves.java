package com.skyrpg.combat.slayer;

import com.skyrpg.combat.slayer.boss.GraveTyrantEntity;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The slayer bosses' signature moves, Hypixel style: every one is announced and shown on the ground before it lands, so a
 * player who watches can dodge it. Some leave things to smash in time ("totems": a display entity you see plus an
 * invisible hitbox you hit):
 * <ul>
 *   <li>Grave Tyrant: Grave Slam (a roar, a leap, a shockwave) from I; Enrage below half health (faster, harder hits);
 *   IV gravestones crashing down on marked circles.</li>
 *   <li>Broodmother: Pounce from range (a hiss, a leap, a landing burst) from I; II-IV egg sacs at two-thirds and
 *   one-third health that hatch spiderlings unless smashed in six seconds.</li>
 *   <li>Bone Marshal: volleys from I; II-IV Arrow Rain on a marked circle; III-IV a War Banner below half health (it takes
 *   half damage and heals while the banner stands).</li>
 *   <li>Void Stalker: Void Bolts from I; III-IV Void Beacons that blow up for half your health unless broken in six
 *   seconds.</li>
 * </ul>
 */
final class BossMoves {
    static final String TOTEM_TAG = "skyrpg_slayer_totem";
    private static final Identifier ENRAGE_SPEED = Identifier.fromNamespaceAndPath("skyrpg_combat", "enrage_speed");
    private static final Identifier ENRAGE_DAMAGE = Identifier.fromNamespaceAndPath("skyrpg_combat", "enrage_damage");

    private record Task(long due, SlayerBosses.Run run, Runnable action) {
    }

    /** Something a boss put down: what you see, what you hit, and when it goes off if nobody breaks it. */
    static final class Totem {
        final SlayerBosses.Run run;
        final String kind;
        final Entity display;
        final Entity hitbox;
        final long due;
        int hits;

        Totem(SlayerBosses.Run run, String kind, Entity display, Entity hitbox, long due, int hits) {
            this.run = run;
            this.kind = kind;
            this.display = display;
            this.hitbox = hitbox;
            this.due = due;
            this.hits = hits;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    static final List<Totem> TOTEMS = new ArrayList<>();

    private BossMoves() {
    }

    static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || !entity.entityTags().contains(TOTEM_TAG)) {
                return InteractionResult.PASS;
            }
            for (Totem totem : TOTEMS) {
                if (totem.hitbox == entity) {
                    strikeTotem(totem, (ServerPlayer) player);
                    break;
                }
            }
            return InteractionResult.SUCCESS;
        });
    }

    // ---------------------------------------------------------------- bookkeeping

    static void later(SlayerBosses.Run run, int ticks, Runnable action) {
        TASKS.add(new Task(SlayerBosses.now() + ticks, run, action));
    }

    static void tick(long now) {
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.due() <= now) {
                    it.remove();
                    due.add(task);
                }
            }
            for (Task task : due) {
                if (SlayerBosses.active(task.run()) && task.run().boss.isAlive()) {
                    task.action().run();
                }
            }
        }
        for (Iterator<Totem> it = TOTEMS.iterator(); it.hasNext(); ) {
            Totem totem = it.next();
            if (!SlayerBosses.active(totem.run) || !totem.run.boss.isAlive()) {
                remove(totem);
                it.remove();
                continue;
            }
            visuals(totem, now);
            if (totem.due > 0 && now >= totem.due) {
                it.remove();
                remove(totem);
                goOff(totem);
            }
        }
    }

    /** The boss is gone (dead, failed, left): everything it put down goes with it. */
    static void clear(SlayerBosses.Run run) {
        TASKS.removeIf(task -> task.run() == run);
        TOTEMS.removeIf(totem -> {
            if (totem.run == run) {
                remove(totem);
                return true;
            }
            return false;
        });
    }

    static boolean hasBanner(SlayerBosses.Run run) {
        for (Totem totem : TOTEMS) {
            if (totem.run == run && totem.kind.equals("banner")) {
                return true;
            }
        }
        return false;
    }

    private static void remove(Totem totem) {
        if (totem.display != null) {
            totem.display.discard();
        }
        if (totem.hitbox != null) {
            totem.hitbox.discard();
        }
    }

    /** Spawns an item display and a hitbox over it, tagged so a restart's sweep can clean them up. */
    private static Totem totem(SlayerBosses.Run run, String kind, Vec3 at, String item, float scale, float width, float height, int ticks, int hits) {
        ServerLevel level = run.level;
        String tag = TOTEM_TAG + "_" + UUID.randomUUID().toString().substring(0, 8);
        String pos = String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", at.x, at.y, at.z);
        command(level, "summon item_display " + pos + " {item:{id:\"" + item + "\",count:1},item_display:\"none\",Glowing:1b,"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f," + (height / 2) + "f,0f],"
                + "scale:[" + scale + "f," + scale + "f," + scale + "f]},brightness:{sky:15,block:15},Tags:[\"" + TOTEM_TAG + "\",\"" + tag + "\"]}");
        command(level, "summon interaction " + pos + " {width:" + width + "f,height:" + height + "f,response:1b,Tags:[\"" + TOTEM_TAG + "\",\"" + tag + "\"]}");
        Entity display = null;
        Entity hitbox = null;
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(2), e -> e.entityTags().contains(tag))) {
            if (entity.getType() == EntityTypes.INTERACTION) {
                hitbox = entity;
            } else {
                display = entity;
            }
        }
        Totem totem = new Totem(run, kind, display, hitbox, ticks > 0 ? SlayerBosses.now() + ticks : 0, hits);
        TOTEMS.add(totem);
        return totem;
    }

    private static void command(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }

    private static void strikeTotem(Totem totem, ServerPlayer player) {
        totem.hits--;
        Vec3 at = totem.hitbox.position().add(0, 0.6, 0);
        ServerLevel level = totem.run.level;
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.7f, 1.4f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.2);
        if (totem.hits > 0) {
            return;
        }
        TOTEMS.remove(totem);
        remove(totem);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.05);
        String what = switch (totem.kind) {
            case "egg" -> "smashed an egg sac!";
            case "banner" -> "tore down the War Banner!";
            case "beacon" -> "broke a Void Beacon!";
            default -> "broke it!";
        };
        SlayerBosses.tell(totem.run, Component.literal(player.getName().getString() + " " + what).withStyle(ChatFormatting.GREEN));
        SlayerBosses.updateBar(totem.run);
    }

    private static void visuals(Totem totem, long now) {
        if (totem.hitbox == null) {
            return;
        }
        Vec3 at = totem.hitbox.position();
        ServerLevel level = totem.run.level;
        switch (totem.kind) {
            case "egg" -> {
                if (now % 10 == 0) {
                    level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.6, at.z, 3, 0.25, 0.2, 0.25, 0.02);
                }
            }
            case "banner" -> {
                if (now % 20 == 0) {
                    Vec3 boss = totem.run.boss.position().add(0, 1.2, 0);
                    Vec3 top = at.add(0, 2.2, 0);
                    for (int i = 0; i <= 10; i++) {
                        Vec3 p = top.lerp(boss, i / 10.0);
                        level.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                    }
                    totem.run.boss.heal(totem.run.boss.getMaxHealth() * 0.01f);
                }
            }
            case "beacon" -> {
                if (now % 2 == 0) {
                    for (int i = 0; i < 6; i++) {
                        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.2 + i * 1.5, at.z, 1, 0.05, 0.3, 0.05, 0);
                    }
                }
                if (now % 20 == 0) {
                    float pitch = 0.6f + 1.2f * (1 - (float) (totem.due - now) / 120f);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1.2f, pitch);
                }
            }
            default -> {
            }
        }
    }

    private static void goOff(Totem totem) {
        SlayerBosses.Run run = totem.run;
        Vec3 at = totem.hitbox == null ? run.boss.position() : totem.hitbox.position();
        switch (totem.kind) {
            case "egg" -> {
                run.level.playSound(null, at.x, at.y, at.z, SoundEvents.TURTLE_EGG_HATCH, SoundSource.HOSTILE, 1.0f, 0.6f);
                ServerPlayer owner = run.level.getServer().getPlayerList().getPlayer(run.owner);
                if (owner != null) {
                    SlayerBosses.raiseAt(run, owner, EntityTypes.CAVE_SPIDER, 2, at);
                }
                SlayerBosses.tell(run, Msg.text("An egg sac hatches!", ChatFormatting.DARK_RED));
            }
            case "beacon" -> {
                run.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
                run.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.6f);
                for (ServerPlayer player : near(run.level, at, 8)) {
                    player.hurtServer(run.level, run.boss.damageSources().indirectMagic(run.boss, run.boss), player.getMaxHealth() * 0.5f);
                }
                SlayerBosses.tell(run, Msg.text("A Void Beacon detonates!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            }
            default -> {
            }
        }
    }

    private static List<ServerPlayer> near(ServerLevel level, Vec3 at, double range) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(range),
                p -> !p.isSpectator() && !p.isCreative() && p.position().distanceTo(at) <= range);
    }

    private static void warn(SlayerBosses.Run run, String text, ChatFormatting color) {
        for (ServerPlayer player : SlayerBosses.players(run, 30)) {
            player.sendOverlayMessage(Msg.text("⚠ " + text, color, ChatFormatting.BOLD));
        }
    }

    /** A ring on the ground, shown several times so it reads as a warning. */
    private static void markCircle(SlayerBosses.Run run, Vec3 at, double radius, int ticks, net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i <= ticks; i += 5) {
            later(run, i, () -> SlayerBosses.ring(run.level, particle, at.add(0, 0.15, 0), radius, (int) (radius * 10)));
        }
    }

    // ---------------------------------------------------------------- Grave Tyrant

    static void graveSlam(SlayerBosses.Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        warn(run, "Grave Slam!", ChatFormatting.RED);
        if (boss instanceof GraveTyrantEntity tyrant) {
            tyrant.setSlamTicks(GraveTyrantEntity.SLAM_POSE_TICKS);
        }
        SlayerBosses.sound(run, SoundEvents.RAVAGER_ROAR, 1.3f, 0.7f);
        markCircle(run, boss.position(), 5.0, 25, ParticleTypes.FLAME);
        later(run, 10, () -> {
            Vec3 toward = owner.position().subtract(boss.position()).multiply(1, 0, 1);
            Vec3 push = toward.lengthSqr() > 0.01 ? toward.normalize().scale(0.45) : Vec3.ZERO;
            boss.setDeltaMovement(push.x, 0.8, push.z);
            boss.syncVelocity = true;
        });
        later(run, 30, () -> {
            Vec3 at = boss.position();
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), at.x, at.y + 0.2, at.z, 80,
                    2.5, 0.2, 2.5, 0.2);
            run.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.5, 0.2, 1.5, 0);
            SlayerBosses.sound(run, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
            float share = 0.1f + 0.03f * run.tierNumber;
            for (ServerPlayer player : near(run.level, at, 5)) {
                player.hurtServer(run.level, boss.damageSources().mobAttack(boss), player.getMaxHealth() * share);
                Vec3 away = player.position().subtract(at).multiply(1, 0, 1);
                Vec3 knock = away.lengthSqr() > 0.01 ? away.normalize().scale(1.1) : new Vec3(0.5, 0, 0);
                player.push(knock.x, 0.55, knock.z);
                player.syncVelocity = true;
            }
        });
    }

    static void enrage(SlayerBosses.Run run) {
        Mob boss = run.boss;
        modifier(boss.getAttribute(Attributes.MOVEMENT_SPEED), ENRAGE_SPEED, 0.35);
        modifier(boss.getAttribute(Attributes.ATTACK_DAMAGE), ENRAGE_DAMAGE, 0.25);
        SlayerBosses.sound(run, SoundEvents.RAVAGER_ROAR, 1.5f, 0.5f);
        warn(run, "The Grave Tyrant is ENRAGED!", ChatFormatting.DARK_RED);
        if (boss instanceof GraveTyrantEntity tyrant) {
            tyrant.setEnraged(true);
        }
        SlayerBosses.particles(run.level, ParticleTypes.ANGRY_VILLAGER, boss.position().add(0, 2, 0), 12, 0.8, 0);
    }

    private static void modifier(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute != null && attribute.getModifier(id) == null) {
            attribute.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    static void gravestones(SlayerBosses.Run run, ServerPlayer owner) {
        RandomSource random = run.level.getRandom();
        warn(run, "Gravestones are falling!", ChatFormatting.GRAY);
        for (int i = 0; i < 3; i++) {
            Vec3 at = i == 0 ? owner.position() : owner.position().add(random.nextDouble() * 8 - 4, 0, random.nextDouble() * 8 - 4);
            markCircle(run, at, 1.8, 35, ParticleTypes.SOUL);
            later(run, 40, () -> {
                run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_STONE_BRICKS.defaultBlockState()), at.x, at.y + 0.5,
                        at.z, 60, 0.8, 0.6, 0.8, 0.2);
                run.level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.6f);
                for (ServerPlayer player : near(run.level, at, 1.9)) {
                    player.hurtServer(run.level, run.boss.damageSources().indirectMagic(run.boss, run.boss), player.getMaxHealth() * 0.3f);
                }
            });
            for (int k = 0; k < 8; k++) {
                int delay = 32 + k;
                later(run, delay, () -> run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE_BRICKS.defaultBlockState()),
                        at.x, at.y + 8 - (delay - 32), at.z, 6, 0.4, 0.2, 0.4, 0));
            }
        }
    }

    // ---------------------------------------------------------------- Broodmother

    static void pounce(SlayerBosses.Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        double distance = boss.distanceTo(owner);
        if (distance < 4 || distance > 18) {
            return;
        }
        warn(run, "The Broodmother is about to pounce!", ChatFormatting.DARK_RED);
        SlayerAbilities.pounceVisual(run, com.skyrpg.combat.slayer.boss.BroodmotherEntity.POUNCE_POSE_TICKS);
        SlayerBosses.sound(run, SoundEvents.SPIDER_AMBIENT, 1.4f, 0.5f);
        SlayerBosses.particles(run.level, ParticleTypes.CRIMSON_SPORE, boss.position().add(0, 1, 0), 20, 0.6, 0.02);
        later(run, 15, () -> {
            Vec3 toward = owner.position().subtract(boss.position()).multiply(1, 0, 1);
            if (toward.lengthSqr() < 0.01) {
                return;
            }
            Vec3 leap = toward.normalize().scale(Math.min(1.6, 0.35 + toward.length() * 0.12));
            boss.setDeltaMovement(leap.x, 0.6, leap.z);
            boss.syncVelocity = true;
        });
        later(run, 35, () -> {
            Vec3 at = boss.position();
            run.level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.3, at.z, 40, 1.5, 0.2, 1.5, 0.05);
            SlayerBosses.sound(run, SoundEvents.SPIDER_STEP, 1.5f, 0.5f);
            for (ServerPlayer player : near(run.level, at, 3.2)) {
                player.hurtServer(run.level, boss.damageSources().mobAttack(boss), player.getMaxHealth() * (0.08f + 0.02f * run.tierNumber));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), boss);
            }
        });
    }

    static void eggSacs(SlayerBosses.Run run) {
        RandomSource random = run.level.getRandom();
        int count = run.tierNumber >= 3 ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 3 + random.nextDouble() * 2.5;
            Vec3 at = SlayerBosses.ground(run.level, run.boss.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
            totem(run, "egg", at, "minecraft:sniffer_egg", 1.1f, 1.0f, 1.0f, 120, 2);
        }
        warn(run, "Egg sacs! Smash them before they hatch!", ChatFormatting.RED);
        SlayerBosses.sound(run, SoundEvents.SNIFFER_EGG_CRACK, 1.5f, 0.6f);
    }

    // ---------------------------------------------------------------- Bone Marshal

    static void arrowRain(SlayerBosses.Run run, ServerPlayer owner) {
        Vec3 center = owner.position();
        double radius = 3.2;
        warn(run, "Arrow Rain! Get out of the circle!", ChatFormatting.WHITE);
        SlayerBosses.sound(run, SoundEvents.SKELETON_AMBIENT, 1.5f, 0.6f);
        markCircle(run, center, radius, 30, ParticleTypes.CRIT);
        RandomSource random = run.level.getRandom();
        for (int i = 0; i < 14; i++) {
            later(run, 32 + i, () -> {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * radius;
                Vec3 from = center.add(Math.cos(angle) * r, 14, Math.sin(angle) * r);
                Arrow arrow = new Arrow(run.level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
                arrow.setOwner(run.boss);
                arrow.setDeltaMovement(0, -2.4, 0);
                arrow.setBaseDamage(2 * run.tier.damage());
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.addTag(SlayerBosses.MINION_TAG);
                run.level.addFreshEntity(arrow);
                later(run, 80, arrow::discard);
            });
        }
        later(run, 32, () -> SlayerBosses.sound(run, SoundEvents.ARROW_SHOOT, 1.5f, 0.6f));
    }

    static void warBanner(SlayerBosses.Run run) {
        RandomSource random = run.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        Vec3 at = SlayerBosses.ground(run.level, run.boss.position().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4));
        totem(run, "banner", at, "minecraft:white_banner", 2.0f, 1.0f, 2.6f, 0, run.tierNumber >= 4 ? 5 : 3);
        warn(run, "The Bone Marshal plants its War Banner! Tear it down!", ChatFormatting.WHITE);
        SlayerBosses.sound(run, SoundEvents.RAID_HORN.value(), 1.0f, 1.2f);
        SlayerBosses.updateBar(run);
    }

    // ---------------------------------------------------------------- Void Stalker

    static void voidBolt(SlayerBosses.Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        if (boss.distanceTo(owner) > 20) {
            return;
        }
        Vec3 from = boss.getEyePosition();
        Vec3 to = owner.position().add(0, 1, 0);
        for (int i = 0; i <= 16; i++) {
            Vec3 at = from.lerp(to, i / 16.0);
            run.level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0);
        }
        SlayerBosses.sound(run, SoundEvents.ENDERMAN_SCREAM, 1.0f, 1.5f);
        owner.hurtServer(run.level, boss.damageSources().indirectMagic(boss, boss), owner.getMaxHealth() * (0.06f + 0.02f * run.tierNumber));
        owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), boss);
    }

    static void voidBeacon(SlayerBosses.Run run, ServerPlayer owner) {
        RandomSource random = run.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        Vec3 at = SlayerBosses.ground(run.level, owner.position().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5));
        totem(run, "beacon", at, "minecraft:beacon", 1.0f, 1.0f, 1.0f, 120, run.tierNumber >= 4 ? 4 : 3);
        warn(run, "A Void Beacon! Break it before it detonates!", ChatFormatting.LIGHT_PURPLE);
        SlayerBosses.sound(run, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
    }
}
