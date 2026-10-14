package com.skyrpg.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional chat titles ("[Founder]"), set by admins with {@code /chat title}. A player with a title shows it before
 * their name, and their name takes its colour instead of their SkyRPG Level colour. Saved with the player.
 */
public final class Titles {
    public record Title(String text, int color) {
        public static final Codec<Title> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("text").forGetter(Title::text),
                Codec.INT.fieldOf("color").forGetter(Title::color)).apply(instance, Title::new));
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

    public static Title get(ServerPlayer player) {
        return player.getAttached(TITLE);
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
        return Component.empty()
                .append(ChatIcons.tinted(ChatIcons.PILL_LEFT, rgb))
                .append(Component.literal(title.text()).withColor(rgb))
                .append(ChatIcons.tinted(ChatIcons.PILL_RIGHT, rgb));
    }
}
