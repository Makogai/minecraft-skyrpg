package com.skyrpg.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

/**
 * A custom sidebar in the SkyRPG look, shown with {@code /board show <json>} or
 * {@link SkyRpgEvents#showBoard}. It takes the place of the vanilla scoreboard sidebar while shown.
 *
 * <p>JSON:
 * <pre>{@code
 * {title: "Build Battle", color: "aqua", footer: "skyrpg",
 *  lines: ["&7Theme: &fCastles", "", ["Time left", "4:00"], {label: "Round", value: "2/3", color: "gold"}],
 *  duration: "5m"}
 * }</pre>
 * A line is a string (left text only, {@code ""} for a gap), a {@code [label, value]} pair, or an
 * object with {@code label}/{@code text}, {@code value} and an optional {@code color} for the value.
 * {@code {player}} in any text becomes the viewer's name. {@code duration} hides it on its own.
 *
 * @param seconds hide after this many seconds, 0 = until /board hide
 */
public record Board(Component title, int color, List<Line> lines, Component footer, int seconds) {
    public static final int MAX_LINES = 15;

    /** {@code right} may be empty. */
    public record Line(Component left, Component right) {
    }

    public Board {
        lines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
    }

    public static Board parse(JsonObject json, HolderLookup.Provider registries) {
        int color = Json.color(Json.get(json, "color", "colour"), Json.GOLD);
        List<Line> lines = new ArrayList<>();
        JsonArray rows = Json.array(json, "lines", "line");
        for (JsonElement row : rows) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            lines.add(line(row, registries));
        }
        return new Board(
                Json.text(Json.get(json, "title", "name"), registries),
                color,
                lines,
                Json.text(Json.get(json, "footer"), registries),
                Json.seconds(Json.get(json, "duration", "seconds"), 0));
    }

    private static Line line(JsonElement row, HolderLookup.Provider registries) {
        if (row.isJsonArray()) {
            JsonArray pair = row.getAsJsonArray();
            return new Line(pair.isEmpty() ? Component.empty() : Json.text(pair.get(0), registries),
                    pair.size() > 1 ? Json.text(pair.get(1), registries) : Component.empty());
        }
        if (row.isJsonObject() && Json.get(row.getAsJsonObject(), "label", "value", "left", "right") != null) {
            JsonObject object = row.getAsJsonObject();
            Component value = Json.text(Json.get(object, "value", "right"), registries);
            JsonElement valueColor = Json.get(object, "color", "colour");
            if (valueColor != null) {
                value = value.copy().withColor(Json.color(valueColor, Json.GOLD) & 0xFFFFFF);
            }
            return new Line(Json.text(Json.get(object, "label", "text", "left"), registries), value);
        }
        // A string, or a whole text component.
        return new Line(Json.text(row, registries), Component.empty());
    }
}
