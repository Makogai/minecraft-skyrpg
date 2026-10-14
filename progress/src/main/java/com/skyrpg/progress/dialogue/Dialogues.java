package com.skyrpg.progress.dialogue;

import com.skyrpg.core.event.NpcInteractions;
import com.skyrpg.core.event.ProgressEvents;
import com.skyrpg.core.net.WaypointTarget;
import com.skyrpg.core.text.Msg;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.dialogue.DialogueData.Choice;
import com.skyrpg.progress.dialogue.DialogueData.Conversation;
import com.skyrpg.progress.dialogue.DialogueData.Page;
import com.skyrpg.progress.quests.QuestData;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.progress.quests.QuestData.Quest;
import com.skyrpg.progress.quests.QuestState;
import com.skyrpg.progress.quests.Quests;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

/**
 * Server side of conversations: which one an NPC click opens (a quest ready to hand in, then a talk objective, then an offer,
 * then a reminder), the page the player is on, and what each choice does. A player has at most one open conversation; every
 * answer is checked against the page the server last showed, the NPC must still be near, and a conversation can only be one
 * the server opened itself.
 */
public final class Dialogues {
    /** How far from the NPC a conversation may be carried on. */
    private static final double RANGE = 10;

    /** What a conversation is for, which decides what finishing it does. */
    public enum Kind {
        OFFER, PROGRESS, COMPLETE, TALK, PREVIEW
    }

    private record Pick(Kind kind, Quest quest, int objective, Conversation conversation) {
    }

    private static final class Session {
        final Conversation conversation;
        final Kind kind;
        final String quest;
        final int objective;
        final boolean readOnly;
        int page;
        int serial;
        List<Choice> choices = List.of();

        Session(Conversation conversation, Kind kind, String quest, int objective, boolean readOnly) {
            this.conversation = conversation;
            this.kind = kind;
            this.quest = quest;
            this.objective = objective;
            this.readOnly = readOnly;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> LAST_MARKERS = new HashMap<>();
    private static int serials;

    private Dialogues() {
    }

    public static void init() {
        DialoguePayloads.register();
        com.skyrpg.core.data.JsonDataLoader.register(SkyRpgProgress.id("dialogue"), DialogueData.DIRECTORY, DialogueData::load);
        NpcInteractions.INTERACT.register(Dialogues::onInteract);
        ServerPlayNetworking.registerGlobalReceiver(DialoguePayloads.Choose.TYPE,
                (payload, context) -> context.server().execute(() -> choose(context.player(), payload.serial(), payload.choice())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SESSIONS.remove(handler.getPlayer().getUUID());
            LAST_MARKERS.remove(handler.getPlayer().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0 && !SESSIONS.isEmpty()) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    Session session = SESSIONS.get(player.getUUID());
                    if (session != null && !session.conversation.narrator() && !near(player, session.conversation.speaker())) {
                        end(player);
                    }
                }
            }
        });
    }

    /** Whether the player is in the middle of a conversation. */
    public static boolean busy(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- choosing what to say

    private static boolean onInteract(ServerPlayer player, Identifier npc) {
        Pick pick = pick(player, npc.toString());
        if (pick == null) {
            return false;
        }
        if (pick.conversation() == null) {
            // Ready to hand in, but nothing to say about it: just pay out.
            Quests.turnIn(player, pick.quest());
            return true;
        }
        NpcInteractions.Info info = NpcInteractions.get(npc);
        if (info != null) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:entity.villager.ambient"));
            if (sound != null) {
                Msg.playSound(player, sound, 0.7f, 1.0f + player.getRandom().nextFloat() * 0.1f);
            }
        }
        open(player, pick.conversation(), pick.kind(), pick.quest().id().toString(), pick.objective(), false);
        return true;
    }

