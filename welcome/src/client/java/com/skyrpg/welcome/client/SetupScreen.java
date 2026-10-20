package com.skyrpg.welcome.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * The first-time setup: Welcome, Performance, Visuals, Controls, Interface, Done. Shown once on the first start and
 * from the title screen, the K menu settings or {@code /skyrpg_setup} later. Every choice is applied right away and saved,
 * so closing the screen at any point leaves the game in a consistent state.
 */
final class SetupScreen extends Screen {
    private enum Step {
        WELCOME("Welcome to SkyRPG", "A quick setup, about a minute. Everything can be changed later."),
        PERFORMANCE("Performance", "Pick what fits your computer. You can fine-tune it in the video settings."),
        VISUALS("Visuals", "Shaders and resource packs found in your game folder."),
        CONTROLS("Controls", "Click a key, then press the new one. Esc cancels, Backspace clears."),
        INTERFACE("Interface", "The settings that matter most. They stay in sync with K > Settings."),
        DONE("You are ready", "Here is what you set up.");

        final String title;
        final String subtitle;

        Step(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final String[] INTERFACE_SETTINGS = {"chat.theme", "chat.message_spacing", "hud.enabled", "utils.minimap",
            "interface.tab_list", "welcome.title_screen"};

    private final Screen parent;
    private final boolean firstRun;
    private final long openedAt = Util.getMillis();
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<AbstractWidget> chrome = new ArrayList<>();
    private final List<SkyButton> presetCards = new ArrayList<>();
    private Step step = Step.WELCOME;
    private long stepStart = Util.getMillis();
    private int direction = 1;
    private Preset chosen;
    private String shaderChoice;
    private boolean shadersTouched;
    private boolean packsDirty;
    private int packsToggled;
    private int keysChanged;
    private KeyMapping listening;
    private int shaderScroll;
    private int packScroll;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int columnW;

    SetupScreen(Screen parent, boolean firstRun) {
        super(Component.literal("SkyRPG Setup"));
        this.parent = parent;
        this.firstRun = firstRun;
    }

    /** Opens the wizard on a given step name (used by the dev capture). */
    SetupScreen at(int index) {
        step = Step.values()[Math.floorMod(index, Step.values().length)];
        return this;
    }

    static int stepCount() {
        return Step.values().length;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        content.clear();
        chrome.clear();
        presetCards.clear();
        cardW = Math.min(width - 12, 420);
        cardH = Math.min(height - 12, 258);
        cardX = (width - cardW) / 2;
        cardY = (height - cardH) / 2;
        cx = cardX + 14;
        cy = cardY + 46;
        cw = cardW - 28;
        ch = cardH - 46 - 36;
        columnW = (cw - 10) / 2;
        buildChrome();
        switch (step) {
            case PERFORMANCE -> buildPerformance();
            case VISUALS -> buildVisuals();
            case CONTROLS -> buildControls();
            case INTERFACE -> buildInterface();
            case DONE -> buildDone();
            default -> { }
        }
    }

    private <T extends AbstractWidget> T content(T widget) {
        content.add(widget);
        return addWidget(widget);
    }

    private void buildChrome() {
        int y = cardY + cardH - 30;
        int index = step.ordinal();
        SkyButton back = new SkyButton(font, SkyButton.Style.GHOST, cx, y, 56, 20, Component.literal("Back"), () -> goTo(step.ordinal() - 1));
        back.active = index > 0;
        chrome.add(addWidget(back));
        boolean last = step == Step.DONE;
        int nextW = last ? 96 : 78;
        SkyButton next = new SkyButton(font, SkyButton.Style.PRIMARY, cardX + cardW - 14 - nextW, y, nextW, 20,
                Component.literal(last ? "Finish" : "Next"), last ? this::finish : () -> goTo(step.ordinal() + 1));
        chrome.add(addWidget(next));
        if (!last && !firstRun) {
            chrome.add(addWidget(new SkyButton(font, SkyButton.Style.GHOST, cardX + cardW - 14 - nextW - 84, y, 80, 20,
                    Component.literal("Close"), this::finish)));
        }
        if (getFocused() == null) {
            setInitialFocus(next);
        }
    }

    private void goTo(int index) {
        if (index < 0 || index >= Step.values().length) {
            return;
        }
        leaveStep();
        direction = index > step.ordinal() ? 1 : -1;
        step = Step.values()[index];
        stepStart = Util.getMillis();
        listening = null;
        clearFocus();
        rebuildWidgets();
    }

    /** Things that are better done once when a step is left (reloading the resource packs is slow). */
    private void leaveStep() {
        if (packsDirty) {
            packsDirty = false;
            minecraft.options.updateResourcePacks(minecraft.getResourcePackRepository());
        }
    }

    private void finish() {
        leaveStep();
        minecraft.options.save();
        WelcomeConfig.setupDone(true);
        minecraft.gui.setScreen(parent);
    }

    /** On the first start the setup cannot be closed: every step has to be gone through to "Finish". */
    @Override
    public void onClose() {
        if (!firstRun) {
            finish();
        }
    }

    // ------------------------------------------------------------------ steps: building

    private void buildPerformance() {
        if (firstRun && chosen == null) {
            // Going straight through with Next still leaves the game tuned: the suggestion for this computer is applied.
            chosen = Preset.suggested();
            chosen.apply(minecraft);
        }
        Preset[] presets = Preset.values();
        int gap = 6;
        int w = (cw - gap * 3) / 4;
        int h = ch >= 150 ? 44 : 40;
        for (int i = 0; i < presets.length; i++) {
            Preset preset = presets[i];
            SkyButton card = new SkyButton(font, SkyButton.Style.CARD, cx + i * (w + gap), cy, w, h, preset.title(), () -> {
                chosen = preset;
                preset.apply(minecraft);
            }).accent(preset.color).selected(() -> chosen == preset).description(Component.literal(tagline(preset)));
            presetCards.add(content(card));
        }
    }

    private static String tagline(Preset preset) {
        return switch (preset) {
            case POTATO -> "Old laptops";
            case BALANCED -> "Most computers";
            case HIGH -> "Gaming PCs";
            case ULTRA -> "Strong GPUs";
        };
    }

    private void buildVisuals() {
        minecraft.getResourcePackRepository().reload();
        int rowH = 18;
        int fit = Math.max(1, (ch - 18) / (rowH + 2));
        // Shaders (left column).
        if (ModCompat.irisLoaded()) {
            List<String> packs = ModCompat.shaderPacks();
            if (shaderChoice == null) {
                shaderChoice = ModCompat.shadersEnabled() ? ModCompat.shaderPack() : "";
            }
            if (!packs.isEmpty()) {
                List<String> options = new ArrayList<>();
                options.add("");
                options.addAll(packs);
                shaderScroll = Math.max(0, Math.min(shaderScroll, Math.max(0, options.size() - fit)));
                int y = cy + 16;
                for (int i = shaderScroll; i < Math.min(options.size(), shaderScroll + fit); i++) {
                    String option = options.get(i);
                    Component label = Component.literal(option.isEmpty() ? "No shaders" : ModCompat.prettyShaderName(option));
                    content(new SkyButton(font, SkyButton.Style.NORMAL, cx, y, columnW, rowH, label, () -> chooseShader(option))
                            .selected(() -> shaderChoice.equals(option)));
                    y += rowH + 2;
                }
            } else {
                content(new SkyButton(font, SkyButton.Style.NORMAL, cx, cy + 16 + 30, columnW, 18, Component.literal("Open shaderpacks folder"),
                        () -> openFolder("shaderpacks")));
            }
        }
        // Resource packs (right column).
        List<Pack> packs = userPacks();
        int rx = cx + columnW + 10;
        if (!packs.isEmpty()) {
            packScroll = Math.max(0, Math.min(packScroll, Math.max(0, packs.size() - (fit - 1))));
            PackRepository repository = minecraft.getResourcePackRepository();
            int y = cy + 16;
            int rows = Math.min(packs.size(), packScroll + fit - 1);
            for (int i = packScroll; i < rows; i++) {
                Pack pack = packs.get(i);
                String id = pack.getId();
                content(SkyRow.toggle(font, rx, y, columnW, 20, pack.getTitle(), pack.getDescription(), () -> repository.getSelectedIds().contains(id), on -> {
                    if (on) {
                        repository.addPack(id);
                    } else {
                        repository.removePack(id);
                    }
                    packsDirty = true;
                    packsToggled++;
                }));
                y += 22;
            }
        }
        content(new SkyButton(font, SkyButton.Style.GHOST, rx, cy + ch - 14, columnW, 14, Component.literal("Open resourcepacks folder"),
                () -> openFolder("resourcepacks")));
    }

    private List<Pack> userPacks() {
        List<Pack> list = new ArrayList<>();
        for (Pack pack : minecraft.getResourcePackRepository().getAvailablePacks()) {
            if (pack.getId().startsWith("file/") && !pack.isRequired()) {
                list.add(pack);
            }
        }
        list.sort((a, b) -> a.getTitle().getString().compareToIgnoreCase(b.getTitle().getString()));
        return list;
    }

    /** Used by the dev capture to drive the wizard. */
    void choosePreset(Preset preset) {
        chosen = preset;
        preset.apply(minecraft);
    }

    void chooseShader(String pack) {
        shaderChoice = pack;
        shadersTouched = true;
        ModCompat.setShaders(!pack.isEmpty(), pack);
    }

    private void openFolder(String name) {
        Path folder = minecraft.gameDirectory.toPath().resolve(name);
        try {
            Files.createDirectories(folder);
            Ui.openFolder(folder);
        } catch (java.io.IOException | RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't open {}", folder, e);
        }
    }

    private List<KeyMapping> keyList() {
        List<KeyMapping> keys = new ArrayList<>();
        for (String name : new String[]{"key.skyrpg_core.menu", "key.skyrpg_utils.map", "key.skyrpg_items.backpack", "key.skyrpg_wiki.lookup", "key.skyrpg_magic.wheel"}) {
            KeyMapping key = KeyMapping.get(name);
            if (key != null) {
                keys.add(key);
            }
        }
        if (ModCompat.loaded("zoomify")) {
            for (KeyMapping key : minecraft.options.keyMappings) {
                if (key.getName().toLowerCase(Locale.ROOT).contains("zoomify") && key.getName().toLowerCase(Locale.ROOT).endsWith("zoom")) {
                    keys.add(key);
                    break;
                }
            }
        }
        keys.add(minecraft.options.keyUp);
        keys.add(minecraft.options.keyJump);
        keys.add(minecraft.options.keySprint);
        keys.add(minecraft.options.keyInventory);
        return keys;
    }

    private void buildControls() {
        List<KeyMapping> keys = keyList();
        int perColumn = (keys.size() + 1) / 2;
        int rowH = 20;
        for (int i = 0; i < keys.size(); i++) {
            KeyMapping key = keys.get(i);
            int col = i / perColumn;
            int row = i % perColumn;
            content(SkyRow.key(font, cx + col * (columnW + 10), cy + row * (rowH + 2), columnW, rowH, Component.translatable(key.getName()),
                    () -> key.isUnbound() ? "not set" : key.getTranslatedKeyMessage().getString(), () -> conflicts(key), () -> listening == key,
                    () -> listening = listening == key ? null : key));
        }
        int y = cy + perColumn * (rowH + 2) + 4;
        content(new SkyButton(font, SkyButton.Style.NORMAL, cx + (cw - 220) / 2, Math.min(y, cy + ch - 18), 220, 18,
                Component.literal("Open all key bindings"), () -> minecraft.gui.setScreen(new ControlsScreen(this, minecraft.options))));
    }

    private boolean conflicts(KeyMapping key) {
        if (key.isUnbound()) {
            return false;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other != key && !java.util.Arrays.asList(minecraft.options.debugKeys).contains(other) && key.same(other)) {
                return true;
            }
        }
        return false;
    }

