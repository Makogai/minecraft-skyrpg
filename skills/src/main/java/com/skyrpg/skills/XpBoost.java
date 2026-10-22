package com.skyrpg.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Skill XP boosts for everyone: automatic 2x weekends (Saturday and Sunday, server time) and boosts the console starts
 * with {@code /xpboost <multiplier> <duration>}. The biggest active one counts. A boss bar shows it while it runs, and
 * chat says when one starts and ends. It multiplies all skill XP, after Insight.
 */
public final class XpBoost {
    public static final double WEEKEND_MULTIPLIER = 2;

    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("multiplier", 1.0).forGetter(d -> d.multiplier),
                Codec.LONG.optionalFieldOf("until", 0L).forGetter(d -> d.until),
                Codec.BOOL.optionalFieldOf("weekends", true).forGetter(d -> d.weekends)).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(SkyRpgSkills.id("xp_boost"), Data::new, CODEC, null);

        /** A boost started by command, and when it ends (epoch millis). */
        double multiplier = 1;
        long until;
        boolean weekends = true;

        Data() {
        }

        Data(double multiplier, long until, boolean weekends) {
            this.multiplier = multiplier;
            this.until = until;
            this.weekends = weekends;
        }
    }

    private static final ServerBossEvent BAR = new ServerBossEvent(UUID.randomUUID(), Component.empty(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private static MinecraftServer server;
    /** The multiplier last tick, to announce changes. */
    private static double shown = 1;

    private XpBoost() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            server = s;
            if (s.getTickCount() % 20 == 0) {
                update(s);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            if (shown > 1) {
                BAR.addPlayer(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> BAR.removePlayer(handler.player));
    }

    /** The XP multiplier right now (1 when nothing runs). */
    public static double multiplier() {
        if (server == null) {
            return 1;
        }
        Data data = data(server);
        double best = 1;
        if (data.until > System.currentTimeMillis()) {
            best = data.multiplier;
        }
        if (data.weekends && weekend(LocalDateTime.now())) {
            best = Math.max(best, WEEKEND_MULTIPLIER);
        }
        return best;
    }

    private static boolean weekend(LocalDateTime now) {
        return now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** When the current boost ends (epoch millis), taking the longer of the command boost and the weekend. */
    private static long endsAt(Data data, double multiplier) {
        long end = 0;
        if (data.until > System.currentTimeMillis() && data.multiplier >= multiplier) {
            end = data.until;
        }
        LocalDateTime now = LocalDateTime.now();
        if (data.weekends && weekend(now) && WEEKEND_MULTIPLIER >= multiplier) {
            LocalDate monday = now.toLocalDate().plusDays(now.getDayOfWeek() == DayOfWeek.SATURDAY ? 2 : 1);
            end = Math.max(end, monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return end;
    }

    private static void update(MinecraftServer s) {
        double now = multiplier();
        if (now > 1) {
            long left = Math.max(0, endsAt(data(s), now) - System.currentTimeMillis());
            BAR.setName(Component.literal("⚡ " + format(now) + " Skill XP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("  " + duration(left) + " left").withStyle(ChatFormatting.YELLOW)));
            BAR.setProgress((float) Math.min(1, left / (double) Duration.ofDays(2).toMillis()));
            s.getPlayerList().getPlayers().forEach(BAR::addPlayer);
        } else {
            BAR.removeAllPlayers();
        }
        if (now != shown) {
            String text = now > 1 ? format(now) + " Skill XP is on! Every skill levels " + format(now) + " as fast." : "The Skill XP boost has ended.";
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                Notice.of(Notice.Kind.EVENT).tag("XP BOOST").send(player, Msg.text(text, now > 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
            shown = now;
        }
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xpboost")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("off").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    Data data = data(ctx.getSource().getServer());
                    data.until = 0;
                    data.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("Command boost off (weekends: " + (data.weekends ? "on" : "off") + ")"), true);
                    return 1;
                }))
                .then(Commands.literal("weekends").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            Data data = data(ctx.getSource().getServer());
                            data.weekends = BoolArgumentType.getBool(ctx, "on");
                            data.setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal("2x weekends " + (data.weekends ? "on" : "off")), true);
                            return 1;
                        })))
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(1, 10)).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("duration", StringArgumentType.word()).executes(ctx -> {
                            long millis = parseDuration(StringArgumentType.getString(ctx, "duration"));
                            if (millis <= 0) {
                                ctx.getSource().sendFailure(Component.literal("Duration like 30m, 3h or 2d"));
                                return 0;
                            }
                            Data data = data(ctx.getSource().getServer());
                            data.multiplier = DoubleArgumentType.getDouble(ctx, "multiplier");
                            data.until = System.currentTimeMillis() + millis;
                            data.setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal(format(data.multiplier) + " Skill XP for " + duration(millis)), true);
                            return 1;
                        }))));
    }

    private static int status(CommandSourceStack source) {
        double now = multiplier();
        Data data = data(source.getServer());
        String text = now > 1 ? format(now) + " Skill XP, " + duration(endsAt(data, now) - System.currentTimeMillis()) + " left" : "No XP boost right now";
        source.sendSuccess(() -> Component.literal(text + " (2x weekends: " + (data.weekends ? "on" : "off") + ")"), false);
        return 1;
    }

    /** "30m", "3h", "2d" or "1h30m" to millis; 0 if it doesn't parse. */
    static long parseDuration(String text) {
        long total = 0;
        long number = -1;
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number = (number < 0 ? 0 : number * 10) + (c - '0');
            } else if (number >= 0 && (c == 'd' || c == 'h' || c == 'm')) {
                total += number * switch (c) {
                    case 'd' -> 86_400_000L;
                    case 'h' -> 3_600_000L;
                    default -> 60_000L;
                };
                number = -1;
            } else {
                return 0;
            }
        }
        return number >= 0 ? 0 : total;
    }

    private static String format(double multiplier) {
        return (multiplier == Math.floor(multiplier) ? String.valueOf((int) multiplier) : String.valueOf(multiplier)) + "x";
    }

    private static String duration(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }

    private static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }
}
