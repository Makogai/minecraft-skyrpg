package com.skyrpg.magic;

import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.spell.SpellRunner;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of casting: checks the wand, the spell, the cooldown and the mana, runs the spell, and tracks charges. The
 * client only says "pressed", "released" and "pick slot N"; the charge is measured here, in server ticks.
 */
public final class Casting {
    /** A charge nobody released (a lost packet) is dropped after this long beyond its full charge. */
    private static final int CHARGE_GRACE = 100;

    private static final class State {
        final Map<String, Integer> cooldownEnd = new HashMap<>();
        SpellDef charging;
        int chargeStart;
        int chargeSlot;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Casting() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Casting::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    static void forget(UUID player) {
        STATES.remove(player);
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    // ------------------------------------------------------------------ queries

    /** Ticks until this spell can be cast again (0 when ready). */
    public static int cooldownLeft(ServerPlayer player, SpellDef spell) {
        State state = STATES.get(player.getUUID());
        if (state == null) {
            return 0;
        }
        return Math.max(0, state.cooldownEnd.getOrDefault(spell.id().toString(), 0) - player.level().getServer().getTickCount());
    }

    public static double manaCost(SpellDef spell, WandDef wand) {
        return spell.mana() * (1 - (wand == null ? 0 : wand.manaDiscount()));
    }

    private static double manaOf(ServerPlayer player) {
        return Stats.available() ? Stats.mana(player) : Double.MAX_VALUE;
    }

    // ------------------------------------------------------------------ input

    /** The cast button went down with a wand in hand. */
    public static void press(ServerPlayer player) {
        ItemStack wand = player.getMainHandItem();
        if (!WandItem.isWand(wand) || player.isSpectator()) {
            cancel(player);
            return;
        }
        WandData data = WandItem.data(wand);
        SpellDef spell = Magic.spell(data.selectedSpell());
        if (spell == null) {
            player.sendOverlayMessage(Msg.text("No spell in this slot. Sneak + right click to add a scroll.", ChatFormatting.GRAY));
            Msg.playSound(player, SoundEvents.DISPENSER_FAIL, 0.6f, 1.4f);
            cancel(player);
            return;
        }
        State state = state(player);
        if (state.charging != null) {
            return;
        }
        if (!canCast(player, spell, WandItem.def(wand))) {
            cancel(player);
            return;
        }
        if (spell.chargeable()) {
            state.charging = spell;
            state.chargeStart = player.level().getServer().getTickCount();
            state.chargeSlot = data.selected();
            Fx.sound(player.level(), player.position(), SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.6f);
            return;
        }
        fire(player, wand, spell, 0);
    }

    /** The cast button came up: let a charged spell go. */
    public static void release(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.charging == null) {
            return;
        }
        SpellDef spell = state.charging;
        int held = player.level().getServer().getTickCount() - state.chargeStart;
        state.charging = null;
        ItemStack wand = player.getMainHandItem();
        if (!WandItem.isWand(wand) || !spell.id().toString().equals(WandItem.data(wand).spellAt(state.chargeSlot))) {
            cancel(player);
            return;
        }
        if (canCast(player, spell, WandItem.def(wand))) {
            fire(player, wand, spell, Math.min(1.0, held / (double) Math.max(1, spell.chargeTicks())));
        } else {
            cancel(player);
        }
    }

