package com.skyrpg.vfx.client;

import com.skyrpg.vfx.SkyRpgVfx;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/**
 * An item's rarity as a lower-case name, for held-item bindings with {@code "rarity"}. In order:
 * <ol>
 *   <li>the SkyRPG item definition's rarity (looked up in skyrpg_items when it's installed, without depending on it);</li>
 *   <li>the colour of the item's name (SkyRPG names are coloured by rarity: legendary is gold, and so on);</li>
 *   <li>vanilla's rarity component (common, uncommon, rare, epic).</li>
 * </ol>
 */
final class Rarities {
    private static MethodHandle definition;
    private static MethodHandle rarity;
    private static boolean looked;

    private Rarities() {
    }

    static String of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (!looked) {
            lookUp();
        }
        if (definition != null) {
            try {
                Object def = definition.invoke(stack);
                if (def != null && rarity.invoke(def) instanceof Enum<?> value) {
                    return value.name().toLowerCase(Locale.ROOT);
                }
            } catch (Throwable e) {
                SkyRpgVfx.LOGGER.warn("Couldn't read a SkyRPG item rarity; using name colours from now on", e);
                definition = null;
            }
        }
        Component name = stack.get(DataComponents.ITEM_NAME);
        TextColor color = name == null ? null : name.getStyle().getColor();
        if (color != null) {
            String byColor = switch (color.getValue() & 0xFFFFFF) {
                case 0x55FF55 -> "uncommon";
                case 0x5555FF -> "rare";
                case 0xAA00AA -> "epic";
                case 0xFFAA00 -> "legendary";
                case 0xFF55FF -> "mythic";
                case 0xFF5555 -> "special";
                default -> null;
            };
            if (byColor != null) {
                return byColor;
            }
        }
        return stack.getRarity().name().toLowerCase(Locale.ROOT);
    }

    private static void lookUp() {
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded("skyrpg_items")) {
            return;
        }
        try {
            Class<?> items = Class.forName("com.skyrpg.items.SkyItems");
            Class<?> def = Class.forName("com.skyrpg.items.ItemDefinition");
            Class<?> rarityType = Class.forName("com.skyrpg.items.Rarity");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            definition = lookup.findStatic(items, "definition", MethodType.methodType(def, ItemStack.class));
            rarity = lookup.findVirtual(def, "rarity", MethodType.methodType(rarityType));
        } catch (ReflectiveOperationException | LinkageError e) {
            SkyRpgVfx.LOGGER.info("SkyRPG item rarities not readable ({}); using name colours", e.toString());
            definition = null;
        }
    }
}
