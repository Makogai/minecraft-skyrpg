package com.skyrpg.progress.collections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** What the client knows: the collections as the server described them, and the player's own counts. Plain data, no client classes. */
public final class ClientCollections {
    private static volatile Map<Identifier, CollectionPayloads.View> views = Map.of();
    private static volatile Map<Identifier, Long> counts = Map.of();

    private ClientCollections() {
    }

    public static void setDefs(List<CollectionPayloads.View> list) {
        Map<Identifier, CollectionPayloads.View> map = new LinkedHashMap<>();
        list.forEach(view -> map.put(view.id(), view));
        views = map;
    }

    public static void setCounts(Map<Identifier, Long> map) {
        counts = Map.copyOf(map);
    }

    public static List<CollectionPayloads.View> views() {
        return List.copyOf(views.values());
    }

    public static CollectionPayloads.View view(Identifier id) {
        return views.get(id);
    }

    public static long count(Identifier id) {
        return counts.getOrDefault(id, 0L);
    }

    public static int tier(Identifier id) {
        return tier(views.get(id), count(id));
    }

    public static int tier(CollectionPayloads.View view, long count) {
        if (view == null) {
            return 0;
        }
        int tier = 0;
        while (tier < view.thresholds().size() && count >= view.thresholds().get(tier)) {
            tier++;
        }
        return tier;
    }

    public static String name(Identifier id) {
        CollectionPayloads.View view = views.get(id);
        if (view != null) {
            return view.name();
        }
        String path = id.getPath();
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
