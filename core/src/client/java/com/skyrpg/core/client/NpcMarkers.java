package com.skyrpg.core.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Quest markers over NPCs: the npcs mod tells this where each NPC stands, the questline says which marker each one
 * gets, and the NPC renderer asks {@link #decorate} for the name tag to draw. The marker rides in the name tag (a gold
 * "!" for a quest to pick up, a grey "?" while it is under way, a gold "?" when it is ready to hand in), which is
 * robust: no world-space drawing, and it follows the NPC's own name-tag visibility.
 */
public final class NpcMarkers {
    public static final int NONE = 0;
    public static final int AVAILABLE = 1;
    public static final int IN_PROGRESS = 2;
    public static final int READY = 3;

    /** Where an NPC stands (x, y, z). */
    public record Spot(Identifier id, double x, double y, double z) {
    }

    private static volatile List<Spot> spots = List.of();
    private static volatile Map<Identifier, Integer> kinds = Map.of();

    private NpcMarkers() {
    }

    public static void setSpots(List<Spot> npcs) {
        spots = List.copyOf(npcs);
    }

    public static void setKinds(Map<Identifier, Integer> markers) {
        kinds = new HashMap<>(markers);
    }

    public static void clear() {
        spots = List.of();
        kinds = Map.of();
    }

    public static int kindOf(Identifier npc) {
        return kinds.getOrDefault(npc, NONE);
    }

    /** The NPC standing where this entity is, or null. */
    public static Identifier npcAt(Entity entity) {
        Identifier best = null;
        double bestDistance = 2.25;
        for (Spot spot : spots) {
            double dx = entity.getX() - spot.x();
            double dz = entity.getZ() - spot.z();
            double dy = entity.getY() - spot.y();
            double distance = dx * dx + dz * dz + dy * dy * 0.25;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = spot.id();
            }
        }
        return best;
    }

    /** The name tag with the NPC's marker around it, or {@code original} when this entity is no NPC with a marker. */
    public static Component decorate(Entity entity, Component original) {
        if (kinds.isEmpty() || original == null) {
            return original;
        }
        Identifier npc = npcAt(entity);
        int kind = npc == null ? NONE : kindOf(npc);
        if (kind == NONE) {
            return original;
        }
        MutableComponent mark = Component.literal(kind == AVAILABLE ? "!" : "?")
                .withStyle(kind == IN_PROGRESS ? ChatFormatting.GRAY : ChatFormatting.GOLD, ChatFormatting.BOLD);
        return Component.empty().append(mark).append(Component.literal(" ")).append(original).append(Component.literal(" ")).append(mark.copy());
    }
}
