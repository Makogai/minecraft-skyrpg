package com.skyrpg.progress.collections;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.progress.SkyRpgProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Collections (see docs/design/journal.md): gather items from the world, climb tiers I to IX, earn coins, SkyRPG XP,
 * permanent stats and Artisan Bench recipes. Counts are saved per player (an attachment) and synced to that player.
 *
 * <p>Public API for other mods in the progress module: {@link #count}, {@link #tier}, {@link #add}.
 */
public final class Collections {
    private static final int SYNC_INTERVAL = 20;

    /** Items gathered per collection id, saved with the player. */
    public static final AttachmentType<Map<Identifier, Long>> COUNTS = AttachmentRegistry.create(SkyRpgProgress.id("collections"), builder -> builder
            .persistent(Codec.unboundedMap(Identifier.CODEC, Codec.LONG).xmap(map -> (Map<Identifier, Long>) new HashMap<>(map), map -> map))
            .initializer(HashMap::new)
            .copyOnDeath());

    private static volatile Map<Identifier, CollectionDef> defs = Map.of();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static MinecraftServer server;

    private Collections() {
    }

    public static void init() {
        CollectionPayloads.register();
        SkyRpgLevel.init();
        JsonDataLoader.register(SkyRpgProgress.id("collections"), "skyrpg/collections", Collections::load);
        CollectionLevels.install((player, id) -> player instanceof ServerPlayer serverPlayer ? tier(serverPlayer, id) : ClientCollections.tier(id));
        CollectionLevels.installNames(id -> {
            CollectionDef def = defs.get(id);
            return def != null ? def.name() : ClientCollections.name(id);
        });
        ItemGains.GAINED.register((player, stack, source) -> gained(player, stack));
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Collections";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Map<Identifier, Long> counts = player.getAttachedOrCreate(COUNTS);
                for (CollectionDef def : defs.values()) {
                    int tier = def.tierFor(counts.getOrDefault(def.id(), 0L));
                    for (int i = 1; i <= tier; i++) {
                        def.tier(i).stats().forEach((stat, value) -> {
                            StatType type = StatTypes.get(stat);
                            if (type != null) {
                                out.add(type, value);
                            }
                        });
                    }
                }
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            DIRTY.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
            ServerPlayNetworking.send(handler.getPlayer(), defsPayload());
            sendCounts(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(tickServer -> {
            if (tickServer.getTickCount() % SYNC_INTERVAL != 0 || DIRTY.isEmpty()) {
                return;
            }
            for (UUID id : List.copyOf(DIRTY)) {
                DIRTY.remove(id);
                ServerPlayer player = tickServer.getPlayerList().getPlayer(id);
                if (player != null) {
                    sendCounts(player);
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            dispatcher.register(Commands.literal("collection").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("id", IdentifierArgument.id())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(defs.keySet(), builder))
                                    .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        Identifier id = IdentifierArgument.getId(ctx, "id");
                                        if (!defs.containsKey(id)) {
                                            ctx.getSource().sendFailure(Component.literal("No collection " + id));
                                            return 0;
                                        }
                                        long amount = LongArgumentType.getLong(ctx, "amount");
                                        add(target, id, amount);
                                        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " now has "
                                                + count(target, id) + " " + defs.get(id).name() + " (tier " + tier(target, id) + ")"), false);
                                        return 1;
                                    })))))
                    .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        target.setAttached(COUNTS, new HashMap<>());
                        DIRTY.add(target.getUUID());
                        Stats.refresh(target);
                        ctx.getSource().sendSuccess(() -> Component.literal("Cleared the collections of " + target.getName().getString()), false);
                        return 1;
                    }))));
            dispatcher.register(Commands.literal("skyrpglevel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("xp", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                SkyRpgLevel.addXp(target, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "xp"), "command");
                                ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " is SkyRPG Level "
                                        + SkyRpgLevel.level(target) + " (" + SkyRpgLevel.xp(target) + " XP)"), false);
                                return 1;
                            }))))
                    .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("xp", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                SkyRpgLevel.setXp(target, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "xp"));
                                ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " is SkyRPG Level "
                                        + SkyRpgLevel.level(target)), false);
                                return 1;
                            })))));
        });
    }

    // ---- API ---------------------------------------------------------------------------------------------------

    /** How many of the collection's items the player has gathered (0 if unknown). */
    public static long count(ServerPlayer player, Identifier collectionId) {
        return player.getAttachedOrCreate(COUNTS).getOrDefault(collectionId, 0L);
    }

    /** The player's tier in the collection: 0 for none, 1 to 9 for I to IX. */
    public static int tier(ServerPlayer player, Identifier collectionId) {
        CollectionDef def = defs.get(collectionId);
        return def == null ? 0 : def.tierFor(count(player, collectionId));
    }

    /** All loaded collections. */
    public static Collection<CollectionDef> all() {
        return defs.values();
    }

    public static CollectionDef get(Identifier id) {
        return defs.get(id);
    }

    /** Adds to a collection directly (commands, quest rewards), paying out any tiers it crosses. */
    public static void add(ServerPlayer player, Identifier collectionId, long amount) {
        CollectionDef def = defs.get(collectionId);
        if (def != null && amount > 0) {
            addTo(player, def, amount);
        }
    }

    // ---- counting ----------------------------------------------------------------------------------------------

    private static void gained(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (CollectionDef def : defs.values()) {
            if (def.matches(stack)) {
                addTo(player, def, stack.getCount());
            }
        }
    }

    private static void addTo(ServerPlayer player, CollectionDef def, long amount) {
        Map<Identifier, Long> counts = player.getAttachedOrCreate(COUNTS);
        long before = counts.getOrDefault(def.id(), 0L);
        long after = before + amount;
        counts.put(def.id(), after);
        DIRTY.add(player.getUUID());
        int oldTier = def.tierFor(before);
        int newTier = def.tierFor(after);
        if (newTier > oldTier) {
            for (int tier = oldTier + 1; tier <= newTier; tier++) {
                tierReached(player, def, tier);
            }
            Stats.refresh(player);
        }
    }

    private static void tierReached(ServerPlayer player, CollectionDef def, int tier) {
        CollectionDef.Tier reward = def.tier(tier);
        Wallet.give(player, reward.coins());
        SkyRpgLevel.addXp(player, reward.xp(), "collection tier");
        Component body = Msg.text(def.name() + " Collection ", ChatFormatting.GRAY)
                .append(Msg.text(CollectionLevels.roman(tier), ChatFormatting.AQUA, ChatFormatting.BOLD));
        Notice.of(Notice.Kind.COLLECTION).tag("TIER UP").hover(Component.literal(def.name() + ": " + Msg.number(def.thresholds()[tier - 1]) + " gathered"))
                .send(player, body);
        String line = reward.describe();
        if (!line.isEmpty()) {
            player.sendSystemMessage(Component.literal("   " + line).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!reward.message().isEmpty()) {
            player.sendSystemMessage(Component.literal("   " + reward.message()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f + tier * 0.06f);
    }

    // ---- data and sync -----------------------------------------------------------------------------------------

    private static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, CollectionDef> loaded = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
            try {
                loaded.put(entry.getKey(), CollectionDef.parse(entry.getKey(), (JsonObject) entry.getValue()));
            } catch (RuntimeException e) {
                SkyRpgProgress.LOGGER.error("Skipping collection {}: {}", entry.getKey(), e.toString());
            }
        }
        defs = Map.copyOf(loaded);
        SkyRpgProgress.LOGGER.info("Loaded {} collections", loaded.size());
        MinecraftServer current = server;
        if (current != null) {
            current.execute(() -> {
                CollectionPayloads.Defs payload = defsPayload();
                for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                    sendCounts(player);
                }
            });
        }
    }

    private static CollectionPayloads.Defs defsPayload() {
        List<CollectionPayloads.View> views = new ArrayList<>();
        for (CollectionDef def : defs.values()) {
            List<Long> thresholds = new ArrayList<>();
            List<String> rewards = new ArrayList<>();
            for (int i = 0; i < def.thresholds().length; i++) {
                thresholds.add(def.thresholds()[i]);
                rewards.add(def.tier(i + 1).describe());
            }
            Identifier icon = def.icon() != null && BuiltInRegistries.ITEM.containsKey(def.icon()) ? def.icon() : Identifier.withDefaultNamespace("barrier");
            views.add(new CollectionPayloads.View(def.id(), def.category(), def.name(), icon, thresholds, rewards));
        }
        return new CollectionPayloads.Defs(views);
    }

    private static void sendCounts(ServerPlayer player) {
        ServerPlayNetworking.send(player, new CollectionPayloads.Counts(new HashMap<>(player.getAttachedOrCreate(COUNTS))));
    }

    /** Whether the player has reached the tier (for other mods in this module). */
    public static boolean reached(Player player, Identifier collectionId, int tier) {
        return player instanceof ServerPlayer serverPlayer && tier(serverPlayer, collectionId) >= tier;
    }
}
