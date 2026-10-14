package com.skyrpg.events;

import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A styled banner that slides in at the top of the screen (the same one skill level ups use), with
 * an optional sound and a copy in chat. Send it with {@link SkyRpgEvents#announce}.
 *
 * <p>JSON ({@code /announce <json>}, and timed announcements inside an event):
 * <pre>{@code
 * {title: "Double drops!", subtitle: "For the next 5 minutes", label: "SERVER EVENT",
 *  color: "#2ECC71", icon: "minecraft:emerald", sound: "minecraft:ui.toast.challenge_complete",
 *  volume: 1, pitch: 1, chat: true}
 * }</pre>
 * Only {@code title} is required. Title and subtitle take plain text with {@code &6} colour codes or
 * a full text component.
 *
 * @param label    small heading over the title, in {@code color}
 * @param sound    played for everyone it's shown to, or null for silence
 * @param chat     also print it in chat (for players with banners turned off, and for the record)
 */
public record Announcement(String label, Component title, Component subtitle, int color, Identifier icon,
                           Identifier sound, float volume, float pitch, boolean chat) {
    public static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("bell");
    public static final Identifier DEFAULT_SOUND = Identifier.withDefaultNamespace("block.note_block.bell");

    public Announcement {
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
    }

    public static Announcement of(String label, Component title, Component subtitle, int color, Identifier icon) {
        return new Announcement(label, title, subtitle, color, icon, DEFAULT_SOUND, 1, 1, true);
    }

    public static Announcement parse(JsonObject json, HolderLookup.Provider registries) {
        if (Json.get(json, "title", "text") == null) {
            throw new IllegalArgumentException("An announcement needs a title, like {title:'Hello!'}");
        }
        String sound = Json.string(json, DEFAULT_SOUND.toString(), "sound");
        return new Announcement(
                Json.string(json, "ANNOUNCEMENT", "label"),
                Json.text(Json.get(json, "title", "text"), registries),
                Json.text(Json.get(json, "subtitle", "description"), registries),
                Json.color(Json.get(json, "color", "colour"), Json.GOLD),
                Json.id(Json.string(json, DEFAULT_ICON.toString(), "icon", "item"), "item"),
                sound.isEmpty() || sound.equals("none") ? null : Json.id(sound, "sound"),
                Json.decimal(json, 1, "volume"),
                Json.decimal(json, 1, "pitch"),
                Json.bool(json, true, "chat"));
    }
}
