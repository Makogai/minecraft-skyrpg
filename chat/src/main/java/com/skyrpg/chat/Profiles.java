package com.skyrpg.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What chat knows about a player: their SkyRPG Level, their name as chat shows it, and the profile card that pops up
 * when you hover over it.
 *
 * <p><b>SkyRPG Level</b> = one per skill level (every loaded skill) + four per slayer level. With seven skills at 50
 * and four slayers at 9 that tops out at 494, so the colour steps (every 40, like Hypixel SkyBlock's) reach dark red
 * only at the very end.
 *
 * <p>Skill and slayer names come from the same data files the skills and combat mods load, and levels from core's
 * {@link SkillLevels}, so chat doesn't depend on either mod.
 */
public final class Profiles {
    public static final int SLAYER_WEIGHT = 4;
    private static final int TOP_SKILLS = 3;
    private static final Identifier ACTIVE_PET = Identifier.fromNamespaceAndPath("skyrpg_pets", "active");

    private static final Identifier PROGRESS_LEVEL = Identifier.fromNamespaceAndPath("skyrpg_progress", "level");

    record SkillInfo(Identifier id, String name, TextColor color, int order) {
    }

    private static List<SkillInfo> skills = List.of();
    private static List<SkillInfo> slayers = List.of();
    private static AttachmentType<?> petAttachment;
    private static AttachmentType<?> levelAttachment;

    private Profiles() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgChat.id("skill_names"), "skyrpg/skills", files -> skills = parse(files, false));
        JsonDataLoader.register(SkyRpgChat.id("slayer_names"), "skyrpg/slayers", files -> slayers = parse(files, true));
    }

    private static List<SkillInfo> parse(Map<Identifier, JsonElement> files, boolean slayer) {
        List<SkillInfo> list = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                String name = json.has("name") ? json.get("name").getAsString() : SkillLevels.displayName(id);
                TextColor color = json.has("color")
                        ? TextColor.parseColor(json.get("color").getAsString()).result().orElse(ChatColors.rgb(ChatColors.TEXT))
                        : ChatColors.rgb(ChatColors.TEXT);
                int order = json.has("order") ? json.get("order").getAsInt() : 100;
                // Slayer levels are answered by the combat mod as skyrpg_combat:slayer/<line>, whatever namespace the file is in.
                Identifier levelId = slayer ? Identifier.fromNamespaceAndPath("skyrpg_combat", "slayer/" + id.getPath()) : id;
                list.add(new SkillInfo(levelId, name, color, order));
            } catch (RuntimeException e) {
                SkyRpgChat.LOGGER.warn("Chat skips {} {}: {}", slayer ? "slayer" : "skill", id, e.getMessage());
            }
        });
        list.sort(Comparator.comparingInt(SkillInfo::order).thenComparing(info -> info.id().toString()));
        return List.copyOf(list);
    }

    /** Skills to count: the loaded ones, or core's well-known ids before data has loaded. */
    private static List<SkillInfo> skillList() {
        if (!skills.isEmpty()) {
            return skills;
        }
        List<SkillInfo> fallback = new ArrayList<>();
        int order = 0;
        for (Identifier id : List.of(SkillLevels.FARMING, SkillLevels.MINING, SkillLevels.FORAGING, SkillLevels.COMBAT, SkillLevels.FISHING)) {
            fallback.add(new SkillInfo(id, SkillLevels.displayName(id), ChatColors.rgb(ChatColors.TEXT), order++));
        }
        return fallback;
    }

    // ------------------------------------------------------------------ numbers

    public static int skillTotal(ServerPlayer player) {
        int total = 0;
        for (SkillInfo skill : skillList()) {
            total += SkillLevels.level(player, skill.id());
        }
        return total;
    }

    public static int slayerTotal(ServerPlayer player) {
        int total = 0;
        for (SkillInfo slayer : slayers) {
            total += SkillLevels.level(player, slayer.id());
        }
        return total;
    }

    /** The progress mod's SkyRPG Level (its synced XP attachment, by id), or -1 if that mod isn't there. */
    private static int progressLevel(ServerPlayer player) {
        try {
            if (levelAttachment == null) {
                levelAttachment = AttachmentRegistryImpl.get(PROGRESS_LEVEL);
                if (levelAttachment == null) {
                    return -1;
                }
            }
            Object xp = player.getAttached(levelAttachment);
            return xp instanceof Integer value ? value / 100 : 0;
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }

    public static int level(ServerPlayer player) {
        int progress = progressLevel(player);
        if (progress >= 0) {
            return progress;
        }
        return skillTotal(player) + SLAYER_WEIGHT * slayerTotal(player);
    }

    public static double skillAverage(ServerPlayer player) {
        List<SkillInfo> list = skillList();
        return list.isEmpty() ? 0 : skillTotal(player) / (double) list.size();
    }

    /** The active pet (a pet item), or empty. Read from the pets mod's synced attachment by id, so chat doesn't need that mod. */
    public static ItemStack activePet(ServerPlayer player) {
        try {
            if (petAttachment == null) {
                petAttachment = AttachmentRegistryImpl.get(ACTIVE_PET);
                if (petAttachment == null) {
                    return ItemStack.EMPTY;
                }
            }
            Object pet = player.getAttached(petAttachment);
            return pet instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (RuntimeException | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ components

    /** "(* 42)": dim brackets, a star and the level, both in the tier colour, with a hover that explains the level. */
    public static MutableComponent badge(int level) {
        ChatFormatting tier = ChatColors.tier(level);
        int tierRgb = TextColor.fromLegacyFormat(tier).getValue();
        return Component.empty().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(levelHover(level))))
                .append(ChatIcons.tinted(ChatIcons.BADGE_LEFT, ChatColors.DIM))
                .append(ChatIcons.tinted(ChatIcons.LEVEL, tierRgb))
                .append(Component.literal(String.valueOf(level)).withStyle(tier))
                .append(ChatIcons.tinted(ChatIcons.BADGE_RIGHT, ChatColors.DIM));
    }

    private static Component levelHover(int level) {
        int index = ChatColors.tierIndex(level);
        MutableComponent hover = Component.literal("SkyRPG Level " + level).withStyle(ChatColors.tier(level), ChatFormatting.BOLD)
                .append(Component.literal(levelAttachment != null ? "\nEarned from quests, skills, collections and bosses."
                        : "\nOne per skill level, " + SLAYER_WEIGHT + " per slayer level.").withStyle(style -> style
                        .withBold(false).withColor(ChatColors.MUTED)));
        if (index < ChatColors.tierCount() - 1) {
            int next = (index + 1) * ChatColors.LEVELS_PER_TIER;
            hover.append(Component.literal("\nNext colour at " + next + ": ").withStyle(style -> style.withBold(false).withColor(ChatColors.DIM)))
                    .append(Component.literal(ChatColors.tierName(index + 1)).withStyle(style -> style.withBold(false)
                            .withColor(ChatColors.tier(next))));
        }
        return hover;
    }

    /** The colour a player's name takes: their title's, else their level tier's. */
    public static TextColor nameColor(ServerPlayer player, int level) {
        Titles.Title title = Titles.get(player);
        return title != null ? ChatColors.rgb(title.color()) : TextColor.fromLegacyFormat(ChatColors.tier(level));
    }

    /** The player's name in their colour: hover for the profile card, click to whisper, shift-click to insert it. */
    public static MutableComponent name(ServerPlayer player) {
        return name(player, level(player));
    }

    public static MutableComponent name(ServerPlayer player, int level) {
        String name = player.getGameProfile().name();
        TextColor color = nameColor(player, level);
        Component card = card(player, level);
        return Component.literal(name).withStyle(style -> style.withColor(color)
                .withHoverEvent(new HoverEvent.ShowText(card))
                .withClickEvent(new ClickEvent.SuggestCommand("/msg " + name + " "))
                .withInsertion(name));
    }

    /** "[Founder] " when the player has a title, else nothing. */
    public static MutableComponent titlePrefix(ServerPlayer player) {
        Titles.Title title = Titles.get(player);
        return title == null ? Component.empty() : Titles.tag(title).append(Component.literal(" "));
    }

    /**
     * The profile card: level and skill average, top skills, slayers, purse, active pet and held item. A snapshot
     * from when the message was sent.
     */
    public static Component card(ServerPlayer player, int level) {
        String name = player.getGameProfile().name();
        MutableComponent card = Component.empty();
        Titles.Title title = Titles.get(player);
        if (title != null) {
            card.append(Titles.tag(title)).append(" ");
        }
        card.append(Component.literal(name).withStyle(style -> style.withColor(nameColor(player, level)).withBold(true)));

        card.append(newLine()).append(label(StatTypes.LEVEL_ICON + " SkyRPG Level ")).append(Component.literal(String.valueOf(level))
                .withStyle(ChatColors.tier(level)));
        card.append(newLine()).append(label("Skill Average ")).append(Component.literal(String.format(Locale.ROOT, "%.1f", skillAverage(player)))
                .withColor(ChatColors.AQUA));

        List<SkillInfo> top = new ArrayList<>(skillList());
        top.sort(Comparator.comparingInt((SkillInfo skill) -> -SkillLevels.level(player, skill.id())).thenComparingInt(SkillInfo::order));
        boolean anySkill = false;
        for (SkillInfo skill : top.subList(0, Math.min(TOP_SKILLS, top.size()))) {
            int skillLevel = SkillLevels.level(player, skill.id());
            if (skillLevel <= 0) {
                continue;
            }
            if (!anySkill) {
                card.append(newLine()).append(newLine()).append(label("Top skills"));
                anySkill = true;
            }
            card.append(newLine()).append(Component.literal(" ▪ ").withColor(ChatColors.DIM))
                    .append(Component.literal(skill.name() + " ").withStyle(style -> style.withColor(skill.color())))
                    .append(Component.literal(String.valueOf(skillLevel)).withColor(ChatColors.TEXT));
        }

        MutableComponent slayerLine = Component.empty();
        boolean anySlayer = false;
        for (SkillInfo slayer : slayers) {
            int slayerLevel = SkillLevels.level(player, slayer.id());
            if (slayerLevel <= 0) {
                continue;
            }
            if (anySlayer) {
                slayerLine.append(Component.literal(" · ").withColor(ChatColors.DIM));
            }
            slayerLine.append(Component.literal(slayer.name() + " ").withStyle(style -> style.withColor(slayer.color())))
                    .append(Component.literal(String.valueOf(slayerLevel)).withColor(ChatColors.TEXT));
            anySlayer = true;
        }
        card.append(newLine());
        if (anySlayer) {
            card.append(newLine()).append(label("Slayers ")).append(slayerLine);
        }
        card.append(newLine()).append(label("Purse ")).append(Component.literal(StatTypes.COIN_ICON + " " + Msg.number(Wallet.balance(player)))
                .withColor(ChatColors.ACCENT));
        ItemStack pet = activePet(player);
        if (!pet.isEmpty()) {
            card.append(newLine()).append(label("Pet ")).append(pet.getHoverName());
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            card.append(newLine()).append(label("Holding ")).append(held.getStyledHoverName());
            if (held.getCount() > 1) {
                card.append(Component.literal(" x" + held.getCount()).withColor(ChatColors.MUTED));
            }
        }
        card.append(newLine()).append(newLine()).append(Component.literal("Click to message · Shift-click to insert name").withColor(ChatColors.DIM));
        return card;
    }

    private static MutableComponent label(String text) {
        return Component.literal(text).withColor(ChatColors.MUTED);
    }

    private static Component newLine() {
        return Component.literal("\n");
    }
}
