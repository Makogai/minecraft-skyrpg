package com.skyrpg.combat.slayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The data behind the K menu's Slayers page. What a slayer level unlocks is read from the bench recipes
 * ({@code data/<ns>/skyrpg/recipes}) that require {@code skyrpg_combat:slayer/<line>}, so new slayer gear shows up
 * on the page without touching it.
 */
final class SlayerPage {
    private record UnlockEntry(int level, Item item) {
    }

    private static Map<String, List<UnlockEntry>> unlocks = Map.of();

    private SlayerPage() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgCombat.id("slayer_unlocks"), "skyrpg/recipes", SlayerPage::load);
        ServerPlayNetworking.registerGlobalReceiver(SlayerPayloads.RequestPage.TYPE, (payload, context) -> send(context.player()));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, List<UnlockEntry>> found = new HashMap<>();
        String prefix = SkyRpgCombat.MOD_ID + ":slayer/";
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                if (!json.has("requires")) {
                    return;
                }
                JsonObject requires = json.getAsJsonObject("requires");
                String skill = requires.get("skill").getAsString();
                if (!skill.startsWith(prefix)) {
                    return;
                }
                Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(json.get("result").getAsString())).orElse(Items.AIR);
                if (item != Items.AIR) {
                    found.computeIfAbsent(skill.substring(prefix.length()), k -> new ArrayList<>())
                            .add(new UnlockEntry(requires.get("level").getAsInt(), item));
                }
            } catch (RuntimeException e) {
                SkyRpgCombat.LOGGER.warn("Skipping recipe {} for the slayer page: {}", id, e.getMessage());
            }
        });
        found.values().forEach(list -> list.sort(Comparator.comparingInt(UnlockEntry::level)
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.item()).getPath())));
        unlocks = found;
    }

    static void send(ServerPlayer player) {
        List<SlayerPayloads.PageLine> lines = new ArrayList<>();
        for (SlayerLine line : Slayers.lines().values()) {
            List<SlayerPayloads.StatPer> perLevel = new ArrayList<>();
            line.levelStat().forEach((stat, per) -> {
                StatType type = StatTypes.get(stat);
                if (type != null) {
                    perLevel.add(new SlayerPayloads.StatPer(type.name(), per, type.percent()));
                }
            });
            List<SlayerPayloads.Unlock> unlockViews = new ArrayList<>();
            for (UnlockEntry entry : unlocks.getOrDefault(line.id(), List.of())) {
                unlockViews.add(new SlayerPayloads.Unlock(entry.level(), new ItemStack(entry.item())));
            }
            List<SlayerPayloads.PageTier> tiers = new ArrayList<>();
            for (int i = 0; i < line.tiers().size(); i++) {
                SlayerLine.Tier tier = line.tiers().get(i);
                SlayerAbilities.TierLore lore = SlayerAbilities.lore(line.kind(), i + 1);
                List<String> abilities = new ArrayList<>();
                if (lore != null) {
                    lore.adds().forEach(a -> abilities.add((a.attack() ? "A|" : "D|") + a.name() + "|" + a.description()));
                }
                tiers.add(new SlayerPayloads.PageTier(new SlayerPayloads.TierView(tier.fee(), tier.xp(), (int) Math.round(tier.health()), tier.level(),
                        tier.minutes(), tier.slayerXp(), tier.requires(), Slayers.level(player, line.id()) >= tier.requires(),
                        lore == null ? "" : lore.title(), abilities), tier.tokensMin(), tier.tokensMax(), tier.rareChance(), tier.eggChance()));
            }
            lines.add(new SlayerPayloads.PageLine(line.id(), line.name(), line.color(), new ItemStack(line.icon()), line.countsText(), line.levels(),
                    perLevel, unlockViews, line.token() == Items.AIR ? ItemStack.EMPTY : new ItemStack(line.token()),
                    line.rare() == Items.AIR ? ItemStack.EMPTY : new ItemStack(line.rare()), tiers));
        }
        ServerPlayNetworking.send(player, new SlayerPayloads.PageData(lines));
    }
}
