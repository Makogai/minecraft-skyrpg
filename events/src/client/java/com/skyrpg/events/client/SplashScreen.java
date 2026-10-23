package com.skyrpg.events.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.client.image.RemoteImages;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The full-screen season splash: on joining while a season is on or coming up, the server sends its art and headline
 * ({@code ShowcasePayloads.Splash}) and this screen plays it: the picture fades in and slowly zooms behind turning light
 * rays, embers rise, the title slams in, the feature lines slide in one by one, and a live countdown runs. Any key or click
 * closes it. Shown once a day per season and state ("soon", "live"), remembered in {@code config/skyrpg/splash_seen.json};
 * {@code /season splash} shows it again.
 */
public final class SplashScreen extends Screen {
    private static final long CLOSE_GRACE_MS = 600;
    private static JsonObject pending;

    private final JsonObject data;
    private final long opened = System.currentTimeMillis();
    private final long clockOffset;
    private final int accent;
    private final List<String> lines = new ArrayList<>();
    private final float[][] embers = new float[70][];
    private long imageReadyAt = -1;

    private SplashScreen(JsonObject data) {
        super(Component.literal(data.get("title").getAsString()));
        this.data = data;
        this.clockOffset = data.has("now") ? data.get("now").getAsLong() - System.currentTimeMillis() : 0;
        this.accent = 0xFF000000 | (data.has("color") ? data.get("color").getAsInt() : 0xFF7A1A);
        if (data.has("lines")) {
            for (JsonElement line : data.getAsJsonArray("lines")) {
                lines.add(line.getAsString());
            }
        }
        java.util.Random random = new java.util.Random(opened);
        for (int i = 0; i < embers.length; i++) {
            // x (0..1), start y (0..1), rise speed (screens per second), size, cyan or accent
            embers[i] = new float[] {random.nextFloat(), random.nextFloat(), 0.03f + random.nextFloat() * 0.07f, 1 + random.nextInt(2),
                    random.nextFloat() < 0.25f ? 1 : 0};
        }
    }

