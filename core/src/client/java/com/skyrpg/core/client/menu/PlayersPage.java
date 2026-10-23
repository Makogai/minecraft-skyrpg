package com.skyrpg.core.client.menu;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.records.PlayerRecords;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Everyone who has played on the server, and their statistics: a list of players on the left (online first), and the
 * picked player's page on the right with headline tiles (playtime, distance, blocks mined, kills, deaths), every
 * statistic by section, and their most mined blocks, crafts and kills. Hovering a number ranks it among all players.
 * The numbers come from the server ({@link PlayerRecords}) when the page opens and every few seconds while it's shown.
 */
public final class PlayersPage implements MenuPage {
    private static final Gson GSON = new Gson();
    private static final long REFRESH_MS = 5_000;
    private static final int LIST_MIN = 96;
    private static final int LIST_MAX = 132;
    /** Below this page width the list shows faces only. */
    private static final int COMPACT_BELOW = 330;
    private static final int COMPACT_LIST = 24;
    private static final int ROW = 24;
    private static final int GAP = 6;
    private static final int HEADER = 40;
    private static final int HERO = 36;
    private static final int HERO_MIN = 76;
    private static final int STAT = 18;
    private static final int STAT_MIN = 128;
    private static final int SLOT = 22;
    private static final int HEADING = 14;
    private static final String[] HEROES = {"play_time", "distance", "mined", "mob_kills", "deaths"};
    /** Sections in this order; any others a mod adds go after them. */
    private static final List<String> SECTION_ORDER = List.of("SkyRPG", "Skills", "Time & Life", "Travel", "Mining & Building", "Combat");

    private static List<Player> players = List.of();
    private static long serverNow;
    private static long receivedAt;
    private static long requestedAt;
    private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();
    private static final Set<UUID> SKIN_ASKED = new HashSet<>();
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private UUID selected;
    private double listScroll;
    private double scroll;
    private double scrollTarget;
    private int contentHeight;
    private long lastFrame = Util.getMillis();

    private record Entry(String section, String key, String label, String format, String icon, long value) {
    }

    private record Count(String id, long count) {
    }

    private record Player(UUID id, String name, boolean online, long first, long last, String skin, String signature,
                          List<Entry> entries, Map<String, Entry> byKey, List<Count> mined, List<Count> crafts, List<Count> killed,
                          List<Count> killedBy) {
        long value(String key) {
            Entry entry = byKey.get(key);
            return entry == null ? 0 : entry.value();
        }
    }

    // ------------------------------------------------------------------ data

    /** The server's answer to {@link PlayerRecords.Request}. */
    public static void receive(String json) {
        try {
            JsonObject board = GSON.fromJson(json, JsonObject.class);
            serverNow = board.has("now") ? board.get("now").getAsLong() : System.currentTimeMillis();
            receivedAt = Util.getMillis();
            List<Player> list = new ArrayList<>();
            for (JsonElement element : board.getAsJsonArray("players")) {
                list.add(parse(element.getAsJsonObject()));
            }
            UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
            list.sort(Comparator.comparing((Player p) -> !p.id().equals(self)).thenComparing(p -> !p.online())
                    .thenComparing(Comparator.comparingLong((Player p) -> p.value("play_time")).reversed()));
            players = List.copyOf(list);
        } catch (RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Bad player records from the server", e);
        }
    }

    private static Player parse(JsonObject o) {
        List<Entry> entries = new ArrayList<>();
        Map<String, Entry> byKey = new HashMap<>();
        if (o.has("entries")) {
            for (JsonElement element : o.getAsJsonArray("entries")) {
                JsonObject e = element.getAsJsonObject();
                Entry entry = new Entry(e.get("s").getAsString(), e.get("k").getAsString(), e.get("l").getAsString(),
                        e.get("f").getAsString(), e.get("i").getAsString(), e.get("v").getAsLong());
                entries.add(entry);
                byKey.put(entry.key(), entry);
            }
        }
        String skin = null;
        String signature = null;
        if (o.has("skin")) {
            JsonArray pair = o.getAsJsonArray("skin");
            skin = pair.get(0).getAsString();
            signature = pair.size() > 1 && !pair.get(1).isJsonNull() ? pair.get(1).getAsString() : null;
        }
        return new Player(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(),
                o.has("online") && o.get("online").getAsBoolean(), o.has("first") ? o.get("first").getAsLong() : 0,
                o.has("last") ? o.get("last").getAsLong() : 0, skin, signature, entries, byKey,
                counts(o, "mined"), counts(o, "crafts"), counts(o, "killed"), counts(o, "killed_by"));
    }

