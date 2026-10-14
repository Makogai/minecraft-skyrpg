package com.skyrpg.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /r} and {@code /reply} answer your last whisper; {@code /emotes} lists the emotes (click one to put it in
 * the chat box); {@code /chat level [player]} explains a SkyRPG Level; admins give chat titles with
 * {@code /chat title <player> <colour> <text>} (a colour name like {@code gold}, or hex like {@code F2B33D}) and take
 * them away with {@code /chat title <player> clear}.
 */
final class ChatCommands {
    private static final int EMOTES_PER_ROW = 4;

    private ChatCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> reply = dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> reply(context, StringArgumentType.getString(context, "message")))));
        dispatcher.register(Commands.literal("reply").redirect(reply));

        dispatcher.register(Commands.literal("emotes").executes(ChatCommands::listEmotes));

        dispatcher.register(Commands.literal("chat")
                .then(Commands.literal("level")
                        .executes(context -> level(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> level(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("title")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("clear").executes(context -> clearTitle(context, EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> setTitle(context, EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "color"), StringArgumentType.getString(context, "text"))))))));
    }

    private static int reply(CommandContext<CommandSourceStack> context, String message) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID partner = Whispers.partner(player);
        if (partner == null) {
            context.getSource().sendFailure(Component.literal("Nobody to reply to yet."));
            return 0;
        }
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(partner);
        if (target == null) {
            context.getSource().sendFailure(Component.literal("They're not online any more."));
            return 0;
        }
        return Whispers.send(context.getSource(), List.of(target), message) ? 1 : 0;
    }

    private static int listEmotes(CommandContext<CommandSourceStack> context) {
        Map<String, List<Emotes.Emote>> groups = new LinkedHashMap<>();
        for (Emotes.Emote emote : Emotes.all()) {
            groups.computeIfAbsent(emote.group(), group -> new ArrayList<>()).add(emote);
        }
        MutableComponent out = Component.literal("✦ Emotes").withColor(ChatColors.ACCENT)
                .append(Component.literal("  type the code in chat, or click one").withColor(ChatColors.DIM));
        for (Map.Entry<String, List<Emotes.Emote>> group : groups.entrySet()) {
            out.append(Component.literal("\n" + group.getKey()).withColor(ChatColors.MUTED));
            List<Emotes.Emote> list = group.getValue();
            for (int i = 0; i < list.size(); i++) {
                out.append(Component.literal(i % EMOTES_PER_ROW == 0 ? "\n  " : "   "));
                Emotes.Emote emote = list.get(i);
                MutableComponent shown = emote.icon() != null ? ChatIcons.icon(emote.icon()) : Component.literal(emote.text());
                out.append(shown.withStyle(style -> (emote.icon() != null ? style : style.withColor(emote.color()))
                                .withClickEvent(new ClickEvent.SuggestCommand(emote.code() + " "))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to use " + emote.code()).withColor(ChatColors.MUTED)))))
                        .append(Component.literal(" " + emote.code()).withStyle(style -> style.withColor(ChatColors.DIM)
                                .withClickEvent(new ClickEvent.SuggestCommand(emote.code() + " "))));
            }
        }
        out.append(Component.literal("\nShowcase: ").withColor(ChatColors.MUTED))
                .append(Component.literal(String.join(" ", List.of("[item]", "[offhand]", "[armor]", "[pet]"))).withColor(ChatColors.TEXT));
        context.getSource().sendSystemMessage(out);
        return Emotes.all().size();
    }

    private static int level(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        int skills = Profiles.skillTotal(player);
        int slayers = Profiles.slayerTotal(player);
        int level = skills + Profiles.SLAYER_WEIGHT * slayers;
        context.getSource().sendSystemMessage(Component.empty()
                .append(Profiles.badge(level)).append(Component.literal(" "))
                .append(Profiles.name(player, level))
                .append(Component.literal(": SkyRPG Level " + level + " = " + skills + " skill levels + " + slayers + " slayer levels × "
                        + Profiles.SLAYER_WEIGHT).withColor(ChatColors.MUTED)));
        return level;
    }

    private static int setTitle(CommandContext<CommandSourceStack> context, ServerPlayer player, String colorName, String text) {
        TextColor color = TextColor.parseColor(colorName).result()
                .or(() -> TextColor.parseColor("#" + colorName).result())
                .orElse(null);
        if (color == null) {
            context.getSource().sendFailure(Component.literal("Unknown colour \"" + colorName + "\". Use a name like gold, or hex like F2B33D."));
            return 0;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > Titles.MAX_LENGTH) {
            context.getSource().sendFailure(Component.literal("Titles are 1 to " + Titles.MAX_LENGTH + " characters."));
            return 0;
        }
        Titles.set(player, new Titles.Title(trimmed, color.getValue()));
        context.getSource().sendSuccess(() -> Component.literal("Chat title set: ").withColor(ChatColors.MUTED)
                .append(Profiles.titlePrefix(player)).append(Profiles.name(player)), true);
        return 1;
    }

    private static int clearTitle(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        Titles.set(player, null);
        context.getSource().sendSuccess(() -> Component.literal("Chat title cleared for ").withColor(ChatColors.MUTED)
                .append(Profiles.name(player)), true);
        return 1;
    }
}
