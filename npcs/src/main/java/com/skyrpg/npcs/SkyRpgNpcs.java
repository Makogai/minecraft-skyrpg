package com.skyrpg.npcs;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NPCs placed by data files: shopkeepers, the banker and the rest of the hub's residents. */
public class SkyRpgNpcs implements ModInitializer {
    public static final String MOD_ID = "skyrpg_npcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        JsonDataLoader.register(id("npcs"), "skyrpg/npcs", Npcs::load);
        NpcMapPayload.init();
        ServerLifecycleEvents.SERVER_STARTED.register(Npcs::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Npcs.stop());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Npcs.onChunkLoad(level, chunk));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Npcs.onEntityLoad(entity));
        ServerTickEvents.END_SERVER_TICK.register(Npcs::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> Npcs.interact(player, level, hand, entity));
        // NPCs can't be hurt anywhere (the hub already protects everything; this covers other worlds).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !entity.entityTags().contains(Npcs.TAG) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("npcs")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(ctx -> {
                    StringBuilder out = new StringBuilder(Npcs.definitions().size() + " NPCs:");
                    Npcs.definitions().values().forEach(def -> out.append("\n ").append(def.id()).append("  ").append(def.name())
                            .append(" (").append(def.role()).append(") ").append(def.dimension()).append(' ')
                            .append(Math.round(def.pos().x)).append(' ').append(Math.round(def.pos().y)).append(' ').append(Math.round(def.pos().z)));
                    ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
                    return Npcs.definitions().size();
                }))
                .then(Commands.literal("respawn").executes(ctx -> {
                    int count = Npcs.respawnAll();
                    ctx.getSource().sendSuccess(() -> Component.literal("Respawning " + count + " NPCs"), true);
                    return count;
                }))));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
