package com.skyrpg.core.client.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * One row on the Settings page, made with {@link SkyRpgSettings}:
 * <ul>
 *   <li>a toggle ({@link SkyRpgSettings#toggle}): on/off, read with {@link #get()};</li>
 *   <li>an action ({@link SkyRpgSettings#action}): a button like "Edit HUD layout", no stored value;</li>
 *   <li>a choice ({@link SkyRpgSettings#choice}): one of a few options, read with {@link #choice()};</li>
 *   <li>a slider ({@link SkyRpgSettings#slider}): a number in a range, read with {@link #number()}.</li>
 * </ul>
 * Values are saved in the client config as soon as they change. {@link #section(String)} puts the row under a
 * small heading inside its category, and {@link #keywords(String...)} adds words the Settings search finds it by.
 */
public final class Setting {
    public enum Kind { TOGGLE, ACTION, CHOICE, SLIDER }

    private final String id;
    private final String category;
    private final Component label;
    private final Component description;
    private final Kind kind;
    // Toggle
    private final boolean defaultValue;
    private final Consumer<Boolean> onChange;
    // Action
    private final Runnable onClick;
    private final Component buttonLabel;
    // Choice
    private final List<String> options;
    private final String defaultChoice;
    private final Consumer<String> onChoice;
    // Slider
    private final double min;
    private final double max;
    private final double step;
    private final double defaultNumber;
    private final DoubleConsumer onNumber;
    private DoubleFunction<String> format;

    private java.util.function.BooleanSupplier boundGetter;
    private Consumer<Boolean> boundSetter;
    private String section;
    private List<String> keywords = List.of();

    Setting(String id, String category, Component label, Component description, boolean defaultValue,
            Consumer<Boolean> onChange, Runnable onClick, Component buttonLabel) {
        this(id, category, label, description, onClick != null ? Kind.ACTION : Kind.TOGGLE, defaultValue, onChange, onClick, buttonLabel,
                List.of(), null, null, 0, 1, 0, 0, null);
    }

    private Setting(String id, String category, Component label, Component description, Kind kind,
                    boolean defaultValue, Consumer<Boolean> onChange, Runnable onClick, Component buttonLabel,
                    List<String> options, String defaultChoice, Consumer<String> onChoice,
                    double min, double max, double step, double defaultNumber, DoubleConsumer onNumber) {
        this.id = id;
        this.category = category;
        this.label = label;
        this.description = description;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.onChange = onChange;
        this.onClick = onClick;
        this.buttonLabel = buttonLabel;
        this.options = options;
        this.defaultChoice = defaultChoice;
        this.onChoice = onChoice;
        this.min = min;
        this.max = max;
        this.step = step;
        this.defaultNumber = defaultNumber;
        this.onNumber = onNumber;
    }

    static Setting choice(String id, String category, Component label, Component description, List<String> options,
                          String defaultValue, Consumer<String> onChange) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Choice setting " + id + " needs at least one option");
        }
        List<String> copy = List.copyOf(options);
        String fallback = copy.contains(defaultValue) ? defaultValue : copy.getFirst();
        return new Setting(id, category, label, description, Kind.CHOICE, false, null, null, null, copy, fallback, onChange,
                0, 1, 0, 0, null);
    }

    static Setting slider(String id, String category, Component label, Component description, double min, double max,
                          double step, double defaultValue, DoubleConsumer onChange) {
        if (!(max > min)) {
            throw new IllegalArgumentException("Slider setting " + id + " needs max > min");
        }
        double cleanStep = Math.max(0, step);
        return new Setting(id, category, label, description, Kind.SLIDER, false, null, null, null, List.of(), null, null,
                min, max, cleanStep, snap(defaultValue, min, max, cleanStep), onChange);
    }

    // ------------------------------------------------------------------ what it is

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public Component label() {
        return label;
    }

    public Component description() {
        return description;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isAction() {
        return kind == Kind.ACTION;
    }

    public boolean isToggle() {
        return kind == Kind.TOGGLE;
    }

    public boolean isChoice() {
        return kind == Kind.CHOICE;
    }

    public boolean isSlider() {
        return kind == Kind.SLIDER;
    }

    /** An action's button text; null for other kinds. */
    public Component buttonLabel() {
        return buttonLabel;
    }

    /** The heading this row sits under inside its category, or null for none (rows without one come first). */
    public String section() {
        return section;
    }

    /** Puts this row under a small heading inside its category, e.g. "Minimap". Returns this, for chaining. */
    public Setting section(String name) {
        this.section = name == null || name.isBlank() ? null : name;
        return this;
    }

    /** Extra words the Settings search finds this row by (its label, description and options always count). */
    public Setting keywords(String... words) {
        this.keywords = List.of(words);
        return this;
    }

    public List<String> keywords() {
        return keywords;
    }

    // ------------------------------------------------------------------ toggles

    /**
     * A toggle: whether it's on. Choices are "on" when they're not on their first option, sliders when above
     * their minimum; actions are never on.
     */
    public boolean get() {
        return switch (kind) {
            case TOGGLE -> boundGetter != null ? boundGetter.getAsBoolean() : SkyRpgSettings.value(this);
            case ACTION -> false;
            case CHOICE -> choiceIndex() != 0;
            case SLIDER -> number() > min;
        };
    }

    /** Switches a toggle on or off (saved, and its change hook runs if the value changed). */
    public void set(boolean value) {
        if (kind == Kind.TOGGLE && get() != value) {
            write(value);
        }
    }

    /**
     * Makes this toggle read and write state kept elsewhere (e.g. a HUD widget's shown flag) instead of its own
     * saved value; the setter is responsible for saving. Returns this, for chaining.
     */
    public Setting bind(java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        this.boundGetter = getter;
        this.boundSetter = setter;
        return this;
    }

    private void write(boolean value) {
        if (boundSetter != null) {
            boundSetter.accept(value);
            changed(value);
        } else {
            SkyRpgSettings.set(this, value);
        }
    }

    /** Flips a toggle, runs an action, picks a choice's next option or nudges a slider one step up. */
    public void activate() {
        switch (kind) {
            case ACTION -> onClick.run();
            case TOGGLE -> write(!get());
            case CHOICE -> cycle(1);
            case SLIDER -> setNumber(number() + (step > 0 ? step : (max - min) / 20));
        }
    }

    boolean defaultValue() {
        return defaultValue;
    }

    void changed(boolean value) {
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    // ------------------------------------------------------------------ choices

    /** A choice's options, in order (empty for other kinds). */
    public List<String> options() {
        return options;
    }

    /** A choice's current option (its default if the saved one is no longer an option); null for other kinds. */
    public String choice() {
        if (kind != Kind.CHOICE) {
            return null;
        }
        var stored = SkyRpgSettings.stored(this);
        if (stored != null && stored.isJsonPrimitive()) {
            String value = stored.getAsString();
            if (options.contains(value)) {
                return value;
            }
        }
        return defaultChoice;
    }

    /** The current option's position in {@link #options()} (0 for other kinds). */
    public int choiceIndex() {
        return kind == Kind.CHOICE ? Math.max(0, options.indexOf(choice())) : 0;
    }

    /** True if this choice is set to {@code option}. */
    public boolean is(String option) {
        return kind == Kind.CHOICE && choice().equals(option);
    }

    /** Picks an option (saved, and the change hook runs if it changed). Unknown options are ignored. */
    public void setChoice(String option) {
        if (kind != Kind.CHOICE || !options.contains(option) || option.equals(choice())) {
            return;
        }
        SkyRpgSettings.store(this, option);
        if (onChoice != null) {
            onChoice.accept(option);
        }
    }

    /** The next ({@code direction} 1) or previous (-1) option, wrapping around. */
    public void cycle(int direction) {
        if (kind == Kind.CHOICE) {
            setChoice(options.get(Math.floorMod(choiceIndex() + direction, options.size())));
        }
    }

    public String defaultChoice() {
        return defaultChoice;
    }

    // ------------------------------------------------------------------ sliders

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** The slider's step; 0 means any value. */
    public double step() {
        return step;
    }

    public double defaultNumber() {
        return defaultNumber;
    }

    /** A slider's value, always within [min, max] and on a step (0 for other kinds). */
    public double number() {
        if (kind != Kind.SLIDER) {
            return 0;
        }
        var stored = SkyRpgSettings.stored(this);
        if (stored != null && stored.isJsonPrimitive() && stored.getAsJsonPrimitive().isNumber()) {
            return snap(stored.getAsDouble());
        }
        return defaultNumber;
    }

    /** {@link #number()} rounded to a whole number. */
    public int intValue() {
        return (int) Math.round(number());
    }

    /** Where the value sits between min (0) and max (1), for drawing the slider. */
    public double progress() {
        return kind == Kind.SLIDER ? (number() - min) / (max - min) : 0;
    }

    /** Sets a slider (clamped and snapped to its step), saves it, and runs the change hook if it changed. */
    public void setNumber(double value) {
        previewNumber(value);
        ClientConfig.save();
    }

    /**
     * Like {@link #setNumber} but without writing the config file, for live feedback while a slider is dragged;
     * {@link #setNumber} (or any other save) writes it later.
     */
    public void previewNumber(double value) {
        if (kind != Kind.SLIDER) {
            return;
        }
        double snapped = snap(value);
        if (snapped == number()) {
            return;
        }
        SkyRpgSettings.storeQuietly(this, snapped);
        if (onNumber != null) {
            onNumber.accept(snapped);
        }
    }

    /** Clamps to [min, max] and rounds to the nearest step (and away from floating point noise like 0.30000000000000004). */
    public double snap(double value) {
        return snap(value, min, max, step);
    }

    private static double snap(double value, double min, double max, double step) {
        double clamped = Mth.clamp(value, min, max);
        if (step <= 0) {
            return clamped;
        }
        double snapped = Math.min(max, min + Math.round((clamped - min) / step) * step);
        return BigDecimal.valueOf(snapped).setScale(Math.max(decimals(step, min, max) + 2, 6), RoundingMode.HALF_UP).doubleValue();
    }

    /** How the slider's value is shown, e.g. {@code v -> Math.round(v * 100) + "%"}. Returns this, for chaining. */
    public Setting format(DoubleFunction<String> format) {
        this.format = format;
        return this;
    }

    /** The value as shown next to the slider. */
    public String formatted() {
        return formatted(number());
    }

    public String formatted(double value) {
        if (format != null) {
            return format.apply(value);
        }
        int decimals = decimals();
        return decimals == 0 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Decimal places worth showing for this slider's step (0 for whole steps, at most 3). */
    private int decimals() {
        return decimals(step, min, max);
    }

    private static int decimals(double step, double min, double max) {
        if (step <= 0) {
            return max - min > 20 ? 0 : 2;
        }
        int scale = Math.max(0, new BigDecimal(Double.toString(step)).stripTrailingZeros().scale());
        return Math.min(3, scale);
    }

    // ------------------------------------------------------------------ any kind

    /** True if the setting is on its default value (always true for actions). */
    public boolean isDefault() {
        return switch (kind) {
            case TOGGLE -> get() == defaultValue;
            case ACTION -> true;
            case CHOICE -> choice().equals(defaultChoice);
            case SLIDER -> number() == defaultNumber;
        };
    }

    /** Puts the setting back to its default (running its change hook if that changes it). */
    public void reset() {
        switch (kind) {
            case TOGGLE -> set(defaultValue);
            case CHOICE -> setChoice(defaultChoice);
            case SLIDER -> setNumber(defaultNumber);
            case ACTION -> { }
        }
    }

    /**
     * Sets the value from text, for commands and dev tools: "true"/"false"/"on"/"off"/"toggle" for a toggle, an
     * option (any case) or its number for a choice, a number for a slider; "run" for an action. Returns false if
     * the text didn't make sense for this setting.
     */
    public boolean setFromString(String text) {
        String value = text.trim();
        switch (kind) {
            case TOGGLE -> {
                switch (value.toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "1" -> set(true);
                    case "false", "off", "no", "0" -> set(false);
                    case "toggle", "flip" -> activate();
                    default -> {
                        return false;
                    }
                }
                return true;
            }
            case CHOICE -> {
                for (String option : options) {
                    if (option.equalsIgnoreCase(value) || option.replace(' ', '_').equalsIgnoreCase(value)) {
                        setChoice(option);
                        return true;
                    }
                }
                try {
                    int index = Integer.parseInt(value);
                    if (index >= 0 && index < options.size()) {
                        setChoice(options.get(index));
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Not an index either.
                }
                return false;
            }
            case SLIDER -> {
                try {
                    setNumber(Double.parseDouble(value));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case ACTION -> {
                if (value.equalsIgnoreCase("run") || value.equalsIgnoreCase("true")) {
                    activate();
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
