package com.skyrpg.progress.titles;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.event.BossKills;
import com.skyrpg.core.event.TitleFlags;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.text.PlayerTitles;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.collections.SkyRpgLevel;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;

/**
 * Earnable titles (docs/design/titles.md): a data-driven catalogue ({@link TitleData}), unlocks checked from a player's state
 * every two seconds (so they are retroactive) plus a few hooks for what state can't show (boss kills, Blood Moon, flags other
 * mods grant), one equipped title per player, the {@code /title} command, and a notice with a sound when one is earned.
 * Showing the title is the chat mod's job: it reads {@link PlayerTitles}.
 */
public final class TitleSystem {
    private static final int CHECK_TICKS = 40;
    private static MinecraftServer server;

    private TitleSystem() {
    }

    public static void init() {
        TitlePayloads.register();
        TitleState.touch();
        JsonDataLoader.register(SkyRpgProgress.id("titles"), TitleData.DIRECTORY, files -> {
            TitleData.load(files);
            if (server != null) {
                server.execute(() -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(player, new TitlePayloads.Defs(TitleData.json()));
                        evaluate(player);
                    }
                });
            }
        });
        PlayerTitles.install(player -> {
            TitleState state = TitleState.of(player);
            TitleData.Def def = state.active().isEmpty() || !state.unlocked().contains(state.active()) ? null : TitleData.get(state.active());
            return def == null ? null : new PlayerTitles.Shown(def.text(), def.icon(), def.color());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> joined.execute(() -> {
            ServerPlayer player = handler.getPlayer();
            ServerPlayNetworking.send(player, new TitlePayloads.Defs(TitleData.json()));
            seedBossKills(player);
            evaluate(player);
        }));
        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.Select.TYPE, (payload, context) ->
                context.server().execute(() -> pick(context.player(), payload.id(), false)));
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            for (ServerPlayer player : ticking.getPlayerList().getPlayers()) {
                if ((ticking.getTickCount() + player.getId()) % CHECK_TICKS == 0) {
                    if (BloodMoon.active() && player.level().dimension().identifier().getPath().equals("overworld") && player.isAlive()) {
                        bump(player, "bloodmoon_seconds", CHECK_TICKS / 20);
                    }
                    evaluate(player);
                }
            }
        });
        BossKills.KILLED.register((player, boss, level, kind) -> {
            TitleState state = TitleState.of(player);
            String bossKey = "boss:" + boss;
            String kindKey = "kind:" + kind.name();
            player.setAttached(TitleState.STATE, state.withCounter(bossKey, state.counter(bossKey) + 1).withCounter(kindKey, state.counter(kindKey) + 1));
            evaluate(player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (BloodMoon.active() && entity instanceof Mob && source.getEntity() instanceof ServerPlayer killer) {
                bump(killer, "bloodmoon_kills", 1);
            }
        });
        TitleFlags.GRANTED.register((player, flag) -> {
            TitleState state = TitleState.of(player);
            if (!state.flags().contains(flag)) {
                player.setAttached(TitleState.STATE, state.withFlag(flag));
                evaluate(player);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(command()));
    }

    private static void bump(ServerPlayer player, String key, int amount) {
        TitleState state = TitleState.of(player);
        player.setAttached(TitleState.STATE, state.withCounter(key, state.counter(key) + amount));
    }

    /** Bosses the SkyRPG Level already counted a first kill for (before titles existed) count as one kill each. */
    private static void seedBossKills(ServerPlayer player) {
        TitleState state = TitleState.of(player);
        TitleState next = state;
        for (String boss : SkyRpgLevel.firstKills(player)) {
            if (next.counter("boss:" + boss) == 0) {
                next = next.withCounter("boss:" + boss, 1);
            }
        }
        if (next != state) {
            player.setAttached(TitleState.STATE, next);
        }
    }

    // ------------------------------------------------------------------ unlocking

    /** Checks every locked title against the player's state; unlocks what is met and refreshes the progress shown on the K page. */
    public static void evaluate(ServerPlayer player) {
        Map<?, TitleData.Def> defs = TitleData.all();
        if (defs.isEmpty()) {
            return;
        }
        TitleState state = TitleState.of(player);
        Set<String> earned = new LinkedHashSet<>();
        Map<String, Integer> live = new HashMap<>();
        for (TitleData.Def def : defs.values()) {
            String id = def.id().toString();
            if (state.unlocked().contains(id)) {
                continue;
            }
            TitleConditions.Progress progress;
            try {
                progress = TitleConditions.check(player, def.condition(), state);
            } catch (RuntimeException e) {
                continue;
            }
            if (progress.done()) {
                earned.add(id);
            } else if (progress.max() > 1) {
                live.put(id, progress.current());
                live.put(id + "#", progress.max());
            }
        }
        if (!live.equals(player.getAttachedOrElse(TitleState.LIVE, Map.of()))) {
            player.setAttached(TitleState.LIVE, live);
        }
        boolean stale = !state.active().isEmpty() && !state.unlocked().contains(state.active());
        if (earned.isEmpty() && !stale) {
            return;
        }
        TitleState next = state.withUnlocked(earned);
        if (stale) {
            next = next.withActive("");
        }
        boolean equipped = false;
        if (next.active().isEmpty() && !earned.isEmpty()) {
            next = next.withActive(earned.iterator().next());
            equipped = true;
        }
        player.setAttached(TitleState.STATE, next);
        announce(player, earned, equipped);
    }

    private static void announce(ServerPlayer player, Set<String> earned, boolean firstEquipped) {
        if (earned.isEmpty()) {
            return;
        }
        List<TitleData.Def> list = earned.stream().map(TitleData::get).filter(java.util.Objects::nonNull).toList();
        if (list.size() > 3) {
            Notice.of(Notice.Kind.SUCCESS).icon(Notice.TROPHY).tag("TITLES UNLOCKED").color(0xF2B33D).send(player,
                    Msg.text("You earned " + list.size() + " titles ", ChatFormatting.GRAY)
                            .append(Msg.text("[Open]", ChatFormatting.AQUA).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/title"))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("List your titles"))))));
        } else {
            for (TitleData.Def def : list) {
                String path = def.id().toString();
                MutableComponent button = Msg.text(firstEquipped && def == list.get(0) ? "equipped" : "[Equip]",
                        firstEquipped && def == list.get(0) ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA);
                if (!(firstEquipped && def == list.get(0))) {
                    button.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/title set " + path))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Wear this title"))));
                }
                Notice.of(Notice.Kind.SUCCESS).icon(PlayerTitles.isGlyph(def.icon()) ? def.icon() : Notice.TROPHY).tag("TITLE UNLOCKED").color(def.color())
                        .hover(Component.literal(def.description())).send(player,
                                tag(def).append(Msg.text("  " + def.description() + "  ", ChatFormatting.GRAY)).append(button));
            }
        }
        TitleData.Def top = list.isEmpty() ? null : list.get(0);
        if (top != null) {
            player.sendOverlayMessage(Component.literal("Title unlocked: ").withStyle(ChatFormatting.GRAY).append(tag(top)));
        }
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
    }

    /** The title as shown next to a name: icon, then text, in the title's colour. */
    static MutableComponent tag(TitleData.Def def) {
        MutableComponent out = Component.empty();
        if (!def.icon().isEmpty()) {
            out.append(PlayerTitles.iconComponent(def.icon(), def.color())).append(" ");
        }
        return out.append(Component.literal(def.text()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(def.color())).withBold(true)));
    }

    // ------------------------------------------------------------------ choosing

    /** Equips a title (an empty id takes it off). {@code verbose} says why a pick failed. */
    static boolean pick(ServerPlayer player, String text, boolean verbose) {
        TitleState state = TitleState.of(player);
        if (text.isEmpty()) {
            player.setAttached(TitleState.STATE, state.withActive(""));
            return true;
        }
        TitleData.Def def = TitleData.find(text);
        if (def == null || !state.unlocked().contains(def.id().toString())) {
            if (verbose) {
                say(player, def == null ? "There is no title called " + text + "." : "You haven't earned " + def.text() + " yet: " + def.description());
            }
            return false;
        }
        player.setAttached(TitleState.STATE, state.withActive(def.id().toString()));
        return true;
    }

    private static void say(ServerPlayer player, String text) {
        Notice.send(player, Notice.Kind.INFO, text);
    }

    // ------------------------------------------------------------------ commands

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command() {
        var root = Commands.literal("title").executes(ctx -> list(ctx.getSource().getPlayerOrException(), false));
        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource().getPlayerOrException(), false)));
        root.then(Commands.literal("locked").executes(ctx -> list(ctx.getSource().getPlayerOrException(), true)));
        root.then(Commands.literal("clear").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            pick(player, "", true);
            say(player, "Title removed.");
            return 1;
        }));
        root.then(Commands.literal("set").then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TitleState state = TitleState.of(player);
                    return SharedSuggestionProvider.suggest(TitleData.all().values().stream()
                            .filter(def -> state.unlocked().contains(def.id().toString())).map(def -> def.id().getPath()), builder);
                })
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!pick(player, StringArgumentType.getString(ctx, "id"), true)) {
                        return 0;
                    }
                    TitleData.Def def = TitleData.find(StringArgumentType.getString(ctx, "id"));
                    Notice.of(Notice.Kind.SUCCESS).tag("TITLE").send(player, Msg.text("You now wear ", ChatFormatting.GRAY).append(tag(def)));
                    return 1;
                })));
        root.then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), TitleData.all().values().stream().map(def -> def.id().getPath())), builder))
                        .executes(ctx -> adminChange(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "id"), true)))));
        root.then(Commands.literal("revoke").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), TitleData.all().values().stream().map(def -> def.id().getPath())), builder))
                        .executes(ctx -> adminChange(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "id"), false)))));
        return root;
    }

    /** {@code /title grant|revoke <player> <id|all>}: for admins and tests. A granted title stays unlocked; a revoked one can be earned again. */
    private static int adminChange(CommandSourceStack source, ServerPlayer player, String text, boolean grant) {
        Set<String> ids = new LinkedHashSet<>();
        if (text.equals("all")) {
            TitleData.all().keySet().forEach(id -> ids.add(id.toString()));
        } else {
            TitleData.Def def = TitleData.find(text);
            if (def == null) {
                source.sendFailure(Component.literal("No such title: " + text));
                return 0;
            }
            ids.add(def.id().toString());
        }
        TitleState state = TitleState.of(player);
        if (grant) {
            Set<String> fresh = new LinkedHashSet<>(ids);
            fresh.removeAll(state.unlocked());
            player.setAttached(TitleState.STATE, state.withUnlocked(fresh));
            announce(player, fresh, false);
        } else {
            player.setAttached(TitleState.STATE, state.without(ids));
        }
        source.sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + ids.size() + " title(s) "
                + (grant ? "to " : "from ") + player.getGameProfile().name()), true);
        return 1;
    }

    private static int list(ServerPlayer player, boolean locked) {
        TitleState state = TitleState.of(player);
        int total = TitleData.all().size();
        int have = (int) TitleData.all().keySet().stream().filter(id -> state.unlocked().contains(id.toString())).count();
        Notice.of(Notice.Kind.SUCCESS).icon(Notice.TROPHY).tag("TITLES").color(0xF2B33D).send(player,
                Msg.text(have + " of " + total + " earned. ", ChatFormatting.GRAY)
                        .append(Msg.text("Open the K menu, Titles page, or use /title set <id>.", ChatFormatting.DARK_GRAY)));
        for (TitleData.Def def : TitleData.all().values()) {
            String id = def.id().toString();
            boolean owned = state.unlocked().contains(id);
            if (owned == locked) {
                continue;
            }
            MutableComponent line = Component.literal("  ").append(tag(def));
            if (owned) {
                boolean active = id.equals(state.active());
                line.append(Msg.text("  " + def.description() + "  ", ChatFormatting.GRAY));
                line.append(active ? Msg.text("[worn]", ChatFormatting.GREEN)
                        : Msg.text("[Equip]", ChatFormatting.AQUA).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/title set " + def.id().getPath()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Wear this title")))));
            } else {
                line.append(Msg.text("  " + (def.hidden() ? "???" : def.description()), ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }
        if (!locked && have < total) {
            player.sendSystemMessage(Msg.text("  " + (total - have) + " still locked: /title locked", ChatFormatting.DARK_GRAY));
        }
        return 1;
    }
}
