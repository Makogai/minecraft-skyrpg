package com.skyrpg.welcome.client;

import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * The SkyRPG title screen: the vanilla panorama under a navy tint with drifting stars, the wordmark, a big Play
 * button that joins the server from {@code config/skyrpg/welcome.json}, the usual menus, and a "What's new" box.
 * Laid out from the window size, so it works from a small window to a 4K monitor at any GUI scale.
 */
final class SkyTitleScreen extends Screen {
    private final long openedAt = Util.getMillis();
    private int logoScale;
    private int logoY;
    private int playBottom;
    private boolean newsFits;

    SkyTitleScreen() {
        super(Component.literal("SkyRPG"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        minecraft.gameRenderer.panorama().startSpin();
        NewsFeed.fetchOnce();
        WelcomeConfig.load();
        int w = width;
        int h = height;
        logoScale = h >= 300 ? 5 : h >= 235 ? 4 : 3;
        boolean tall = h >= 235;
        int playH = tall ? 26 : 22;
        int rowH = tall ? 20 : 18;
        int gap = 4;

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Singleplayer", () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        entries.add(new Entry("Multiplayer", this::multiplayer));
        entries.add(new Entry("Settings", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));
        entries.add(new Entry("SkyRPG Setup", () -> minecraft.gui.setScreen(new SetupScreen(this, false))));
        entries.add(new Entry("Wiki", this::wiki));
        if (ModCompat.loaded("modmenu")) {
            entries.add(new Entry("Mods", this::mods));
        }
        int rows = (entries.size() + 1) / 2;
        int blockH = logoScale * 8 + 18 + 12 + playH + 13 + rows * (rowH + gap) + 16;
        int top = Math.max(6, (h - blockH) / 2 - (h >= 260 ? 8 : 0));
        logoY = top;
        int pw = Math.min(w - 20, 212);
        int px = (w - pw) / 2;
        int y = top + logoScale * 8 + 18 + 12;

        String address = WelcomeConfig.serverAddress();
        SkyButton play = addRenderableWidget(new SkyButton(font, SkyButton.Style.PRIMARY, px, y, pw, playH,
                Component.literal("Play " + WelcomeConfig.serverName()), this::play)
                .description(Component.literal(address.isEmpty() ? "Opens the Multiplayer screen" : "Joins " + address)));
        y += playH + 13;
        playBottom = y - 11;

        int colW = (pw - gap) / 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean alone = i == entries.size() - 1 && entries.size() % 2 == 1;
            int x = alone ? px : px + (i % 2) * (colW + gap);
            int bw = alone ? pw : colW;
            addRenderableWidget(new SkyButton(font, SkyButton.Style.NORMAL, x, y, bw, rowH, Component.literal(entry.label), entry.action));
            if (i % 2 == 1 || alone) {
                y += rowH + gap;
            }
        }
        addRenderableWidget(new SkyButton(font, SkyButton.Style.GHOST, px, y + 1, pw, 14, Component.literal("Quit Game"), minecraft::stop));
        setInitialFocus(play);
        newsFits = w >= 540 && !NewsFeed.items().isEmpty();
    }

    private record Entry(String label, Runnable action) {
    }

    // ------------------------------------------------------------------ actions

    private void multiplayer() {
        minecraft.gui.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
    }

    private void play() {
        String address = WelcomeConfig.serverAddress();
        if (address.isEmpty()) {
            multiplayer();
            return;
        }
        SkyRpgWelcomeClient.joinServer(this, address);
    }

    private void wiki() {
        if (!SkyRpgMenu.open("wiki")) {
            openWebsite();
        }
    }

    private void openWebsite() {
        try {
            net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, URI.create(WelcomeConfig.wikiUrl()));
        } catch (RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't open the website", e);
        }
    }

