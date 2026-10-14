package com.skyrpg.core.client.hud;

import com.google.gson.JsonObject;
import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.ClientConfig;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Owns every {@link HudWidget}: where each one is (anchor, offset, scale, shown), saved in
 * {@code config/skyrpg/client.json} under "hud", and drawing them each frame.
 *
 * <p>Widgets go where the player put them, but if one would cover vanilla's HUD (hotbar, food and
 * air, status effect icons, boss bars) or a widget placed before it, it is nudged to the nearest free
 * spot first: down for widgets pinned to the top, up for the bottom, either way for the middle. So a
 * growing events widget pushes the sidebar down instead of covering it. The HUD editor shows the
 * saved places.
 */
public final class HudManager {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    /** Space kept between a widget and whatever it was moved off. */
    private static final int GAP = 2;
    /** Height of the hotbar, XP bar and food/air rows at the bottom centre. */
    public static final int HOTBAR_BAND = 52;

    /** Where a widget sits and whether it's shown. */
    public static final class Layout {
        public HudAnchor anchor;
        public int x;
        public int y;
        public float scale;
        public boolean enabled;

        Layout(HudAnchor anchor, int x, int y, float scale, boolean enabled) {
            this.anchor = anchor;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.enabled = enabled;
        }
    }

    /** A widget's on-screen box in GUI pixels. */
    public record Box(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean intersects(Box other) {
            return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
        }
    }

    private static final List<HudWidget> WIDGETS = new ArrayList<>();
    /**
     * Widgets that draw themselves in their own HUD slot; HudManager only lays them out (see
     * {@link #drawsItself}). Made on first use, since a field added by hot-swapping skips its initializer.
     */
    private static Set<Identifier> selfDrawn;
    private static final Map<Identifier, Layout> LAYOUTS = new HashMap<>();
    private static Setting showHud;
    private static Field bossEvents;
    private static boolean bossEventsLookedUp;

    private HudManager() {
    }

    public static void register(HudWidget widget) {
        WIDGETS.add(widget);
        // A "Widgets" toggle in the HUD settings, sharing the editor's shown flag (and saved the way it saves).
        SkyRpgSettings.toggle("hud.widget." + widget.id().toString().replace(':', '.'), SkyRpgSettings.HUD,
                        widget.name().getString(), "Show or hide this info box (also in the HUD editor)", widget.enabledByDefault(),
                        () -> layout(widget).enabled, value -> {
                            layout(widget).enabled = value;
                            save();
                        })
                .section("Widgets").keywords("hud", "widget", "show", "hide", "info box");
    }

    /**
     * Marks a registered widget as drawing itself in its own HUD slot (the sidebar draws in vanilla's
     * scoreboard slot) at {@link #placedBox}: it is laid out with the others but not drawn by them.
     */
    public static void drawsItself(HudWidget widget) {
        if (selfDrawn == null) {
            selfDrawn = new HashSet<>();
        }
        selfDrawn.add(widget.id());
    }

    public static List<HudWidget> widgets() {
        return WIDGETS;
    }

