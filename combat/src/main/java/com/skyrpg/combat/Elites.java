package com.skyrpg.combat;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.BloodMoon;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Elite mobs: a few naturally spawned hostile mobs come back stronger (more at night and underground): three levels up,
 * a gold "✦ Elite" nameplate (see {@link MobLevels}), four times the health, harder hits, a little faster and bigger,
 * and a faint aura. Killing one pays coins, Combat XP and an Elite Trophy, with a small chance of a Lucky Clover or a
 * Prism Fragment (on top of the usual levelled-mob rewards). No custom name, so Elites still despawn like any mob.
 */
public final class Elites {
    public static final String TAG = "skyrpg_elite";
    private static final double DAY_CHANCE = 0.03;
    private static final double NIGHT_CHANCE = 0.06;
    private static final double UNDERGROUND_CHANCE = 0.08;
    private static final double BONUS_CHANCE = 1 / 50.0;
    private static final double COMBAT_XP = 40;
    private static final int ELITE_LEVELS = 3;
    private static final Identifier HEALTH = SkyRpgCombat.id("elite_health");
    private static final Identifier DAMAGE = SkyRpgCombat.id("elite_damage");
    private static final Identifier SPEED = SkyRpgCombat.id("elite_speed");
    private static final Identifier SIZE = SkyRpgCombat.id("elite_size");

    private Elites() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob && mob.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer player) {
                reward(player, mob);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) {
                server.getPlayerList().getPlayers().forEach(Elites::aura);
            }
        });
    }

    /** From the spawn hook: maybe turn a freshly spawned hostile mob into an Elite. */
    public static void maybeElite(Mob mob, EntitySpawnReason reason) {
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level)
                || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
                        && !(reason == EntitySpawnReason.EVENT && BloodMoon.active())
                || level.dimension().identifier().getNamespace().equals("skyrpg_hub")) {
            return;
        }
        BlockPos pos = mob.blockPosition();
        double chance = pos.getY() < 40 && !level.canSeeSky(pos) ? UNDERGROUND_CHANCE : level.isDarkOutside() ? NIGHT_CHANCE : DAY_CHANCE;
        // A Blood Moon makes Elites three times as likely, everywhere (its hordes spawn as events, so they count too).
        if (mob.getRandom().nextDouble() < chance * (BloodMoon.active() ? 3 : 1)) {
            makeElite(mob);
        }
    }

    /** Makes the mob an Elite (also used by /elite for testing). */
    public static void makeElite(Mob mob) {
        mob.addTag(TAG);
        int level = MobLevels.level(mob);
        if (level == 0 && mob.level() instanceof ServerLevel serverLevel) {
            level = MobLevels.levelAt(serverLevel, mob.blockPosition());
        }
        MobLevels.set(mob, new MobLevels.MobInfo(level + ELITE_LEVELS, true));
        boost(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH, 3.0);
        boost(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, 0.5);
        boost(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, 0.1);
        boost(mob.getAttribute(Attributes.SCALE), SIZE, 0.15);
        mob.setHealth(mob.getMaxHealth());
    }

    private static void boost(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute != null) {
            attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A faint glittering aura on Elites near each player, so they stand out. */
    private static void aura(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(32), m -> m.entityTags().contains(TAG))) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2,
                    mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.02);
        }
    }

    private static void reward(ServerPlayer player, Mob mob) {
        RandomSource random = player.getRandom();
        long coins = 50 + random.nextInt(151);
        Wallet.give(player, coins);
        SkillLevels.giveXp(player, SkillLevels.COMBAT, COMBAT_XP);
        Level level = mob.level();
        mob.spawnAtLocation((ServerLevel) level, new ItemStack(CombatItems.ELITE_TROPHY));
        if (random.nextDouble() < BONUS_CHANCE) {
            Identifier bonus = Identifier.parse(random.nextBoolean() ? "skyrpg_farming:lucky_clover" : "skyrpg_mining:prism_fragment");
            Item item = BuiltInRegistries.ITEM.getValue(bonus);
            if (BuiltInRegistries.ITEM.containsKey(bonus)) {
                mob.spawnAtLocation((ServerLevel) level, new ItemStack(item));
                Notice.rareDrop(player, new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.AQUA), Notice.rgb(ChatFormatting.AQUA));
            }
        }
        Notice.of(Notice.Kind.COMBAT).tag("ELITE SLAIN").color(0xFFC940).send(player, Msg.text("You slew the Elite ", ChatFormatting.GRAY)
                .append(mob.getName().copy().withStyle(ChatFormatting.GOLD))
                .append(Msg.text("  +" + Msg.number(coins) + " coins", ChatFormatting.GOLD)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
    }
}
