package com.skyrpg.progress.quests;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ProgressEvents;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.quests.QuestData.Chapter;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.progress.quests.QuestData.Quest;
import com.skyrpg.vfx.Vfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Skyhaven Chronicle questline (see docs/design/journal.md): quests are data ({@link QuestData}), each player's
 * progress is an attachment ({@link QuestState}), and the objective checkers live in {@link Objectives}. Event hooks (kills,
 * NPC talks, bench crafts, slayer quests) bump counters; everything else is polled once a second. A quest completes the
 * moment all its objectives are met, then pays out its rewards.
 */
public final class Quests {
    private static final Identifier FX_COMPLETE = Identifier.fromNamespaceAndPath("skyrpg_vfx", "rare_drop");
    private static final Identifier FX_CHAPTER = Identifier.fromNamespaceAndPath("skyrpg_vfx", "level_up");
    private static MinecraftServer server;

    private Quests() {
    }

    public static void init() {
        QuestPayloads.register();
        com.skyrpg.progress.dialogue.Dialogues.init();
        JsonDataLoader.register(SkyRpgProgress.id("quests"), QuestData.DIRECTORY, files -> {
            QuestData.load(files);
            if (server != null) {
                server.execute(() -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        sendDefs(player);
                        refresh(player);
                    }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> joined.execute(() -> {
            sendDefs(handler.getPlayer());
            refresh(handler.getPlayer());
        }));
        ServerPlayNetworking.registerGlobalReceiver(QuestPayloads.Action.TYPE, (payload, context) -> context.server().execute(() -> action(context.player(), payload)));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && entity != killer) {
                killed(killer, entity);
            }
        });
        ProgressEvents.BENCH_CRAFTED.register((player, item, count) -> bump(player, "c:" + item, count, true));
        ProgressEvents.SLAYER_QUEST.register((player, line, tier, completed) -> {
            String prefix = completed ? "s:" : "p:";
            bump(player, prefix + line, tier, false);
            bump(player, prefix + "any", tier, false);
            if (completed) {
                bump(player, "p:" + line, tier, false);
                bump(player, "p:any", tier, false);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            for (ServerPlayer player : ticking.getPlayerList().getPlayers()) {
                if ((ticking.getTickCount() + player.getId()) % 20 == 0) {
                    refresh(player);
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(command()));
    }

    // ---------------------------------------------------------------- state helpers

    static void sendDefs(ServerPlayer player) {
        ServerPlayNetworking.send(player, new QuestPayloads.Defs(QuestData.rawJson()));
    }

    private static void save(ServerPlayer player, QuestState state) {
        if (!state.equals(QuestState.of(player))) {
            player.setAttached(QuestState.STATE, state);
        }
    }

    /** Whether the quest has been unlocked (its prerequisites are done). */
    public static boolean available(QuestState state, Quest quest) {
        for (Identifier prerequisite : quest.prerequisites()) {
            if (!state.isDone(prerequisite.toString())) {
                return false;
            }
        }
        return true;
    }

    /** Unlocked, not done and not abandoned: either waiting for its NPC to offer it, or under way. */
    public static boolean open(QuestState state, Quest quest) {
        String key = quest.id().toString();
        return !state.isDone(key) && state.counter("x:" + key) == 0 && available(state, quest);
    }

    /** The player has taken the quest on (accepted it from its NPC, or it started by itself). */
    public static boolean accepted(QuestState state, Quest quest) {
        return state.counter("a:" + quest.id()) > 0;
    }

    /** Open and waiting for an NPC to offer it. */
    public static boolean offered(QuestState state, Quest quest) {
        return open(state, quest) && !accepted(state, quest);
    }

    /** Accepted and under way (including ready to hand in). */
    public static boolean active(QuestState state, Quest quest) {
        return open(state, quest) && accepted(state, quest);
    }

    /** Every objective is met and the quest only needs handing in. */
    public static boolean ready(QuestState state, Quest quest) {
        return active(state, quest) && state.counter("r:" + quest.id()) > 0;
    }

    /** The quest to show when nothing is tracked: the first open main quest, else the first open side quest. */
    public static Quest firstActive(QuestState state) {
        Quest side = null;
        for (Quest quest : QuestData.quests().values()) {
            if (open(state, quest)) {
                if (!quest.side()) {
                    return quest;
                }
                if (side == null) {
                    side = quest;
                }
            }
        }
        return side;
    }

    // ---------------------------------------------------------------- hooks

    private static void bump(ServerPlayer player, String key, int amount, boolean add) {
        QuestState state = QuestState.of(player);
        int next = add ? state.counter(key) + amount : Math.max(state.counter(key), amount);
        if (next != state.counter(key)) {
            save(player, state.withCounter(key, next));
            refresh(player);
        }
    }

    private static void killed(ServerPlayer player, LivingEntity entity) {
        QuestState state = QuestState.of(player);
        QuestState next = state;
        for (Quest quest : QuestData.quests().values()) {
            if (!active(state, quest) || ready(state, quest)) {
                continue;
            }
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                if (o.type().equals("kill") && Objectives.killMatches(entity, o)) {
                    String key = Objectives.killKey(quest, i);
                    if (next.counter(key) < o.target()) {
                        next = next.withCounter(key, next.counter(key) + 1);
                    }
                }
            }
        }
        if (!next.equals(state)) {
            save(player, next);
            refresh(player);
        }
    }

    private static void action(ServerPlayer player, QuestPayloads.Action action) {
        switch (action.kind()) {
            case "opened" -> bump(player, "journal", 1, false);
            case "track" -> {
                Quest quest = QuestData.quest(action.quest());
                if (quest != null) {
                    track(player, quest, action.objective());
                }
            }
            case "reread" -> {
                Quest quest = QuestData.quest(action.quest());
                if (quest != null) {
                    com.skyrpg.progress.dialogue.Dialogues.reread(player, quest, action.objective() == 1 ? "complete" : "offer");
                }
            }
            case "abandon" -> {
                Quest quest = QuestData.quest(action.quest());
                if (quest != null) {
                    abandon(player, quest);
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- the engine

    /** Rechecks every open quest for the player, announces new ones and completes the finished ones. */
    public static void refresh(ServerPlayer player) {
        if (QuestData.quests().isEmpty()) {
            return;
        }
        migrate(player);
        for (int pass = 0; pass < 12; pass++) {
            QuestState state = QuestState.of(player);
            Map<String, Integer> live = new HashMap<>();
            List<Quest> announce = new ArrayList<>();
            List<Quest> narrate = new ArrayList<>();
            List<Quest> readied = new ArrayList<>();
            Quest finished = null;
            for (Quest quest : QuestData.quests().values()) {
                if (!open(state, quest)) {
                    continue;
                }
                String key = quest.id().toString();
                if (!state.seen().contains(key)) {
                    state = state.withSeen(key);
                    announce.add(quest);
                }
                if (!accepted(state, quest)) {
                    if (quest.hasGiver()) {
                        continue;
                    }
                    // No NPC offers this one: it starts by itself, with a journal entry.
                    state = state.withCounter("a:" + key, 1);
                    narrate.add(quest);
                }
                boolean wasReady = state.counter("r:" + key) > 0;
                boolean all = true;
                for (int i = 0; i < quest.objectives().size(); i++) {
                    Objective o = quest.objectives().get(i);
                    int value = wasReady ? o.target() : Objectives.value(player, state, quest, i);
                    if (!wasReady && (o.type().equals("visit") || o.flag("sticky")) && value >= o.target() && state.counter(Objectives.visitKey(quest, i)) == 0) {
                        state = state.withCounter(Objectives.visitKey(quest, i), 1);
                    }
                    live.put(key + "#" + i, Math.min(value, o.target()));
                    all &= value >= o.target();
                }
                if (all && !wasReady) {
                    if (quest.hasTurnIn()) {
                        state = state.withCounter("r:" + key, 1);
                        readied.add(quest);
                    } else if (finished == null) {
                        finished = quest;
                    }
                }
            }
            if (state.tracked().isEmpty() || !activeQuest(state, state.tracked())) {
                Quest first = firstActive(state);
                state = state.withTracked(first == null ? "" : first.id().toString());
            }
            save(player, state);
            if (!live.equals(QuestState.live(player))) {
                player.setAttached(QuestState.LIVE, Map.copyOf(live));
            }
            for (Quest quest : announce) {
                announceNew(player, quest);
            }
            if (!narrate.isEmpty()) {
                NARRATE.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).addAll(narrate.stream().map(q -> q.id().toString()).toList());
            }
            narrateWhenSafe(player);
            for (Quest quest : readied) {
                announceReady(player, quest);
            }
            if (finished == null) {
                com.skyrpg.progress.dialogue.Dialogues.sendMarkers(player);
                return;
            }
            complete(player, finished, true);
        }
    }

    /** Quests that started by themselves and whose journal entry has not been shown yet. */
    private static final Map<java.util.UUID, List<String>> NARRATE = new HashMap<>();

    /** Shows waiting journal entries, one at a time, once the player is out of a fight (so a page never pops up mid-combat). */
    private static void narrateWhenSafe(ServerPlayer player) {
        List<String> waiting = NARRATE.get(player.getUUID());
        if (waiting == null || waiting.isEmpty()) {
            return;
        }
        boolean fighting = player.tickCount - player.getLastHurtByMobTimestamp() < 200 || player.tickCount - player.getLastHurtMobTimestamp() < 100;
        if (fighting || com.skyrpg.progress.dialogue.Dialogues.busy(player)) {
            return;
        }
        Quest quest = QuestData.quest(waiting.removeFirst());
        if (quest != null) {
            com.skyrpg.progress.dialogue.Dialogues.narrate(player, quest, "offer");
        }
    }

    /**
     * Brings a player saved before the dialogue questline into it, once: quests they had been told about count as accepted, a
     * quest whose old version ({@code replaces}) was done is done, quests whose objectives are already met are done without
     * ceremony, and everything a finished quest needed before it is finished too.
     */
    private static void migrate(ServerPlayer player) {
        QuestState state = QuestState.of(player);
        if (state.counter("qv") > 0) {
            return;
        }
        Set<String> done = new HashSet<>(state.done());
        Set<String> seen = new HashSet<>(state.seen());
        for (Quest quest : QuestData.quests().values()) {
            for (String old : quest.replaces()) {
                if (state.done().contains(old)) {
                    done.add(quest.id().toString());
                }
                if (state.seen().contains(old)) {
                    seen.add(quest.id().toString());
                }
            }
        }
        QuestState next = new QuestState(Set.copyOf(done), Set.copyOf(seen), state.counters(), state.tracked());
        for (Quest quest : QuestData.quests().values()) {
            String key = quest.id().toString();
            if (!seen.contains(key) || done.contains(key)) {
                continue;
            }
            boolean met = !quest.objectives().isEmpty();
            for (int i = 0; i < quest.objectives().size() && met; i++) {
                Objective o = quest.objectives().get(i);
                met = !o.type().equals("talk") && Objectives.value(player, next, quest, i) >= o.target();
            }
            next = met ? next.withDone(key) : next.withCounter("a:" + key, 1);
        }
        Set<String> before = new HashSet<>();
        for (String key : next.done()) {
            Quest quest = QuestData.quest(key);
            if (quest != null) {
                collectPrerequisites(quest, before);
            }
        }
        for (String id : before) {
            next = next.withDone(id).withSeen(id);
        }
        save(player, next.withCounter("qv", 1));
    }

    private static void announceReady(ServerPlayer player, Quest quest) {
        if (quest.objectives().isEmpty()) {
            return;
        }
        Notice.of(Notice.Kind.QUEST).tag("RETURN").hover(Component.literal("Open the Journal (K)")).send(player,
                Component.literal(quest.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                        .append(Component.literal("  Return to " + quest.turnInName()).withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.UI_TOAST_IN, 0.7f, 1.4f);
        Chapter chapter = QuestData.chapters().get(quest.chapter());
        ServerPlayNetworking.send(player, new QuestPayloads.Toast(chapter == null ? "minecraft:book" : chapter.icon(), "OBJECTIVES DONE",
                quest.title(), "Return to " + quest.turnInName(), 0xFFF2B33D));
    }

    /** The player agreed to take the quest on (its NPC's offer dialogue ended well). */
    public static void accept(ServerPlayer player, Quest quest) {
        QuestState state = QuestState.of(player);
        if (!offered(state, quest)) {
            return;
        }
        save(player, state.withCounter("a:" + quest.id(), 1).withSeen(quest.id().toString()).withTracked(quest.id().toString()));
        String lead = quest.objectives().isEmpty() ? "Return to " + quest.turnInName() : quest.objectives().getFirst().label();
        Notice.of(Notice.Kind.QUEST).tag("ACCEPTED").hover(Component.literal("Open the Journal (K)"))
                .send(player, Component.literal(quest.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                        .append(Component.literal("  " + lead).withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.UI_TOAST_IN, 0.7f, 1.2f);
        refresh(player);
    }

    /** The player finished the quest's hand-in dialogue: pay out. */
    public static void turnIn(ServerPlayer player, Quest quest) {
        if (ready(QuestState.of(player), quest)) {
            complete(player, quest, true);
            refresh(player);
        }
    }

    /** The player finished the dialogue a talk objective asks for. */
    public static void talked(ServerPlayer player, Quest quest, int objective) {
        QuestState state = QuestState.of(player);
        if (active(state, quest) && objective >= 0 && objective < quest.objectives().size() && quest.objectives().get(objective).type().equals("talk")) {
            save(player, state.withCounter(Objectives.talkKey(quest, objective), 1));
            refresh(player);
        }
    }

    private static boolean activeQuest(QuestState state, String id) {
        Quest quest = QuestData.quest(id);
        return quest != null && open(state, quest);
    }

    private static void announceNew(ServerPlayer player, Quest quest) {
        Chapter chapter = QuestData.chapters().get(quest.chapter());
        String lead = quest.hasGiver() ? "Speak to " + quest.giverName() : quest.objectives().isEmpty() ? "" : quest.objectives().getFirst().label();
        Notice.of(Notice.Kind.QUEST).tag(quest.side() ? "SIDE QUEST" : "NEW QUEST").hover(Component.literal("Open the Journal (K)"))
                .send(player, Component.literal(quest.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                        .append(Component.literal(lead.isEmpty() ? "" : "  " + lead).withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.UI_TOAST_IN, 0.7f, 1.2f);
        ServerPlayNetworking.send(player, new QuestPayloads.Toast(chapter == null ? "minecraft:book" : chapter.icon(), quest.side() ? "SIDE QUEST" : "NEW QUEST",
                quest.title(), lead, 0xFFF2B33D));
    }

    /** Marks a quest done and pays its rewards (and the chapter's completion notice, if it was the last one). */
    public static void complete(ServerPlayer player, Quest quest, boolean rewards) {
        String key = quest.id().toString();
        QuestState state = QuestState.of(player).withDone(key).withSeen(key);
        save(player, state);
        if (!rewards) {
            return;
        }
        QuestData.Rewards r = quest.rewards();
        List<Component> parts = new ArrayList<>();
        if (r.coins() > 0) {
            Wallet.give(player, r.coins());
            parts.add(Msg.coins(r.coins()));
        }
        if (r.xp() > 0) {
            Adapters.addXp(player, r.xp(), quest.title());
            parts.add(Component.literal("+" + r.xp() + " SkyRPG XP").withStyle(ChatFormatting.AQUA));
        }
        for (QuestData.RewardItem reward : r.items()) {
            Item item = BuiltInRegistries.ITEM.getOptional(reward.item()).orElse(null);
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, reward.count());
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
            parts.add(Component.literal(reward.count() > 1 ? reward.count() + "x " : "").withStyle(ChatFormatting.WHITE).append(name));
        }
        Component body = Component.literal(quest.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        MutableList rewardLine = new MutableList();
        for (Component part : parts) {
            rewardLine.add(part);
        }
        Notice.of(Notice.Kind.QUEST_COMPLETE).send(player, rewardLine.appendTo(body.copy()));
        if (!r.hint().isEmpty()) {
            Notice.of(Notice.Kind.QUEST).tag("HINT").send(player, Component.literal(r.hint()).withStyle(ChatFormatting.GRAY));
        }
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        Vfx.play(player, FX_COMPLETE);
        Chapter chapter = QuestData.chapters().get(quest.chapter());
        boolean chapterDone = chapter != null && !quest.side();
        if (chapterDone) {
            for (Quest other : QuestData.inChapter(quest.chapter())) {
                if (!other.side() && !QuestState.of(player).isDone(other.id().toString())) {
                    chapterDone = false;
                    break;
                }
            }
        }
        ServerPlayNetworking.send(player, new QuestPayloads.Toast(chapter == null ? "minecraft:book" : chapter.icon(),
                chapterDone ? "CHAPTER COMPLETE" : "QUEST COMPLETE", chapterDone ? chapter.title() : quest.title(),
                chapterDone ? "The Skyhaven Chronicle continues..." : "", chapterDone ? 0xFFFFAA00 : 0xFF55DD77));
        if (chapterDone) {
            Notice.of(Notice.Kind.CHAPTER_COMPLETE).send(player, Component.literal(chapter.title()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            Vfx.play(player, FX_CHAPTER);
        }
    }

    /** A small helper that joins reward parts with a dot. */
    private static final class MutableList {
        private final List<Component> parts = new ArrayList<>();

        void add(Component part) {
            parts.add(part);
        }

        net.minecraft.network.chat.MutableComponent appendTo(net.minecraft.network.chat.MutableComponent line) {
            for (int i = 0; i < parts.size(); i++) {
                line.append(Component.literal(i == 0 ? "  " : "  ·  ").withStyle(ChatFormatting.DARK_GRAY)).append(parts.get(i));
            }
            return line;
        }
    }

    private static void abandon(ServerPlayer player, Quest quest) {
        QuestState state = QuestState.of(player);
        if (!quest.side() || !open(state, quest)) {
            say(player, "Only open side quests can be abandoned.");
            return;
        }
        save(player, state.withCounter("x:" + quest.id(), 1));
        say(player, "Abandoned " + quest.title() + ".");
        refresh(player);
    }

    /** Tracks the quest and points the waypoint at objective {@code index} (negative = the first unfinished one with a marker). */
    public static void track(ServerPlayer player, Quest quest, int index) {
        QuestState state = QuestState.of(player);
        if (!open(state, quest)) {
            say(player, "That quest isn't open.");
            return;
        }
        save(player, state.withTracked(quest.id().toString()));
        // Not taken yet: point at whoever offers it. Done with its goals: point at whoever takes it.
        if (!accepted(state, quest) || ready(state, quest)) {
            boolean offer = !accepted(state, quest);
            String npc = offer ? quest.giverNpc() : quest.turnInNpc();
            com.skyrpg.core.event.NpcInteractions.Info info = npc.isEmpty() ? null : com.skyrpg.core.event.NpcInteractions.get(Identifier.parse(npc));
            if (info == null) {
                WaypointTarget.clear(player);
                say(player, "Tracking " + quest.title() + ".");
            } else {
                WaypointTarget.send(player, info.name(), info.pos(), QuestTracking.COLOR, info.dimension());
                say(player, (offer ? "Speak to " : "Return to ") + info.name() + ": tracking them.");
            }
            return;
        }
        Map<String, Integer> live = QuestState.live(player);
        int target = index;
        if (target < 0 || target >= quest.objectives().size()) {
            target = -1;
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                if (live.getOrDefault(quest.id() + "#" + i, 0) < o.target() && o.trackable()) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            WaypointTarget.clear(player);
            say(player, "Tracking " + quest.title() + ". Nothing to point at right now.");
            return;
        }
        say(player, QuestTracking.track(player, quest, target));
    }

    private static void say(ServerPlayer player, String text) {
        Notice.send(player, Notice.Kind.QUEST, Component.literal(text));
    }

    // ---------------------------------------------------------------- commands

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("quest").executes(ctx -> list(ctx.getSource().getPlayerOrException()));
        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource().getPlayerOrException())));
        root.then(Commands.literal("track").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Quest quest = QuestData.quest(QuestState.of(player).tracked());
            if (quest == null) {
                say(player, "You have no open quest.");
                return 0;
            }
            track(player, quest, -1);
            return 1;
        }).then(idArgument("quest", false).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Quest quest = QuestData.quest(fullId(StringArgumentType.getString(ctx, "quest")));
            if (quest == null) {
                say(player, "No such quest.");
                return 0;
            }
            track(player, quest, -1);
            return 1;
        })));
        root.then(Commands.literal("abandon").then(idArgument("quest", false).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Quest quest = QuestData.quest(fullId(StringArgumentType.getString(ctx, "quest")));
            if (quest == null) {
                say(player, "No such quest.");
                return 0;
            }
            abandon(player, quest);
            return 1;
        })));
        // Ops: /quest dialogue <conversation id> reads any conversation (nothing happens at its end)
        root.then(Commands.literal("dialogue").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.greedyString()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        com.skyrpg.progress.dialogue.DialogueData.all().keySet().stream().map(Identifier::toString).toList(), builder))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Identifier id = Identifier.tryParse(fullId(StringArgumentType.getString(ctx, "id")));
                            if (id == null || !com.skyrpg.progress.dialogue.Dialogues.preview(player, id)) {
                                ctx.getSource().sendFailure(Component.literal("No such conversation."));
                                return 0;
                            }
                            return 1;
                        })));
        // Ops: /quest complete|set|reset|accept <players> <quest|all>
        for (String verb : List.of("complete", "set", "reset", "accept")) {
            root.then(Commands.literal(verb).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("players", EntityArgument.players()).then(idArgument("quest", true).executes(ctx -> {
                        String text = StringArgumentType.getString(ctx, "quest");
                        int count = 0;
                        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                            count += admin(ctx.getSource(), player, verb, text) ? 1 : 0;
                        }
                        int total = count;
                        ctx.getSource().sendSuccess(() -> Component.literal("/quest " + verb + " " + text + ": " + total + " player(s)"), true);
                        return count;
                    }))));
        }
        return root;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument(String name, boolean allowAll) {
        return Commands.argument(name, StringArgumentType.greedyString()).suggests((ctx, builder) -> {
            List<String> ids = new ArrayList<>();
            if (allowAll) {
                ids.add("all");
            }
            QuestData.quests().keySet().forEach(id -> ids.add(id.toString()));
            return SharedSuggestionProvider.suggest(ids, builder);
        });
    }

    private static String fullId(String text) {
        return text.contains(":") ? text : SkyRpgProgress.MOD_ID + ":" + text;
    }

    private static boolean admin(CommandSourceStack source, ServerPlayer player, String verb, String text) {
        if (text.equals("all")) {
            switch (verb) {
                case "reset" -> {
                    player.setAttached(QuestState.STATE, QuestState.EMPTY);
                    refresh(player);
                }
                case "complete" -> {
                    for (Quest quest : QuestData.quests().values()) {
                        if (!QuestState.of(player).isDone(quest.id().toString())) {
                            complete(player, quest, false);
                        }
                    }
                }
                default -> {
                    source.sendFailure(Component.literal("Use a quest id with set."));
                    return false;
                }
            }
            return true;
        }
        Quest quest = QuestData.quest(fullId(text));
        if (quest == null) {
            source.sendFailure(Component.literal("No such quest: " + text));
            return false;
        }
        switch (verb) {
            case "complete" -> complete(player, quest, true);
            case "accept" -> {
                save(player, QuestState.of(player).withCounter("a:" + quest.id(), 1).withSeen(quest.id().toString()));
                refresh(player);
            }
            case "reset" -> {
                save(player, QuestState.of(player).without(quest.id().toString()));
                refresh(player);
            }
            case "set" -> {
                // Everything before it counts as done (no rewards), and it becomes the open quest.
                Set<String> before = new HashSet<>();
                collectPrerequisites(quest, before);
                QuestState state = QuestState.of(player).without(quest.id().toString());
                for (String id : before) {
                    state = state.withDone(id).withSeen(id);
                }
                save(player, state.withTracked(quest.id().toString()));
                refresh(player);
            }
            default -> {
            }
        }
        return true;
    }

    private static void collectPrerequisites(Quest quest, Set<String> into) {
        for (Identifier prerequisite : quest.prerequisites()) {
            Quest parent = QuestData.quest(prerequisite);
            if (parent != null && into.add(prerequisite.toString())) {
                collectPrerequisites(parent, into);
            }
        }
    }

    private static int list(ServerPlayer player) {
        QuestState state = QuestState.of(player);
        Map<String, Integer> live = QuestState.live(player);
        for (Chapter chapter : QuestData.chapters().values()) {
            List<Quest> quests = QuestData.inChapter(chapter.id());
            int done = 0;
            for (Quest quest : quests) {
                done += state.isDone(quest.id().toString()) ? 1 : 0;
            }
            Notice.of(Notice.Kind.QUEST).tag(chapter.title().toUpperCase()).click("/quest track").send(player,
                    Component.literal(done + "/" + quests.size()).withStyle(done == quests.size() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            for (Quest quest : quests) {
                if (offered(state, quest)) {
                    player.sendSystemMessage(Component.literal("   " + quest.title()).withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("  Speak to " + quest.giverName()).withStyle(ChatFormatting.GRAY)));
                } else if (ready(state, quest)) {
                    player.sendSystemMessage(Component.literal("   " + quest.title()).withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("  Return to " + quest.turnInName()).withStyle(ChatFormatting.GRAY)));
                } else if (active(state, quest)) {
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < quest.objectives().size(); i++) {
                        Objective o = quest.objectives().get(i);
                        int value = live.getOrDefault(quest.id() + "#" + i, 0);
                        text.append(value >= o.target() ? " [x] " : " [ ] ").append(o.label());
                        if (o.target() > 1 && value < o.target()) {
                            text.append(" (").append(value).append("/").append(o.target()).append(")");
                        }
                    }
                    player.sendSystemMessage(Component.literal("   " + quest.title()).withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(text.toString()).withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        return 1;
    }
}
