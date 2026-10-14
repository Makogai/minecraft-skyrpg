package com.skyrpg.core.client.config;

import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How a Settings category looks in the Settings page's sidebar: its icon (an item, or a 16x16 texture) and a
 * line of description under its title. Get one with {@link SkyRpgSettings#category(String)}; every setter
 * returns this, so they chain:
 * <pre>
 * SkyRpgSettings.category("Map").icon(new ItemStack(Items.FILLED_MAP)).description("Minimap and world map");
 * </pre>
 * Categories nobody configures get an icon picked from their name, or a lettered badge.
 */
public final class SettingCategory {
    /** Icons for categories named like these (lower case), so most get a fitting one without asking. */
    private static final Map<String, Item> KNOWN = Map.ofEntries(
            Map.entry("interface", Items.PAINTING),
            Map.entry("hud", Items.COMPASS),
            Map.entry("gameplay", Items.DIAMOND_SWORD),
            Map.entry("map", Items.FILLED_MAP),
            Map.entry("maps", Items.FILLED_MAP),
            Map.entry("info", Items.SPYGLASS),
            Map.entry("chat", Items.WRITABLE_BOOK),
            Map.entry("social", Items.WRITABLE_BOOK),
            Map.entry("combat", Items.IRON_SWORD),
            Map.entry("effects", Items.BLAZE_POWDER),
            Map.entry("visuals", Items.ENDER_EYE),
            Map.entry("vfx", Items.BLAZE_POWDER),
            Map.entry("particles", Items.BLAZE_POWDER),
            Map.entry("graphics", Items.ENDER_EYE),
            Map.entry("performance", Items.REDSTONE),
            Map.entry("sound", Items.NOTE_BLOCK),
            Map.entry("sounds", Items.NOTE_BLOCK),
            Map.entry("audio", Items.NOTE_BLOCK),
            Map.entry("pets", Items.BONE),
            Map.entry("mining", Items.IRON_PICKAXE),
            Map.entry("farming", Items.WHEAT),
            Map.entry("foraging", Items.OAK_SAPLING),
            Map.entry("fishing", Items.FISHING_ROD),
            Map.entry("items", Items.CHEST),
            Map.entry("inventory", Items.CHEST),
            Map.entry("economy", Items.GOLD_INGOT),
            Map.entry("events", Items.FIREWORK_ROCKET),
            Map.entry("world", Items.GRASS_BLOCK),
            Map.entry("skills", Items.EXPERIENCE_BOTTLE),
            Map.entry("notifications", Items.BELL),
            Map.entry("controls", Items.LEVER),
            Map.entry("dev", Items.COMMAND_BLOCK),
            Map.entry("debug", Items.COMMAND_BLOCK));

    private final String name;
    private ItemStack icon;
    private Identifier texture;
    /** The icon picked from the name, made on first use. */
    private ItemStack known;
    private Component description = Component.empty();

    SettingCategory(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** An item drawn as the category's icon. Returns this, for chaining. */
    public SettingCategory icon(ItemStack stack) {
        this.icon = stack;
        this.texture = null;
        return this;
    }

    /** A 16x16 texture (the whole file, e.g. {@code skyrpg_vfx:textures/gui/vfx.png}) drawn as the icon. */
    public SettingCategory icon(Identifier texture) {
        this.texture = texture;
        this.icon = null;
        return this;
    }

    /** One line shown under the category's title on the Settings page. Returns this, for chaining. */
    public SettingCategory description(String text) {
        this.description = text == null ? Component.empty() : Component.literal(text);
        return this;
    }

    public Component description() {
        return description;
    }

    /** The texture icon, or null if the category uses an item. */
    public Identifier texture() {
        return texture;
    }

    /** The item icon: the one set, else one known for the category's name, else null (draw a lettered badge). */
    public ItemStack icon() {
        if (icon != null || texture != null) {
            return icon;
        }
        if (known == null) {
            Item item = KNOWN.get(name.toLowerCase(Locale.ROOT).trim());
            known = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        return known.isEmpty() ? null : known;
    }

    /** A short lower-case name for commands and dev tools: "Weapon FX" -> "weapon_fx". */
    public String slug() {
        return slug(name);
    }

    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
