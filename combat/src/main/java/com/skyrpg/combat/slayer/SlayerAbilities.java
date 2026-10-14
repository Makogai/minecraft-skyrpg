package com.skyrpg.combat.slayer;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.combat.slayer.boss.BoneMarshalEntity;
import com.skyrpg.combat.slayer.boss.BroodmotherEntity;
import com.skyrpg.combat.slayer.boss.VoidStalkerEntity;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Every slayer tier adds one attack and one defence to its boss, on top of the tiers below, so a tier IV fight uses
 * everything its line has. The catalogue ({@link #lore}) is what the slayer menu shows; the rest makes them happen.
 * The older signature moves (Grave Slam, Pounce, Arrow Rain, Void Bolt...) live in {@link BossMoves} and
 * {@link SlayerBosses}; this adds the newer ones and the defences, and tells the bosses' models what to show.
 */
public final class SlayerAbilities {
    /** One ability as the menu shows it. */
    public record Ability(boolean attack, String name, String description) {
    }

    /** What a tier is called and what it adds. */
    public record TierLore(String title, List<Ability> adds) {
    }

    private static final Map<String, List<TierLore>> LORE = Map.of(
            "grave_tyrant", List.of(
                    new TierLore("Risen Tyrant", List.of(
                            new Ability(true, "Grave Slam", "Raises its club and smashes the ground around it"),
                            new Ability(false, "Rotting Hide", "Arrows and other projectiles do half damage"))),
                    new TierLore("Plague Tyrant", List.of(
                            new Ability(true, "Raise the Dead", "Zombies claw out of the ground to help it"),
                            new Ability(false, "Life Drain", "Heals 2% of its health every time it hits you; enrages below half"))),
                    new TierLore("Dread Tyrant", List.of(
                            new Ability(true, "Gravestones", "Tombstones fall from the sky; break them before they burst"),
                            new Ability(false, "Bone Armour", "After every Grave Slam it takes 60% less damage for 3 seconds"))),
                    new TierLore("Tyrant King", List.of(
                            new Ability(true, "Plague Aura & Tomb Prison", "Rots everyone near it, and seals you in a stone tomb"),
                            new Ability(false, "Undying", "The first time it falls, it rises again with a third of its health")))),
            "broodmother", List.of(
                    new TierLore("Brood Mother", List.of(
                            new Ability(true, "Web Shot", "Spits silk that slows you to a crawl"),
                            new Ability(false, "Chitin Plates", "Takes 20% less damage from melee hits"))),
                    new TierLore("Venom Matron", List.of(
                            new Ability(true, "Pounce", "Crouches, then leaps onto you"),
                            new Ability(false, "Brood Shield", "Takes 70% less damage while three or more of her brood live"))),
                    new TierLore("Silk Empress", List.of(
                            new Ability(true, "Egg Sacs & Brood", "Lays egg sacs and hatches cave spiders"),
                            new Ability(false, "Silk Cocoon", "At half health she cocoons herself and heals; cut her out"))),
                    new TierLore("Brood Queen Arachnia", List.of(
                            new Ability(true, "Silk Prison & Venom Bite", "Webs you in place, then pounces; her bites poison"),
                            new Ability(false, "Venom Blood", "Hitting her up close poisons you")))),
            "bone_marshal", List.of(
                    new TierLore("Bone Sergeant", List.of(
                            new Ability(true, "Volley", "Fires three arrows at once"),
                            new Ability(false, "Bone Plating", "Can't be knocked back"))),
                    new TierLore("Bone Captain", List.of(
                            new Ability(true, "Arrow Rain", "Arrows rain down on a circle around you; volleys grow to five"),
                            new Ability(false, "Tactical Retreat", "Leaps away when you get too close"))),
                    new TierLore("Bone Commander", List.of(
                            new Ability(true, "Piercing Shot", "Draws its greatbow, then fires a shot that hits for 30%; break line of sight"),
                            new Ability(false, "War Banner", "Below half health it plants a banner: half damage and healing"))),
                    new TierLore("Bone Marshal Supreme", List.of(
                            new Ability(true, "Legion", "Calls archers and a bone knight to its side"),
                            new Ability(false, "Bone Shield", "Raises a bone shield that stops most damage for 4 seconds")))),
            "void_stalker", List.of(
                    new TierLore("Void Wanderer", List.of(
                            new Ability(true, "Void Bolt", "Blinks behind you and hurls bolts of void"),
                            new Ability(false, "Phase Step", "Slips out of reality to dodge 1 hit in 5"))),
                    new TierLore("Void Seeker", List.of(
                            new Ability(true, "Void Grasp", "Drags you towards it; its hits blind"),
                            new Ability(false, "Null Shield", "Shields itself at two thirds and one third health; hits break it"))),
                    new TierLore("Void Reaver", List.of(
                            new Ability(true, "Void Beacon", "Plants a beacon that detonates unless you break it"),
                            new Ability(false, "Mirror Images", "Splits off Void Echoes; takes half damage while they live"))),
                    new TierLore("Void Sovereign", List.of(
                            new Ability(true, "Rifts & Void Collapse", "Rifts burst under you; at a quarter health it collapses the void"),
                            new Ability(false, "Void Siphon", "Heals whenever its rifts or collapse hurt someone")))));

    private record TempBlock(ServerLevel level, BlockPos pos, Block block, long until) {
    }

    private static final List<TempBlock> TEMP_BLOCKS = new ArrayList<>();
    private static final String ECHO_TAG = "skyrpg_void_echo";

    private SlayerAbilities() {
    }

    /** The lore of tier {@code tier} (1-4) of a line kind, or null. */
    public static TierLore lore(String kind, int tier) {
        List<TierLore> tiers = LORE.get(kind);
        return tiers == null || tier < 1 || tier > tiers.size() ? null : tiers.get(tier - 1);
    }

    static void init() {
        // Damage that depends on what hit the boss: projectiles against Rotting Hide, melee against Chitin.
        IncomingDamage.registerHit(SlayerAbilities::hitMultiplier);
        // Phase Step: the Void Stalker sometimes isn't there to be hit. Silk Cocoon and the hits that count against it.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            SlayerBosses.Run run = entity instanceof Mob mob && mob.entityTags().contains(SlayerBosses.BOSS_TAG) ? SlayerBosses.byBoss(mob) : null;
            if (run == null || !fromPlayer(source)) {
                return true;
            }
            if (run.line.kind().equals("void_stalker") && run.level.getRandom().nextDouble() < 0.2) {
                phaseStep(run);
                return false;
            }
            return true;
        });
        // Undying: the Tyrant King gets back up once.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            SlayerBosses.Run run = entity instanceof Mob mob && mob.entityTags().contains(SlayerBosses.BOSS_TAG) ? SlayerBosses.byBoss(mob) : null;
            if (run == null || !run.line.kind().equals("grave_tyrant") || run.tierNumber < 4 || run.undyingUsed) {
                return true;
            }
            run.undyingUsed = true;
            run.boss.setHealth(run.boss.getMaxHealth() * 0.35f);
            SlayerBosses.particles(run.level, ParticleTypes.SOUL, run.boss.position().add(0, 1.5, 0), 80, 1.2, 0.08);
            SlayerBosses.particles(run.level, ParticleTypes.TOTEM_OF_UNDYING, run.boss.position().add(0, 1.5, 0), 60, 1.0, 0.4);
            SlayerBosses.sound(run, SoundEvents.WITHER_SPAWN, 1.2f, 0.6f);
            SlayerBosses.tell(run, Msg.text("The Tyrant King refuses to stay dead!", ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
            if (!run.enraged) {
                run.enraged = true;
                BossMoves.enrage(run);
            }
            SlayerBosses.updateBar(run);
            return false;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            SlayerBosses.Run run = entity instanceof Mob mob && mob.entityTags().contains(SlayerBosses.BOSS_TAG) ? SlayerBosses.byBoss(mob) : null;
            if (run == null || !fromPlayer(source)) {
                return;
            }
            // Cutting the Broodmother out of her cocoon.
            if (run.cocoonHits > 0) {
                run.cocoonHits--;
                SlayerBosses.particles(run.level, ParticleTypes.ITEM_COBWEB, run.boss.position().add(0, 1, 0), 12, 0.6, 0.05);
                if (run.cocoonHits == 0) {
                    endCocoon(run, true);
                }
                SlayerBosses.updateBar(run);
            }
            // Venom Blood: biting back at whoever hits her up close.
            if (run.line.kind().equals("broodmother") && run.tierNumber >= 4 && source.getDirectEntity() instanceof ServerPlayer player
                    && player.distanceTo(run.boss) < 5) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), run.boss);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearTempBlocks());
    }

    private static boolean fromPlayer(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer
                || source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer;
    }

    /** Rotting Hide (projectiles) and Chitin Plates (melee). */
    private static double hitMultiplier(LivingEntity entity, DamageSource source) {
        if (!entity.entityTags().contains(SlayerBosses.BOSS_TAG) || !(entity instanceof Mob mob)) {
            return 1;
        }
        SlayerBosses.Run run = SlayerBosses.byBoss(mob);
        if (run == null) {
            return 1;
        }
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        return switch (run.line.kind()) {
            case "grave_tyrant" -> projectile ? 0.5 : 1;
            case "broodmother" -> !projectile && source.getDirectEntity() instanceof ServerPlayer ? 0.8 : 1;
            default -> 1;
        };
    }

    /** Defences that cut all damage for a while (see SlayerBosses.damageTaken). */
    static double defence(SlayerBosses.Run run, long now) {
        double multiplier = 1;
        switch (run.line.kind()) {
            case "grave_tyrant" -> {
                if (run.tierNumber >= 3 && run.boneArmourUntil > now) {
                    multiplier *= 0.4;
                }
            }
            case "broodmother" -> {
                if (run.cocoonHits > 0) {
                    multiplier *= 0.05;
                } else if (run.tierNumber >= 2 && livingMinions(run) >= 3) {
                    multiplier *= 0.3;
                }
            }
            case "void_stalker" -> {
                if (run.tierNumber >= 3 && echoesAlive(run) > 0) {
                    multiplier *= 0.5;
                }
            }
            default -> {
            }
        }
        return multiplier;
    }

    // ---------------------------------------------------------------- the per-tick part of each fight

    /** Called every tick of a fight (after the older moves), with the fight's age and health share. */
    static void tick(SlayerBosses.Run run, ServerPlayer owner, long t, float health, boolean newPhase, long now) {
        Mob boss = run.boss;
        int tier = run.tierNumber;
        switch (run.line.kind()) {
            case "grave_tyrant" -> {
                if (tier >= 4 && t % 450 == 330) {
                    tombPrison(run, owner, now);
                }
            }
            case "broodmother" -> {
                if (tier >= 3 && !run.cocoonUsed && health < 0.5f) {
                    startCocoon(run, now);
                }
                if (run.cocoonHits > 0) {
                    boss.getNavigation().stop();
                    if (t % 20 == 0) {
                        boss.heal(boss.getMaxHealth() * 0.03f);
                        SlayerBosses.ring(run.level, ParticleTypes.ITEM_COBWEB, boss.position().add(0, 0.8, 0), 1.2, 14);
                    }
                    if (now >= run.cocoonUntil) {
                        endCocoon(run, false);
                    }
                }
                if (tier >= 4 && t % 400 == 250) {
                    silkPrison(run, owner, now);
                }
                if (boss instanceof BroodmotherEntity brood && t % 10 == 0) {
                    brood.setShielded(tier >= 2 && run.cocoonHits == 0 && livingMinions(run) >= 3);
                }
            }
            case "bone_marshal" -> {
                if (tier >= 2 && now >= run.retreatReadyAt && boss.distanceTo(owner) < 3.5) {
                    retreat(run, owner, now);
                }
                if (tier >= 3 && t % 320 == 180) {
                    piercingShot(run, owner);
                }
                if (tier >= 4 && t % 400 == 100) {
                    SlayerBosses.raiseAt(run, owner, EntityTypes.SKELETON, 3, boss.position());
                    if (SlayerBosses.raiseAt(run, owner, EntityTypes.WITHER_SKELETON, 1, boss.position()) > 0) {
                        SlayerBosses.sound(run, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.6f);
                        SlayerBosses.tell(run, Component.literal("The Bone Marshal calls its legion!").withColor(run.line.color() & 0xFFFFFF));
                    }
                }
                if (boss instanceof BoneMarshalEntity marshal && t % 10 == 0) {
                    marshal.setShielded(run.shieldUntil > now);
                    marshal.setBannerPlanted(BossMoves.hasBanner(run));
                }
            }
            case "void_stalker" -> {
                if (tier >= 2 && t % 300 == 40) {
                    voidGrasp(run, owner);
                }
                if (tier >= 3 && newPhase) {
                    mirrorImages(run, owner);
                }
                if (tier >= 4 && !run.collapseUsed && health < 0.25f) {
                    run.collapseUsed = true;
                    voidCollapse(run);
                }
                if (boss instanceof VoidStalkerEntity stalker && t % 10 == 0) {
                    stalker.setShielded(run.nullHits > 0 || echoesAlive(run) > 0);
                }
            }
            default -> {
            }
        }
    }

    /** Every tick of the server: temporary blocks melt away. */
    static void tickBlocks(long now) {
        for (Iterator<TempBlock> it = TEMP_BLOCKS.iterator(); it.hasNext(); ) {
            TempBlock temp = it.next();
            if (now >= temp.until()) {
                if (temp.level().getBlockState(temp.pos()).is(temp.block())) {
                    temp.level().setBlock(temp.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                it.remove();
            }
        }
    }

    static void clearTempBlocks() {
        TEMP_BLOCKS.forEach(temp -> {
            if (temp.level().getBlockState(temp.pos()).is(temp.block())) {
                temp.level().setBlock(temp.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        TEMP_BLOCKS.clear();
    }

    private static void tempBlock(ServerLevel level, BlockPos pos, Block block, long until) {
        BlockState here = level.getBlockState(pos);
        if (here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty()) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            TEMP_BLOCKS.add(new TempBlock(level, pos, block, until));
        }
    }

    // ---------------------------------------------------------------- Grave Tyrant

    /** Bone Armour (tier III): called right after a Grave Slam lands. */
    static void afterSlam(SlayerBosses.Run run) {
        if (run.tierNumber >= 3) {
            run.boneArmourUntil = SlayerBosses.now() + 60;
            SlayerBosses.particles(run.level, ParticleTypes.WHITE_ASH, run.boss.position().add(0, 1.2, 0), 40, 0.8, 0.02);
            SlayerBosses.sound(run, SoundEvents.SKELETON_HURT, 1.2f, 0.5f);
        }
    }

    /** Tomb Prison (tier IV): a shell of stone around you for three seconds. Break out or wait. */
    private static void tombPrison(SlayerBosses.Run run, ServerPlayer owner, long now) {
        if (owner.distanceTo(run.boss) > 16) {
            return;
        }
        warn(run, owner, "The Tyrant King seals you in a tomb!", ChatFormatting.DARK_GREEN);
        BlockPos feet = owner.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    boolean wall = dx != 0 || dz != 0;
                    if (wall || dy == 2) {
                        tempBlock(run.level, feet.offset(dx, dy, dz), Blocks.CHISELED_TUFF_BRICKS, now + 60);
                    }
                }
            }
        }
        owner.hurtServer(run.level, run.boss.damageSources().indirectMagic(run.boss, run.boss), owner.getMaxHealth() * 0.08f);
        SlayerBosses.sound(run, SoundEvents.DEEPSLATE_PLACE, 1.4f, 0.6f);
    }

    // ---------------------------------------------------------------- Broodmother

    private static int livingMinions(SlayerBosses.Run run) {
        run.minions.removeIf(minion -> !minion.isAlive());
        return run.minions.size();
    }

    private static void startCocoon(SlayerBosses.Run run, long now) {
        run.cocoonUsed = true;
        run.cocoonHits = 6 + 2 * (run.tierNumber - 3);
        run.cocoonUntil = now + 120;
        if (run.boss instanceof BroodmotherEntity brood) {
            brood.setCocooned(true);
        }
        SlayerBosses.sound(run, SoundEvents.SPIDER_AMBIENT, 1.4f, 0.4f);
        SlayerBosses.tell(run, Msg.text("The Broodmother wraps herself in silk! Cut her out (" + run.cocoonHits + " hits)", ChatFormatting.WHITE));
        SlayerBosses.updateBar(run);
    }

    private static void endCocoon(SlayerBosses.Run run, boolean cut) {
        run.cocoonHits = 0;
        if (run.boss instanceof BroodmotherEntity brood) {
            brood.setCocooned(false);
        }
        SlayerBosses.particles(run.level, ParticleTypes.ITEM_COBWEB, run.boss.position().add(0, 1, 0), 60, 1.0, 0.15);
        SlayerBosses.sound(run, cut ? SoundEvents.SHEEP_SHEAR : SoundEvents.SPIDER_HURT, 1.2f, 0.8f);
        SlayerBosses.tell(run, Msg.text(cut ? "You cut the Broodmother out of her cocoon!" : "The Broodmother bursts from her cocoon!",
                cut ? ChatFormatting.GREEN : ChatFormatting.RED));
        SlayerBosses.updateBar(run);
    }

    /** Silk Prison (tier IV): webs around you, then she pounces. */
    private static void silkPrison(SlayerBosses.Run run, ServerPlayer owner, long now) {
        if (owner.distanceTo(run.boss) > 16) {
            return;
        }
        warn(run, owner, "Silk erupts around you!", ChatFormatting.WHITE);
        BlockPos feet = owner.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tempBlock(run.level, feet.offset(dx, 0, dz), Blocks.COBWEB, now + 80);
                tempBlock(run.level, feet.offset(dx, 1, dz), Blocks.COBWEB, now + 80);
            }
        }
        owner.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), run.boss);
        BossMoves.later(run, 30, () -> BossMoves.pounce(run, owner));
    }

    /** Pounce telegraph for the model (called by BossMoves.pounce). */
    static void pounceVisual(SlayerBosses.Run run, int ticks) {
        if (run.boss instanceof BroodmotherEntity brood) {
            brood.setPounceTicks(ticks);
        }
    }

    // ---------------------------------------------------------------- Bone Marshal

    /** Tactical Retreat (tier II): leaps away from you. */
    private static void retreat(SlayerBosses.Run run, ServerPlayer owner, long now) {
        run.retreatReadyAt = now + 160;
        Vec3 away = run.boss.position().subtract(owner.position()).multiply(1, 0, 1);
        Vec3 push = away.lengthSqr() > 0.01 ? away.normalize().scale(1.4) : new Vec3(1.2, 0, 0);
        run.boss.setDeltaMovement(push.x, 0.55, push.z);
        run.boss.syncVelocity = true;
        SlayerBosses.particles(run.level, ParticleTypes.POOF, run.boss.position(), 12, 0.4, 0.05);
        SlayerBosses.sound(run, SoundEvents.SKELETON_STEP, 1.4f, 0.6f);
    }

    /** Piercing Shot (tier III): a long draw (break line of sight!), then a heavy shot. */
    private static void piercingShot(SlayerBosses.Run run, ServerPlayer owner) {
        if (run.boss.distanceTo(owner) > 40) {
            return;
        }
        int draw = 30;
        if (run.boss instanceof BoneMarshalEntity marshal) {
            marshal.setDrawTicks(draw);
        }
        warn(run, owner, "The Bone Marshal draws its greatbow!", ChatFormatting.WHITE);
        SlayerBosses.sound(run, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.4f, 0.5f);
        BossMoves.later(run, draw, () -> {
            Mob boss = run.boss;
            Vec3 from = boss.getEyePosition();
            Vec3 to = owner.getEyePosition();
            for (int i = 0; i <= 24; i++) {
                Vec3 at = from.lerp(to, i / 24.0);
                run.level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            SlayerBosses.sound(run, SoundEvents.TRIDENT_THROW.value(), 1.6f, 0.6f);
            if (owner.isAlive() && boss.hasLineOfSight(owner) && owner.distanceTo(boss) < 44) {
                owner.hurtServer(run.level, boss.damageSources().mobAttack(boss), owner.getMaxHealth() * 0.3f);
                Vec3 knock = to.subtract(from).normalize().scale(1.2);
                owner.push(knock.x, 0.4, knock.z);
                owner.syncVelocity = true;
            }
        });
    }

    // ---------------------------------------------------------------- Void Stalker

    private static void phaseStep(SlayerBosses.Run run) {
        Mob boss = run.boss;
        if (boss instanceof VoidStalkerEntity stalker) {
            stalker.setPhased(true);
            BossMoves.later(run, 10, () -> stalker.setPhased(false));
        }
        SlayerBosses.particles(run.level, ParticleTypes.REVERSE_PORTAL, boss.position().add(0, 1.5, 0), 30, 0.5, 0.1);
        SlayerBosses.sound(run, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        Vec3 side = new Vec3(run.level.getRandom().nextDouble() * 2 - 1, 0, run.level.getRandom().nextDouble() * 2 - 1).normalize().scale(3);
        Vec3 to = SlayerBosses.ground(run.level, boss.position().add(side));
        boss.teleportTo(to.x, to.y, to.z);
    }

    /** Void Grasp (tier II): drags you towards it. */
    private static void voidGrasp(SlayerBosses.Run run, ServerPlayer owner) {
        if (owner.distanceTo(run.boss) > 20 || owner.distanceTo(run.boss) < 3) {
            return;
        }
        warn(run, owner, "The void grasps at you!", ChatFormatting.LIGHT_PURPLE);
        Vec3 pull = run.boss.position().subtract(owner.position()).normalize().scale(1.3);
        owner.push(pull.x, 0.35, pull.z);
        owner.syncVelocity = true;
        SlayerBosses.sound(run, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.4f);
        Vec3 from = run.boss.position().add(0, 1.5, 0);
        Vec3 to = owner.position().add(0, 1, 0);
        for (int i = 0; i <= 16; i++) {
            Vec3 at = from.lerp(to, i / 16.0);
            run.level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
        }
    }

    private static int echoesAlive(SlayerBosses.Run run) {
        int alive = 0;
        for (Mob minion : run.minions) {
            if (minion.isAlive() && minion.entityTags().contains(ECHO_TAG)) {
                alive++;
            }
        }
        return alive;
    }

    /** Mirror Images (tier III): two Void Echoes split off; it takes half damage while they live. */
    private static void mirrorImages(SlayerBosses.Run run, ServerPlayer owner) {
        int before = run.minions.size();
        SlayerBosses.raiseAt(run, owner, EntityTypes.ENDERMAN, 2, run.boss.position());
        for (int i = before; i < run.minions.size(); i++) {
            Mob echo = run.minions.get(i);
            echo.addTag(ECHO_TAG);
            echo.setCustomName(Msg.text("Void Echo", ChatFormatting.DARK_PURPLE));
            MobLevels.set(echo, new MobLevels.MobInfo(Math.max(1, run.tier.level() / 2), false));
        }
        SlayerBosses.sound(run, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4f, 0.8f);
        SlayerBosses.tell(run, Msg.text("The Void Stalker splits into echoes! Kill them to break its guard.", ChatFormatting.LIGHT_PURPLE));
    }

    /** Void Collapse (tier IV, at a quarter health): three seconds to get away, then the void implodes. */
    private static void voidCollapse(SlayerBosses.Run run) {
        Mob boss = run.boss;
        if (boss instanceof VoidStalkerEntity stalker) {
            stalker.setChannelTicks(60);
        }
        for (ServerPlayer player : SlayerBosses.players(run, 24)) {
            warn(run, player, "THE VOID COLLAPSES! Get away!", ChatFormatting.DARK_PURPLE);
        }
        SlayerBosses.sound(run, SoundEvents.WARDEN_SONIC_CHARGE, 2.0f, 0.5f);
        for (int t = 0; t < 60; t += 5) {
            final double radius = 8 - t / 60.0 * 6;
            BossMoves.later(run, t, () -> SlayerBosses.ring(run.level, ParticleTypes.REVERSE_PORTAL, boss.position().add(0, 0.3, 0), radius, 32));
        }
        BossMoves.later(run, 60, () -> {
            SlayerBosses.particles(run.level, ParticleTypes.SONIC_BOOM, boss.position().add(0, 1.5, 0), 1, 0, 0);
            SlayerBosses.particles(run.level, ParticleTypes.PORTAL, boss.position().add(0, 1, 0), 200, 4, 1.5);
            SlayerBosses.sound(run, SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 0.6f);
            int hit = 0;
            for (ServerPlayer player : SlayerBosses.players(run, 8)) {
                player.hurtServer(run.level, boss.damageSources().indirectMagic(boss, boss), player.getMaxHealth() * 0.45f);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), boss);
                hit++;
            }
            siphon(run, hit);
        });
    }

    /** Void Siphon (tier IV): heals 5% for every player its rifts or collapse hurt. */
    static void siphon(SlayerBosses.Run run, int playersHit) {
        if (run.tierNumber >= 4 && playersHit > 0 && run.boss.isAlive()) {
            run.boss.heal(run.boss.getMaxHealth() * 0.05f * playersHit);
            SlayerBosses.particles(run.level, ParticleTypes.WITCH, run.boss.position().add(0, 1.5, 0), 20, 0.5, 0.05);
        }
    }

    // ---------------------------------------------------------------- shared

    private static void warn(SlayerBosses.Run run, ServerPlayer player, String text, ChatFormatting color) {
        player.sendOverlayMessage(Msg.text("⚠ " + text, color, ChatFormatting.BOLD));
    }

    /** What shows on the boss bar for the active defences. */
    static void barExtras(SlayerBosses.Run run, net.minecraft.network.chat.MutableComponent name, long now) {
        if (run.cocoonHits > 0) {
            name.append(Msg.text("  ❂ Cocoon: " + run.cocoonHits, ChatFormatting.WHITE));
        }
        if (run.boneArmourUntil > now) {
            name.append(Msg.text("  ☗ Bone Armour", ChatFormatting.WHITE));
        }
        if (run.line.kind().equals("broodmother") && run.tierNumber >= 2 && run.cocoonHits == 0 && livingMinions(run) >= 3) {
            name.append(Msg.text("  ⬢ Brood Shield", ChatFormatting.GREEN));
        }
        if (run.line.kind().equals("void_stalker") && echoesAlive(run) > 0) {
            name.append(Msg.text("  ◈ Echoes: " + echoesAlive(run), ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** The tier's title on the bar ("Tyrant King"). */
    static String title(String kind, int tier) {
        TierLore lore = lore(kind, tier);
        return lore == null ? "" : lore.title();
    }
}
