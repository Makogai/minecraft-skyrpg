package com.skyrpg.combat.slayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.combat.MobLevels;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Slayer quests (see docs/design/slayers.md): pay the Slayer Master, kill that line's mobs to fill the quest bar, beat
 * the boss that rises for slayer XP and drops. Slayer levels give stats and unlock gear at the bench (they're "skills"
 * named {@code skyrpg_combat:slayer/<line>} to everything else). Lines are data:
 * {@code data/<ns>/skyrpg/slayers/*.json} (see {@link SlayerLine}); the boss fights live in {@link SlayerBosses}.
 */
public final class Slayers {
    public static final String DIRECTORY = "skyrpg/slayers";
    public static final AttachmentType<SlayerState> STATE = AttachmentRegistry.create(SkyRpgCombat.id("slayer"), builder -> builder
            .persistent(SlayerState.CODEC)
            .syncWith(SlayerState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());
    private static final Identifier GOLDEN_EGG = Identifier.fromNamespaceAndPath("skyrpg_pets", "golden_pet_egg");

    private static Map<String, SlayerLine> lines = Map.of();

    private Slayers() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Map.Entry<Integer, SlayerLine>> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                int order = json.has("order") ? json.get("order").getAsInt() : 100;
                loaded.add(Map.entry(order, SlayerLine.parse(id, json)));
            } catch (RuntimeException e) {
                SkyRpgCombat.LOGGER.warn("Skipping slayer line {}: {}", id, e.getMessage());
            }
        });
        loaded.sort(Comparator.comparingInt(Map.Entry::getKey));
        Map<String, SlayerLine> byId = new LinkedHashMap<>();
        for (Map.Entry<Integer, SlayerLine> entry : loaded) {
            SlayerLine line = entry.getValue();
            byId.put(line.id(), line);
            SkillLevels.nameExtra(skill(line.id()), line.name() + " Slayer");
        }
        lines = byId;
    }

    public static void init() {
        SlayerPayloads.register();
        SlayerPage.init();
        SlayerEntities.init();
        JsonDataLoader.register(SkyRpgCombat.id("slayers"), DIRECTORY, Slayers::load);
        SkillLevels.installExtra(SkyRpgCombat.MOD_ID, (player, skill) ->
                skill.getPath().startsWith("slayer/") ? level(player, skill.getPath().substring("slayer/".length())) : 0);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Slayers";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (SlayerLine line : lines.values()) {
                    int level = level(player, line.id());
                    if (level > 0) {
                        line.levelStat().forEach((stat, per) -> {
                            StatType type = StatTypes.get(stat);
                            if (type != null) {
                                out.add(type, per * level);
                            }
                        });
                    }
                }
            }
        });
        // "open_slayer": params {"tab": "spawners"} opens on the spawner tab.
        Actions.register("open_slayer", (player, params) ->
                open(player, params.has("tab") && params.get("tab").getAsString().equals("spawners") ? 1 : 0));
        ServerPlayNetworking.registerGlobalReceiver(SlayerPayloads.Start.TYPE, (payload, context) -> start(context.player(), payload.line(), payload.tier()));
        ServerPlayNetworking.registerGlobalReceiver(SlayerPayloads.Cancel.TYPE, (payload, context) -> cancel(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SlayerPayloads.Buy.TYPE, (payload, context) -> {
            Spawners.Result result = Spawners.buy(context.player(), payload.index(), payload.count());
            reply(context.player(), result.message(), result.ok());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                SlayerBosses.ownerDied(player);
                return;
            }
            if (!(entity instanceof Mob mob)) {
                return;
            }
            ServerPlayer killer = source.getEntity() instanceof ServerPlayer direct ? direct
                    : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (killer != null) {
                killed(killer, mob);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendStatus(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(Slayers::tick);
        SlayerBosses.init();
        SlayerGear.init();
        Spawners.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("slayer")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException(), 0);
                    return 1;
                })
                .then(Commands.literal("spawners").executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException(), 1);
                    return 1;
                }))
                // /slayer xp <players> <line> <amount>
                .then(Commands.literal("xp").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("line", StringArgumentType.word()).then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                .executes(ctx -> {
                                    String line = StringArgumentType.getString(ctx, "line");
                                    if (!lines.containsKey(line)) {
                                        ctx.getSource().sendFailure(Component.literal("Lines: " + String.join(", ", lines.keySet())));
                                        return 0;
                                    }
                                    double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                        addXp(player, lines.get(line), amount);
                                    }
                                    return done(ctx.getSource(), "Gave " + amount + " " + line + " slayer XP");
                                })))))
                // /slayer start <players> <line> <tier>: start a quest for free (testing, events)
                .then(Commands.literal("start").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("line", StringArgumentType.word()).then(Commands.argument("tier", IntegerArgumentType.integer(1, 9))
                                .executes(ctx -> {
                                    String line = StringArgumentType.getString(ctx, "line");
                                    int tier = IntegerArgumentType.getInteger(ctx, "tier");
                                    if (!lines.containsKey(line) || lines.get(line).tier(tier) == null) {
                                        ctx.getSource().sendFailure(Component.literal("Lines: " + String.join(", ", lines.keySet())));
                                        return 0;
                                    }
                                    int count = 0;
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                        if (quest(player) == null) {
                                            setQuest(player, new SlayerState.Quest(line, tier, 0, false));
                                            sendStatus(player);
                                            count++;
                                        }
                                    }
                                    return done(ctx.getSource(), "Started " + count + " quest(s)");
                                })))))
                // /slayer fill <players>: fill the quest bar, so the boss rises
                .then(Commands.literal("fill").then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    int count = 0;
                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                        count += fill(player) ? 1 : 0;
                    }
                    return done(ctx.getSource(), "Filled " + count + " quest bar(s)");
                })))
                // /slayer clear <players>: end their quest, no refund
                .then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                        endQuest(player);
                    }
                    return done(ctx.getSource(), "Cleared");
                })))));
    }

    private static int done(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    // ---------------------------------------------------------------- state

    public static Map<String, SlayerLine> lines() {
        return lines;
    }

    public static SlayerLine line(String id) {
        return lines.get(id);
    }

    public static Identifier skill(String line) {
        return SkyRpgCombat.id("slayer/" + line);
    }

    public static SlayerState state(net.minecraft.world.entity.player.Player player) {
        return player.getAttachedOrElse(STATE, SlayerState.EMPTY);
    }

    public static int level(net.minecraft.world.entity.player.Player player, String lineId) {
        SlayerLine line = lines.get(lineId);
        return line == null ? 0 : line.level(state(player).xp(lineId));
    }

    static SlayerState.Quest quest(ServerPlayer player) {
        return state(player).quest().orElse(null);
    }

    static void setQuest(ServerPlayer player, SlayerState.Quest quest) {
        player.setAttached(STATE, state(player).withQuest(quest));
    }

    /** Ends the player's quest (no rewards, no refund) and takes down its boss. */
    static void endQuest(ServerPlayer player) {
        SlayerBosses.despawn(player);
        setQuest(player, null);
        sendStatus(player);
    }

    // ---------------------------------------------------------------- quests

    private static void start(ServerPlayer player, String lineId, int tierNumber) {
        SlayerLine line = lines.get(lineId);
        SlayerLine.Tier tier = line == null ? null : line.tier(tierNumber);
        if (tier == null) {
            reply(player, Component.literal("That quest doesn't exist."), false);
            return;
        }
        if (quest(player) != null) {
            reply(player, Component.literal("You're already on a quest. Finish or cancel it first."), false);
            return;
        }
        if (level(player, lineId) < tier.requires()) {
            reply(player, Component.literal("Needs " + line.name() + " Slayer " + tier.requires() + "."), false);
            return;
        }
        if (!Wallet.take(player, tier.fee())) {
            reply(player, Component.literal("Not enough coins: it costs " + Msg.number(tier.fee()) + "."), false);
            return;
        }
        setQuest(player, new SlayerState.Quest(lineId, tierNumber, 0, false));
        com.skyrpg.core.event.ProgressEvents.SLAYER_QUEST.invoker().onSlayer(player, lineId, tierNumber, false);
        Notice.of(Notice.Kind.SLAYER).tag("QUEST STARTED").send(player, Component.literal(line.name() + " " + Msg.roman(tierNumber)).withColor(line.color() & 0xFFFFFF)
                .append(Msg.text(": slay " + Msg.number(tier.xp()) + " worth of " + countsText(line)
                + " to summon it (tougher ones count more).", ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.WITHER_SPAWN, 0.4f, 1.6f);
        reply(player, Component.literal("Quest started: " + line.name() + " " + Msg.roman(tierNumber)), true);
        sendStatus(player);
    }

    private static void cancel(ServerPlayer player) {
        SlayerState.Quest quest = quest(player);
        if (quest == null) {
            reply(player, Component.literal("You're not on a quest."), false);
            return;
        }
        endQuest(player);
        Notice.send(player, Notice.Kind.SLAYER, Msg.text("Slayer quest cancelled.", ChatFormatting.GRAY));
        reply(player, Component.literal("Quest cancelled."), true);
    }

    /** A kill: fills the quest bar if it's the right kind of mob, and summons the boss when it's full. */
    private static void killed(ServerPlayer player, Mob mob) {
        SlayerState.Quest quest = quest(player);
        if (quest == null || quest.bossUp() || MobLevels.farmed(mob) || SlayerBosses.isSlayerMob(mob)) {
            return;
        }
        SlayerLine line = lines.get(quest.line());
        SlayerLine.Tier tier = line == null ? null : line.tier(quest.tier());
        if (tier == null || !line.counts(mob)) {
            return;
        }
        double progress = quest.progress() + 1 + MobLevels.level(mob) / 5;
        if (progress >= tier.xp()) {
            setQuest(player, new SlayerState.Quest(quest.line(), quest.tier(), tier.xp(), true));
            SlayerBosses.summon(player, line, quest.tier(), tier);
        } else {
            setQuest(player, new SlayerState.Quest(quest.line(), quest.tier(), progress, false));
        }
        sendStatus(player);
    }

    private static boolean fill(ServerPlayer player) {
        SlayerState.Quest quest = quest(player);
        SlayerLine line = quest == null ? null : lines.get(quest.line());
        SlayerLine.Tier tier = line == null ? null : line.tier(quest.tier());
        if (tier == null || quest.bossUp()) {
            return false;
        }
        setQuest(player, new SlayerState.Quest(quest.line(), quest.tier(), tier.xp(), true));
        SlayerBosses.summon(player, line, quest.tier(), tier);
        sendStatus(player);
        return true;
    }

    /** The boss fell: slayer XP, Combat XP, tokens and maybe a rare drop. */
    static void complete(ServerPlayer player, SlayerLine line, int tierNumber, SlayerLine.Tier tier, int ticks) {
        setQuest(player, null);
        com.skyrpg.core.event.ProgressEvents.SLAYER_QUEST.invoker().onSlayer(player, line.id(), tierNumber, true);
        RandomSource random = player.getRandom();
        double find = 1 + Math.max(0, Stats.get(player, StatTypes.MAGIC_FIND)) / 100;
        Notice.of(Notice.Kind.SLAYER).icon(Notice.TROPHY).tag("QUEST COMPLETE").color(0xFFAA00).send(player, Msg.text(line.name() + " " + Msg.roman(tierNumber) + " slain!", ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        double combatXp = tier.health() / 20;
        SkillLevels.giveXp(player, SkillLevels.COMBAT, combatXp);

        MutableComponent drops = Msg.text("Rewards: ", ChatFormatting.GRAY);
        boolean first = true;
        int tokens = tier.tokensMax() <= 0 ? 0 : tier.tokensMin() + random.nextInt(Math.max(1, tier.tokensMax() - tier.tokensMin() + 1));
        player.setAttached(STATE, state(player).withKill(line.id(), tierNumber, ticks, line.token() != Items.AIR ? tokens : 0));
        if (tokens > 0 && line.token() != Items.AIR) {
            give(player, new ItemStack(line.token(), tokens));
            drops.append(Msg.text(tokens + "x ", ChatFormatting.WHITE)).append(new ItemStack(line.token()).getHoverName().copy().withStyle(ChatFormatting.WHITE));
            first = false;
        }
        drops.append(Msg.text((first ? "" : ", ") + "+" + Msg.number(Math.round(combatXp)) + " Combat XP", ChatFormatting.DARK_AQUA));
        Notice.send(player, Notice.Kind.SLAYER, drops);
        addXp(player, line, tier.slayerXp() * (1 + SlayerGear.xpBonus(player, line.id())));
        if (line.rare() != Items.AIR && random.nextDouble() < tier.rareChance() * find) {
            rareDrop(player, line.rare(), ChatFormatting.LIGHT_PURPLE, false);
        }
        Item egg = BuiltInRegistries.ITEM.getOptional(GOLDEN_EGG).orElse(null);
        if (egg != null && random.nextDouble() < tier.eggChance() * find) {
            rareDrop(player, egg, ChatFormatting.GOLD, true);
        }
        sendStatus(player);
    }

    private static void rareDrop(ServerPlayer player, Item item, ChatFormatting color, boolean announce) {
        ItemStack stack = new ItemStack(item);
        give(player, stack.copy());
        int rgb = Notice.rgb(color);
        MutableComponent message = Notice.rareDrop(stack.getHoverName().copy().withStyle(color), rgb);
        if (announce) {
            MutableComponent all = Notice.rareDrop("RARE FIND!", player.getName().copy().withStyle(ChatFormatting.YELLOW)
                    .append(Msg.text(" found a ", ChatFormatting.GOLD)).append(stack.getHoverName().copy().withStyle(color))
                    .append(Msg.text(" from a slayer boss!", ChatFormatting.GOLD)), rgb);
            player.level().getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(all));
        } else {
            player.sendSystemMessage(message);
        }
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }

    private static void addXp(ServerPlayer player, SlayerLine line, double amount) {
        int before = line.level(state(player).xp(line.id()));
        player.setAttached(STATE, state(player).addXp(line.id(), amount));
        double xp = state(player).xp(line.id());
        int after = line.level(xp);
        int next = line.nextLevelXp(xp);
        Notice.send(player, Notice.Kind.SLAYER, Msg.text(line.name() + " Slayer: ", ChatFormatting.GRAY)
                .append(Msg.text("+" + Msg.number(Math.round(amount)) + " XP", ChatFormatting.LIGHT_PURPLE))
                .append(Msg.text(next < 0 ? "  (max level)" : "  (" + Msg.number(Math.round(xp)) + " / " + Msg.number(next) + " to level " + (after + 1) + ")",
                        ChatFormatting.DARK_GRAY)));
        for (int level = before + 1; level <= after; level++) {
            Notice.of(Notice.Kind.SLAYER).tag("LEVEL UP").color(0xB06AE8).send(player, Component.literal(line.name() + " Slayer " + level).withColor(line.color() & 0xFFFFFF)
                    .append(Msg.text("  ", ChatFormatting.GRAY))
                    .append(Msg.text("  " + perLevelText(line), ChatFormatting.GREEN)));
            if (level == 3 || level == 6) {
                Notice.send(player, Notice.Kind.SLAYER, Msg.text("New slayer gear is available at the Artisan Bench!", ChatFormatting.GOLD));
            }
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 0.8f);
            SkillLevels.LEVEL_UP.invoker().onLevelUp(player, skill(line.id()), level - 1, level);
        }
        Stats.refresh(player);
    }

    // ---------------------------------------------------------------- the screen and the HUD

    static void open(ServerPlayer player, int tab) {
        ServerPlayNetworking.send(player, view(player, Component.empty(), true, false, tab));
        Msg.playSound(player, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.9f);
    }

    static void reply(ServerPlayer player, Component notice, boolean good) {
        ServerPlayNetworking.send(player, view(player, notice, good, true, -1));
        Msg.playSound(player, good ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, 0.6f, good ? 1.2f : 1.0f);
    }

    private static SlayerPayloads.View view(ServerPlayer player, Component notice, boolean good, boolean refresh, int tab) {
        List<SlayerPayloads.LineView> views = new ArrayList<>();
        SlayerState state = state(player);
        for (SlayerLine line : lines.values()) {
            double xp = state.xp(line.id());
            int level = line.level(xp);
            List<SlayerPayloads.TierView> tiers = new ArrayList<>();
            for (int i = 0; i < line.tiers().size(); i++) {
                SlayerLine.Tier tier = line.tiers().get(i);
                SlayerAbilities.TierLore lore = SlayerAbilities.lore(line.kind(), i + 1);
                List<String> abilities = new ArrayList<>();
                if (lore != null) {
                    lore.adds().forEach(a -> abilities.add((a.attack() ? "A|" : "D|") + a.name() + "|" + a.description()));
                }
                tiers.add(new SlayerPayloads.TierView(tier.fee(), tier.xp(), (int) Math.round(tier.health()), tier.level(), tier.minutes(),
                        tier.slayerXp(), tier.requires(), level >= tier.requires(), lore == null ? "" : lore.title(), abilities));
            }
            views.add(new SlayerPayloads.LineView(line.id(), line.name(), line.color(), new ItemStack(line.icon()), level, (int) Math.floor(xp),
                    line.nextLevelXp(xp), countsText(line), perLevelText(line), tiers));
        }
        SlayerState.Quest quest = state.quest().orElse(null);
        return new SlayerPayloads.View(views, Spawners.views(player), quest == null ? "" : quest.line(), quest == null ? 0 : quest.tier(),
                notice, good, refresh, tab);
    }

    /** "zombies, husks and drowned", from the line's data ("counts_text"), or its boss's name. */
    private static String countsText(SlayerLine line) {
        return line.countsText();
    }

    /** What each level gives: "+3 Health". */
    private static String perLevelText(SlayerLine line) {
        List<String> parts = new ArrayList<>();
        line.levelStat().forEach((stat, per) -> {
            StatType type = StatTypes.get(stat);
            if (type != null) {
                parts.add("+" + Msg.number(per) + (type.percent() ? "% " : " ") + type.name());
            }
        });
        return String.join(", ", parts);
    }

    static void sendStatus(ServerPlayer player) {
        SlayerState.Quest quest = quest(player);
        SlayerLine line = quest == null ? null : lines.get(quest.line());
        SlayerLine.Tier tier = line == null ? null : line.tier(quest.tier());
        if (tier == null) {
            ServerPlayNetworking.send(player, SlayerPayloads.Status.NONE);
            return;
        }
        ServerPlayNetworking.send(player, new SlayerPayloads.Status(line.name(), line.color(), quest.tier(), (int) Math.floor(quest.progress()),
                tier.xp(), quest.bossUp(), SlayerBosses.secondsLeft(player)));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SlayerState.Quest quest = quest(player);
            if (quest != null) {
                if (!lines.containsKey(quest.line())) {
                    // Its line was taken out of the data.
                    setQuest(player, null);
                }
                sendStatus(player);
            }
        }
    }
}
