package com.skyrpg.items.bow;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A SkyRPG bow: vanilla's draw and vanilla arrows as ammo, but the arrows are tagged to deal SkyRPG damage (see
 * {@link Bows}), the draw can be faster ({@code "draw_speed"}), and a bow can shoot several arrows at once
 * ({@code "arrows"}). Its melee hit is plain: the "damage" stat only counts for arrows.
 */
public class SkyBowItem extends BowItem {
    public SkyBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) {
            return false;
        }
        JsonObject bow = Bows.config(stack);
        int drawn = (int) ((getUseDuration(stack, entity) - remaining) * Bows.num(bow, "draw_speed", 1.0));
        float power = getPowerForTime(drawn);
        if (power < 0.1) {
            return false;
        }
        List<ItemStack> projectiles = draw(stack, ammo, player);
        // More than one arrow per shot: the extras cost nothing and can't be picked up.
        for (int i = 1; i < (int) Bows.num(bow, "arrows", 1) && !projectiles.isEmpty(); i++) {
            ItemStack extra = projectiles.get(0).copyWithCount(1);
            extra.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            projectiles = new ArrayList<>(projectiles);
            projectiles.add(extra);
        }
        if (level instanceof ServerLevel server && !projectiles.isEmpty()) {
            shoot(server, player, player.getUsedItemHand(), stack, projectiles, power * 3.0f, 1.0f, power == 1.0f, null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f,
                1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {
        ItemStack bowStack = projectile instanceof AbstractArrow arrow ? arrow.getWeaponItem() : ItemStack.EMPTY;
        JsonObject bow = Bows.config(bowStack);
        // A volley fans out: the first arrow straight ahead, the others to each side.
        float spread = (float) Bows.num(bow, "spread", 0);
        float yaw = index == 0 ? 0 : (index % 2 == 1 ? spread * ((index + 1) / 2) : -spread * (index / 2));
        float pitch = index == 0 ? 0 : -spread * 0.3f;
        projectile.shootFromRotation(shooter, shooter.getXRot() + pitch, shooter.getYRot() + yaw + angle, 0.0f, velocity, inaccuracy);
        if (projectile instanceof AbstractArrow arrow && shooter instanceof ServerPlayer player) {
            Bows.prepare(arrow, bowStack, player, velocity / 3.0f, index);
        }
    }
}
