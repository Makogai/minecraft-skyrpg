package com.skyrpg.pets;

import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Pets: a pet item remembers its rarity and XP (custom data) and shows its level, stats and perks in its name and lore.
 * Right-clicking one moves it into the player's pet storage; the K menu equips, unequips and takes pets out. The active
 * pet earns 25% of the XP its player earns in its own skill (5% in the others), gives its stats and, from level 25 and
 * 50, its perks; clients draw it floating at its player's shoulder.
 */
public final class Pets {
    public static final int MAX_LEVEL = 50;
    private static final double OWN_SKILL_SHARE = 0.25;
    private static final double OTHER_SKILL_SHARE = 0.05;
    private static final String FOLLOWER_TAG = "skyrpg_pet";
    private static final String TYPE_KEY = "pet";
    private static final String RARITY_KEY = "rarity";
    private static final String XP_KEY = "xp";

    /** The active pet; synced to every client, which draws it at the player's shoulder. */
    public static final AttachmentType<ItemStack> ACTIVE = AttachmentRegistry.create(SkyRpgPets.id("active"), builder -> builder
            .persistent(ItemStack.OPTIONAL_CODEC)
            .syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all())
            .copyOnDeath());

    /** Pets kept in storage (items with their type, rarity and XP); saved with the player and synced to the owner only. */
    public static final AttachmentType<List<ItemStack>> STORAGE = AttachmentRegistry.create(SkyRpgPets.id("storage"), builder -> builder
            .persistent(ItemStack.OPTIONAL_CODEC.listOf())
            .syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    private Pets() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Pet";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                ItemStack pet = active(player);
                PetType type = type(pet);
                if (type != null) {
                    type.stats(level(pet), rarity(pet)).forEach(out::add);
                }
            }
        });
        GearEffects.addSource(player -> {
            ItemStack pet = active(player);
            PetType type = type(pet);
            return type == null ? Map.of() : type.perks(level(pet));
        });
        SkillLevels.XP_EARNED.register(Pets::xpEarned);
        ServerTickEvents.END_SERVER_TICK.register(Pets::tick);
    }

    // ---------------------------------------------------------------- the pet item's data

    /** A new pet of this type and rarity, at level 1. */
    public static ItemStack create(PetType type, PetRarity rarity) {
        ItemStack stack = new ItemStack(PetItems.pet(type));
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE_KEY, type.id());
        tag.putString(RARITY_KEY, rarity.name());
        tag.putDouble(XP_KEY, 0);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        refreshLook(stack);
        return stack;
    }

    public static PetType type(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PetItems.PetItem item)) {
            return null;
        }
        return item.type();
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static PetRarity rarity(ItemStack stack) {
        return PetRarity.parse(data(stack).getStringOr(RARITY_KEY, "common"));
    }

    public static double xp(ItemStack stack) {
        return data(stack).getDoubleOr(XP_KEY, 0);
    }

    /** Total XP to reach a level: 250 x (level - 1)^2, so level 50 is about 600,000. */
    public static double xpFor(int level) {
        return 250.0 * (level - 1) * (level - 1);
    }

    public static int level(ItemStack stack) {
        double xp = xp(stack);
        int level = 1 + (int) Math.floor(Math.sqrt(xp / 250.0));
        return Mth.clamp(level, 1, MAX_LEVEL);
    }

    private static void setXp(ItemStack stack, double xp) {
        CompoundTag tag = data(stack);
        tag.putDouble(XP_KEY, Math.min(xp, xpFor(MAX_LEVEL)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Name "[Lv 12] Rare Mole" and lore with stats, perks and XP to the next level. */
    public static void refreshLook(ItemStack stack) {
        PetType type = type(stack);
        if (type == null) {
            return;
        }
        PetRarity rarity = rarity(stack);
        int level = level(stack);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("[Lv " + level + "] ").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))
                .append(Component.literal(rarity.label() + " " + type.displayName()).withStyle(style -> style.withItalic(false).withColor(rarity.color()))));
        List<Component> lines = new ArrayList<>();
        lines.add(plain(SkillLevels.displayName(type.skill()) + " pet", ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());
        for (Map.Entry<StatType, Double> stat : type.stats(level, rarity).entrySet()) {
            lines.add(Component.literal(stat.getKey().symbol() + " ").withStyle(style -> style.withItalic(false).withColor(stat.getKey().color()))
                    .append(plain(stat.getKey().name() + " ", ChatFormatting.GRAY))
                    .append(Component.literal(stat.getKey().formatBonus(stat.getValue())).withStyle(style -> style.withItalic(false)
                            .withColor(stat.getKey().color()))));
        }
        lines.add(Component.empty());
        lines.add(perkLine(level >= 25, "Lv 25: " + type.perk25Text()));
        lines.add(perkLine(level >= 50, "Lv 50: " + type.perk50Text()));
        lines.add(Component.empty());
        if (level >= MAX_LEVEL) {
            lines.add(plain("MAX LEVEL", ChatFormatting.GOLD));
        } else {
            double into = xp(stack) - xpFor(level);
            double need = xpFor(level + 1) - xpFor(level);
            lines.add(plain("Next level: " + Msg.number(Math.floor(into)) + " / " + Msg.number(need) + " XP", ChatFormatting.DARK_AQUA));
        }
        lines.add(plain("Right-click to add it to your pet storage (K menu, Pets).", ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static MutableComponent plain(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }

    private static Component perkLine(boolean unlocked, String text) {
        return plain((unlocked ? "✔ " : "✘ ") + text, unlocked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    // ---------------------------------------------------------------- chat

    private static final String[] GEMS = {Notice.RARITY_COMMON, Notice.RARITY_UNCOMMON, Notice.RARITY_RARE, Notice.RARITY_EPIC, Notice.RARITY_LEGENDARY};

    /** The chat-icon gem for a rarity. */
    public static String gem(PetRarity rarity) {
        return GEMS[rarity.ordinal()];
    }

    /** A PET notice with the pet's rarity gem and colour. */
    public static Notice notice(PetRarity rarity) {
        return Notice.of(Notice.Kind.PET).icon(gem(rarity)).color(rgb(rarity));
    }

    /** A rarity's colour as 0xRRGGBB. */
    public static int rgb(PetRarity rarity) {
        var color = net.minecraft.network.chat.Style.EMPTY.withColor(rarity.color()).getColor();
        return color == null ? 0xFFFFFF : color.getValue();
    }

    /** "Rare Mole" in the rarity colour (bold), hovering to the pet's full tooltip. */
    public static MutableComponent coloredName(ItemStack pet) {
        PetRarity rarity = rarity(pet);
        PetType type = type(pet);
        HoverEvent hover = pet.isEmpty() ? null : new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(pet));
        return Component.literal(rarity.label() + " " + (type == null ? "Pet" : type.displayName())).withStyle(style -> style
                .withColor(rarity.color()).withBold(true).withItalic(false).withHoverEvent(hover));
    }

    /** "Lv 12" in aqua. */
    public static MutableComponent levelText(int level) {
        return Component.literal("Lv " + level).withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(true).withItalic(false));
    }

    /** Body text in the notices' soft grey. */
    public static MutableComponent grey(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(Notice.BODY_COLOR).withBold(false).withItalic(false));
    }

    /** "Rare Mole  Lv 12" for the notices. */
    public static MutableComponent nameAndLevel(ItemStack pet) {
        return coloredName(pet).append(grey("  ")).append(levelText(level(pet)));
    }

    // ---------------------------------------------------------------- the pet storage

    /** How many pets a player can keep in storage (override with {@code -Dskyrpg.petStorage=N}); the K menu uses the same number. */
    public static int maxStorage() {
        return Math.max(1, Integer.getInteger("skyrpg.petStorage", 30));
    }

    /** The stored pets (a copy; the server's, or the synced one on the owner's client). */
    public static List<ItemStack> storage(Player player) {
        return new ArrayList<>(player.getAttachedOrElse(STORAGE, List.of()));
    }

    private static void setStorage(ServerPlayer player, List<ItemStack> list) {
        if (list.isEmpty()) {
            player.removeAttached(STORAGE);
        } else {
            player.setAttached(STORAGE, list);
        }
    }

    public static boolean storageFull(ServerPlayer player) {
        return storage(player).size() >= maxStorage();
    }

    /** A pet item with its data in place (bare ones from commands or the creative tab become Common). */
    private static ItemStack normalised(ItemStack stack) {
        PetType type = type(stack);
        if (type == null) {
            return ItemStack.EMPTY;
        }
        ItemStack one = stack.copyWithCount(1);
        if (!one.has(DataComponents.CUSTOM_DATA)) {
            one = create(type, PetRarity.COMMON);
        }
        refreshLook(one);
        return one;
    }

    /**
     * Moves a pet (the stack is the player's own item, already checked to be a pet) into storage and equips it when no pet is
     * out. The caller removes the item from wherever it was only if this returns true.
     */
    public static boolean claim(ServerPlayer player, ItemStack stack) {
        ItemStack pet = normalised(stack);
        if (pet.isEmpty()) {
            return false;
        }
        if (storageFull(player)) {
            notice(rarity(pet)).tag("STORAGE FULL").send(player, grey("Your pet storage is full (" + maxStorage() + "). Take a pet out in the K menu first; ")
                    .append(coloredName(pet).withStyle(style -> style.withBold(false))).append(grey(" stays with you.")));
            return false;
        }
        List<ItemStack> list = storage(player);
        list.add(pet);
        setStorage(player, list);
        notice(rarity(pet)).tag("PET STORED").send(player, coloredName(pet).append(grey(" added to your pet storage.")));
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 0.8f, 1.4f);
        if (active(player).isEmpty()) {
            equipStored(player, list.size() - 1);
        }
        return true;
    }

    /** Adds a freshly made pet (an egg hatching, a command) straight to storage; false when it is full. */
    public static boolean store(ServerPlayer player, ItemStack pet, boolean announce) {
        if (storageFull(player)) {
            return false;
        }
        List<ItemStack> list = storage(player);
        list.add(normalised(pet));
        setStorage(player, list);
        if (active(player).isEmpty()) {
            equipStored(player, list.size() - 1);
        } else if (announce) {
            notice(rarity(pet)).tag("PET STORED").send(player, coloredName(pet).append(grey(" added to your pet storage.")));
        }
        return true;
    }

    /** Makes the stored pet at {@code index} the active one; the pet that was out takes its place in storage. */
    static void equipStored(ServerPlayer player, int index) {
        List<ItemStack> list = storage(player);
        if (index < 0 || index >= list.size() || type(list.get(index)) == null) {
            return;
        }
        ItemStack next = list.get(index).copyWithCount(1);
        ItemStack old = active(player);
        if (type(old) != null) {
            refreshLook(old);
            list.set(index, old);
        } else {
            list.remove(index);
        }
        refreshLook(next);
        setStorage(player, list);
        player.setAttached(ACTIVE, next);
        Stats.refresh(player);
        MutableComponent body = nameAndLevel(next);
        if (type(old) == null) {
            body.append(grey("  is following you"));
        } else {
            body.append(grey("  takes over from ")).append(coloredName(old).withStyle(style -> style.withBold(false)));
        }
        notice(rarity(next)).tag(type(old) == null ? "PET OUT" : "PET SWAPPED").send(player, body);
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 0.8f, 1.2f);
    }

    /** Sends the active pet back to storage. */
    static boolean unequip(ServerPlayer player) {
        ItemStack pet = active(player);
        if (type(pet) == null) {
            Notice.send(player, Notice.Kind.PET, grey("You have no pet out."));
            return false;
        }
        List<ItemStack> list = storage(player);
        if (list.size() >= maxStorage()) {
            notice(rarity(pet)).tag("STORAGE FULL").send(player, grey("Your pet storage is full (" + maxStorage() + "); take a pet out first."));
            return false;
        }
        refreshLook(pet);
        list.add(pet);
        setStorage(player, list);
        player.removeAttached(ACTIVE);
        Stats.refresh(player);
        notice(rarity(pet)).tag("PET AWAY").send(player, grey("Put away ").append(coloredName(pet)).append(grey(". It is in your pet storage.")));
        return true;
    }

    /** Moves the stored pet at {@code index} into the inventory as an item (to trade or give away). */
    static boolean takeOut(ServerPlayer player, int index) {
        List<ItemStack> list = storage(player);
        if (index < 0 || index >= list.size() || type(list.get(index)) == null) {
            return false;
        }
        ItemStack pet = list.get(index).copyWithCount(1);
        if (player.getInventory().getFreeSlot() < 0) {
            Notice.send(player, Notice.Kind.PET, grey("Your inventory is full. Make room to take ").append(coloredName(pet).withStyle(style -> style.withBold(false)))
                    .append(grey(" out.")));
            return false;
        }
        refreshLook(pet);
        ItemStack shown = pet.copy();
        player.getInventory().add(pet);
        if (!pet.isEmpty()) {
            return false;
        }
        list.remove(index);
        setStorage(player, list);
        notice(rarity(shown)).tag("PET TAKEN OUT").send(player, grey("Took ").append(coloredName(shown)).append(grey(" out. It is in your inventory.")));
        return true;
    }

    // ---------------------------------------------------------------- the active pet

    public static ItemStack active(Player player) {
        return player.getAttachedOrElse(ACTIVE, ItemStack.EMPTY);
    }

    private static void xpEarned(ServerPlayer player, net.minecraft.resources.Identifier skill, double xp) {
        ItemStack pet = active(player);
        PetType type = type(pet);
        if (type == null || xp <= 0) {
            return;
        }
        int before = level(pet);
        ItemStack updated = pet.copy();
        setXp(updated, xp(updated) + xp * (skill.equals(type.skill()) ? OWN_SKILL_SHARE : OTHER_SKILL_SHARE));
        int after = level(updated);
        if (after > before) {
            refreshLook(updated);
            MutableComponent body = coloredName(updated).append(grey(" reached level ")).append(levelText(after)).append(grey("!"));
            if (after == 25 || after == 50) {
                body.append(Component.literal("  New perk: ").withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false)))
                        .append(Component.literal(after == 25 ? type.perk25Text() : type.perk50Text())
                                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
            } else if (after == MAX_LEVEL) {
                body.append(Component.literal("  MAX LEVEL").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
            }
            notice(rarity(updated)).tag("PET LEVEL UP").send(player, body);
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
            player.setAttached(ACTIVE, updated);
            Stats.refresh(player);
        } else {
            player.setAttached(ACTIVE, updated);
        }
    }

    /** Item displays left from an older build that tried to make pets ride players: gone. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Display.ItemDisplay stray : level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class),
                    display -> display.entityTags().contains(FOLLOWER_TAG))) {
                stray.discard();
            }
        }
    }
}
