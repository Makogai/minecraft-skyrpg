package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.builder.Library;
import com.skyrpg.builder.Schematics;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Choosing a blueprint. "Library": everything on the server (uploads and Minecraft's village houses, igloo, outpost
 * and ruined portals). "My schematics": the files in this game's {@code schematics} folder (where Litematica saves),
 * read here first so their card shows the build (or why it can't be read), then uploaded when picked. Two views:
 * cards with a turning preview of each build, or a compact list.
 */
final class BlueprintScreen extends Screen {
    private static final int ROW = 28;
    private static final int CARD_H = 142;
    private static final int PREVIEW_H = 86;
    private static final int GAP = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    /** Remembered while the game runs: cards (true) or the list, and the last tab. */
    private static boolean cards = true;
    private static int lastTab;

    private final BuilderScreen parent;
    private int tab = lastTab;
    private List<Library.Info> library;
    private String me = "";
    private List<LocalFile> files = List.of();
    private final Set<String> requested = new HashSet<>();
    private EditBox search;
    private int left;
    private int top;
    private int w;
    private int h;
    private double scroll;
    private int contentHeight;
    private String status = "";
    private int statusColor = Theme.MUTED;
    private boolean uploading;
    private int uploadSession;
    private String uploadingName = "";
    private int listTop;
    private int listHeight;
    private final List<Hit> hits = new ArrayList<>();

    private record LocalFile(Path path, String name, String folder, long size, long modified) {
    }

    /** Something shown as a card or a row. */
    private record Entry(String key, String category, String name, String detail, String extra, boolean current, Runnable pick, Runnable delete,
                         Supplier<Blueprint> source, boolean slowSource) {
    }

    /** Where an entry was drawn this frame, and its delete button if it has one. */
    private record Hit(int x, int y, int w, int h, Entry entry, int deleteX, int deleteY, int deleteW, int deleteH) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean overDelete(double mx, double my) {
            return entry.delete() != null && mx >= deleteX && mx < deleteX + deleteW && my >= deleteY && my < deleteY + deleteH;
        }
    }

    BlueprintScreen(BuilderScreen parent) {
        super(Component.literal("Blueprints"));
        this.parent = parent;
    }

    BlockPos station() {
        return parent.station();
    }

    @Override
    protected void init() {
        w = Math.min(cards ? 580 : 440, width - 16);
        h = Math.min(cards ? 360 : 310, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, left + 10, top + 52, w - 20, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search blueprints...").withColor(Theme.DIM));
        search.setValue(previous);
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        if (library == null) {
            ClientPlayNetworking.send(BuilderPayloads.Action.of(parent.station(), "library"));
        }
        files = scanFiles();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        lastTab = tab;
        minecraft.gui.setScreen(parent);
    }

    void parentUpdate(BuilderPayloads.Menu menu) {
        parent.update(menu);
    }

    void library(BuilderPayloads.LibraryList list) {
        library = list.entries();
        me = list.player();
    }

    void uploaded(BuilderPayloads.UploadResult result) {
        uploading = false;
        if (result.ok()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f));
            chooseAndPlace(result.id(), uploadingName);
        } else {
            status = result.message();
            statusColor = Theme.RED;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f, 0.6f));
        }
    }

    /** Loads the blueprint into the station and goes straight to placing it in the world. */
    private void chooseAndPlace(String id, String name) {
        ClientPlayNetworking.send(new BuilderPayloads.Action(parent.station(), "choose", 0, 0, 0, id));
        ClientPlayNetworking.send(BuilderPayloads.Action.of(parent.station(), "close"));
        lastTab = tab;
        PlacementMode.start(parent.station(), id, name, 0, false, null);
    }

    static Path folder() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    private static List<LocalFile> scanFiles() {
        Path root = folder();
        List<LocalFile> found = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (Stream<Path> walk = Files.walk(root, 4)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    String name = path.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!(lower.endsWith(".litematic") || lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".nbt"))) {
                        continue;
                    }
                    Path parentDir = root.relativize(path).getParent();
                    found.add(new LocalFile(path, name, parentDir == null ? "" : parentDir.toString().replace('\\', '/'), Files.size(path),
                            Files.getLastModifiedTime(path).toMillis()));
                }
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        found.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return found;
    }

    // ------------------------------------------------------------------ entries

    private boolean matches(String... texts) {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }
        for (String text : texts) {
            if (text.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        if (tab == 0) {
            if (library == null) {
                return entries;
            }
            for (Library.Info info : library) {
                if (!matches(info.name(), info.category(), info.author())) {
                    continue;
                }
                boolean upload = info.kind() == Blueprint.KIND_UPLOAD;
                boolean mine = upload && info.author().equals(me);
                String detail = info.sizeX() + " × " + info.sizeY() + " × " + info.sizeZ() + "  ·  " + String.format("%,d", info.blocks()) + " blocks";
                String id = info.id();
                entries.add(new Entry("lib:" + id, info.category(), info.name(), detail, upload ? "by " + info.author() : info.category(),
                        id.equals(parent.menu().blueprintId()),
                        () -> chooseAndPlace(id, info.name()),
                        mine ? () -> {
                            ClientPlayNetworking.send(new BuilderPayloads.Action(parent.station(), "delete", 0, 0, 0, id));
                            status = "Deleted " + info.name() + ".";
                            statusColor = Theme.MUTED;
                        } : null,
                        () -> {
                            Blueprint blueprint = Ghosts.BLUEPRINTS.get(id);
                            if (blueprint == null && requested.add(id)) {
                                ClientPlayNetworking.send(new BuilderPayloads.Action(parent.station(), "geometry", 0, 0, 0, id));
                            }
                            return blueprint;
                        }, false));
            }
        } else {
            for (LocalFile file : files) {
                if (!matches(file.name(), file.folder())) {
                    continue;
                }
                String extra = (file.folder().isEmpty() ? "" : file.folder() + "  ·  ") + kb(file.size()) + "  ·  "
                        + DATE.format(Instant.ofEpochMilli(file.modified()));
                entries.add(new Entry("file:" + file.path() + ":" + file.modified(), "", file.name(), "", extra, false, () -> upload(file), null,
                        () -> readLocal(file), true));
            }
        }
        return entries;
    }

    /** Reads a local schematic for its card (off the render thread). Errors carry the reason to show. */
    private static Blueprint readLocal(LocalFile file) {
        if (file.size() > Library.MAX_UPLOAD_BYTES) {
            throw new IllegalStateException("Too big (the most is 2 MB).");
        }
        try {
            return Schematics.read(Files.readAllBytes(file.path()), file.name(), "local", "", Blueprint.KIND_UPLOAD).blueprint();
        } catch (Schematics.BadFile e) {
            throw new IllegalStateException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read the file.");
        }
    }

    private static String kb(long bytes) {
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB" : String.format("%.1f MB", bytes / 1048576.0);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x9006121A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Draw.shadow(g, left, top, w, h, 6, 0.6f);
        Draw.panel(g, left, top, w, h, Theme.WINDOW, 0xFF1F4A55);
        Draw.fit(g, font, Component.literal("CHOOSE A BLUEPRINT"), left + 10, top + 10, w - 160, Draw.Align.LEFT, BuilderScreen.CYAN);
        drawTabs(g, mouseX, mouseY);
        drawViewSwitch(g, mouseX, mouseY);
        if (cards) {
            int[] box = styleBox();
            boolean over = mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
            boolean real = Previews.realStyle;
            Draw.panel(g, box[0], box[1], box[2], box[3], real ? 0xFF132A30 : over ? Theme.CARD_HOVER : Theme.CARD, real ? BuilderScreen.CYAN : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(real ? "Real" : "Simple"), box[0] + box[2] / 2, box[1] + 5, box[2], Draw.Align.CENTER,
                    real ? BuilderScreen.CYAN : Theme.MUTED);
            if (over) {
                Draw.queueTooltip(List.of(Component.literal("Previews: " + (real ? "real blocks" : "simple cubes")),
                        Component.literal("Click to switch").withColor(Theme.MUTED)), BuilderScreen.CYAN);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        listTop = top + 74;
        listHeight = h - 74 - 30;
        hits.clear();
        List<Entry> entries = entries();
        g.enableScissor(left + 4, listTop, left + w - 4, listTop + listHeight);
        contentHeight = cards ? drawCards(g, entries, mouseX, mouseY) : drawList(g, entries, mouseX, mouseY);
        g.disableScissor();
        if (contentHeight > listHeight) {
            Draw.scrollbar(g, left + w - 6, listTop, listHeight, contentHeight, scroll, BuilderScreen.CYAN);
        }
        if (entries.isEmpty()) {
            drawEmpty(g);
        }
        drawFooter(g, mouseX, mouseY);
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY, width, height);
    }

    private String[] tabNames() {
        return new String[]{"Library", "My schematics (" + files.size() + ")"};
    }

    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String[] names = tabNames();
        int tx = left + 10;
        for (int i = 0; i < names.length; i++) {
            int tw = font.width(names[i]) + 16;
            boolean active = tab == i;
            boolean over = mouseX >= tx && mouseX < tx + tw && mouseY >= top + 26 && mouseY < top + 44;
            Draw.panel(g, tx, top + 26, tw, 18, active ? 0xFF132A30 : over ? Theme.CARD_HOVER : Theme.CARD, active ? BuilderScreen.CYAN : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(names[i]), tx + tw / 2, top + 31, tw, Draw.Align.CENTER, active ? BuilderScreen.CYAN : Theme.TEXT);
            tx += tw + 4;
        }
    }

    private static final String[] VIEWS = {"Cards", "List"};

    /** "Cards | List" in the top right. */
    private void drawViewSwitch(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + w - 10;
        for (int i = VIEWS.length - 1; i >= 0; i--) {
            int bw = font.width(VIEWS[i]) + 14;
            x -= bw;
            boolean active = (i == 0) == cards;
            boolean over = mouseX >= x && mouseX < x + bw && mouseY >= top + 26 && mouseY < top + 44;
            Draw.panel(g, x, top + 26, bw, 18, active ? 0xFF2A2210 : over ? Theme.CARD_HOVER : Theme.CARD, active ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(VIEWS[i]), x + bw / 2, top + 31, bw, Draw.Align.CENTER, active ? Theme.ACCENT : Theme.MUTED);
            x -= 3;
        }
    }

    /** The Real / Simple preview style button, left of Cards | List; its box, for drawing and clicks. */
    private int[] styleBox() {
        int x = left + w - 10;
        for (String view : VIEWS) {
            x -= font.width(view) + 14 + 3;
        }
        int bw = font.width("Simple") + 14;
        return new int[]{x - bw - 6, top + 26, bw, 18};
    }

    private int viewSwitchAt(double mx, double my) {
        int x = left + w - 10;
        for (int i = VIEWS.length - 1; i >= 0; i--) {
            int bw = font.width(VIEWS[i]) + 14;
            x -= bw;
            if (mx >= x && mx < x + bw && my >= top + 26 && my < top + 44) {
                return i;
            }
            x -= 3;
        }
        return -1;
    }

    private void drawEmpty(GuiGraphicsExtractor g) {
        String[] lines;
        if (tab == 0) {
            lines = library == null ? new String[]{"Loading..."} : new String[]{"Nothing matches."};
        } else if (files.isEmpty()) {
            lines = new String[]{"No schematics here yet.", "Put .litematic, .schem or .nbt files in this game's", "\"schematics\" folder (Litematica saves there),",
                    "then come back. Up to 128 blocks on each side."};
        } else {
            lines = new String[]{"Nothing matches."};
        }
        for (int i = 0; i < lines.length; i++) {
            Draw.fit(g, font, Component.literal(lines[i]), left + w / 2, listTop + 16 + i * 12, w - 20, Draw.Align.CENTER, i == 0 ? Theme.TEXT : Theme.MUTED);
        }
    }

    private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int fy = top + h - 22;
        if (tab == 1) {
            int bw = font.width("Open folder") + 14;
            boolean over = mouseX >= left + 10 && mouseX < left + 10 + bw && mouseY >= fy && mouseY < fy + 16;
            Draw.panel(g, left + 10, fy, bw, 16, over ? Theme.CARD_HOVER : Theme.CARD, over ? BuilderScreen.CYAN : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal("Open folder"), left + 10 + bw / 2, fy + 4, bw, Draw.Align.CENTER, Theme.TEXT);
        }
        String footer = uploading ? "Uploading..." : status.isEmpty() ? (tab == 0 ? "Click a blueprint to load it · Esc: back"
                : "Click a schematic to upload and load it · Esc: back") : status;
        Draw.fit(g, font, Component.literal(footer), left + w - 10, fy + 4, w - (tab == 1 ? 110 : 20), Draw.Align.RIGHT,
                uploading ? BuilderScreen.CYAN : status.isEmpty() ? Theme.DIM : statusColor);
    }

    // ------------------------------------------------------------------ cards

    private int drawCards(GuiGraphicsExtractor g, List<Entry> entries, int mouseX, int mouseY) {
        int inner = w - 20;
        int columns = Math.max(1, (inner + GAP) / (120 + GAP));
        int cardW = (inner - (columns - 1) * GAP) / columns;
        int y = listTop - (int) Math.round(scroll);
        int start = y;
        int column = 0;
        String category = null;
        for (Entry entry : entries) {
            if (!entry.category().isEmpty() && !entry.category().equals(category)) {
                if (column > 0) {
                    y += CARD_H + GAP;
                    column = 0;
                }
                category = entry.category();
                if (y + 14 > listTop && y < listTop + listHeight) {
                    Draw.fit(g, font, Component.literal(category.toUpperCase(Locale.ROOT)), left + 12, y + 3, inner, Draw.Align.LEFT, Theme.ACCENT);
                }
                y += 15;
            }
            int x = left + 10 + column * (cardW + GAP);
            if (y + CARD_H > listTop && y < listTop + listHeight) {
                drawCard(g, entry, x, y, cardW, mouseX, mouseY);
            }
            column++;
            if (column == columns) {
                column = 0;
                y += CARD_H + GAP;
            }
        }
        if (column > 0) {
            y += CARD_H + GAP;
        }
        return y - start;
    }

    private void drawCard(GuiGraphicsExtractor g, Entry entry, int x, int y, int cardW, int mouseX, int mouseY) {
        boolean over = mouseX >= x && mouseX < x + cardW && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + CARD_H, listTop + listHeight);
        Draw.panel(g, x, y, cardW, CARD_H, over ? Theme.CARD_HOVER : Theme.CARD, entry.current() ? Theme.ACCENT : over ? BuilderScreen.CYAN : Theme.CARD_BORDER);
        // The preview box: blueprint paper with the build turning on it.
        int px = x + 4;
        int py = y + 4;
        int pw = cardW - 8;
        Draw.rounded(g, px, py, pw, PREVIEW_H, 0xFF0B1220);
        for (int gx = px + 6; gx < px + pw - 1; gx += 6) {
            g.fill(gx, py + 1, gx + 1, py + PREVIEW_H - 1, 0x0F5CA8FF);
        }
        for (int gy = py + 6; gy < py + PREVIEW_H - 1; gy += 6) {
            g.fill(px + 1, gy, px + pw - 1, gy + 1, 0x0F5CA8FF);
        }
        Previews.Preview preview = Previews.get(entry.key(), entry.source(), entry.slowSource());
        if (preview.state == Previews.State.READY) {
            Previews.draw(g, preview, px + 2, py + 2, pw - 4, PREVIEW_H - 4);
        } else if (preview.state == Previews.State.FAILED) {
            Draw.fit(g, font, Component.literal("Can't read it"), px + pw / 2, py + PREVIEW_H / 2 - 4, pw - 6, Draw.Align.CENTER, Theme.RED);
        } else {
            String dots = ".".repeat(1 + (int) ((Util.getMillis() / 300) % 3));
            Draw.fit(g, font, Component.literal(dots), px + pw / 2, py + PREVIEW_H / 2 - 4, pw - 6, Draw.Align.CENTER, Theme.DIM);
        }
        int ty = py + PREVIEW_H + 5;
        String detail = detail(entry, preview);
        boolean failed = preview.state == Previews.State.FAILED && tab == 1;
        Draw.fit(g, font, Component.literal(entry.name()), x + 7, ty, cardW - 14, Draw.Align.LEFT, entry.current() ? Theme.ACCENT : Theme.TEXT);
        Draw.fit(g, font, Component.literal(detail), x + 7, ty + 12, cardW - 14, Draw.Align.LEFT, failed ? Theme.RED : Theme.MUTED);
        Draw.fit(g, font, Component.literal(entry.extra()), x + 7, ty + 23, cardW - 14, Draw.Align.LEFT, Theme.DIM);
        int size = entry.delete() != null ? 12 : 0;
        int dx = x + cardW - size - 6;
        int dy = y + 6;
        boolean overDelete = size > 0 && mouseX >= dx && mouseX < dx + size && mouseY >= dy && mouseY < dy + size;
        if (size > 0 && over) {
            Draw.panel(g, dx, dy, size, size, overDelete ? 0xFF3A1A1E : 0xC0121620, overDelete ? Theme.RED : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal("×"), dx + size / 2 + 1, dy + 2, size, Draw.Align.CENTER, overDelete ? Theme.RED : Theme.MUTED);
        }
        int visibleTop = Math.max(y, listTop);
        hits.add(new Hit(x, visibleTop, cardW, Math.min(y + CARD_H, listTop + listHeight) - visibleTop, entry, dx, dy, size, size));
        if (overDelete) {
            Draw.queueTooltip(List.of(Component.literal("Delete this blueprint").withColor(Theme.RED)), Theme.RED);
        } else if (over) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(entry.name()));
            if (!detail.isEmpty()) {
                tip.add(Component.literal(detail).withColor(failed ? Theme.RED : Theme.MUTED));
            }
            tip.add(Component.literal(tab == 0 ? "Click to load it" : "Click to upload and load it").withColor(BuilderScreen.CYAN));
            Draw.queueTooltip(tip, BuilderScreen.CYAN);
        }
    }

    /** The size line: from the library entry, or for local files from reading them (or why that failed). */
    private String detail(Entry entry, Previews.Preview preview) {
        if (!entry.detail().isEmpty()) {
            return entry.detail();
        }
        if (preview.state == Previews.State.FAILED) {
            return preview.error;
        }
        Blueprint blueprint = preview.blueprint;
        if (blueprint == null) {
            return "Reading...";
        }
        return blueprint.sizeText() + "  ·  " + String.format("%,d", blueprint.count()) + " blocks";
    }

    // ------------------------------------------------------------------ list

    private int drawList(GuiGraphicsExtractor g, List<Entry> entries, int mouseX, int mouseY) {
        int y = listTop - (int) Math.round(scroll);
        int start = y;
        String category = null;
        for (Entry entry : entries) {
            if (!entry.category().isEmpty() && !entry.category().equals(category)) {
                category = entry.category();
                if (y + 14 > listTop && y < listTop + listHeight) {
                    Draw.fit(g, font, Component.literal(category.toUpperCase(Locale.ROOT)), left + 12, y + 4, w - 24, Draw.Align.LEFT, Theme.ACCENT);
                }
                y += 16;
            }
            if (y + ROW > listTop && y < listTop + listHeight) {
                drawRow(g, entry, y, mouseX, mouseY);
            }
            y += ROW;
        }
        return y - start;
    }

    private void drawRow(GuiGraphicsExtractor g, Entry entry, int y, int mouseX, int mouseY) {
        int x = left + 8;
        int rw = w - 18;
        int visibleTop = Math.max(y, listTop);
        boolean over = mouseX >= x && mouseX < x + rw && mouseY >= visibleTop && mouseY < Math.min(y + ROW - 3, listTop + listHeight);
        Draw.panel(g, x, y, rw, ROW - 3, over ? Theme.CARD_HOVER : Theme.CARD, entry.current() ? Theme.ACCENT : over ? BuilderScreen.CYAN : Theme.CARD_BORDER);
        int deleteW = entry.delete() != null ? font.width("Delete") + 10 : 0;
        Draw.fit(g, font, Component.literal(entry.name()), x + 8, y + 4, rw - 16 - deleteW, Draw.Align.LEFT, entry.current() ? Theme.ACCENT : Theme.TEXT);
        String detail = entry.detail().isEmpty() ? entry.extra() : entry.detail() + (entry.extra().isEmpty() ? "" : "  ·  " + entry.extra());
        Draw.fit(g, font, Component.literal(detail), x + 8, y + 14, rw - 16 - deleteW, Draw.Align.LEFT, Theme.DIM);
        int dx = x + rw - deleteW - 4;
        boolean overDelete = deleteW > 0 && over && mouseX >= dx;
        if (deleteW > 0 && over) {
            Draw.fit(g, font, Component.literal("Delete"), x + rw - 6, y + 9, deleteW, Draw.Align.RIGHT, overDelete ? Theme.RED : Theme.DIM);
        }
        hits.add(new Hit(x, visibleTop, rw, Math.min(y + ROW - 3, listTop + listHeight) - visibleTop, entry, dx, y, deleteW + 4, ROW - 3));
    }

    // ------------------------------------------------------------------ uploading

    private void upload(LocalFile file) {
        if (uploading) {
            return;
        }
        if (file.size() > Library.MAX_UPLOAD_BYTES) {
            status = "That file is too big (the most is 2 MB).";
            statusColor = Theme.RED;
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.path());
        } catch (IOException e) {
            status = "Couldn't read that file.";
            statusColor = Theme.RED;
            return;
        }
        status = "";
        uploading = true;
        uploadingName = Schematics.cleanName(Schematics.baseName(file.name()));
        uploadSession++;
        int count = Math.max(1, (bytes.length + Library.CHUNK_BYTES - 1) / Library.CHUNK_BYTES);
        for (int i = 0; i < count; i++) {
            int from = i * Library.CHUNK_BYTES;
            int to = Math.min(bytes.length, from + Library.CHUNK_BYTES);
            ClientPlayNetworking.send(new BuilderPayloads.UploadChunk(uploadSession, i, count, file.name(), java.util.Arrays.copyOfRange(bytes, from, to)));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        String[] names = tabNames();
        int tx = left + 10;
        for (int i = 0; i < names.length; i++) {
            int tw = font.width(names[i]) + 16;
            if (mx >= tx && mx < tx + tw && my >= top + 26 && my < top + 44) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                    status = "";
                    if (i == 1) {
                        files = scanFiles();
                    }
                    click();
                }
                return true;
            }
            tx += tw + 4;
        }
        if (cards) {
            int[] box = styleBox();
            if (mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
                BuilderSettings.toggleStyle();
                click();
                return true;
            }
        }
        int view = viewSwitchAt(mx, my);
        if (view >= 0) {
            if ((view == 0) != cards) {
                cards = view == 0;
                scroll = 0;
                click();
                rebuildWidgets();
            }
            return true;
        }
        if (tab == 1) {
            int bw = font.width("Open folder") + 14;
            int fy = top + h - 22;
            if (mx >= left + 10 && mx < left + 10 + bw && my >= fy && my < fy + 16) {
                click();
                openFolder(folder());
                return true;
            }
        }
        if (my >= listTop && my < listTop + listHeight) {
            for (Hit hit : hits) {
                if (hit.over(mx, my)) {
                    click();
                    if (hit.overDelete(mx, my)) {
                        hit.entry().delete().run();
                    } else {
                        hit.entry().pick().run();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static void openFolder(Path folder) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            Files.createDirectories(folder);
            String command = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(command, folder.toString()).start();
        } catch (IOException e) {
            // The folder's name is in the empty-list hint.
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 24, 0, Math.max(0, contentHeight - listHeight));
        return true;
    }
}
