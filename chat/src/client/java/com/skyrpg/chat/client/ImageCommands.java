package com.skyrpg.chat.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.skyrpg.chat.ChatImages;
import com.skyrpg.chat.Stickers;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client commands for pictures in chat: {@code /stickers} (the list, click one to put it in your message),
 * {@code /share screenshot [file]} (your latest screenshot, or a named one from the screenshots folder, to everyone) and
 * {@code /skyrpgimg load <url>} (what the "click to load" link runs).
 */
final class ImageCommands {
    private static final int SHARE_WIDTH = 640;
    private static final int SHARE_LIMIT = 300_000;
    private static final int MUTED = 0xFF8D96A8;
    private static final int ACCENT = 0xFFF2B33D;
    private static int uploads;

    private static final SuggestionProvider<FabricClientCommandSource> SCREENSHOTS = (context, builder) -> {
        String typed = builder.getRemainingLowerCase();
        for (Path path : screenshots()) {
            String name = path.getFileName().toString();
            if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private ImageCommands() {
    }

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommands.literal("stickers").executes(ImageCommands::listStickers));
            dispatcher.register(ClientCommands.literal("share")
                    .then(ClientCommands.literal("screenshot")
                            .executes(context -> share(context, null))
                            .then(ClientCommands.argument("file", StringArgumentType.greedyString()).suggests(SCREENSHOTS)
                                    .executes(context -> share(context, StringArgumentType.getString(context, "file"))))));
            dispatcher.register(ClientCommands.literal("skyrpgimg")
                    .then(ClientCommands.literal("load")
                            .then(ClientCommands.argument("url", StringArgumentType.greedyString()).executes(context -> {
                                String url = StringArgumentType.getString(context, "url");
                                if (ImageRefs.scan(url).isEmpty()) {
                                    context.getSource().sendError(Component.literal("That isn't a link to a picture."));
                                    return 0;
                                }
                                ImageCache.approve(url);
                                ImageCache.request(new ImageRefs.Ref(ImageRefs.Kind.URL, url));
                                ImageCache.relayout();
                                return 1;
                            }))));
        });
    }

    // ------------------------------------------------------------------ /stickers

    private static int listStickers(CommandContext<FabricClientCommandSource> context) {
        MutableComponent out = Component.literal("✦ Stickers").withColor(ACCENT)
                .append(Component.literal("  type [s:name] in chat, or click one").withColor(MUTED));
        List<String> names = Stickers.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            out.append(Component.literal(i % 5 == 0 ? "\n  " : "   "));
            out.append(Component.literal("[s:" + name + "]").withStyle(style -> style.withColor(0xFFC8CEDA)
                    .withClickEvent(new ClickEvent.SuggestCommand("[s:" + name + "]"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to use " + name).withColor(MUTED)))));
        }
        context.getSource().sendFeedback(out);
        return names.size();
    }

    // ------------------------------------------------------------------ /share screenshot

    private static List<Path> screenshots() {
        Path folder = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var stream = Files.list(folder)) {
            List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                    }).toList());
            files.sort(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed());
            return files.size() > 40 ? files.subList(0, 40) : files;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static int share(CommandContext<FabricClientCommandSource> context, String name) {
        FabricClientCommandSource source = context.getSource();
        if (!ClientPlayNetworking.canSend(ChatImages.Upload.TYPE)) {
            source.sendError(Component.literal("This server doesn't support sharing screenshots."));
            return 0;
        }
        Path file;
        if (name == null) {
            List<Path> all = screenshots();
            if (all.isEmpty()) {
                source.sendError(Component.literal("No screenshots yet. Press F2 in game first."));
                return 0;
            }
            file = all.getFirst();
        } else {
            Path folder = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");
            file = folder.resolve(name).normalize();
            if (!file.startsWith(folder.normalize()) || !Files.isRegularFile(file)) {
                source.sendError(Component.literal("No screenshot called " + name + " in the screenshots folder."));
                return 0;
            }
        }
        Path chosen = file;
        int upload = ++uploads;
        source.sendFeedback(Component.literal("Sharing " + chosen.getFileName() + "...").withColor(MUTED));
        Thread worker = new Thread(() -> {
            try {
                byte[] bytes = compress(chosen);
                Minecraft.getInstance().execute(() -> send(upload, bytes));
            } catch (IOException | RuntimeException e) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.hud.getChat()
                        .addClientSystemMessage(Component.literal("Couldn't share that screenshot: " + e.getMessage()).withColor(0xFFFF5555)));
            }
        }, "SkyRPG chat screenshot");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static void send(int upload, byte[] bytes) {
        if (!ClientPlayNetworking.canSend(ChatImages.Upload.TYPE)) {
            return;
        }
        int total = (bytes.length + ChatImages.CHUNK - 1) / ChatImages.CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * ChatImages.CHUNK;
            ClientPlayNetworking.send(new ChatImages.Upload(upload, i, total, java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + ChatImages.CHUNK))));
        }
    }

    /** At most 640 px wide, as a JPEG under about 300 KB (a PNG if that is smaller and still fits). */
    static byte[] compress(Path file) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            throw new IOException("not a picture");
        }
        int width = Math.min(SHARE_WIDTH, source.getWidth());
        int height = Math.max(1, Math.round((float) source.getHeight() * width / source.getWidth()));
        if (height > SHARE_WIDTH) {
            width = Math.max(1, Math.round((float) width * SHARE_WIDTH / height));
            height = SHARE_WIDTH;
        }
        while (true) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.drawImage(source.getScaledInstance(width, height, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
            g.dispose();
            for (float quality : new float[] {0.85f, 0.72f, 0.6f, 0.48f, 0.36f}) {
                byte[] jpeg = jpeg(scaled, quality);
                if (jpeg.length <= SHARE_LIMIT) {
                    return jpeg;
                }
            }
            if (width < 200) {
                throw new IOException("couldn't make it small enough");
            }
            width = width * 4 / 5;
            height = Math.max(1, height * 4 / 5);
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
