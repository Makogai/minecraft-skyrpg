package com.skyrpg.chat.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import java.util.List;
import net.minecraft.client.Minecraft;

/** The Chat category on the SkyRPG Settings page. */
public final class ChatSettings {
    public static final String CATEGORY = "Chat";
    public static final String TIME_OFF = "Off";
    public static final String TIME_MINUTES = "12:34";
    public static final String TIME_SECONDS = "12:34:56";

    public static Setting theme;
    public static Setting stripes;
    public static Setting timestamps;
    public static Setting compact;
    public static Setting tabs;
    public static Setting mentions;
    public static Setting pingVolume;
    public static Setting whisperPing;
    public static Setting images;
    public static Setting linkMode;
    public static Setting imageHeight;
    public static Setting stickers;
    public static final String LINKS_WHITELIST = "Known sites";
    public static final String LINKS_ANY = "Any site (click to load)";
    public static final String LINKS_OFF = "Off";

    private ChatSettings() {
    }

    static void register() {
        SkyRpgSettings.category(CATEGORY).description("How chat looks, mentions, timestamps and tabs");

        theme = SkyRpgSettings.toggle("chat.theme", CATEGORY, "Themed chat box",
                "SkyRPG colours and a framed panel for the chat instead of plain black. Uses the Text Background Opacity setting",
                true, value -> refresh()).section("Look");
        stripes = SkyRpgSettings.toggle("chat.stripes", CATEGORY, "Message stripes",
                "A coloured stripe beside each message by kind: player chat, whispers, joins, deaths, SkyRPG and more. Hover one for the time",
                true, value -> ChatHooks.retagAll()).section("Look");
        timestamps = SkyRpgSettings.choice("chat.timestamps", CATEGORY, "Timestamps",
                "The time each message arrived, before it", List.of(TIME_OFF, TIME_MINUTES, TIME_SECONDS), TIME_OFF,
                value -> refresh()).section("Look");
        compact = SkyRpgSettings.toggle("chat.compact", CATEGORY, "Compact repeats",
                "The same message several times in a row shows once, with a grey (x3)", true, null).section("Look");

        mentions = SkyRpgSettings.toggle("chat.mentions", CATEGORY, "Highlight mentions",
                "Your name or @name in someone's message is highlighted and gives a soft ding", true, null)
                .section("Mentions").keywords("ping", "name", "@");
        pingVolume = SkyRpgSettings.slider("chat.ping_volume", CATEGORY, "Ping volume",
                "How loud mentions and whispers ding. 0% is silent", 0, 1, 0.05, 0.7, null)
                .format(value -> Math.round(value * 100) + "%").section("Mentions").keywords("sound", "ding");
        whisperPing = SkyRpgSettings.toggle("chat.whisper_ping", CATEGORY, "Ding on whispers",
                "A softer ding when someone whispers to you", true, null).section("Mentions").keywords("msg", "sound");

        tabs = SkyRpgSettings.toggle("chat.tabs", CATEGORY, "Chat tabs",
                "All / Chat / Whispers / SkyRPG tabs on the open chat. Closing chat goes back to All", true,
                value -> ChatTabs.reset()).section("Tabs");

        images = SkyRpgSettings.toggle("chat.images", CATEGORY, "Show images",
                "Pictures inline in chat: shared screenshots, stickers and picture links. Off shows only the text",
                true, value -> relayout()).section("Images").keywords("picture", "screenshot", "link");
        linkMode = SkyRpgSettings.choice("chat.image_links", CATEGORY, "Images from links",
                "Known sites (imgur, Discord, Reddit, Tenor, GitHub) load by themselves. Any site shows a link you click to load. "
                        + "Loading a link tells that site your address, so Off loads nothing from links",
                List.of(LINKS_WHITELIST, LINKS_ANY, LINKS_OFF), LINKS_WHITELIST, value -> relayout()).section("Images")
                .keywords("privacy", "url", "download");
        imageHeight = SkyRpgSettings.slider("chat.image_height", CATEGORY, "Max image height",
                "The tallest a picture gets in chat, in chat lines. Pictures keep their shape", 3, 12, 1, 7, value -> relayout())
                .format(value -> Math.round(value) + " lines").section("Images");
        stickers = SkyRpgSettings.toggle("chat.stickers", CATEGORY, "Show stickers",
                "[s:gg]-style stickers show as pictures. Off leaves the text as typed", true, value -> relayout()).section("Images");
    }

    private static void relayout() {
        ImageCache.relayout();
    }

    public static boolean imagesOn() {
        return images == null || images.get();
    }

    public static boolean stickersOn() {
        return stickers == null || stickers.get();
    }

    /** Re-lays out the chat history, for settings that change how lines are drawn. */
    static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().rescaleChat();
        }
    }

    public static boolean timestampsOn() {
        return timestamps != null && !timestamps.is(TIME_OFF);
    }
}
