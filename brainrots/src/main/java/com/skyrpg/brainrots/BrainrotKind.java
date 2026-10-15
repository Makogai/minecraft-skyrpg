package com.skyrpg.brainrots;

import java.util.Locale;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;

/**
 * The fifteen brainrots, in album order: their names, how rare they are, how they get around, where they like to be,
 * their size, and a line for the album. Each has its voice line ({@link #voice}, the narrator saying its name).
 */
public enum BrainrotKind {
    TUNG_TUNG_SAHUR("tung_tung_sahur", "Tung Tung Tung Sahur", Rarity.COMMON, Move.HOP, Habitat.ANYWHERE, 0.6f, 1.9f,
            "Knocks three times, then nine, then never stops. Nobody knows why it carries the bat."),
    TRALALERO_TRALALA("tralalero_tralala", "Tralalero Tralala", Rarity.UNCOMMON, Move.WALK, Habitat.BEACH, 0.9f, 1.2f,
            "A shark that took up running. Three legs, three sneakers, no regrets."),
    BOMBARDIRO_CROCODILO("bombardiro_crocodilo", "Bombardiro Crocodilo", Rarity.EPIC, Move.FLY, Habitat.ANYWHERE, 1.8f, 0.8f,
            "Half crocodile, half bomber, all drama. Circles high overhead, then swoops low to show off."),
    BALLERINA_CAPPUCCINA("ballerina_cappuccina", "Ballerina Cappuccina", Rarity.UNCOMMON, Move.SPIN, Habitat.ANYWHERE, 0.6f, 1.6f,
            "Twirls on her toes all day and never spills a drop of her cappuccino head."),
    BRR_BRR_PATAPIM("brr_brr_patapim", "Brr Brr Patapim", Rarity.COMMON, Move.WALK, Habitat.FOREST, 0.8f, 1.6f,
            "A forest monkey that slowly turned into a tree. Its feet are bigger than its patience."),
    LIRILI_LARILA("lirili_larila", "Lirilì Larilà", Rarity.UNCOMMON, Move.WALK, Habitat.DESERT, 0.9f, 1.4f,
            "An elephant cactus in sandals, wandering the dunes. Do not hug."),
    CHIMPANZINI_BANANINI("chimpanzini_bananini", "Chimpanzini Bananini", Rarity.COMMON, Move.HOP, Habitat.JUNGLE, 0.9f, 1.3f,
            "A chimp who lives in a banana. Peel with care."),
    BOMBOMBINI_GUSINI("bombombini_gusini", "Bombombini Gusini", Rarity.RARE, Move.FLY, Habitat.ANYWHERE, 1.4f, 0.9f,
            "A goose that became a fighter jet. Honks at the speed of sound."),
    TRIPPI_TROPPI("trippi_troppi", "Trippi Troppi", Rarity.RARE, Move.WALK, Habitat.BEACH, 0.8f, 1.0f,
            "Shrimp body, cat brain. Purrs and clicks at the same time."),
    CAPPUCCINO_ASSASSINO("cappuccino_assassino", "Cappuccino Assassino", Rarity.RARE, Move.WALK, Habitat.NIGHT, 0.8f, 1.2f,
            "Silent, deadly, extra foam. Mostly seen after dark."),
    FRIGO_CAMELO("frigo_camelo", "Frigo Camelo", Rarity.UNCOMMON, Move.WALK, Habitat.HOT_OR_COLD, 0.8f, 2.0f,
            "Keeps drinks cold across the hottest deserts and the coldest snow. The hump holds snacks."),
    LA_VACA_SATURNO("la_vaca_saturno", "La Vaca Saturno Saturnita", Rarity.LEGENDARY, Move.FLOAT, Habitat.NIGHT_ONLY, 1.2f, 1.3f,
            "A cow with its own planetary ring, grazing the stars on clear nights."),
    BONECA_AMBALABU("boneca_ambalabu", "Boneca Ambalabu", Rarity.UNCOMMON, Move.HOP, Habitat.SWAMP, 0.7f, 1.5f,
            "A frog on a tyre on legs. Rolls when it's late, which is always."),
    GLORBO_FRUTTODRILLO("glorbo_fruttodrillo", "Glorbo Fruttodrillo", Rarity.UNCOMMON, Move.WALK, Habitat.SWAMP, 0.9f, 0.7f,
            "A crocodile that is mostly watermelon. Snaps first, drips after."),
    BURBALONI_LULILOLI("burbaloni_luliloli", "Burbaloni Luliloli", Rarity.COMMON, Move.HOP, Habitat.BEACH, 0.8f, 1.2f,
            "A capybara napping in a coconut. Far too relaxed to run away.");

