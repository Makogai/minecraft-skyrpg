package com.skyrpg.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.combat.Warmups;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Waystones: attune to one by using it, then use any waystone to travel to the others you've attuned (or to the hub)
 * for a few XP levels. The names live here (one list for the whole server, saved with the overworld); each player's
 * attunements live in their {@link Explorer} data. Villages get a waystone next to their bell the first time it loads.
 */
public final class Waystones {
    private static final double USE_RANGE = 6;
    private static final int MAX_COST = 5;
    private static final int HUB_COST_FROM = 2;
    private static final String[] WORDS = {"Elder", "Moss", "Ash", "Raven", "Thorn", "Amber", "Frost", "Dusk", "Glimmer", "Stone", "Willow",
            "Ember", "Hollow", "Silver", "Briar", "Wolf", "Star", "Mist", "Iron", "Bramble"};
    private static final String[] PLACES = {"watch", "ford", "rest", "gate", "stead", "mere", "crest", "vale", "hollow", "reach", "shrine", "cairn"};

    /**
     * One waystone the server knows about. {@code id} is its identity (players are attuned to ids, not places, so a waystone
     * can be picked up and put down somewhere else); {@code placed} is false while it is an item in someone's hands, and
     * then {@code pos} is just where it last stood. {@code owner} is the player who placed it ("" for generated ones).
     * {@code fixed}: 1 for the hub's and dungeon entrances' waystones, which can't be picked up, 0 for the rest, -1 not yet worked out.
     */
    record Entry(String id, String dimension, long pos, String name, String owner, boolean placed, int fixed) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("id", "").forGetter(Entry::id),
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension), Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("name").forGetter(Entry::name), Codec.STRING.optionalFieldOf("owner", "").forGetter(Entry::owner),
                Codec.BOOL.optionalFieldOf("placed", true).forGetter(Entry::placed),
                Codec.INT.optionalFieldOf("fixed", -1).forGetter(Entry::fixed)).apply(i, Entry::new));

        String key() {
            return dimension + "|" + pos;
        }

        Entry withName(String newName) {
            return new Entry(id, dimension, pos, newName, owner, placed, fixed);
        }

        Entry withFixed(int value) {
            return new Entry(id, dimension, pos, name, owner, placed, value);
        }

        Entry carried() {
            return new Entry(id, dimension, pos, name, owner, false, fixed);
        }
    }

    /** The identity a waystone gets when it is first seen at a position (and the one data from before ids implies). */
    static String legacyId(String dimension, long pos) {
        return UUID.nameUUIDFromBytes(("skyrpg:waystone:" + dimension + "|" + pos).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Every waystone, and which villages already got theirs. */
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().optionalFieldOf("waystones", List.of()).forGetter(d -> List.copyOf(d.byId.values())),
                Codec.LONG.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> List.copyOf(d.villages))).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgWorld.id("waystones"), Data::new, CODEC, null);

        final Map<String, Entry> byId = new LinkedHashMap<>();
        /** Placed waystones by "dimension|pos", to the id that stands there. */
        private final Map<String, String> idAt = new HashMap<>();
        final Set<Long> villages = new HashSet<>();

        Data() {
        }

        Data(List<Entry> entries, List<Long> villages) {
            for (Entry entry : entries) {
                Entry kept = entry.id().isEmpty() ? new Entry(legacyId(entry.dimension(), entry.pos()), entry.dimension(), entry.pos(), entry.name(),
                        entry.owner(), entry.placed(), entry.fixed()) : entry;
                byId.put(kept.id(), kept);
                if (kept.placed()) {
                    idAt.put(kept.key(), kept.id());
                }
            }
            this.villages.addAll(villages);
        }

        /** The waystone standing at this position, if we know of one. */
        Entry get(String dimension, BlockPos pos) {
            String id = idAt.get(dimension + "|" + pos.asLong());
            return id == null ? null : byId.get(id);
        }

        void put(Entry entry) {
            Entry old = byId.put(entry.id(), entry);
            if (old != null && old.placed() && old.id().equals(idAt.get(old.key()))) {
                idAt.remove(old.key());
            }
            if (entry.placed()) {
                idAt.put(entry.key(), entry.id());
            }
            setDirty();
        }

        void remove(Entry entry) {
            Entry old = byId.remove(entry.id());
            if (old != null && old.placed() && old.id().equals(idAt.get(old.key()))) {
                idAt.remove(old.key());
            }
            setDirty();
        }
    }

    /** Where a player's open menu can take them, in the order the menu shows. {@code hereId} is the waystone they opened it at. */
    private record Trip(String dimension, BlockPos from, String hereId, List<Target> targets) {
    }

    private record Target(boolean hub, Entry entry, int cost) {
    }

    private static final Map<UUID, Trip> OPEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIXED_HINT = new HashMap<>();
    /** Waystones a creative player just broke on purpose (so removing the block doesn't turn them into items). */
    private static final Set<String> FORGOTTEN = new HashSet<>();
    private static final List<BlockPos> BELLS = new ArrayList<>();

    private Waystones() {
    }

    static void init() {
        // Villages: remember bells as their chunks load, place the waystone once the area is loaded.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (level.dimension() != Level.OVERWORLD) {
                return;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BellBlockEntity) {
                    BELLS.add(blockEntity.getBlockPos());
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!BELLS.isEmpty() && server.getTickCount() % 20 == 0) {
                villageWaystones(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            OPEN.clear();
            BELLS.clear();
            FORGOTTEN.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> FORGOTTEN.clear());
        // A waystone that was moved while its owner was away: their list catches up the moment they join.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refresh(handler.getPlayer()));
        // Breaking: the hub's and the dungeon entrances' waystones stay put (and say so as soon as you swing at one); every other
        // one needs a pickaxe, then comes off as an item that remembers it.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level instanceof ServerLevel serverLevel && !player.isCreative() && !player.isSpectator()
                    && level.getBlockState(pos).getBlock() instanceof WaystoneBlock && isFixed(serverLevel, pos)) {
                fixedMessage(player, serverLevel);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(state.getBlock() instanceof WaystoneBlock) || player.isCreative() || !(level instanceof ServerLevel serverLevel)) {
                return true;
            }
            if (isFixed(serverLevel, pos)) {
                fixedMessage(player, serverLevel);
                return false;
            }
            if (!player.hasCorrectToolForDrops(state)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendOverlayMessage(Component.literal("A waystone can only be taken up with a pickaxe.").withStyle(ChatFormatting.RED));
                }
                return false;
            }
            return true;
        });
    }

    private static void fixedMessage(Player player, ServerLevel level) {
        if (player instanceof ServerPlayer serverPlayer) {
            long now = level.getServer().getTickCount();
            if (now - LAST_FIXED_HINT.getOrDefault(player.getUUID(), -100L) >= 40) {
                LAST_FIXED_HINT.put(player.getUUID(), now);
                serverPlayer.sendOverlayMessage(Component.literal(isHub(dimensionOf(level)) ? "Skyhaven's waystone can't be moved."
                        : "This waystone anchors the dungeon: it can't be taken away.").withStyle(ChatFormatting.RED));
            }
        }
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    private static boolean isHub(String dimension) {
        return dimension.startsWith("skyrpg_hub:");
    }

    // ---------------------------------------------------------------- using one

    /** The waystone standing at {@code pos}: the one we know, or (for a generated one nobody has used yet) a new entry. */
    static Entry entryAt(ServerLevel level, BlockPos pos) {
        Data data = data(level.getServer());
        Entry entry = withKnownFixed(level, pos, data.get(dimensionOf(level), pos));
        return entry;
    }

    /** The hub's and the dungeon entrances' waystones can't be picked up. */
    static boolean isFixed(ServerLevel level, BlockPos pos) {
        return entryAt(level, pos).fixed() == 1;
    }

    /** A player used the waystone at {@code pos} (holding a name tag to rename it, or not). */
    static void use(ServerPlayer player, BlockPos pos, ItemStack nameTag) {
        ServerLevel level = player.level();
        String dimension = dimensionOf(level);
        Entry entry = entryAt(level, pos);
        if (nameTag != null && nameTag.has(DataComponents.CUSTOM_NAME)) {
            String name = cleanName(nameTag.getHoverName().getString());
            if (name == null) {
                player.sendOverlayMessage(Component.literal("A waystone's name is 1 to " + MAX_NAME + " characters.").withStyle(ChatFormatting.RED));
            } else if (!canRename(player, entry)) {
                player.sendOverlayMessage(Component.literal(cannotRename(entry)).withStyle(ChatFormatting.RED));
            } else {
                rename(player, level, entry, name);
                if (!player.isCreative()) {
                    nameTag.shrink(1);
                }
                return;
            }
        }
        if (!isHub(dimension) && !Explorer.state(player).attuned(entry.id())) {
            attuneAndLight(player, level, pos, entry);
            WorldPayloads.banner(player, SkyRpgWorld.id("waystone"), "WAYSTONE ATTUNED", Component.literal(entry.name()),
                    Component.literal("Use any waystone to travel back here"), 0xFF5CE1E6);
        }
        openMenu(player, entry);
    }

    private static void attuneAndLight(ServerPlayer player, ServerLevel level, BlockPos pos, Entry entry) {
        Explorer.attune(player, entry.id(), entry.dimension(), pos, entry.name());
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(WaystoneBlock.LIT) && !state.getValue(WaystoneBlock.LIT)) {
            level.setBlock(pos, state.setValue(WaystoneBlock.LIT, true), Block.UPDATE_ALL);
        }
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.8);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
    }

    // ---------------------------------------------------------------- names

    /** The longest a waystone's name may be. */
    static final int MAX_NAME = 24;

    /** The name tidied up (no stray spaces), or null if it can't be a waystone's name. */
    static String cleanName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) {
            return null;
        }
        return name;
    }

    /** The one who placed it can rename it; a generated waystone (a wayshrine, a village, a dungeon) can be renamed by anyone attuned to it. Not Skyhaven's. */
    static boolean canRename(Player player, Entry entry) {
        if (isHub(entry.dimension())) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        return entry.owner().isEmpty() ? Explorer.state(player).attuned(entry.id()) : entry.owner().equals(player.getUUID().toString());
    }

    private static String cannotRename(Entry entry) {
        if (isHub(entry.dimension())) {
            return "Skyhaven's waystone keeps its name.";
        }
        return entry.owner().isEmpty() ? "Attune to this waystone before you rename it." : "Only the player who placed this waystone can rename it.";
    }

    private static void rename(ServerPlayer player, ServerLevel level, Entry entry, String name) {
        data(level.getServer()).put(entry.withName(name));
        refreshAll(level.getServer());
        player.sendOverlayMessage(Component.literal("Waystone renamed: " + name).withStyle(ChatFormatting.AQUA));
        level.playSound(null, BlockPos.of(entry.pos()), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
    }

    /** From the menu's Rename button: the name the player typed. Everything is checked again here. */
    static void renameFromMenu(ServerPlayer player, String raw) {
        Trip trip = OPEN.get(player.getUUID());
        ServerLevel level = player.level();
        if (trip == null || !dimensionOf(level).equals(trip.dimension()) || player.position().distanceTo(Vec3.atCenterOf(trip.from())) > USE_RANGE) {
            return;
        }
        Entry entry = data(level.getServer()).byId.get(trip.hereId());
        if (entry == null || !entry.placed() || !entry.key().equals(trip.dimension() + "|" + trip.from().asLong())) {
            return;
        }
        String name = cleanName(raw);
        if (name == null) {
            player.sendOverlayMessage(Component.literal("A waystone's name is 1 to " + MAX_NAME + " characters.").withStyle(ChatFormatting.RED));
        } else if (!canRename(player, entry)) {
            player.sendOverlayMessage(Component.literal(cannotRename(entry)).withStyle(ChatFormatting.RED));
        } else {
            rename(player, level, entry, name);
            openMenu(player, data(level.getServer()).byId.get(entry.id()));
        }
    }

    // ---------------------------------------------------------------- picking up and placing

    private static final String ID_TAG = "skyrpg_waystone";

    /** The item for a waystone that has been taken up: it remembers which waystone it is (and its name, which an anvil can change). */
    static ItemStack carryItem(Entry entry, boolean lit) {
        ItemStack stack = new ItemStack(WorldContent.WAYSTONE_ITEM);
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_TAG, entry.id());
        tag.putBoolean("lit", lit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name()).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.AQUA)));
        Style grey = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("A waystone you took up.").withStyle(grey),
                Component.literal("Place it to move it: everyone attuned to it").withStyle(grey),
                Component.literal("can travel to its new spot. Rename it in an anvil.").withStyle(grey))));
        return stack;
    }

    private static String identityOf(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? "" : custom.copyTag().getStringOr(ID_TAG, "");
    }

    /** A waystone block was just placed from an item: a brand-new waystone, or the one the item remembers, standing in a new place. */
    static void placed(ServerLevel level, BlockPos pos, LivingEntity placer, ItemStack stack) {
        Data data = data(level.getServer());
        String dimension = dimensionOf(level);
        Entry stale = data.get(dimension, pos);
        if (stale != null) {
            data.remove(stale); // a block we never saw go: that waystone is gone
        }
        String stackId = identityOf(stack);
        Entry carried = stackId.isEmpty() ? null : data.byId.get(stackId);
        boolean moved = carried != null && !carried.placed();
        String wanted = stack.has(DataComponents.CUSTOM_NAME) ? cleanName(stack.getHoverName().getString()) : null;
        boolean lit = false;
        Entry entry;
        if (moved) {
            entry = new Entry(carried.id(), dimension, pos.asLong(), wanted != null ? wanted : carried.name(), carried.owner(), true, 0);
            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            lit = custom != null && custom.copyTag().getBooleanOr("lit", false);
        } else {
            // No identity, or the waystone it remembers already stands somewhere (a copied item): this one is new.
            entry = new Entry(UUID.randomUUID().toString(), dimension, pos.asLong(), wanted != null ? wanted : autoName(level, pos),
                    placer instanceof ServerPlayer owner ? owner.getUUID().toString() : "", true, 0);
        }
        data.put(entry);
        if (placer instanceof ServerPlayer player) {
            if (!isHub(dimension)) {
                Explorer.attune(player, entry.id(), dimension, pos, entry.name());
                lit = true;
            }
            player.sendOverlayMessage(Component.literal(moved ? "Waystone set up again: " + entry.name() : "New waystone: " + entry.name() + " (attuned)")
                    .withStyle(ChatFormatting.AQUA));
        }
        BlockState state = level.getBlockState(pos);
        if (lit && state.hasProperty(WaystoneBlock.LIT) && !state.getValue(WaystoneBlock.LIT)) {
            level.setBlock(pos, state.setValue(WaystoneBlock.LIT, true), Block.UPDATE_ALL);
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        refreshAll(level.getServer());
    }

    /** A creative player broke this waystone: it is deleted, not carried. */
    static void forget(ServerLevel level, BlockPos pos) {
        Data data = data(level.getServer());
        Entry entry = data.get(dimensionOf(level), pos);
        if (entry != null) {
            data.remove(entry);
        }
        FORGOTTEN.add(dimensionOf(level) + "|" + pos.asLong());
        refreshAll(level.getServer());
    }

    /** The waystone block at {@code pos} is gone (broken, or taken by anything): it becomes an item that remembers it, nothing lost and nothing copied. */
    static void removed(ServerLevel level, BlockPos pos, boolean lit) {
        if (FORGOTTEN.remove(dimensionOf(level) + "|" + pos.asLong())) {
            return;
        }
        Data data = data(level.getServer());
        Entry entry = withKnownFixed(level, pos, data.get(dimensionOf(level), pos));
        if (entry.fixed() == 1) {
            data.remove(entry); // something took an anchored waystone away (a command): nothing to carry
            refreshAll(level.getServer());
            return;
        }
        data.put(entry.carried());
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, carryItem(entry, lit));
        drop.setDefaultPickUpDelay();
        drop.setUnlimitedLifetime();
        drop.setPermanentlyInvulnerable(true);
        level.addFreshEntity(drop);
        refreshAll(level.getServer());
    }

    /** The entry for the waystone at {@code pos} with its "fixed" flag worked out and saved; a generated waystone nobody has used yet gets its entry here. */
    private static Entry withKnownFixed(ServerLevel level, BlockPos pos, Entry existing) {
        Data data = data(level.getServer());
        String dimension = dimensionOf(level);
        Entry entry = existing;
        if (entry == null) {
            String legacy = legacyId(dimension, pos.asLong());
            entry = new Entry(data.byId.containsKey(legacy) ? UUID.randomUUID().toString() : legacy, dimension, pos.asLong(),
                    isHub(dimension) ? "Skyhaven" : autoName(level, pos), "", true, -1);
        }
        if (entry.fixed() < 0) {
            entry = entry.withFixed(isHub(dimension) || anchorsDungeon(level, pos) ? 1 : 0);
        }
        if (!entry.equals(existing)) {
            data.put(entry);
        }
        return entry;
    }

    // ---------------------------------------------------------------- keeping players' lists current

    /** Brings a player's attuned list up to date: names, positions, which ones are being carried; waystones that no longer exist drop out. */
    static void refresh(ServerPlayer player) {
        Data data = data(player.level().getServer());
        List<Explorer.Attuned> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Explorer.Attuned attuned : Explorer.state(player).waystones()) {
            Entry entry = data.byId.get(attuned.identity());
            if (entry != null && seen.add(entry.id())) {
                kept.add(new Explorer.Attuned(entry.dimension(), entry.pos(), entry.name(), entry.id(), entry.placed()));
            }
        }
        Explorer.setWaystones(player, kept);
    }

    static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    private static void openMenu(ServerPlayer player, Entry here) {
        ServerLevel level = player.level();
        Data data = data(level.getServer());
        List<Target> targets = new ArrayList<>();
        List<WorldPayloads.Destination> shown = new ArrayList<>();
        boolean inHub = isHub(here.dimension());
        if (!inHub) {
            targets.add(new Target(true, null, 0));
            shown.add(new WorldPayloads.Destination(WorldPayloads.Destination.HUB, "Skyhaven", "Return to the hub", 0));
        }
        refresh(player);
        BlockPos herePos = BlockPos.of(here.pos());
        for (Explorer.Attuned attuned : Explorer.state(player).waystones()) {
            Entry entry = data.byId.get(attuned.identity());
            if (entry == null || !entry.placed() || entry.id().equals(here.id())) {
                continue; // one that somebody is carrying can't be travelled to
            }
            int cost = inHub ? HUB_COST_FROM : cost(here.dimension(), herePos, entry);
            targets.add(new Target(false, entry, cost));
            shown.add(new WorldPayloads.Destination(WorldPayloads.Destination.WAYSTONE, entry.name(), describe(here, entry), cost));
        }
        OPEN.put(player.getUUID(), new Trip(here.dimension(), herePos, here.id(), targets));
        if (ServerPlayNetworking.canSend(player, WorldPayloads.WaystoneMenu.TYPE)) {
            ServerPlayNetworking.send(player, new WorldPayloads.WaystoneMenu(here.name(), shown, canRename(player, here)));
        } else {
            Notice.send(player, Notice.Kind.INFO, Component.literal(here.name() + ": install SkyRPG to travel between waystones.").withStyle(ChatFormatting.AQUA));
        }
        level.playSound(null, herePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    private static int cost(String dimension, BlockPos from, Entry to) {
        double distance = dimension.equals(to.dimension()) ? Math.sqrt(from.distSqr(BlockPos.of(to.pos()))) : 3000;
        return Math.min(MAX_COST, 1 + (int) (distance / 1000));
    }

    private static String describe(Entry here, Entry to) {
        BlockPos pos = BlockPos.of(to.pos());
        String world = switch (to.dimension()) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> to.dimension();
        };
        if (!here.dimension().equals(to.dimension())) {
            return world + " · " + pos.getX() + ", " + pos.getZ();
        }
        long blocks = Math.round(Math.sqrt(BlockPos.of(here.pos()).distSqr(pos)));
        return world + " · " + Msg.number(blocks) + " blocks · " + pos.getX() + ", " + pos.getZ();
    }

    // ---------------------------------------------------------------- travelling

    /** From the menu: go to the destination at {@code index}, if the player is still at their waystone and can pay. */
    static void travel(ServerPlayer player, int index) {
        Trip trip = OPEN.remove(player.getUUID());
        if (trip == null || index < 0 || index >= trip.targets().size() || !dimensionOf(player.level()).equals(trip.dimension())
                || player.position().distanceTo(Vec3.atCenterOf(trip.from())) > USE_RANGE) {
            return;
        }
        Target target = trip.targets().get(index);
        if (!player.isCreative() && player.experienceLevel < target.cost()) {
            player.sendOverlayMessage(Component.literal("You need " + target.cost() + " XP levels to travel there.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }
        // Refused in combat; otherwise the player stands still for the warm-up (see Warmups in core) before the trip.
        Warmups.request(player, target.hub() ? "the Hub" : target.entry().name(), false, () -> go(player, target));
    }

    /** The trip itself, once the warm-up is over: everything is checked again, since the player has been standing about. */
    private static void go(ServerPlayer player, Target target) {
        if (!player.isCreative() && player.experienceLevel < target.cost()) {
            player.sendOverlayMessage(Component.literal("You need " + target.cost() + " XP levels to travel there.").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel fromLevel = player.level();
        Vec3 from = player.position();
        if (target.hub()) {
            effects(fromLevel, from, false);
            // The warm-up has been served; /hub must not make the player wait again.
            Warmups.runBypassing(() -> fromLevel.getServer().getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), "hub"));
            return;
        }
        Entry entry = data(fromLevel.getServer()).byId.get(target.entry().id());
        if (entry == null || !entry.placed()) {
            player.sendOverlayMessage(Component.literal("That waystone has been taken up.").withStyle(ChatFormatting.RED));
            return;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension()));
        ServerLevel toLevel = fromLevel.getServer().getLevel(key);
        if (toLevel == null) {
            return;
        }
        BlockPos stone = BlockPos.of(entry.pos());
        toLevel.getChunk(stone);
        if (!(toLevel.getBlockState(stone).getBlock() instanceof WaystoneBlock)) {
            data(fromLevel.getServer()).remove(entry);
            player.sendOverlayMessage(Component.literal("That waystone is gone.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!player.isCreative()) {
            player.giveExperienceLevels(-target.cost());
        }
        Vec3 to = standingSpot(toLevel, stone);
        effects(fromLevel, from, false);
        float yaw = (float) Math.toDegrees(Math.atan2(-(stone.getX() + 0.5 - to.x), stone.getZ() + 0.5 - to.z));
        player.teleport(new TeleportTransition(toLevel, to, Vec3.ZERO, yaw, 10, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        effects(toLevel, to, true);
        player.sendOverlayMessage(Component.literal("✦ " + entry.name()).withStyle(ChatFormatting.AQUA));
    }

    /** Somewhere to stand next to a waystone: two free blocks over a solid one. */
    private static Vec3 standingSpot(ServerLevel level, BlockPos stone) {
        for (int distance = 1; distance <= 2; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos feet = stone.relative(direction, distance).above(dy);
                    // Two blocks out, the block in between must be open too, or we'd land behind a wall.
                    BlockPos between = stone.relative(direction, 1).above(dy);
                    if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && open(level, feet)
                            && (distance == 1 || open(level, between))) {
                        return Vec3.atBottomCenterOf(feet);
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(stone.above());
    }

    /** Room for a player: no collision at the feet or the head. */
    private static boolean open(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void effects(ServerLevel level, Vec3 at, boolean arrive) {
        level.sendParticles(arrive ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 50, 0.4, 0.9, 0.4, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 12, 0.3, 0.8, 0.3, 0.03);
        level.playSound(null, at.x, at.y, at.z, arrive ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, arrive ? 1.3f : 1.1f);
    }

    // ---------------------------------------------------------------- names and villages

    /** A name for a new waystone: the dungeon it guards, the village it stands in, or a made-up place and its biome. */
    static String autoName(ServerLevel level, BlockPos pos) {
        Dungeons.Dungeon dungeon = anchoredDungeon(level, pos);
        if (dungeon != null) {
            return dungeon.name();
        }
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
        String word = WORDS[(int) Math.floorMod(hash >>> 7, WORDS.length)] + PLACES[(int) Math.floorMod(hash >>> 23, PLACES.length)];
        Holder<Biome> biome = level.getBiome(pos);
        String biomeName = biome.unwrapKey().map(key -> title(key.identifier().getPath())).orElse("Wilds");
        if (level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid()) {
            return word + " Village";
        }
        return word + " · " + biomeName;
    }

    /** The dungeon this waystone anchors (it stands in the dungeon's entrance piece, not just somewhere near it), if any. */
    private static Dungeons.Dungeon anchoredDungeon(ServerLevel level, BlockPos pos) {
        for (Marker marker : DungeonMarkers.near(level, Vec3.atCenterOf(pos), 48)) {
            DungeonMarkers.Info info = DungeonMarkers.info(level, marker);
            if (info.kind().equals("dungeon") && info.dungeon() != null
                    && (info.piece() != null ? info.piece().isInside(pos) : marker.position().distanceToSqr(Vec3.atCenterOf(pos)) <= 24 * 24)) {
                return info.dungeon();
            }
        }
        return null;
    }

    /** True if this waystone anchors a dungeon: those stay put. */
    private static boolean anchorsDungeon(ServerLevel level, BlockPos pos) {
        return anchoredDungeon(level, pos) != null;
    }

    private static String title(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }

    private static void villageWaystones(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Data data = data(server);
        for (BlockPos bell : new ArrayList<>(BELLS)) {
            if (!level.isLoaded(bell.offset(-4, 0, -4)) || !level.isLoaded(bell.offset(4, 0, 4))) {
                continue;
            }
            BELLS.remove(bell);
            StructureStart village = level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE);
            if (!village.isValid() || data.villages.contains(village.getChunkPos().pack())) {
                continue;
            }
            BlockPos spot = besideBell(level, bell);
            if (spot == null) {
                continue;
            }
            data.villages.add(village.getChunkPos().pack());
            data.setDirty();
            level.setBlock(spot, WorldContent.WAYSTONE.defaultBlockState(), Block.UPDATE_ALL);
            String dimension = dimensionOf(level);
            String legacy = legacyId(dimension, spot.asLong());
            data.put(new Entry(data.byId.containsKey(legacy) ? UUID.randomUUID().toString() : legacy, dimension, spot.asLong(), autoName(level, spot), "", true, 0));
        }
    }

    /** A free spot on solid ground a couple of blocks from the bell. */
    private static BlockPos besideBell(ServerLevel level, BlockPos bell) {
        for (int distance = 2; distance <= 4; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos pos = bell.relative(direction, distance).above(dy);
                    BlockState here = level.getBlockState(pos);
                    if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
                            && level.getBlockState(pos.above()).isAir()
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** /waystone list: every waystone the server knows, including ones somebody is carrying. */
    static List<String> list(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (Entry entry : data(server).byId.values()) {
            lines.add(entry.name() + " · " + entry.dimension() + " " + BlockPos.of(entry.pos()).toShortString()
                    + (entry.placed() ? "" : " (carried)") + (entry.fixed() == 1 ? " (fixed)" : ""));
        }
        return lines;
    }
}
