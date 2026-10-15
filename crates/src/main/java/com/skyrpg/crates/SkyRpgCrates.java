package com.skyrpg.crates;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.skyrpg.core.data.JsonDataLoader;
import java.util.Collection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SkyRPG Crates: crates that drop in the survival world, opened with a key on a CS2-style reel. */
public final class SkyRpgCrates implements ModInitializer {
    public static final String MOD_ID = "skyrpg_crates";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CrateItems.init();
        CrateDrops.init();
        CratePayloads.register();
        JsonDataLoader.register(id("crates"), Crates.DIRECTORY, Crates::load);
        JsonDataLoader.register(id("candy"), Candy.DIRECTORY, Candy::load);
        ServerPlayNetworking.registerGlobalReceiver(CratePayloads.Open.TYPE, (payload, context) -> Crates.open(context.player(), payload.crate()));
        ServerTickEvents.END_SERVER_TICK.register(Crates::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Crates.leaving(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register(CrateDrops::onDeath);
        ScytheAbilities.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(command()));
    }

    /** /crates give <players> <crate> <crates> <keys>, and /crates rig <players> <tier> (the next crate lands on it). Ops only. */
    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        var give = Commands.argument("keys", IntegerArgumentType.integer(0, 64))
                .executes(c -> give(EntityArgument.getPlayers(c, "targets"), IdentifierArgument.getId(c, "crate").toString(),
                        IntegerArgumentType.getInteger(c, "crates"), IntegerArgumentType.getInteger(c, "keys"), c.getSource()::sendSuccess));
        var rig = Commands.argument("tier", IntegerArgumentType.integer(0, 15)).executes(c -> {
            EntityArgument.getPlayers(c, "targets").forEach(p -> Crates.rig(p, IntegerArgumentType.getInteger(c, "tier")));
            return 1;
        });
        return Commands.literal("crates")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("crate", IdentifierArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(crateIds(), b))
                                .then(Commands.argument("crates", IntegerArgumentType.integer(0, 64)).then(give)))))
                .then(Commands.literal("rig").then(Commands.argument("targets", EntityArgument.players()).then(rig)));
    }

    private static Iterable<String> crateIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        Crates.all().forEach(crate -> ids.add(crate.id().toString()));
        return ids;
    }

    private static int give(Collection<ServerPlayer> players, String crateId, int crates, int keys,
                            java.util.function.BiConsumer<java.util.function.Supplier<Component>, Boolean> reply) {
        Crate crate = Crates.byId(crateId);
        if (crate == null) {
            reply.accept(() -> Component.literal("No such crate: " + crateId), false);
            return 0;
        }
        for (ServerPlayer player : players) {
            if (crates > 0) {
                player.getInventory().add(new ItemStack(crate.item(), crates));
            }
            if (keys > 0) {
                player.getInventory().add(new ItemStack(crate.key(), keys));
            }
        }
        reply.accept(() -> Component.literal("Gave " + crates + " " + crate.name() + " and " + keys + " keys to " + players.size() + " players"), true);
        return players.size();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
