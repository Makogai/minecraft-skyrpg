package com.skyrpg.progress.quests;

import com.google.gson.JsonElement;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.progress.quests.QuestData.Objective;
import com.skyrpg.world.DangerRegions;
import com.skyrpg.world.Explorer;
import com.skyrpg.world.RegionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** The checkers: how far along a player is on one objective. Event-driven ones read the counters in {@link QuestState}. */
final class Objectives {
    private static final Identifier ENCHANTS = Identifier.fromNamespaceAndPath("skyrpg_enchants", "enchants");
    private static final Identifier TOME = Identifier.fromNamespaceAndPath("skyrpg_enchants", "arcane_tome");

    private Objectives() {
    }

    /** The counter key for kills on this objective. */
    static String killKey(QuestData.Quest quest, int index) {
        return "k:" + quest.id() + "#" + index;
    }

    /** Set once the player has finished the talk objective's dialogue with its NPC. */
    static String talkKey(QuestData.Quest quest, int index) {
        return "t:" + quest.id() + "#" + index;
    }

    static String visitKey(QuestData.Quest quest, int index) {
        return "v:" + quest.id() + "#" + index;
    }

    /** Current value of the objective (not capped at the target). */
    static int value(ServerPlayer player, QuestState state, QuestData.Quest quest, int index) {
        Objective o = quest.objectives().get(index);
        if (o.flag("sticky") && state.counter(visitKey(quest, index)) > 0) {
            return o.target();
        }
        try {
            return switch (o.type()) {
                case "skill_level" -> SkillLevels.level(player, skillId(o.str("skill")));
                case "collection" -> o.json().has("tier") ? Adapters.collectionTier(player, Identifier.parse(o.str("id")))
                        : (int) Math.min(Integer.MAX_VALUE, Adapters.collectionCount(player, Identifier.parse(o.str("id"))));
                case "kill" -> state.counter(killKey(quest, index));
                case "dungeon_clear" -> cleared(player, o) ? 1 : 0;
                case "slayer_tier" -> state.counter((o.flag("started") ? "p:" : "s:") + (o.str("line").isEmpty() ? "any" : o.str("line")));
                case "craft" -> craft(player, state, o);
                case "obtain" -> obtain(player, o);
                case "visit" -> state.counter(visitKey(quest, index)) > 0 || visited(player, o) ? 1 : 0;
                case "talk" -> state.counter(talkKey(quest, index)) > 0 ? 1 : 0;
                case "reach_level" -> Adapters.level(player);
                case "journal" -> state.counter("journal") > 0 ? 1 : 0;
                default -> 0;
            };
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Identifier skillId(String skill) {
        return Identifier.parse(skill.contains(":") ? skill : "skyrpg_skills:" + skill);
    }

    private static boolean cleared(ServerPlayer player, Objective o) {
        var cleared = Explorer.state(player).cleared();
        if (o.json().has("bosses")) {
            for (JsonElement element : o.json().getAsJsonArray("bosses")) {
                if (cleared.contains(element.getAsString())) {
                    return true;
                }
            }
            return false;
        }
        return cleared.contains(o.str("boss"));
    }

    private static int craft(ServerPlayer player, QuestState state, Objective o) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (o.json().has("items")) {
            o.json().getAsJsonArray("items").forEach(element -> ids.add(element.getAsString()));
        } else {
            ids.add(o.str("item"));
        }
        int total = 0;
        for (String raw : ids) {
            Identifier id = Identifier.parse(raw);
            total += state.counter("c:" + id);
            if (!o.flag("bench")) {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                total += item == null ? 0 : player.getStats().getValue(Stats.ITEM_CRAFTED, item);
            }
        }
        return total;
    }

    private static int obtain(ServerPlayer player, Objective o) {
        boolean enchanted = o.flag("enchanted");
        Item single = o.json().has("item") ? BuiltInRegistries.ITEM.getOptional(Identifier.parse(o.str("item"))).orElse(null) : null;
        java.util.Set<Item> any = new java.util.HashSet<>();
        if (o.json().has("items")) {
            for (JsonElement element : o.json().getAsJsonArray("items")) {
                BuiltInRegistries.ITEM.getOptional(Identifier.parse(element.getAsString())).ifPresent(any::add);
            }
        }
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            boolean match = enchanted ? isEnchanted(stack) : stack.getItem() == single || any.contains(stack.getItem());
            if (match) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean isEnchanted(ItemStack stack) {
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TOME)) {
            return false;
        }
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ENCHANTS).orElse(null);
        return type != null && stack.get(type) != null;
    }

    /** Whether the player is somewhere that counts for a {@code visit} objective right now. */
    static boolean visited(ServerPlayer player, Objective o) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        String id = o.str("id");
        return switch (o.str("kind")) {
            case "biome" -> id.startsWith("#")
                    ? level.getBiome(pos).is(TagKey.<Biome>create(Registries.BIOME, Identifier.parse(id.substring(1))))
                    : level.getBiome(pos).is(ResourceKey.<Biome>create(Registries.BIOME, Identifier.parse(id)));
            case "structure" -> inStructure(level, pos, id);
            case "region" -> {
                RegionShape region = level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? DangerRegions.at(level, pos.getX(), pos.getZ()) : null;
                yield region != null && (id.equals("any") || region.type().equals(id));
            }
            case "layer" -> level.dimension() == net.minecraft.world.level.Level.OVERWORLD && pos.getY() >= o.num("min_y", -64) && pos.getY() <= o.num("max_y", 0)
                    && !level.canSeeSky(pos);
            case "dimension" -> level.dimension().identifier().toString().equals(id);
            case "waystone" -> Explorer.state(player).waystones().size() >= o.target();
            case "dungeon_found" -> Explorer.state(player).dungeons().stream().anyMatch(found -> found.dungeon().equals(id));
            default -> false;
        };
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, String id) {
        Holder<Structure> holder = structure(level, id);
        return holder != null && level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(holder)).isValid();
    }

    static Holder<Structure> structure(ServerLevel level, String id) {
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(id))).orElse(null);
    }

    /** Whether a killed entity counts for a {@code kill} objective: its entity id, or a {@code #tag}. */
    static boolean killMatches(LivingEntity entity, Objective o) {
        String want = o.str("entity");
        if (want.isEmpty() || want.equals("any")) {
            return true;
        }
        if (want.startsWith("#")) {
            return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(want.substring(1))));
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(want);
    }
}
