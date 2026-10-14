package com.skyrpg.enchants.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.enchants.Category;
import com.skyrpg.enchants.Codex;
import com.skyrpg.enchants.Enchant;
import com.skyrpg.enchants.EnchantContent;
import com.skyrpg.enchants.EnchantList;
import com.skyrpg.enchants.EnchantPayloads;
import com.skyrpg.enchants.EnchantRarity;
import com.skyrpg.enchants.Enchanting;
import com.skyrpg.enchants.Enchants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Arcane Enchanting: Seraphine's screen and every enchanting table's. Three tabs around a glowing altar:
 * <ul>
 *   <li>Enchant: your enchantable gear on the left, the chosen item on the altar, the enchants it can take on the right.
 *   Pick one, pick a level (owned levels are ticked, locked ones say why), and bind it.</li>
 *   <li>Tomes: your tomes on the left; for the chosen one, combine it with a twin, salvage it into Runic Dust, or apply
 *   it to any item that takes it.</li>
 *   <li>Codex: every enchant there is, what it does and where it comes from; tome-only enchants stay "???" until you
 *   find them.</li>
 * </ul>
 * Replacing an enchant (a conflict, or another ultimate) needs a second click. The server checks everything again and
 * answers with a fresh view; a success sets the altar off.
 */
public final class EnchantingScreen extends Screen {
    private static final int MAX_WIDTH = 500;
    private static final int MAX_HEIGHT = 300;
    private static final int HEADER = 40;
    private static final int FOOTER = 18;
    private static final int PAD = 10;
    private static final int GAP = 8;
    private static final int SLOT = 20;
    private static final int GEAR_COLUMNS = 4;
    private static final int ROW = 20;
    private static final int ROW_OPEN = 76;
    private static final int APPLY_ROW = 26;
    private static final int CARD_HEIGHT = 56;
    private static final long NOTICE_MILLIS = 4000;
    private static final long CONFIRM_MILLIS = 3000;
    private static final String[] TABS = {"Enchant", "Tomes", "Codex"};
    private static final int WINDOW = 0xF4100A1E;
    private static final int WINDOW_TOP = 0x40332066;
    private static final int PANEL = 0x70060410;
    private static final int PANEL_BORDER = 0xFF2E2248;
    private static final int ROW_FILL = 0xFF171127;
    private static final int ROW_HOVER = 0xFF1F1734;
    private static final String[] CODEX_FILTERS = {"All", "Weapons", "Bows", "Armor", "Tools", "Fishing", "Ultimate"};

    private record Entry(int slot, ItemStack stack) {
    }

    private record Star(float x, float y, float phase, float speed) {
    }

    private EnchantPayloads.View view;
    private int tab;
    private int gearSlot = -1;
    private int tomeSlot = -1;
    private Identifier chosen;
    private int chosenLevel;
    private double listScroll;
    private double gearScroll;
    private double codexScroll;
    private int codexFilter;
    private String confirmKey = "";
    private long confirmAt;
    private Component notice = Component.empty();
    private boolean noticeGood;
    private long noticeAt;
    private final ArcaneFx fx = new ArcaneFx();
    private final List<Star> stars = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private boolean binding;

    private int left;
    private int top;
    private int windowWidth;
    private int windowHeight;
    private int gearX;
    private int altarX;
    private int altarW;
    private int panelX;
    private int panelW;

