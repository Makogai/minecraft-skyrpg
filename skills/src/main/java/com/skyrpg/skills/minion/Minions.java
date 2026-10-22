package com.skyrpg.skills.minion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.SkillXpSources;
import com.skyrpg.skills.Skill;
import com.skyrpg.skills.Skills;
import com.skyrpg.skills.SkyRpgSkills;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Minions: a block that works one block for its owner, once every {@link #INTERVAL_TICKS} (mines its ore, chops its
 * log, harvests its crop). It keeps the drops and {@link #XP_SHARE} of the skill XP until the owner right-clicks it to
 * collect. Work is counted from the world's game time, so it carries on while nobody is near, up to
 * {@link #MAX_ACTIONS} actions (two hours). Insight doesn't boost minion XP.
 */
public final class Minions {
    public static final int INTERVAL_TICKS = 600;
    public static final int MAX_ACTIONS = 240;
    public static final double XP_SHARE = 0.5;

    public static final MinionBlock BLOCK;
    public static final Item ITEM;

    static {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, SkyRpgSkills.id("minion"));
        BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new MinionBlock(BlockBehaviour.Properties.of().setId(blockKey)
                .mapColor(MapColor.METAL).strength(2.0f, 1200.0f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.IMMOVEABLE)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SkyRpgSkills.id("minion"));
        ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    }

    record Entry(String owner, String target, long since) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("owner").forGetter(Entry::owner), Codec.STRING.optionalFieldOf("target", "").forGetter(Entry::target),
                Codec.LONG.fieldOf("since").forGetter(Entry::since)).apply(i, Entry::new));
    }

    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("minions", Map.of()).forGetter(d -> d.byKey)).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgSkills.id("minions"), Data::new, CODEC, null);

        /** Minions by "dimension|pos". */
        final Map<String, Entry> byKey = new LinkedHashMap<>();

        Data() {
        }

        Data(Map<String, Entry> entries) {
            byKey.putAll(entries);
        }
    }

    private Minions() {
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(BLOCK) || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            Entry entry = data(level.getServer()).byKey.get(key(level, pos));
            if (entry != null && !entry.owner().equals(player.getStringUUID()) && !player.isCreative()) {
                serverPlayer.sendSystemMessage(Msg.text("This minion belongs to someone else.", ChatFormatting.RED));
                return false;
            }
            if (entry != null && entry.owner().equals(player.getStringUUID())) {
                collect(serverPlayer, (ServerLevel) level, pos, entry);
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (state.is(BLOCK)) {
                Data data = data(level.getServer());
                if (data.byKey.remove(key(level, pos)) != null) {
                    data.setDirty();
                }
            }
        });
    }

    /** How many minions a player may have: one, plus one per 10 levels of their best skill (six at level 50). */
    public static int limit(ServerPlayer player) {
        int best = 0;
        for (Skill skill : Skills.all()) {
            best = Math.max(best, PlayerSkills.getLevel(player, skill));
        }
        return 1 + best / 10;
    }

    static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Data data = data(level.getServer());
        long owned = data.byKey.values().stream().filter(e -> e.owner().equals(player.getStringUUID())).count();
        if (owned >= limit(player)) {
            level.removeBlock(pos, false);
            if (!player.isCreative()) {
                ItemGains.give(player, new ItemStack(ITEM), ItemGains.Source.OTHER);
            }
            player.sendSystemMessage(Msg.text("You can have " + limit(player) + " minions: one, plus one per 10 levels of your best skill.",
                    ChatFormatting.RED));
            return;
        }
        data.byKey.put(key(level, pos), new Entry(player.getStringUUID(), "", level.getGameTime()));
        data.setDirty();
        player.sendSystemMessage(Msg.text("Minion placed. Right-click it with a block to work (an ore, a log, or seeds for a crop).",
                ChatFormatting.GRAY));
    }

    /** Right-click: set its work if holding a block it can work, else collect what it made. */
    static void use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        Data data = data(level.getServer());
        String key = key(level, pos);
        Entry entry = data.byKey.get(key);
        if (entry == null) {
            entry = new Entry(player.getStringUUID(), "", level.getGameTime());
            data.byKey.put(key, entry);
            data.setDirty();
        }
        if (!entry.owner().equals(player.getStringUUID())) {
            player.sendSystemMessage(Msg.text("This minion belongs to someone else.", ChatFormatting.RED));
            return;
        }
        BlockState work = work(held);
        if (work != null && !SkillXpSources.forBlock(work).isEmpty()) {
            String target = BuiltInRegistries.BLOCK.getKey(work.getBlock()).toString();
            if (!target.equals(entry.target())) {
                collect(player, level, pos, entry);
                data.byKey.put(key, new Entry(entry.owner(), target, level.getGameTime()));
                data.setDirty();
                player.sendSystemMessage(Msg.text("Your minion now works ", ChatFormatting.GRAY)
                        .append(work.getBlock().getName().withStyle(ChatFormatting.GOLD))
                        .append(Msg.text(": one every " + INTERVAL_TICKS / 20 + "s, with half the XP.", ChatFormatting.GRAY)));
                level.playSound(null, pos, SoundEvents.VILLAGER_WORK_TOOLSMITH, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1.2f);
                return;
            }
        }
        if (entry.target().isEmpty()) {
            player.sendSystemMessage(Msg.text("Right-click your minion with a block to give it work: an ore, a log, or seeds for a crop.",
                    ChatFormatting.YELLOW));
            return;
        }
        collect(player, level, pos, entry);
    }

    /** The block a held item would set a minion to work: the block itself, or a fully grown crop for seeds. */
    static BlockState work(ItemStack held) {
        if (!(held.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() == BLOCK) {
            return null;
        }
        Block block = blockItem.getBlock();
        return block instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge()) : block.defaultBlockState();
    }

    /** The block a placed minion works, or null. */
    static BlockState target(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return null;
        }
        Entry entry = data(level.getServer()).byKey.get(key(level, pos));
        if (entry == null || entry.target().isEmpty()) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(entry.target()))
                .map(block -> block instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge()) : block.defaultBlockState())
                .orElse(null);
    }

    /** Actions done since the last collection, capped at {@link #MAX_ACTIONS}. */
    static int actions(Level level, Entry entry) {
        return (int) Math.min(MAX_ACTIONS, Math.max(0, (level.getGameTime() - entry.since()) / INTERVAL_TICKS));
    }

    static boolean full(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return false;
        }
        Entry entry = data(level.getServer()).byKey.get(key(level, pos));
        return entry != null && actions(level, entry) >= MAX_ACTIONS;
    }

    /** Gives the owner everything the minion made since last time, and half the XP. */
    private static void collect(ServerPlayer player, ServerLevel level, BlockPos pos, Entry entry) {
        BlockState work = target(level, pos);
        int actions = actions(level, entry);
        Data data = data(level.getServer());
        // Keep the leftover part of an action so collecting often doesn't lose time.
        long since = actions >= MAX_ACTIONS ? level.getGameTime() : entry.since() + (long) actions * INTERVAL_TICKS;
        data.byKey.put(key(level, pos), new Entry(entry.owner(), entry.target(), since));
        data.setDirty();
        if (work == null || actions == 0) {
            long left = INTERVAL_TICKS - (level.getGameTime() - entry.since()) % INTERVAL_TICKS;
            player.sendSystemMessage(Msg.text("Nothing yet: next one in " + Math.max(1, left / 20) + "s.", ChatFormatting.GRAY));
            return;
        }
        Map<Item, Integer> counts = new LinkedHashMap<>();
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < actions; i++) {
            for (ItemStack drop : Block.getDrops(work, level, pos, null, null, ItemStack.EMPTY)) {
                counts.merge(drop.getItem(), drop.getCount(), Integer::sum);
                drops.add(drop);
            }
        }
        drops.forEach(drop -> ItemGains.give(player, drop, ItemGains.Source.OTHER));
        Map<Identifier, Double> xp = new HashMap<>();
        for (SkillXpSources.Grant grant : SkillXpSources.forBlock(work)) {
            xp.merge(grant.skill(), grant.xp() * XP_SHARE * actions, Double::sum);
        }
        xp.forEach((id, amount) -> {
            Skill skill = Skills.get(id);
            if (skill != null) {
                PlayerSkills.addXp(player, skill, amount);
            }
        });
        var line = Msg.text("Minion: " + actions + " × ", ChatFormatting.GRAY).append(work.getBlock().getName().withStyle(ChatFormatting.GOLD));
        counts.forEach((item, n) -> line.append(Msg.text("  +" + n + " ", ChatFormatting.GREEN)).append(item.getName(new ItemStack(item))));
        xp.forEach((id, amount) -> {
            Skill skill = Skills.get(id);
            if (skill != null) {
                line.append(Msg.text("  +" + Msg.number(amount) + " " + skill.name() + " XP", ChatFormatting.DARK_AQUA));
            }
        });
        if (actions >= MAX_ACTIONS) {
            line.append(Msg.text("  (it was full)", ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(line);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.1f);
    }

    static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }
}