    /** The conversation this NPC has for the player right now, or null. Ready beats talk beats offer beats reminder. */
    private static Pick pick(ServerPlayer player, String npc) {
        QuestState state = QuestState.of(player);
        Pick talk = null;
        Pick offer = null;
        Pick progress = null;
        for (Quest quest : QuestData.quests().values()) {
            if (!Quests.open(state, quest)) {
                continue;
            }
            if (!Quests.accepted(state, quest)) {
                if (offer == null && quest.giverNpc().equals(npc) && quest.hasDialogue("offer")) {
                    offer = new Pick(Kind.OFFER, quest, -1, DialogueData.get(quest.dialogue("offer")));
                }
                continue;
            }
            if (Quests.ready(state, quest)) {
                if (quest.turnInNpc().equals(npc)) {
                    return new Pick(Kind.COMPLETE, quest, -1, quest.hasDialogue("complete") ? DialogueData.get(quest.dialogue("complete")) : null);
                }
                continue;
            }
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                if (talk == null && o.type().equals("talk") && o.str("npc").equals(npc) && state.counter("t:" + quest.id() + "#" + i) == 0
                        && quest.hasDialogue("talk_" + i)) {
                    talk = new Pick(Kind.TALK, quest, i, DialogueData.get(quest.dialogue("talk_" + i)));
                }
            }
            if (progress == null && quest.giverNpc().equals(npc) && quest.hasDialogue("progress")) {
                progress = new Pick(Kind.PROGRESS, quest, -1, DialogueData.get(quest.dialogue("progress")));
            }
        }
        Pick best = talk != null ? talk : offer != null ? offer : progress;
        return best != null && best.conversation() != null ? best : null;
    }

    /**
     * What marker each NPC should show this player: 1 = a quest to pick up (or an NPC to talk to), 2 = a quest in progress,
     * 3 = a quest ready to hand in. Sent to the client by {@link #sendMarkers}.
     */
    private static Map<String, Integer> markers(ServerPlayer player) {
        QuestState state = QuestState.of(player);
        Map<String, Integer> out = new HashMap<>();
        for (Quest quest : QuestData.quests().values()) {
            if (!Quests.open(state, quest)) {
                continue;
            }
            if (!Quests.accepted(state, quest)) {
                if (quest.hasGiver() && quest.hasDialogue("offer")) {
                    mark(out, quest.giverNpc(), 1);
                }
                continue;
            }
            if (Quests.ready(state, quest)) {
                mark(out, quest.turnInNpc(), 3);
                continue;
            }
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                if (o.type().equals("talk") && state.counter("t:" + quest.id() + "#" + i) == 0 && quest.hasDialogue("talk_" + i)) {
                    mark(out, o.str("npc"), 1);
                }
            }
            if (quest.hasDialogue("progress")) {
                mark(out, quest.giverNpc(), 2);
            }
        }
        return out;
    }

    private static void mark(Map<String, Integer> out, String npc, int kind) {
        if (!npc.isEmpty()) {
            // Ready outranks "to pick up", which outranks "in progress".
            int current = out.getOrDefault(npc, 0);
            if (rank(kind) > rank(current)) {
                out.put(npc, kind);
            }
        }
    }

    private static int rank(int kind) {
        return switch (kind) {
            case 3 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            default -> 0;
        };
    }

    /** Sends the player's NPC markers if they changed since last time. */
    public static void sendMarkers(ServerPlayer player) {
        Map<String, Integer> now = markers(player);
        if (!now.equals(LAST_MARKERS.get(player.getUUID()))) {
            LAST_MARKERS.put(player.getUUID(), now);
            ServerPlayNetworking.send(player, new DialoguePayloads.Markers(now));
        }
    }

    // ---------------------------------------------------------------- opening

    /** A journal entry: the narrator speaks and the player reads. {@code kind} is {@code offer} or {@code complete}. */
    public static void narrate(ServerPlayer player, Quest quest, String kind) {
        Conversation conversation = DialogueData.get(quest.dialogue(kind));
        if (conversation != null) {
            open(player, conversation, Kind.PREVIEW, quest.id().toString(), -1, true);
        }
    }

    /** Re-reads a dialogue from the Journal: the pages without choices, and nothing happens at the end. */
    public static void reread(ServerPlayer player, Quest quest, String kind) {
        QuestState state = QuestState.of(player);
        // Only what the player has already heard.
        boolean heard = switch (kind) {
            case "offer" -> Quests.accepted(state, quest) || state.isDone(quest.id().toString());
            case "complete" -> state.isDone(quest.id().toString());
            default -> false;
        };
        if (heard && quest.hasDialogue(kind)) {
            narrateReread(player, quest.dialogue(kind));
        }
    }

    /** Opens any conversation as a read-only preview (op command). */
    public static boolean preview(ServerPlayer player, Identifier id) {
        return narrateReread(player, id);
    }

    private static boolean narrateReread(ServerPlayer player, Identifier id) {
        Conversation conversation = DialogueData.get(id);
        if (conversation == null) {
            return false;
        }
        open(player, conversation, Kind.PREVIEW, "", -1, true);
        return true;
    }

    private static void open(ServerPlayer player, Conversation conversation, Kind kind, String quest, int objective, boolean readOnly) {
        Session session = new Session(conversation, kind, quest, objective, readOnly);
        SESSIONS.put(player.getUUID(), session);
        show(player, session);
    }

    private static void show(ServerPlayer player, Session session) {
        Page page = session.conversation.pages().get(session.page);
        boolean last = session.page == session.conversation.pages().size() - 1;
        List<Choice> choices = new ArrayList<>();
        if (!session.readOnly) {
            choices.addAll(page.choices());
            // An offer or a reminder from a shopkeeper also offers their shop, so a quest never gets between you and the wares.
            NpcInteractions.Info info = session.conversation.narrator() ? null : NpcInteractions.get(Identifier.parse(session.conversation.speaker()));
            boolean talkative = session.kind == Kind.PROGRESS || session.kind == Kind.OFFER;
            if (talkative && last && info != null && info.hasAction() && choices.stream().noneMatch(c -> c.action().equals("shop"))) {
                Choice shop = new Choice("Let me see your wares.", "shop", "", new com.google.gson.JsonObject());
                if (session.kind == Kind.PROGRESS) {
                    if (choices.isEmpty()) {
                        choices.add(new Choice("Thanks, I will get on with it.", "end", "", new com.google.gson.JsonObject()));
                    }
                    choices.addFirst(shop);
                } else {
                    choices.add(shop);
                }
            }
        }
        session.choices = List.copyOf(choices);
        session.serial = ++serials;
        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        String playerName = player.getGameProfile().name();
        for (Choice choice : choices) {
            labels.add(choice.label().replace("{player}", playerName));
            actions.add(choice.action());
        }
        String speaker = session.conversation.speaker();
        NpcInteractions.Info info = speaker.isEmpty() ? null : NpcInteractions.get(Identifier.parse(speaker));
        String name = info != null ? info.name() : speaker.isEmpty() ? "Journal" : speaker;
        int color = info != null ? info.color() : 0xF2B33D;
        String skin = info == null ? "" : info.skin().withPath(path -> "textures/" + path + ".png").toString();
        ServerPlayNetworking.send(player, new DialoguePayloads.Show(session.serial, speaker, name, 0xFF000000 | color, skin, page.mood(),
                page.text().replace("{player}", playerName), labels, actions, session.readOnly, session.page, session.conversation.pages().size()));
    }

    // ---------------------------------------------------------------- choices

    private static boolean near(ServerPlayer player, String npc) {
        NpcInteractions.Info info = NpcInteractions.get(Identifier.parse(npc));
        return info != null && player.level().dimension().identifier().equals(info.dimension()) && player.position().distanceToSqr(info.pos()) <= RANGE * RANGE;
    }

    private static void end(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            ServerPlayNetworking.send(player, new DialoguePayloads.Close());
        }
    }

    private static void choose(ServerPlayer player, int serial, int choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.serial != serial) {
            return;
        }
        if (!session.conversation.narrator() && !session.readOnly && !near(player, session.conversation.speaker())) {
            end(player);
            return;
        }
        if (choice == DialoguePayloads.CANCEL) {
            end(player);
            return;
        }
        if (choice == DialoguePayloads.ADVANCE) {
            if (!session.choices.isEmpty()) {
                return;
            }
            advance(player, session);
            return;
        }
        if (choice < 0 || choice >= session.choices.size()) {
            return;
        }
        Choice picked = session.choices.get(choice);
        switch (picked.action()) {
            case "next" -> advance(player, session);
            case "goto" -> {
                int index = session.conversation.indexOf(picked.page());
                if (index < 0) {
                    advance(player, session);
                } else {
                    session.page = index;
                    show(player, session);
                }
            }
            case "accept", "turn_in", "end" -> finish(player, session);
            case "shop" -> {
                // Handing in or finishing a talk counts first; an offer or a reminder is just set aside.
                if (session.kind == Kind.COMPLETE || session.kind == Kind.TALK) {
                    finish(player, session);
                } else {
                    end(player);
                }
                if (!session.conversation.narrator()) {
                    NpcInteractions.runAction(player, Identifier.parse(session.conversation.speaker()));
                }
            }
            case "run" -> {
                run(player, session, picked);
                String target = picked.page();
                int index = target.isEmpty() ? -1 : session.conversation.indexOf(target);
                if (index >= 0) {
                    session.page = index;
                    show(player, session);
                } else {
                    advance(player, session);
                }
            }
            default -> end(player);
        }
    }

    private static void advance(ServerPlayer player, Session session) {
        if (session.page + 1 < session.conversation.pages().size()) {
            session.page++;
            show(player, session);
        } else {
            finish(player, session);
        }
    }

    /** The conversation reached its end: do what it was for. */
    private static void finish(ServerPlayer player, Session session) {
        SESSIONS.remove(player.getUUID());
        ServerPlayNetworking.send(player, new DialoguePayloads.Close());
        if (session.readOnly) {
            return;
        }
        Quest quest = session.quest.isEmpty() ? null : QuestData.quest(session.quest);
        if (quest != null) {
            switch (session.kind) {
                case OFFER -> Quests.accept(player, quest);
                case COMPLETE -> Quests.turnIn(player, quest);
                case TALK -> Quests.talked(player, quest, session.objective);
                default -> {
                }
            }
        }
        if (!session.conversation.narrator()) {
            ProgressEvents.NPC_TALKED.invoker().onTalked(player, Identifier.parse(session.conversation.speaker()));
        }
    }

    /** The safe actions a choice may run. Nothing here gives anything: it only points the player somewhere. */
    private static void run(ServerPlayer player, Session session, Choice choice) {
        switch (choice.args().has("do") ? choice.args().get("do").getAsString() : "") {
            case "waypoint" -> {
                String npc = choice.args().has("npc") ? choice.args().get("npc").getAsString() : "";
                NpcInteractions.Info info = npc.isEmpty() ? null : NpcInteractions.get(Identifier.parse(npc));
                if (info != null) {
                    WaypointTarget.send(player, info.name(), info.pos(), 0xFFF2B33D, info.dimension());
                    com.skyrpg.core.text.Notice.send(player, com.skyrpg.core.text.Notice.Kind.QUEST, net.minecraft.network.chat.Component.literal("Tracking " + info.name() + ". Follow the compass at the top of your screen."));
                }
            }
            case "track" -> {
                Quest quest = QuestData.quest(choice.args().has("quest") ? choice.args().get("quest").getAsString() : session.quest);
                if (quest != null) {
                    Quests.track(player, quest, -1);
                }
            }
            default -> {
            }
        }
    }
}
