package com.skyrpg.enchants;

import com.mojang.serialization.Codec;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Codex: every enchant a player has come across (on an item they carried or as a tome). Each first discovery pays
 * Enchanting XP by rarity and shows a banner; the enchanting screen's Codex tab lists them all, with "???" for tome-only
 * enchants nobody has shown you yet. Saved with the player and synced to them.
 */
public final class Codex {
    public static final Identifier ENCHANTING = Identifier.fromNamespaceAndPath("skyrpg_skills", "enchanting");

    /** Discovered enchant ids. */
    public record State(Set<String> discovered) {
        public static final State EMPTY = new State(Set.of());
        public static final Codec<State> CODEC = Codec.STRING.listOf().xmap(list -> new State(Set.copyOf(list)), state -> List.copyOf(new TreeSet<>(state.discovered())));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                .map(list -> new State(Set.copyOf(list)), state -> new ArrayList<>(state.discovered()));

        public boolean has(Identifier enchant) {
            return discovered.contains(enchant.toString());
        }
    }

    public static final AttachmentType<State> STATE = AttachmentRegistry.create(SkyRpgEnchants.id("codex"), builder -> builder
            .persistent(State.CODEC)
            .syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .copyOnDeath());

    private Codex() {
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Discovered.TYPE, Discovered.CODEC);
        // Tomes and enchanted gear picked up, traded or crafted count too: look through bags every few seconds.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 0) {
                server.getPlayerList().getPlayers().forEach(Codex::scan);
            }
        });
    }

    public static State state(Player player) {
        return player.getAttachedOrElse(STATE, State.EMPTY);
    }

    private static void scan(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.has(EnchantContent.ENCHANTS)) {
                EnchantContent.enchants(stack).levels().keySet().forEach(id -> discover(player, id));
            }
        }
    }

    /** Adds an enchant to the player's Codex; the first time pays Enchanting XP and shows a banner. */
    public static void discover(ServerPlayer player, Identifier enchant) {
        Enchant def = Enchants.get(enchant);
        State state = state(player);
        if (def == null || state.has(enchant)) {
            return;
        }
        Set<String> next = new TreeSet<>(state.discovered());
        next.add(enchant.toString());
        player.setAttached(STATE, new State(Set.copyOf(next)));
        double xp = Enchants.rules().skillXp().discover().getOrDefault(def.rarity(), 100.0);
        SkillLevels.giveXp(player, ENCHANTING, xp);
        Notice.of(Notice.Kind.ENCHANT).icon(Notice.rarityGem(def.rarity().color())).tag("CODEX DISCOVERY").send(player, Component.empty()
                .append(Component.literal(def.name()).withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(false)))
                .append(Msg.text("  " + def.rarity().label() + " · +" + Msg.number(xp) + " Enchanting XP", ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 1.4f);
        if (ServerPlayNetworking.canSend(player, Discovered.TYPE)) {
            ServerPlayNetworking.send(player, new Discovered(enchant.toString(), (int) xp, next.size()));
        }
    }

    /** A first discovery, for the client's banner. */
    public record Discovered(String enchant, int xp, int total) implements CustomPacketPayload {
        public static final Type<Discovered> TYPE = new Type<>(SkyRpgEnchants.id("discovered"));
        public static final StreamCodec<ByteBuf, Discovered> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Discovered::enchant, ByteBufCodecs.VAR_INT, Discovered::xp, ByteBufCodecs.VAR_INT, Discovered::total,
                Discovered::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