    public static void init() {
        showHud = SkyRpgSettings.toggle("hud.enabled", SkyRpgSettings.HUD, "SkyRPG HUD", "Show info boxes like your purse and skill progress", true, null);
        SkyRpgSettings.action("hud.edit", SkyRpgSettings.HUD, "Edit HUD layout", "Drag, resize and hide every info box", "Edit",
                () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(Minecraft.getInstance().gui.screen())));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SkyRpgCore.id("widgets"), (g, deltaTracker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
                return;
            }
            if (showHud.get() && !(minecraft.gui.screen() instanceof HudEditorScreen)) {
                // A plain loop, not a lambda: new lambdas in here would renumber this one and break hot-swapping it.
                for (Map.Entry<HudWidget, Box> entry : place(minecraft.font, g.guiWidth(), g.guiHeight()).entrySet()) {
                    if (selfDrawn == null || !selfDrawn.contains(entry.getKey().id())) {
                        draw(g, minecraft.font, entry.getKey(), entry.getValue(), false);
                    }
                }
            }
            // Banners go over the widgets (and keep clear of them where they can, see Banners).
            Banners.renderOnTop(g, minecraft.font);
        });
    }

    public static Layout layout(HudWidget widget) {
        return LAYOUTS.computeIfAbsent(widget.id(), id -> {
            Layout layout = defaultLayout(widget);
            JsonObject saved = ClientConfig.section("hud").getAsJsonObject(id.toString());
            if (saved != null) {
                try {
                    layout.anchor = HudAnchor.valueOf(saved.get("anchor").getAsString());
                    layout.x = saved.get("x").getAsInt();
                    layout.y = saved.get("y").getAsInt();
                    layout.scale = Mth.clamp(saved.get("scale").getAsFloat(), MIN_SCALE, MAX_SCALE);
                    layout.enabled = saved.get("enabled").getAsBoolean();
                } catch (RuntimeException e) {
                    SkyRpgCore.LOGGER.warn("Ignoring broken HUD layout for {}", id);
                }
            }
            return layout;
        });
    }

    private static Layout defaultLayout(HudWidget widget) {
        return new Layout(widget.defaultAnchor(), widget.defaultX(), widget.defaultY(), 1f, widget.enabledByDefault());
    }

    private static boolean isDefault(HudWidget widget, Layout layout) {
        return layout.anchor == widget.defaultAnchor() && layout.x == widget.defaultX() && layout.y == widget.defaultY()
                && layout.scale == 1f && layout.enabled == widget.enabledByDefault();
    }

    /** Where the player put the widget (clamped to the screen), before any nudging. The editor works with this. */
    public static Box box(HudWidget widget, Font font, int screenWidth, int screenHeight) {
        Layout layout = layout(widget);
        int width = Math.round(widget.width(font) * layout.scale);
        int height = Math.round(widget.height(font) * layout.scale);
        int x = Math.round(screenWidth * layout.anchor.fx - width * layout.anchor.fx) + layout.x;
        int y = Math.round(screenHeight * layout.anchor.fy - height * layout.anchor.fy) + layout.y;
        return new Box(Mth.clamp(x, 0, Math.max(0, screenWidth - width)), Mth.clamp(y, 0, Math.max(0, screenHeight - height)), width, height);
    }

    /**
     * The left edge a widget of {@code width} (scaled) has at its saved place. For widgets that size
     * themselves around their neighbours without needing their own height (which {@link #box} would ask for).
     */
    public static int left(HudWidget widget, int width, int screenWidth) {
        Layout layout = layout(widget);
        int x = Math.round(screenWidth * layout.anchor.fx - width * layout.anchor.fx) + layout.x;
        return Mth.clamp(x, 0, Math.max(0, screenWidth - width));
    }

    /** True if a box spanning {@code x .. x + width} is above the hotbar, which with the XP and food rows takes {@link #HOTBAR_BAND} at the bottom. */
    public static boolean overHotbar(int x, int width, int screenWidth) {
        return x < screenWidth / 2 + 91 && screenWidth / 2 - 91 < x + width;
    }

    /** Where the widget is drawn this frame: its {@link #box}, moved off anything it would cover. */
    public static Box placedBox(HudWidget widget, Font font, int screenWidth, int screenHeight) {
        Box placed = place(font, screenWidth, screenHeight).get(widget);
        return placed != null ? placed : box(widget, font, screenWidth, screenHeight);
    }

    /** Everything on the HUD right now that an overlay (like a banner) should keep clear of. */
    public static List<Box> occupied(Font font, int screenWidth, int screenHeight) {
        List<Box> boxes = new ArrayList<>(vanillaBoxes(screenWidth, screenHeight));
        boxes.addAll(place(font, screenWidth, screenHeight).values());
        return boxes;
    }

    /**
     * Lays out every shown widget: top-pinned ones first, then bottom, then middle (the most free to
     * move), each in registration order, each moved off vanilla's HUD and the ones before it.
     */
    private static Map<HudWidget, Box> place(Font font, int screenWidth, int screenHeight) {
        List<Box> taken = new ArrayList<>(vanillaBoxes(screenWidth, screenHeight));
        int vanilla = taken.size();
        List<HudWidget> order = new ArrayList<>();
        for (HudWidget widget : WIDGETS) {
            if (layout(widget).enabled && widget.visible()) {
                order.add(widget);
            }
        }
        order.sort(Comparator.comparingInt(widget -> rowOrder(layout(widget).anchor)));
        Map<HudWidget, Box> placed = new LinkedHashMap<>();
        for (HudWidget widget : order) {
            Box free = findFree(box(widget, font, screenWidth, screenHeight), layout(widget).anchor, taken, vanilla, screenWidth, screenHeight);
            placed.put(widget, free);
            taken.add(free);
        }
        return placed;
    }

    private static int rowOrder(HudAnchor anchor) {
        return anchor.fy == 0 ? 0 : anchor.fy == 1 ? 1 : 2;
    }

    /**
     * The nearest spot for {@code wanted} that covers nothing in {@code taken}: up or down first, then
     * sideways but never into the middle third (that's where the player is looking). If nothing is free,
     * the up-or-down spot that covers the least, counting vanilla's HUD (the first {@code vanilla}
     * boxes) four times over.
     */
    private static Box findFree(Box wanted, HudAnchor anchor, List<Box> taken, int vanilla, int screenWidth, int screenHeight) {
        if (free(wanted, taken)) {
            return wanted;
        }
        // Up or down, staying in the same column: just past the edge of something in the way.
        List<Integer> ys = new ArrayList<>();
        for (Box box : taken) {
            ys.add(box.y() + box.height() + GAP);
            ys.add(box.y() - wanted.height() - GAP);
        }
        ys.removeIf(y -> y < 0 || y > screenHeight - wanted.height()
                || (anchor.fy == 0 && y < wanted.y()) || (anchor.fy == 1 && y > wanted.y()));
        ys.sort(Comparator.comparingInt(y -> Math.abs(y - wanted.y())));
        for (int y : ys) {
            Box moved = new Box(wanted.x(), y, wanted.width(), wanted.height());
            if (free(moved, taken)) {
                return moved;
            }
        }
        // Then sideways, away from the edge it's pinned to.
        List<Integer> xs = new ArrayList<>();
        for (Box box : taken) {
            xs.add(box.x() + box.width() + GAP);
            xs.add(box.x() - wanted.width() - GAP);
        }
        xs.removeIf(x -> x < 0 || x > screenWidth - wanted.width()
                || (anchor.fx == 0 && x < wanted.x()) || (anchor.fx == 1 && x > wanted.x())
                || (x < screenWidth * 2 / 3 && x + wanted.width() > screenWidth / 3));
        xs.sort(Comparator.comparingInt(x -> Math.abs(x - wanted.x())));
        for (int x : xs) {
            Box moved = new Box(x, wanted.y(), wanted.width(), wanted.height());
            if (free(moved, taken)) {
                return moved;
            }
        }
        // Nowhere free (a small screen with a lot showing): cover as little as possible.
        Box best = wanted;
        long bestCovered = covered(wanted, taken, vanilla);
        for (int y : ys) {
            Box moved = new Box(wanted.x(), y, wanted.width(), wanted.height());
            long covered = covered(moved, taken, vanilla);
            if (covered < bestCovered) {
                best = moved;
                bestCovered = covered;
            }
        }
        return best;
    }

    private static long covered(Box box, List<Box> taken, int vanilla) {
        long total = 0;
        for (int i = 0; i < taken.size(); i++) {
            Box other = taken.get(i);
            long w = Math.min(box.x() + box.width(), other.x() + other.width()) - Math.max(box.x(), other.x());
            long h = Math.min(box.y() + box.height(), other.y() + other.height()) - Math.max(box.y(), other.y());
            if (w > 0 && h > 0) {
                total += w * h * (i < vanilla ? 4 : 1);
            }
        }
        return total;
    }

    private static boolean free(Box box, List<Box> taken) {
        for (Box other : taken) {
            if (box.intersects(other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vanilla HUD that widgets keep clear of: the hotbar with the XP bar, the food and air rows (the
     * health and mana bars replace the hearts and armor on the left), status effect icons in the top
     * right and boss bars at the top.
     */
    private static List<Box> vanillaBoxes(int screenWidth, int screenHeight) {
        List<Box> boxes = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof HudEditorScreen) {
            return boxes;
        }
        int center = screenWidth / 2;
        boxes.add(new Box(center - 91, screenHeight - 29, 182, 29));
        boxes.add(new Box(center + 1, screenHeight - 49, 90, 20));
        if (minecraft.player != null) {
            int beneficial = 0;
            int harmful = 0;
            for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
                if (effect.showIcon()) {
                    if (effect.getEffect().value().isBeneficial()) {
                        beneficial++;
                    } else {
                        harmful++;
                    }
                }
            }
            int columns = Math.max(beneficial, harmful);
            if (columns > 0) {
                boxes.add(new Box(screenWidth - 25 * columns - 1, 0, 25 * columns + 1, harmful > 0 ? 52 : 26));
            }
        }
        int bossBars = bossBarCount(minecraft);
        if (bossBars > 0) {
            boxes.add(new Box(center - 100, 0, 200, Math.min(screenHeight / 3, 19 * bossBars + 2)));
        }
        return boxes;
    }

    /** Boss bars showing; BossHealthOverlay keeps them private, so this reads its map (0 if that fails). */
    private static int bossBarCount(Minecraft minecraft) {
        if (!bossEventsLookedUp) {
            bossEventsLookedUp = true;
            try {
                bossEvents = BossHealthOverlay.class.getDeclaredField("events");
                bossEvents.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                bossEvents = null;
            }
        }
        if (bossEvents == null) {
            return 0;
        }
        try {
            return ((Map<?, ?>) bossEvents.get(minecraft.gui.hud.getBossOverlay())).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** Re-pins a widget to whichever screen third its centre is in, keeping it where it is. */
    static void moveTo(HudWidget widget, Font font, int boxX, int boxY, int screenWidth, int screenHeight) {
        Layout layout = layout(widget);
        int width = Math.round(widget.width(font) * layout.scale);
        int height = Math.round(widget.height(font) * layout.scale);
        boxX = Mth.clamp(boxX, 0, Math.max(0, screenWidth - width));
        boxY = Mth.clamp(boxY, 0, Math.max(0, screenHeight - height));
        layout.anchor = HudAnchor.nearest(boxX + width / 2.0, boxY + height / 2.0, screenWidth, screenHeight);
        layout.x = boxX - Math.round(screenWidth * layout.anchor.fx - width * layout.anchor.fx);
        layout.y = boxY - Math.round(screenHeight * layout.anchor.fy - height * layout.anchor.fy);
    }

    static void reset(HudWidget widget) {
        LAYOUTS.remove(widget.id());
        ClientConfig.section("hud").remove(widget.id().toString());
    }

    /** Saves the widgets that were changed; ones left at their defaults follow any new defaults. */
    static void save() {
        JsonObject hud = ClientConfig.section("hud");
        for (HudWidget widget : WIDGETS) {
            Layout layout = layout(widget);
            if (isDefault(widget, layout)) {
                hud.remove(widget.id().toString());
                continue;
            }
            JsonObject saved = new JsonObject();
            saved.addProperty("anchor", layout.anchor.name());
            saved.addProperty("x", layout.x);
            saved.addProperty("y", layout.y);
            saved.addProperty("scale", layout.scale);
            saved.addProperty("enabled", layout.enabled);
            hud.add(widget.id().toString(), saved);
        }
        ClientConfig.save();
    }

    /** Draws at the saved place; the HUD editor uses this. */
    static void draw(GuiGraphicsExtractor g, Font font, HudWidget widget, boolean editing) {
        draw(g, font, widget, box(widget, font, g.guiWidth(), g.guiHeight()), editing);
    }

    private static void draw(GuiGraphicsExtractor g, Font font, HudWidget widget, Box box, boolean editing) {
        float scale = layout(widget).scale;
        g.pose().pushMatrix();
        g.pose().translate(box.x(), box.y());
        g.pose().scale(scale, scale);
        widget.render(g, font, editing);
        g.pose().popMatrix();
    }
}
