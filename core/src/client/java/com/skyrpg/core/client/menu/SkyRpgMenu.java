package com.skyrpg.core.client.menu;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The central SkyRPG menu: where mods register pages and profile stats, and how it's opened. */
public final class SkyRpgMenu {
    private static final List<MenuPage> PAGES = new ArrayList<>();
    private static final List<ProfileStat> PROFILE_STATS = new ArrayList<>();
    private static Identifier lastPage;

    /** How big the menu window is drawn: Auto (about 80% of the screen), Compact or Large. See {@link MenuLayout}. */
    static final Setting MENU_SIZE = SkyRpgSettings.choice("interface.menu_size", SkyRpgSettings.INTERFACE, "Menu size",
                    "How big this menu is on your screen. Auto fits it to your monitor",
                    List.of(MenuLayout.Size.AUTO.label, MenuLayout.Size.COMPACT.label, MenuLayout.Size.LARGE.label),
                    MenuLayout.Size.AUTO.label, null)
            .section("Menu").keywords("scale", "zoom", "bigger", "smaller", "gui");

    private SkyRpgMenu() {
    }

    public static void registerPage(MenuPage page) {
        PAGES.add(page);
        PAGES.sort(Comparator.comparingInt(MenuPage::order));
    }

    public static void registerProfileStat(ProfileStat stat) {
        PROFILE_STATS.add(stat);
        PROFILE_STATS.sort(Comparator.comparingInt(ProfileStat::order));
    }

    public static List<MenuPage> pages() {
        return PAGES;
    }

    public static List<ProfileStat> profileStats() {
        return PROFILE_STATS;
    }

    /** Opens the menu on the page it was last left on. */
    public static void open() {
        open(lastPage);
    }

    public static void open(Identifier pageId) {
        if (PAGES.isEmpty()) {
            return;
        }
        MenuPage page = PAGES.stream().filter(p -> p.id().equals(pageId)).findFirst().orElse(PAGES.getFirst());
        Minecraft.getInstance().gui.setScreen(new SkyRpgMenuScreen(page));
    }

    /**
     * Opens a page by its short name, for commands and dev tools. Returns false if unknown. A part of the page can
     * follow a dash, and a leading "menu-" is ignored, so a screenshot's one-word page name can reach anything:
     * <ul>
     *   <li>"skills", "settings", "menu-profile": a page;</li>
     *   <li>"settings-hud", "menu-settings-weapon_fx": a Settings category (by name, any case, spaces as _);</li>
     *   <li>"settings-search-minimap": the Settings search, typed in (_ for spaces).</li>
     * </ul>
     */
    public static boolean open(String name) {
        String request = name.toLowerCase(Locale.ROOT);
        if (request.startsWith("menu-")) {
            request = request.substring(5);
        } else if (request.equals("menu")) {
            open();
            return true;
        }
        for (MenuPage page : PAGES) {
            if (page.id().getPath().equals(request) || page.id().toString().equals(request)) {
                open(page.id());
                return true;
            }
        }
        // "settings-hud": the longest page name that prefixes the request, then the rest goes to the page.
        MenuPage best = null;
        for (MenuPage page : PAGES) {
            String path = page.id().getPath();
            if (request.startsWith(path + "-") && (best == null || path.length() > best.id().getPath().length())) {
                best = page;
            }
        }
        if (best == null) {
            return false;
        }
        String sub = request.substring(best.id().getPath().length() + 1);
        open(best.id());
        best.showSubpage(sub);
        return true;
    }

    /**
     * Opens the Settings page on a category, e.g. {@code openSettings("Map")} for a "Map settings" button. Unknown
     * categories just open Settings.
     */
    public static void openSettings(String category) {
        Identifier settings = SkyRpgCore.id("settings");
        open(settings);
        for (MenuPage page : PAGES) {
            if (page.id().equals(settings)) {
                page.showSubpage(category);
            }
        }
    }

    static MenuLayout.Size size() {
        return MenuLayout.Size.of(MENU_SIZE.choice());
    }

    static void remember(MenuPage page) {
        lastPage = page.id();
    }
}
