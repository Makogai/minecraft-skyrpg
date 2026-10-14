package com.skyrpg.mounts;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mounts: the griffin. Wild ones nest in the Shattered Highlands, feeding raw meat tames one into a Griffin Whistle, whistles
 * live in a stable on the player (K menu, Mounts), and a called griffin can be ridden and flown. See {@link GriffinEntity},
 * {@link Mounts} and docs/design/the_wild.md.
 */
public class SkyRpgMounts implements ModInitializer {
    public static final String MOD_ID = "skyrpg_mounts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MountBlocks.init();
        MountItems.init();
        MountEntities.init();
        Mounts.init();
        MountPayloads.register();
        Registry.register(BuiltInRegistries.FEATURE_TYPE, id("griffin_nest"), NestFeature.CODEC);
        // Nests in the mountain biomes; the feature itself only builds one inside a Shattered Highlands region (core's Regions lookup).
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.WINDSWEPT_HILLS,
                Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST), GenerationStep.Decoration.SURFACE_STRUCTURES,
                ResourceKey.<PlacedFeature>create(Registries.PLACED_FEATURE, id("griffin_nest")));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(commands()));
    }

    private static List<String> colors() {
        return Arrays.stream(GriffinColor.values()).map(GriffinColor::id).toList();
    }

    /**
     * /mounts: list your stable; /mounts call: call your active mount (or the first in the stable); /mounts away: put it away.
     * Ops: /mounts nest, /mounts griffin [color], /mounts storm, /mounts give &lt;player&gt; griffin [color].
     */
    private static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("mounts")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    List<ItemStack> stored = Mounts.storage(player);
                    ItemStack active = Mounts.active(player);
                    Mounts.say(player, "Stable " + stored.size() + " / " + Mounts.maxStorage() + (active.isEmpty() ? "" : "  (out: " + Mounts.name(active) + ")")
                            + (stored.isEmpty() && active.isEmpty() ? ". Tame a griffin: feed a wild one raw meat." : ":"));
                    for (int i = 0; i < stored.size(); i++) {
                        ItemStack mount = stored.get(i);
                        player.sendSystemMessage(Component.literal("  " + (i + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.literal(Mounts.name(mount)).withStyle(style -> style.withColor(Mounts.color(mount).nameRgb())))
                                .append(Component.literal(" " + Mounts.color(mount).rarity().label().toUpperCase(java.util.Locale.ROOT)).withStyle(style -> style.withColor(Mounts.color(mount).rarity().rgb()).withBold(true)))
                                .append(Component.literal("  Speed +" + Mounts.speedBonus(mount) + "%, Flight Stamina +" + Mounts.staminaBonus(mount) + "%")
                                        .withStyle(ChatFormatting.GRAY)));
                    }
                    return stored.size();
                })
                .then(Commands.literal("call").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!Mounts.active(player).isEmpty()) {
                        return Mounts.summon(player, -1) ? 1 : 0;
                    }
                    return Mounts.summon(player, 0) ? 1 : 0;
                }))
                .then(Commands.literal("away").executes(ctx -> Mounts.away(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
                .then(Commands.literal("nest").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.level();
                    BlockPos at = player.blockPosition();
                    int placed = NestFeature.build(level, at);
                    GriffinNestBlock.maintain(level, at);
                    ctx.getSource().sendSuccess(() -> Component.literal("Placed a griffin nest (" + placed + " blocks)"), true);
                    return placed;
                }))
                .then(Commands.literal("griffin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> spawnWild(ctx.getSource(), GriffinColor.random(ctx.getSource().getLevel().getRandom())))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(colors(), b))
                                .executes(ctx -> spawnWild(ctx.getSource(), GriffinColor.parse(StringArgumentType.getString(ctx, "color"))))))
                .then(Commands.literal("claim").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    // test helper: the same as right-clicking the whistle in your hand (into the stable, and called if none is out)
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack held = player.getMainHandItem();
                    if (held.is(MountItems.GRIFFIN_WHISTLE) && Mounts.claim(player, held)) {
                        held.shrink(1);
                        return 1;
                    }
                    return 0;
                }))
                .then(Commands.literal("tame").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    // test helper: tames the nearest wild griffin (within 16 blocks) as if it accepted the meat
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    GriffinEntity wild = player.level().getEntitiesOfClass(GriffinEntity.class, player.getBoundingBox().inflate(16),
                            g -> !g.isTame() && !g.isStorm()).stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                    if (wild == null) {
                        ctx.getSource().sendFailure(Component.literal("No wild griffin within 16 blocks"));
                        return 0;
                    }
                    Mounts.tame(player, wild);
                    return 1;
                }))
                .then(Commands.literal("storm").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerLevel level = source.getLevel();
                    GriffinEntity griffin = MountEntities.GRIFFIN.create(level, EntitySpawnReason.COMMAND);
                    griffin.snapTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, 0f, 0f);
                    griffin.addTag(GriffinEntity.STORM_TAG);
                    griffin.getAttribute(Attributes.SCALE).setBaseValue(2.5);
                    griffin.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
                    griffin.setHealth(400);
                    griffin.setPersistenceRequired();
                    level.addFreshEntity(griffin);
                    source.sendSuccess(() -> Component.literal("A Storm Griffin (test body: scale 2.5, tagged " + GriffinEntity.STORM_TAG + ")"), true);
                    return 1;
                }))
                .then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("griffin")
                                        .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(colors(), b))
                                                .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        GriffinColor.parse(StringArgumentType.getString(ctx, "color"))))))));
    }

    private static int spawnWild(CommandSourceStack source, GriffinColor color) {
        ServerLevel level = source.getLevel();
        GriffinEntity griffin = MountEntities.GRIFFIN.create(level, EntitySpawnReason.COMMAND);
        griffin.snapTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y, 0f);
        griffin.setColor(color);
        level.addFreshEntity(griffin);
        source.sendSuccess(() -> Component.literal("Spawned a wild " + color.label().toLowerCase() + " griffin (" + color.rarity().label().toLowerCase() + ")"), true);
        return 1;
    }

    private static int give(CommandSourceStack source, ServerPlayer target, GriffinColor color) {
        GriffinColor c = color != null ? color : GriffinColor.random(target.getRandom());
        ItemStack whistle = Mounts.create(c, target.getRandom(), target.getUUID(), target.getGameProfile().name());
        ItemStack shown = whistle.copy();
        if (!target.getInventory().add(whistle)) {
            target.spawnAtLocation(target.level(), whistle);
        }
        source.sendSuccess(() -> Component.literal("Gave " + target.getName().getString() + " a " + c.label().toLowerCase() + " Griffin Whistle (" + Mounts.name(shown) + ", " + c.rarity().label().toLowerCase() + ")"), true);
        return 1;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
