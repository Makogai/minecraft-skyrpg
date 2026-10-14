package com.skyrpg.events;

import com.mojang.serialization.DynamicOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Keeps a player's whole inventory (and where they stood) on disk while they play an event, in
 * {@code <world>/skyrpg_events/stash/<uuid>.dat}. The file is written before the inventory is
 * cleared and deleted only after it's given back, so a crash or disconnect can't lose anything.
 * A file of its own rather than SavedData (which only reaches disk on autosave) or blocks in the world
 * (which a rebuild can wipe): the only copy must be on disk before the inventory is touched.
 */
final class Stash {
    private Stash() {
    }

    private static Path file(MinecraftServer server, UUID player) {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg_events").resolve("stash").resolve(player + ".dat");
    }

    static boolean exists(MinecraftServer server, UUID player) {
        return Files.exists(file(server, player));
    }

    /** The event that holds a stash, for messages; "" if unknown. */
    static String eventOf(MinecraftServer server, UUID player) {
        try {
            return NbtIo.readCompressed(file(server, player), NbtAccounter.unlimitedHeap()).getStringOr("Event", "");
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Saves everything the player carries and where they are, then empties their inventory.
     *
     * @return false if it couldn't be saved; the inventory is left alone then
     */
    static boolean save(MinecraftServer server, ServerPlayer player, String eventId) {
        DynamicOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inventory = player.getInventory();
        ListTag items = new ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Item", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
            items.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.put("Items", items);
        root.putString("Event", eventId);
        root.putString("Name", player.getScoreboardName());
        root.putString("Dimension", player.level().dimension().identifier().toString());
        root.putDouble("X", player.getX());
        root.putDouble("Y", player.getY());
        root.putDouble("Z", player.getZ());
        root.putFloat("Yaw", player.getYRot());
        root.putFloat("Pitch", player.getXRot());

        Path file = file(server, player.getUUID());
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            SkyRpgEvents.LOGGER.error("Couldn't stash the inventory of {}", player.getScoreboardName(), e);
            return false;
        }
        inventory.clearContent();
        player.containerMenu.broadcastChanges();
        SkyRpgEvents.LOGGER.info("Stashed the inventory of {} ({} stacks) for {}", player.getScoreboardName(), items.size(), eventId);
        return true;
    }

    /**
     * Gives a stashed inventory back (replacing whatever they have now, e.g. the event kit) and deletes
     * the file.
     *
     * @return where they stood when it was stashed, or null if there was no stash
     */
    static EventSetup.Location restore(MinecraftServer server, ServerPlayer player) {
        Path file = file(server, player.getUUID());
        if (!Files.exists(file)) {
            return null;
        }
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.error("Couldn't read the stashed inventory of {}; leaving {} in place", player.getScoreboardName(), file, e);
            return null;
        }
        DynamicOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        for (CompoundTag entry : root.getListOrEmpty("Items").compoundStream().toList()) {
            int slot = entry.getIntOr("Slot", -1);
            ItemStack stack = ItemStack.CODEC.parse(ops, entry.get("Item")).resultOrPartial(error ->
                    SkyRpgEvents.LOGGER.warn("Dropping an unreadable stashed item of {}: {}", player.getScoreboardName(), error)).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
            } else {
                inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
            }
        }
        player.containerMenu.broadcastChanges();
        try {
            Files.delete(file);
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.error("Gave {} their inventory back but couldn't delete {}", player.getScoreboardName(), file, e);
        }
        SkyRpgEvents.LOGGER.info("Gave {} their stashed inventory back", player.getScoreboardName());
        return new EventSetup.Location(Identifier.parse(root.getStringOr("Dimension", "minecraft:overworld")),
                root.getDoubleOr("X", player.getX()), root.getDoubleOr("Y", player.getY()), root.getDoubleOr("Z", player.getZ()),
                root.getFloatOr("Yaw", 0), root.getFloatOr("Pitch", 0));
    }

    static boolean teleport(MinecraftServer server, ServerPlayer player, EventSetup.Location to) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, to.dimension()));
        if (level == null) {
            SkyRpgEvents.LOGGER.warn("Can't send {} to unknown dimension {}", player.getScoreboardName(), to.dimension());
            return false;
        }
        player.stopFallFlying();
        player.resetFallDistance();
        return player.teleportTo(level, to.x(), to.y(), to.z(), java.util.Set.of(), to.yaw(), to.pitch(), true);
    }
}