    // ---------------------------------------------------------------- arrival

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending != null && client.gui.screen() == null && client.level != null && client.player != null) {
                JsonObject next = pending;
                pending = null;
                client.gui.setScreen(new SplashScreen(next));
                if (next.has("sound")) {
                    Ui.play(Identifier.parse(next.get("sound").getAsString()), next.has("pitch") ? next.get("pitch").getAsFloat() : 1f, 0.8f);
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> pending = null);
    }

    /** From the network: queue it, unless it was already seen today (and isn't forced). */
    static void offer(String json) {
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        String key = data.get("id").getAsString() + ":" + data.get("state").getAsString();
        boolean force = data.has("force") && data.get("force").getAsBoolean();
        JsonObject seen = readSeen();
        String today = LocalDate.now().toString();
        if (!force && seen.has(key) && seen.get(key).getAsString().equals(today)) {
            return;
        }
        seen.addProperty(key, today);
        writeSeen(seen);
        if (data.has("image")) {
            RemoteImages.get(data.get("image").getAsString());
        }
        pending = data;
    }

    private static Path seenFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("skyrpg").resolve("splash_seen.json");
    }

    private static JsonObject readSeen() {
        try {
            Path file = seenFile();
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
            // a broken file just means it shows again
        }
        return new JsonObject();
    }

    private static void writeSeen(JsonObject seen) {
        try {
            Path file = seenFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, seen.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not worth bothering the player about
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float in = Draw.easeOut(Mth.clamp((System.currentTimeMillis() - opened) / 400f, 0f, 1f));
        g.fill(0, 0, width, height, Draw.withAlpha(0xFF06030C, 0.97f * in));
        g.fillGradient(0, 0, width, height / 3, Draw.withAlpha(Draw.mix(0xFF06030C, accent, 0.18f), 0.6f * in), 0);
        g.fillGradient(0, height * 2 / 3, width, height, 0, Draw.withAlpha(0xFF000000, 0.8f * in));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        try {
            draw(g);
        } catch (RuntimeException e) {
            // never let a bad splash keep anyone out of the game
            com.skyrpg.events.SkyRpgEvents.LOGGER.warn("Splash failed", e);
            onClose();
        }
    }

    private void draw(GuiGraphicsExtractor g) {
        long t = System.currentTimeMillis() - opened;
        int cx = width / 2;

        RemoteImages.Entry image = data.has("image") ? RemoteImages.get(data.get("image").getAsString()) : null;
        boolean hasImage = image != null && image.ready();
        if (hasImage && imageReadyAt < 0) {
            imageReadyAt = t;
        }
        // The picture's box: as big as fits in the top part, keeping its shape.
        int boxW = (int) (width * 0.62f);
        int boxH = (int) (height * 0.44f);
        if (hasImage) {
            float aspect = image.width() / (float) image.height();
            if (boxW / (float) boxH > aspect) {
                boxW = (int) (boxH * aspect);
            } else {
                boxH = (int) (boxW / aspect);
            }
        } else {
            boxH = image == null ? 0 : boxH;
        }
        int boxX = cx - boxW / 2;
        int boxY = (int) (height * 0.07f);

        rays(g, cx, boxY + boxH / 2, Math.max(boxW, 120), t);
        embers(g, t);

        if (image != null) {
            float fade = hasImage ? Draw.easeOut(Mth.clamp((t - Math.max(imageReadyAt, 250)) / 700f, 0f, 1f)) : 0f;
            // glow and frame
            Draw.shadow(g, boxX, boxY, boxW, boxH, 14, 0.6f * Math.max(fade, 0.3f));
            g.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, Draw.withAlpha(accent, 0.25f + 0.55f * fade));
            g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF0B0712);
            if (hasImage) {
                // slow zoom (Ken Burns) inside the frame
                float zoom = 1.0f + 0.06f * Math.min(1f, t / 14000f) + 0.04f * (1 - fade);
                int dw = (int) (boxW * zoom);
                int dh = (int) (boxH * zoom);
                g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
                int alpha = (int) (255 * fade);
                g.blit(RenderPipelines.GUI_TEXTURED, image.texture(), cx - dw / 2, boxY + boxH / 2 - dh / 2, 0f, 0f, dw, dh,
                        image.width(), image.height(), image.width(), image.height(), (alpha << 24) | 0xFFFFFF);
                g.disableScissor();
            } else {
                // still loading: a soft shimmer
                float s = (t % 1600) / 1600f;
                int sx = boxX + (int) ((boxW + 80) * s) - 40;
                g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
                g.fillGradient(sx - 30, boxY, sx + 30, boxY + boxH, Draw.withAlpha(accent, 0.08f), Draw.withAlpha(accent, 0.02f));
                g.disableScissor();
            }
        }

        int y = boxY + boxH + (image == null ? (int) (height * 0.16f) : 14);
        // state pill
        boolean live = "live".equals(data.get("state").getAsString());
        float pillIn = Draw.easeOut(Mth.clamp((t - 450) / 300f, 0f, 1f));
        if (pillIn > 0) {
            String pill = live ? "LIVE NOW" : "COMING SOON";
            int pw = font.width(pill) + 22;
            int pillColor = live ? 0xFF4ADE80 : accent;
            Draw.panel(g, cx - pw / 2, y, pw, 13, Draw.withAlpha(0xFF0B0712, 0.9f * pillIn), Draw.withAlpha(pillColor, pillIn));
            float pulse = 0.5f + 0.5f * Mth.sin(t / 220f);
            g.fill(cx - pw / 2 + 5, y + 5, cx - pw / 2 + 8, y + 8, Draw.withAlpha(pillColor, pillIn * (0.4f + 0.6f * pulse)));
            Draw.fit(g, font, Component.literal(pill), cx + 5, y + 3, pw, Draw.Align.CENTER, Draw.withAlpha(pillColor, pillIn));
        }
        y += 20;

        // title slam
        float slam = Mth.clamp((t - 650) / 380f, 0f, 1f);
        if (slam > 0) {
            float scale = 3.2f * (1 + 0.5f * (1 - Draw.easeOut(slam)));
            int alpha = (int) (255 * Math.min(1f, slam * 2));
            Draw.fit(g, font, Component.literal(data.get("title").getAsString().toUpperCase(java.util.Locale.ROOT)), cx, y, width - 30,
                    scale, 1.2f, Draw.Align.CENTER, (alpha << 24) | (accent & 0xFFFFFF), true);
        }
        y += 32;

        // subtitle and the clock
        float subIn = Draw.easeOut(Mth.clamp((t - 950) / 400f, 0f, 1f));
        if (subIn > 0) {
            long now = System.currentTimeMillis() + clockOffset;
            String clock = live ? clock(data.get("ends").getAsLong() - now) + " left" : "starts in " + clock(data.get("starts").getAsLong() - now);
            String sub = data.has("subtitle") ? data.get("subtitle").getAsString() + "  ·  " + clock : clock;
            Draw.fit(g, font, Component.literal(sub), cx, y, width - 40, 1.1f, 0.8f, Draw.Align.CENTER, Draw.withAlpha(Theme.TEXT, subIn), true);
        }
        y += 20;

        // feature lines, two columns when there's room
        boolean twoCols = lines.size() > 3 && width > 360;
        int colW = twoCols ? Math.min(230, width / 2 - 30) : Math.min(320, width - 60);
        int rows = twoCols ? (lines.size() + 1) / 2 : lines.size();
        for (int i = 0; i < lines.size(); i++) {
            float in = Draw.easeOut(Mth.clamp((t - 1200 - i * 130L) / 380f, 0f, 1f));
            if (in <= 0) {
                continue;
            }
            int col = twoCols ? i / rows : 0;
            int row = twoCols ? i % rows : i;
            int lx = twoCols ? cx - colW - 8 + col * (colW + 16) : cx - colW / 2;
            int ly = y + row * 16;
            int slide = (int) ((1 - in) * -24);
            Draw.panel(g, lx + slide, ly, colW, 13, Draw.withAlpha(0xFF120A1C, 0.85f * in), Draw.withAlpha(accent, 0.35f * in));
            g.fill(lx + slide, ly, lx + slide + 2, ly + 13, Draw.withAlpha(accent, in));
            Draw.fit(g, font, Component.literal(lines.get(i)), lx + slide + 7, ly + 3, colW - 10, Draw.Align.LEFT, Draw.withAlpha(Theme.TEXT, in));
        }

        // footer
        if (t > 1800) {
            float blink = 0.55f + 0.45f * Mth.sin((t - 1800) / 380f);
            Draw.fit(g, font, Component.literal("Click or press any key to play"), cx, height - 22, width - 20, Draw.Align.CENTER,
                    Draw.withAlpha(Theme.MUTED, blink));
            Draw.fit(g, font, Component.literal("/season splash shows this again"), cx, height - 11, width - 20, 0.75f, 0.5f,
                    Draw.Align.CENTER, Theme.DIM, false);
        }
    }

    /** Slowly turning light rays behind the picture. */
    private void rays(GuiGraphicsExtractor g, int x, int y, int size, long t) {
        float in = Draw.easeOut(Mth.clamp(t / 1200f, 0f, 1f));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(t / 9000f);
        int length = (int) (size * 0.9f * in);
        for (int i = 0; i < 16; i++) {
            g.pose().rotate(Mth.TWO_PI / 16);
            g.fillGradient(-5, 0, 5, length, Draw.withAlpha(accent, 0.08f), Draw.withAlpha(accent, 0f));
        }
        g.pose().popMatrix();
    }

    /** Embers (and a few cyan soul sparks) rising over the whole screen. */
    private void embers(GuiGraphicsExtractor g, long t) {
        float sec = t / 1000f;
        float in = Mth.clamp(t / 800f, 0f, 1f);
        for (float[] e : embers) {
            float y = (e[1] - sec * e[2]) % 1f;
            if (y < 0) {
                y += 1;
            }
            int px = (int) (e[0] * width + Mth.sin(sec * 1.3f + e[0] * 20) * 6);
            int py = (int) (y * height);
            int size = (int) e[3];
            float fadeTop = Mth.clamp(y * 3, 0f, 1f);
            int color = e[4] > 0 ? 0xFF7FF3FF : Draw.lighten(accent, 0.2f);
            g.fill(px, py, px + size, py + size, Draw.withAlpha(color, 0.75f * fadeTop * in));
        }
    }

    private static String clock(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }

    // ---------------------------------------------------------------- closing

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (System.currentTimeMillis() - opened > CLOSE_GRACE_MS) {
            onClose();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (System.currentTimeMillis() - opened > CLOSE_GRACE_MS) {
            onClose();
        }
        return true;
    }
}
