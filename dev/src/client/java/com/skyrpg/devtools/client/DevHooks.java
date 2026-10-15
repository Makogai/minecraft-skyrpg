package com.skyrpg.devtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.economy.net.EconomyPayloads;
import com.skyrpg.events.net.EventPayloads;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;

/**
 * Dev-only test data for the camera client, so UI can be checked with stress data without touching
 * other players: fake events and winner boards are fed straight into this client's events UI, bank and
 * pay requests go to the server like the screens' buttons would, and clicks, typing and keys drive
 * whatever screen is open.
 */
final class DevHooks {
    private static final String[] NAMES = {"Bartholomew_Long", "Steve", "Alex", "xX_Sk1lled_Xx", "Notch", "Jeb_", "Dinnerbone",
            "Grumm", "Kingbdogz", "LongNamePlayer16", "Bot11", "Mumbo"};
    /** Time left per fake event: plenty, the gold last minute, the red last ten seconds. */
    private static final long[] ENDS_IN = {245_000, 8_000, 52_000, 0};

    private DevHooks() {
    }

    /** "events:N" shows N fake events (0 clears them). */
    static void events(int count) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        List<EventPayloads.EventView> views = new ArrayList<>();
        String[] titles = {"The Great Skyhaven Timber Rush Extravaganza", "Monster Hunt", "Sky Rings Grand Prix of the Floating Isles", "Trivia Night"};
        int[] colors = {0xFFC8913C, 0xFFE04848, 0xFF5CE1E6, 0xFFB98CFF};
        String[] icons = {"oak_log", "iron_sword", "elytra", "book"};
        for (int i = 0; i < count; i++) {
            List<EventPayloads.Entry> top = new ArrayList<>();
            for (int p = 0; p < NAMES.length; p++) {
                top.add(new EventPayloads.Entry(new UUID(7, p), NAMES[p], 1_234_567 / (p + 1), p + 1));
            }
            boolean lobby = i == 3;
            views.add(new EventPayloads.EventView("dev_" + i, titles[i % titles.length], "Chop the most logs before the clock runs out!",
                    colors[i % colors.length], Identifier.withDefaultNamespace(icons[i % icons.length]), "logs",
                    lobby ? now + 25_000 : now - 60_000, lobby ? now + 325_000 : now + ENDS_IN[i % ENDS_IN.length], i == 3, true, i != 3,
                    NAMES.length + 1, i == 3 ? List.of() : top, i == 3 ? 0 : 4_321, i == 3 ? 0 : 13));
        }
        invoke("com.skyrpg.events.client.ClientEvents", "apply", EventPayloads.State.class, new EventPayloads.State(now, views));
    }

    /** "winner:N" shows the podium with N winners per place (ties) and long prize labels. */
    static void winner(int perPlace) {
        Minecraft minecraft = Minecraft.getInstance();
        List<EventPayloads.Placing> podium = new ArrayList<>();
        String[] prizes = {"", "200 coins + Champion Sash of Skyhaven + 4 steak", "100 coins + 2 diamonds + 4 steak", "50 coins + 4 steak"};
        int index = 0;
        for (int place = 1; place <= 3; place++) {
            for (int i = 0; i < perPlace; i++) {
                podium.add(new EventPayloads.Placing(place, new UUID(9, index), NAMES[index % NAMES.length], 98_765 / place, prizes[place]));
                index++;
            }
        }
        if (minecraft.player != null) {
            podium.set(0, new EventPayloads.Placing(1, minecraft.player.getUUID(), minecraft.player.getScoreboardName(), 98_765, prizes[1]));
        }
        invoke("com.skyrpg.events.client.WinnerBoard", "show", EventPayloads.Results.class,
                new EventPayloads.Results("dev", "The Great Skyhaven Timber Rush Extravaganza", 0xFFC8913C, Identifier.withDefaultNamespace("oak_log"),
                        "logs", 13, podium, 1, 98_765, prizes[1]));
    }

    /** "bank:deposit:N" / "bank:withdraw:N" (the bank must be open on the server: /execute as ... run bank). */
    static void bank(boolean deposit, long amount) {
        ClientPlayNetworking.send(new EconomyPayloads.BankAction(deposit, EconomyPayloads.BankAction.EXACT, amount));
    }

    /** "picker:N" opens the amount picker for offer N in the open shop, like right-clicking it. */
    static void picker(Minecraft minecraft, int offer) {
        Object screen = minecraft.gui.screen();
        if (screen == null || !screen.getClass().getSimpleName().equals("ShopScreen")) {
            return;
        }
        try {
            Method open = screen.getClass().getDeclaredMethod("openPicker", int.class);
            open.setAccessible(true);
            open.invoke(screen, offer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "mouse:X:Y" moves the (virtual) mouse to GUI coordinates, e.g. off any button before a screenshot. */
    static void mouse(double x, double y) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // The mouse position is in window coordinates, which aren't the framebuffer's on a scaled display (200% on Windows).
            double scale = minecraft.getWindow().getScreenWidth() / (double) minecraft.getWindow().getGuiScaledWidth();
            set(minecraft.mouseHandler, "xpos", x * scale);
            set(minecraft.mouseHandler, "ypos", y * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "click:X:Y" / "rclick:X:Y": a click on the open screen at GUI coordinates, with the mouse moved there first (for hover states). */
    static void click(Minecraft minecraft, double x, double y, int button) {
        mouse(x, y);
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            // Press and release: containers place a carried item on release (the press may start a drag).
            MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
            // Fabric's allowMouseClick hooks (the chat tabs use one) run where the real mouse handler would call the screen.
            if (!net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, event)) {
                return;
            }
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
        }
    }

    /**
     * A click the way GLFW delivers one: the handler's cursor position is set (window pixels) and onButton runs for the
     * press and the release, so other mods' mouse mixins and Fabric's screen events are all in the path.
     */
    static void realClick(Minecraft minecraft, double guiX, double guiY, int button) {
        com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
        double sx = guiX * window.getScreenWidth() / (double) window.getGuiScaledWidth();
        double sy = guiY * window.getScreenHeight() / (double) window.getGuiScaledHeight();
        try {
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(minecraft.mouseHandler, sx);
            fy.setDouble(minecraft.mouseHandler, sy);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MouseHandler cursor fields", e);
        }
        MouseButtonInfo info = new MouseButtonInfo(button, 0);
        minecraft.mouseHandler.onButton(window.handle(), info, 1);
        minecraft.mouseHandler.onButton(window.handle(), info, 0);
    }

    /** "press:NAME": clicks a key mapping once (as if its key went down and up), by its translation key. */
    static void press(Minecraft minecraft, String name) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals(name)) {
                KeyMapping.click(mapping.getDefaultKey());
                return;
            }
        }
    }

    /** "setting:ID:VALUE": sets a toggle on the Settings page (as if clicked, so it's saved and its change hook runs). */
    static void setting(String id, boolean value) {
        for (List<Setting> settings : SkyRpgSettings.byCategory().values()) {
            for (Setting setting : settings) {
                if (setting.id().equals(id) && !setting.isAction() && setting.get() != value) {
                    setting.activate();
                }
            }
        }
    }

    /**
     * "setting:ID:VALUE" for any kind of setting: true/false for a toggle, an option for a choice
     * ("setting:interface.menu_size:large"), a number for a slider. Saved, and its change hook runs.
     */
    static void setting(String id, String value) {
        Setting setting = SkyRpgSettings.find(id);
        if (setting != null) {
            setting.setFromString(value);
        }
    }

    /** "useblock": right-clicks the block in the crosshair with the main hand. */
    static void useBlock(Minecraft minecraft) {
        if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        }
    }

    /** "useentity": right-clicks the entity in the crosshair (an NPC), like a player's click. */
    static void useEntity(Minecraft minecraft) {
        if (minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            minecraft.gameMode.interact(minecraft.player, hit.getEntity(), hit, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /** "attack": hits the block in the crosshair (starts breaking it; instant-break blocks break). */
    static void attack(Minecraft minecraft) {
        if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            minecraft.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
        }
    }

    /** "type:TEXT": types each character into the open screen. */
    static void type(Minecraft minecraft, String text) {
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            text.codePoints().forEach(c -> screen.charTyped(new CharacterEvent(c)));
        }
    }

    /** A real key press and release, through the game's keyboard handler (so mixins and key bindings see it). */
    static void rawKey(Minecraft minecraft, int key, boolean shift) {
        KeyEvent event = new KeyEvent(key, 0, shift ? 1 : 0);
        minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), 1, event);
        minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), 0, event);
    }

    /** "key:NAME": presses enter, backspace, up or down in the open screen. */
    static void key(Minecraft minecraft, String name) {
        int key = switch (name) {
            case "enter" -> InputConstants.KEY_RETURN;
            case "backspace" -> InputConstants.KEY_BACKSPACE;
            case "up" -> InputConstants.KEY_UP;
            case "down" -> InputConstants.KEY_DOWN;
            default -> -1;
        };
        Screen screen = minecraft.gui.screen();
        if (screen != null && key >= 0) {
            screen.keyPressed(new KeyEvent(key, 0, 0));
        }
    }

    /** "pay:NAME:AMOUNT": the Pay page's request, to someone by name, "self", or "nobody" (an offline UUID). */
    static void pay(Minecraft minecraft, String who, long amount) {
        UUID target = switch (who) {
            case "self" -> minecraft.player.getUUID();
            case "nobody" -> new UUID(0, 42);
            default -> minecraft.getConnection().getPlayerInfo(who).getProfile().id();
        };
        ClientPlayNetworking.send(new EconomyPayloads.Pay(target, amount));
    }

    private static void set(MouseHandler handler, String field, double value) throws ReflectiveOperationException {
        Field f = MouseHandler.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setDouble(handler, value);
    }

    private static void invoke(String className, String method, Class<?> argType, Object arg) {
        try {
            Method m = Class.forName(className).getDeclaredMethod(method, argType);
            m.setAccessible(true);
            m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