    /** The wheel (or the mouse wheel) picked a slot of the held wand. */
    public static void select(ServerPlayer player, int slot) {
        ItemStack wand = player.getMainHandItem();
        if (!WandItem.isWand(wand)) {
            return;
        }
        WandData data = WandItem.data(wand);
        if (slot < 0 || slot >= data.slots().size() || slot == data.selected()) {
            return;
        }
        WandItem.setData(wand, data.select(slot));
        cancel(player);
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.charging = null;
        }
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f + 0.1f * slot);
    }

    private static void cancel(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, MagicPayloads.ChargeCancel.TYPE)) {
            ServerPlayNetworking.send(player, new MagicPayloads.ChargeCancel());
        }
    }

    // ------------------------------------------------------------------ casting

    /** Cooldown and mana, with the player told why not. */
    private static boolean canCast(ServerPlayer player, SpellDef spell, WandDef wand) {
        int left = cooldownLeft(player, spell);
        if (left > 0) {
            player.sendOverlayMessage(Component.empty().append(Msg.text(spell.name(), ChatFormatting.WHITE))
                    .append(Msg.text(" is recharging: " + String.format("%.1f", left / 20.0) + "s", ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.DISPENSER_FAIL, 0.5f, 1.8f);
            return false;
        }
        double cost = manaCost(spell, wand);
        if (manaOf(player) < cost) {
            player.sendOverlayMessage(Component.empty().append(Msg.text("Not enough mana", ChatFormatting.RED))
                    .append(Msg.text("  " + spell.name() + " needs " + Msg.number(Math.ceil(cost)) + ", you have " + Msg.number(Math.floor(manaOf(player))), ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
            return false;
        }
        return true;
    }

    private static void fire(ServerPlayer player, ItemStack wandStack, SpellDef spell, double charge) {
        WandDef wand = WandItem.def(wandStack);
        if (cast(player, spell, wand, charge)) {
            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
    }

    /** Casts a spell as this player: checks cooldown and mana, runs it, then charges them. Returns whether it went off. */
    public static boolean cast(ServerPlayer player, SpellDef spell, WandDef wand, double charge) {
        if (!canCast(player, spell, wand)) {
            return false;
        }
        CastContext ctx = new CastContext(player, spell, wand, charge);
        if (!SpellRunner.run(ctx)) {
            return false;
        }
        double cost = manaCost(spell, wand);
        if (Stats.available() && !Stats.spendMana(player, cost)) {
            return true; // went off a tick after they spent it elsewhere: free cast
        }
        int cooldown = (int) Math.round(spell.cooldownTicks() * (1 - (wand == null ? 0 : wand.cooldownDiscount())));
        if (cooldown > 0) {
            state(player).cooldownEnd.put(spell.id().toString(), player.level().getServer().getTickCount() + cooldown);
            ServerPlayNetworking.send(player, new MagicPayloads.Cooldown(spell.id().toString(), cooldown));
        }
        Fx.play(player, "cast_" + spell.element().id());
        return true;
    }

    // ------------------------------------------------------------------ charging, each tick

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = STATES.get(player.getUUID());
            if (state == null || state.charging == null) {
                continue;
            }
            ItemStack wand = player.getMainHandItem();
            int held = now - state.chargeStart;
            if (!WandItem.isWand(wand) || !state.charging.id().toString().equals(WandItem.data(wand).spellAt(state.chargeSlot))
                    || held > state.charging.chargeTicks() + CHARGE_GRACE || !player.isAlive()) {
                state.charging = null;
                cancel(player);
                continue;
            }
            double progress = Math.min(1.0, held / (double) Math.max(1, state.charging.chargeTicks()));
            Vec3 tip = new CastContext(player, state.charging, null, 0).muzzle();
            ParticleOptions particle = switch (state.charging.element()) {
                case FIRE -> ParticleTypes.FLAME;
                case FROST -> ParticleTypes.SNOWFLAKE;
                case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
                case ARCANE -> ParticleTypes.ENCHANT;
                case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            };
            int count = 1 + (int) (progress * 3);
            // Everyone but the caster sees the particles: right in front of your own camera they would fill the screen (the ring is your cue).
            Fx.particlesExcept(player.level(), player, particle, tip, count, 0.12 + progress * 0.2, 0.02);
            if (held % 5 == 0 && held <= state.charging.chargeTicks()) {
                Fx.sound(player.level(), player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, (float) (0.8 + progress * 1.2));
            }
            if (held == state.charging.chargeTicks()) {
                Fx.sound(player.level(), player.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
                Fx.particlesExcept(player.level(), player, ParticleTypes.END_ROD, tip, 12, 0.3, 0.08);
            }
        }
    }
}
