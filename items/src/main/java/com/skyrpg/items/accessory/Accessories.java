package com.skyrpg.items.accessory;

import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The accessories a player wears (amulet, cloak, belt, bracers). They live in a player attachment, not in
 * the inventory: saved with the player, kept on death (death drops and /clear never touch them), and synced
 * to everyone who can see them, so other clients can draw them. The inventory screen shows them as extra
 * {@code InventoryMenu} slots ({@link AccessoryMenuSlot}), and they give their item's stats through the
 * "Accessories" stat source.
 * <p>
 * The player's own client gets them through those menu slots only, like armour: a second copy from the
 * attachment sync would arrive late and undo clicks the client has already predicted (and in creative,
 * where the client sends its slots to the server, put a taken-off accessory back).
 * <p>
 * Never change a stack inside the attachment; every change is a new {@link AccessoryLoadout} set through
 * here, which is what syncs it.
 */
public final class Accessories {
    public static final AttachmentType<AccessoryLoadout> LOADOUT = AttachmentRegistry.create(SkyRpgItems.id("accessories"), builder -> builder
            .persistent(AccessoryLoadout.CODEC)
            .copyOnDeath()
            .syncWith(AccessoryLoadout.STREAM_CODEC, AttachmentSyncPredicate.allButTarget()));

    /** Every accessory: the slot tags together. */
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, SkyRpgItems.id("accessories"));

    private Accessories() {
    }

    public static void init() {
        Stats.registerSource(new AccessoryStatSource());
        Stats.registerSource(new MoonbloodCharmSource());
        UseItemCallback.EVENT.register((player, level, hand) -> equipFromHand(player, hand));
    }

    /** What the player wears. Works on both sides (other players too, on a client); never null. */
    public static AccessoryLoadout loadout(Player player) {
        return player.getAttachedOrElse(LOADOUT, AccessoryLoadout.EMPTY);
    }

    /** The stack stored in this slot, whatever it is. Don't change it. */
    public static ItemStack get(Player player, AccessorySlot slot) {
        return loadout(player).get(slot);
    }

    /**
     * The accessory worn in this slot, or EMPTY if the slot holds something that doesn't belong there
     * (creative players can put anything anywhere). Use this for stats and rendering.
     */
    public static ItemStack worn(Player player, AccessorySlot slot) {
        ItemStack stack = get(player, slot);
        return slot.fits(stack) ? stack : ItemStack.EMPTY;
    }

    /** Puts this stack in the slot, as given. On the server it syncs and the player's stats update. */
    public static void set(Player player, AccessorySlot slot, ItemStack stack) {
        player.setAttached(LOADOUT, loadout(player).with(slot, stack));
        if (player instanceof ServerPlayer serverPlayer) {
            Stats.refresh(serverPlayer);
        }
    }

    /** Brings worn SkyRPG accessories in line with their definitions, like the inventory (see {@link SkyItems#normalize}). */
    public static void normalize(ServerPlayer player) {
        for (AccessorySlot slot : AccessorySlot.values()) {
            ItemStack stack = get(player, slot);
            ItemStack copy = stack.copy();
            SkyItems.normalize(copy);
            if (!ItemStack.matches(copy, stack)) {
                set(player, slot, copy);
            }
        }
    }

    /** Plays the slot's equip sound for everyone nearby (server only, like armour). */
    static void playEquipSound(Player player, AccessorySlot slot) {
        if (!player.level().isClientSide() && !player.isSilent() && !player.isSpectator()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), slot.equipSound(), player.getSoundSource(), 1.0f, 1.0f);
        }
    }

    /**
     * Right-clicking with an accessory puts it on, swapping with whatever was in its slot, like armour.
     * The server does it; the client just swings.
     */
    private static InteractionResult equipFromHand(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        AccessorySlot slot = AccessorySlot.of(held);
        if (slot == null || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        ItemStack worn = get(player, slot);
        if (ItemStack.isSameItemSameComponents(held, worn)) {
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (held.getCount() <= 1) {
            // Creative keeps what it held (a copy goes on) unless something comes off to replace it.
            ItemStack inHand = !worn.isEmpty() ? worn : player.isCreative() ? held : ItemStack.EMPTY;
            set(player, slot, held.copy());
            player.setItemInHand(hand, inHand);
        } else {
            set(player, slot, held.copyWithCount(1));
            if (!player.isCreative()) {
                player.setItemInHand(hand, held.copyWithCount(held.getCount() - 1));
            }
            if (!worn.isEmpty() && !player.getInventory().add(worn)) {
                player.drop(worn, false, Prediction.SERVER_ONLY);
            }
        }
        playEquipSound(player, slot);
        return InteractionResult.SUCCESS;
    }
}
