package com.skyrpg.magic.client;

import com.skyrpg.core.stats.Stats;
import com.skyrpg.magic.Magic;
import com.skyrpg.magic.MagicItems;
import com.skyrpg.magic.MagicPayloads;
import com.skyrpg.magic.SpellDef;
import com.skyrpg.magic.WandData;
import com.skyrpg.magic.WandDef;
import com.skyrpg.magic.WandItem;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the client knows about casting: cooldowns the server told it about, the charge it is holding, and a screen shake. The server
 * decides everything; this is only for drawing and for not sending requests that would fail.
 */
public final class ClientMagic {
    private record Cooldown(int start, int end) {
    }

    private static final Map<String, Cooldown> COOLDOWNS = new HashMap<>();
    private static int tick;
    /** The spell being charged, or null. */
    private static SpellDef charging;
    private static int chargeStart;
    private static int shakeTotal;
    private static int shakeLeft;
    private static float shakeAmount;
    /** The client tick until which the HUD shows its "not enough mana" flash. */
    private static int manaFlashUntil;
    private static int readyFlashUntil;
    private static String readyFlashSpell = "";

    private ClientMagic() {
    }

    static void tick() {
        tick++;
        if (shakeLeft > 0) {
            shakeLeft--;
        }
        COOLDOWNS.entrySet().removeIf(e -> {
            if (tick >= e.getValue().end()) {
                readyFlashUntil = tick + 8;
                readyFlashSpell = e.getKey();
                return true;
            }
            return false;
        });
    }

    static void reset() {
        COOLDOWNS.clear();
        charging = null;
        shakeLeft = 0;
    }

    public static int now() {
        return tick;
    }

    // ------------------------------------------------------------------ wand and spells

    public static boolean holdingWand() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && WandItem.isWand(player.getMainHandItem());
    }

    public static ItemStack wandStack() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    public static WandData wandData() {
        return WandItem.data(wandStack());
    }

    public static WandDef wandDef() {
        return WandItem.def(wandStack());
    }

    public static SpellDef selectedSpell() {
        return Magic.spell(wandData().selectedSpell());
    }

    /** A scroll stack to draw as the spell's icon. */
    public static ItemStack icon(SpellDef spell) {
        Item scroll = MagicItems.scrollFor(spell.id().toString());
        return scroll == null ? ItemStack.EMPTY : new ItemStack(scroll);
    }

    /** What the spell costs with the held wand's discount. */
    public static double cost(SpellDef spell) {
        WandDef wand = wandDef();
        return spell.mana() * (1 - (wand == null ? 0 : wand.manaDiscount()));
    }

    public static double mana() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : Stats.mana(player);
    }

    // ------------------------------------------------------------------ cooldowns

    public static void cooldown(String spell, int ticks) {
        COOLDOWNS.put(spell, new Cooldown(tick, tick + ticks));
    }

    /** Ticks left on this spell's cooldown (0 when ready). */
    public static int cooldownLeft(SpellDef spell) {
        Cooldown cooldown = COOLDOWNS.get(spell.id().toString());
        return cooldown == null ? 0 : Math.max(0, cooldown.end() - tick);
    }

    /** How much of the cooldown is still to go, 0 to 1. */
    public static float cooldownFraction(SpellDef spell) {
        Cooldown cooldown = COOLDOWNS.get(spell.id().toString());
        if (cooldown == null || cooldown.end() <= cooldown.start()) {
            return 0;
        }
        return Math.max(0, Math.min(1, (cooldown.end() - tick) / (float) (cooldown.end() - cooldown.start())));
    }

    public static boolean readyFlash(SpellDef spell) {
        return tick < readyFlashUntil && spell.id().toString().equals(readyFlashSpell);
    }

    // ------------------------------------------------------------------ charge

    static void startCharge(SpellDef spell) {
        charging = spell;
        chargeStart = tick;
    }

    static void endCharge() {
        charging = null;
    }

    public static SpellDef charging() {
        return charging;
    }

    /** 0 to 1 while charging, else 0. */
    public static float chargeProgress(float partialTick) {
        if (charging == null) {
            return 0;
        }
        return Math.min(1f, (tick - chargeStart + partialTick) / Math.max(1, charging.chargeTicks()));
    }

    // ------------------------------------------------------------------ feedback

    static void flashMana() {
        manaFlashUntil = tick + 14;
    }

    public static float manaFlash() {
        return tick < manaFlashUntil ? (manaFlashUntil - tick) / 14f : 0;
    }

    static void shake(float amount, int ticks) {
        if (amount >= shakeAmount * Math.max(0, shakeLeft) / Math.max(1, shakeTotal)) {
            shakeAmount = amount;
            shakeTotal = Math.max(1, ticks);
            shakeLeft = shakeTotal;
        }
    }

    /** The camera offset for this frame, in degrees: {@code {yaw, pitch}} (zero when not shaking). */
    public static float[] shakeOffset(float partialTick) {
        if (shakeLeft <= 0) {
            return new float[] {0, 0};
        }
        float life = (shakeLeft - partialTick) / shakeTotal;
        float strength = shakeAmount * Math.max(0, life) * Math.max(0, life);
        double time = (tick + partialTick) * 2.3;
        return new float[] {(float) (Math.sin(time * 1.7) * strength * 2.2), (float) (Math.cos(time * 2.1) * strength * 1.6)};
    }

    // ------------------------------------------------------------------ talking to the server

    static void sendPress() {
        ClientPlayNetworking.send(new MagicPayloads.Press());
    }

    static void sendRelease() {
        ClientPlayNetworking.send(new MagicPayloads.Release());
    }

    /** Picks a slot of the held wand (shown at once; the server confirms). */
    public static void select(int slot) {
        ItemStack wand = wandStack();
        if (!WandItem.isWand(wand)) {
            return;
        }
        WandData data = wandData();
        if (slot < 0 || slot >= data.slots().size() || slot == data.selected()) {
            return;
        }
        WandItem.setData(wand, data.select(slot));
        endCharge();
        ClientPlayNetworking.send(new MagicPayloads.Select(slot));
    }
}
