package com.skyrpg.items;

import com.skyrpg.items.bow.SkyBowItem;
import com.skyrpg.items.crafting.Bench;
import com.skyrpg.items.shield.Shields;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

/**
 * Every item SkyRPG adds, as real registered items with their own textures and worn models. What they
 * do (name, rarity, stats, ability, set) comes from data definitions keyed by these ids, see
 * {@link ItemDefinition}; this class only decides what kind of item each one is.
 */
public final class ModItems {
    /** What an item is, for its rarity line ("UNCOMMON HELMET") and slot rules. */
    public enum Kind {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, PICKAXE, AXE, HOE, WAND, AMULET, CLOAK, BELT, BRACERS, ROD, BACKPACK, SHIELD, BOW;

        public boolean isArmor() {
            return ordinal() <= BOOTS.ordinal();
        }
    }

    private static final Map<Item, Kind> KINDS = new LinkedHashMap<>();
    private static final Map<Identifier, Item> BY_ID = new LinkedHashMap<>();

    // Armour sets. Worn looks live in assets/skyrpg_items/equipment/<set>.json.
    public static final Item HARVESTER_HELMET = armor("harvester", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item HARVESTER_CHESTPLATE = armor("harvester", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item HARVESTER_LEGGINGS = armor("harvester", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item HARVESTER_BOOTS = armor("harvester", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item PROSPECTOR_HELMET = armor("prospector", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item PROSPECTOR_CHESTPLATE = armor("prospector", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item PROSPECTOR_LEGGINGS = armor("prospector", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item PROSPECTOR_BOOTS = armor("prospector", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item WOODSMAN_HELMET = armor("woodsman", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WOODSMAN_CHESTPLATE = armor("woodsman", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WOODSMAN_LEGGINGS = armor("woodsman", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WOODSMAN_BOOTS = armor("woodsman", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item VANGUARD_HELMET = armor("vanguard", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item VANGUARD_CHESTPLATE = armor("vanguard", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item VANGUARD_LEGGINGS = armor("vanguard", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item VANGUARD_BOOTS = armor("vanguard", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);

    // Tools: vanilla tool behaviour (tilling, stripping, mining tiers) from the property helpers.
    public static final Item HARVESTER_HOE = register("harvester_hoe", Kind.HOE, p -> p.hoe(ToolMaterial.IRON, -2.0f, -1.0f));
    public static final Item PROSPECTOR_PICKAXE = register("prospector_pickaxe", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.DIAMOND, 1.0f, -2.8f));
    public static final Item WOODSMAN_AXE = register("woodsman_axe", Kind.AXE, p -> p.axe(ToolMaterial.IRON, 6.0f, -3.1f));

    // Weapons. Their real damage comes from the "damage" stat in their definition.
    public static final Item VANGUARD_BLADE = register("vanguard_blade", Kind.SWORD, p -> p.sword(ToolMaterial.IRON, 3.0f, -2.4f));
    public static final Item RIFTBLADE = register("riftblade", Kind.SWORD, p -> p.sword(ToolMaterial.DIAMOND, 3.0f, -2.4f));
    public static final Item MENDING_WAND = register("mending_wand", Kind.WAND, p -> p.stacksTo(1));

    // Mining gear, sold and crafted at the Mining Master as Mining levels unlock it.
    public static final Item QUARRY_PICK = register("quarry_pick", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.IRON, 1.0f, -2.8f));
    public static final Item DELVER_PICKAXE = register("delver_pickaxe", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.IRON, 1.0f, -2.8f));
    public static final Item SKYIRON_PICKAXE = register("skyiron_pickaxe", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.DIAMOND, 1.0f, -2.8f));
    public static final Item DUSKBORE_PICK = register("duskbore_pick", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.DIAMOND, 1.0f, -2.8f));
    public static final Item EMBERHEART_PICKAXE = register("emberheart_pickaxe", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.DIAMOND, 1.0f, -2.8f));
    public static final Item LUMEN_BREAKER = register("lumen_breaker", Kind.PICKAXE, p -> p.pickaxe(ToolMaterial.NETHERITE, 1.0f, -2.8f));
    public static final Item DELVER_HELMET = armor("delver", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item DELVER_CHESTPLATE = armor("delver", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item DELVER_LEGGINGS = armor("delver", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item DELVER_BOOTS = armor("delver", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SKYFORGED_HELMET = armor("skyforged", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item SKYFORGED_CHESTPLATE = armor("skyforged", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item SKYFORGED_LEGGINGS = armor("skyforged", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item SKYFORGED_BOOTS = armor("skyforged", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item DEEPWARDEN_HELMET = armor("deepwarden", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item DEEPWARDEN_CHESTPLATE = armor("deepwarden", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item DEEPWARDEN_LEGGINGS = armor("deepwarden", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item DEEPWARDEN_BOOTS = armor("deepwarden", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERHEART_HELMET = armor("emberheart", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERHEART_CHESTPLATE = armor("emberheart", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERHEART_LEGGINGS = armor("emberheart", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERHEART_BOOTS = armor("emberheart", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item LUMENWARD_HELMET = armor("lumenward", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item LUMENWARD_CHESTPLATE = armor("lumenward", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item LUMENWARD_LEGGINGS = armor("lumenward", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item LUMENWARD_BOOTS = armor("lumenward", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_DIAMOND);

    // Farming gear, sold and crafted as Farming levels unlock it. What the tools and sets do is in their
    // definitions (perks, abilities, set bonuses); the farming mod makes the effects work.
    public static final Item THORNFIELD_SICKLE = register("thornfield_sickle", Kind.HOE, p -> p.hoe(ToolMaterial.IRON, -2.0f, -1.0f));
    public static final Item SUNREAPER_SCYTHE = register("sunreaper_scythe", Kind.HOE, p -> p.hoe(ToolMaterial.IRON, -2.0f, -1.0f));
    public static final Item FROSTBITE_SCYTHE = register("frostbite_scythe", Kind.HOE, p -> p.hoe(ToolMaterial.DIAMOND, -3.0f, 0.0f));
    public static final Item CINDER_SCYTHE = register("cinder_scythe", Kind.HOE, p -> p.hoe(ToolMaterial.DIAMOND, -3.0f, 0.0f));
    public static final Item MOONLIT_REAPER = register("moonlit_reaper", Kind.HOE, p -> p.hoe(ToolMaterial.NETHERITE, -4.0f, 0.0f));
    public static final Item FIELDHAND_HELMET = armor("fieldhand", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FIELDHAND_CHESTPLATE = armor("fieldhand", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FIELDHAND_LEGGINGS = armor("fieldhand", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FIELDHAND_BOOTS = armor("fieldhand", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SUNWARD_HELMET = armor("sunward", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SUNWARD_CHESTPLATE = armor("sunward", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SUNWARD_LEGGINGS = armor("sunward", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SUNWARD_BOOTS = armor("sunward", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FROSTGROVE_HELMET = armor("frostgrove", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FROSTGROVE_CHESTPLATE = armor("frostgrove", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FROSTGROVE_LEGGINGS = armor("frostgrove", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FROSTGROVE_BOOTS = armor("frostgrove", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item CINDERFIELD_HELMET = armor("cinderfield", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item CINDERFIELD_CHESTPLATE = armor("cinderfield", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item CINDERFIELD_LEGGINGS = armor("cinderfield", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item CINDERFIELD_BOOTS = armor("cinderfield", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item MOONHARVEST_HELMET = armor("moonharvest", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item MOONHARVEST_CHESTPLATE = armor("moonharvest", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item MOONHARVEST_LEGGINGS = armor("moonharvest", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item MOONHARVEST_BOOTS = armor("moonharvest", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_GOLD);

    // Foraging gear, sold and crafted as Foraging levels unlock it. Treefeller and the perks are the foraging mod's.
    public static final Item IRONBARK_HATCHET = register("ironbark_hatchet", Kind.AXE, p -> p.axe(ToolMaterial.IRON, 6.0f, -3.0f));
    public static final Item MISTPINE_SPLITTER = register("mistpine_splitter", Kind.AXE, p -> p.axe(ToolMaterial.DIAMOND, 6.0f, -3.0f));
    public static final Item WILLOWCLEAVER = register("willowcleaver", Kind.AXE, p -> p.axe(ToolMaterial.DIAMOND, 6.0f, -3.0f));
    public static final Item EMBERBRAND_AXE = register("emberbrand_axe", Kind.AXE, p -> p.axe(ToolMaterial.NETHERITE, 6.0f, -3.0f));
    public static final Item STARWOOD_GREATAXE = register("starwood_greataxe", Kind.AXE, p -> p.axe(ToolMaterial.NETHERITE, 6.0f, -3.0f));
    public static final Item BARKGUARD_HELMET = armor("barkguard", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item BARKGUARD_CHESTPLATE = armor("barkguard", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item BARKGUARD_LEGGINGS = armor("barkguard", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item BARKGUARD_BOOTS = armor("barkguard", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MISTWARDEN_HELMET = armor("mistwarden", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MISTWARDEN_CHESTPLATE = armor("mistwarden", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MISTWARDEN_LEGGINGS = armor("mistwarden", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MISTWARDEN_BOOTS = armor("mistwarden", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WILLOWSHADE_HELMET = armor("willowshade", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WILLOWSHADE_CHESTPLATE = armor("willowshade", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WILLOWSHADE_LEGGINGS = armor("willowshade", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item WILLOWSHADE_BOOTS = armor("willowshade", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item EMBERBARK_HELMET = armor("emberbark", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERBARK_CHESTPLATE = armor("emberbark", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERBARK_LEGGINGS = armor("emberbark", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item EMBERBARK_BOOTS = armor("emberbark", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item STARWOOD_HELMET = armor("starwood", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item STARWOOD_CHESTPLATE = armor("starwood", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item STARWOOD_LEGGINGS = armor("starwood", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item STARWOOD_BOOTS = armor("starwood", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_GOLD);

    // Combat gear: the sword line (each made from the one before) and its armour. Effects are the combat mod's.
    public static final Item FANGSTEEL_SABRE = register("fangsteel_sabre", Kind.SWORD, p -> p.sword(ToolMaterial.IRON, 3.0f, -2.4f));
    public static final Item STORMEDGE = register("stormedge", Kind.SWORD, p -> p.sword(ToolMaterial.DIAMOND, 3.0f, -2.4f));
    public static final Item CINDERFANG = register("cinderfang", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    // Slayer weapons (Artisan Bench, slayer level 6 and the boss's rare drop); the combat mod makes their effects work.
    public static final Item GRAVEBANE = register("gravebane", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item BROODFANG = register("broodfang", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item MARROWBLADE = register("marrowblade", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item VOIDRENDER = register("voidrender", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item STARFALL_BLADE = register("starfall_blade", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    // World dungeon gear (boss drops from the skyrpg_world dungeons).
    public static final Item CRYPTBREAKER = register("cryptbreaker", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item ARACHNE_FANG = register("arachne_fang", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item TIDECALLER_BLADE = register("tidecaller_blade", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item WARDENS_SHROUD_HELMET = armor("wardens_shroud", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item WARDENS_SHROUD_CHESTPLATE = armor("wardens_shroud", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item WARDENS_SHROUD_LEGGINGS = armor("wardens_shroud", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item WARDENS_SHROUD_BOOTS = armor("wardens_shroud", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item BLIGHTFORGED_HELMET = armor("blightforged", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item BLIGHTFORGED_CHESTPLATE = armor("blightforged", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item BLIGHTFORGED_LEGGINGS = armor("blightforged", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item BLIGHTFORGED_BOOTS = armor("blightforged", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item CINDERPLATE_HELMET = armor("cinderplate", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item CINDERPLATE_CHESTPLATE = armor("cinderplate", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item CINDERPLATE_LEGGINGS = armor("cinderplate", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item CINDERPLATE_BOOTS = armor("cinderplate", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RIMEGUARD_HELMET = armor("rimeguard", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RIMEGUARD_CHESTPLATE = armor("rimeguard", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RIMEGUARD_LEGGINGS = armor("rimeguard", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RIMEGUARD_BOOTS = armor("rimeguard", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item MIREWOVEN_HELMET = armor("mirewoven", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MIREWOVEN_CHESTPLATE = armor("mirewoven", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MIREWOVEN_LEGGINGS = armor("mirewoven", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MIREWOVEN_BOOTS = armor("mirewoven", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ARACHNE_CARAPACE_HELMET = armor("arachne_carapace", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ARACHNE_CARAPACE_CHESTPLATE = armor("arachne_carapace", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ARACHNE_CARAPACE_LEGGINGS = armor("arachne_carapace", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ARACHNE_CARAPACE_BOOTS = armor("arachne_carapace", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item TIDE_REGALIA_HELMET = armor("tide_regalia", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item TIDE_REGALIA_CHESTPLATE = armor("tide_regalia", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item TIDE_REGALIA_LEGGINGS = armor("tide_regalia", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item TIDE_REGALIA_BOOTS = armor("tide_regalia", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item RUNEFORGED_HELMET = armor("runeforged", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RUNEFORGED_CHESTPLATE = armor("runeforged", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RUNEFORGED_LEGGINGS = armor("runeforged", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RUNEFORGED_BOOTS = armor("runeforged", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    // The Nether: Hellforged Ingots and the Magma Tyrant's core. The full set makes fire and lava harmless.
    public static final Item HELLFORGED_HELMET = armor("hellforged", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_NETHERITE, Item.Properties::fireResistant);
    public static final Item HELLFORGED_CHESTPLATE = armor("hellforged", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_NETHERITE, Item.Properties::fireResistant);
    public static final Item HELLFORGED_LEGGINGS = armor("hellforged", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_NETHERITE, Item.Properties::fireResistant);
    public static final Item HELLFORGED_BOOTS = armor("hellforged", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_NETHERITE, Item.Properties::fireResistant);
    // Slayer armour: one 4-piece set per slayer line, crafted from its tokens (docs/design/slayers.md).
    public static final Item GRAVEWARDEN_HELMET = armor("gravewarden", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item GRAVEWARDEN_CHESTPLATE = armor("gravewarden", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item GRAVEWARDEN_LEGGINGS = armor("gravewarden", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item GRAVEWARDEN_BOOTS = armor("gravewarden", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item SILKSTALKER_HELMET = armor("silkstalker", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SILKSTALKER_CHESTPLATE = armor("silkstalker", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SILKSTALKER_LEGGINGS = armor("silkstalker", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item SILKSTALKER_BOOTS = armor("silkstalker", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item MARSHALS_BONEMAIL_HELMET = armor("marshals_bonemail", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item MARSHALS_BONEMAIL_CHESTPLATE = armor("marshals_bonemail", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item MARSHALS_BONEMAIL_LEGGINGS = armor("marshals_bonemail", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item MARSHALS_BONEMAIL_BOOTS = armor("marshals_bonemail", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item VOIDWALKER_HELMET = armor("voidwalker", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item VOIDWALKER_CHESTPLATE = armor("voidwalker", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item VOIDWALKER_LEGGINGS = armor("voidwalker", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item VOIDWALKER_BOOTS = armor("voidwalker", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FANGGUARD_HELMET = armor("fangguard", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FANGGUARD_CHESTPLATE = armor("fangguard", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FANGGUARD_LEGGINGS = armor("fangguard", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item FANGGUARD_BOOTS = armor("fangguard", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item STORMPLATE_HELMET = armor("stormplate", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item STORMPLATE_CHESTPLATE = armor("stormplate", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item STORMPLATE_LEGGINGS = armor("stormplate", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item STORMPLATE_BOOTS = armor("stormplate", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_IRON);
    public static final Item RIFTWEAVE_HELMET = armor("riftweave", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item RIFTWEAVE_CHESTPLATE = armor("riftweave", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item RIFTWEAVE_LEGGINGS = armor("riftweave", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item RIFTWEAVE_BOOTS = armor("riftweave", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item CINDERGUARD_HELMET = armor("cinderguard", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item CINDERGUARD_CHESTPLATE = armor("cinderguard", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item CINDERGUARD_LEGGINGS = armor("cinderguard", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item CINDERGUARD_BOOTS = armor("cinderguard", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Item STARFORGED_HELMET = armor("starforged", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item STARFORGED_CHESTPLATE = armor("starforged", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item STARFORGED_LEGGINGS = armor("starforged", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Item STARFORGED_BOOTS = armor("starforged", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_DIAMOND);

    // Fishing gear: rods (vanilla fishing rods underneath) and armour. What they do is the fishing mod's.
    public static final Item DRIFTWOOD_ROD = registerRod("driftwood_rod");
    public static final Item REEF_ROD = registerRod("reef_rod");
    public static final Item STORMCALLER_ROD = registerRod("stormcaller_rod");
    public static final Item ABYSSAL_ROD = registerRod("abyssal_rod");
    public static final Item LEVIATHAN_ROD = registerRod("leviathan_rod");
    // Tier V: also fishes in the Nether's lava (it's in the skyrpg_fishing:lava_rods tag; the fishing mod does the rest).
    public static final Item MAGMALINE_ROD = registerRod("magmaline_rod", Item.Properties::fireResistant);
    public static final Item ANGLER_HELMET = armor("angler", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ANGLER_CHESTPLATE = armor("angler", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ANGLER_LEGGINGS = armor("angler", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ANGLER_BOOTS = armor("angler", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item REEFWALKER_HELMET = armor("reefwalker", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item REEFWALKER_CHESTPLATE = armor("reefwalker", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item REEFWALKER_LEGGINGS = armor("reefwalker", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item REEFWALKER_BOOTS = armor("reefwalker", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Item ABYSSAL_HELMET = armor("abyssal", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ABYSSAL_CHESTPLATE = armor("abyssal", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ABYSSAL_LEGGINGS = armor("abyssal", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item ABYSSAL_BOOTS = armor("abyssal", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Item LEVIATHAN_HELMET = armor("leviathan", ArmorType.HELMET, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item LEVIATHAN_CHESTPLATE = armor("leviathan", ArmorType.CHESTPLATE, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item LEVIATHAN_LEGGINGS = armor("leviathan", ArmorType.LEGGINGS, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Item LEVIATHAN_BOOTS = armor("leviathan", ArmorType.BOOTS, SoundEvents.ARMOR_EQUIP_GOLD);

    // Backpacks, carried in their own inventory slots; how much each holds is in Backpacks.capacity.
    public static final Item SATCHEL = register("satchel", Kind.BACKPACK, p -> p);
    public static final Item TRAIL_PACK = register("trail_pack", Kind.BACKPACK, p -> p);
    public static final Item RANGER_PACK = register("ranger_pack", Kind.BACKPACK, p -> p);
    public static final Item VOYAGER_PACK = register("voyager_pack", Kind.BACKPACK, p -> p);
    public static final Item COLOSSAL_PACK = register("colossal_pack", Kind.BACKPACK, p -> p);

    // Accessories. Which slot each goes in comes from the skyrpg_items:accessory/<slot> item tags.
    public static final Item EMBERSTONE_PENDANT = register("emberstone_pendant", Kind.AMULET, p -> p);
    public static final Item STARGLASS_AMULET = register("starglass_amulet", Kind.AMULET, p -> p);
    public static final Item WAYFARER_MANTLE = register("wayfarer_mantle", Kind.CLOAK, p -> p);
    public static final Item DUSKWING_CLOAK = register("duskwing_cloak", Kind.CLOAK, p -> p);
    public static final Item SUNWOVEN_SASH = register("sunwoven_sash", Kind.BELT, p -> p);
    public static final Item TINKER_BELT = register("tinker_belt", Kind.BELT, p -> p);
    public static final Item IRONBARK_BRACERS = register("ironbark_bracers", Kind.BRACERS, p -> p);
    public static final Item DUELIST_VAMBRACES = register("duelist_vambraces", Kind.BRACERS, p -> p);
    // Slayer accessories (Artisan Bench, slayer level 3).
    public static final Item TYRANTS_AMULET = register("tyrants_amulet", Kind.AMULET, p -> p);
    public static final Item BROODWEAVE_CLOAK = register("broodweave_cloak", Kind.CLOAK, p -> p);
    public static final Item MARSHALS_BELT = register("marshals_belt", Kind.BELT, p -> p);
    public static final Item VOIDBOUND_BRACERS = register("voidbound_bracers", Kind.BRACERS, p -> p);
    // Dungeon accessories (world dungeon boss drops).
    public static final Item WIDOWSILK_CLOAK = register("widowsilk_cloak", Kind.CLOAK, p -> p);
    public static final Item TIDEMOTHER_PENDANT = register("tidemother_pendant", Kind.AMULET, p -> p);
    // Danger-region gear (crafted from region essences and dread crystals).
    public static final Item BLIGHTREAVER = register("blightreaver", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item CINDERBRAND = register("cinderbrand", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item RIMECLEAVER = register("rimecleaver", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    public static final Item MIREFANG = register("mirefang", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f));
    // The Deep: the Iron Thane's forge hammer, heavy and slow (the vanilla attack speed attribute is -3.2 here, a sword is -2.4).
    public static final Item THANES_HAMMER = register("thanes_hammer", Kind.SWORD, p -> p.sword(ToolMaterial.NETHERITE, 3.0f, -3.2f));
    public static final Item BLIGHTHEART_AMULET = register("blightheart_amulet", Kind.AMULET, p -> p);
    public static final Item ASHEN_BRACERS = register("ashen_bracers", Kind.BRACERS, p -> p);
    public static final Item FROSTWOVEN_CLOAK = register("frostwoven_cloak", Kind.CLOAK, p -> p);
    public static final Item BOG_BELT = register("bog_belt", Kind.BELT, p -> p);
    // The Wild: crafted from Blood Shards, stronger at night and under a Blood Moon (MoonbloodCharmSource).
    public static final Item MOONBLOOD_CHARM = register("moonblood_charm", Kind.AMULET, p -> p);

    // Shields: vanilla's blocking (the blocks_attacks component), one per stage, bigger and more ornate each time. Effects are
    // gear effects in their definitions, done by Shields. They sit in the off hand.
    public static final Item OAK_BUCKLER = shield("oak_buckler", 0.50f);
    public static final Item IRON_KITE_SHIELD = shield("iron_kite_shield", 0.65f);
    public static final Item SKYIRON_BULWARK = shield("skyiron_bulwark", 0.75f);
    public static final Item EMBERFORGED_AEGIS = shield("emberforged_aegis", 0.80f);
    public static final Item FROSTWARD_BARRIER = shield("frostward_barrier", 0.80f);
    public static final Item THUNDERGUARD = shield("thunderguard", 0.85f);
    public static final Item ASTRAL_AEGIS = shield("astral_aegis", 0.90f);

    // Bows: vanilla draw and arrows, but the arrows deal SkyRPG damage (the "damage" stat, Strength and crits). What each bow
    // does beyond that is the "bow" object of its definition, done by SkyBowItem and Bows.
    public static final Item HUNTERS_BOW = bow("hunters_bow");
    public static final Item RECURVE_BOW = bow("recurve_bow");
    public static final Item SKYIRON_LONGBOW = bow("skyiron_longbow");
    public static final Item EMBER_BOW = bow("ember_bow");
    public static final Item FROSTBITE_BOW = bow("frostbite_bow");
    public static final Item STORMCALLER_BOW = bow("stormcaller_bow");
    public static final Item STARFALL_BOW = bow("starfall_bow");

    private ModItems() {
    }

    /** Loads the class so everything above is registered during mod init, then adds the creative tab. */
    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgItems.id("items")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_items"))
                        .icon(() -> new ItemStack(RIFTBLADE))
                        .displayItems((parameters, output) -> {
                            BY_ID.values().forEach(item -> output.accept(SkyItems.create(item, 1)));
                            output.accept(new ItemStack(Bench.ITEM));
                        })
                        .build());
    }

    public static Kind kind(Item item) {
        return KINDS.get(item);
    }

    public static Item get(Identifier id) {
        return BY_ID.get(id);
    }

    public static Collection<Item> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    private static Item armor(String set, ArmorType type, Holder<SoundEvent> sound) {
        return armor(set, type, sound, p -> p);
    }

    /** As above, plus {@code extra} properties (fireResistant: a dropped piece doesn't burn in lava, like netherite). */
    private static Item armor(String set, ArmorType type, Holder<SoundEvent> sound, UnaryOperator<Item.Properties> extra) {
        Kind kind = switch (type) {
            case HELMET -> Kind.HELMET;
            case CHESTPLATE -> Kind.CHESTPLATE;
            case LEGGINGS -> Kind.LEGGINGS;
            default -> Kind.BOOTS;
        };
        // humanoidArmor adds (zero) armour attributes; SkyRPG armour has none at all.
        return register(set + "_" + type.getName(), kind,
                p -> extra.apply(p.humanoidArmor(material(set, sound), type).attributes(ItemAttributeModifiers.EMPTY)));
    }

    /**
     * No vanilla armour points or toughness: SkyRPG Defense does that job. Durability doesn't matter
     * because every SkyRPG item is unbreakable.
     */
    private static ArmorMaterial material(String set, Holder<SoundEvent> sound) {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        for (ArmorType type : ArmorType.values()) {
            defense.put(type, 0);
        }
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, SkyRpgItems.id(set));
        return new ArmorMaterial(1, defense, 15, sound, 0, 0, ItemTags.REPAIRS_IRON_ARMOR, asset);
    }

    /** A fishing rod: vanilla's rod item (casting, reeling), with SkyRPG's usual unbreakable, single-stack properties. */
    private static Item registerRod(String name) {
        return registerRod(name, p -> p);
    }

    private static Item registerRod(String name, UnaryOperator<Item.Properties> extra) {
        Identifier id = SkyRpgItems.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new FishingRodItem(extra.apply(new Item.Properties().setId(key).stacksTo(1)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE).component(DataComponents.TOOLTIP_DISPLAY, SkyItems.TOOLTIP).enchantable(1))));
        KINDS.put(item, Kind.ROD);
        BY_ID.put(id, item);
        return item;
    }

    private static Item shield(String name, float blocked) {
        return register(name, Kind.SHIELD, Item::new, p -> p.equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, provider -> Shields.blocksAttacks(provider, blocked)));
    }

    private static Item bow(String name) {
        return register(name, Kind.BOW, SkyBowItem::new, p -> p.enchantable(1));
    }

    private static Item register(String name, Kind kind, UnaryOperator<Item.Properties> properties) {
        return register(name, kind, Item::new, properties);
    }

    private static Item register(String name, Kind kind, Function<Item.Properties, Item> factory, UnaryOperator<Item.Properties> properties) {
        Identifier id = SkyRpgItems.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties base = new Item.Properties().setId(key).stacksTo(1)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.TOOLTIP_DISPLAY, SkyItems.TOOLTIP);
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(base)));
        KINDS.put(item, kind);
        BY_ID.put(id, item);
        return item;
    }
}
