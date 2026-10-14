package com.skyrpg.items;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.NpcInteractions;
import com.skyrpg.core.items.ToolWear;
import com.skyrpg.core.text.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Looking after SkyRPG tools: they wear down (see {@link ToolWear}), a broken one reminds you on the action bar while you
 * hold it, and Brannoc the Blacksmith mends a damaged tool for coins: right-click him holding it, then once more to pay
 * (sneak to open his shop instead). The other way to repair is an anvil with the tool's own material.
 */
final class ToolCare {
    private static final Identifier BLACKSMITH = Identifier.fromNamespaceAndPath("skyrpg_npcs", "blacksmith");
    /** How long a quote stays good, in ticks. */
    private static final long QUOTE_TICKS = 200;
    private static final int REMIND_TICKS = 40;

    private record Quote(net.minecraft.world.item.Item item, int damage, long cost, long until) {
    }

    private static final Map<UUID, Quote> QUOTES = new HashMap<>();
    /** Players currently holding a broken tool, so their stats are recomputed when that changes. */
    private static final Set<UUID> HOLDING_BROKEN = new java.util.HashSet<>();

    private ToolCare() {
    }

    static void init() {
        ToolWear.install(SkyItems::isTool);
        ServerTickEvents.END_SERVER_TICK.register(ToolCare::tick);
        NpcInteractions.INTERACT.register(ToolCare::onInteract);
    }

    static void forget(UUID player) {
        QUOTES.remove(player);
        HOLDING_BROKEN.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean broken = ToolWear.isBroken(player.getMainHandItem());
            if (broken != HOLDING_BROKEN.contains(player.getUUID())) {
                if (broken) {
                    HOLDING_BROKEN.add(player.getUUID());
                } else {
                    HOLDING_BROKEN.remove(player.getUUID());
                }
                ToolWear.refreshStats(player);
            }
            if (broken && server.getTickCount() % REMIND_TICKS == 0) {
                player.sendOverlayMessage(ToolWear.brokenMessage(player.getMainHandItem()));
            }
        }
    }

    /** Coins per point of durability to mend a tool of this rarity. */
    static double ratePerPoint(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0.4;
            case UNCOMMON -> 0.6;
            case RARE -> 0.8;
            case EPIC -> 1.0;
            default -> 1.2;
        };
    }

    /** What it costs to mend a damaged tool completely. */
    static long repairCost(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(stack.getDamageValue() * ratePerPoint(def.rarity())));
    }

    private static boolean onInteract(ServerPlayer player, Identifier npc) {
        if (!npc.equals(BLACKSMITH) || player.isShiftKeyDown()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (!ToolWear.wears(held) || held.getDamageValue() <= 0) {
            return false;
        }
        long now = player.level().getServer().getTickCount();
        long cost = repairCost(held);
        Quote quote = QUOTES.get(player.getUUID());
        if (quote == null || now > quote.until() || quote.item() != held.getItem() || quote.damage() != held.getDamageValue()) {
            QUOTES.put(player.getUUID(), new Quote(held.getItem(), held.getDamageValue(), cost, now + QUOTE_TICKS));
            say(player, Component.literal("I can mend your ").append(held.getHoverName()).append(" for ")
                    .append(Msg.coins(cost)).append(". Right-click me again to pay (sneak to shop instead)."));
            return true;
        }
        QUOTES.remove(player.getUUID());
        if (!Wallet.take(player, cost)) {
            say(player, Component.literal("That costs ").append(Msg.coins(cost)).append(", and you are short. Come back with more."));
            return true;
        }
        held.setDamageValue(0);
        ToolWear.refreshStats(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.1f);
        say(player, Component.literal("Good as new. ").append(held.getHoverName()).append(" is fully mended for ").append(Msg.coins(cost)).append("."));
        return true;
    }

    private static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(Component.literal("Brannoc Ashforge: ").withStyle(ChatFormatting.GOLD).append(message.copy().withStyle(ChatFormatting.WHITE)));
    }
}