    private static List<Count> counts(JsonObject o, String name) {
        List<Count> list = new ArrayList<>();
        if (o.has(name)) {
            for (JsonElement element : o.getAsJsonArray(name)) {
                JsonArray pair = element.getAsJsonArray();
                list.add(new Count(pair.get(0).getAsString(), pair.get(1).getAsLong()));
            }
        }
        return list;
    }

    private static void request() {
        requestedAt = Util.getMillis();
        if (ClientPlayNetworking.canSend(PlayerRecords.Request.TYPE)) {
            ClientPlayNetworking.send(new PlayerRecords.Request());
        }
    }

    private Player current() {
        for (Player player : players) {
            if (player.id().equals(selected)) {
                return player;
            }
        }
        return players.isEmpty() ? null : players.getFirst();
    }

    // ------------------------------------------------------------------ page

    @Override
    public Identifier id() {
        return SkyRpgCore.id("players");
    }

    @Override
    public Component title() {
        return Component.literal("Players");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Everyone's statistics: playtime, travel, mining, kills and more");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public void opened() {
        scroll = scrollTarget = 0;
        request();
    }

    @Override
    public Component hint() {
        return Component.literal("↑↓ player · hover a number to compare");
    }

    private static boolean compact(PageContext ctx) {
        return ctx.width() < COMPACT_BELOW;
    }

    private static int listWidth(PageContext ctx) {
        return compact(ctx) ? COMPACT_LIST : Mth.clamp(ctx.width() / 4, LIST_MIN, LIST_MAX);
    }

