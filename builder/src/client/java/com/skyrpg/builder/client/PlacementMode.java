package com.skyrpg.builder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.builder.Blueprint;
import com.skyrpg.builder.BuilderPayloads;
import com.skyrpg.builder.Placement;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Placing a blueprint in the world, with no menu open: the hologram follows where you aim (its near side sits on the
 * block you look at and it stretches away from you), and an overlay lists the keys. Scroll or R turns it, M mirrors
 * it, the arrow keys nudge it (away/towards/left/right as you look; this locks it in place), Page Up/Down or
 * Shift+scroll raise and lower it, G locks or unlocks it, left-click or Enter places it, right-click, Backspace or
 * Esc cancels. Keys and clicks go nowhere else while placing ({@code mixin/}).
 */
public final class PlacementMode {
    private static final double AIM_RANGE = 64;
    private static final int MAX_OFFSET = 64;

    private static boolean active;
    private static BlockPos station;
    private static String blueprintId;
    private static String name = "";
    private static int turns;
    private static boolean mirrored;
    private static BlockPos origin;
    private static boolean locked;
    private static int lift;
    private static String flash = "";
    private static long flashUntil;

    private PlacementMode() {
    }

    public static boolean active() {
        return active && Minecraft.getInstance().gui.screen() == null;
    }

    static BlockPos station() {
        return active ? station : null;
    }

    static String blueprintId() {
        return active ? blueprintId : null;
    }

    /** Starts placing {@code blueprintId} for the station; {@code current} is where it is now (null: follow the aim at once). */
    static void start(BlockPos stationPos, String id, String blueprintName, int startTurns, boolean startMirrored, BlockPos current) {
        active = true;
        station = stationPos;
        blueprintId = id;
        name = blueprintName == null ? "" : blueprintName;
        turns = Math.floorMod(startTurns, 4);
        mirrored = startMirrored;
        origin = current;
        // Moving one that's already placed starts locked where it is (arrows fine-tune it, G follows your aim);
        // a new one follows your aim straight away.
        locked = current != null;
        lift = 0;
        flash = "";
        Minecraft.getInstance().gui.setScreen(null);
    }

    static void stop() {
        active = false;
        station = null;
    }

    private static Blueprint blueprint() {
        return blueprintId == null ? null : Ghosts.BLUEPRINTS.get(blueprintId);
    }

    /** The hologram as it would be placed now, or null while the blueprint hasn't arrived. */
    static Placement placement() {
        Blueprint blueprint = blueprint();
        if (!active || blueprint == null || origin == null) {
            return null;
        }
        return Placement.of(blueprint, origin, turns, mirrored);
    }

    static boolean inRange() {
        if (origin == null || station == null) {
            return false;
        }
        BlockPos offset = origin.subtract(station);
        Placement placement = placement();
        boolean coversStation = placement != null && placement.contains(station);
        return Math.abs(offset.getX()) <= MAX_OFFSET && Math.abs(offset.getY()) <= MAX_OFFSET && Math.abs(offset.getZ()) <= MAX_OFFSET && !coversStation;
    }

