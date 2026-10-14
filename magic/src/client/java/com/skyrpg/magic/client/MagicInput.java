package com.skyrpg.magic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.magic.MagicPayloads;
import com.skyrpg.magic.SpellDef;
import com.skyrpg.magic.WandData;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Wand controls, read from the client. With a wand in the main hand: left click (the attack key) casts the selected spell and
 * holding it charges a chargeable one; holding the wheel key (right click) opens the spell wheel; sneak + wheel key opens the
 * slots; sneak + mouse wheel cycles spells. The vanilla attack, block breaking and use actions are switched off for a wand by the
 * {@code MinecraftMixin}, except that use still goes to whatever you aim at that has a use of its own (a chest, a door, an NPC).
 */
public final class MagicInput {
    /** Ticks the wheel key is held before the wheel opens (a quick tap does nothing). */
    private static final int WHEEL_DELAY = 5;

    public static KeyMapping wheelKey;
    public static KeyMapping wheelToggleKey;

    private static boolean attackWas;
    private static boolean useWas;
    /** The wheel key went down and could still become the wheel (or the slots). */
    private static boolean pending;
    private static int heldTicks;
    /** Set after the wheel or slots opened: ignore the key until it is let go, so closing the screen does not reopen it. */
    private static boolean locked;
    private static boolean toggleWas;

    private MagicInput() {
    }

    static void init() {
        KeyMapping.Category category = com.skyrpg.core.client.SkyRpgCoreClient.CATEGORY;
        wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skyrpg_magic.wheel", InputConstants.Type.MOUSE,
                InputConstants.MOUSE_BUTTON_RIGHT, category));
        wheelToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skyrpg_magic.wheel_toggle", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_G, category));
    }

    /** Whether the wheel key is down: its own binding, or (while it is still on right click) the use key, so tools that press use work too. */
    static boolean wheelDown(Minecraft minecraft) {
        return wheelKey.isDown() || (wheelKey.isDefault() && minecraft.options.keyUse.isDown());
    }

    // ------------------------------------------------------------------ for the mixins

    /** Whether a wand is in hand and nothing else (a screen) has the mouse. */
    public static boolean active(Minecraft minecraft) {
        return minecraft.player != null && minecraft.gui.screen() == null && !minecraft.player.isSpectator() && ClientMagic.holdingWand();
    }

    /**
     * Whether vanilla's use action is switched off for this right click. Not with a shield in the off hand: it raises as usual (the
     * wheel still opens if you keep holding, which lowers it again).
     */
    public static boolean cancelsUse(Minecraft minecraft) {
        return takesUse(minecraft) && !minecraft.player.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
    }

    /** Whether a right click is the wand's (the wheel or the slots) rather than something vanilla would do with what you aim at. */
    public static boolean takesUse(Minecraft minecraft) {
        if (!active(minecraft)) {
            return false;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity() instanceof Enemy;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
            return !hasUse(minecraft, blockHit.getBlockPos());
        }
        return true;
    }

    private static boolean hasUse(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        return state.getBlock() instanceof EntityBlock || state.getBlock() instanceof LeverBlock
                || state.getMenuProvider(minecraft.level, pos) != null
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BEDS);
    }

    // ------------------------------------------------------------------ each tick

    static void tick(Minecraft minecraft) {
        ClientMagic.tick();
        if (minecraft.player == null || minecraft.level == null) {
            ClientMagic.reset();
            attackWas = false;
            useWas = false;
            pending = false;
            return;
        }
        boolean attack = minecraft.options.keyAttack.isDown();
        boolean use = wheelDown(minecraft);
        boolean toggle = wheelToggleKey.isDown();
        boolean screenOpen = minecraft.gui.screen() != null;
        if (!active(minecraft)) {
            if (ClientMagic.charging() != null) {
                ClientMagic.endCharge();
                if (!screenOpen) {
                    ClientMagic.sendRelease();
                }
            }
            pending = false;
            attackWas = attack;
            // While a screen has the keys, keep "was down" so the first tick back is not a fresh press.
            useWas = screenOpen ? use || useWas : use;
            toggleWas = toggle;
            if (!use) {
                locked = false;
            }
            return;
        }

        // Left click: cast, or charge and release.
        if (attack && !attackWas) {
            press(minecraft);
        } else if (!attack && attackWas && ClientMagic.charging() != null) {
            ClientMagic.endCharge();
            ClientMagic.sendRelease();
        }
        attackWas = attack;

        // The wheel key: hold for the wheel, sneak + press for the slots.
        if (!use) {
            locked = false;
            pending = false;
        } else if (!locked) {
            if (!useWas && takesUse(minecraft)) {
                if (minecraft.player.isShiftKeyDown()) {
                    ClientPlayNetworking.send(new MagicPayloads.OpenSlots());
                    locked = true;
                } else {
                    pending = true;
                    heldTicks = 0;
                }
            }
            if (pending && ++heldTicks >= WHEEL_DELAY) {
                pending = false;
                locked = true;
                SpellWheelScreen.open(false);
            }
        }
        useWas = use;
        if (toggle && !toggleWas) {
            SpellWheelScreen.open(true);
        }
        toggleWas = toggle;
    }

    private static void press(Minecraft minecraft) {
        SpellDef spell = ClientMagic.selectedSpell();
        if (spell != null) {
            if (ClientMagic.cooldownLeft(spell) > 0) {
                // The server says why; the HUD shows the sweep.
                ClientMagic.sendPress();
                return;
            }
            if (ClientMagic.mana() < ClientMagic.cost(spell)) {
                ClientMagic.flashMana();
            } else if (spell.chargeable()) {
                ClientMagic.startCharge(spell);
            }
        }
        ClientMagic.sendPress();
    }

    /** Sneak + mouse wheel: the next or previous spell. Returns true if it took the scroll. */
    public static boolean scroll(Minecraft minecraft, double amount) {
        if (!active(minecraft) || !minecraft.player.isShiftKeyDown() || amount == 0) {
            return false;
        }
        WandData data = ClientMagic.wandData();
        int count = data.slots().size();
        if (count <= 1) {
            return true;
        }
        int next = Math.floorMod(data.selected() + (amount < 0 ? 1 : -1), count);
        ClientMagic.select(next);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f + 0.05f * next, 0.5f));
        return true;
    }
}
