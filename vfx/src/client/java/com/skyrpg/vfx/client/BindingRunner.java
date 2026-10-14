package com.skyrpg.vfx.client;

import com.skyrpg.vfx.data.BindingDef;
import com.skyrpg.vfx.data.Condition;
import com.skyrpg.vfx.data.VfxLibrary;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;

/**
 * Plays bindings' ambient effects on entities near the camera, every tick. Bindings are indexed by entity type when the
 * library changes, so each entity only checks the bindings that can apply to it; the (cheap) rate roll comes before the
 * armour and item checks; and there's a per-tick cap on plays.
 */
final class BindingRunner {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final RandomGenerator RANDOM = new SplittableRandom();
    private static final BindingDef[] NONE = {};

    private static VfxLibrary indexed;
    private static Map<EntityType<?>, BindingDef[]> byType = Map.of();
    private static BindingDef[] byTag = NONE;
    private static BindingDef[] living = NONE;
    private static float maxRange;

    private BindingRunner() {
    }

    static void tick(Minecraft minecraft, VfxQuality quality, float vanilla) {
        VfxLibrary library = VfxRuntime.library();
        if (library != indexed) {
            index(library);
        }
        if (maxRange <= 0 || minecraft.level == null) {
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return;
        }
        Vec3 eye = minecraft.gameRenderer.mainCamera().position();
        double reach = maxRange * quality.rangeScale();
        double reachSq = reach * reach;
        float rateScale = quality.bindingScale() * vanilla;
        int plays = quality.bindingPlays();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (plays <= 0) {
                return;
            }
            if (entity.isRemoved() || entity.isInvisible() || entity.isSpectator()) {
                continue;
            }
            double distSq = entity.distanceToSqr(eye);
            if (distSq > reachSq) {
                continue;
            }
            EntityType<?> type = entity.getType();
            BindingDef[] typed = byType.get(type);
            if (typed != null) {
                for (BindingDef binding : typed) {
                    plays -= tryPlay(minecraft, binding, entity, camera, distSq, quality, rateScale);
                }
            }
            for (BindingDef binding : byTag) {
                if (!binding.entityTypes.contains(type) && hasTag(binding, entity)) {
                    plays -= tryPlay(minecraft, binding, entity, camera, distSq, quality, rateScale);
                }
            }
            if (living.length > 0 && entity instanceof LivingEntity) {
                for (BindingDef binding : living) {
                    plays -= tryPlay(minecraft, binding, entity, camera, distSq, quality, rateScale);
                }
            }
        }
    }

    private static int tryPlay(Minecraft minecraft, BindingDef binding, Entity entity, Entity camera, double distSq, VfxQuality quality,
                               float rateScale) {
        double range = binding.range * quality.rangeScale();
        if (distSq > range * range || (!binding.self && entity == camera)) {
            return 0;
        }
        float rate = binding.rate * rateScale;
        int times = (int) rate;
        if (RANDOM.nextFloat() < rate - times) {
            times++;
        }
        if (times == 0) {
            return 0;
        }
        int hand = 0;
        if (binding.armorAssets != null || binding.hand != null) {
            if (!(entity instanceof LivingEntity livingEntity)) {
                return 0;
            }
            if (binding.armorAssets != null && !wearing(binding, livingEntity)) {
                return 0;
            }
            if (binding.hand != null) {
                hand = holding(binding, livingEntity);
                if (hand < 0) {
                    return 0;
                }
            }
        }
        if ((binding.required | binding.forbidden) != 0
                && !Condition.test(VfxRuntime.conditions(entity, minecraft.level), binding.required, binding.forbidden)) {
            return 0;
        }
        for (int i = 0; i < times; i++) {
            VfxRuntime.play(binding.effect, entity, entity.getX(), entity.getY(), entity.getZ(), hand, 0, true);
        }
        return times;
    }

    private static boolean hasTag(BindingDef binding, Entity entity) {
        for (TagKey<EntityType<?>> tag : binding.entityTags) {
            if (entity.typeHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wearing(BindingDef binding, LivingEntity entity) {
        int pieces = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null) {
                continue;
            }
            Optional<ResourceKey<EquipmentAsset>> asset = equippable.assetId();
            if (asset.isPresent() && binding.armorAssets.contains(asset.get().identifier())) {
                pieces++;
            }
        }
        return pieces >= binding.armorPieces;
    }

    /** 0 if the main hand matches, 1 for the off hand, -1 for neither. */
    private static int holding(BindingDef binding, LivingEntity entity) {
        if (binding.hand != BindingDef.Hand.OFF && matches(binding, entity.getMainHandItem())) {
            return 0;
        }
        if (binding.hand != BindingDef.Hand.MAIN && matches(binding, entity.getOffhandItem())) {
            return 1;
        }
        return -1;
    }

    private static boolean matches(BindingDef binding, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (binding.hasItemFilter() && !inList(stack, binding.items, binding.itemTags)) {
            return false;
        }
        if (binding.exceptItems != null && inList(stack, binding.exceptItems, binding.exceptTags)) {
            return false;
        }
        if (binding.rarities != null) {
            String rarity = Rarities.of(stack);
            return rarity != null && binding.rarities.contains(rarity);
        }
        return true;
    }

    private static boolean inList(ItemStack stack, List<Item> items, List<TagKey<Item>> tags) {
        if (items != null && items.contains(stack.getItem())) {
            return true;
        }
        if (tags != null) {
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void index(VfxLibrary library) {
        indexed = library;
        Map<EntityType<?>, List<BindingDef>> types = new IdentityHashMap<>();
        List<BindingDef> tagged = new ArrayList<>();
        List<BindingDef> any = new ArrayList<>();
        float range = 0;
        for (BindingDef binding : library.bindings().values()) {
            range = Math.max(range, binding.range);
            if (binding.hasEntityFilter()) {
                for (EntityType<?> type : binding.entityTypes) {
                    types.computeIfAbsent(type, t -> new ArrayList<>()).add(binding);
                }
                if (!binding.entityTags.isEmpty()) {
                    tagged.add(binding);
                }
            } else {
                any.add(binding);
            }
        }
        Map<EntityType<?>, BindingDef[]> flat = new IdentityHashMap<>();
        types.forEach((type, list) -> flat.put(type, list.toArray(NONE)));
        byType = flat;
        byTag = tagged.toArray(NONE);
        living = any.toArray(NONE);
        maxRange = range;
    }
}
