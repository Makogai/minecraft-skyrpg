package com.skyrpg.enchants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Where tomes come from:
 * <ul>
 *   <li>Any hostile mob a player kills: a small chance (better for tougher mobs, and with Magic Find) of a tome of a
 *   combat enchant the table also offers, usually at a low level. Spawner mobs never drop them.</li>
 *   <li>Bosses, from {@code data/<namespace>/skyrpg/tome_drops/*.json}: by entity id or tag, optionally from a slayer
 *   tier up (per file or per drop, {@code "min_tier"}), each drop with its own chance. This is where the tome-only and
 *   ultimate enchants live.</li>
 *   <li>Earning skill XP (rules.json's {@code skill_finds}): now and then a tome for that skill's tools, and very rarely
 *   its ultimate.</li>
 * </ul>
 * Tomes go straight into the finder's bag; rare ones are announced to everyone.
 */
public final class TomeDrops {
    public static final String DIRECTORY = "skyrpg/tome_drops";
    private static final String TIER_TAG = "skyrpg_slayer_boss_tier_";
    /** Gear effect: tomes this much likelier (0.1 = +10%), from the Enchanting talent Tome Hunter. */
    public static final Identifier TOME_LUCK = SkyRpgEnchants.id("tome_luck");

    /** One boss's tomes. {@code entity} or {@code tag} picks the mob. */
    record BossDrops(Identifier entity, String tag, int minTier, List<Drop> drops) {
    }

    record Drop(Identifier enchant, int level, double chance, int minTier) {
    }

    private static List<BossDrops> bosses = List.of();

    private TomeDrops() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgEnchants.id("tome_drops"), DIRECTORY, TomeDrops::load);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                onKill(player, entity);
            }
        });
        SkillLevels.XP_EARNED.register(TomeDrops::onXp);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<BossDrops> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                List<Drop> drops = new ArrayList<>();
                json.getAsJsonArray("drops").forEach(e -> {
                    JsonObject drop = e.getAsJsonObject();
                    drops.add(new Drop(Identifier.parse(drop.get("enchant").getAsString()),
                            drop.has("level") ? drop.get("level").getAsInt() : 1, drop.get("chance").getAsDouble(),
                            drop.has("min_tier") ? drop.get("min_tier").getAsInt() : 0));
                });
                loaded.add(new BossDrops(json.has("entity") ? Identifier.parse(json.get("entity").getAsString()) : null,
                        json.has("tag") ? json.get("tag").getAsString() : null, json.has("min_tier") ? json.get("min_tier").getAsInt() : 0,
                        List.copyOf(drops)));
            } catch (RuntimeException e) {
                SkyRpgEnchants.LOGGER.error("Skipping invalid tome drops {}: {}", id, e.getMessage());
            }
        });
        bosses = List.copyOf(loaded);
    }

    /** Magic Find and the Tome Hunter talent make every tome likelier. */
    private static double luck(ServerPlayer player) {
        return (1 + Math.max(0, Stats.get(player, StatTypes.MAGIC_FIND)) / 100) * (1 + GearEffects.value(player, TOME_LUCK));
    }

    private static void onKill(ServerPlayer player, LivingEntity victim) {
        if (victim.entityTags().contains(MobTags.FARMED)) {
            return;
        }
        RandomSource random = player.getRandom();
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        for (BossDrops boss : bosses) {
            if ((boss.entity() != null && !boss.entity().equals(type)) || (boss.tag() != null && !victim.entityTags().contains(boss.tag()))
                    || tier(victim) < boss.minTier()) {
                continue;
            }
            for (Drop drop : boss.drops()) {
                Enchant def = Enchants.get(drop.enchant());
                if (def != null && tier(victim) >= drop.minTier() && random.nextDouble() < drop.chance() * luck(player)) {
                    give(player, def, Math.min(drop.level(), def.maxLevel()), "RARE DROP!");
                }
            }
        }
        if (!(victim instanceof Enemy)) {
            return;
        }
        EnchantRules.MobTomes rules = Enchants.rules().mobTomes();
        double chance = Math.min(rules.maxChance(), rules.chance() + rules.perHeart() * victim.getMaxHealth() / 2) * luck(player);
        if (random.nextDouble() < chance) {
            Enchant def = pick(random, rules.categories(), rules.rarityWeights());
            if (def != null) {
                give(player, def, level(random, rules.levelWeights(), def.tableMax()), "TOME DROP!");
            }
        }
    }

    /** The slayer tier from the boss's tags (0 if it isn't a slayer boss). */
    private static int tier(LivingEntity victim) {
        int tier = 0;
        for (String tag : victim.entityTags()) {
            if (tag.startsWith(TIER_TAG)) {
                try {
                    tier = Math.max(tier, Integer.parseInt(tag.substring(TIER_TAG.length())));
                } catch (NumberFormatException ignored) {
                    // not ours
                }
            }
        }
        return tier;
    }

    private static void onXp(ServerPlayer player, Identifier skill, double xp) {
        EnchantRules.SkillFind find = Enchants.rules().skillFinds().get(skill);
        if (find == null || xp <= 0) {
            return;
        }
        RandomSource random = player.getRandom();
        if (find.ultimate() != null && random.nextDouble() < find.ultimateChance() * luck(player)) {
            Enchant ultimate = Enchants.get(find.ultimate());
            if (ultimate != null) {
                give(player, ultimate, 1, "MYTHIC FIND!");
                return;
            }
        }
        if (random.nextDouble() < find.chance() * luck(player)) {
            Enchant def = pick(random, find.categories(), Enchants.rules().mobTomes().rarityWeights());
            if (def != null) {
                give(player, def, level(random, Enchants.rules().mobTomes().levelWeights(), def.tableMax()), "TOME FOUND!");
            }
        }
    }

    /** A random table enchant for these categories, its rarity drawn by weight. */
    private static Enchant pick(RandomSource random, List<Category> categories, Map<EnchantRarity, Integer> weights) {
        List<Enchant> pool = Enchants.all().stream()
                .filter(def -> def.tableable() && weights.getOrDefault(def.rarity(), 0) > 0)
                .filter(def -> categories.isEmpty() || def.appliesTo().stream().anyMatch(categories::contains))
                .toList();
        int total = pool.stream().mapToInt(def -> weights.get(def.rarity())).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Enchant def : pool) {
            roll -= weights.get(def.rarity());
            if (roll < 0) {
                return def;
            }
        }
        return null;
    }

    private static int level(RandomSource random, int[] weights, int max) {
        int total = 0;
        for (int i = 0; i < Math.min(weights.length, max); i++) {
            total += weights[i];
        }
        if (total <= 0) {
            return 1;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < Math.min(weights.length, max); i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i + 1;
            }
        }
        return 1;
    }

    /** Gives a tome and says so; epic and better finds are announced to everyone. */
    public static void give(ServerPlayer player, Enchant def, int level, String label) {
        Enchanting.give(player, EnchantContent.tome(def, level));
        MutableComponent tome = Component.literal("Tome of " + def.title(level))
                .withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(def.ultimate()));
        Notice.of(Notice.Kind.ENCHANT).icon(Notice.rarityGem(def.rarity().color())).tag(label.toUpperCase(java.util.Locale.ROOT))
                .send(player, tome.copy().append(Msg.text("  (" + def.rarity().label() + ")", ChatFormatting.DARK_GRAY)));
        Msg.playSound(player, def.rarity().ordinal() >= EnchantRarity.EPIC.ordinal() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                : SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        if (def.rarity().ordinal() >= EnchantRarity.EPIC.ordinal()) {
            Component announce = Notice.of(Notice.Kind.ENCHANT).icon(Notice.rarityGem(def.rarity().color())).tag("TOME FOUND").build(
                    Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE)
                    .append(Msg.text(" found a ", ChatFormatting.GRAY)).append(tome));
            player.level().getServer().getPlayerList().getPlayers().stream().filter(other -> other != player)
                    .forEach(other -> other.sendSystemMessage(announce));
        }
        Codex.discover(player, def.id());
    }
}