    private void bind(KeyMapping key, InputConstants.Key value) {
        key.setKey(value);
        KeyMapping.resetMapping();
        minecraft.options.save();
        keysChanged++;
        listening = null;
    }

    private void buildInterface() {
        int y = cy;
        int rowH = ch >= 150 ? 22 : 20;
        for (String id : INTERFACE_SETTINGS) {
            SkyRow row = SkyRow.forSetting(font, id, cx, y, cw, rowH);
            if (row != null) {
                content(row);
                y += rowH + 2;
            }
        }
    }

    private void buildDone() {
        int w = Math.min(220, cw);
        String address = WelcomeConfig.serverAddress();
        content(new SkyButton(font, SkyButton.Style.PRIMARY, cx + (cw - w) / 2, cy + ch - 28, w, 26,
                Component.literal("Play " + WelcomeConfig.serverName()), () -> {
                    finishForPlay(address);
                }));
    }

    private void finishForPlay(String address) {
        leaveStep();
        minecraft.options.save();
        WelcomeConfig.setupDone(true);
        if (address.isEmpty()) {
            minecraft.gui.setScreen(parent);
        } else {
            SkyRpgWelcomeClient.joinServer(parent, address);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            extractPanorama(g, partialTick);
            Backdrop.draw(g, width, height, 1f);
        } else {
            extractBlurredBackground(g);
            Backdrop.draw(g, width, height, 0.8f);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Draw.clearQueuedTooltip();
        float intro = Draw.easeOut((Util.getMillis() - openedAt) / 300f);
        int rise = Math.round((1 - intro) * 12);
        g.pose().pushMatrix();
        g.pose().translate(0, rise);
        int my = mouseY - rise;
        Draw.shadow(g, cardX, cardY, cardW, cardH, 10, 0.6f * intro);
        Draw.panel(g, cardX, cardY, cardW, cardH, Theme.WINDOW, Theme.BORDER);
        g.fill(cardX + 2, cardY, cardX + cardW - 2, cardY + 1, Draw.withAlpha(Theme.ACCENT, 0.9f));
        drawHeader(g);
        g.fill(cardX + 1, cardY + cardH - 36, cardX + cardW - 1, cardY + cardH - 2, Theme.FOOTER);
        g.fill(cardX + 1, cardY + cardH - 36, cardX + cardW - 1, cardY + cardH - 35, Theme.BORDER);

        // Step content slides in from the side it was reached from, over a fade.
        float t = Draw.easeOut((Util.getMillis() - stepStart) / 240f);
        int shift = Math.round((1 - t) * 16 * direction);
        g.enableScissor(cardX + 1, cardY + 41, cardX + cardW - 1, cardY + cardH - 36);
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        drawContent(g, mouseX - shift, my);
        for (AbstractWidget widget : content) {
            widget.extractRenderState(g, mouseX - shift, my, partialTick);
        }
        g.pose().popMatrix();
        g.fill(cardX + 1, cardY + 41, cardX + cardW - 1, cardY + cardH - 36, Draw.withAlpha(Theme.WINDOW, 1 - t));
        g.disableScissor();
        for (AbstractWidget widget : chrome) {
            widget.extractRenderState(g, mouseX, my, partialTick);
        }
        g.pose().popMatrix();
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY, width, height);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        int x = cardX + 14;
        Draw.fit(g, font, Component.literal(step.title), x, cardY + 10, cardW - 28 - 100, 1.5f, 1f, Draw.Align.LEFT, Theme.TEXT, false);
        Draw.fitText(g, font, step.subtitle, x, cardY + 27, cardW - 28, Theme.MUTED);
        g.fill(cardX + 1, cardY + 40, cardX + cardW - 1, cardY + 41, Theme.BORDER);
        // Step dots, the current one a gold pill.
        int count = Step.values().length;
        int dx = cardX + cardW - 14;
        for (int i = count - 1; i >= 0; i--) {
            boolean current = i == step.ordinal();
            int w = current ? 14 : 5;
            dx -= w;
            Draw.rounded(g, dx, cardY + 13, w, 5, current ? Theme.ACCENT : i < step.ordinal() ? Draw.withAlpha(Theme.ACCENT, 0.5f) : Theme.TRACK);
            dx -= 4;
        }
    }

