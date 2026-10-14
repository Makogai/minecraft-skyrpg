package com.skyrpg.pets;

import com.skyrpg.core.text.Notice;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pets: five kinds, one per skill, that level up with that skill and give stats and perks. */
public class SkyRpgPets implements ModInitializer {
    public static final String MOD_ID = "skyrpg_pets";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PetItems.init();
        Pets.init();
        PetPayloads.register();
        // /pet: what your pet is; /pet away: unequip it into storage; /pet storage: list; /pet give (op): test pets.
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("pet")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack pet = Pets.active(player);
                    if (pet.isEmpty()) {
                        Notice.send(player, Notice.Kind.PET, Pets.grey("You have no pet out. Right-click a pet, or open the Pets page in the K menu."));
                    } else {
                        Pets.refreshLook(pet);
                        Pets.notice(Pets.rarity(pet)).tag("YOUR PET").send(player, Pets.nameAndLevel(pet));
                        var lore = pet.get(DataComponents.LORE);
                        if (lore != null && lore.lines().size() > 2) {
                            // the lore without its first line (the skill) and last line (the right-click hint)
                            lore.lines().subList(1, lore.lines().size() - 1).forEach(line -> player.sendSystemMessage(Component.literal("    ").append(line)));
                        }
                    }
                    return 1;
                })
                .then(Commands.literal("away").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return Pets.unequip(player) ? 1 : 0;
                }))
                .then(Commands.literal("storage").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    java.util.List<ItemStack> stored = Pets.storage(player);
                    Notice.send(player, Notice.Kind.PET, Pets.grey("Pet storage " + stored.size() + " / " + Pets.maxStorage()
                            + (stored.isEmpty() ? ". Right-click a pet item to store it." : ":")));
                    for (int i = 0; i < stored.size(); i++) {
                        player.sendSystemMessage(Component.literal("  " + (i + 1) + ". ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                                .append(Pets.nameAndLevel(stored.get(i))));
                    }
                    return stored.size();
                }))
                .then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(PetType.values()).map(PetType::id), b))
                                        .then(Commands.argument("rarity", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(PetRarity.values()).map(r -> r.name().toLowerCase(java.util.Locale.ROOT)), b))
                                                .executes(ctx -> {
                                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                                    String typeName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type");
                                                    PetType type = java.util.Arrays.stream(PetType.values()).filter(t -> t.id().equalsIgnoreCase(typeName)).findFirst().orElse(null);
                                                    PetRarity rarity = PetRarity.parse(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rarity"));
                                                    if (type == null) {
                                                        ctx.getSource().sendFailure(Component.literal("Unknown pet type: " + typeName));
                                                        return 0;
                                                    }
                                                    if (!Pets.store(target, Pets.create(type, rarity), true)) {
                                                        ctx.getSource().sendFailure(Component.literal(target.getName().getString() + "'s pet storage is full."));
                                                        return 0;
                                                    }
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Gave " + rarity.label() + " " + type.displayName()
                                                            + " to " + target.getName().getString()), true);
                                                    return 1;
                                                })))))));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
