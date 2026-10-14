package com.skyrpg.core.event;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What other mods may know about the NPCs, without depending on the npcs mod: who they are and where they stand
 * ({@link #all()}), a hook that lets a mod take over a right-click ({@link #INTERACT}, the questline opens its
 * dialogues through it) and a way to run an NPC's normal action, such as opening its shop ({@link #runAction}).
 */
public final class NpcInteractions {
    /** One NPC's public data. {@code actionType} is a core {@code Actions} type, or empty when the NPC only talks. */
    public record Info(Identifier id, String name, String role, int color, Identifier skin, Identifier dimension, Vec3 pos,
                       String hint, String actionType, JsonObject actionParams, List<String> greetings) {
        /** Whether clicking this NPC normally opens something (a shop, the bank, enchanting...). */
        public boolean hasAction() {
            return !actionType.isEmpty() && !actionType.equals("dialog");
        }
    }

    @FunctionalInterface
    public interface Interact {
        /** A player right-clicked an NPC. Return true to say you handled it: the NPC then does nothing else. */
        boolean onInteract(ServerPlayer player, Identifier npc);
    }

    public static final Event<Interact> INTERACT = EventFactory.createArrayBacked(Interact.class, listeners -> (player, npc) -> {
        for (Interact listener : listeners) {
            if (listener.onInteract(player, npc)) {
                return true;
            }
        }
        return false;
    });

    private static volatile Map<Identifier, Info> npcs = Map.of();

    private NpcInteractions() {
    }

    /** Called by the npcs mod whenever its data loads. */
    public static void publish(Map<Identifier, Info> loaded) {
        npcs = Map.copyOf(loaded);
    }

    public static Map<Identifier, Info> all() {
        return npcs;
    }

    public static Info get(Identifier id) {
        return npcs.get(id);
    }

    /** Runs the NPC's normal click action (opens its shop...). Returns false if it has none or no mod handles it. */
    public static boolean runAction(ServerPlayer player, Identifier id) {
        Info info = npcs.get(id);
        return info != null && info.hasAction() && com.skyrpg.core.action.Actions.run(info.actionType(), player, info.actionParams());
    }
}