    private EnchantingScreen(EnchantPayloads.View view) {
        super(Component.literal("Arcane Enchanting"));
        this.view = view;
        this.tab = Math.max(0, Math.min(TABS.length - 1, view.tab()));
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42);
        for (int i = 0; i < 46; i++) {
            stars.add(new Star(random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28f, 0.6f + random.nextFloat() * 1.6f));
        }
    }

    /** A view from the server: opens the screen, or updates the open one (with the altar's flash on a success). */
    public static void receive(EnchantPayloads.View view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof EnchantingScreen open) {
            open.view = view;
            open.notify(view.notice(), view.good());
            if (view.tab() >= 0) {
                open.tab = view.tab();
            }
            if (view.flash() != EnchantPayloads.FLASH_NONE) {
                open.flash(view.flash());
            }
        } else if (!view.refresh()) {
            minecraft.gui.setScreen(new EnchantingScreen(view));
        }
    }

    private void notify(Component text, boolean good) {
        if (!text.getString().isEmpty()) {
            notice = text;
            noticeGood = good;
            noticeAt = Util.getMillis();
        }
        binding = false;
        confirmKey = "";
    }

    private void flash(int kind) {
        int color = switch (kind) {
            case EnchantPayloads.FLASH_TOME -> ArcaneFx.CYAN;
            case EnchantPayloads.FLASH_COMBINE -> EnchantRarity.ULTIMATE.color();
            case EnchantPayloads.FLASH_SALVAGE -> ArcaneFx.VIOLET;
            default -> ArcaneFx.GOLD;
        };
        fx.flash(altarCx(), altarCy(), color);
        float pitch = kind == EnchantPayloads.FLASH_SALVAGE ? 0.7f : 1.2f;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.9f));
    }

    @Override
    protected void init() {
        windowWidth = Math.min(width - 20, MAX_WIDTH);
        windowHeight = Math.min(height - 20, MAX_HEIGHT);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
        gearX = left + PAD;
        altarW = windowWidth < 440 ? 116 : 150;
        altarX = gearX + gearWidth() + GAP;
        panelX = altarX + altarW + GAP;
        panelW = left + windowWidth - PAD - panelX;
    }

    private static int gearWidth() {
        return GEAR_COLUMNS * SLOT + 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xA0060310);
    }

    // ---------------------------------------------------------------- state from the client

    private Inventory inventory() {
        return minecraft.player.getInventory();
    }

    /** Enchantable gear: worn armour first, then the held item, the hotbar, the bag and the offhand. */
    private List<Entry> gear() {
        Inventory inventory = inventory();
        List<Integer> order = new ArrayList<>(List.of(39, 38, 37, 36, inventory.getSelectedSlot()));
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (!order.contains(slot)) {
                order.add(slot);
            }
        }
        order.add(40);
        List<Entry> out = new ArrayList<>();
        for (int slot : order) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !EnchantContent.isTome(stack) && Category.enchantable(stack)) {
                out.add(new Entry(slot, stack));
            }
        }
        return out;
    }

    private List<Entry> tomes() {
        Inventory inventory = inventory();
        List<Entry> out = new ArrayList<>();
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (EnchantContent.isTome(stack)) {
                out.add(new Entry(slot, stack));
            }
        }
        return out;
    }

    private ItemStack selectedGear(List<Entry> gear) {
        for (Entry entry : gear) {
            if (entry.slot() == gearSlot) {
                return entry.stack();
            }
        }
        gearSlot = gear.isEmpty() ? -1 : gear.getFirst().slot();
        chosen = null;
        return gear.isEmpty() ? ItemStack.EMPTY : gear.getFirst().stack();
    }

    private ItemStack selectedTome(List<Entry> tomes) {
        for (Entry entry : tomes) {
            if (entry.slot() == tomeSlot) {
                return entry.stack();
            }
        }
        tomeSlot = tomes.isEmpty() ? -1 : tomes.getFirst().slot();
        return tomes.isEmpty() ? ItemStack.EMPTY : tomes.getFirst().stack();
    }

    private int power() {
        return view.power() < 0 ? Enchanting.FULL_POWER : view.power();
    }

    private int enchantingLevel() {
        return SkillLevels.level(minecraft.player, Codex.ENCHANTING);
    }

    private int material(Identifier id) {
        return view.materials().getOrDefault(id.toString(), 0);
    }

    private boolean creative() {
        return minecraft.player.isCreative();
    }

    /** Enchants on the item this one would replace: conflicts, or another ultimate. */
    private static List<Identifier> replaced(Enchant def, EnchantList current) {
        List<Identifier> out = new ArrayList<>();
        for (Identifier other : current.levels().keySet()) {
            Enchant otherDef = Enchants.get(other);
            if (def.conflictsWith(other) || (def.ultimate() && otherDef != null && otherDef.ultimate() && !other.equals(def.id()))) {
                out.add(other);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- layout helpers

    private int bodyTop() {
        return top + HEADER;
    }

    private int bodyBottom() {
        return top + windowHeight - FOOTER;
    }

    private float altarRadius() {
        return Math.min(altarW / 2f - 6, (bodyBottom() - bodyTop() - 70) / 2f);
    }

    private float altarCx() {
        return altarX + altarW / 2f;
    }

    private float altarCy() {
        return bodyTop() + 8 + altarRadius();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        float opening = Draw.easeOut((Util.getMillis() - openedAt) / 260f);
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - opening) * 10);
        drawWindow(g);
        drawHeader(g, mouseX, mouseY);
        List<Entry> gear = gear();
        List<Entry> tomes = tomes();
        if (tab == 2) {
            drawCodex(g, mouseX, mouseY);
        } else {
            ItemStack shown = tab == 0 ? selectedGear(gear) : selectedTome(tomes);
            drawGrid(g, mouseX, mouseY, tab == 0 ? gear : tomes, tab == 0 ? gearSlot : tomeSlot,
                    tab == 0 ? "YOUR GEAR" : "YOUR TOMES");
            drawAltar(g, shown);
            if (tab == 0) {
                drawEnchants(g, mouseX, mouseY, shown);
            } else {
                drawTome(g, mouseX, mouseY, shown, tomes, gear);
            }
        }
        drawFooter(g);
        g.nextStratum();
        drawNotice(g);
        g.pose().popMatrix();
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private void drawWindow(GuiGraphicsExtractor g) {
        float t = ArcaneFx.seconds();
        float pulse = 0.5f + 0.5f * Mth.sin(t * 1.4f);
        Draw.rounded(g, left - 2, top - 2, windowWidth + 4, windowHeight + 4, Draw.withAlpha(ArcaneFx.VIOLET, 0.10f + 0.10f * pulse));
        Draw.rounded(g, left - 1, top - 1, windowWidth + 2, windowHeight + 2, Draw.withAlpha(ArcaneFx.LAVENDER, 0.18f + 0.18f * pulse));
        Draw.panel(g, left, top, windowWidth, windowHeight, WINDOW, 0xFF3A2A60);
        g.fillGradient(left + 1, top + 1, left + windowWidth - 1, top + 70, WINDOW_TOP, 0x00000000);
        for (Star star : stars) {
            float twinkle = 0.5f + 0.5f * Mth.sin(t * star.speed() + star.phase());
            int x = left + 4 + Math.round(star.x() * (windowWidth - 8));
            int y = top + 4 + Math.round(star.y() * (windowHeight - 8));
            g.fill(x, y, x + 1, y + 1, Draw.withAlpha(0xFFE6DAFF, 0.08f + 0.35f * twinkle));
        }
        // A shimmering line along the top: violet, cyan and gold chasing each other.
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            int x1 = left + 2 + (windowWidth - 4) * i / segments;
            int x2 = left + 2 + (windowWidth - 4) * (i + 1) / segments;
            float wave = 0.5f + 0.5f * Mth.sin(t * 1.8f - i * 0.45f);
            int color = Draw.mix(Draw.mix(ArcaneFx.LAVENDER, ArcaneFx.CYAN, wave), ArcaneFx.GOLD, Math.max(0, wave - 0.7f) * 2);
            g.fill(x1, top, x2, top + 2, color);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        float t = ArcaneFx.seconds();
        String title = "✦ Arcane Enchanting ✦";
        MutableComponent shimmer = Component.empty();
        for (int i = 0; i < title.length(); i++) {
            float wave = 0.5f + 0.5f * Mth.sin(t * 2.6f - i * 0.38f);
            shimmer.append(Component.literal(String.valueOf(title.charAt(i))).withColor(Draw.mix(0xFFD9C4FF, ArcaneFx.GOLD, wave * wave)));
        }
        Draw.scaledText(g, font, shimmer, left + PAD, top + 8, 1.25f, 0xFFFFFFFF);
        if (tab == 2) {
            drawCodexProgress(g);
        } else {
            drawPower(g, mouseX, mouseY);
        }
        drawTabs(g, mouseX, mouseY);
    }

    private void drawCodexProgress(GuiGraphicsExtractor g) {
        Set<String> found = Codex.state(minecraft.player).discovered();
        List<Enchant> all = Enchants.all();
        long discovered = all.stream().filter(def -> found.contains(def.id().toString())).count();
        Component text = Component.literal("Codex  ").withColor(Theme.MUTED).append(Component.literal(discovered + " / " + all.size())
                .withColor(ArcaneFx.LAVENDER)).append(Component.literal(" discovered").withColor(Theme.MUTED));
        Draw.text(g, font, text, left + PAD, top + 24, Theme.MUTED);
        Draw.bar(g, left + PAD + font.width(text) + 6, top + 25, 80, 6, all.isEmpty() ? 0 : discovered / (float) all.size(), ArcaneFx.LAVENDER);
    }

    private void drawPower(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int power = power();
        String where = view.power() < 0 ? "Seraphine's library" : "Enchanting table";
        Draw.text(g, font, where, left + PAD, top + 24, Theme.MUTED);
        int px = left + PAD + font.width(where) + 6;
        for (int i = 0; i < Enchanting.FULL_POWER; i++) {
            boolean lit = i < power;
            g.fill(px + i * 4, top + 26, px + i * 4 + 3, top + 31, lit ? Draw.mix(ArcaneFx.LAVENDER, ArcaneFx.GOLD, i / 14f) : 0xFF2A2140);
        }
        Draw.text(g, font, power + "/15", px + Enchanting.FULL_POWER * 4 + 4, top + 24, power >= 15 ? ArcaneFx.GOLD : Theme.MUTED);
        if (inside(mouseX, mouseY, px, top + 22, Enchanting.FULL_POWER * 4 + 30, 12)) {
            Draw.queueTooltip(List.of(Component.literal("Bookshelf power").withColor(ArcaneFx.LAVENDER),
                    Component.literal(view.power() < 0 ? "Seraphine's library counts as fifteen bookshelves."
                            : "Bookshelves around the table (one block of air between). Higher levels need more of them.")
                            .withColor(Theme.MUTED)), ArcaneFx.LAVENDER);
        }
    }

    /** Tabs, right-aligned in the header. */
    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tx = left + windowWidth - PAD;
        for (int i = TABS.length - 1; i >= 0; i--) {
            int w = font.width(TABS[i]) + 16;
            tx -= w;
            boolean chosenTab = i == tab;
            boolean hovered = inside(mouseX, mouseY, tx, top + 10, w, 16);
            Draw.rounded(g, tx, top + 10, w, 16, chosenTab ? Draw.withAlpha(ArcaneFx.LAVENDER, 0.25f) : hovered ? ROW_HOVER : ROW_FILL);
            if (chosenTab) {
                g.fill(tx + 3, top + 25, tx + w - 3, top + 26, ArcaneFx.LAVENDER);
            }
            Draw.text(g, font, TABS[i], tx + 8, top + 14, chosenTab ? 0xFFF0E6FF : hovered ? Theme.TEXT : Theme.MUTED);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            tx -= 4;
        }
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        int y = top + windowHeight - FOOTER + 5;
        int x = left + PAD;
        x = chip(g, x, y, StatTypes.COIN_ICON + " " + Msg.compact(view.coins()), Theme.ACCENT);
        x = chip(g, x, y, "⭐ " + (creative() ? "∞" : minecraft.player.experienceLevel) + " levels", 0xFF9CFF6A);
        x = chip(g, x, y, "Lapis " + material(Identifier.withDefaultNamespace("lapis_lazuli")), 0xFF5C8DFF);
        x = chip(g, x, y, "Runic Dust " + material(BuiltInRegistries.ITEM.getKey(EnchantContent.RUNIC_DUST)), ArcaneFx.LAVENDER);
        x = chip(g, x, y, "Enchanting " + enchantingLevel(), ArcaneFx.CYAN);
    }

    /** The server's answer, as a toast over the bottom of the window that fades after a few seconds. */
    private void drawNotice(GuiGraphicsExtractor g) {
        long age = Util.getMillis() - noticeAt;
        if (age >= NOTICE_MILLIS || notice.getString().isEmpty()) {
            return;
        }
        float alpha = Math.min(1, Math.min(age / 150f, (NOTICE_MILLIS - age) / 400f));
        int color = noticeGood ? Theme.GREEN : Theme.RED;
        int w = Math.min(font.width(notice) + 20, windowWidth - 40);
        int x = left + (windowWidth - w) / 2;
        int y = bodyBottom() - 20 + Math.round((1 - Draw.easeOut(Math.min(1, age / 200f))) * 6);
        Draw.panel(g, x, y, w, 16, Draw.withAlpha(0xFF0C0818, alpha * 0.95f), Draw.withAlpha(color, alpha * 0.8f));
        g.fill(x + 2, y, x + w - 2, y + 1, Draw.withAlpha(color, alpha));
        Draw.fit(g, font, notice, x + w / 2, y + 4, w - 12, Draw.Align.CENTER, Draw.withAlpha(color, Math.max(0.05f, alpha)));
    }

    private int chip(GuiGraphicsExtractor g, int x, int y, String text, int color) {
        Draw.text(g, font, text, x, y, color);
        return x + font.width(text) + 10;
    }

    // ---------------------------------------------------------------- the left grid (gear or tomes)

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, List<Entry> entries, int selected, String label) {
        int y0 = bodyTop();
        int h = bodyBottom() - y0 - 2;
        Draw.panel(g, gearX, y0, gearWidth(), h, PANEL, PANEL_BORDER);
        Draw.text(g, font, label, gearX + 5, y0 + 5, Theme.DIM);
        int gridTop = y0 + 17;
        int gridHeight = h - 20;
        int rows = (entries.size() + GEAR_COLUMNS - 1) / GEAR_COLUMNS;
        int content = rows * SLOT;
        gearScroll = Mth.clamp(gearScroll, 0, Math.max(0, content - gridHeight));
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(tab == 0 ? "Nothing here takes enchants. Swords, bows, armour, tools and rods do."
                    : "No tomes yet. Monsters, bosses and gathering turn them up."), gearWidth() - 10);
            for (int i = 0; i < lines.size(); i++) {
                g.text(font, lines.get(i), gearX + 5, gridTop + 2 + i * 10, Theme.DIM, false);
            }
            return;
        }
        g.enableScissor(gearX + 1, gridTop, gearX + gearWidth() - 1, gridTop + gridHeight);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int x = gearX + 4 + (i % GEAR_COLUMNS) * SLOT;
            int y = gridTop + (i / GEAR_COLUMNS) * SLOT - (int) Math.round(gearScroll);
            boolean isSelected = entry.slot() == selected;
            boolean hovered = inside(mouseX, mouseY, x, y, SLOT - 2, SLOT - 2) && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            int border = isSelected ? Draw.withAlpha(ArcaneFx.LAVENDER, 0.6f + 0.4f * Mth.sin(ArcaneFx.seconds() * 4)) : hovered ? 0xFF4A3A70 : 0xFF2A2140;
            Draw.panel(g, x, y, SLOT - 2, SLOT - 2, isSelected ? 0xFF2A1A4A : 0xFF140E22, border);
            g.item(entry.stack(), x + 1, y + 1);
            if (EnchantContent.isTome(entry.stack())) {
                Enchant def = Enchants.get(EnchantContent.enchants(entry.stack()).levels().keySet().iterator().next());
                if (def != null) {
                    g.fill(x + 3, y + SLOT - 5, x + SLOT - 5, y + SLOT - 4, def.rarity().color());
                }
            }
            if (entry.slot() >= 36 && entry.slot() <= 39) {
                g.fill(x + SLOT - 6, y + 2, x + SLOT - 4, y + 4, ArcaneFx.CYAN); // worn
            }
            if (hovered) {
                g.setTooltipForNextFrame(font, entry.stack(), mouseX, mouseY);
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
        g.disableScissor();
        if (content > gridHeight) {
            Draw.scrollbar(g, gearX + gearWidth() - 4, gridTop, gridHeight, content, gearScroll, ArcaneFx.LAVENDER);
        }
    }

    // ---------------------------------------------------------------- the altar

    private void drawAltar(GuiGraphicsExtractor g, ItemStack stack) {
        float cx = altarCx();
        float cy = altarCy();
        float radius = altarRadius();
        int accent = ArcaneFx.LAVENDER;
        Enchant chosenDef = chosen == null ? null : Enchants.get(chosen);
        if (tab == 0 && chosenDef != null) {
            accent = chosenDef.rarity().color();
        } else if (tab == 1 && EnchantContent.isTome(stack)) {
            Enchant def = Enchants.get(EnchantContent.enchants(stack).levels().keySet().iterator().next());
            accent = def == null ? accent : def.rarity().color();
        }
        fx.update(cx, cy, radius, binding ? 70 : 16);
        fx.drawAltar(g, font, cx, cy, radius, accent);
        if (!stack.isEmpty()) {
            float bob = Mth.sin(ArcaneFx.seconds() * 2.2f) * 3;
            float scale = 2f * fx.flashScale();
            int size = Math.round(16 * scale);
            Draw.item(g, stack, Math.round(cx - size / 2f), Math.round(cy - size / 2f + bob), scale);
        }
        fx.drawFlash(g, cx, cy, radius);
        fx.drawMotes(g, font);

        // Under the altar: the item's name and what it already holds.
        int y = Math.round(cy + radius + 6);
        int w = altarW - 4;
        if (stack.isEmpty()) {
            Draw.fit(g, font, Component.literal(tab == 0 ? "Choose an item" : "Choose a tome"), altarX + altarW / 2, y, w, Draw.Align.CENTER, Theme.DIM);
            return;
        }
        Draw.fit(g, font, stack.getHoverName(), altarX + altarW / 2, y, w, Draw.Align.CENTER, Theme.TEXT);
        if (tab != 0) {
            return;
        }
        List<Map.Entry<Enchant, Integer>> held = EnchantTooltips.sorted(EnchantContent.enchants(stack));
        int lines = Math.max(1, (bodyBottom() - y - 14) / 10);
        if (held.isEmpty()) {
            Draw.fit(g, font, Component.literal("No enchants yet"), altarX + altarW / 2, y + 12, w, Draw.Align.CENTER, Theme.DIM);
        }
        for (int i = 0; i < held.size() && i < lines; i++) {
            Map.Entry<Enchant, Integer> entry = held.get(i);
            Component text = i == lines - 1 && held.size() > lines ? Component.literal("+" + (held.size() - i) + " more").withColor(Theme.DIM)
                    : EnchantTooltips.name(entry.getKey(), entry.getValue());
            Draw.fit(g, font, text, altarX + altarW / 2, y + 12 + i * 10, w, Draw.Align.CENTER, Theme.TEXT);
        }
    }

    // ---------------------------------------------------------------- enchant tab

    private List<Enchant> offered(ItemStack stack) {
        return Enchants.forItem(stack).stream().filter(Enchant::tableable)
                .sorted(java.util.Comparator.comparing(Enchant::rarity).thenComparing(Enchant::name)).toList();
    }

    private int enchantRowHeight(Enchant def) {
        return def.id().equals(chosen) ? ROW_OPEN : ROW;
    }

    private void drawEnchants(GuiGraphicsExtractor g, int mouseX, int mouseY, ItemStack stack) {
        int y0 = bodyTop();
        int h = bodyBottom() - y0 - 2;
        Draw.panel(g, panelX, y0, panelW, h, PANEL, PANEL_BORDER);
        if (stack.isEmpty()) {
            Draw.text(g, font, "ENCHANTS", panelX + 6, y0 + 5, Theme.DIM);
            return;
        }
        List<Enchant> offered = offered(stack);
        long tomeOnly = Enchants.forItem(stack).size() - offered.size();
        Draw.text(g, font, "ENCHANTS  " + offered.size(), panelX + 6, y0 + 5, Theme.DIM);
        if (tomeOnly > 0) {
            Draw.rightText(g, font, "+" + tomeOnly + " from tomes", panelX + panelW - 6, y0 + 5, 0xFF6A5A8A);
        }
        if (chosen == null && !offered.isEmpty()) {
            choose(offered.getFirst(), stack);
        }
        int listTop = y0 + 17;
        int listHeight = h - 20;
        int content = 0;
        for (Enchant def : offered) {
            content += enchantRowHeight(def) + 2;
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, content - listHeight));
        EnchantList current = EnchantContent.enchants(stack);
        g.enableScissor(panelX + 1, listTop, panelX + panelW - 1, listTop + listHeight);
        int y = listTop - (int) Math.round(listScroll);
        int rowW = panelW - 10;
        for (Enchant def : offered) {
            int rh = enchantRowHeight(def);
            if (y + rh >= listTop && y <= listTop + listHeight) {
                boolean hovered = inside(mouseX, mouseY, panelX + 4, y, rowW, rh) && mouseY >= listTop && mouseY < listTop + listHeight;
                drawEnchantRow(g, mouseX, mouseY, def, current, panelX + 4, y, rowW, rh, hovered);
            }
            y += rh + 2;
        }
        g.disableScissor();
        if (content > listHeight) {
            Draw.scrollbar(g, panelX + panelW - 4, listTop, listHeight, content, listScroll, ArcaneFx.LAVENDER);
        }
    }

    private void choose(Enchant def, ItemStack stack) {
        chosen = def.id();
        int have = EnchantContent.enchants(stack).level(def.id());
        chosenLevel = Math.min(def.tableMax(), have + 1);
        for (int level = have + 1; level <= def.tableMax(); level++) {
            if (lockReason(def, level) == null) {
                chosenLevel = level;
            }
        }
    }

    /** Why a level can't be chosen here, or null. */
    private String lockReason(Enchant def, int level) {
        int shelves = Enchants.rules().shelves(level);
        if (power() < shelves) {
            return "Needs " + shelves + " bookshelves around the table";
        }
        int requires = def.cost(level).requires();
        if (enchantingLevel() < requires) {
            return "Needs Enchanting " + requires;
        }
        return null;
    }

    private void drawEnchantRow(GuiGraphicsExtractor g, int mouseX, int mouseY, Enchant def, EnchantList current, int x, int y, int w, int h,
                                boolean hovered) {
        boolean open = def.id().equals(chosen);
        int color = def.rarity().color();
        Draw.rounded(g, x, y, w, h, open ? Draw.mix(ROW_FILL, color, 0.10f) : hovered ? ROW_HOVER : ROW_FILL);
        g.fill(x, y + 1, x + 2, y + h - 1, color);
        int have = current.level(def.id());
        Draw.fitText(g, font, (def.ultimate() ? "✦ " : "") + def.name(), x + 7, y + 6, w - 60, color);
        String levels = (have > 0 ? Enchant.roman(have) : "–") + " / " + Enchant.roman(def.tableMax());
        Draw.rightText(g, font, levels, x + w - 6, y + 6, have >= def.tableMax() ? ArcaneFx.GOLD : Theme.MUTED);
        if (!open) {
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                int next = Math.min(def.tableMax(), have + 1);
                Draw.queueTooltip(List.of(Component.literal(def.title(next)).withColor(color),
                        Component.literal(def.describe(next)).withColor(Theme.MUTED),
                        Component.literal(def.rarity().label() + " · click to choose a level").withColor(Theme.DIM)), color);
            }
            return;
        }
        // Description at the chosen level.
        List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(def.describe(chosenLevel)), w - 14);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), x + 7, y + 18 + i * 10, 0xFFB8AED0, false);
        }
        // Level pills.
        int py = y + 40;
        for (int level = 1; level <= def.tableMax(); level++) {
            int px = x + 7 + (level - 1) * 24;
            String reason = lockReason(def, level);
            boolean owned = level <= have;
            boolean selected = level == chosenLevel;
            boolean pillHover = inside(mouseX, mouseY, px, py, 21, 13);
            int fill = owned ? 0xFF1C3A2A : selected ? Draw.mix(ROW_FILL, color, 0.45f) : pillHover ? ROW_HOVER : 0xFF110B1E;
            int border = owned ? 0xFF3C7A54 : selected ? color : reason != null ? 0xFF3A2A40 : Draw.mix(0xFF2A2140, color, 0.35f);
            Draw.panel(g, px, py, 21, 13, fill, border);
            String label = owned ? "✔" : Enchant.roman(level);
            int textColor = owned ? Theme.GREEN : reason != null ? 0xFF6A5A7A : selected ? 0xFFFFFFFF : Theme.TEXT;
            Draw.text(g, font, label, px + (21 - font.width(label)) / 2 + 1, py + 3, textColor);
            if (pillHover) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(def.title(level)).withColor(color));
                tip.add(Component.literal(def.describe(level)).withColor(Theme.MUTED));
                if (owned) {
                    tip.add(Component.literal("Already on this item").withColor(Theme.GREEN));
                } else if (reason != null) {
                    tip.add(Component.literal("🔒 " + reason).withColor(Theme.RED));
                }
                Draw.queueTooltip(tip, color);
            }
        }
        // Cost and the bind button.
        int cy = y + 61;
        Enchant.LevelCost cost = def.cost(chosenLevel);
        int cx = x + 7;
        boolean affordable = true;
        boolean canCoins = view.coins() >= cost.coins();
        affordable &= canCoins;
        cx = costChip(g, cx, cy, StatTypes.COIN_ICON + " " + Msg.compact(cost.coins()), canCoins, Theme.ACCENT);
        boolean canXp = creative() || minecraft.player.experienceLevel >= cost.xp();
        affordable &= canXp;
        if (cost.xp() > 0) {
            cx = costChip(g, cx, cy, "⭐ " + cost.xp(), canXp, 0xFF9CFF6A);
        }
        for (Map.Entry<Identifier, Integer> material : cost.materials().entrySet()) {
            boolean enough = material(material.getKey()) >= material.getValue();
            affordable &= enough;
            Item item = BuiltInRegistries.ITEM.getValue(material.getKey());
            ItemStack icon = new ItemStack(item);
            Draw.item(g, icon, cx, cy - 4, 0.75f);
            cx = costChip(g, cx + 13, cy, "×" + material.getValue(), enough, Theme.TEXT);
            if (inside(mouseX, mouseY, cx - 30, cy - 4, 30, 12)) {
                Draw.queueTooltip(List.of(icon.getHoverName(), Component.literal("You have " + material(material.getKey())).withColor(Theme.MUTED)),
                        ArcaneFx.LAVENDER);
            }
        }
        String locked = chosenLevel <= have ? "Owned" : lockReason(def, chosenLevel);
        List<Identifier> replaces = replaced(def, current);
        String key = "enchant:" + def.id() + ":" + chosenLevel;
        boolean armed = confirmKey.equals(key) && Util.getMillis() - confirmAt < CONFIRM_MILLIS;
        String label = locked != null ? "🔒" : armed ? "Replace?" : "✦ Bind";
        boolean enabled = locked == null && affordable;
        drawButton(g, mouseX, mouseY, x + w - 62, y + 39, 56, 15, label, enabled, armed ? Theme.RED : color);
        if (inside(mouseX, mouseY, x + w - 62, y + 39, 56, 15)) {
            binding = enabled;
            List<Component> tip = new ArrayList<>();
            if (locked != null) {
                tip.add(Component.literal(locked).withColor(Theme.RED));
            } else if (!affordable) {
                tip.add(Component.literal("You can't afford this yet").withColor(Theme.RED));
            } else {
                tip.add(Component.literal("Bind " + def.title(chosenLevel)).withColor(color));
                tip.add(Component.literal("+" + Msg.number(Math.round(cost.xp() * Enchants.rules().skillXp().perXpLevel()
                        + cost.coins() / 1000.0 * Enchants.rules().skillXp().per1000Coins())) + " Enchanting XP").withColor(ArcaneFx.CYAN));
            }
            if (!replaces.isEmpty()) {
                tip.add(Component.literal("Replaces " + names(replaces, current)).withColor(0xFFFF9A6A));
            }
            Draw.queueTooltip(tip, color);
        }
    }

    private int costChip(GuiGraphicsExtractor g, int x, int y, String text, boolean ok, int color) {
        Draw.text(g, font, text, x, y, ok ? color : Theme.RED);
        return x + font.width(text) + 7;
    }

    private void drawButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean enabled, int color) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        float pulse = 0.5f + 0.5f * Mth.sin(ArcaneFx.seconds() * 3);
        int fill = !enabled ? 0xFF1A1426 : hovered ? Draw.mix(0xFF2A1A4A, color, 0.55f) : Draw.mix(0xFF2A1A4A, color, 0.25f + 0.12f * pulse);
        int border = !enabled ? 0xFF2E2440 : Draw.mix(color, 0xFFFFFFFF, hovered ? 0.4f : 0.1f);
        Draw.panel(g, x, y, w, h, fill, border);
        Draw.fit(g, font, Component.literal(label), x + w / 2, y + (h - 8) / 2, w - 4, Draw.Align.CENTER, enabled ? 0xFFFFFFFF : 0xFF6A5A7A);
        if (hovered && enabled) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private static String names(List<Identifier> ids, EnchantList current) {
        List<String> names = new ArrayList<>();
        for (Identifier id : ids) {
            Enchant def = Enchants.get(id);
            names.add(def == null ? id.getPath() : def.title(current.level(id)));
        }
        return String.join(", ", names);
    }

    // ---------------------------------------------------------------- tomes tab

    private int twin(List<Entry> tomes, ItemStack tome) {
        for (Entry entry : tomes) {
            if (entry.slot() != tomeSlot && EnchantContent.enchants(entry.stack()).equals(EnchantContent.enchants(tome))) {
                return entry.slot();
            }
        }
        return -1;
    }

    private void drawTome(GuiGraphicsExtractor g, int mouseX, int mouseY, ItemStack tome, List<Entry> tomes, List<Entry> gear) {
        int y0 = bodyTop();
        int h = bodyBottom() - y0 - 2;
        Draw.panel(g, panelX, y0, panelW, h, PANEL, PANEL_BORDER);
        if (!EnchantContent.isTome(tome)) {
            Draw.text(g, font, "TOMES", panelX + 6, y0 + 5, Theme.DIM);
            List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal("A tome holds one enchant. Apply it to gear here, combine "
                    + "two of the same into a stronger one, or salvage it into Runic Dust."), panelW - 12);
            for (int i = 0; i < lines.size(); i++) {
                g.text(font, lines.get(i), panelX + 6, y0 + 20 + i * 10, Theme.MUTED, false);
            }
            return;
        }
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(tome).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        if (def == null) {
            Draw.text(g, font, "This tome's magic has faded", panelX + 6, y0 + 6, Theme.MUTED);
            return;
        }
        int level = inside.getValue();
        int color = def.rarity().color();
        int x = panelX + 6;
        int w = panelW - 12;
        Draw.fit(g, font, EnchantTooltips.name(def, level), x, y0 + 6, w, Draw.Align.LEFT, color);
        Draw.fitText(g, font, def.rarity().label() + " · " + String.join(", ", def.appliesTo().stream().map(Category::label).toList()),
                x, y0 + 17, w, Theme.DIM);
        List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(def.describe(level)), w);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), x, y0 + 29 + i * 10, 0xFFB8AED0, false);
        }
        // Combine and salvage.
        int by = y0 + 52;
        int half = (w - 6) / 2;
        int twin = twin(tomes, tome);
        boolean canCombine = twin >= 0 && level < def.maxLevel();
        long combineCoins = Enchants.rules().combineCoins(def.rarity(), level + 1);
        int combineDust = Enchants.rules().combineDust(def.rarity(), level + 1);
        boolean affordCombine = view.coins() >= combineCoins && material(BuiltInRegistries.ITEM.getKey(EnchantContent.RUNIC_DUST)) >= combineDust;
        drawButton(g, mouseX, mouseY, x, by, half, 15, level >= def.maxLevel() ? "Max level" : "Combine → " + Enchant.roman(level + 1),
                canCombine && affordCombine, EnchantRarity.ULTIMATE.color());
        if (inside(mouseX, mouseY, x, by, half, 15)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Combine two tomes of " + def.title(level)).withColor(color));
            if (level >= def.maxLevel()) {
                tip.add(Component.literal(def.name() + " can't go higher").withColor(Theme.MUTED));
            } else {
                tip.add(Component.literal("Makes one " + def.title(level + 1)).withColor(Theme.MUTED));
                tip.add(Component.literal(StatTypes.COIN_ICON + " " + Msg.number(combineCoins) + (combineDust > 0 ? "  ·  Runic Dust ×" + combineDust : ""))
                        .withColor(affordCombine ? Theme.ACCENT : Theme.RED));
                if (twin < 0) {
                    tip.add(Component.literal("You need a second one").withColor(Theme.RED));
                }
            }
            Draw.queueTooltip(tip, EnchantRarity.ULTIMATE.color());
        }
        int dust = Enchants.rules().salvageDust(def.rarity(), level);
        String salvageKey = "salvage:" + tomeSlot;
        boolean salvageArmed = confirmKey.equals(salvageKey) && Util.getMillis() - confirmAt < CONFIRM_MILLIS;
        drawButton(g, mouseX, mouseY, x + half + 6, by, w - half - 6, 15, salvageArmed ? "Sure?" : "Salvage → " + dust + " Dust", true,
                salvageArmed ? Theme.RED : ArcaneFx.VIOLET);
        if (inside(mouseX, mouseY, x + half + 6, by, w - half - 6, 15)) {
            Draw.queueTooltip(List.of(Component.literal("Break this tome down").withColor(ArcaneFx.LAVENDER),
                    Component.literal("Gives " + dust + " Runic Dust (click twice)").withColor(Theme.MUTED)), ArcaneFx.LAVENDER);
        }
        // Apply to gear.
        int ay = by + 22;
        Draw.text(g, font, "APPLY TO", x, ay, Theme.DIM);
        List<Entry> targets = gear.stream().filter(entry -> def.appliesTo(entry.stack())).toList();
        int listTop = ay + 11;
        int listHeight = bodyBottom() - 4 - listTop;
        if (targets.isEmpty()) {
            Draw.fitText(g, font, "Nothing you carry takes " + def.name(), x, listTop + 2, w, Theme.MUTED);
            return;
        }
        int content = targets.size() * (APPLY_ROW + 2);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, content - listHeight));
        g.enableScissor(panelX + 1, listTop, panelX + panelW - 1, listTop + listHeight);
        int ry = listTop - (int) Math.round(listScroll);
        for (Entry target : targets) {
            if (ry + APPLY_ROW >= listTop && ry <= listTop + listHeight) {
                drawApplyRow(g, mouseX, mouseY, def, level, target, x, ry, w, listTop, listHeight);
            }
            ry += APPLY_ROW + 2;
        }
        g.disableScissor();
        if (content > listHeight) {
            Draw.scrollbar(g, panelX + panelW - 4, listTop, listHeight, content, listScroll, ArcaneFx.LAVENDER);
        }
    }

    private void drawApplyRow(GuiGraphicsExtractor g, int mouseX, int mouseY, Enchant def, int tomeLevel, Entry target, int x, int y, int w,
                              int listTop, int listHeight) {
        boolean visible = mouseY >= listTop && mouseY < listTop + listHeight;
        boolean hovered = visible && inside(mouseX, mouseY, x, y, w, APPLY_ROW);
        Draw.rounded(g, x, y, w, APPLY_ROW, hovered ? ROW_HOVER : ROW_FILL);
        g.item(target.stack(), x + 4, y + 5);
        EnchantList current = EnchantContent.enchants(target.stack());
        int have = current.level(def.id());
        int result = Enchanting.resultLevel(def, have, tomeLevel);
        Draw.fit(g, font, target.stack().getHoverName(), x + 24, y + 4, w - 92, Draw.Align.LEFT, Theme.TEXT);
        Component change = result <= have ? Component.literal(have > 0 ? "Has " + def.title(have) : "No gain").withColor(Theme.DIM)
                : Component.literal((have > 0 ? Enchant.roman(have) : "–") + " → ").withColor(Theme.MUTED)
                .append(Component.literal(def.title(result)).withColor(def.rarity().color()));
        Draw.fit(g, font, change, x + 24, y + 14, w - 92, Draw.Align.LEFT, Theme.MUTED);
        long coins = Enchants.rules().tomeCoins(def.rarity(), result);
        int xp = Enchants.rules().tomeXp(result);
        boolean affordable = view.coins() >= coins && (creative() || minecraft.player.experienceLevel >= xp);
        List<Identifier> replaces = replaced(def, current);
        String key = "tome:" + tomeSlot + ":" + target.slot();
        boolean armed = confirmKey.equals(key) && Util.getMillis() - confirmAt < CONFIRM_MILLIS;
        boolean enabled = result > have && affordable;
        int bx = x + w - 62;
        drawButton(g, mouseX, mouseY, bx, y + 5, 56, 15, armed ? "Replace?" : "Apply", enabled, armed ? Theme.RED : def.rarity().color());
        if (visible && inside(mouseX, mouseY, bx, y + 5, 56, 15)) {
            binding = enabled;
            List<Component> tip = new ArrayList<>();
            if (result <= have) {
                tip.add(Component.literal("It already has " + def.title(have)).withColor(Theme.MUTED));
            } else {
                tip.add(Component.literal("Apply " + def.title(result)).withColor(def.rarity().color()));
                tip.add(Component.literal(StatTypes.COIN_ICON + " " + Msg.number(coins) + "   ⭐ " + xp + " levels")
                        .withColor(affordable ? Theme.ACCENT : Theme.RED));
            }
            if (!replaces.isEmpty()) {
                tip.add(Component.literal("Replaces " + names(replaces, current)).withColor(0xFFFF9A6A));
            }
            Draw.queueTooltip(tip, def.rarity().color());
        } else if (hovered) {
            g.setTooltipForNextFrame(font, target.stack(), mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- codex tab

    private boolean codexShows(Enchant def) {
        return switch (codexFilter) {
            case 1 -> def.appliesTo().contains(Category.SWORD);
            case 2 -> def.appliesTo().contains(Category.BOW);
            case 3 -> def.appliesTo().stream().anyMatch(c -> c == Category.ARMOR || c == Category.HELMET || c == Category.CHESTPLATE
                    || c == Category.LEGGINGS || c == Category.BOOTS);
            case 4 -> def.appliesTo().stream().anyMatch(c -> c == Category.TOOL || c == Category.PICKAXE || c == Category.AXE
                    || c == Category.HOE || c == Category.SHOVEL || c == Category.WAND);
            case 5 -> def.appliesTo().contains(Category.ROD);
            case 6 -> def.ultimate();
            default -> true;
        };
    }

    private void drawCodex(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = left + PAD;
        int w = windowWidth - PAD * 2;
        int y0 = bodyTop();
        Set<String> found = Codex.state(minecraft.player).discovered();
        List<Enchant> all = Enchants.all();
        // Filters.
        int fx = x0;
        for (int i = 0; i < CODEX_FILTERS.length; i++) {
            int fw = font.width(CODEX_FILTERS[i]) + 10;
            boolean on = i == codexFilter;
            boolean hovered = inside(mouseX, mouseY, fx, y0, fw, 14);
            Draw.rounded(g, fx, y0, fw, 14, on ? Draw.withAlpha(ArcaneFx.LAVENDER, 0.25f) : hovered ? ROW_HOVER : ROW_FILL);
            Draw.text(g, font, CODEX_FILTERS[i], fx + 5, y0 + 3, on ? 0xFFF0E6FF : hovered ? Theme.TEXT : Theme.MUTED);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            fx += fw + 3;
        }
        List<Enchant> shown = all.stream().filter(this::codexShows).toList();
        int listTop = y0 + 19;
        int listHeight = bodyBottom() - 2 - listTop;
        int columns = w >= 360 ? 2 : 1;
        int cardW = (w - (columns - 1) * 6) / columns;
        int rows = (shown.size() + columns - 1) / columns;
        int content = rows * (CARD_HEIGHT + 4);
        codexScroll = Mth.clamp(codexScroll, 0, Math.max(0, content - listHeight));
        g.enableScissor(x0, listTop, x0 + w, listTop + listHeight);
        for (int i = 0; i < shown.size(); i++) {
            int cx = x0 + (i % columns) * (cardW + 6);
            int cy = listTop + (i / columns) * (CARD_HEIGHT + 4) - (int) Math.round(codexScroll);
            if (cy + CARD_HEIGHT < listTop || cy > listTop + listHeight) {
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, cx, cy, cardW, CARD_HEIGHT) && mouseY >= listTop && mouseY < listTop + listHeight;
            drawCodexCard(g, shown.get(i), found.contains(shown.get(i).id().toString()), cx, cy, cardW, hovered);
        }
        g.disableScissor();
        if (content > listHeight) {
            Draw.scrollbar(g, x0 + w - 2, listTop, listHeight, content, codexScroll, ArcaneFx.LAVENDER);
        }
    }

    private void drawCodexCard(GuiGraphicsExtractor g, Enchant def, boolean found, int x, int y, int w, boolean hovered) {
        boolean hidden = !found && !def.tableable();
        int color = def.rarity().color();
        Draw.rounded(g, x, y, w, CARD_HEIGHT, hovered ? ROW_HOVER : ROW_FILL);
        g.fill(x, y + 1, x + 2, y + CARD_HEIGHT - 1, hidden ? Draw.withAlpha(color, 0.35f) : color);
        String symbol = def.appliesTo().getFirst().symbol();
        Draw.text(g, font, symbol, x + 7, y + 5, hidden ? 0xFF4A3A60 : color);
        if (hidden) {
            Component runes = Component.literal(def.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ArcaneFx.RUNES);
            Draw.fit(g, font, runes, x + 18, y + 5, w - 70, Draw.Align.LEFT, Draw.withAlpha(color, 0.55f));
        } else {
            Draw.fit(g, font, Component.literal(def.name()).withStyle(Style.EMPTY.withBold(def.ultimate())), x + 18, y + 5, w - 70, Draw.Align.LEFT, color);
        }
        Draw.rightText(g, font, (found ? "✔ " : "") + def.rarity().label(), x + w - 6, y + 5, found ? Theme.GREEN : Theme.DIM);
        String what = hidden ? "Undiscovered. Its secrets reveal themselves when you find its tome."
                : def.describe(def.maxLevel()) + (def.maxLevel() > 1 ? "  (" + Enchant.roman(def.maxLevel()) + ")" : "");
        List<FormattedCharSequence> lines = Draw.wrap(font, Component.literal(what), w - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, i == 1 && lines.size() > 2 ? Draw.ellipsize(font, Component.literal(lineText(lines, 1)), w - 12) : lines.get(i),
                    x + 7, y + 17 + i * 10, hidden ? 0xFF6A5A8A : 0xFFB8AED0, false);
        }
        Draw.fitText(g, font, "➤ " + def.sourceText(), x + 7, y + 41, w - 12, Theme.DIM);
        if (hovered && !hidden) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(def.name()).withStyle(Style.EMPTY.withColor(color).withBold(def.ultimate())));
            tip.add(Component.literal(def.rarity().label() + " · " + String.join(", ", def.appliesTo().stream().map(Category::label).toList()))
                    .withColor(Theme.DIM));
            for (int level = 1; level <= def.maxLevel(); level++) {
                tip.add(Component.literal(Enchant.roman(level) + "  ").withColor(level <= def.tableMax() ? ArcaneFx.LAVENDER : EnchantRarity.ULTIMATE.color())
                        .append(Component.literal(def.describe(level)).withColor(Theme.MUTED)));
            }
            if (!def.conflicts().isEmpty()) {
                tip.add(Component.literal("Conflicts with " + String.join(", ", def.conflicts().stream()
                        .map(id -> Enchants.get(id) == null ? id.getPath() : Enchants.get(id).name()).toList())).withColor(0xFFFF9A6A));
            }
            Draw.queueTooltip(tip, color);
        }
    }

    /** The wrapped lines from {@code from} on, as one string (for an ellipsized last line). */
    private static String lineText(List<FormattedCharSequence> lines, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            StringBuilder line = new StringBuilder();
            lines.get(i).accept((index, style, codePoint) -> {
                line.appendCodePoint(codePoint);
                return true;
            });
            out.append(i > from ? " " : "").append(line.toString().strip());
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y() - 0; // the opening slide is over by the time anyone clicks
        // Tabs.
        int tx = left + windowWidth - PAD;
        for (int i = TABS.length - 1; i >= 0; i--) {
            int w = font.width(TABS[i]) + 16;
            tx -= w;
            if (inside(mx, my, tx, top + 10, w, 16)) {
                if (tab != i) {
                    tab = i;
                    listScroll = 0;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f));
                }
                return true;
            }
            tx -= 4;
        }
        boolean handled = switch (tab) {
            case 0 -> clickEnchant(mx, my);
            case 1 -> clickTomes(mx, my);
            default -> clickCodex(mx, my);
        };
        return handled || super.mouseClicked(event, doubleClick);
    }

    private boolean clickGrid(double mx, double my, List<Entry> entries, boolean tomes) {
        int gridTop = bodyTop() + 17;
        int gridHeight = bodyBottom() - bodyTop() - 22;
        if (my < gridTop || my >= gridTop + gridHeight) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            int x = gearX + 4 + (i % GEAR_COLUMNS) * SLOT;
            int y = gridTop + (i / GEAR_COLUMNS) * SLOT - (int) Math.round(gearScroll);
            if (inside(mx, my, x, y, SLOT - 2, SLOT - 2)) {
                if (tomes) {
                    tomeSlot = entries.get(i).slot();
                } else if (gearSlot != entries.get(i).slot()) {
                    gearSlot = entries.get(i).slot();
                    chosen = null;
                }
                listScroll = 0;
                click(1.2f);
                return true;
            }
        }
        return false;
    }

    private boolean clickEnchant(double mx, double my) {
        List<Entry> gear = gear();
        if (clickGrid(mx, my, gear, false)) {
            return true;
        }
        ItemStack stack = selectedGear(gear);
        if (stack.isEmpty()) {
            return false;
        }
        int listTop = bodyTop() + 17;
        int listHeight = bodyBottom() - bodyTop() - 22;
        if (my < listTop || my >= listTop + listHeight || mx < panelX || mx >= panelX + panelW) {
            return false;
        }
        EnchantList current = EnchantContent.enchants(stack);
        int y = listTop - (int) Math.round(listScroll);
        int x = panelX + 4;
        int w = panelW - 10;
        for (Enchant def : offered(stack)) {
            int h = enchantRowHeight(def);
            if (inside(mx, my, x, y, w, h)) {
                if (!def.id().equals(chosen)) {
                    choose(def, stack);
                    click(1.0f);
                    return true;
                }
                // Level pills.
                for (int level = 1; level <= def.tableMax(); level++) {
                    if (inside(mx, my, x + 7 + (level - 1) * 24, y + 40, 21, 13)) {
                        if (level > current.level(def.id())) {
                            chosenLevel = level;
                            click(1.0f + level * 0.08f);
                        } else {
                            click(0.6f);
                        }
                        return true;
                    }
                }
                if (inside(mx, my, x + w - 62, y + 39, 56, 15)) {
                    bind(def, current);
                    return true;
                }
                return true;
            }
            y += h + 2;
        }
        return false;
    }

    private void bind(Enchant def, EnchantList current) {
        if (chosenLevel <= current.level(def.id()) || lockReason(def, chosenLevel) != null) {
            click(0.6f);
            return;
        }
        String key = "enchant:" + def.id() + ":" + chosenLevel;
        boolean replaces = !replaced(def, current).isEmpty();
        if (replaces && !(confirmKey.equals(key) && Util.getMillis() - confirmAt < CONFIRM_MILLIS)) {
            confirmKey = key;
            confirmAt = Util.getMillis();
            click(0.8f);
            return;
        }
        confirmKey = "";
        ClientPlayNetworking.send(new EnchantPayloads.EnchantItem(gearSlot, def.id().toString(), chosenLevel, replaces));
        click(1.4f);
    }

    private boolean clickTomes(double mx, double my) {
        List<Entry> tomes = tomes();
        if (clickGrid(mx, my, tomes, true)) {
            return true;
        }
        ItemStack tome = selectedTome(tomes);
        if (!EnchantContent.isTome(tome)) {
            return false;
        }
        Map.Entry<Identifier, Integer> inside = EnchantContent.enchants(tome).levels().entrySet().iterator().next();
        Enchant def = Enchants.get(inside.getKey());
        if (def == null) {
            return false;
        }
        int x = panelX + 6;
        int w = panelW - 12;
        int by = bodyTop() + 52;
        int half = (w - 6) / 2;
        if (inside(mx, my, x, by, half, 15)) {
            int twin = twin(tomes, tome);
            if (twin >= 0 && inside.getValue() < def.maxLevel()) {
                ClientPlayNetworking.send(new EnchantPayloads.Combine(tomeSlot, twin));
                click(1.3f);
            } else {
                click(0.6f);
            }
            return true;
        }
        if (inside(mx, my, x + half + 6, by, w - half - 6, 15)) {
            String key = "salvage:" + tomeSlot;
            if (confirmKey.equals(key) && Util.getMillis() - confirmAt < CONFIRM_MILLIS) {
                confirmKey = "";
                ClientPlayNetworking.send(new EnchantPayloads.Salvage(tomeSlot));
                click(0.9f);
            } else {
                confirmKey = key;
                confirmAt = Util.getMillis();
                click(0.8f);
            }
            return true;
        }
        int listTop = by + 33;
        int listHeight = bodyBottom() - 4 - listTop;
        if (my < listTop || my >= listTop + listHeight) {
            return false;
        }
        List<Entry> targets = gear().stream().filter(entry -> def.appliesTo(entry.stack())).toList();
        int y = listTop - (int) Math.round(listScroll);
        for (Entry target : targets) {
            if (inside(mx, my, x + w - 62, y + 5, 56, 15)) {
                EnchantList current = EnchantContent.enchants(target.stack());
                if (Enchanting.resultLevel(def, current.level(def.id()), inside.getValue()) <= current.level(def.id())) {
                    click(0.6f);
                    return true;
                }
                boolean replaces = !replaced(def, current).isEmpty();
                String key = "tome:" + tomeSlot + ":" + target.slot();
                if (replaces && !(confirmKey.equals(key) && Util.getMillis() - confirmAt < CONFIRM_MILLIS)) {
                    confirmKey = key;
                    confirmAt = Util.getMillis();
                    click(0.8f);
                    return true;
                }
                confirmKey = "";
                ClientPlayNetworking.send(new EnchantPayloads.ApplyTome(tomeSlot, target.slot(), replaces));
                click(1.4f);
                return true;
            }
            y += APPLY_ROW + 2;
        }
        return false;
    }

    private boolean clickCodex(double mx, double my) {
        int fx = left + PAD;
        for (int i = 0; i < CODEX_FILTERS.length; i++) {
            int fw = font.width(CODEX_FILTERS[i]) + 10;
            if (inside(mx, my, fx, bodyTop(), fw, 14)) {
                codexFilter = i;
                codexScroll = 0;
                click(1.1f);
                return true;
            }
            fx += fw + 3;
        }
        return false;
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 2) {
            codexScroll -= scrollY * 24;
        } else if (mouseX < altarX) {
            gearScroll -= scrollY * SLOT;
        } else {
            listScroll -= scrollY * 24;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        // The bind button glows the altar up only while it's hovered.
        binding = false;
    }

    static MutableComponent rarityText(EnchantRarity rarity) {
        return Component.literal(rarity.label()).withStyle(Style.EMPTY.withColor(rarity.color()));
    }

    static Component muted(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
