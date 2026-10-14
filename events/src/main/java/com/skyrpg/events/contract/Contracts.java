package com.skyrpg.events.contract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.action.Catches;
import com.skyrpg.core.world.PlacedBlocks;
import com.skyrpg.events.SkyRpgEvents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The contracts board: three contracts a day for the whole server, picked from data templates ({@link ContractTemplate})
 * in different categories, among those someone online can do. Everyone's progress counts towards the same goal, and when
 * it's met everyone who helped gets the full reward (players who are offline get it when they next join). Delivery
 * contracts are handed in on the board. Each contract can be rerolled once a day for coins. Saved in the world folder
 * ({@code skyrpg/contracts.json}).
 */
public final class Contracts {
    public static final String DIRECTORY = "skyrpg/contracts";
    public static final int PER_DAY = 3;
    public static final long REROLL_COST = 500;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One of today's contracts. */
    private static final class Active {
        Identifier template;
        int amount;
        int progress;
        final Set<UUID> contributors = new HashSet<>();
        boolean done;
        boolean rerolled;
    }

    private static Map<Identifier, ContractTemplate> templates = Map.of();
    private static final List<Active> TODAY = new ArrayList<>();
    /** Rewards waiting for players who helped while offline: player -> templates whose reward they're owed (with amounts). */
    private static final Map<UUID, List<int[]>> PENDING = new HashMap<>();
    private static final Map<UUID, List<Identifier>> PENDING_TEMPLATES = new HashMap<>();
    private static long day = -1;
    private static MinecraftServer server;
    private static boolean dirty;
    private static boolean changed;

    private Contracts() {
    }