    static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.player.blockPosition().distSqr(station) > 112 * 112) {
            stop();
            return;
        }
        Blueprint blueprint = blueprint();
        if (blueprint == null || locked || minecraft.gui.screen() != null) {
            return;
        }
        HitResult hit = minecraft.player.pick(AIM_RANGE, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
        Placement shape = Placement.of(blueprint, BlockPos.ZERO, turns, mirrored);
        int width = shape.width();
        int depth = shape.depth();
        Direction facing = minecraft.player.getDirection();
        // The near side's middle sits on the target; the build stretches away from you.
        int x = switch (facing) {
            case EAST -> target.getX();
            case WEST -> target.getX() - (width - 1);
            default -> target.getX() - width / 2;
        };
        int z = switch (facing) {
            case SOUTH -> target.getZ();
            case NORTH -> target.getZ() - (depth - 1);
            default -> target.getZ() - depth / 2;
        };
        origin = new BlockPos(x, target.getY() + lift, z);
    }

    // ------------------------------------------------------------------ input (from the mixins)

    /** A key while placing. True when it was ours (the game doesn't see it). */
    public static boolean key(KeyEvent event, int action) {
        if (!active()) {
            return false;
        }
        int key = event.key();
        boolean press = action != 0;
        boolean shift = event.hasShiftDown();
        switch (key) {
            case InputConstants.KEY_R -> {
                if (press) {
                    turn(shift ? -1 : 1);
                }
            }
            case InputConstants.KEY_M -> {
                if (action == 1) {
                    mirrored = !mirrored;
                    sound(1.3f);
                }
            }
            case InputConstants.KEY_G -> {
                if (action == 1) {
                    locked = !locked;
                    lift = 0;
                    say(locked ? "Locked in place: nudge it with the arrow keys" : "Following your aim");
                    sound(locked ? 0.9f : 1.1f);
                }
            }
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> {
                if (press) {
                    nudge(key, shift ? 5 : 1);
                }
            }
            case InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN -> {
                if (press) {
                    raise(key == InputConstants.KEY_PAGEUP ? 1 : -1);
                }
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (action == 1) {
                    confirm();
                }
            }
            case InputConstants.KEY_ESCAPE, InputConstants.KEY_BACKSPACE -> {
                if (action == 1) {
                    cancel();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A mouse button while placing: left places it, right cancels. True when it was ours. */
    public static boolean mouse(int button, int action) {
        if (!active()) {
            return false;
        }
        if (action == 1) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                confirm();
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                cancel();
            }
        }
        return button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    /** The mouse wheel while placing: turn it (Shift: raise/lower). */
    public static boolean scroll(double amount) {
        if (!active() || amount == 0) {
            return false;
        }
        if (Minecraft.getInstance().hasShiftDown()) {
            raise(amount > 0 ? 1 : -1);
        } else {
            turn(amount > 0 ? 1 : -1);
        }
        return true;
    }

    private static void turn(int step) {
        Placement before = placement();
        turns = Math.floorMod(turns + step, 4);
        Placement after = placement();
        if (locked && before != null && after != null) {
            // Turn about the middle.
            origin = origin.offset((before.width() - after.width()) / 2, 0, (before.depth() - after.depth()) / 2);
        }
        sound(1.2f);
    }

    private static void raise(int step) {
        if (origin != null) {
            lift += step;
            if (locked) {
                origin = origin.above(step);
            }
            sound(1.0f + step * 0.1f);
        }
    }

    private static void nudge(int key, int step) {
        Minecraft minecraft = Minecraft.getInstance();
        if (origin == null || minecraft.player == null) {
            return;
        }
        if (!locked) {
            locked = true;
            say("Locked in place: G to follow your aim again");
        }
        Direction facing = minecraft.player.getDirection();
        Direction move = switch (key) {
            case InputConstants.KEY_UP -> facing;
            case InputConstants.KEY_DOWN -> facing.getOpposite();
            case InputConstants.KEY_LEFT -> facing.getCounterClockWise();
            default -> facing.getClockWise();
        };
        origin = origin.relative(move, step);
        sound(1.0f);
    }

    private static void confirm() {
        Minecraft minecraft = Minecraft.getInstance();
        if (origin == null) {
            return;
        }
        if (!inRange()) {
            say("Too far from the station (64 blocks at most), or over it");
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f, 0.8f));
            return;
        }
        BlockPos offset = origin.subtract(station);
        ClientPlayNetworking.send(new BuilderPayloads.Action(station, "place", offset.getX(), offset.getY(), offset.getZ(),
                turns + "," + (mirrored ? 1 : 0)));
        // Show it where it was put right away; the server's own update follows a moment later.
        Ghosts.update(new BuilderPayloads.Ghost(station, BuilderPayloads.Ghost.PLAN, blueprintId, origin, turns, mirrored, 0));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 1.0f));
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.literal("Placed! Deliver the materials at the station. Sneak + right-click it to move this again.")
                    .withColor(BuilderScreen.CYAN));
        }
        stop();
    }

    private static void cancel() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f));
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.literal("Placing cancelled: the hologram stays where it was.").withColor(Theme.MUTED));
        }
        stop();
    }

    private static void say(String text) {
        flash = text;
        flashUntil = System.currentTimeMillis() + 2500;
    }

    private static void sound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35f));
    }

    // ------------------------------------------------------------------ overlay

    private static final String[][] KEYS = {
            {"Aim", "move"}, {"Scroll / R", "turn"}, {"M", "mirror"}, {"↑↓←→", "nudge"},
            {"PgUp/PgDn", "raise / lower"}, {"G", "lock / follow aim"}, {"Click / Enter", "place here"}, {"Esc", "cancel"},
    };

    /**
     * The key strip at the bottom of the screen, just above the bars: what's being placed, how it's going, and every
     * key. {@code bottom} is where it ends; returns its height (0 when not placing).
     */
    static int renderHud(GuiGraphicsExtractor g, int bottom) {
        if (!active()) {
            return 0;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        // Lay the keys out in rows that fit the screen.
        // Narrow enough to stay clear of the HUD boxes on the left and right.
        int maxW = Math.min(310, g.guiWidth() - 20);
        java.util.List<java.util.List<Integer>> rows = new java.util.ArrayList<>();
        java.util.List<Integer> row = new java.util.ArrayList<>();
        int rowW = 0;
        int[] widths = new int[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            widths[i] = font.width(KEYS[i][0]) + 6 + 3 + font.width(KEYS[i][1]) + 10;
            if (rowW + widths[i] > maxW - 16 && !row.isEmpty()) {
                rows.add(row);
                row = new java.util.ArrayList<>();
                rowW = 0;
            }
            row.add(i);
            rowW += widths[i];
        }
        rows.add(row);
        int widest = 0;
        for (java.util.List<Integer> keys : rows) {
            int total = 0;
            for (int i : keys) {
                total += widths[i];
            }
            widest = Math.max(widest, total);
        }
        int w = Math.min(maxW, Math.max(widest + 6, 200));
        int h = 20 + rows.size() * 14 + 4;
        int x = (g.guiWidth() - w) / 2;
        int y = bottom - h;
        Draw.shadow(g, x, y, w, h, 4, 0.5f);
        Draw.panel(g, x, y, w, h, 0xE0121620, 0xFF1F4A55);
        String status;
        int statusColor;
        if (blueprint() == null || origin == null) {
            status = "aim at the ground where it should go";
            statusColor = Theme.MUTED;
        } else if (!inRange()) {
            status = "too far from the station (64 blocks at most)";
            statusColor = Theme.RED;
        } else if (!flash.isEmpty() && System.currentTimeMillis() < flashUntil) {
            status = flash;
            statusColor = Theme.ACCENT;
        } else {
            status = locked ? "locked in place" : "following your aim";
            statusColor = Theme.GREEN;
        }
        int labelW = font.width("PLACING") + 6;
        Draw.text(g, font, "PLACING", x + 8, y + 6, BuilderScreen.CYAN);
        int nameW = Math.min(font.width(name), (w - 24 - labelW) / 2);
        Draw.fit(g, font, Component.literal(name), x + 8 + labelW, y + 6, nameW, Draw.Align.LEFT, Theme.TEXT);
        Draw.fit(g, font, Component.literal("·  " + status), x + 14 + labelW + nameW, y + 6, w - 22 - labelW - nameW, Draw.Align.LEFT, statusColor);
        for (int r = 0; r < rows.size(); r++) {
            java.util.List<Integer> keys = rows.get(r);
            int total = 0;
            for (int i : keys) {
                total += widths[i];
            }
            int cx = x + (w - total + 10) / 2;
            int cy = y + 20 + r * 14;
            for (int i : keys) {
                String keyText = KEYS[i][0];
                int kw = font.width(keyText) + 6;
                Draw.rounded(g, cx, cy, kw, 11, Theme.KEY);
                Draw.outline(g, cx, cy, kw, 11, Theme.CARD_BORDER);
                Draw.text(g, font, keyText, cx + 3, cy + 2, Theme.TEXT);
                Draw.text(g, font, KEYS[i][1], cx + kw + 3, cy + 2, Theme.MUTED);
                cx += widths[i];
            }
        }
        return h + 4;
    }
}
