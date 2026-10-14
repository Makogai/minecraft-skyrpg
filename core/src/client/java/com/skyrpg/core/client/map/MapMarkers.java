package com.skyrpg.core.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Icons on SkyRPG's maps (the minimap and the world map), from any mod: NPCs, waypoints, event spots...
 * A mod registers a {@link Provider}; whichever mod draws maps (the {@code skyrpg_utils} mod) asks every
 * provider for the markers of the dimension it's showing, every frame, so providers should hand out markers
 * they already have rather than work them out each time.
 * <pre>
 * MapMarkers.register((dimension, out) -> {
 *     if (dimension.equals(HUB)) {
 *         out.accept(new MapMarkers.Marker(id("bank"), 12.5, 65, -4.5, new MapMarkers.Item(new ItemStack(Items.GOLD_INGOT)),
 *                 Component.literal("Bank"), List.of(Component.literal("Deposit and withdraw coins")), 0xFFF2B33D));
 *     }
 * });
 * </pre>
 */
public final class MapMarkers {
    /** How a marker looks. */
    public sealed interface Icon permits Item, Face, Dot {
    }

    /** An item's icon. */
    public record Item(ItemStack stack) implements Icon {
    }

    /** A face, from a texture in the player-skin layout (an NPC's or player's skin). */
    public record Face(Identifier skin) implements Icon {
    }

    /** A round pin in a colour (ARGB). */
    public record Dot(int color) implements Icon {
    }

    /**
     * One marker: where it is, its icon, a name (shown on hover, and next to it when zoomed in), more lines for the
     * hover tooltip, and an accent colour (ARGB) for its frame and tooltip.
     */
    public record Marker(Identifier id, double x, double y, double z, Icon icon, Component name, List<Component> details, int color) {
    }

    @FunctionalInterface
    public interface Provider {
        /** Hands every marker in {@code dimension} (e.g. {@code skyrpg_hub:hub}) to {@code out}. */
        void collect(Identifier dimension, Consumer<Marker> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    private MapMarkers() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    /** Every marker in the dimension, from all providers. */
    public static List<Marker> collect(Identifier dimension) {
        List<Marker> markers = new ArrayList<>();
        for (Provider provider : PROVIDERS) {
            provider.collect(dimension, markers::add);
        }
        return markers;
    }
}
