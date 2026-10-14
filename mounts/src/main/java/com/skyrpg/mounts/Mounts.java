package com.skyrpg.mounts;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * The mount stable. A tamed griffin is a Griffin Whistle item (colour, name, owner, Speed and Flight Stamina in its custom
 * data). Using one adds it to the player's stable (a saved list, like pet storage) and calls it if no mount is out; the K
 * menu's Mounts page calls any stable mount, puts the one that is out away, and takes one out as an item to trade. Only one
 * mount is out at a time: {@link #ACTIVE} holds its whistle while the {@link GriffinEntity} exists. A mount that is put
 * away, strays, is knocked out or whose owner logs off goes back into storage.
 */
public final class Mounts {
    private static final String COLOR_KEY = "color";
    private static final String NAME_KEY = "name";
    private static final String OWNER_KEY = "owner";
    private static final String OWNER_NAME_KEY = "owner_name";
    private static final String SPEED_KEY = "speed";
    private static final String STAMINA_KEY = "stamina";
    private static final String ID_KEY = "id";
    private static final String[] NAMES = {"Aerie", "Zephyr", "Talon", "Gale", "Skye", "Cirrus", "Kestrel", "Nimbus", "Sirocco", "Aster",
            "Boreas", "Ember", "Pennon", "Tempest", "Sunny", "Rook", "Haze", "Merlin", "Alba", "Cinder"};
    private static final int MAX_SPEED_BONUS = 15;
    private static final int MAX_STAMINA_BONUS = 25;

    /** The mount that is out: its whistle. Synced to its owner only (the K page). */
    public static final AttachmentType<ItemStack> ACTIVE = AttachmentRegistry.create(SkyRpgMounts.id("active"), builder -> builder
            .persistent(ItemStack.OPTIONAL_CODEC)
            .syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    /** Whistles in the stable; saved with the player and synced to the owner only. */
    public static final AttachmentType<List<ItemStack>> STORAGE = AttachmentRegistry.create(SkyRpgMounts.id("storage"), builder -> builder
            .persistent(ItemStack.OPTIONAL_CODEC.listOf())
            .syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    /** The griffin entity each online owner has out. */
    private static final Map<UUID, GriffinEntity> OUT = new HashMap<>();

    private Mounts() {
    }

    static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> away(handler.getPlayer(), false));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // a mount left "out" by a restart or crash has no body any more: back into the stable
            ServerPlayer player = handler.getPlayer();
            if (!active(player).isEmpty() && out(player) == null) {
                away(player, false);
            }
            grantTitleFlags(player);
        });
    }

    // ---------------------------------------------------------------- the whistle's data

    public static boolean isWhistle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == MountItems.GRIFFIN_WHISTLE;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static GriffinColor color(ItemStack stack) {
        return GriffinColor.parse(data(stack).getStringOr(COLOR_KEY, "golden"));
    }

    public static String name(ItemStack stack) {
        return data(stack).getStringOr(NAME_KEY, "Griffin");
    }

    public static int speedBonus(ItemStack stack) {
        return data(stack).getIntOr(SPEED_KEY, 0);
    }

    public static int staminaBonus(ItemStack stack) {
        return data(stack).getIntOr(STAMINA_KEY, 0);
    }

    public static String ownerName(ItemStack stack) {
        return data(stack).getStringOr(OWNER_NAME_KEY, "");
    }

    private static String mountId(ItemStack stack) {
        return data(stack).getStringOr(ID_KEY, "");
    }

    /** Seconds of flight a mount of this stamina bonus gets from a full bar. */
    public static float flightSeconds(int staminaBonus) {
        return GriffinEntity.BASE_FLIGHT_SECONDS * (1f + staminaBonus / 100f);
    }

    /** A new whistle with a random name and small random stats (more often low than high). */
    public static ItemStack create(GriffinColor color, RandomSource random, UUID owner, String ownerName) {
        int speed = Math.round((random.nextFloat() + random.nextFloat()) * 0.5f * MAX_SPEED_BONUS);
        int stamina = Math.round((random.nextFloat() + random.nextFloat()) * 0.5f * MAX_STAMINA_BONUS);
        return create(color, NAMES[random.nextInt(NAMES.length)], speed, stamina, owner, ownerName);
    }

    public static ItemStack create(GriffinColor color, String name, int speed, int stamina, UUID owner, String ownerName) {
        ItemStack stack = new ItemStack(MountItems.GRIFFIN_WHISTLE);
        CompoundTag tag = new CompoundTag();
        tag.putString(COLOR_KEY, color.id());
        tag.putString(NAME_KEY, name);
        tag.putInt(SPEED_KEY, speed);
        tag.putInt(STAMINA_KEY, stamina);
        tag.putString(ID_KEY, UUID.randomUUID().toString());
        if (owner != null) {
            tag.putString(OWNER_KEY, owner.toString());
            tag.putString(OWNER_NAME_KEY, ownerName);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        refreshLook(stack);
        return stack;
    }

    /** A whistle with its data in place (a bare one from the creative tab or a command becomes a fresh golden griffin). */
    private static ItemStack normalised(ItemStack stack, ServerPlayer player) {
        if (!isWhistle(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack one = stack.copyWithCount(1);
        if (!one.has(DataComponents.CUSTOM_DATA) || mountId(one).isEmpty()) {
            one = create(GriffinColor.GOLDEN, player.getRandom(), player.getUUID(), player.getGameProfile().name());
        }
        // whoever uses a whistle becomes the owner: whistles are traded
        CompoundTag tag = data(one);
        tag.putString(OWNER_KEY, player.getStringUUID());
        tag.putString(OWNER_NAME_KEY, player.getGameProfile().name());
        one.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        refreshLook(one);
        return one;
    }

    /** Name, colour model and lore: stats, how it flies, how to use it. */
    public static void refreshLook(ItemStack stack) {
        GriffinColor color = color(stack);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(color.id()), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name(stack)).withStyle(style -> style.withItalic(false).withColor(color.nameRgb()).withBold(true))
                .append(Component.literal("  Griffin Whistle").withStyle(style -> style.withItalic(false).withBold(false).withColor(ChatFormatting.GRAY))));
        int speed = speedBonus(stack);
        int stamina = staminaBonus(stack);
        List<Component> lines = new ArrayList<>();
        lines.add(plain(color.rarity().label().toUpperCase(java.util.Locale.ROOT), color.rarity().color()).withStyle(style -> style.withBold(true))
                .append(Component.literal("  " + color.label() + " griffin").withStyle(style -> style.withBold(false).withItalic(false).withColor(ChatFormatting.DARK_GRAY))));
        lines.add(Component.empty());
        lines.add(stat("Speed", "+" + speed + "%", ChatFormatting.AQUA));
        lines.add(stat("Flight Stamina", "+" + stamina + "%", ChatFormatting.GREEN)
                .append(plain("  (" + Math.round(flightSeconds(stamina)) + " s of flight)", ChatFormatting.DARK_GRAY)));
        String owner = ownerName(stack);
        if (!owner.isEmpty()) {
            lines.add(Component.empty());
            lines.add(plain("Tamed by " + owner, ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.empty());
        lines.add(plain("Right-click: add it to your stable and call it.", ChatFormatting.YELLOW));
        lines.add(plain("Call, put away or trade it in the K menu, Mounts.", ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static MutableComponent plain(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }

    private static MutableComponent stat(String name, String value, ChatFormatting color) {
        return plain(name + " ", ChatFormatting.GRAY).append(Component.literal(value).withStyle(style -> style.withItalic(false).withColor(color)));
    }

    // ---------------------------------------------------------------- chat

    public static void say(ServerPlayer player, String text) {
        Notice.of(Notice.Kind.PET).tag("GRIFFIN").send(player, body(text));
    }

    private static MutableComponent body(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(Notice.BODY_COLOR).withBold(false).withItalic(false));
    }

    private static MutableComponent coloredName(ItemStack whistle) {
        GriffinColor color = color(whistle);
        return Component.literal(name(whistle)).withStyle(style -> style.withColor(color.nameRgb()).withBold(true).withItalic(false));
    }

    // ---------------------------------------------------------------- the stable

    /** How many mounts a player can keep in the stable (override with {@code -Dskyrpg.mountStable=N}); the K menu uses the same number. */
    public static int maxStorage() {
        return Math.max(1, Integer.getInteger("skyrpg.mountStable", 20));
    }

    public static ItemStack active(Player player) {
        return player.getAttachedOrElse(ACTIVE, ItemStack.EMPTY);
    }

    /** The stable (a copy; the server's, or the synced one on the owner's client). */
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

    /** The griffin entity this player has out, or null. */
    public static GriffinEntity out(ServerPlayer player) {
        GriffinEntity griffin = OUT.get(player.getUUID());
        if (griffin == null || griffin.isRemoved() || !griffin.isAlive()) {
            OUT.remove(player.getUUID());
            return null;
        }
        return griffin;
    }

    /**
     * Using a whistle: it goes into the stable (owned by the user from now on) and, if no mount is out, is called right away.
     * The caller removes the item only if this returns true.
     */
    public static boolean claim(ServerPlayer player, ItemStack stack) {
        ItemStack mount = normalised(stack, player);
        if (mount.isEmpty()) {
            return false;
        }
        if (storageFull(player)) {
            say(player, "Your stable is full (" + maxStorage() + "). Take a mount out in the K menu first; ".concat(name(mount)) + " stays with you.");
            return false;
        }
        List<ItemStack> list = storage(player);
        list.add(mount);
        setStorage(player, list);
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 0.8f, 1.4f);
        if (active(player).isEmpty()) {
            return summon(player, list.size() - 1);
        }
        Notice.of(Notice.Kind.PET).tag("STABLE").send(player, coloredName(mount).append(body(" went into your stable. Call it in the K menu, Mounts.")));
        return true;
    }

    /** Adds a freshly made mount straight to the stable (a command, the creative tab); false when it is full. */
    public static boolean store(ServerPlayer player, ItemStack whistle) {
        if (storageFull(player)) {
            return false;
        }
        ItemStack mount = normalised(whistle, player);
        List<ItemStack> list = storage(player);
        list.add(mount);
        setStorage(player, list);
        return true;
    }

    /**
     * Calls the stable mount at {@code index} (-1: the one marked active whose body is missing) next to the player, putting away
     * the one that was out. Returns false if it could not be called.
     */
    public static boolean summon(ServerPlayer player, int index) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack mount;
        List<ItemStack> list = storage(player);
        if (index < 0) {
            mount = active(player);
            if (mount.isEmpty()) {
                return false;
            }
            GriffinEntity existing = out(player);
            if (existing != null) {
                existing.discard();
                OUT.remove(player.getUUID());
            }
        } else {
            if (index >= list.size() || !isWhistle(list.get(index))) {
                return false;
            }
            mount = list.get(index).copyWithCount(1);
            if (!active(player).isEmpty()) {
                away(player, true);
                list = storage(player);
                // the index may have moved: find it again by id
                index = indexOfId(list, mountId(mount));
                if (index < 0) {
                    return false;
                }
            }
            list.remove(index);
            setStorage(player, list);
        }
        refreshLook(mount);
        GriffinEntity griffin = MountEntities.GRIFFIN.create(level, EntitySpawnReason.COMMAND);
        if (griffin == null) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        double fx = look.x, fz = look.z;
        double len = Math.max(1.0E-3, Math.sqrt(fx * fx + fz * fz));
        Vec3 at = findSpot(level, player.position(), new Vec3(fx / len, 0, fz / len));
        griffin.snapTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
        griffin.setColor(color(mount));
        griffin.setTamed(player.getUUID(), mountId(mount), speedBonus(mount), staminaBonus(mount));
        griffin.setCustomName(Component.literal(name(mount)).withStyle(style -> style.withColor(color(mount).nameRgb())));
        griffin.setCustomNameVisible(true);
        level.addFreshEntity(griffin);
        player.setAttached(ACTIVE, mount);
        OUT.put(player.getUUID(), griffin);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, at.x, at.y + 0.8, at.z, 14, 0.5, 0.5, 0.5, 0.04);
        Notice.of(Notice.Kind.PET).tag("MOUNT OUT").send(player, coloredName(mount).append(body(" arrives. Right-click it to mount.")));
        Msg.playSound(player, SoundEvents.PARROT_IMITATE_PHANTOM, 1.0f, 0.8f);
        return true;
    }

    private static int indexOfId(List<ItemStack> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (mountId(list.get(i)).equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** A spot 2 to 3 blocks in front of the player where a griffin fits; the player's own spot if there is none. */
    private static Vec3 findSpot(ServerLevel level, Vec3 origin, Vec3 forward) {
        net.minecraft.world.phys.AABB box = MountEntities.GRIFFIN.getDimensions().makeBoundingBox(0, 0, 0);
        for (double d : new double[]{3.0, 2.4, 3.6, 4.4}) {
            for (double side : new double[]{0, 1.2, -1.2}) {
                Vec3 p = origin.add(forward.scale(d)).add(-forward.z * side, 0, forward.x * side);
                for (double dy : new double[]{0, 1, -1, 2}) {
                    Vec3 q = p.add(0, dy, 0);
                    if (level.noCollision(box.move(q))) {
                        BlockPosHolder below = new BlockPosHolder(q);
                        if (dy <= 0 || below.solidBelow(level)) {
                            return q;
                        }
                    }
                }
            }
        }
        return origin.add(0, 0.2, 0);
    }

    private record BlockPosHolder(Vec3 at) {
        boolean solidBelow(ServerLevel level) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, at.y - 0.2, at.z);
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }
    }

    /**
     * Puts the mount that is out back into the stable. {@code announce} sends the player a notice; {@code force} stays valid
     * when the stable is full (it never loses a mount).
     */
    public static boolean away(ServerPlayer player, boolean announce) {
        ItemStack mount = active(player);
        if (mount.isEmpty()) {
            return false;
        }
        GriffinEntity griffin = out(player);
        if (griffin != null) {
            if (griffin.isVehicle()) {
                griffin.ejectPassengers();
            }
            griffin.discard();
        }
        OUT.remove(player.getUUID());
        refreshLook(mount);
        List<ItemStack> list = storage(player);
        list.add(mount);
        setStorage(player, list);
        player.removeAttached(ACTIVE);
        if (announce) {
            Notice.of(Notice.Kind.PET).tag("MOUNT AWAY").send(player, coloredName(mount).append(body(" went back to the stable.")));
        }
        return true;
    }

    /** Moves the stable mount at {@code index} into the inventory as a whistle (to trade or give away). */
    static boolean takeOut(ServerPlayer player, int index) {
        List<ItemStack> list = storage(player);
        if (index < 0 || index >= list.size() || !isWhistle(list.get(index))) {
            return false;
        }
        ItemStack mount = list.get(index).copyWithCount(1);
        if (player.getInventory().getFreeSlot() < 0) {
            say(player, "Your inventory is full. Make room to take " + name(mount) + " out.");
            return false;
        }
        refreshLook(mount);
        ItemStack shown = mount.copy();
        player.getInventory().add(mount);
        if (!mount.isEmpty()) {
            return false;
        }
        list.remove(index);
        setStorage(player, list);
        Notice.of(Notice.Kind.PET).tag("MOUNT TAKEN OUT").send(player, body("Took ").append(coloredName(shown)).append(body(" out as a Griffin Whistle.")));
        return true;
    }

    // ---------------------------------------------------------------- taming

    /** Titles for griffins already owned (stable, mount out, inventory): the flags the progress mod's titles look for. */
    private static void grantTitleFlags(ServerPlayer player) {
        java.util.List<ItemStack> owned = new java.util.ArrayList<>(storage(player));
        owned.add(active(player));
        owned.addAll(player.getInventory().getNonEquipmentItems());
        for (ItemStack stack : owned) {
            if (isWhistle(stack)) {
                titleFlags(player, color(stack));
            }
        }
    }

    private static void titleFlags(ServerPlayer player, GriffinColor color) {
        com.skyrpg.core.event.TitleFlags.grant(player, "griffin_tamed");
        if (color.rarity() == GriffinRarity.LEGENDARY) {
            com.skyrpg.core.event.TitleFlags.grant(player, "griffin_legendary");
        }
    }

    /** A wild griffin accepted the meat: it turns into a whistle for the tamer. */
    static void tame(ServerPlayer player, GriffinEntity wild) {
        ItemStack whistle = create(wild.getColor(), player.getRandom(), player.getUUID(), player.getGameProfile().name());
        ServerLevel level = (ServerLevel) wild.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, wild.getX(), wild.getY(1.0), wild.getZ(), 12, 0.6, 0.5, 0.6, 0.05);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, wild.getX(), wild.getY(0.6), wild.getZ(), 16, 0.5, 0.5, 0.5, 0.05);
        wild.playSound(SoundEvents.PARROT_IMITATE_PHANTOM, 1.2f, 1.0f);
        wild.discard();
        ItemStack shown = whistle.copy();
        if (!player.getInventory().add(whistle)) {
            player.spawnAtLocation(level, whistle);
        }
        Notice.of(Notice.Kind.PET).tag("TAMED").send(player, body("The griffin trusts you! ").append(coloredName(shown))
                .append(body(" turned into a Griffin Whistle (Speed +" + speedBonus(shown) + "%, Flight Stamina +" + staminaBonus(shown) + "%).")));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        GriffinColor tamed = color(shown);
        titleFlags(player, tamed);
        if (tamed.rarity().announced() && player.level().getServer() != null) {
            boolean legendary = tamed.rarity() == GriffinRarity.LEGENDARY;
            Component line = Notice.of(Notice.Kind.PET).tag("GRIFFIN").color(tamed.rarity().rgb()).build(
                    Component.literal(player.getGameProfile().name()).withStyle(style -> style.withBold(true).withColor(Notice.BODY_COLOR))
                            .append(body(" tamed a "))
                            .append(Component.literal(tamed.rarity().label().toUpperCase(java.util.Locale.ROOT) + " " + tamed.label())
                                    .withStyle(style -> style.withBold(true).withColor(tamed.rarity().rgb())))
                            .append(body(" griffin!")));
            player.level().getServer().getPlayerList().broadcastSystemMessage(line, false);
        }
    }
}
