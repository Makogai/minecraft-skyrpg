package com.skyrpg.events.client.billboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.client.image.RemoteImages;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.events.billboard.BillboardTheme;
import com.skyrpg.events.billboard.Billboards;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The billboard editor: up to four picture links (they take turns), the size (16:9, by width in blocks), the look (frame
 * and back materials) and how long each picture stays. Save sends it to the server, which checks it.
 */
public final class BillboardScreen extends Screen {
    private static final int W = 360;
    private static final int H = 292;

    private final int entity;
    private final boolean stand;
    private final List<EditBox> links = new ArrayList<>();
    private final List<String> startLinks = new ArrayList<>();
    private int boardWidth;
    private int theme;
    private int seconds;
    private final List<Button> widthButtons = new ArrayList<>();
    private final List<Button> themeButtons = new ArrayList<>();

    BillboardScreen(int entity, String json) {
        super(Component.literal("Billboard"));
        this.entity = entity;
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        this.boardWidth = data.get("width").getAsInt();
        this.theme = data.get("theme").getAsInt();
        this.seconds = data.get("seconds").getAsInt();
        this.stand = data.get("stand").getAsBoolean();
        for (JsonElement e : data.getAsJsonArray("images")) {
            startLinks.add(e.getAsString());
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        int x = left() + 12;
        int y = top() + 56;
        List<String> kept = new ArrayList<>();
        for (EditBox box : links) {
            kept.add(box.getValue());
        }
        links.clear();
        widthButtons.clear();
        themeButtons.clear();
        for (int i = 0; i < 4; i++) {
            EditBox box = new EditBox(font, x, y + i * 20, W - 24, 16, Component.literal("Picture " + (i + 1)));
            box.setMaxLength(400);
            box.setHint(Component.literal(i == 0 ? "https://... picture link (PNG or JPG)" : "another picture (optional)"));
            String value = i < kept.size() ? kept.get(i) : i < startLinks.size() ? startLinks.get(i) : "";
            box.setValue(value);
            links.add(addRenderableWidget(box));
        }
        y += 4 * 20 + 18;
        List<Integer> widths = BillboardTheme.WIDTHS;
        int bw = (W - 24 - (widths.size() - 1) * 4) / widths.size();
        for (int i = 0; i < widths.size(); i++) {
            int value = widths.get(i);
            widthButtons.add(addRenderableWidget(Button.builder(Component.literal(value + " wide"), b -> boardWidth = value)
                    .bounds(x + i * (bw + 4), y, bw, 18).build()));
        }
        y += 34;
        List<BillboardTheme> themes = BillboardTheme.ALL;
        int tw = (W - 24 - (themes.size() - 1) * 4) / themes.size();
        for (int i = 0; i < themes.size(); i++) {
            int value = i;
            themeButtons.add(addRenderableWidget(Button.builder(Component.literal(themes.get(i).name()), b -> theme = value)
                    .bounds(x + i * (tw + 4), y, tw, 18).build()));
        }
        y += 34;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> seconds = Math.max(3, seconds - 1)).bounds(x + 150, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> seconds = Math.min(120, seconds + 1)).bounds(x + 216, y, 20, 18).build());
        int by = top() + H - 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(left() + W - 172, by, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(left() + W - 88, by, 76, 20).build());
    }

    private void save() {
        JsonObject json = new JsonObject();
        json.addProperty("width", boardWidth);
        json.addProperty("theme", theme);
        json.addProperty("seconds", seconds);
        JsonArray images = new JsonArray();
        for (EditBox box : links) {
            if (!box.getValue().isBlank()) {
                images.add(box.getValue().trim());
            }
        }
        json.add("images", images);
        ClientPlayNetworking.send(new Billboards.Configure(entity, json.toString()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        Draw.shadow(g, x, y, W, H, 10, 0.6f);
        Draw.panel(g, x, y, W, H, Theme.WINDOW, Theme.BORDER);
        g.fill(x, y, x + W, y + 2, 0xFFFF7A1A);
        Draw.fit(g, font, Component.literal("BILLBOARD"), x + 12, y + 10, 200, 1.4f, 1f, Draw.Align.LEFT, 0xFFFF9A3C, true);
        Draw.fit(g, font, Component.literal(stand ? "Free-standing · break it to move it" : "On the wall · break it to move it"), x + 12, y + 26, 200,
                Draw.Align.LEFT, Theme.MUTED);
        // a little preview of the first picture
        String first = links.isEmpty() ? "" : links.get(0).getValue().trim();
        int px = x + W - 12 - 80;
        int py = y + 6;
        g.fill(px - 1, py - 1, px + 81, py + 46, Theme.BORDER);
        g.fill(px, py, px + 80, py + 45, 0xFF0A0B10);
        if (first.startsWith("https://") || first.startsWith("http://")) {
            RemoteImages.Entry image = RemoteImages.get(first);
            if (image.ready()) {
                g.blit(RenderPipelines.GUI_TEXTURED, image.texture(), px, py, 0f, 0f, 80, 45, image.width(), image.height(), image.width(), image.height());
            } else {
                Draw.fit(g, font, Component.literal(image.state() == RemoteImages.State.FAILED ? "can't load" : "loading..."), px + 40, py + 18, 76,
                        Draw.Align.CENTER, Theme.DIM);
            }
        } else {
            Draw.fit(g, font, Component.literal("no picture"), px + 40, py + 18, 76, Draw.Align.CENTER, Theme.DIM);
        }
        int lx = x + 12;
        Draw.fit(g, font, Component.literal("Pictures (they take turns)"), lx, y + 56 - 11, 200, Draw.Align.LEFT, Theme.TEXT);
        int sy = y + 56 + 4 * 20 + 18;
        Draw.fit(g, font, Component.literal("Size (16:9): " + boardWidth + " x " + trim(boardWidth * 9 / 16f) + " blocks"), lx, sy - 11, 300, Draw.Align.LEFT, Theme.TEXT);
        Draw.fit(g, font, Component.literal("Look"), lx, sy + 34 - 11, 200, Draw.Align.LEFT, Theme.TEXT);
        int ty = sy + 68;
        Draw.fit(g, font, Component.literal("Each picture shows for"), lx, ty + 5, 140, Draw.Align.LEFT, Theme.TEXT);
        Draw.fit(g, font, Component.literal(seconds + "s"), lx + 193, ty + 5, 40, Draw.Align.CENTER, 0xFFFFD24A);
        Draw.fit(g, font, Component.literal("Tip: drop pictures in docs/showcase/ and use their makogai.github.io link."), lx, y + H - 46, W - 24,
                0.75f, 0.6f, Draw.Align.LEFT, Theme.DIM, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // mark the chosen size and look
        for (int i = 0; i < widthButtons.size(); i++) {
            if (BillboardTheme.WIDTHS.get(i) == boardWidth) {
                Button b = widthButtons.get(i);
                Draw.outline(g, b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFF9A3C);
            }
        }
        for (int i = 0; i < themeButtons.size(); i++) {
            if (i == theme) {
                Button b = themeButtons.get(i);
                Draw.outline(g, b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFF9A3C);
            }
        }
    }

    private static String trim(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
