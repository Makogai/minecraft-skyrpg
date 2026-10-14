package com.skyrpg.core.client.config;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Settings shown on the SkyRPG menu's Settings page, grouped by category in registration order.
 * Mods register their own:
 * <pre>
 * Setting damageNumbers = SkyRpgSettings.toggle("combat.damage_numbers", SkyRpgSettings.GAMEPLAY,
 *         "Damage numbers", "Show floating damage when you hit mobs", true, null);
 * if (damageNumbers.get()) { ... }
 *
 * Setting quality = SkyRpgSettings.choice("vfx.quality", "Effects", "Particle quality", "How many particles",
 *         List.of("Low", "Medium", "High"), "High", null).section("Particles");
 * if (quality.is("Low")) { ... }
 *
 * Setting volume = SkyRpgSettings.slider("chat.ping_volume", "Chat", "Mention sound", "How loud a mention pings",
 *         0, 1, 0.05, 0.8, null).format(v -> Math.round(v * 100) + "%");
 * float v = (float) volume.number();
 *
 * SkyRpgSettings.categoryIcon("Effects", new ItemStack(Items.BLAZE_POWDER));
 * </pre>
 * Every value is saved in {@code config/skyrpg/client.json} as soon as it changes.
 */
public final class SkyRpgSettings {
    public static final String INTERFACE = "Interface";
    public static final String HUD = "HUD";
    public static final String GAMEPLAY = "Gameplay";

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();
    private static final Map<String, SettingCategory> CATEGORIES = new LinkedHashMap<>();

    private SkyRpgSettings() {
    }

    /** A remembered on/off setting. {@code onChange} (optional) runs whenever it's flipped. */
    public static Setting toggle(String id, String category, String label, String description, boolean defaultValue, Consumer<Boolean> onChange) {
        return add(new Setting(id, category, Component.literal(label), Component.literal(description), defaultValue, onChange, null, null));
    }

    /** A toggle whose state lives elsewhere: read with {@code getter}, changed with {@code setter} (which saves). */
    public static Setting toggle(String id, String category, String label, String description, boolean defaultValue,
                                 java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        return toggle(id, category, label, description, defaultValue, null).bind(getter, setter);
    }

    /** A row with a button, e.g. "Edit HUD layout". */
    public static Setting action(String id, String category, String label, String description, String button, Runnable onClick) {
        return add(new Setting(id, category, Component.literal(label), Component.literal(description), false, null, onClick, Component.literal(button)));
    }

    /**
     * A remembered pick from a few {@code options} (shown as a segmented control, or a "‹ option ›" switcher when
     * they're too long). {@code defaultValue} must be one of them (else the first is used). {@code onChange}
     * (optional) runs with the new option whenever it changes. Read it with {@link Setting#choice()} or
     * {@link Setting#is(String)}, or {@link #choiceValue(String)} by id.
     */
    public static Setting choice(String id, String category, String label, String description, List<String> options,
                                 String defaultValue, Consumer<String> onChange) {
        return add(Setting.choice(id, category, Component.literal(label), Component.literal(description), options, defaultValue, onChange));
    }

    /**
     * A remembered number from {@code min} to {@code max}, in steps of {@code step} (0 for any value), shown as a
     * slider. {@code onChange} (optional) runs with each new value, live while the slider is dragged; the file is
     * saved when it's let go. Read it with {@link Setting#number()}, or {@link #sliderValue(String)} by id; choose
     * how the value is shown with {@link Setting#format}.
     */
    public static Setting slider(String id, String category, String label, String description, double min, double max,
                                 double step, double defaultValue, DoubleConsumer onChange) {
        return add(Setting.slider(id, category, Component.literal(label), Component.literal(description), min, max, step, defaultValue, onChange));
    }

    private static Setting add(Setting setting) {
        SETTINGS.put(setting.id(), setting);
        category(setting.category());
        return setting;
    }

    // ------------------------------------------------------------------ reading by id

    /** The setting with this id, or null if no mod registered it. */
    public static Setting find(String id) {
        return SETTINGS.get(id);
    }

    /** A choice's current option by id, or null if there's no such choice setting. */
    public static String choiceValue(String id) {
        Setting setting = SETTINGS.get(id);
        return setting != null && setting.isChoice() ? setting.choice() : null;
    }

    /** A slider's current value by id, or {@code fallback} if there's no such slider setting. */
    public static double sliderValue(String id, double fallback) {
        Setting setting = SETTINGS.get(id);
        return setting != null && setting.isSlider() ? setting.number() : fallback;
    }

    /** A slider's current value by id, or 0 if there's no such slider setting. */
    public static double sliderValue(String id) {
        return sliderValue(id, 0);
    }

    // ------------------------------------------------------------------ categories

    /**
     * The category called {@code name} (made if new), to give it an icon and a description. Categories show in the
     * Settings sidebar once they have a setting.
     */
    public static SettingCategory category(String name) {
        return CATEGORIES.computeIfAbsent(name, SettingCategory::new);
    }

    /** Shorthand for {@code category(name).icon(stack)}. */
    public static void categoryIcon(String category, ItemStack icon) {
        category(category).icon(icon);
    }

    /** Shorthand for {@code category(name).icon(texture)}: a 16x16 texture drawn whole. */
    public static void categoryIcon(String category, Identifier texture) {
        category(category).icon(texture);
    }

    /** Categories with at least one setting, in the order {@link #byCategory()} lists them. */
    public static List<SettingCategory> categories() {
        List<SettingCategory> list = new ArrayList<>();
        for (String name : byCategory().keySet()) {
            list.add(category(name));
        }
        return list;
    }

    /**
     * Settings grouped by category: Interface, HUD and Gameplay first, then other mods' categories in the
     * order they were first used (mods start in no fixed order).
     */
    public static Map<String, List<Setting>> byCategory() {
        Map<String, List<Setting>> grouped = new LinkedHashMap<>();
        for (String first : List.of(INTERFACE, HUD, GAMEPLAY)) {
            grouped.put(first, new ArrayList<>());
        }
        for (Setting setting : SETTINGS.values()) {
            grouped.computeIfAbsent(setting.category(), c -> new ArrayList<>()).add(setting);
        }
        grouped.values().removeIf(List::isEmpty);
        return grouped;
    }

    // ------------------------------------------------------------------ storage

    static JsonElement stored(Setting setting) {
        return ClientConfig.section("settings").get(setting.id());
    }

    static boolean value(Setting setting) {
        var stored = stored(setting);
        return stored != null && stored.isJsonPrimitive() ? stored.getAsBoolean() : setting.defaultValue();
    }

    static void set(Setting setting, boolean value) {
        ClientConfig.section("settings").addProperty(setting.id(), value);
        ClientConfig.save();
        setting.changed(value);
    }

    static void store(Setting setting, String value) {
        ClientConfig.section("settings").addProperty(setting.id(), value);
        ClientConfig.save();
    }

    /** Changes the value in memory only; the next {@link ClientConfig#save()} writes it. */
    static void storeQuietly(Setting setting, double value) {
        ClientConfig.section("settings").addProperty(setting.id(), value);
    }
}