    public static void init() {
        ContractPayloads.register();
        JsonDataLoader.register(SkyRpgEvents.id("contracts"), DIRECTORY, Contracts::load);
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            read();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            write();
            server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> joined.execute(() -> {
            refreshDay();
            payPending(handler.player);
            send(handler.player, Component.empty(), true);
        }));
        ServerPlayNetworking.registerGlobalReceiver(ContractPayloads.Action.TYPE, (payload, context) -> {
            if (payload.reroll()) {
                reroll(context.player(), payload.index());
            } else {
                handIn(context.player(), payload.index());
            }
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative() && !PlacedBlocks.isPlaced(level, pos)) {
                progress(serverPlayer, active -> {
                    ContractTemplate.Goal goal = template(active).goal();
                    return goal.type() == ContractTemplate.GoalType.BREAK_BLOCKS && goal.rule().matches(state);
                });
            }
        });
        Catches.CAUGHT.register((player, stack, seaCreature) -> progress(player, active -> {
            ContractTemplate.Goal goal = template(active).goal();
            return goal.type() == ContractTemplate.GoalType.CATCH && goal.rule().matchesCatch(seaCreature);
        }));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && entity != killer) {
                boolean elite = entity.entityTags().contains("skyrpg_elite");
                progress(killer, active -> {
                    ContractTemplate.Goal goal = template(active).goal();
                    return goal.type() == ContractTemplate.GoalType.KILL_MOBS && goal.rule().matches(entity)
                            || goal.type() == ContractTemplate.GoalType.KILL_ELITES && elite;
                });
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (ticking.getTickCount() % 20 == 0) {
                refreshDay();
            }
            if (changed && ticking.getTickCount() % 5 == 0) {
                changed = false;
                ticking.getPlayerList().getPlayers().forEach(p -> send(p, Component.empty(), true));
            }
            if (dirty && ticking.getTickCount() % 600 == 0) {
                write();
            }
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, ContractTemplate> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, ContractTemplate.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgEvents.LOGGER.warn("Skipping contract {}: {}", id, e.getMessage());
            }
        });
        templates = loaded;
        SkyRpgEvents.LOGGER.info("Loaded {} contract templates", loaded.size());
    }

    private static ContractTemplate template(Active active) {
        return templates.get(active.template);
    }

    // ---------------------------------------------------------------- the day's contracts

    /** On a new day (or with no contracts yet), picks new ones. */
    private static void refreshDay() {
        if (server == null || templates.isEmpty()) {
            return;
        }
        long today = LocalDate.now().toEpochDay();
        boolean broken = TODAY.stream().anyMatch(active -> template(active) == null);
        if (today != day || TODAY.isEmpty() || broken) {
            day = today;
            TODAY.clear();
            Set<String> categories = new HashSet<>();
            for (int i = 0; i < PER_DAY; i++) {
                Active active = pick(categories, null);
                if (active != null) {
                    categories.add(template(active).category());
                    TODAY.add(active);
                }
            }
            dirty = true;
            changed = true;
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                server.getPlayerList().broadcastSystemMessage(Msg.text("📋 New contracts on the board! ", ChatFormatting.GOLD)
                        .append(Msg.text("(K -> Contracts)", ChatFormatting.GRAY)), false);
            }
        }
    }

    /** A random template someone online can do, preferring categories not used yet (and never {@code not}). */
    private static Active pick(Set<String> usedCategories, Identifier not) {
        List<ContractTemplate> doable = new ArrayList<>();
        for (ContractTemplate template : templates.values()) {
            if (template.id().equals(not) || TODAY.stream().anyMatch(a -> a.template.equals(template.id()))) {
                continue;
            }
            if (template.skill() == null || bestLevel(template.skill()) >= template.level()) {
                doable.add(template);
            }
        }
        List<ContractTemplate> fresh = doable.stream().filter(t -> !usedCategories.contains(t.category())).toList();
        List<ContractTemplate> choices = fresh.isEmpty() ? doable : fresh;
        if (choices.isEmpty()) {
            return null;
        }
        RandomSource random = RandomSource.create();
        ContractTemplate template = choices.get(random.nextInt(choices.size()));
        Active active = new Active();
        active.template = template.id();
        active.amount = nice(template.minAmount() + random.nextInt(template.maxAmount() - template.minAmount() + 1));
        return active;
    }

    /** The highest level anyone online has in the skill (0 if nobody is online). */
    private static int bestLevel(Identifier skill) {
        int best = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            best = Math.max(best, SkillLevels.level(player, skill));
        }
        return best;
    }

    /** Rounds to a tidy number (8, 16, 25, 48, 150...). */
    private static int nice(int amount) {
        int step = amount < 20 ? 1 : amount < 100 ? 4 : amount < 500 ? 25 : 50;
        return Math.max(1, Math.round(amount / (float) step) * step);
    }

    // ---------------------------------------------------------------- progress

    private interface Matcher {
        boolean matches(Active active);
    }

    private static void progress(ServerPlayer player, Matcher matcher) {
        for (Active active : TODAY) {
            if (!active.done && template(active) != null && matcher.matches(active)) {
                add(player, active, 1);
            }
        }
    }

    private static void add(ServerPlayer player, Active active, int count) {
        active.progress = Math.min(active.amount, active.progress + count);
        active.contributors.add(player.getUUID());
        dirty = true;
        changed = true;
        if (active.progress >= active.amount) {
            complete(active);
        }
    }

    private static void complete(Active active) {
        active.done = true;
        ContractTemplate template = template(active);
        server.getPlayerList().broadcastSystemMessage(Msg.text("✔ CONTRACT COMPLETE! ", ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Msg.text(template.name(), ChatFormatting.YELLOW))
                .append(Msg.text("  Rewards went to everyone who helped.", ChatFormatting.GRAY)), false);
        for (UUID helper : active.contributors) {
            ServerPlayer player = server.getPlayerList().getPlayer(helper);
            if (player != null) {
                pay(player, template, active.amount);
            } else {
                PENDING.computeIfAbsent(helper, id -> new ArrayList<>()).add(new int[] {active.amount});
                PENDING_TEMPLATES.computeIfAbsent(helper, id -> new ArrayList<>()).add(template.id());
            }
        }
    }

    /** Coins and XP grow with the amount (they're for the smallest one); items don't. */
    private static double scale(ContractTemplate template, int amount) {
        return amount / (double) Math.max(1, template.minAmount());
    }

    private static void pay(ServerPlayer player, ContractTemplate template, int amount) {
        double scale = scale(template, amount);
        long coins = Math.round(template.coins() * scale);
        Wallet.give(player, coins);
        template.xp().forEach((skill, xp) -> SkillLevels.giveXp(player, skill, xp * scale));
        template.items().forEach((item, count) -> give(player, new ItemStack(item, count)));
        Notice.of(Notice.Kind.CONTRACT).tag("COMPLETE").send(player, Msg.text("+" + Msg.number(coins) + " coins", ChatFormatting.GOLD)
                .append(Msg.text(" for " + template.name(), ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
    }

    private static void payPending(ServerPlayer player) {
        List<int[]> amounts = PENDING.remove(player.getUUID());
        List<Identifier> owed = PENDING_TEMPLATES.remove(player.getUUID());
        if (amounts == null || owed == null) {
            return;
        }
        Notice.send(player, Notice.Kind.CONTRACT, Msg.text("Contracts finished while you were away:", ChatFormatting.GOLD));
        for (int i = 0; i < owed.size(); i++) {
            ContractTemplate template = templates.get(owed.get(i));
            if (template != null) {
                pay(player, template, amounts.get(i)[0]);
            }
        }
        dirty = true;
    }

    // ---------------------------------------------------------------- board actions

    private static void handIn(ServerPlayer player, int index) {
        if (index < 0 || index >= TODAY.size()) {
            return;
        }
        Active active = TODAY.get(index);
        ContractTemplate template = template(active);
        if (template == null || active.done || template.goal().type() != ContractTemplate.GoalType.DELIVER) {
            return;
        }
        Item item = template.goal().item();
        int needed = active.amount - active.progress;
        int taken = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && taken < needed; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int n = Math.min(needed - taken, stack.getCount());
                stack.shrink(n);
                taken += n;
            }
        }
        if (taken == 0) {
            send(player, Component.literal("You don't have any ").append(new ItemStack(item).getHoverName()).append(" to hand in."), false);
            return;
        }
        add(player, active, taken);
        send(player, Component.literal("Handed in " + Msg.number(taken) + " ").append(new ItemStack(item).getHoverName()), true);
    }

    private static void reroll(ServerPlayer player, int index) {
        if (index < 0 || index >= TODAY.size()) {
            return;
        }
        Active active = TODAY.get(index);
        if (active.done || active.rerolled || active.progress > 0) {
            send(player, Component.literal(active.rerolled ? "Already rerolled today." : "Only untouched contracts can be rerolled."), false);
            return;
        }
        if (!Wallet.take(player, REROLL_COST)) {
            send(player, Component.literal("A reroll costs " + Msg.number(REROLL_COST) + " coins."), false);
            return;
        }
        Set<String> others = new HashSet<>();
        TODAY.stream().filter(a -> a != active && template(a) != null).forEach(a -> others.add(template(a).category()));
        Active fresh = pick(others, active.template);
        if (fresh == null) {
            Wallet.give(player, REROLL_COST);
            send(player, Component.literal("No other contract to offer right now."), false);
            return;
        }
        fresh.rerolled = true;
        TODAY.set(index, fresh);
        dirty = true;
        changed = true;
        send(player, Component.literal("New contract: " + template(fresh).name()), true);
    }

    // ---------------------------------------------------------------- sync and saving

    private static void send(ServerPlayer player, Component notice, boolean good) {
        List<ContractPayloads.Entry> entries = new ArrayList<>();
        for (Active active : TODAY) {
            ContractTemplate template = template(active);
            if (template == null) {
                continue;
            }
            double scale = scale(template, active.amount);
            List<Component> rewards = new ArrayList<>();
            rewards.add(Component.literal("+" + Msg.number(Math.round(template.coins() * scale)) + " coins").withColor(0xF2B33D));
            template.xp().forEach((skill, xp) -> rewards.add(Component.literal("+" + Msg.number(Math.round(xp * scale)) + " "
                    + SkillLevels.displayName(skill) + " XP").withColor(0x5CE1E6)));
            template.items().forEach((item, count) -> rewards.add(Component.literal("+" + count + " ").append(new ItemStack(item).getHoverName())));
            ItemStack deliver = template.goal().type() == ContractTemplate.GoalType.DELIVER ? new ItemStack(template.goal().item()) : ItemStack.EMPTY;
            entries.add(new ContractPayloads.Entry(template.name(), template.description(), template.category(), active.progress, active.amount,
                    rewards, deliver, active.contributors.contains(player.getUUID()), active.done, active.rerolled));
        }
        ServerPlayNetworking.send(player, new ContractPayloads.Board(entries, REROLL_COST, notice, good));
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg").resolve("contracts.json");
    }

    private static void read() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            day = json.get("day").getAsLong();
            TODAY.clear();
            for (JsonElement element : json.getAsJsonArray("contracts")) {
                JsonObject c = element.getAsJsonObject();
                Active active = new Active();
                active.template = Identifier.parse(c.get("template").getAsString());
                active.amount = c.get("amount").getAsInt();
                active.progress = c.get("progress").getAsInt();
                active.done = c.get("done").getAsBoolean();
                active.rerolled = c.get("rerolled").getAsBoolean();
                c.getAsJsonArray("contributors").forEach(id -> active.contributors.add(UUID.fromString(id.getAsString())));
                TODAY.add(active);
            }
            PENDING.clear();
            PENDING_TEMPLATES.clear();
            if (json.has("pending")) {
                json.getAsJsonObject("pending").entrySet().forEach(entry -> {
                    UUID id = UUID.fromString(entry.getKey());
                    for (JsonElement owed : entry.getValue().getAsJsonArray()) {
                        JsonObject o = owed.getAsJsonObject();
                        PENDING.computeIfAbsent(id, k -> new ArrayList<>()).add(new int[] {o.get("amount").getAsInt()});
                        PENDING_TEMPLATES.computeIfAbsent(id, k -> new ArrayList<>()).add(Identifier.parse(o.get("template").getAsString()));
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't read contracts {}", file, e);
        }
    }

    private static void write() {
        if (server == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("day", day);
        JsonArray contracts = new JsonArray();
        for (Active active : TODAY) {
            JsonObject c = new JsonObject();
            c.addProperty("template", active.template.toString());
            c.addProperty("amount", active.amount);
            c.addProperty("progress", active.progress);
            c.addProperty("done", active.done);
            c.addProperty("rerolled", active.rerolled);
            JsonArray contributors = new JsonArray();
            active.contributors.forEach(id -> contributors.add(id.toString()));
            c.add("contributors", contributors);
            contracts.add(c);
        }
        json.add("contracts", contracts);
        JsonObject pending = new JsonObject();
        PENDING_TEMPLATES.forEach((id, owed) -> {
            JsonArray list = new JsonArray();
            for (int i = 0; i < owed.size(); i++) {
                JsonObject o = new JsonObject();
                o.addProperty("template", owed.get(i).toString());
                o.addProperty("amount", PENDING.get(id).get(i)[0]);
                list.add(o);
            }
            pending.add(id.toString(), list);
        });
        json.add("pending", pending);
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't save contracts", e);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }

    /** For /contracts commands: today's contract names. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        TODAY.forEach(active -> names.add(template(active) == null ? active.template.toString() : template(active).name()));
        return names;
    }

    /** Forces new contracts now (admin). */
    public static void newDay() {
        day = -1;
        refreshDay();
    }

    /** The item id string of a deliver goal, for messages. */
    static String itemName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