    private int paragraph(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : Draw.wrap(font, Component.literal(text), w)) {
            g.text(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        switch (step) {
            case WELCOME -> drawWelcome(g);
            case PERFORMANCE -> drawPerformance(g);
            case VISUALS -> drawVisuals(g);
            case DONE -> drawDone(g);
            default -> { }
        }
    }

    private void drawWelcome(GuiGraphicsExtractor g) {
        int y = paragraph(g, "SkyRPG turns Minecraft into an RPG: level skills, fight bosses, run dungeons and collect pets. "
                + "This quick setup tunes the game for your computer.", cx, cy, cw, Theme.TEXT);
        y += 8;
        int gap = 8;
        int w = (cw - gap * 2) / 3;
        int h = Math.max(46, Math.min(70, ch - 90));
        String[] titles = {"Skills", "Combat", "Companions"};
        String[] subs = {"Mine, farm, fish, chop and level up", "Bosses, slayers and dungeons", "Pets and mounts that grow with you"};
        int[] colors = {Theme.AQUA, Theme.RED, Theme.PURPLE};
        for (int i = 0; i < 3; i++) {
            int x = cx + i * (w + gap);
            Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
            g.fill(x + 2, y, x + w - 2, y + 1, Draw.withAlpha(colors[i], 0.8f));
            Draw.text(g, font, titles[i], x + 8, y + 10, colors[i]);
            int ly = y + 24;
            for (FormattedCharSequence line : Draw.wrap(font, Component.literal(subs[i]), w - 16)) {
                if (ly + 8 > y + h - 2) {
                    break;
                }
                g.text(font, line, x + 8, ly, Theme.MUTED, false);
                ly += 9;
            }
        }
        y += h + 10;
        if (y + 10 < cy + ch) {
            paragraph(g, "Reopen this any time from the title screen or K > Settings > Interface.", cx, y, cw, Theme.DIM);
        }
    }

    private void drawPerformance(GuiGraphicsExtractor g) {
        Preset focus = chosen != null ? chosen : Preset.suggested();
        for (int i = 0; i < presetCards.size(); i++) {
            if (presetCards.get(i).isHovered()) {
                focus = Preset.values()[i];
            }
        }
        int top = cy + (ch >= 150 ? 44 : 40) + 8;
        int h = cy + ch - top - 12;
        Draw.panel(g, cx, top, cw, h, Theme.CARD, Theme.CARD_BORDER);
        Draw.text(g, font, Component.literal(focus.label).withColor(Theme.ACCENT), cx + 8, top + 7, Theme.ACCENT);
        int ly = paragraph(g, focus.description, cx + 8, top + 19, cw - 16, Theme.MUTED);
        String[][] facts = {
                {"View distance", focus.renderDistance + " chunks"},
                {"Simulation", focus.simulationDistance + " chunks"},
                {"Graphics", capital(focus.graphics.name())},
                {"Particles", capital(focus.particles.name())},
                {"Clouds", capital(focus.clouds.name())},
                {"Entity distance", Math.round(focus.entityDistance * 100) + "%"},
                {"Max FPS", focus.maxFps >= 260 ? "Unlimited" : Integer.toString(focus.maxFps)},
                {"VSync", focus.vsync ? "On" : "Off"},
        };
        int colW = (cw - 16) / 2;
        int fy = Math.max(ly + 2, top + 19 + 20);
        for (int i = 0; i < facts.length; i++) {
            int x = cx + 8 + (i % 2) * colW;
            int y = fy + (i / 2) * 10;
            if (y + 8 > top + h - 2) {
                break;
            }
            Draw.text(g, font, facts[i][0], x, y, Theme.DIM);
            Draw.text(g, font, facts[i][1], x + 78, y, Theme.TEXT);
        }
        Preset suggested = Preset.suggested();
        String hint = "Suggested for this PC: " + suggested.label + "  (" + Preset.machineSummary() + ")";
        int hintY = cy + ch - 10;
        Draw.fitText(g, font, Component.literal(chosen == null ? hint : "Applied: " + chosen.label + ". " + hint).withColor(Theme.MUTED), cx, hintY, cw, Theme.MUTED);
    }

    private static String capital(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private void drawVisuals(GuiGraphicsExtractor g) {
        int rx = cx + columnW + 10;
        Draw.text(g, font, "SHADERS", cx, cy + 2, Theme.ACCENT);
        Draw.text(g, font, "RESOURCE PACKS", rx, cy + 2, Theme.ACCENT);
        g.fill(cx, cy + 12, cx + columnW, cy + 13, Theme.BORDER);
        g.fill(rx, cy + 12, rx + columnW, cy + 13, Theme.BORDER);
        if (!ModCompat.irisLoaded()) {
            paragraph(g, "Shaders need the Iris mod, which is not installed here. Install it to use shader packs.", cx, cy + 20, columnW, Theme.DIM);
        } else if (ModCompat.shaderPacks().isEmpty()) {
            paragraph(g, "No shader packs found. Drop shader pack .zip files into the shaderpacks folder, then reopen this step.", cx, cy + 20, columnW, Theme.DIM);
        }
        if (userPacks().isEmpty()) {
            paragraph(g, "No resource packs found. Put .zip packs into the resourcepacks folder to see them here.", rx, cy + 20, columnW, Theme.DIM);
        }
    }

    private void drawDone(GuiGraphicsExtractor g) {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Performance", chosen != null ? chosen.label + " preset" : "left as it was"});
        String shaders = !ModCompat.irisLoaded() ? "not installed" : !shadersTouched ? "left as it was" : shaderChoice.isEmpty() ? "off" : ModCompat.prettyShaderName(shaderChoice);
        lines.add(new String[]{"Shaders", shaders});
        lines.add(new String[]{"Resource packs", packsToggled == 0 ? "left as they were" : packsToggled + " changed"});
        lines.add(new String[]{"Key bindings", keysChanged == 0 ? "left as they were" : keysChanged + " changed"});
        int y = cy + 2;
        for (String[] line : lines) {
            Draw.text(g, font, line[0], cx + 4, y, Theme.DIM);
            Draw.fitText(g, font, line[1], cx + 100, y, cw - 104, Theme.TEXT);
            y += 12;
        }
        y += 6;
        if (!WelcomeConfig.serverAddress().isEmpty()) {
            paragraph(g, "Play joins " + WelcomeConfig.serverName() + " at " + WelcomeConfig.serverAddress() + ". Ask the host to whitelist your name first.",
                    cx + 4, y, cw - 8, Theme.MUTED);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                listening = null;
            } else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                bind(listening, InputConstants.UNKNOWN);
            } else {
                bind(listening, InputConstants.getKey(event));
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (!firstRun) {
                finish();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // a mouse button other than left or right binds the key being changed (26.3 numbers buttons from 1)
        if (listening != null && event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
            bind(listening, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        if (listening != null) {
            // A click somewhere else cancels; a click on the row itself toggles it (handled by the row).
            KeyMapping before = listening;
            boolean handled = super.mouseClicked(event, doubleClick);
            if (listening == before) {
                listening = null;
            }
            return handled;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (step == Step.VISUALS) {
            int delta = scrollY > 0 ? -1 : 1;
            if (mouseX < cx + columnW + 5) {
                shaderScroll = Math.max(0, shaderScroll + delta);
            } else {
                packScroll = Math.max(0, packScroll + delta);
            }
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        leaveStep();
    }

    /** For the dev capture: the settings that must exist for the interface step. */
    static boolean hasInterfaceSettings() {
        for (String id : INTERFACE_SETTINGS) {
            Setting s = SkyRpgSettings.find(id);
            if (s != null) {
                return true;
            }
        }
        return false;
    }
}
