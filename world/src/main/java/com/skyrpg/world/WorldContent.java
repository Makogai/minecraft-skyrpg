package com.skyrpg.world;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world mod's blocks and items: the waystone, dungeon vault keys and boss trophies, the danger regions' ground and
 * essences, the deep layers' cave blocks and drops, and the Nether biomes' ground.
 */
public final class WorldContent {
    public static final Block WAYSTONE = block("waystone", p -> new WaystoneBlock(p.mapColor(MapColor.STONE).sound(SoundType.DEEPSLATE_BRICKS)
            .strength(8.0f, 3_600_000.0f).requiresCorrectToolForDrops().noLootTable().pushReaction(PushReaction.IMMOVEABLE).noOcclusion()
            .lightLevel(state -> state.getValue(WaystoneBlock.LIT) ? 9 : 3)));
    public static final Item WAYSTONE_ITEM = item("waystone", p -> new BlockItem(WAYSTONE, p.rarity(Rarity.RARE).fireResistant().useBlockDescriptionPrefix()));

    // Vault keys: from the dungeon's Elites and its boss; one opens that dungeon's vault.
    public static final Item CRYPT_KEY = item("crypt_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item HOLLOW_KEY = item("hollow_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item TIDE_KEY = item("tide_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    // Boss trophies, for crafting dungeon gear later.
    public static final Item WARDEN_SIGIL = item("warden_sigil", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item ARACHNE_EYE = item("arachne_eye", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item TIDE_PEARL = item("tide_pearl", p -> new Item(p.rarity(Rarity.EPIC)));

    // Danger regions: the corrupted ground (placed by world generation) and what their monsters drop.
    public static final Block BLIGHTGRASS = block("blightgrass", p -> new Block(p.mapColor(MapColor.COLOR_PURPLE).strength(0.6f).sound(SoundType.GRASS)));
    public static final Block BLIGHT_CRYSTAL = block("blight_crystal", p -> new ClusterBlock(p.mapColor(MapColor.COLOR_RED).strength(1.5f)
            .sound(SoundType.AMETHYST_CLUSTER).noOcclusion().lightLevel(state -> 6)));
    public static final Block ASHEN_SOIL = block("ashen_soil", p -> new Block(p.mapColor(MapColor.COLOR_GRAY).strength(0.5f).sound(SoundType.SAND)));
    public static final Block CURSED_ICE = block("cursed_ice", p -> new Block(p.mapColor(MapColor.ICE).strength(0.8f).friction(0.98f)
            .sound(SoundType.GLASS)));
    public static final Block MIRE_MUD = block("mire_mud", p -> new Block(p.mapColor(MapColor.TERRACOTTA_GREEN).strength(0.5f).sound(SoundType.MUD)
            .speedFactor(0.6f)));
    public static final Item BLIGHTGRASS_ITEM = blockItem("blightgrass", BLIGHTGRASS);
    public static final Item BLIGHT_CRYSTAL_ITEM = blockItem("blight_crystal", BLIGHT_CRYSTAL);
    public static final Item ASHEN_SOIL_ITEM = blockItem("ashen_soil", ASHEN_SOIL);
    public static final Item CURSED_ICE_ITEM = blockItem("cursed_ice", CURSED_ICE);
    public static final Item MIRE_MUD_ITEM = blockItem("mire_mud", MIRE_MUD);
    public static final Item BLIGHT_ESSENCE = item("blight_essence", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item CINDER_ESSENCE = item("cinder_essence", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item FROST_ESSENCE = item("frost_essence", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item MIRE_ESSENCE = item("mire_essence", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item DREAD_CRYSTAL = item("dread_crystal", p -> new Item(p.rarity(Rarity.EPIC)));

    // The Deep: cave blocks placed by the underground layers (DeepLayerFeature), their monsters' drops, and the Dwarven
    // Halls' key and trophy.
    public static final Block GLIMMERSTONE = block("glimmerstone", p -> new Block(p.mapColor(MapColor.STONE).strength(1.5f, 6.0f)
            .requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(state -> 5)));
    public static final Block GLIMMER_CLUSTER = block("glimmer_cluster", p -> new AmethystClusterBlock(7.0f, 3.0f, p.mapColor(MapColor.COLOR_PURPLE)
            .forceSolidOn().noOcclusion().strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST_CLUSTER).lightLevel(state -> 10).pushReaction(PushReaction.POPPED)));
    public static final Block GLOWCAP = block("glowcap", p -> new CavePlantBlock(Block.box(5, 0, 5, 11, 7, 11), p.mapColor(MapColor.COLOR_CYAN)
            .noCollision().instabreak().sound(SoundType.FUNGUS).lightLevel(state -> 9).pushReaction(PushReaction.POPPED)));
    public static final Block GLOWCAP_BLOCK = block("glowcap_block", p -> new Block(p.mapColor(MapColor.COLOR_CYAN).strength(0.2f)
            .sound(SoundType.SHROOMLIGHT).lightLevel(state -> 13)));
    public static final Block SPORE_PUFFBALL = block("spore_puffball", p -> new SporePuffballBlock(p.mapColor(MapColor.TERRACOTTA_WHITE)
            .noCollision().instabreak().sound(SoundType.FUNGUS).pushReaction(PushReaction.POPPED)));
    public static final Block DWARVEN_RUNESTONE = block("dwarven_runestone", p -> new Block(p.mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(state -> 7)));
    public static final Item GLIMMERSTONE_ITEM = blockItem("glimmerstone", GLIMMERSTONE);
    public static final Item GLIMMER_CLUSTER_ITEM = blockItem("glimmer_cluster", GLIMMER_CLUSTER);
    public static final Item GLOWCAP_ITEM = blockItem("glowcap", GLOWCAP);
    public static final Item GLOWCAP_BLOCK_ITEM = blockItem("glowcap_block", GLOWCAP_BLOCK);
    public static final Item SPORE_PUFFBALL_ITEM = item("spore_puffball", p -> new BlockItem(SPORE_PUFFBALL, p.rarity(Rarity.UNCOMMON)
            .useBlockDescriptionPrefix()));
    public static final Item DWARVEN_RUNESTONE_ITEM = item("dwarven_runestone", p -> new BlockItem(DWARVEN_RUNESTONE, p.rarity(Rarity.UNCOMMON)
            .useBlockDescriptionPrefix()));
    public static final Item GLIMMER_GEODE = item("glimmer_geode", p -> new GlimmerGeodeItem(p.rarity(Rarity.RARE)));
    public static final Item GLOWCAP_SPORES = item("glowcap_spores", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item DWARVEN_COG = item("dwarven_cog", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item RIFT_EMBER = item("rift_ember", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item THANE_KEY = item("thane_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item THANES_RUNE = item("thanes_rune", p -> new Item(p.rarity(Rarity.EPIC)));

    // The Nether (docs/design/nether.md): the Infernal Citadel's key and the Magma Tyrant's trophy, and the ground the
    // new biomes are painted with (NetherSurfaceFeature): the Ember Forest's ash and the Obsidian Spires' glowing cracks.
    public static final Item CITADEL_KEY = item("citadel_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16).fireResistant()));
    public static final Item TYRANTS_CORE = item("tyrants_core", p -> new Item(p.rarity(Rarity.EPIC).fireResistant()));
    public static final Block EMBER_ASH = block("ember_ash", p -> new EmberAshBlock(p.mapColor(MapColor.TERRACOTTA_BLACK).strength(0.5f)
            .sound(SoundType.SOUL_SOIL).lightLevel(state -> 3)));
    public static final Block VOID_CRACK = block("void_crack", p -> new VoidCrackBlock(p.mapColor(MapColor.COLOR_PURPLE).strength(1.5f, 6.0f)
            .requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(state -> 8)));
    public static final Item EMBER_ASH_ITEM = blockItem("ember_ash", EMBER_ASH);
    public static final Item VOID_CRACK_ITEM = item("void_crack", p -> new BlockItem(VOID_CRACK, p.rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));

    // The End (docs/design/end_game.md): the three new biomes' blocks (EndRealm), the Astral Shard the clusters drop, and the Astral
    // Observatory's key and trophy.
    public static final Block STARSTONE = block("starstone", p -> new Block(p.mapColor(MapColor.SAND).strength(3.0f, 9.0f)
            .requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(state -> 6)));
    public static final Block ASTRAL_CRYSTAL = block("astral_crystal", p -> new AmethystClusterBlock(7.0f, 3.0f, p.mapColor(MapColor.COLOR_LIGHT_BLUE)
            .forceSolidOn().noOcclusion().strength(1.5f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST_CLUSTER).lightLevel(state -> 11).pushReaction(PushReaction.POPPED)));
    public static final Block VOID_BLOOM = block("void_bloom", p -> new CavePlantBlock(Block.box(4, 0, 4, 12, 12, 12), p.mapColor(MapColor.COLOR_PURPLE)
            .noCollision().instabreak().sound(SoundType.GRASS).lightLevel(state -> 8).pushReaction(PushReaction.POPPED)));
    public static final Block VOID_CAP = block("void_cap", p -> new Block(p.mapColor(MapColor.COLOR_PURPLE).strength(0.4f)
            .sound(SoundType.SHROOMLIGHT).lightLevel(state -> 7)));
    public static final Item STARSTONE_ITEM = item("starstone", p -> new BlockItem(STARSTONE, p.rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));
    public static final Item ASTRAL_CRYSTAL_ITEM = item("astral_crystal", p -> new BlockItem(ASTRAL_CRYSTAL, p.rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));
    public static final Item VOID_BLOOM_ITEM = item("void_bloom", p -> new BlockItem(VOID_BLOOM, p.rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));
    public static final Item VOID_CAP_ITEM = blockItem("void_cap", VOID_CAP);
    public static final Item ASTRAL_SHARD = item("astral_shard", p -> new Item(p.rarity(Rarity.RARE)));
    public static final Item OBSERVATORY_KEY = item("observatory_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item STAR_WARDEN_CORE = item("star_warden_core", p -> new Item(p.rarity(Rarity.EPIC)));
    // The Ruined Citadel's and the Catacombs' keys and boss trophies.
    public static final Item CITADEL_CREST_KEY = item("citadel_crest_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item FALLEN_CREST = item("fallen_crest", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item OSSUARY_KEY = item("ossuary_key", p -> new Item(p.rarity(Rarity.RARE).stacksTo(16)));
    public static final Item BONELORD_PHYLACTERY = item("bonelord_phylactery", p -> new Item(p.rarity(Rarity.EPIC)));

    // The Wild (docs/design/the_wild.md): Blood Moon shards, the two new regions' essences, the six region bosses' trophies and
    // the Heart Sigils that wake them.
    public static final Item BLOOD_SHARD = item("blood_shard", p -> new Item(p.rarity(Rarity.RARE)));
    public static final Item GALE_ESSENCE = item("gale_essence", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item ECTOPLASM = item("ectoplasm", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final Item BLIGHTED_HEART = item("blighted_heart", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item CINDER_CORE = item("cinder_core", p -> new Item(p.rarity(Rarity.EPIC).fireResistant()));
    public static final Item FROZEN_CROWN = item("frozen_crown", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item BOG_MOTHERS_EYE = item("bog_mothers_eye", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item WRAITH_LANTERN = item("wraith_lantern", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item STORMFEATHER = item("stormfeather", p -> new Item(p.rarity(Rarity.EPIC)));
    public static final Item BLIGHTLANDS_SIGIL = sigil("blightlands");
    public static final Item ASHEN_WASTES_SIGIL = sigil("ashen_wastes");
    public static final Item FROSTGRAVE_SIGIL = sigil("frostgrave");
    public static final Item MIRE_SIGIL = sigil("mire");
    public static final Item HAUNTED_MOORS_SIGIL = sigil("haunted_moors");
    public static final Item SHATTERED_HIGHLANDS_SIGIL = sigil("shattered_highlands");

    private static final List<Item> TAB = List.of(WAYSTONE_ITEM, CRYPT_KEY, HOLLOW_KEY, TIDE_KEY, THANE_KEY, CITADEL_KEY, WARDEN_SIGIL, ARACHNE_EYE,
            TIDE_PEARL, THANES_RUNE, TYRANTS_CORE, BLIGHT_ESSENCE, CINDER_ESSENCE, FROST_ESSENCE, MIRE_ESSENCE, DREAD_CRYSTAL, BLIGHTGRASS_ITEM,
            BLIGHT_CRYSTAL_ITEM, ASHEN_SOIL_ITEM, CURSED_ICE_ITEM, MIRE_MUD_ITEM, GLIMMER_GEODE, GLOWCAP_SPORES, DWARVEN_COG, RIFT_EMBER,
            GLIMMERSTONE_ITEM, GLIMMER_CLUSTER_ITEM, GLOWCAP_ITEM, GLOWCAP_BLOCK_ITEM, SPORE_PUFFBALL_ITEM, DWARVEN_RUNESTONE_ITEM, EMBER_ASH_ITEM,
            VOID_CRACK_ITEM, OBSERVATORY_KEY, STAR_WARDEN_CORE, CITADEL_CREST_KEY, FALLEN_CREST, OSSUARY_KEY, BONELORD_PHYLACTERY,
            ASTRAL_SHARD, STARSTONE_ITEM, ASTRAL_CRYSTAL_ITEM, VOID_BLOOM_ITEM, VOID_CAP_ITEM, BLOOD_SHARD, GALE_ESSENCE, ECTOPLASM,
            BLIGHTED_HEART, CINDER_CORE, FROZEN_CROWN, BOG_MOTHERS_EYE, WRAITH_LANTERN, STORMFEATHER, BLIGHTLANDS_SIGIL, ASHEN_WASTES_SIGIL,
            FROSTGRAVE_SIGIL, MIRE_SIGIL, HAUNTED_MOORS_SIGIL, SHATTERED_HIGHLANDS_SIGIL);

    private WorldContent() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgWorld.id("world")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_world"))
                        .icon(() -> new ItemStack(WAYSTONE_ITEM))
                        .displayItems((parameters, output) -> TAB.forEach(item -> output.accept(new ItemStack(item))))
                        .build());
    }

    private static Item sigil(String type) {
        return item(type + "_sigil", p -> new RegionBosses.SigilItem(type, p.rarity(Rarity.EPIC).stacksTo(16)));
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgWorld.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
    }

    private static Item blockItem(String name, Block block) {
        return item(name, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
    }

    /** A crystal cluster sitting on the ground (no facing: it only grows upward). */
    static final class ClusterBlock extends Block {
        private static final VoxelShape SHAPE = box(3, 0, 3, 13, 9, 13);

        ClusterBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
                                      net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return SHAPE;
        }
    }

    /** The Ember Forest's ground: ash that still smoulders, sparks drifting up from it now and then. */
    static final class EmberAshBlock extends Block {
        EmberAshBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
            if (random.nextInt(5) != 0 || !level.getBlockState(pos.above()).isAir()) {
                return;
            }
            double x = pos.getX() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            if (random.nextInt(8) == 0) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x, pos.getY() + 1.0, z, 0, 0, 0);
            } else {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.05, z,
                        (random.nextDouble() - 0.5) * 0.02, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    /** A seam of the void through blackstone (the Obsidian Spires' floors): it glows, and motes of it drift up. */
    static final class VoidCrackBlock extends Block {
        VoidCrackBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
            if (random.nextInt(3) != 0 || !level.getBlockState(pos.above()).isAir()) {
                return;
            }
            level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.02,
                    pos.getZ() + random.nextDouble(), 0, 0.02 + random.nextDouble() * 0.04, 0);
        }
    }

    /** A small plant (a glowcap) that grows on any solid floor, cave stone included. */
    static final class CavePlantBlock extends VegetationBlock {
        private final VoxelShape shape;

        CavePlantBlock(VoxelShape shape, Properties properties) {
            super(properties);
            this.shape = shape;
        }

        @Override
        protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
                                      net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return shape;
        }

        @Override
        protected boolean mayPlaceOn(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
                                     net.minecraft.core.BlockPos pos) {
            return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
        }
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgWorld.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }
}
