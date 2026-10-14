package com.skyrpg.world;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Dungeon chests are per player: everyone who opens one gets their own roll of its loot table, plus coins and Delving
 * XP by the chest's tier and the dungeon's level, so friends never race each other to the loot. What a player leaves
 * in their copy stays there until the dungeon resets. The chest block itself never holds anything.
 */
final class DungeonLoot {
    private static final int SLOTS = 27;
    /** Coins and Delving XP per dungeon level, by chest tier. */
    private static final Map<String, double[]> TIERS = Map.of(
            "common", new double[] {15, 3.5}, "rare", new double[] {30, 7}, "secret", new double[] {45, 10},
            "vault", new double[] {100, 25}, "reward", new double[] {0, 0}, "wayshrine", new double[] {6, 2});
    private static final Map<String, String> TITLES = Map.of("common", "Chest", "rare", "Rare Chest", "secret", "Secret Cache",
            "vault", "Vault", "reward", "Reward Chest", "wayshrine", "Shrine Chest");

    private DungeonLoot() {
    }

    /** The chest no longer keeps vanilla loot of its own (it could leak through hoppers or breaking it). */
    static void emptyBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null) {
            container.setLootTable(null);
            container.clearContent();
            container.setChanged();
        }
    }

    /** A player right-clicked a dungeon chest: open their own copy (rolling it the first time). */
    static InteractionResult open(ServerLevel level, ServerPlayer player, Marker marker, DungeonMarkers.Info info) {
        CompoundTag state = DungeonMarkers.state(marker);
        String key = player.getUUID().toString();
        if (info.def().has("for") && info.def().get("for").isJsonArray()) {
            boolean allowed = false;
            for (var id : info.def().getAsJsonArray("for")) {
                allowed |= id.getAsString().equals(key);
            }
            if (!allowed) {
                player.sendOverlayMessage(Component.literal("This reward belongs to the party that won the fight.").withStyle(ChatFormatting.GOLD));
                return InteractionResult.FAIL;
            }
        }
        String tier = Dungeons.str(info.def(), "tier", "common");
        CompoundTag players = state.getCompoundOrEmpty("players");
        List<ItemStack> items;
        if (players.contains(key)) {
            items = decode(level, players.getListOrEmpty(key));
        } else {
            items = roll(level, player, info, tier);
            players.put(key, encode(level, items));
            state.put("players", players);
            if (!state.contains("at")) {
                state.putLong("at", level.getGameTime());
            }
            DungeonMarkers.setState(marker, state);
            pay(player, info, tier);
        }
        BlockPos pos = marker.blockPosition();
        LootBox box = new LootBox(level, marker.getUUID(), key, pos);
        for (int slot = 0; slot < SLOTS && slot < items.size(); slot++) {
            box.setItem(slot, items.get(slot));
        }
        String dungeon = info.dungeon() == null ? "" : " · " + info.dungeon().name();
        Component title = Component.literal(TITLES.getOrDefault(tier, "Chest") + dungeon);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, box), title));
        lid(level, pos, true);
        level.playSound(null, pos, tier.equals("vault") || tier.equals("reward") ? SoundEvents.VAULT_OPEN_SHUTTER : SoundEvents.CHEST_OPEN,
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Rolls the chest's table (a Reward Chest rolls once more per bonus) into random slots. */
    private static List<ItemStack> roll(ServerLevel level, ServerPlayer player, DungeonMarkers.Info info, String tier) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            slots.add(ItemStack.EMPTY);
        }
        Identifier loot = Identifier.tryParse(Dungeons.str(info.def(), "loot", ""));
        if (loot == null) {
            return slots;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, loot));
        int rolls = 1 + (int) Dungeons.num(info.def(), "bonus", 0);
        RandomSource random = level.getRandom();
        for (int roll = 0; roll < rolls; roll++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(info.pos()))
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                List<Integer> empty = new ArrayList<>();
                for (int i = 0; i < SLOTS; i++) {
                    if (slots.get(i).isEmpty()) {
                        empty.add(i);
                    }
                }
                if (empty.isEmpty()) {
                    break;
                }
                slots.set(empty.get(random.nextInt(empty.size())), stack);
            }
        }
        return slots;
    }

    private static void pay(ServerPlayer player, DungeonMarkers.Info info, String tier) {
        double[] rates = TIERS.getOrDefault(tier, TIERS.get("common"));
        int level = info.dungeon() == null ? 8 : info.dungeon().level();
        long coins = Math.round(level * rates[0] * (0.8 + 0.4 * player.getRandom().nextDouble()));
        double xp = level * rates[1];
        if (coins > 0) {
            Wallet.give(player, coins);
        }
        DungeonRuns.delving(player, xp);
        if (coins > 0 || xp > 0) {
            Notice.of(Notice.Kind.DUNGEON).icon(Notice.GEM).tag(TITLES.getOrDefault(tier, "Chest").toUpperCase()).send(player,
                    Component.literal("+").withStyle(ChatFormatting.GRAY).append(Msg.coins(coins))
                    .append(Component.literal(" · +" + Msg.number((long) xp) + " Delving XP").withStyle(ChatFormatting.DARK_AQUA)));
        }
        if (tier.equals("vault") || tier.equals("reward")) {
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        }
    }

    private static void lid(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
        }
    }

    private static ListTag encode(ServerLevel level, List<ItemStack> items) {
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            final int index = slot;
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(tag -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", index);
                entry.put("item", tag);
                list.add(entry);
            });
        }
        return list;
    }

    private static List<ItemStack> decode(ServerLevel level, ListTag list) {
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            items.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            int slot = entry.getIntOr("slot", -1);
            Tag item = entry.get("item");
            if (slot >= 0 && slot < SLOTS && item != null) {
                items.set(slot, ItemStack.CODEC.parse(ops, item).result().orElse(ItemStack.EMPTY));
            }
        }
        return items;
    }

    /** One player's copy of a chest; what's left in it is saved back to the marker when they close it. */
    private static final class LootBox extends SimpleContainer {
        private final ServerLevel level;
        private final UUID marker;
        private final String player;
        private final BlockPos pos;

        LootBox(ServerLevel level, UUID marker, String player, BlockPos pos) {
            super(SLOTS);
            this.level = level;
            this.marker = marker;
            this.player = player;
            this.pos = pos;
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            lid(level, pos, false);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 1.0f);
            Marker entity = DungeonMarkers.loaded(marker);
            if (entity == null) {
                return;
            }
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < SLOTS; slot++) {
                items.add(getItem(slot));
            }
            CompoundTag state = DungeonMarkers.state(entity);
            CompoundTag players = state.getCompoundOrEmpty("players");
            players.put(player, encode(level, items));
            state.put("players", players);
            DungeonMarkers.setState(entity, state);
        }
    }
}
