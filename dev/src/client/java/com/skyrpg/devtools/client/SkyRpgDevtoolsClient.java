package com.skyrpg.devtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.hud.HudEditorScreen;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.devtools.ScreenshotPayload;
import com.skyrpg.devtools.SkyRpgDevtools;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.resources.Identifier;

/**
 * Takes screenshots on request from the server (see {@link ScreenshotPayload}), saved to the client's
 * screenshots folder. Payloads starting with "set:" change the client instead ("set:guiscale:3",
 * "set:window:1280x720"), so the camera client can check every GUI scale and window size.
 */
public class SkyRpgDevtoolsClient implements ClientModInitializer {
    /** Long enough for menu intro animations to finish. */
    private static final int DELAY_TICKS = 25;
    private static int countdown = -1;
    /** Set by "delay:N" for the next screenshot only. */
    private static int nextDelay = -1;
    /** The tab list only draws while its key is held, so the "tab" page holds it until the shot is taken. */
    private static boolean holdingTab;
    /** Ticks left holding the use key ("holduse:N"), e.g. to eat. */
    private static int holdUse;
    /** Ticks left holding a movement key ("holdkey:NAME:N"), e.g. to fly a griffin: key name to ticks. */
    private static final java.util.Map<String, Integer> HELD_KEYS = new java.util.HashMap<>();
    /** The camera client's player name (./gradlew :dev:runClient -Pcam). */
    private static final String CAMERA_NAME = "SkyCam";
    private static final String CAMERA_TITLE = "SkyCam - Claude's camera test client (not your game)";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ScreenshotPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = context.client();
            String page = payload.page();
            if (page.startsWith("set:")) {
                apply(minecraft, page.substring(4));
                return;
            }
            // The screenshot command's page is a single word, so "set-events-3" works there too.
            if (page.startsWith("set-")) {
                apply(minecraft, page.substring(4).replace('-', ':'));
                return;
            }
            // Vanilla screens too, to check the theme: "inventory", "options", "pause".
            switch (page) {
                case "" -> { }
                case "close" -> minecraft.gui.setScreen(null);
                case "inventory" -> minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                case "options" -> minecraft.gui.setScreen(new OptionsScreen(null, minecraft.options));
                case "pause" -> minecraft.gui.setScreen(new PauseScreen(true));
                case "tooltip" -> minecraft.gui.setScreen(new TooltipPreviewScreen(-1));
                case "hudedit" -> minecraft.gui.setScreen(new HudEditorScreen(null));
                case "chat" -> minecraft.gui.setScreen(new ChatScreen("", false));
                case "tab" -> {
                    minecraft.gui.setScreen(null);
                    minecraft.options.keyPlayerList.setDown(true);
                    holdingTab = true;
                }
                default -> {
                    // "tooltip-12" previews inventory slot 12.
                    if (page.startsWith("tooltip-")) {
                        minecraft.gui.setScreen(new TooltipPreviewScreen(Integer.parseInt(page.substring(8))));
                    } else {
                        SkyRpgMenu.open(page);
                    }
                }
            }
            countdown = nextDelay > 0 ? nextDelay : DELAY_TICKS;
            nextDelay = -1;
        });
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            // The camera client says so in its title bar; vanilla resets the title now and then, so keep setting it.
            if (isCamera(minecraft) && minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
                minecraft.getWindow().setTitle(CAMERA_TITLE);
            }
            if (holdUse > 0 && --holdUse == 0) {
                minecraft.options.keyUse.setDown(false);
            }
            HELD_KEYS.entrySet().removeIf(held -> {
                if (held.setValue(held.getValue() - 1) <= 1) {
                    heldKey(minecraft, held.getKey()).setDown(false);
                    return true;
                }
                return false;
            });
            if (countdown > 0 && --countdown == 0) {
                countdown = -1;
                Screenshot.grab(minecraft.gameDirectory, minecraft.gameRenderer.mainRenderTarget(), message -> { });
                if (holdingTab) {
                    holdingTab = false;
                    minecraft.options.keyPlayerList.setDown(false);
                }
            }
        });
        if (isCamera(Minecraft.getInstance())) {
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SkyRpgDevtools.MOD_ID, "camera_notice"),
                    (g, deltaTracker) -> drawCameraNotice(g));
        }
    }

    private static net.minecraft.client.KeyMapping heldKey(Minecraft minecraft, String name) {
        return switch (name) {
            case "forward" -> minecraft.options.keyUp;
            case "back" -> minecraft.options.keyDown;
            case "left" -> minecraft.options.keyLeft;
            case "right" -> minecraft.options.keyRight;
            case "jump" -> minecraft.options.keyJump;
            case "sneak" -> minecraft.options.keyShift;
            case "sprint" -> minecraft.options.keySprint;
            // Mouse buttons too: holdkey:attack:N holds left click (charging a wand spell), holdkey:use:N holds right click.
            case "attack" -> minecraft.options.keyAttack;
            case "use" -> minecraft.options.keyUse;
            // holdkey:playerlist:N holds Tab, to photograph the tab list
            case "playerlist" -> minecraft.options.keyPlayerList;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static boolean isCamera(Minecraft minecraft) {
        return CAMERA_NAME.equals(minecraft.getUser().getName());
    }

    /**
     * While someone has the camera client's window focused, a red notice at the top says what it is,
     * so it isn't mistaken for the real game. Screenshots are taken with the window in the background,
     * so they never show it.
     */
    private static void drawCameraNotice(GuiGraphicsExtractor g) {
        Minecraft minecraft = Minecraft.getInstance();
        // Not while a screenshot is pending, so it never shows up in them.
        if (!minecraft.isWindowActive() || countdown >= 0) {
            return;
        }
        Font font = minecraft.font;
        String text = "SKYCAM: Claude's camera test client. Your game is the other window.";
        int w = font.width(text) + 16;
        int x = (g.guiWidth() - w) / 2;
        g.nextStratum();
        g.fill(x, 2, x + w, 18, 0xF0400A0A);
        g.outline(x, 2, w, 16, 0xFFFF5555);
        g.text(font, text, x + 8, 6, 0xFFFFDDDD, false);
    }

    /** "guiscale:3" (0 = auto), "window:1280x720", input for the open screen, or test data (see {@link DevHooks}). */
    private static void apply(Minecraft minecraft, String setting) {
        String[] parts = setting.split(":", 2);
        if (parts.length < 2) {
            // Value-less settings ("set:reload") get an empty value instead of being dropped.
            parts = new String[] {parts[0], ""};
        }
        try {
            switch (parts[0]) {
                case "guiscale" -> {
                    minecraft.options.guiScale().set(Integer.parseInt(parts[1]));
                    minecraft.resizeGui();
                }
                case "window" -> {
                    String[] size = parts[1].split("x");
                    minecraft.getWindow().setWindowed(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
                }
                // Test data for this client only (see DevHooks): "events:3", "winner:2", "bank:deposit:500", "mouse:0:0".
                case "events" -> DevHooks.events(Integer.parseInt(parts[1]));
                case "winner" -> DevHooks.winner(Integer.parseInt(parts[1]));
                case "bank" -> {
                    String[] action = parts[1].split(":");
                    DevHooks.bank(action[0].equals("deposit"), Long.parseLong(action[1]));
                }
                case "picker" -> DevHooks.picker(minecraft, Integer.parseInt(parts[1]));
                case "mouse" -> {
                    String[] xy = parts[1].split(":");
                    DevHooks.mouse(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                }
                // Input for the open screen, to drive UI like a player would: "click:X:Y" (GUI coordinates, also moves
                // the mouse there), "rclick:X:Y" (right button), "type:2.5k", "key:enter" (enter, backspace, up, down).
                case "click", "rclick" -> {
                    String[] xy = parts[1].split(":");
                    DevHooks.click(minecraft, Double.parseDouble(xy[0]), Double.parseDouble(xy[1]),
                            parts[0].equals("rclick") ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT);
                }
                // "realclick:X:Y": a left click through Minecraft's own mouse handler (GUI coordinates), so every mod's
                // mouse mixins and Fabric's screen events run exactly as for a real mouse button.
                case "realclick" -> {
                    String[] xy = parts[1].split(":");
                    DevHooks.realClick(minecraft, Double.parseDouble(xy[0]), Double.parseDouble(xy[1]), InputConstants.MOUSE_BUTTON_LEFT);
                }
                // "press:<key mapping name>": one press of a key mapping, e.g. press:key.skyrpg_items.backpack.
                case "press" -> DevHooks.press(minecraft, parts[1]);
                // "setting:<id>:<value>": sets a Settings page setting, e.g. setting:utils.minimap_rotate:true,
                // setting:interface.menu_size:large (a choice's option, any case) or a slider's number.
                case "setting" -> {
                    String[] request = parts[1].split(":");
                    DevHooks.setting(request[0], request[1]);
                }
                case "type" -> DevHooks.type(minecraft, parts[1]);
                // "chat:TEXT": sends TEXT as the player's own chat message (or a command if it starts with "/"), so chat
                // formatting can be tested end to end. Write it last in a page, since the rest of the line is the text.
                case "chat" -> {
                    if (parts[1].startsWith("/")) {
                        minecraft.player.connection.sendCommand(parts[1].substring(1));
                    } else {
                        minecraft.player.connection.sendChat(parts[1]);
                    }
                }
                // "use": right-click with the main hand, like the use key (e.g. to equip an accessory from the hand).
                case "use" -> minecraft.gameMode.useItem(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                // "useblock" / "attack": right-click or hit the block in the crosshair, like the use and attack keys.
                case "useblock" -> DevHooks.useBlock(minecraft);
                // "useentity": right-click the entity (NPC) in the crosshair.
                case "useentity" -> DevHooks.useEntity(minecraft);
                // "holduse:N": hold right-click for N ticks (eating, drawing a bow).
                case "holduse" -> {
                    holdUse = Integer.parseInt(parts[1]);
                    minecraft.options.keyUse.setDown(true);
                }
                case "attack" -> DevHooks.attack(minecraft);
                // "holdkey:NAME:N": hold a movement key for N ticks: forward, back, left, right, jump, sneak, sprint, attack or use (e.g. to fly a griffin, or charge a wand).
                case "holdkey" -> {
                    String[] request = parts[1].split(":");
                    heldKey(minecraft, request[0]).setDown(true);
                    HELD_KEYS.put(request[0], Math.max(2, Integer.parseInt(request[1])));
                }
                case "key" -> DevHooks.key(minecraft, parts[1]);
                // "rawkey:CODE[:shift]": a real key press and release (GLFW code) through the keyboard handler, like a player's.
                case "rawkey" -> DevHooks.rawKey(minecraft, Integer.parseInt(parts[1]), parts.length > 2 && parts[2].equals("shift"));
                // "reload:x": reloads the resource packs (F3+T), e.g. after editing item models and running :items:processResources.
                case "reload" -> minecraft.reloadResourcePacks();
                // "delay:N": the next screenshot waits N ticks instead of DELAY_TICKS (to catch an animation).
                case "delay" -> nextDelay = Integer.parseInt(parts[1]);
                // "pay:<player|self|nobody>:<amount>" sends the Pay page's request straight to the server, to test its checks.
                case "pay" -> {
                    String[] request = parts[1].split(":");
                    DevHooks.pay(minecraft, request[0], Long.parseLong(request[1]));
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            // A typo in a dev command shouldn't take the client down.
        }
    }
}
