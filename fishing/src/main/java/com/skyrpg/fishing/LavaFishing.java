package com.skyrpg.fishing;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Lava fishing: a lava rod (item tag {@code skyrpg_fishing:lava_rods}, the Magmaline Rod) fishes in the Nether's lava.
 * <ul>
 *   <li>Its bobber floats on lava as on water, can't burn, and bites with the usual timing and Fishing Speed
 *       ({@code FishingHookLavaMixin}).</li>
 *   <li>A catch rolls {@code skyrpg_fishing:gameplay/lava_fishing} and goes straight into the inventory (an item flung
 *       out of lava would burn).</li>
 *   <li>The rare find is a Molten Pearl, and lava sea creatures ({@code "fluid": "lava"}) come up instead of water ones
 *       (see {@link Catching} and {@link SeaCreatures}).</li>
 * </ul>
 * Only the owner's held rod and the dimension decide it, so the client (which also moves the bobber) agrees with the
 * server.
 */
public final class LavaFishing {
    public static final TagKey<Item> LAVA_RODS = TagKey.create(Registries.ITEM, SkyRpgFishing.id("lava_rods"));
    public static final ResourceKey<LootTable> CATCHES = ResourceKey.create(Registries.LOOT_TABLE, SkyRpgFishing.id("gameplay/lava_fishing"));

    private LavaFishing() {
    }

    /** A hook that treats lava as water: its owner holds a lava rod, in the Nether. */
    public static boolean isLavaHook(FishingHook hook) {
        Player owner = hook.getPlayerOwner();
        return owner != null && hook.level().dimension() == Level.NETHER
                && (owner.getMainHandItem().is(LAVA_RODS) || owner.getOffhandItem().is(LAVA_RODS));
    }

    /** A lava hook that is in lava right now: what it catches is lava fishing's. */
    public static boolean inLava(FishingHook hook) {
        return isLavaHook(hook) && hook.level().getFluidState(hook.blockPosition()).is(FluidTags.LAVA);
    }

    /** Vanilla's water particles for the approaching fish and the bite, as lava pops and flames. */
    public static ParticleOptions particle(ParticleOptions water) {
        if (water == ParticleTypes.FISHING) {
            return ParticleTypes.SMALL_FLAME;
        }
        if (water == ParticleTypes.BUBBLE || water == ParticleTypes.SPLASH) {
            return ParticleTypes.LAVA;
        }
        return water;
    }

    /** A lava catch: into the inventory, or at the player's feet if it's full. */
    public static void hand(ServerPlayer player, ItemStack stack) {
        Catching.give(player, stack);
    }
}