    @Override
    public void render(PageContext ctx) {
        if (Util.getMillis() - requestedAt > REFRESH_MS) {
            request();
        }
        GuiGraphicsExtractor g = ctx.graphics();
        if (players.isEmpty()) {
            String text = receivedAt == 0 ? "Asking the server…" : "No players yet";
            Draw.fit(g, ctx.font(), Component.literal(text), ctx.x() + ctx.width() / 2, ctx.y() + ctx.height() / 2 - 4, ctx.width() - 8,
                    Draw.Align.CENTER, Theme.DIM);
            return;
        }
        long now = Util.getMillis();
        float frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        Player player = current();
        selected = player.id();
        drawList(ctx, player);

        int listWidth = listWidth(ctx);
        int x = ctx.x() + listWidth + GAP + 2;
        int width = ctx.width() - listWidth - GAP - 2;
        double maxScroll = Math.max(0, contentHeight - ctx.height());
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll += (scrollTarget - scroll) * Math.min(1, frameSeconds * 16);
        if (Math.abs(scrollTarget - scroll) < 0.3) {
            scroll = scrollTarget;
        }
        g.enableScissor(x, ctx.y(), x + width, ctx.y() + ctx.height());
        int top = ctx.y() - (int) Math.round(scroll);
        contentHeight = drawDetail(ctx, player, x, top, width) - top;
        g.disableScissor();
        if (scroll > 0.5) {
            g.fillGradient(x, ctx.y(), x + width, ctx.y() + 8, Theme.WINDOW, Theme.WINDOW & 0x00FFFFFF);
        }
        if (scroll < maxScroll - 0.5) {
            g.fillGradient(x, ctx.y() + ctx.height() - 8, x + width, ctx.y() + ctx.height(), Theme.WINDOW & 0x00FFFFFF, Theme.WINDOW);
        }
        if (contentHeight > ctx.height()) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, ctx.y(), ctx.height(), contentHeight, scroll, Theme.DIM);
        }
    }

    // ------------------------------------------------------------------ the player list

    private void drawList(PageContext ctx, Player current) {
        GuiGraphicsExtractor g = ctx.graphics();
        int w = listWidth(ctx);
        int x = ctx.x();
        int content = players.size() * (ROW + 2);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, content - ctx.height()));
        g.enableScissor(x, ctx.y(), x + w, ctx.y() + ctx.height());
        int y = ctx.y() - (int) listScroll;
        for (Player player : players) {
            boolean picked = player == current;
            boolean hovered = ctx.hovered(x, y, w, ROW) && ctx.mouseY() >= ctx.y() && ctx.mouseY() < ctx.y() + ctx.height();
            Draw.panel(g, x, y, w, ROW, picked ? Theme.CARD_HOVER : hovered ? Theme.CARD_HOVER : Theme.CARD,
                    picked ? Theme.ACCENT : Theme.CARD_BORDER);
            if (compact(ctx)) {
                face(g, player, x + (w - 14) / 2, y + 5, 14);
                dot(g, player, x + w - 6, y + 3);
                if (hovered) {
                    ctx.tooltip(List.of(Component.literal(player.name()).withColor(Theme.TEXT), status(player)), Theme.ACCENT);
                }
            } else {
                face(g, player, x + 5, y + 4, 16);
                dot(g, player, x + 17, y + 16);
                Draw.fitText(g, ctx.font(), player.name(), x + 26, y + 4, w - 30, picked ? Theme.ACCENT : Theme.TEXT);
                Draw.fitText(g, ctx.font(), Component.literal(time(player.value("play_time")) + " played").withColor(Theme.DIM),
                        x + 26, y + 14, w - 30, Theme.DIM);
            }
            y += ROW + 2;
        }
        g.disableScissor();
    }

    private static void dot(GuiGraphicsExtractor g, Player player, int x, int y) {
        if (player.online()) {
            g.fill(x - 1, y - 1, x + 4, y + 4, Theme.CARD);
            g.fill(x, y, x + 3, y + 3, Theme.GREEN);
        }
    }

    private static Component status(Player player) {
        return player.online() ? Component.literal("● Online now").withColor(Theme.GREEN)
                : Component.literal("Last seen " + ago(player.last())).withColor(Theme.MUTED);
    }

    // ------------------------------------------------------------------ the picked player's page

    /** Draws from {@code y} down and returns where it ended. */
    private int drawDetail(PageContext ctx, Player player, int x, int y, int width) {
        GuiGraphicsExtractor g = ctx.graphics();

        // Header: face, name, online or last seen, first joined.
        Draw.panel(g, x, y, width, HEADER, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, Draw.withAlpha(Theme.ACCENT, 0.6f));
        face(g, player, x + 8, y + 8, 24);
        Draw.fit(g, ctx.font(), Component.literal(player.name()), x + 40, y + 8, width - 48, 1.5f, 1f, Draw.Align.LEFT, Theme.TEXT, false);
        String joined = player.first() > 0 ? "  ·  First seen " + ago(player.first()) : "";
        Draw.fitText(g, ctx.font(), status(player).copy().append(Component.literal(joined).withColor(Theme.DIM)), x + 40, y + 24,
                width - 48, Theme.MUTED);
        y += HEADER + GAP;

        // Headline tiles.
        List<Entry> heroes = new ArrayList<>();
        for (String key : HEROES) {
            Entry entry = player.byKey().get(key);
            if (entry != null) {
                heroes.add(entry);
            }
        }
        int columns = Mth.clamp((width + GAP) / (HERO_MIN + GAP), 1, Math.max(1, heroes.size()));
        int tileWidth = (width - GAP * (columns - 1)) / columns;
        for (int i = 0; i < heroes.size(); i++) {
            Entry entry = heroes.get(i);
            int tx = x + (i % columns) * (tileWidth + GAP);
            int ty = y + (i / columns) * (HERO + GAP);
            boolean hovered = visible(ctx, ty, HERO) && ctx.hovered(tx, ty, tileWidth, HERO);
            Draw.panel(g, tx, ty, tileWidth, HERO, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.item(g, icon(entry.icon()), tx + 5, ty + (HERO - 16) / 2, 1f);
            int room = tileWidth - 30;
            Draw.fitNumber(g, ctx.font(), format(entry), tx + 25, ty + 8, room, Draw.Align.LEFT, Theme.TEXT);
            Draw.fitText(g, ctx.font(), entry.label(), tx + 25, ty + 20, room, Theme.MUTED);
            int rank = rank(entry.key(), player);
            if (rank > 0 && players.size() > 1) {
                String chip = "#" + rank;
                int chipWidth = ctx.font().width(chip) + 4;
                Draw.rounded(g, tx + tileWidth - chipWidth - 3, ty + 3, chipWidth, 10, rank == 1 ? Theme.ACCENT_SOFT : Theme.KEY);
                Draw.text(g, ctx.font(), chip, tx + tileWidth - chipWidth - 1, ty + 4, rank == 1 ? Theme.ACCENT : Theme.DIM);
            }
            if (hovered) {
                ranking(ctx, entry, player);
            }
        }
        y += ((heroes.size() + columns - 1) / columns) * (HERO + GAP) + 2;

        // Every statistic, by section.
        Map<String, List<Entry>> sections = new LinkedHashMap<>();
        SECTION_ORDER.forEach(name -> sections.put(name, new ArrayList<>()));
        for (Entry entry : player.entries()) {
            sections.computeIfAbsent(entry.section(), name -> new ArrayList<>()).add(entry);
        }
        int statColumns = Mth.clamp((width + GAP) / (STAT_MIN + GAP), 1, 3);
        int statWidth = (width - GAP * (statColumns - 1)) / statColumns;
        for (Map.Entry<String, List<Entry>> section : sections.entrySet()) {
            if (section.getValue().isEmpty()) {
                continue;
            }
            y = heading(ctx, section.getKey(), x, y, width);
            List<Entry> entries = section.getValue();
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                int sx = x + (i % statColumns) * (statWidth + GAP);
                int sy = y + (i / statColumns) * (STAT + 2);
                boolean hovered = visible(ctx, sy, STAT) && ctx.hovered(sx, sy, statWidth, STAT);
                Draw.panel(g, sx, sy, statWidth, STAT, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.ACCENT : Theme.CARD_BORDER);
                Draw.item(g, icon(entry.icon()), sx + 3, sy + 3, 0.75f);
                String value = format(entry);
                int valueWidth = Math.min(ctx.font().width(value), statWidth / 2);
                Draw.fitText(g, ctx.font(), entry.label(), sx + 19, sy + 5, statWidth - valueWidth - 26, Theme.MUTED);
                Draw.fitNumber(g, ctx.font(), value, sx + statWidth - 5 - valueWidth, sy + 5, valueWidth, Draw.Align.RIGHT,
                        entry.value() == 0 ? Theme.DIM : Theme.TEXT);
                if (hovered) {
                    ranking(ctx, entry, player);
                }
            }
            y += ((entries.size() + statColumns - 1) / statColumns) * (STAT + 2) + 4;
        }

        y = slots(ctx, "Most mined", player.mined(), false, x, y, width);
        y = slots(ctx, "Most crafted", player.crafts(), false, x, y, width);
        y = slots(ctx, "Most killed", player.killed(), true, x, y, width);
        y = slots(ctx, "Killed by", player.killedBy(), true, x, y, width);
        return y;
    }

    private static boolean visible(PageContext ctx, int y, int h) {
        return ctx.mouseY() >= ctx.y() && ctx.mouseY() < ctx.y() + ctx.height() && y + h > ctx.y() && y < ctx.y() + ctx.height();
    }

    private static int heading(PageContext ctx, String name, int x, int y, int width) {
        String label = name.toUpperCase(Locale.ROOT);
        Draw.text(ctx.graphics(), ctx.font(), label, x + 2, y + 2, Theme.DIM);
        int lineX = x + 2 + ctx.font().width(label) + 6;
        ctx.graphics().fill(lineX, y + 6, x + width, y + 7, Theme.BORDER);
        return y + HEADING;
    }

    /** A row of item slots with counts, e.g. the blocks mined most. */
    private static int slots(PageContext ctx, String name, List<Count> counts, boolean mobs, int x, int y, int width) {
        if (counts.isEmpty()) {
            return y;
        }
        GuiGraphicsExtractor g = ctx.graphics();
        y = heading(ctx, name, x, y, width);
        int perRow = Math.max(1, (width + 3) / (SLOT + 3));
        for (int i = 0; i < counts.size(); i++) {
            Count count = counts.get(i);
            int sx = x + (i % perRow) * (SLOT + 3);
            int sy = y + (i / perRow) * (SLOT + 3);
            boolean hovered = visible(ctx, sy, SLOT) && ctx.hovered(sx, sy, SLOT, SLOT);
            Draw.panel(g, sx, sy, SLOT, SLOT, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.item(g, mobs ? mobIcon(count.id()) : icon(count.id()), sx + 3, sy + 3, 1f);
            String short_ = compactCount(count.count());
            g.pose().pushMatrix();
            g.pose().translate(sx + SLOT - 2, sy + SLOT - 7);
            g.pose().scale(0.75f, 0.75f);
            Draw.rightText(g, ctx.font(), short_, 0, 0, Theme.TEXT);
            g.pose().popMatrix();
            if (hovered) {
                Component title = mobs ? mobName(count.id()) : icon(count.id()).getHoverName();
                ctx.tooltip(List.of(title.copy().withColor(Theme.TEXT),
                        Component.literal(String.format(Locale.ROOT, "%,d", count.count())).withColor(Theme.ACCENT)), Theme.ACCENT);
            }
        }
        return y + ((counts.size() + perRow - 1) / perRow) * (SLOT + 3) + 4;
    }

    /** Hovering a number: where everyone stands on it. */
    private static void ranking(PageContext ctx, Entry entry, Player shown) {
        List<Player> ranked = new ArrayList<>(players.stream().filter(p -> p.byKey().containsKey(entry.key())).toList());
        ranked.sort(Comparator.comparingLong((Player p) -> p.value(entry.key())).reversed());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entry.label()).withColor(Theme.ACCENT));
        for (int i = 0; i < ranked.size() && i < 10; i++) {
            Player p = ranked.get(i);
            Entry theirs = p.byKey().get(entry.key());
            boolean self = p == shown;
            lines.add(Component.literal("#" + (i + 1) + " ").withColor(i == 0 ? Theme.ACCENT : Theme.DIM)
                    .append(Component.literal(p.name()).withColor(self ? Theme.TEXT : Theme.MUTED))
                    .append(Component.literal("  " + format(theirs)).withColor(self ? Theme.ACCENT : Theme.TEXT)));
        }
        ctx.tooltip(lines, Theme.ACCENT);
    }

    private static int rank(String key, Player player) {
        long value = player.value(key);
        if (!player.byKey().containsKey(key)) {
            return 0;
        }
        int rank = 1;
        for (Player other : players) {
            if (other.byKey().containsKey(key) && other.value(key) > value) {
                rank++;
            }
        }
        return rank;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double mx, double my, int button) {
        int w = listWidth(ctx);
        if (button != InputConstants.MOUSE_BUTTON_LEFT || mx < ctx.x() || mx >= ctx.x() + w || my < ctx.y() || my >= ctx.y() + ctx.height()) {
            return false;
        }
        int index = (int) ((my - ctx.y() + listScroll) / (ROW + 2));
        if (index >= 0 && index < players.size()) {
            pick(players.get(index));
            return true;
        }
        return false;
    }

    private void pick(Player player) {
        if (!player.id().equals(selected)) {
            selected = player.id();
            scroll = scrollTarget = 0;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
        }
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double mx, double my, double amount) {
        if (mx < ctx.x() + listWidth(ctx)) {
            listScroll -= amount * 26;
        } else {
            scrollTarget -= amount * 32;
        }
        return true;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        if (players.isEmpty() || event.key() != InputConstants.KEY_UP && event.key() != InputConstants.KEY_DOWN) {
            return false;
        }
        int index = players.indexOf(current());
        index = Mth.clamp(index + (event.key() == InputConstants.KEY_DOWN ? 1 : -1), 0, players.size() - 1);
        pick(players.get(index));
        int rowTop = index * (ROW + 2);
        listScroll = Mth.clamp(listScroll, rowTop + ROW - ctx.height(), rowTop);
        return true;
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void face(GuiGraphicsExtractor g, Player player, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(g, skin(player), x, y, size, player.online() ? 0xFFFFFFFF : 0xFFB8B8B8);
    }

    /** From the tab list when they're online, else their saved skin (fetched once), else the default for their UUID. */
    private static PlayerSkin skin(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.id());
            if (info != null) {
                return info.getSkin();
            }
        }
        PlayerSkin known = SKINS.get(player.id());
        if (known != null) {
            return known;
        }
        if (player.skin() != null && SKIN_ASKED.add(player.id())) {
            GameProfile profile = new GameProfile(player.id(), player.name(), new PropertyMap(ImmutableMultimap.of("textures",
                    new Property("textures", player.skin(), player.signature()))));
            minecraft.getSkinManager().get(profile).thenAccept(skin -> skin.ifPresent(s -> minecraft.execute(() -> SKINS.put(player.id(), s))));
        }
        return DefaultPlayerSkin.get(player.id());
    }

    private static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            Item item = parsed == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.BARRIER);
            return new ItemStack(item);
        });
    }

    private static ItemStack mobIcon(String entity) {
        return ICONS.computeIfAbsent("mob:" + entity, key -> {
            Identifier id = Identifier.tryParse(entity);
            if (id == null) {
                return new ItemStack(Items.SKELETON_SKULL);
            }
            if (id.getPath().equals("player")) {
                return new ItemStack(Items.PLAYER_HEAD);
            }
            Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
            return new ItemStack(BuiltInRegistries.ITEM.getOptional(egg).orElse(Items.SKELETON_SKULL));
        });
    }

    private static Component mobName(String entity) {
        Identifier id = Identifier.tryParse(entity);
        return id == null ? Component.literal(entity)
                : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription()).orElse(Component.literal(id.getPath()));
    }

    // ------------------------------------------------------------------ formatting

    private static String format(Entry entry) {
        long v = entry.value();
        return switch (entry.format()) {
            case "time" -> time(v);
            case "distance" -> v >= 100_000 ? String.format(Locale.ROOT, "%,.1f km", v / 100_000.0) : String.format(Locale.ROOT, "%,d m", v / 100);
            case "damage" -> String.format(Locale.ROOT, "%,d", v / 10);
            case "average" -> String.format(Locale.ROOT, "%.1f", v / 100.0);
            default -> String.format(Locale.ROOT, "%,d", v);
        };
    }

    /** Game ticks as "2d 4h", "3h 12m", "12m" or "40s". */
    private static String time(long ticks) {
        long seconds = ticks / 20;
        long days = seconds / 86_400;
        long hours = seconds / 3600 % 24;
        long minutes = seconds / 60 % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes > 0 ? minutes + "m" : seconds + "s";
    }

    /** How long before the server's "now" (as of the last answer) a moment was. */
    private static String ago(long millis) {
        long minutes = Math.max(0, serverNow + (Util.getMillis() - receivedAt) - millis) / 60_000;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        if (minutes < 60 * 24) {
            return minutes / 60 + "h ago";
        }
        return minutes / (60 * 24) + "d ago";
    }

    private static String compactCount(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000) {
            return n / 1000 + "k";
        }
        if (n >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        }
        return Long.toString(n);
    }
}