    /** How rare: spawn weight, colour, and coins for the first (and later) catch. */
    public enum Rarity {
        COMMON("Common", 10, 0xFFD5DAE3, 25),
        UNCOMMON("Uncommon", 6, 0xFF55DD77, 75),
        RARE("Rare", 3, 0xFF5CA8FF, 200),
        EPIC("Epic", 1.5, 0xFFB98CFF, 500),
        LEGENDARY("Legendary", 0.7, 0xFFFFAA00, 1500);

        public final String label;
        public final double weight;
        public final int color;
        public final int firstCoins;

        Rarity(String label, double weight, int color, int firstCoins) {
            this.label = label;
            this.weight = weight;
            this.color = color;
            this.firstCoins = firstCoins;
        }
    }

    /** How it gets around: walks, hops, spins as it walks, flies circles high up (swooping low now and then), or floats just off the ground. */
    public enum Move { WALK, HOP, SPIN, FLY, FLOAT }

    /** Where it's most often found (it can still turn up anywhere, except the night-only one in daylight). */
    public enum Habitat {
        ANYWHERE("Anywhere"),
        BEACH("Beaches and coasts"),
        FOREST("Forests"),
        DESERT("Deserts and badlands"),
        JUNGLE("Jungles"),
        SWAMP("Swamps and rivers"),
        HOT_OR_COLD("Deserts and snowy places"),
        NIGHT("Anywhere, mostly at night"),
        NIGHT_ONLY("Anywhere, only at night");

        public final String label;

        Habitat(String label) {
            this.label = label;
        }

        /** Spawn weight multiplier for this biome and time. */
        double weight(Holder<Biome> biome, boolean night) {
            return switch (this) {
                case ANYWHERE -> 1;
                case BEACH -> biome.is(ConventionalBiomeTags.IS_BEACH) || biome.is(ConventionalBiomeTags.IS_OCEAN) ? 5 : 0.6;
                case FOREST -> biome.is(ConventionalBiomeTags.IS_FOREST) ? 5 : 0.6;
                case DESERT -> biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(ConventionalBiomeTags.IS_BADLANDS) ? 6 : 0.4;
                case JUNGLE -> biome.is(ConventionalBiomeTags.IS_JUNGLE) ? 6 : 0.5;
                case SWAMP -> biome.is(ConventionalBiomeTags.IS_SWAMP) || biome.is(ConventionalBiomeTags.IS_RIVER) ? 5 : 0.5;
                case HOT_OR_COLD -> biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(ConventionalBiomeTags.IS_SNOWY)
                        || biome.is(ConventionalBiomeTags.IS_ICY) ? 5 : 0.5;
                case NIGHT -> night ? 4 : 0.4;
                // The one legendary: only at night, but not so rare you'd never meet it (about 1 in 40 night spawns).
                case NIGHT_ONLY -> night ? 2.5 : 0;
            };
        }
    }

    public final String id;
    public final String displayName;
    public final Rarity rarity;
    public final Move move;
    public final Habitat habitat;
    public final float width;
    public final float height;
    public final String lore;
    /** The narrator saying its name. Set when the mod registers its sounds. */
    public SoundEvent voice;

    BrainrotKind(String id, String displayName, Rarity rarity, Move move, Habitat habitat, float width, float height, String lore) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.move = move;
        this.habitat = habitat;
        this.width = width;
        this.height = height;
        this.lore = lore;
    }

    public static BrainrotKind byId(String id) {
        for (BrainrotKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    public static BrainrotKind byOrdinal(int ordinal) {
        BrainrotKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TUNG_TUNG_SAHUR;
    }

    public boolean flies() {
        return move == Move.FLY;
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}
