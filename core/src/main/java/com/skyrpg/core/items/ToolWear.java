package com.skyrpg.core.items;

import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Wear and tear of SkyRPG's gathering tools (pickaxes, axes, hoes, scythes, shovels and fishing rods), for any mod.
 * <ul>
 *   <li>A SkyRPG tool never breaks for good: it stops at 1 durability and is <em>broken</em> until it is repaired. A broken
 *   tool mines at hand speed, drops nothing that needs a tool, has no tier, stats, perks, enchants or abilities, and can't
 *   till, strip or cast. Everything else that wears (vanilla tools, armour...) is untouched.</li>
 *   <li>An enchant (or anything else) can <em>protect</em> a tool, so it takes no wear at all ({@link #addProtector}).</li>
 *   <li>Abilities that break many blocks wrap the extra blocks in {@link #bulk}: only one in {@value #BULK_STEP} of them
 *   wears the tool.</li>
 * </ul>
 * The {@code skyrpg_items} mod says which stacks are tools ({@link #install}); a mixin in core routes every durability
 * change through {@link #adjust}.
 */
public final class ToolWear {
    /** In a {@link #bulk} run, one extra block in this many costs a point of durability. */
    public static final int BULK_STEP = 4;

    private static Predicate<ItemStack> tools = stack -> false;
    private static final List<Predicate<ItemStack>> PROTECTORS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<int[]> BULK = new ThreadLocal<>();

    private ToolWear() {
    }

    /** Installs which stacks are SkyRPG tools (the items mod does). */
    public static void install(Predicate<ItemStack> installed) {
        tools = installed;
    }

    /** Adds a reason for a tool to take no wear (the Unbreakable enchant). */
    public static void addProtector(Predicate<ItemStack> protector) {
        PROTECTORS.add(protector);
    }

    /** Whether this is a SkyRPG tool (an old one that is still unbreakable counts too: see {@link #wears}). */
    public static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && tools.test(stack);
    }

    /** A SkyRPG tool that actually has durability right now (not an old, still unbreakable one). */
    public static boolean wears(ItemStack stack) {
        return isTool(stack) && stack.isDamageableItem();
    }

    /** True while an enchant or the like keeps this tool from taking wear. */
    public static boolean isProtected(ItemStack stack) {
        for (Predicate<ItemStack> protector : PROTECTORS) {
            if (protector.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** A SkyRPG tool at 1 durability: it does nothing until repaired. */
    public static boolean isBroken(ItemStack stack) {
        return wears(stack) && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Runs {@code action} (an ability's extra block breaks) so that only one block in {@value #BULK_STEP} wears the tool. */
    public static void bulk(Runnable action) {
        int[] previous = BULK.get();
        BULK.set(new int[1]);
        try {
            action.run();
        } finally {
            if (previous == null) {
                BULK.remove();
            } else {
                BULK.set(previous);
            }
        }
    }

    /**
     * What a durability change of {@code amount} becomes for this stack: nothing for a protected tool, a reduced amount in
     * a {@link #bulk} run, and never enough to break the tool (it stops at 1 durability, with a message). Repairs
     * (amount of 0 or less) and everything that isn't a SkyRPG tool pass through.
     */
    public static int adjust(ItemStack stack, int amount, ServerPlayer player) {
        if (amount <= 0 || !wears(stack)) {
            return amount;
        }
        if (isProtected(stack)) {
            return 0;
        }
        int[] counter = BULK.get();
        if (counter != null && ++counter[0] % BULK_STEP != 0) {
            return 0;
        }
        int room = stack.getMaxDamage() - 1 - stack.getDamageValue();
        int allowed = Math.max(0, Math.min(amount, room));
        if (room > 0 && allowed >= room && player != null) {
            player.sendOverlayMessage(brokenMessage(stack));
            Msg.playSound(player, SoundEvents.ITEM_BREAK.value(), 0.8f, 0.9f);
        }
        return allowed;
    }

    /** "Your Prospector Pickaxe is broken - repair it" in red. */
    public static Component brokenMessage(ItemStack stack) {
        return Msg.text("Your ", ChatFormatting.RED).append(stack.getHoverName().copy().withStyle(ChatFormatting.RED)).append(Msg.text(" is broken — repair it", ChatFormatting.RED));
    }

    /** Wears the held tool (a harvested crop, which vanilla doesn't charge for). Does nothing in creative or for a broken tool. */
    public static void wear(ServerPlayer player, int amount) {
        ItemStack held = player.getMainHandItem();
        if (wears(held) && !isBroken(held)) {
            held.hurtAndBreak(amount, player, EquipmentSlot.MAINHAND);
        }
    }

    /** Recomputes the player's stats now (a tool just broke or was repaired). */
    public static void refreshStats(ServerPlayer player) {
        Stats.refresh(player);
    }
}
