package com.skyrpg.combat;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.skyrpg.combat.mixin.DummyAccess;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Training Dummy, the combat version of a node: place it, hit it, and it shows every hit's damage and your DPS
 * over the last 5 seconds. It never dies. Each hit gives a little Combat XP, capped per hour so it can't be AFK-farmed.
 * Sneak and hit it with an empty hand to pick it back up.
 */
public final class TrainingDummy {
    public static final String TAG = "skyrpg_training_dummy";
    private static final double XP_PER_HIT = 0.5;
    private static final double XP_PER_HOUR = 150;
    private static final int WINDOW = 100;
    private static final Map<UUID, Deque<long[]>> HITS = new HashMap<>();
    private static final Map<UUID, double[]> XP = new HashMap<>();

    private TrainingDummy() {
    }

    /** The item that places a dummy. */
    public static final class DummyItem extends Item {
        public DummyItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos at = context.getClickedPos().relative(context.getClickedFace());
            if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.above()).canBeReplaced()) {
                return InteractionResult.FAIL;
            }
            Mannequin dummy = EntityTypes.MANNEQUIN.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
            if (dummy == null) {
                return InteractionResult.FAIL;
            }
            float yaw = context.getPlayer() == null ? 0 : context.getPlayer().getYRot() + 180;
            dummy.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
            dummy.setYHeadRot(yaw);
            dummy.setYBodyRot(yaw);
            dummy.addTag(TAG);
            dummy.setCustomName(Component.literal("Training Dummy").withStyle(ChatFormatting.GOLD));
            dummy.setCustomNameVisible(true);
            DummyAccess access = (DummyAccess) dummy;
            access.skyrpg$setImmovable(true);
            access.skyrpg$setHideDescription(false);
            access.skyrpg$setDescription(Component.literal("Hit me!").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Sneak-punch to pick up").withStyle(ChatFormatting.DARK_GRAY)));
            JsonObject skin = new JsonObject();
            skin.addProperty("texture", SkyRpgCombat.id("entity/training_dummy").toString());
            skin.addProperty("model", "wide");
            ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, skin).result().ifPresent(profile -> dummy.setComponent(DataComponents.PROFILE, profile));
            serverLevel.addFreshEntity(dummy);
            serverLevel.playSound(null, at, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!isDummy(entity)) {
                return true;
            }
            if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
                    && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                entity.discard();
                player.getInventory().add(new ItemStack(CombatItems.TRAINING_DUMMY));
                player.level().playSound(null, entity.blockPosition(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                return false;
            }
            return source.getEntity() instanceof ServerPlayer; // only players can hurt it (no lava, no mobs)
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!isDummy(entity)) {
                return;
            }
            entity.setHealth(entity.getMaxHealth());
            if (source.getEntity() instanceof ServerPlayer player && damageTaken > 0) {
                hit(player, damageTaken * StatTypes.HEALTH_SCALE);
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (isDummy(entity)) {
                entity.setHealth(entity.getMaxHealth());
                return false;
            }
            return true;
        });
    }

    static boolean isDummy(LivingEntity entity) {
        return entity instanceof Mannequin && entity.entityTags().contains(TAG);
    }

    private static void hit(ServerPlayer player, double damage) {
        long now = player.level().getServer().getTickCount();
        Deque<long[]> hits = HITS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        hits.addLast(new long[] {now, Math.round(damage * 10)});
        while (!hits.isEmpty() && hits.peekFirst()[0] < now - WINDOW) {
            hits.removeFirst();
        }
        double total = hits.stream().mapToLong(h -> h[1]).sum() / 10.0;
        double seconds = Math.max(1, (now - hits.peekFirst()[0]) / 20.0 + 0.5);
        player.sendOverlayMessage(Msg.text("DPS ", ChatFormatting.GRAY)
                .append(Msg.text(Msg.number(Math.round(total / seconds)), ChatFormatting.RED))
                .append(Msg.text("   last hit ", ChatFormatting.GRAY))
                .append(Msg.text(Msg.number(Math.round(damage)), ChatFormatting.WHITE)));
        // A trickle of Combat XP, capped per hour of server time.
        double[] xp = XP.computeIfAbsent(player.getUUID(), id -> new double[] {now, 0});
        if (now - xp[0] >= 72000) {
            xp[0] = now;
            xp[1] = 0;
        }
        if (xp[1] < XP_PER_HOUR) {
            xp[1] += XP_PER_HIT;
            SkillLevels.giveXp(player, SkillLevels.COMBAT, XP_PER_HIT);
        }
    }
}
