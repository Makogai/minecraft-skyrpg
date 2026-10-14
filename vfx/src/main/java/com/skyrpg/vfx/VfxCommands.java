package com.skyrpg.vfx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.skyrpg.vfx.data.BindingDef;
import com.skyrpg.vfx.data.EffectDef;
import com.skyrpg.vfx.data.VfxLibrary;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ops only:
 * <ul>
 *   <li>{@code /vfx play <id>}: on yourself (or at the command's position from a command block);</li>
 *   <li>{@code /vfx play <id> <pos>} or {@code /vfx play <id> <entities>};</li>
 *   <li>{@code /vfx list [bindings]}: loaded effects (click one to play it on yourself) or bindings;</li>
 *   <li>{@code /vfx shake <strength> <ticks>} and {@code /vfx flash <argb int> <ticks>}: test the screen shake and flash on yourself;</li>
 *   <li>{@code /vfx reload}: re-reads just the VFX files and sends them to everyone, with any problems.</li>
 * </ul>
 */
final class VfxCommands {
    private static final int MAX_PROBLEMS_SHOWN = 8;

    private VfxCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vfx")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("play")
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(VfxServer.library().effects().keySet(), builder))
                                .executes(VfxCommands::playHere)
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> playAt(ctx, Vec3Argument.getVec3(ctx, "pos"))))
                                .then(Commands.argument("entities", EntityArgument.entities())
                                        .executes(ctx -> playOn(ctx, EntityArgument.getEntities(ctx, "entities"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> listEffects(ctx.getSource()))
                        .then(Commands.literal("effects").executes(ctx -> listEffects(ctx.getSource())))
                        .then(Commands.literal("bindings").executes(ctx -> listBindings(ctx.getSource()))))
                .then(Commands.literal("shake")
                        .then(Commands.argument("strength", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0, 5))
                                .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> {
                                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            Vfx.shake(player, com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "strength"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"));
                                            return 1;
                                        }))))
                .then(Commands.literal("flash")
                        .then(Commands.argument("argb", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> {
                                            Vfx.flash(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "argb"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"));
                                            return 1;
                                        }))))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
    }

    private static Identifier effect(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if (VfxServer.library().effect(id) != null) {
            return id;
        }
        ctx.getSource().sendFailure(Component.literal("Unknown effect " + id + ". See /vfx list."));
        return null;
    }

    private static int playHere(CommandContext<CommandSourceStack> ctx) {
        Entity entity = ctx.getSource().getEntity();
        return entity != null ? playOn(ctx, List.of(entity)) : playAt(ctx, ctx.getSource().getPosition());
    }

    private static int playAt(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        Identifier id = effect(ctx);
        if (id == null) {
            return 0;
        }
        Vfx.play(ctx.getSource().getLevel(), id, pos);
        ctx.getSource().sendSuccess(() -> Component.literal("Played " + id + " at " + String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z)), false);
        return 1;
    }

    private static int playOn(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        Identifier id = effect(ctx);
        if (id == null) {
            return 0;
        }
        for (Entity entity : entities) {
            Vfx.play(entity, id);
        }
        int count = entities.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Played " + id + " on " + count + " entit" + (count == 1 ? "y" : "ies")), false);
        return count;
    }

    private static int listEffects(CommandSourceStack source) {
        VfxLibrary library = VfxServer.library();
        MutableComponent text = Component.literal("VFX effects (" + library.effects().size() + "), click to play on yourself:")
                .withStyle(ChatFormatting.GOLD);
        for (EffectDef effect : library.effects().values()) {
            String command = "/vfx play " + effect.id();
            MutableComponent hover = Component.literal(effect.id().toString()).withStyle(ChatFormatting.YELLOW);
            if (!effect.description().isEmpty()) {
                hover.append(Component.literal("\n" + effect.description()).withStyle(ChatFormatting.GRAY));
            }
            hover.append(Component.literal("\n" + effect.emitters().length + " emitter(s), " + effect.length() + " tick(s)").withStyle(ChatFormatting.DARK_GRAY));
            text.append(Component.literal("\n "))
                    .append(Component.literal(effect.id().toString()).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.RunCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(hover))));
        }
        text.append(Component.literal("\n" + library.bindings().size() + " bindings: /vfx list bindings").withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> text, false);
        return library.effects().size();
    }

    private static int listBindings(CommandSourceStack source) {
        VfxLibrary library = VfxServer.library();
        MutableComponent text = Component.literal("VFX bindings (" + library.bindings().size() + "):").withStyle(ChatFormatting.GOLD);
        for (BindingDef binding : library.bindings().values()) {
            text.append(Component.literal("\n " + binding.id).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" -> " + binding.effectId + " (" + binding.describe() + ", " + binding.rate + "/tick)")
                            .withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return library.bindings().size();
    }

    private static int reload(CommandSourceStack source) {
        VfxLibrary library = VfxServer.reload(source.getServer());
        source.sendSuccess(() -> Component.literal("Reloaded " + library.effects().size() + " VFX effects and " + library.bindings().size()
                + " bindings" + (library.problems().isEmpty() ? "" : " (" + library.errors() + " errors, " + library.problems().size() + " problems)"))
                .withStyle(library.errors() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        int shown = 0;
        for (String problem : library.problems()) {
            if (shown++ >= MAX_PROBLEMS_SHOWN) {
                source.sendSystemMessage(Component.literal(" ...and " + (library.problems().size() - MAX_PROBLEMS_SHOWN) + " more in the log")
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            source.sendSystemMessage(Component.literal(" " + problem).withStyle(ChatFormatting.YELLOW));
        }
        return library.effects().size();
    }
}