    private void mods() {
        try {
            Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            minecraft.gui.setScreen((Screen) screen.getConstructor(Screen.class).newInstance(this));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't open Mod Menu", e);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractPanorama(g, partialTick);
        float fade = Draw.easeOut((Util.getMillis() - openedAt) / 700f);
        Backdrop.draw(g, width, height, 0.75f + 0.25f * fade);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float intro = Draw.easeOut((Util.getMillis() - openedAt) / 600f);
        drawLogo(g, intro);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String address = WelcomeConfig.serverAddress();
        String hint = address.isEmpty() ? "Pick a server in Multiplayer" : WelcomeConfig.serverName() + "  ·  " + address;
        g.centeredText(font, hint, width / 2, playBottom, Draw.withAlpha(Theme.MUTED, intro));
        if (newsFits) {
            drawNews(g, intro);
        }
        drawFooter(g, intro);
    }

    private void drawLogo(GuiGraphicsExtractor g, float intro) {
        int s = logoScale;
        Component sky = Component.literal("SKY");
        Component rpg = Component.literal("RPG");
        int wordW = font.width("SKYRPG") * s;
        float slide = (1 - intro) * 10;
        int x = (width - wordW) / 2;
        int y = logoY + Math.round(slide);
        // Soft golden glow behind the word, breathing slowly.
        float breathe = 0.7f + 0.3f * (float) Math.sin(Util.getMillis() / 1400.0);
        for (int i = 0; i < 6; i++) {
            int pad = 4 + i * 5;
            Draw.rounded(g, x - pad, y - pad / 2, wordW + pad * 2, s * 8 + pad, Draw.withAlpha(Theme.ACCENT, 0.018f * breathe * intro));
        }
        // Shadow, then the word.
        Draw.scaledText(g, font, Component.literal("SKYRPG").withColor(0xFF05070C), x + s / 2 + 1, y + s / 2 + 1, s, 0xFF05070C);
        Draw.scaledText(g, font, sky.copy().withColor(Draw.withAlpha(Theme.TEXT, intro)), x, y, s, Theme.TEXT);
        Draw.scaledText(g, font, rpg.copy().withColor(Draw.withAlpha(Theme.ACCENT, intro)), x + font.width("SKY") * s, y, s, Theme.ACCENT);
        // A gold rule that fades out to both sides, with a light that travels along it.
        int ruleY = y + s * 8 + 5;
        int ruleW = Math.min(width - 40, Math.max(wordW + 40, 160));
        int rx = (width - ruleW) / 2;
        for (int i = 0; i < ruleW; i += 2) {
            float edge = 1 - Math.abs((i - ruleW / 2f) / (ruleW / 2f));
            g.fill(rx + i, ruleY, rx + i + 2, ruleY + 1, Draw.withAlpha(Theme.ACCENT, edge * edge * 0.9f * intro));
        }
        float travel = (Util.getMillis() % 5000) / 5000f;
        int lx = rx + Math.round(travel * ruleW);
        g.fill(lx - 6, ruleY, lx + 6, ruleY + 1, Draw.withAlpha(0xFFFFFFFF, 0.55f * intro));
        String tagline = "AN RPG ADVENTURE FOR MINECRAFT";
        g.centeredText(font, tagline, width / 2, ruleY + 6, Draw.withAlpha(Theme.MUTED, intro));
    }

    private void drawNews(GuiGraphicsExtractor g, float intro) {
        int w = 150;
        int x = width - w - 12;
        int y = 12;
        List<NewsFeed.Item> items = NewsFeed.items();
        int h = 22;
        List<List<FormattedCharSequence>> bodies = new ArrayList<>();
        for (NewsFeed.Item item : items) {
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(item.text()), w - 16);
            if (lines.size() > 3) {
                lines = lines.subList(0, 3);
            }
            bodies.add(lines);
            h += 12 + lines.size() * 9 + 6;
        }
        h = Math.min(h, height - 30);
        float slide = (1 - intro) * 12;
        g.pose().pushMatrix();
        g.pose().translate(slide, 0);
        Draw.shadow(g, x, y, w, h, 6, 0.5f);
        Draw.panel(g, x, y, w, h, Draw.withAlpha(Theme.WINDOW, 0.92f), Theme.BORDER);
        g.fill(x + 2, y, x + w - 2, y + 1, Draw.withAlpha(Theme.ACCENT, 0.85f));
        Draw.text(g, font, "WHAT'S NEW", x + 8, y + 8, Theme.ACCENT);
        if (NewsFeed.updateAvailable()) {
            Draw.rightText(g, font, "update available", x + w - 8, y + 8, Theme.GREEN);
        }
        int cy = y + 22;
        for (int i = 0; i < items.size(); i++) {
            NewsFeed.Item item = items.get(i);
            if (cy + 12 > y + h) {
                break;
            }
            Draw.fitText(g, font, item.title(), x + 8, cy, w - 16 - (item.date().isEmpty() ? 0 : 44), Theme.TEXT);
            if (!item.date().isEmpty()) {
                Draw.rightText(g, font, item.date().length() > 5 ? item.date().substring(5) : item.date(), x + w - 8, cy, Theme.DIM);
            }
            cy += 11;
            for (FormattedCharSequence line : bodies.get(i)) {
                if (cy + 8 > y + h - 2) {
                    break;
                }
                g.text(font, line, x + 8, cy, Theme.MUTED, false);
                cy += 9;
            }
            cy += 6;
        }
        g.pose().popMatrix();
    }

    private void drawFooter(GuiGraphicsExtractor g, float intro) {
        int y = height - 12;
        String left = "SkyRPG " + PackInfo.packVersion();
        Draw.text(g, font, left, 8, y, Draw.withAlpha(Theme.DIM, intro));
        String right = "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        Draw.rightText(g, font, right, width - 8, y, Draw.withAlpha(Theme.DIM, intro));
    }
}
