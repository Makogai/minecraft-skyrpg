package com.skyrpg.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import com.skyrpg.core.text.PlayerTitles;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional chat titles ("[Founder]"), set by admins with {@code /chat title}. A player with a title shows it before
 * their name, and their name takes its colour instead of their SkyRPG Level colour. Saved with the player.
 */
public final class Titles {
    /** {@code icon} is empty for a plain staff title; earned titles (progress mod) carry one. */
    public record Title(String text, int color, String icon) {
        public static final Codec<Title> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("text").forGetter(Title::text),
                Codec.INT.fieldOf("color").forGetter(Title::color),
                Codec.STRING.optionalFieldOf("icon", "").forGetter(Title::icon)).apply(instance, Title::new));

        public Title(String text, int color) {
            this(text, color, "");
        }
    }

    public static final int MAX_LENGTH = 16;

    public static final AttachmentType<Title> TITLE = AttachmentRegistry.create(SkyRpgChat.id("title"), builder -> builder
            .persistent(Title.CODEC)
            .copyOnDeath());

    private Titles() {
    }

    static void init() {
        // Touching the class registers the attachment during mod init.
    }

    /** The title an admin set ({@code /chat title}), or null. It overrides the player's earned one and colours their name. */
    public static Title get(ServerPlayer player) {
        return player.getAttached(TITLE);
    }

    /** The title shown with the player's name: the admin-set one if there is one, else the earned title they equipped, else null. */
    public static Title effective(ServerPlayer player) {
        Title admin = get(player);
        if (admin != null) {
            return admin;
        }
        PlayerTitles.Shown earned = PlayerTitles.active(player);
        return earned == null ? null : new Title(earned.text(), earned.color(), earned.icon());
    }

    public static void set(ServerPlayer player, Title title) {
        if (title == null) {
            player.removeAttached(TITLE);
        } else {
            player.setAttached(TITLE, title);
        }
    }

    /** "Founder" in the title's colour, between two small pill chevron icons. */
    public static MutableComponent tag(Title title) {
        int rgb = title.color() & 0xFFFFFF;
        MutableComponent icon = title.icon().isEmpty() ? Component.empty()
                : PlayerTitles.iconComponent(title.icon(), rgb).append(Component.literal(" ").withStyle(style -> style.withFont(FontDescription.DEFAULT)));
        return Component.empty()
                .append(ChatIcons.tinted(ChatIcons.PILL_LEFT, rgb))
                .append(icon)
                .append(Component.literal(title.text()).withColor(rgb))
                .append(ChatIcons.tinted(ChatIcons.PILL_RIGHT, rgb));
    }
}
