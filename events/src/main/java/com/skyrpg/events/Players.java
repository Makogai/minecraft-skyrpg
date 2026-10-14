package com.skyrpg.events;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * What events do to the players in them (see {@link EventSetup}): joining from chat, the inventory
 * stash, kits, the arena teleport and gliding.
 */
final class Players {
    private static final List<String> JOIN_WORDS = List.of(".join", "!join");
    private static final List<String> LEAVE_WORDS = List.of(".leave", "!leave");

    private Players() {
    }

    static void init() {
        // ".join" in chat joins an opt-in event (and isn't shown as chat).
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.signedContent().trim().toLowerCase(Locale.ROOT);
            boolean joining = JOIN_WORDS.contains(text);
            if (!joining && !LEAVE_WORDS.contains(text)) {
                return true;
            }
            List<ActiveEvent> open = EventManager.running().stream()
                    .filter(event -> event.spec().participation() == EventSpec.Participation.OPT_IN && event.spec().setup().chatJoin())
                    .filter(event -> event.hasJoined(sender.getUUID()) != joining)
                    .toList();
            if (open.isEmpty()) {
                boolean anyOpen = EventManager.running().stream().anyMatch(event -> event.spec().setup().chatJoin()
                        && event.spec().participation() == EventSpec.Participation.OPT_IN);
                if (!anyOpen) {
                    return true;
                }
                Notice.send(sender, Notice.Kind.EVENT, Msg.text(joining ? "You're already in." : "You're not in an event.", ChatFormatting.GRAY));
                return false;
            }
            // Several to pick from: the ones still taking players first.
            List<ActiveEvent> lobby = open.stream().filter(ActiveEvent::inLobby).toList();
            List<ActiveEvent> pick = lobby.size() == 1 ? lobby : open;
            if (pick.size() > 1) {
                Notice.send(sender, Notice.Kind.EVENT, Msg.text("Several events are running; use /event " + (joining ? "join" : "leave") + " <"
                        + String.join("|", pick.stream().map(ActiveEvent::id).toList()) + ">", ChatFormatting.GRAY));
                return false;
            }
            ActiveEvent event = pick.getFirst();
            if (joining) {
                if (EventManager.join(event, sender)) {
                    Notice.send(sender, Notice.Kind.EVENT, Msg.text("You joined " + event.spec().name() + ". Good luck!", ChatFormatting.GREEN));
                }
            } else if (EventManager.leave(event, sender)) {
                Notice.send(sender, Notice.Kind.EVENT, Msg.text("You left " + event.spec().name() + ".", ChatFormatting.GRAY));
            }
            return false;
        });

        // Dying drops the kit, so hand it out again (and put them back in the arena).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            for (ActiveEvent event : EventManager.running()) {
                if (event.equipped.contains(player.getUUID())) {
                    giveKit(player, event.spec().setup());
                    if (event.spec().setup().startAt() != null) {
                        Stash.teleport(EventManager.server(), player, event.spec().setup().startAt());
                    }
                }
            }
        });
    }

    /**
     * Gets a player ready to play: stashes their inventory, gives the kit, sends them to the arena.
     *
     * @return false if they can't play (their inventory couldn't be put away safely)
     */
    static boolean equip(MinecraftServer server, ActiveEvent event, ServerPlayer player) {
        UUID id = player.getUUID();
        EventSetup setup = event.spec().setup();
        if (event.equipped.contains(id) || !setup.equips()) {
            return true;
        }
        if (setup.stashInventory()) {
            if (Stash.exists(server, id)) {
                Notice.send(player, Notice.Kind.WARN, Msg.text("Your inventory is already put away for another event, so you can't play "
                        + event.spec().name() + " right now.", ChatFormatting.RED));
                return false;
            }
            if (!Stash.save(server, player, event.id())) {
                Notice.send(player, Notice.Kind.WARN, Msg.text("Couldn't put your inventory away safely, so you can't play "
                        + event.spec().name() + " this time.", ChatFormatting.RED));
                return false;
            }
            Notice.send(player, Notice.Kind.EVENT, Msg.text("Your inventory is safely put away until " + event.spec().name() + " is over.", ChatFormatting.GRAY));
        } else if (setup.startAt() != null && setup.returnAfter()) {
            event.returnTo.put(id, new EventSetup.Location(player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()));
        }
        event.equipped.add(id);
        event.remember(player);
        giveKit(player, setup);
        if (setup.startAt() != null) {
            Stash.teleport(server, player, setup.startAt());
        }
        return true;
    }

    /** Undoes {@link #equip}: gives the inventory back and sends them back where they were. */
    static void unequip(MinecraftServer server, ActiveEvent event, ServerPlayer player) {
        if (!event.equipped.remove(player.getUUID())) {
            return;
        }
        EventSetup setup = event.spec().setup();
        EventSetup.Location back = event.returnTo.remove(player.getUUID());
        if (setup.stashInventory()) {
            EventSetup.Location stashedAt = Stash.restore(server, player);
            if (back == null) {
                back = stashedAt;
            }
        }
        if (setup.startAt() != null && setup.returnAfter() && back != null) {
            Stash.teleport(server, player, back);
        }
    }

    /**
     * On join: gives back an inventory left over from an event that ended (or a crash) while they were
     * away, and equips late arrivals for everyone-events that need it.
     */
    static void onJoin(MinecraftServer server, ServerPlayer player, Collection<ActiveEvent> running) {
        UUID id = player.getUUID();
        boolean held = running.stream().anyMatch(event -> event.equipped.contains(id));
        if (!held && Stash.exists(server, id)) {
            String from = Stash.eventOf(server, id);
            EventSetup.Location back = Stash.restore(server, player);
            if (back != null) {
                Stash.teleport(server, player, back);
                Notice.send(player, Notice.Kind.EVENT, Msg.text("Here's the inventory you had before " + (from.isEmpty() ? "the event" : from) + ".", ChatFormatting.GREEN));
            }
        }
        // Late arrivals for everyone-events, and joined players who were away when it began.
        for (ActiveEvent event : List.copyOf(running)) {
            if (event.begun && event.participates(player) && !event.equipped.contains(id)) {
                equip(server, event, player);
            }
        }
    }

    static void giveKit(ServerPlayer player, EventSetup setup) {
        if (setup.kit().isEmpty()) {
            return;
        }
        ItemParser parser = new ItemParser(player.level().registryAccess());
        for (EventSetup.KitItem kit : setup.kit()) {
            ItemStack stack;
            try {
                stack = parser.parse(new StringReader(kit.item())).createItemStack(kit.count());
            } catch (CommandSyntaxException e) {
                SkyRpgEvents.LOGGER.warn("Kit item '{}' doesn't parse: {}", kit.item(), e.getMessage());
                continue;
            }
            put(player, stack, kit.slot());
        }
        player.containerMenu.broadcastChanges();
    }

    private static void put(ServerPlayer player, ItemStack stack, String slot) {
        Inventory inventory = player.getInventory();
        String name = slot.toLowerCase(Locale.ROOT);
        try {
            if (name.startsWith("hotbar.")) {
                inventory.setItem(Integer.parseInt(name.substring("hotbar.".length())), stack);
                return;
            }
            if (name.startsWith("inventory.")) {
                inventory.setItem(9 + Integer.parseInt(name.substring("inventory.".length())), stack);
                return;
            }
            if (name.equals("weapon.offhand")) {
                name = "offhand";
            } else if (name.startsWith("armor.")) {
                name = name.substring("armor.".length());
            }
            if (!name.isEmpty()) {
                player.setItemSlot(EquipmentSlot.byName(name), stack);
                return;
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            SkyRpgEvents.LOGGER.warn("Unknown kit slot '{}', giving the item normally", slot);
        }
        inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    /**
     * Players tagged {@link SkyRpgEvents#GLIDE_TAG} (by scripts) or playing an event with auto_glide start
     * gliding as soon as they're in the air, so nobody has to jump and double-tap.
     */
    static void glide(MinecraftServer server, Collection<ActiveEvent> running) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.onGround() || player.isFallFlying() || player.getAbilities().flying || player.isSpectator()) {
                continue;
            }
            boolean glides = player.entityTags().contains(SkyRpgEvents.GLIDE_TAG) || running.stream().anyMatch(event ->
                    event.begun && event.spec().setup().autoGlide() && event.participates(player));
            if (glides) {
                player.tryToStartFallFlying();
            }
        }
    }

    static Component joinedLine(ActiveEvent event, ServerPlayer player) {
        int playing = event.participantCount();
        return Notice.of(Notice.Kind.EVENT).tag("JOINED").build(Msg.text(player.getScoreboardName(), ChatFormatting.GOLD)
                .append(Msg.text(" joined " + event.spec().name() + "! ", ChatFormatting.GRAY))
                .append(Msg.text("(" + playing + (playing == 1 ? " player)" : " players)"), ChatFormatting.DARK_GRAY)));
    }
}
