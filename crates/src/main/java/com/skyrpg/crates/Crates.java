package com.skyrpg.crates;

import com.google.gson.JsonElement;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Opening crates. The server picks the prize the moment you press Open (taking the crate and a key), sends the client
 * a reel to spin that stops on it, and hands the prize over when the reel stops ({@link #ROLL_TICKS} later, or at once
 * if you log off). Rare tiers are announced to everyone.
 */
public final class Crates {
    static final String DIRECTORY = "skyrpg/crates";
    /** How long the reel spins (the client's CrateScreen.ROLL_MS), plus a moment. */
    static final int ROLL_TICKS = 144;
    private static final int REEL = 60;

    private static final Map<Identifier, Crate> CRATES = new LinkedHashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Tests and screenshots (op command): the tier this player's next crate lands on. */
    private static final Map<UUID, Integer> RIGGED = new HashMap<>();

    private record Pending(Crate crate, int tier, Crate.Reward reward, long due) {
    }

    private Crates() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        CRATES.clear();
        files.forEach((id, json) -> {
            try {
                CRATES.put(id, Crate.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgCrates.LOGGER.error("Skipping invalid crate {}: {}", id, e.getMessage());
            }
        });
    }

    public static Iterable<Crate> all() {
        return CRATES.values();
    }

    static Crate byId(String id) {
        return CRATES.get(Identifier.tryParse(id));
    }

    /** The crate this item is (or is the key of). */
    static Crate byItem(Item item) {
        for (Crate crate : CRATES.values()) {
            if (crate.item() == item || crate.key() == item) {
                return crate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the screen

    static void showMenu(ServerPlayer player, Crate crate) {
        double total = crate.totalWeight();
        List<CratePayloads.Tier> tiers = new ArrayList<>();
        for (Crate.Tier tier : crate.tiers()) {
            List<CratePayloads.Card> cards = new ArrayList<>();
            for (Crate.Reward reward : tier.rewards()) {
                cards.add(new CratePayloads.Card(reward.display(), reward.displayName(), tier.color(), false));
            }
            tiers.add(new CratePayloads.Tier(tier.name(), tier.color(), tier.weight() / total, cards));
        }
        ServerPlayNetworking.send(player, new CratePayloads.Menu(crate.id().toString(), crate.name(), crate.subtitle(),
                new ItemStack(crate.item()), new ItemStack(crate.key()), crate.key().getDefaultInstance().getHoverName().getString(),
                crate.keyHint(), count(player, crate.item()), count(player, crate.key()), tiers));
    }

    // ------------------------------------------------------------------ opening

    static void open(ServerPlayer player, String crateId) {
        Crate crate = byId(crateId);
        if (crate == null || PENDING.containsKey(player.getUUID())) {
            return;
        }
        if (count(player, crate.item()) == 0 || count(player, crate.key()) == 0) {
            Notice.send(player, Notice.Kind.CRATE, Component.literal(count(player, crate.item()) == 0 ? "You don't have a " + crate.name() + "."
                    : crate.keyHint() + ".").withColor(0xFF8A8A));
            showMenu(player, crate);
            return;
        }
        take(player, crate.item());
        take(player, crate.key());
        RandomSource random = player.getRandom();
        Integer rigged = RIGGED.remove(player.getUUID());
        int tierIndex = rigged != null ? Math.min(rigged, crate.tiers().size() - 1) : crate.pickTier(random, 1);
        Crate.Tier tier = crate.tiers().get(tierIndex);
        Crate.Reward prize = tier.pick(random);

        // The reel: random cards (with the rare tiers a little more common than they really are, for show), and the
        // prize near the end, where it slows down.
        int winner = REEL - 8 + random.nextInt(4);
        List<CratePayloads.Card> reel = new ArrayList<>(REEL);
        for (int i = 0; i < REEL; i++) {
            if (i == winner) {
                reel.add(new CratePayloads.Card(prize.display(), prize.displayName(), tier.color(), tier.mystery()));
                continue;
            }
            Crate.Tier shown = crate.tiers().get(crate.pickTier(random, 0.65));
            Crate.Reward filler = shown.pick(random);
            reel.add(new CratePayloads.Card(filler.display(), filler.displayName(), shown.color(), shown.mystery()));
        }
        CratePayloads.Card prizeCard = new CratePayloads.Card(prize.display(), prize.displayName(), tier.color(), false);
        ServerPlayNetworking.send(player, new CratePayloads.Roll(crate.id().toString(), reel, winner, prizeCard, tier.name(),
                count(player, crate.item()), count(player, crate.key())));
        PENDING.put(player.getUUID(), new Pending(crate, tierIndex, prize, player.level().getServer().getTickCount() + ROLL_TICKS));
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    static void rig(ServerPlayer player, int tier) {
        RIGGED.put(player.getUUID(), tier);
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        PENDING.entrySet().removeIf(entry -> {
            if (entry.getValue().due() > now) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                deliver(player, entry.getValue());
            }
            return true;
        });
    }

    /** Someone logging off mid-roll still gets the prize. */
    static void leaving(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            deliver(player, pending);
        }
    }

    private static void deliver(ServerPlayer player, Pending pending) {
        Crate.Tier tier = pending.crate().tiers().get(pending.tier());
        ItemStack shown = pending.reward().display();
        pending.reward().give(player);
        int color = tier.color() & 0xFFFFFF;
        Component prize = Component.literal(pending.reward().displayName()).withColor(color)
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(shown))));
        Notice.send(player, Notice.Kind.CRATE, Component.literal("You unboxed ").append(prize)
                .append(Component.literal("  (" + tier.name() + ")").withColor(0x8D96A8)));
        if (pending.tier() >= pending.crate().announceFrom()) {
            MutableComponent message = Notice.of(Notice.Kind.CRATE).build(Component.literal(player.getGameProfile().name()).withColor(0xFFFFFF)
                    .append(Component.literal(" unboxed ").withColor(0xB4BCCB)).append(prize)
                    .append(Component.literal(" from a " + pending.crate().name() + "!").withColor(0xB4BCCB)));
            boolean top = pending.tier() == pending.crate().tiers().size() - 1;
            // Everyone else hears about it (the opener already has their own line).
            for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(message);
                    other.level().playSound(null, other.blockPosition(), top ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                            SoundSource.PLAYERS, 0.5f, top ? 1f : 1.4f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ inventory

    static int count(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return;
            }
        }
    }
}
